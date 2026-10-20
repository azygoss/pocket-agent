// SPDX-License-Identifier: GPL-3.0-or-later
import XCTest

/// UI flows. `testEndToEnd` drives the real stack started by
/// scripts/e2e.sh (backend + unprivileged sshd + tmux + `pocket-agent pair
/// --yes`), which writes the backend URL and pairing code into
/// /tmp/pa-e2e/. The simulator shares the Mac's filesystem and loopback.
final class PocketAgentUITests: XCTestCase {
    let e2eDir = "/tmp/pa-e2e"

    override func setUp() { continueAfterFailure = false }

    func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-resetState"]
        app.launch()
        return app
    }

    func testWelcomeAndCodeValidation() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["Pocket Agent"].waitForExistence(timeout: 5))
        app.buttons["pairButton"].tap()
        let code = app.textFields["codeField"]
        XCTAssertTrue(code.waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["submitPair"].isEnabled)
        let backend = app.textFields["backendField"]
        backend.tap()
        backend.typeText("http://127.0.0.1:1")
        code.tap()
        code.typeText("abcdefgh")
        XCTAssertEqual(code.value as? String, "ABCD-EFGH")
        app.buttons["submitPair"].tap()
        XCTAssertTrue(app.otherElements["errorBanner"].waitForExistence(timeout: 15)
                      || app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Backend'")).firstMatch.waitForExistence(timeout: 1))
        attach(app, "pair-error")
    }

    private func read(_ name: String, timeout: TimeInterval = 30) -> String? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let s = try? String(contentsOfFile: "\(e2eDir)/\(name)", encoding: .utf8)
                .trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty { return s }
            Thread.sleep(forTimeInterval: 0.3)
        }
        return nil
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let shot = app.screenshot()
        // Also written next to the e2e fixtures so the script can collect them.
        let dir = "\(e2eDir)/shots"
        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        try? shot.pngRepresentation.write(to: URL(fileURLWithPath: "\(dir)/\(name).png"))
        let a = XCTAttachment(screenshot: shot)
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    func testEndToEnd() throws {
        guard FileManager.default.fileExists(atPath: "\(e2eDir)/backend") else {
            throw XCTSkip("run apps/ios/scripts/e2e.sh")
        }
        let app = launch()
        let backendURL = try XCTUnwrap(read("backend"))
        try? FileManager.default.removeItem(atPath: "\(e2eDir)/code")
        try "go".write(toFile: "\(e2eDir)/start-pair", atomically: true, encoding: .utf8)
        let code = try XCTUnwrap(read("code"), "pair code not produced")

        // 1) Pair with the manual code.
        app.buttons["pairButton"].tap()
        let backend = app.textFields["backendField"]
        XCTAssertTrue(backend.waitForExistence(timeout: 5))
        backend.tap()
        backend.typeText(backendURL)
        app.textFields["codeField"].tap()
        app.textFields["codeField"].typeText(code)
        attach(app, "01-code-entered")
        app.buttons["submitPair"].tap()
        sleep(1)
        attach(app, "02a-after-submit")
        // Host runs with --yes, so approval may already be past; the step
        // list must be visible either way.
        XCTAssertTrue(app.staticTexts["SSH bağlantısı kuruluyor"].waitForExistence(timeout: 15))

        // 2) TOFU prompt (e2e sshd keys are not announced) → trust.
        let trust = app.buttons["trustHostKey"]
        if trust.waitForExistence(timeout: 30) {
            attach(app, "03-host-key")
            trust.tap()
        }
        XCTAssertTrue(app.tabBars.buttons["Oturumlar"].waitForExistence(timeout: 30))
        attach(app, "04-hosts")

        // 3) Host detail lists the tmux session created by the script.
        let host = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'host-'")).firstMatch
        XCTAssertTrue(host.waitForExistence(timeout: 10))
        host.tap()
        let tmux = app.descendants(matching: .any)["tmux-pa-e2e"]
        let found = tmux.waitForExistence(timeout: 20)
        attach(app, "05a-host-detail")
        XCTAssertTrue(found)
        attach(app, "05-tmux-list")

        // 4) Attach and type a command; the script checks it ran on the host.
        tmux.tap()
        XCTAssertTrue(app.descendants(matching: .any)["key-esc"].waitForExistence(timeout: 15))
        sleep(4)
        app.typeText("echo ios-e2e-ok > \(e2eDir)/typed.txt\n")
        sleep(2)
        attach(app, "06-terminal")
        XCTAssertEqual(read("typed.txt", timeout: 10), "ios-e2e-ok")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // 5) SFTP: open files, navigate to /tmp/pa-e2e, preview a file.
        app.descendants(matching: .any)["openFiles"].tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'file-'")).firstMatch.waitForExistence(timeout: 15))
        attach(app, "07-files")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // 6) Inbox shows the approval the script posted; approve it.
        app.tabBars.buttons["Gelen"].tap()
        let approve = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'approve-'")).firstMatch
        if !approve.waitForExistence(timeout: 20) {
            app.swipeDown()
        }
        XCTAssertTrue(approve.waitForExistence(timeout: 20))
        attach(app, "08-inbox")
        approve.tap()
        XCTAssertTrue(app.staticTexts["Karar verildi"].waitForExistence(timeout: 10))
        attach(app, "09-approved")

        // 7) Settings shows the device key and a valid backend token.
        app.tabBars.buttons["Ayarlar"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["deviceFingerprint"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Geçerli"].waitForExistence(timeout: 10))
        attach(app, "10-settings")
    }
}
