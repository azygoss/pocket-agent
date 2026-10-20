// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// A paired (or manually added) SSH host.
struct Host: Identifiable, Codable, Hashable {
    var id = UUID()
    var name: String
    var hostname: String
    var port: Int = 22
    var user: String
    /// Backend this host was paired through (events come from there).
    var backendURL: String?
    /// Host key fingerprints announced by the host during pairing.
    var announcedHostKeys: [String] = []
    /// Fingerprint the user explicitly trusted on first connect (TOFU).
    var trustedHostKey: String?
    var pairedAt = Date()
    var lastConnected: Date?
    var accent: Int = 0

    var address: String { port == 22 ? "\(user)@\(hostname)" : "\(user)@\(hostname):\(port)" }

    /// Fingerprints that are accepted without asking.
    var pinnedHostKeys: [String] {
        var keys = announcedHostKeys
        if let t = trustedHostKey, !keys.contains(t) { keys.append(t) }
        return keys
    }
}

/// A backend this phone holds a device token for. The token itself lives in
/// the Keychain under `tokenAccount`.
struct BackendAccount: Identifiable, Codable, Hashable {
    var url: String
    var tenantID: String
    var deviceID: String
    var addedAt = Date()
    var id: String { url }
    var tokenAccount: String { "backend-token:" + url }
    var displayHost: String { URL(string: url)?.host ?? url }
}

/// Agent event summary as served by `GET /v1/events` (privacy whitelist:
/// never terminal content).
struct AgentEvent: Identifiable, Codable, Hashable {
    var eventID: String
    var opaqueHost: String
    var opaqueSession: String
    var source: String
    var category: String
    var message: String
    var digest: String
    var revision: String
    var createdAt: Date
    var expiresAt: Date
    var backendURL: String = ""

    var id: String { backendURL + "|" + eventID }

    enum CodingKeys: String, CodingKey {
        case eventID = "EventID", opaqueHost = "OpaqueHost", opaqueSession = "OpaqueSess"
        case source = "Source", category = "Category", message = "Message"
        case digest = "Digest", revision = "Revision"
        case createdAt = "CreatedAt", expiresAt = "ExpiresAt"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        eventID = try c.decode(String.self, forKey: .eventID)
        opaqueHost = try c.decodeIfPresent(String.self, forKey: .opaqueHost) ?? ""
        opaqueSession = try c.decodeIfPresent(String.self, forKey: .opaqueSession) ?? ""
        source = try c.decodeIfPresent(String.self, forKey: .source) ?? ""
        category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        message = try c.decodeIfPresent(String.self, forKey: .message) ?? ""
        digest = try c.decodeIfPresent(String.self, forKey: .digest) ?? ""
        revision = try c.decodeIfPresent(String.self, forKey: .revision) ?? ""
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        expiresAt = try c.decodeIfPresent(Date.self, forKey: .expiresAt) ?? Date().addingTimeInterval(86_400)
    }

    init(eventID: String, source: String, category: String, message: String,
         digest: String = "", revision: String = "", createdAt: Date = Date(),
         opaqueHost: String = "", opaqueSession: String = "") {
        self.eventID = eventID
        self.opaqueHost = opaqueHost
        self.opaqueSession = opaqueSession
        self.source = source
        self.category = category
        self.message = message
        self.digest = digest
        self.revision = revision
        self.createdAt = createdAt
        self.expiresAt = createdAt.addingTimeInterval(86_400)
    }

    var kind: Kind { Kind(rawValue: category) ?? .other }

    enum Kind: String {
        case approvalRequired = "APPROVAL_REQUIRED"
        case taskComplete = "TASK_COMPLETE"
        case taskFailed = "TASK_FAILED"
        case question = "QUESTION"
        case sessionEnded = "SESSION_ENDED"
        case other
    }
}

struct TmuxSession: Identifiable, Hashable {
    var name: String
    var windows: Int
    var attached: Bool
    var activity: Date?
    var id: String { name }

    /// Parses `tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_activity}'`.
    /// tmux rewrites control characters (e.g. tabs) to `_` when not on a TTY, so
    /// the separator is `|`; the last three fields are numeric, so a `|` inside
    /// a session name is still parsed correctly.
    static func parse(_ output: String) -> [TmuxSession] {
        output.split(whereSeparator: \.isNewline).compactMap { line in
            let f = line.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            guard f.count >= 4 else { return nil }
            let name = f.dropLast(3).joined(separator: "|")
            guard !name.isEmpty else { return nil }
            let n = f.count
            return TmuxSession(name: name, windows: Int(f[n - 3]) ?? 1, attached: (Int(f[n - 2]) ?? 0) > 0,
                               activity: Double(f[n - 1]).map { Date(timeIntervalSince1970: $0) })
        }
        .sorted { ($0.activity ?? .distantPast) > ($1.activity ?? .distantPast) }
    }
}

struct RemoteFile: Identifiable, Hashable {
    var name: String
    var path: String
    var isDirectory: Bool
    var isSymlink: Bool
    var size: UInt64
    var modified: Date?
    var permissions: UInt32?
    var id: String { path }
}
