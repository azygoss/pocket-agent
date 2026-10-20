// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import Foundation
import UIKit

/// The phone's SSH identity. The Ed25519 key pair is generated on device and
/// stored in the Keychain; only the OpenSSH public key ever leaves the phone
/// (sent once in the pairing claim, then authorized by the host).
struct DeviceIdentity {
    static let keyAccount = "ssh-ed25519-private"
    static let idAccount = "device-id"

    let privateKey: Curve25519.Signing.PrivateKey
    let deviceID: String

    var comment: String { "pocket-agent@\(DeviceIdentity.deviceName)" }

    var publicKeyOpenSSH: String {
        "ssh-ed25519 \(OpenSSH.ed25519Blob(privateKey.publicKey.rawRepresentation).base64EncodedString()) \(comment)"
    }

    var fingerprint: String {
        OpenSSH.fingerprint(blob: OpenSSH.ed25519Blob(privateKey.publicKey.rawRepresentation))
    }

    static var deviceName: String {
        let n = UIDevice.current.name
        return n.isEmpty ? UIDevice.current.model : n
    }

    static func loadOrCreate() throws -> DeviceIdentity {
        let id: String
        if let existing = Keychain.string(idAccount) {
            id = existing
        } else {
            id = "device:" + UUID().uuidString.lowercased()
            try Keychain.setString(id, for: idAccount)
        }
        if let raw = Keychain.get(keyAccount),
           let key = try? Curve25519.Signing.PrivateKey(rawRepresentation: raw) {
            return DeviceIdentity(privateKey: key, deviceID: id)
        }
        let key = Curve25519.Signing.PrivateKey()
        try Keychain.set(key.rawRepresentation, for: keyAccount)
        return DeviceIdentity(privateKey: key, deviceID: id)
    }

    /// New key pair: every host must re-pair because the old public key is
    /// what their authorized_keys trusts.
    static func regenerate() throws -> DeviceIdentity {
        Keychain.delete(keyAccount)
        return try loadOrCreate()
    }
}

enum OpenSSH {
    static func string(_ s: String) -> Data {
        lengthPrefixed(Data(s.utf8))
    }

    static func lengthPrefixed(_ d: Data) -> Data {
        var len = UInt32(d.count).bigEndian
        return Data(bytes: &len, count: 4) + d
    }

    static func ed25519Blob(_ raw: Data) -> Data {
        string("ssh-ed25519") + lengthPrefixed(raw)
    }

    /// `SHA256:<base64 without padding>`, identical to `ssh-keygen -lf`.
    static func fingerprint(blob: Data) -> String {
        let digest = Data(SHA256.hash(data: blob)).base64EncodedString()
        return "SHA256:" + digest.trimmingCharacters(in: CharacterSet(charactersIn: "="))
    }

    /// Fingerprint of an authorized_keys style line (`type base64 [comment]`).
    static func fingerprint(authorizedKey line: String) -> String? {
        let parts = line.split(separator: " ")
        guard parts.count >= 2, let blob = Data(base64Encoded: String(parts[1])) else { return nil }
        return fingerprint(blob: blob)
    }
}
