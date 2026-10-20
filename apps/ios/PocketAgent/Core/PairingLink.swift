// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

/// Parses what the host prints for pairing: the QR content
/// `pa2|<backend>|<code>|<user>` (also legacy `pa1|`), a
/// `pocketagent://pair?backend=…&code=…` link, or a bare `XXXX-XXXX` code
/// (then `fallbackBackend` is used).
enum PairingLink {
    static let alphabet = Set("ABCDEFGHJKMNPQRSTUVWXYZ23456789")

    struct Target: Equatable {
        var backend: String
        var code: String
    }

    static func parse(_ text: String, fallbackBackend: String = "") -> Target? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.hasPrefix("pa1|") || t.hasPrefix("pa2|") {
            let parts = t.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            guard parts.count >= 3, !parts[1].isEmpty, let code = normalize(parts[2]) else { return nil }
            return Target(backend: parts[1], code: code)
        }
        if let url = URL(string: t), url.scheme == "pocketagent",
           let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
           let b = items.first(where: { $0.name == "backend" })?.value,
           let c = items.first(where: { $0.name == "code" })?.value.flatMap(normalize) {
            return Target(backend: b, code: c)
        }
        guard let code = normalize(t), !fallbackBackend.isEmpty else { return nil }
        return Target(backend: fallbackBackend, code: code)
    }

    /// `abcd ef23` → `ABCD-EF23`; nil unless exactly 8 alphanumerics.
    static func normalize(_ raw: String) -> String? {
        let c = raw.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
        guard c.count == 8 else { return nil }
        return String(c.prefix(4)) + "-" + String(c.suffix(4))
    }

    /// Adds https:// when the scheme is missing; refuses plain http to
    /// anything but loopback/local network (ATS would block it anyway).
    static func normalizeBackend(_ raw: String) -> (url: String, error: String?) {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        if s.isEmpty { return ("", nil) }
        if !s.contains("://") { s = "https://" + s }
        guard let u = URL(string: s), let host = u.host, !host.isEmpty,
              u.scheme == "https" || u.scheme == "http" else {
            return (s, "Geçerli bir adres değil")
        }
        if u.scheme == "http" && !isLocal(host) {
            return (s, "Uzak adreslere şifresiz HTTP ile bağlanılmaz; https:// kullan")
        }
        return (s, nil)
    }

    static func isLocal(_ host: String) -> Bool {
        host == "localhost" || host == "127.0.0.1" || host == "::1" || host.hasSuffix(".local")
            || host.hasPrefix("10.") || host.hasPrefix("192.168.")
            || (host.hasPrefix("172.") && (16...31).contains(Int(host.split(separator: ".").dropFirst().first ?? "") ?? 0))
    }
}
