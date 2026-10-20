// SPDX-License-Identifier: GPL-3.0-or-later
import Citadel
import Crypto
import Foundation
import NIOCore
import NIOSSH

enum SSHFailure: LocalizedError {
    case hostKeyMismatch(expected: [String], got: String)
    case hostKeyRejected(String)
    case authFailed
    case unreachable(String)
    case command(String)

    var errorDescription: String? {
        switch self {
        case let .hostKeyMismatch(_, got):
            "Host anahtarı beklenenle eşleşmiyor (\(got)). Biri araya giriyor olabilir."
        case .hostKeyRejected: "Host anahtarına güvenilmedi; bağlantı kesildi."
        case .authFailed: "SSH kimlik doğrulaması başarısız. Bu cihazın anahtarı host'ta yetkili mi? (pocket-agent pair)"
        case let .unreachable(m): "Host'a ulaşılamadı: \(m)"
        case let .command(m): m
        }
    }
}

/// Decides unknown host keys. Pinned keys pass silently; a different key for
/// a host with pins fails hard; an unpinned host asks the user (TOFU).
final class TOFUValidator: NIOSSHClientServerAuthenticationDelegate, @unchecked Sendable {
    let pinned: [String]
    let ask: @Sendable (String, String) async -> Bool
    private(set) var accepted: String?

    init(pinned: [String], ask: @escaping @Sendable (String, String) async -> Bool) {
        self.pinned = pinned
        self.ask = ask
    }

    static func describe(_ key: NIOSSHPublicKey) -> (type: String, fingerprint: String) {
        let line = String(openSSHPublicKey: key)
        return (String(line.split(separator: " ").first ?? "ssh"), OpenSSH.fingerprint(authorizedKey: line) ?? "?")
    }

    func validateHostKey(hostKey: NIOSSHPublicKey, validationCompletePromise: EventLoopPromise<Void>) {
        let (type, fp) = Self.describe(hostKey)
        if pinned.contains(fp) {
            accepted = fp
            validationCompletePromise.succeed(())
            return
        }
        if !pinned.isEmpty {
            validationCompletePromise.fail(SSHFailure.hostKeyMismatch(expected: pinned, got: fp))
            return
        }
        let ask = self.ask
        Task {
            if await ask(type, fp) {
                self.accepted = fp
                validationCompletePromise.succeed(())
            } else {
                validationCompletePromise.fail(SSHFailure.hostKeyRejected(fp))
            }
        }
    }
}

/// One authenticated SSH connection to a host; terminals, tmux queries and
/// SFTP all multiplex over it.
final class SSHConnection: @unchecked Sendable {
    let client: SSHClient
    let hostKey: String?
    private var sftpClient: SFTPClient?

    private init(client: SSHClient, hostKey: String?) {
        self.client = client
        self.hostKey = hostKey
    }

    static func connect(host: Host, key: Curve25519.Signing.PrivateKey,
                        askHostKey: @escaping @Sendable (String, String) async -> Bool) async throws -> SSHConnection {
        let validator = TOFUValidator(pinned: host.pinnedHostKeys, ask: askHostKey)
        do {
            let client = try await SSHClient.connect(
                host: host.hostname, port: host.port,
                authenticationMethod: .ed25519(username: host.user, privateKey: key),
                hostKeyValidator: .custom(validator),
                reconnect: .never,
                connectTimeout: .seconds(15))
            return SSHConnection(client: client, hostKey: validator.accepted)
        } catch let e as SSHFailure {
            throw e
        } catch SSHClientError.allAuthenticationOptionsFailed {
            throw SSHFailure.authFailed
        } catch {
            let text = String(describing: error)
            if text.contains("hostKeyMismatch") || text.contains("hostKeyRejected") {
                throw SSHFailure.hostKeyRejected(text)
            }
            if text.lowercased().contains("auth") { throw SSHFailure.authFailed }
            throw SSHFailure.unreachable(Self.readable(error))
        }
    }

    static func readable(_ error: Error) -> String {
        let t = String(describing: error)
        if t.contains("connectTimeout") || t.contains("timeout") { return "zaman aşımı" }
        if t.contains("refused") || t.contains("ECONNREFUSED") { return "bağlantı reddedildi (sshd çalışıyor mu?)" }
        return t.count > 140 ? String(t.prefix(140)) + "…" : t
    }

    var isActive: Bool { client.isConnected }

    /// Runs a command; stderr is ignored and a non-zero exit is not an error
    /// (callers append `|| true` where needed).
    func run(_ command: String) async throws -> String {
        let buf = try await client.executeCommand(command, maxResponseSize: 4 << 20, mergeStreams: false)
        return String(buffer: buf)
    }

    static let pathPrefix = "PATH=\"$PATH:/opt/homebrew/bin:/usr/local/bin:/snap/bin\"; export PATH; "

    func listTmux() async throws -> [TmuxSession] {
        let out = try await run(Self.pathPrefix +
            "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_activity}' 2>/dev/null || true")
        return TmuxSession.parse(out)
    }

    func hasTmux() async -> Bool {
        (try? await run(Self.pathPrefix + "command -v tmux >/dev/null && echo yes || echo no"))?.contains("yes") ?? false
    }

    func newTmux(_ name: String) async throws {
        _ = try await run(Self.pathPrefix + "tmux new-session -d -s \(Shell.quote(name))")
    }

    func killTmux(_ name: String) async throws {
        _ = try await run(Self.pathPrefix + "tmux kill-session -t \(Shell.quote(name)) 2>/dev/null || true")
    }

    func sftp() async throws -> SFTPClient {
        if let s = sftpClient, s.isActive { return s }
        let s = try await client.openSFTP()
        sftpClient = s
        return s
    }

    func close() {
        let c = client
        Task { try? await c.close() }
    }
}

enum Shell {
    /// POSIX single-quote escaping.
    static func quote(_ s: String) -> String {
        "'" + s.replacingOccurrences(of: "'", with: "'\\''") + "'"
    }
}
