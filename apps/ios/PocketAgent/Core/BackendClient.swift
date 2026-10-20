// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

enum BackendError: LocalizedError, Equatable {
    case badCode, alreadyClaimed, rejected, timeout, oldHost, unauthorized, rateLimited
    case http(Int)
    case network(String)
    case badPayload

    var errorDescription: String? {
        switch self {
        case .badCode: "Kod tanınmadı veya süresi dolmuş (5 dk)."
        case .alreadyClaimed: "Bu kod başka bir cihazda kullanılmış."
        case .rejected: "Host eşleştirmeyi reddetti."
        case .timeout: "Host'ta onay gelmedi. pocket-agent pair'i yeniden çalıştır."
        case .oldHost: "Host CLI eski: anahtarsız eşleme için pocket-agent'ı güncelle."
        case .unauthorized: "Cihaz token'ı geçersiz veya iptal edilmiş — yeniden eşle."
        case .rateLimited: "Çok fazla deneme. Bir dakika bekleyip tekrar dene."
        case let .http(code): "Backend HTTP \(code) döndü. Adres doğru mu?"
        case let .network(msg): msg
        case .badPayload: "Backend yanıtı çözülemedi."
        }
    }
}

struct ClaimResult: Equatable {
    var sshHost: String
    var sshPort: Int
    var sshUser: String
    var deviceToken: String
    var tenantID: String
    var hostKeys: [String]
    var hostName: String?
}

/// Device-side client for the pocket-agent backend. Every authenticated call
/// sends `Authorization: Bearer <device token>`.
struct BackendClient {
    var baseURL: String
    var token: String?
    var session: URLSession = .shared

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { dec in
            let s = try dec.singleValueContainer().decode(String.self)
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = f.date(from: s) { return date }
            f.formatOptions = [.withInternetDateTime]
            if let date = f.date(from: s) { return date }
            return Date()
        }
        return d
    }()

    private func request(_ path: String, method: String = "GET", body: [String: Any]? = nil) -> URLRequest {
        var r = URLRequest(url: URL(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path)!)
        r.httpMethod = method
        r.timeoutInterval = 12
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty { r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        return r
    }

    private func send(_ r: URLRequest) async throws -> (Data, Int) {
        do {
            let (data, resp) = try await session.data(for: r)
            return (data, (resp as? HTTPURLResponse)?.statusCode ?? 0)
        } catch let e as URLError {
            throw BackendError.network(Self.describe(e))
        }
    }

    static func describe(_ e: URLError) -> String {
        switch e.code {
        case .cannotFindHost, .dnsLookupFailed: "Adres bulunamadı. Backend adresini kontrol et."
        case .cannotConnectToHost: "Backend bağlantıyı reddetti. Sunucu çalışıyor mu?"
        case .timedOut: "Backend zamanında yanıt vermedi."
        case .notConnectedToInternet: "İnternet bağlantısı yok."
        case .serverCertificateUntrusted, .serverCertificateHasBadDate, .serverCertificateNotYetValid,
             .serverCertificateHasUnknownRoot, .secureConnectionFailed:
            "HTTPS sertifikası doğrulanamadı."
        case .appTransportSecurityRequiresSecureConnection: "Şifresiz HTTP'ye izin yok; https:// kullan."
        default: e.localizedDescription
        }
    }

    private static func normalized(_ code: String) -> String {
        code.replacingOccurrences(of: "-", with: "")
    }

    func health() async throws {
        let (_, code) = try await send(request("/v1/healthz"))
        guard code == 200 else { throw BackendError.http(code) }
    }

    struct WhoAmI: Decodable, Equatable {
        var tenant_id: String
        var kind: String
        var subject: String
    }

    func whoami() async throws -> WhoAmI {
        let (data, code) = try await send(request("/v1/whoami"))
        switch code {
        case 200: return try BackendClient.decoder.decode(WhoAmI.self, from: data)
        case 401: throw BackendError.unauthorized
        default: throw BackendError.http(code)
        }
    }

    /// Keyless claim: only the public key travels.
    func claim(code: String, deviceID: String, deviceName: String, publicKey: String) async throws -> ClaimResult {
        let r = request("/v1/pairing-sessions/\(Self.normalized(code))/claim", method: "POST",
                        body: ["device_id": deviceID, "device_name": deviceName, "public_key": publicKey])
        let (data, status) = try await send(r)
        switch status {
        case 200: return try Self.parseClaim(data)
        case 400: throw BackendError.oldHost
        case 404, 410: throw BackendError.badCode
        case 409: throw BackendError.alreadyClaimed
        case 429: throw BackendError.rateLimited
        default: throw BackendError.http(status)
        }
    }

    static func parseClaim(_ data: Data) throws -> ClaimResult {
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let payloadText = o["payload"] as? String,
              let p = try? JSONSerialization.jsonObject(with: Data(payloadText.utf8)) as? [String: Any]
        else { throw BackendError.badPayload }
        if p["private_key"] != nil { throw BackendError.oldHost }
        guard let host = p["ssh_host"] as? String, let user = p["ssh_user"] as? String,
              let token = o["device_token"] as? String else { throw BackendError.badPayload }
        return ClaimResult(sshHost: host, sshPort: p["ssh_port"] as? Int ?? 22, sshUser: user,
                           deviceToken: token, tenantID: o["tenant_id"] as? String ?? "",
                           hostKeys: p["host_keys"] as? [String] ?? [],
                           hostName: (p["host_name"] as? String).flatMap { $0.isEmpty ? nil : $0 })
    }

    func pairingState(code: String) async throws -> String {
        let (data, status) = try await send(request("/v1/pairing-sessions/\(Self.normalized(code))"))
        switch status {
        case 200:
            let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            return o?["state"] as? String ?? ""
        case 401, 404: throw BackendError.rejected
        default: throw BackendError.http(status)
        }
    }

    /// Waits until the host user approves (CONSUMED) or rejects the device.
    func awaitApproval(code: String, timeout: TimeInterval = 300, poll: TimeInterval = 1.5) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            try Task.checkCancellation()
            do {
                switch try await pairingState(code: code) {
                case "CONSUMED": return
                case "REJECTED": throw BackendError.rejected
                default: break
                }
            } catch BackendError.network {
                // transient: keep polling
            }
            try await Task.sleep(nanoseconds: UInt64(poll * 1_000_000_000))
        }
        throw BackendError.timeout
    }

    func events(after: String? = nil) async throws -> [AgentEvent] {
        var path = "/v1/events"
        if let after, !after.isEmpty,
           let q = after.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) { path += "?after=" + q }
        let (data, status) = try await send(request(path))
        switch status {
        case 200:
            var events = try Self.decoder.decode([AgentEvent].self, from: data)
            for i in events.indices { events[i].backendURL = baseURL }
            return events
        case 401: throw BackendError.unauthorized
        default: throw BackendError.http(status)
        }
    }

    enum Decision: Equatable { case accepted, alreadyDecided }

    /// First decision wins (CAS on digest/revision).
    func approve(_ e: AgentEvent) async throws -> Decision {
        let r = request("/v1/approvals/\(e.eventID)/actions", method: "POST",
                        body: ["digest": e.digest, "revision": e.revision])
        let (_, status) = try await send(r)
        switch status {
        case 200, 202: return .accepted
        case 409: return .alreadyDecided
        case 401: throw BackendError.unauthorized
        default: throw BackendError.http(status)
        }
    }
}
