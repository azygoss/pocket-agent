// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import SwiftUI
import UIKit

/// App-wide state: paired hosts, backend accounts, the agent inbox and live
/// SSH connections. Persisted state is small JSON in Application Support;
/// secrets (SSH key, device tokens) live only in the Keychain.
@MainActor
final class AppModel: ObservableObject {
    @Published var hosts: [Host] = [] { didSet { save() } }
    @Published var accounts: [BackendAccount] = [] { didSet { save() } }
    @Published var events: [AgentEvent] = []
    @Published var inboxState: LoadState = .idle
    @Published var decided: Set<String> = []
    @Published var identity: DeviceIdentity?
    @Published var identityError: String?
    @Published var hostKeyPrompt: HostKeyPrompt?
    @Published var connectionStatus: [UUID: ConnectionStatus] = [:]
    @Published var openTerminals: [TerminalSession] = []
    @Published var pendingPairLink: PairingLink.Target?
    /// While the pairing sheet is up, it presents the host-key prompt itself
    /// (SwiftUI cannot stack two sheets from the root).
    @Published var pairingSheetVisible = false
    @Published var lastBackend: String = UserDefaults.standard.string(forKey: "lastBackend") ?? "" {
        didSet { UserDefaults.standard.set(lastBackend, forKey: "lastBackend") }
    }

    enum LoadState: Equatable { case idle, loading, loaded, failed(String) }
    enum ConnectionStatus: Equatable { case connecting, connected, failed(String) }

    struct HostKeyPrompt: Identifiable {
        let id = UUID()
        let host: Host
        let keyType: String
        let fingerprint: String
        let respond: (Bool) -> Void
    }

    private var connections: [UUID: SSHConnection] = [:]
    private let storeURL: URL
    private var loading = true

