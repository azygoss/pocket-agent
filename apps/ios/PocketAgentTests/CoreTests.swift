// SPDX-License-Identifier: GPL-3.0-or-later
import CryptoKit
import XCTest
@testable import PocketAgent

final class PairingLinkTests: XCTestCase {
    func testQRv2() {
        XCTAssertEqual(PairingLink.parse("pa2|http://h:8080|fqat-r6dw|berk"),
                       .init(backend: "http://h:8080", code: "FQAT-R6DW"))
    }
    func testLegacyQR() {
        XCTAssertEqual(PairingLink.parse("pa1|https://x.io|ABCDEF23")?.code, "ABCD-EF23")
    }
    func testManualNeedsBackend() {
        XCTAssertNil(PairingLink.parse("abcd ef23"))
        XCTAssertEqual(PairingLink.parse("abcd ef23", fallbackBackend: "https://b").map(\.code), "ABCD-EF23")
    }
    func testDeepLink() {
        XCTAssertEqual(PairingLink.parse("pocketagent://pair?backend=https://b.io&code=abcdefgh"),
                       .init(backend: "https://b.io", code: "ABCD-EFGH"))
    }
    func testJunk() {
        XCTAssertNil(PairingLink.parse("hello", fallbackBackend: "https://b"))
        XCTAssertNil(PairingLink.parse("pa2|only"))
    }
    func testBackendNormalization() {
        XCTAssertEqual(PairingLink.normalizeBackend(" agent.example.com/ ").url, "https://agent.example.com")
        XCTAssertNil(PairingLink.normalizeBackend("http://127.0.0.1:8080").error)
        XCTAssertNotNil(PairingLink.normalizeBackend("http://128.140.70.117:8080").error)
        XCTAssertNotNil(PairingLink.normalizeBackend("ftp://x").error)
    }
}

final class OpenSSHTests: XCTestCase {
    // Vector: RFC 8032 test 1 public key; fingerprint from `ssh-keygen -lf`.
    func testFingerprintMatchesSSHKeygen() throws {
        let raw = Data(hex: "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        let blob = OpenSSH.ed25519Blob(raw)
        XCTAssertEqual(blob.count, 4 + 11 + 4 + 32)
        XCTAssertEqual(OpenSSH.fingerprint(blob: blob), ExpectedVectors.rfc8032Fingerprint)
    }
    func testPublicKeyLineRoundTrips() throws {
        let key = Curve25519.Signing.PrivateKey()
        let id = DeviceIdentity(privateKey: key, deviceID: "device:t")
        let line = id.publicKeyOpenSSH
        XCTAssertTrue(line.hasPrefix("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5"))
        XCTAssertEqual(OpenSSH.fingerprint(authorizedKey: line), id.fingerprint)
        XCTAssertFalse(line.contains("PRIVATE"))
    }
}

final class ModelTests: XCTestCase {
    func testTmuxParse() {
        let out = "main|3|1|1700000100\nscra|tch|1|0|1700000200\n\n"
        let s = TmuxSession.parse(out)
        XCTAssertEqual(s.map(\.name), ["scra|tch", "main"])
        XCTAssertTrue(s[1].attached)
        XCTAssertEqual(s[1].windows, 3)
    }
    func testEventDecoding() throws {
        let json = """
        [{"EventID":"event:1","TenantID":"t","OpaqueHost":"h","OpaqueSess":"s","Source":"claude",
          "Category":"APPROVAL_REQUIRED","Message":"Bash: rm","Digest":"d","Revision":"1",
          "CreatedAt":"2026-10-20T12:00:00.123Z","ExpiresAt":"2026-10-21T12:00:00Z"}]
        """
        let e = try BackendClient.decoder.decode([AgentEvent].self, from: Data(json.utf8))
        XCTAssertEqual(e.first?.kind, .approvalRequired)
        XCTAssertEqual(e.first?.digest, "d")
    }
    func testClaimParsing() throws {
        let body = #"{"state":"CLAIMED","device_token":"pad_x","tenant_id":"tenant:1","payload":"{\"ssh_host\":\"h\",\"ssh_port\":2222,\"ssh_user\":\"u\",\"host_keys\":[\"SHA256:a\"]}"}"#
        let c = try BackendClient.parseClaim(Data(body.utf8))
        XCTAssertEqual(c.sshPort, 2222)
        XCTAssertEqual(c.deviceToken, "pad_x")
        XCTAssertEqual(c.hostKeys, ["SHA256:a"])
    }
    func testLegacyPrivateKeyPayloadRejected() {
        let body = #"{"device_token":"pad_x","payload":"{\"ssh_host\":\"h\",\"ssh_user\":\"u\",\"private_key\":\"k\"}"}"#
        XCTAssertThrowsError(try BackendClient.parseClaim(Data(body.utf8))) {
            XCTAssertEqual($0 as? BackendError, .oldHost)
        }
    }
    func testShellQuote() {
        XCTAssertEqual(Shell.quote("a'b"), "'a'\\''b'")
    }
    func testPinnedKeysIncludeTrusted() {
        var h = Host(name: "x", hostname: "x", user: "u")
        h.announcedHostKeys = ["SHA256:a"]
        h.trustedHostKey = "SHA256:b"
        XCTAssertEqual(h.pinnedHostKeys, ["SHA256:a", "SHA256:b"])
    }
}

/// Keyless pairing against a stubbed backend: asserts the claim carries only
/// the public key and the device token is used as a bearer afterwards.
final class BackendClientTests: XCTestCase {
    override func setUp() { StubProtocol.requests = [] }

