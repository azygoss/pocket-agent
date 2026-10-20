# Pocket Agent for iOS

SwiftUI client with the same protocol as the Android app. It provides:

- Keyless pairing: QR scan, photo, paste or manual code.
- An Ed25519 key generated on device and stored in the Keychain (`ThisDeviceOnly`).
- SSH terminal (SwiftTerm) with a key bar, tmux session list, attach and kill.
- SFTP browsing, preview, upload, rename and delete.
- Agent inbox with approvals.
- TOFU host-key pinning.

## Build

```sh
brew install xcodegen
cd apps/ios && xcodegen generate
open PocketAgent.xcodeproj        # or:
xcodebuild test -scheme PocketAgent -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PocketAgentTests
```

## End-to-end (Simulator)

`scripts/e2e.sh` starts the following, then drives the app with XCUITest:

- the real backend (Postgres if `PA_TEST_DATABASE_URL`);
- an unprivileged sshd on 127.0.0.1:2222;
- a tmux session;
- `pocket-agent pair --yes`.

The test covers pair → TOFU → tmux attach → typing a command that runs on the host → SFTP → approving an inbox event → settings. Screenshots land in `/tmp/pa-e2e/shots`.

## Release

Things to set before the first upload:

1. Copy your team ID into `Config/Signing.local.xcconfig` (`DEVELOPMENT_TEAM = XXXXXXXXXX`; gitignored). Change `POCKET_BUNDLE_ID` in `Config/Shared.xcconfig` if `dev.pocketagent.ios` is taken.
2. Bump `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `Config/Shared.xcconfig`.

Then archive and upload: Product → Archive → Distribute → App Store Connect, or `xcodebuild archive -scheme PocketAgent -configuration Release`.

What is already in the project:

- `PrivacyInfo.xcprivacy`: no tracking, no collected data.
- `ITSAppUsesNonExemptEncryption = NO`: standard SSH/TLS only. Confirm this for your export situation.
- App icon (`scripts/make-icon.swift` regenerates it).

App Store review notes: the app needs a self-hosted backend and SSH host. Give reviewers a demo backend URL plus a pairing code, or a screen recording.