    init(storeDirectory: URL? = nil) {
        let dir = storeDirectory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("PocketAgent", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        storeURL = dir.appendingPathComponent("state.json")
        if ProcessInfo.processInfo.arguments.contains("-resetState") {
            try? FileManager.default.removeItem(at: storeURL)
            Keychain.deleteAll()
            UserDefaults.standard.removeObject(forKey: "lastBackend")
            lastBackend = ""
        }
        load()
        loading = false
        do {
            identity = try DeviceIdentity.loadOrCreate()
        } catch {
            identityError = error.localizedDescription
        }
    }

    // MARK: persistence

    private struct Persisted: Codable {
        var hosts: [Host]
        var accounts: [BackendAccount]
    }

    private func load() {
        guard let data = try? Data(contentsOf: storeURL),
              let p = try? JSONDecoder().decode(Persisted.self, from: data) else { return }
        hosts = p.hosts
        accounts = p.accounts
    }

    private func save() {
        guard !loading, let data = try? JSONEncoder().encode(Persisted(hosts: hosts, accounts: accounts)) else { return }
        try? data.write(to: storeURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    // MARK: pairing

    enum PairStep: Equatable { case claiming, waitingApproval(fingerprint: String), connecting, done }

    /// Keyless pairing: claim with this phone's public key, wait for the host
    /// to authorize it, store the device token, then connect over SSH.
    func pair(backend rawBackend: String, code: String, progress: @escaping (PairStep) -> Void) async throws -> Host {
        let (backend, err) = PairingLink.normalizeBackend(rawBackend)
        if let err { throw BackendError.network(err) }
        guard let identity else { throw BackendError.network(identityError ?? "Cihaz anahtarı yok") }
        progress(.claiming)
        let client = BackendClient(baseURL: backend)
        let claim = try await client.claim(code: code, deviceID: identity.deviceID,
                                           deviceName: DeviceIdentity.deviceName,
                                           publicKey: identity.publicKeyOpenSSH)
        progress(.waitingApproval(fingerprint: identity.fingerprint))
        let authed = BackendClient(baseURL: backend, token: claim.deviceToken)
        try await authed.awaitApproval(code: code)
        try Keychain.setString(claim.deviceToken, for: "backend-token:" + backend)
        let account = BackendAccount(url: backend, tenantID: claim.tenantID, deviceID: identity.deviceID)
        accounts.removeAll { $0.url == backend }
        accounts.append(account)
        lastBackend = backend

        var host = hosts.first {
            $0.hostname.caseInsensitiveCompare(claim.sshHost) == .orderedSame && $0.port == claim.sshPort && $0.user == claim.sshUser
        } ?? Host(name: Self.shortName(claim.hostName ?? claim.sshHost), hostname: claim.sshHost, port: claim.sshPort,
                  user: claim.sshUser, accent: hosts.count % Theme.accents.count)
        host.backendURL = backend
        host.announcedHostKeys = claim.hostKeys
        host.pairedAt = Date()
        upsert(host)
        progress(.connecting)
        _ = try await connection(for: host, fresh: true)
        progress(.done)
        Task { await refreshInbox() }
        return host
    }

    /// `ip-172-16-4-2.us-west-2.compute.internal` → `ip-172-16-4-2`; IPs stay whole.
    static func shortName(_ name: String) -> String {
        if name.allSatisfy({ $0.isNumber || $0 == "." || $0 == ":" }) { return name }
        return String(name.split(separator: ".").first ?? Substring(name))
    }

    func upsert(_ host: Host) {
        if let i = hosts.firstIndex(where: { $0.id == host.id }) { hosts[i] = host } else { hosts.append(host) }
    }

    func forget(_ host: Host) {
        connections.removeValue(forKey: host.id)?.close()
        openTerminals.filter { $0.host.id == host.id }.forEach { $0.stop() }
        openTerminals.removeAll { $0.host.id == host.id }
        hosts.removeAll { $0.id == host.id }
        connectionStatus[host.id] = nil
    }

    func removeAccount(_ account: BackendAccount) {
        Keychain.delete(account.tokenAccount)
        accounts.removeAll { $0.url == account.url }
        events.removeAll { $0.backendURL == account.url }
    }

    // MARK: SSH

    func connection(for host: Host, fresh: Bool = false) async throws -> SSHConnection {
        if !fresh, let c = connections[host.id], c.isActive { return c }
        guard let identity else { throw SSHFailure.authFailed }
        connectionStatus[host.id] = .connecting
        do {
            let c = try await SSHConnection.connect(host: host, key: identity.privateKey) { [weak self] type, fp in
                await self?.askHostKey(host: host, type: type, fingerprint: fp) ?? false
            }
            connections[host.id] = c
            connectionStatus[host.id] = .connected
            var h = hosts.first { $0.id == host.id } ?? host
            if h.pinnedHostKeys.isEmpty, let k = c.hostKey { h.trustedHostKey = k }
            h.lastConnected = Date()
            upsert(h)
            return c
        } catch {
            connectionStatus[host.id] = .failed(error.localizedDescription)
            throw error
        }
    }

    /// After a verified host key change the user can explicitly re-trust.
    func resetHostKey(_ host: Host) {
        var h = host
        h.trustedHostKey = nil
        h.announcedHostKeys = []
        upsert(h)
        connections.removeValue(forKey: host.id)?.close()
    }

    private func askHostKey(host: Host, type: String, fingerprint: String) async -> Bool {
        await withCheckedContinuation { cont in
            hostKeyPrompt = HostKeyPrompt(host: host, keyType: type, fingerprint: fingerprint) { [weak self] ok in
                self?.hostKeyPrompt = nil
                cont.resume(returning: ok)
            }
        }
    }

    func openTerminal(host: Host, tmux: String?) async throws -> TerminalSession {
        if let existing = openTerminals.first(where: { $0.host.id == host.id && $0.tmuxName == tmux && $0.state != .closed }) {
            return existing
        }
        let c = try await connection(for: host)
        let t = TerminalSession(host: host, tmuxName: tmux)
        openTerminals.append(t)
        t.start(on: c)
        return t
    }

    func closeTerminal(_ t: TerminalSession) {
        t.stop()
        openTerminals.removeAll { $0.id == t.id }
    }

    // MARK: inbox

    func token(for account: BackendAccount) -> String? { Keychain.string(account.tokenAccount) }

    func refreshInbox() async {
        guard !accounts.isEmpty else {
            events = []
            inboxState = .loaded
            return
        }
        if events.isEmpty { inboxState = .loading }
        var all: [AgentEvent] = []
        var errors: [String] = []
        for a in accounts {
            let c = BackendClient(baseURL: a.url, token: token(for: a))
            do {
                all += try await c.events()
            } catch {
                errors.append("\(a.displayHost): \(error.localizedDescription)")
            }
        }
        events = all.sorted { $0.createdAt > $1.createdAt }
        inboxState = errors.isEmpty || !all.isEmpty ? .loaded : .failed(errors.joined(separator: "\n"))
    }

    func approve(_ e: AgentEvent) async throws -> BackendClient.Decision {
        guard let a = accounts.first(where: { $0.url == e.backendURL }) else { throw BackendError.unauthorized }
        let d = try await BackendClient(baseURL: a.url, token: token(for: a)).approve(e)
        decided.insert(e.id)
        return d
    }

    var pendingApprovals: Int {
        events.filter { $0.kind == .approvalRequired && !decided.contains($0.id) && $0.expiresAt > Date() }.count
    }

    func regenerateKey() throws {
        identity = try DeviceIdentity.regenerate()
        connections.values.forEach { $0.close() }
        connections.removeAll()
        connectionStatus.removeAll()
    }
}