    func session() -> URLSession {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: cfg)
    }

    func testClaimAndAwaitApproval() async throws {
        StubProtocol.handler = { req in
            switch req.url!.path {
            case "/v1/pairing-sessions/ABCDEFGH/claim":
                return (200, #"{"device_token":"pad_t","tenant_id":"t","payload":"{\"ssh_host\":\"h\",\"ssh_user\":\"u\"}"}"#)
            case "/v1/pairing-sessions/ABCDEFGH":
                return (200, #"{"state":"CONSUMED"}"#)
            default: return (404, "")
            }
        }
        let c = BackendClient(baseURL: "https://b.test", session: session())
        let r = try await c.claim(code: "ABCD-EFGH", deviceID: "device:1", deviceName: "iPhone", publicKey: "ssh-ed25519 AAAA x")
        let authed = BackendClient(baseURL: "https://b.test", token: r.deviceToken, session: session())
        try await authed.awaitApproval(code: "ABCD-EFGH", timeout: 2, poll: 0.05)
        let claim = StubProtocol.requests[0]
        XCTAssertNil(claim.value(forHTTPHeaderField: "Authorization"))
        let body = try JSONSerialization.jsonObject(with: claim.bodyData) as! [String: String]
        XCTAssertEqual(body["public_key"], "ssh-ed25519 AAAA x")
        XCTAssertNil(body["private_key"])
        XCTAssertEqual(StubProtocol.requests[1].value(forHTTPHeaderField: "Authorization"), "Bearer pad_t")
    }

    func testRejectedAndErrors() async {
        StubProtocol.handler = { req in
            req.url!.path.hasSuffix("/claim") ? (409, "") : (200, #"{"state":"REJECTED"}"#)
        }
        let c = BackendClient(baseURL: "https://b.test", token: "pad", session: session())
        do {
            _ = try await c.claim(code: "ABCDEFGH", deviceID: "d", deviceName: "n", publicKey: "k")
            XCTFail("expected 409")
        } catch { XCTAssertEqual(error as? BackendError, .alreadyClaimed) }
        do {
            try await c.awaitApproval(code: "ABCDEFGH", timeout: 1, poll: 0.05)
            XCTFail("expected rejection")
        } catch { XCTAssertEqual(error as? BackendError, .rejected) }
    }

    func testUnauthorizedEvents() async {
        StubProtocol.handler = { _ in (401, "") }
        do {
            _ = try await BackendClient(baseURL: "https://b.test", token: "bad", session: session()).events()
            XCTFail()
        } catch { XCTAssertEqual(error as? BackendError, .unauthorized) }
    }
}

final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: (URLRequest) -> (Int, String) = { _ in (404, "") }
    nonisolated(unsafe) static var requests: [URLRequest] = []
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        var r = request
        if r.httpBody == nil, let s = r.httpBodyStream {
            s.open()
            var d = Data()
            var buf = [UInt8](repeating: 0, count: 4096)
            while s.hasBytesAvailable { let n = s.read(&buf, maxLength: buf.count); if n <= 0 { break }; d.append(buf, count: n) }
            s.close()
            r.httpBody = d
        }
        StubProtocol.requests.append(r)
        let (code, body) = StubProtocol.handler(r)
        let resp = HTTPURLResponse(url: r.url!, statusCode: code, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: resp, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

extension URLRequest {
    var bodyData: Data { httpBody ?? Data() }
}

extension Data {
    init(hex: String) {
        var d = Data()
        var i = hex.startIndex
        while i < hex.endIndex {
            let j = hex.index(i, offsetBy: 2)
            d.append(UInt8(hex[i..<j], radix: 16)!)
            i = j
        }
        self = d
    }
}

enum ExpectedVectors {
    /// `ssh-keygen -lf` of the RFC 8032 test 1 public key.
    static let rfc8032Fingerprint = "SHA256:bbXpuKG6zhzdmnxq256TlqzFBzRl2f6OOg722cYNbU8"
}
