// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Security

/// Small Keychain wrapper. Items are device-only (never synced to iCloud or
/// included in unencrypted backups) and readable after first unlock so the
/// inbox can refresh in the background.
enum Keychain {
    static let service = "dev.pocketagent.ios"

    enum Failure: LocalizedError {
        case status(OSStatus)
        var errorDescription: String? {
            if case let .status(s) = self { return "Keychain hatası (\(s))" }
            return nil
        }
    }

    private static func base(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account]
    }

    static func set(_ data: Data, for account: String) throws {
        let query = base(account)
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        var status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attrs) { $1 } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw Failure.status(status) }
    }

    static func get(_ account: String) -> Data? {
        var q = base(account)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    static func string(_ account: String) -> String? {
        get(account).flatMap { String(data: $0, encoding: .utf8) }
    }

    static func setString(_ value: String, for account: String) throws {
        try set(Data(value.utf8), for: account)
    }

    static func delete(_ account: String) {
        SecItemDelete(base(account) as CFDictionary)
    }

    static func deleteAll() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrService as String: service] as CFDictionary)
    }
}
