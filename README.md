# Pocket Agent

[![CI](https://github.com/azygoss/pocket-agent/actions/workflows/ci.yml/badge.svg)](https://github.com/azygoss/pocket-agent/actions)
[![npm](https://img.shields.io/npm/v/pocket-agent-cli)](https://www.npmjs.com/package/pocket-agent-cli)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

Self-hosted Android terminal and agent companion. SSH from your phone into
your own host, manage tmux sessions, browse files over SFTP, and answer agent
approvals from your pocket — no third-party cloud. Terminal and file traffic
flows over your direct SSH connection.

The backend only ever sees agent event summaries and approval metadata;
terminal output, file contents, diffs, and chat never reach it.

## What it does

- **Real SSH terminal** — TOFU host-key pinning, ed25519/RSA key auth, full
  ANSI/VT parser (alternate screen, scroll regions, synchronized output,
  DSR/DA/DECRQM queries); works with Codex, vim, htop, tmux, and other TUIs
- **Persistent tmux sessions** — named sessions, multi-session tabs,
  reconnect-attach; sessions survive connection drops on the host
- **SFTP file manager** — list, read, write, download, and share
- **Gateway + workspace** — workspace files, git diffs, and HTTP preview
  tunneled over SSH loopback (jailed, token-authenticated)
- **Agent hooks** — Claude/Codex event stream: notifications, approval
  requests, chat-style output, 24h-TTL summary inbox
- **Android UX** — themes, fonts, pinch-zoom, OSC52 clipboard, OSC8
  hyperlinks, key bar, multiple terminal tabs
- **Mosh bootstrap** — `mosh-server` is packaged for the host; the roaming
  client side is experimental (see Status and limits)

## Architecture and data boundary

```
Android app ──SSH/PTY+SFTP──► Host (sshd + tmux + gateway :loopback)
    │                            │
    └──HTTPS──► Backend ◄──flusher──┘ pocket-agent daemon
                (event summaries,       (journal → summary POST)
                 approvals, inbox)
```

| Sent to the backend | **Never** sent to the backend |
|---|---|
| Agent event summary (source, category, message) | Terminal output / keystrokes |
| Approval request + decision metadata | File contents, diffs, chat |
| Pairing claim, tenant boundary | SSH keys, gateway token |

The backend stores summaries with a 24-hour TTL; tokens are stored hash-only.

## Quick install

Requirements: Node 18+ on the host (for npm install) plus sshd; Docker +
Compose for the backend; Android 10+ (minSdk 29).

```bash
# 1) Backend (optional but recommended — powers agent events/approvals)
cd deploy/docker-compose && cp .env.example .env   # fill in your domain
docker compose up -d                               # or ./bootstrap.sh

# 2) Host CLI — no Go toolchain needed, binaries ship in the package
npm install -g pocket-agent-cli
pocket-agent onboard --backend https://<your-domain>

# 3) Android APK
# https://github.com/azygoss/pocket-agent/releases/latest → app-release.apk
# In the app, scan the QR code or enter the XXXX-XXXX pairing code
```

The npm package ships Linux/macOS x64 + arm64 and Windows x64 (experimental)
binaries; it installs the `pocket-agent` and `pocket-agent-hook` commands.

## Everyday CLI commands

| Command | What it does |
|---|---|
| `pocket-agent onboard` | Daemon + gateway + hooks + pair QR in one step |
| `pocket-agent pair / unpair` | Generate QR + XXXX-XXXX code / remove pairing |
| `pocket-agent doctor --json` | sshd, port, disk, backend checks |
| `pocket-agent status --json` | Version + health summary |
| `pocket-agent hooks install` | Register Claude/Codex hooks in their config |
| `pocket-agent service install` | systemd user units (daemon + gateway) |
| `pocket-agent servers` | List active tmux sessions |
| `pocket-agent gateway serve` | File/diff server (loopback only) |
| `pocket-agent completion bash` | Shell completion (bash/zsh/fish) |
| `pocket-agent version` | Version |

Full list: `pocket-agent help`.

## Android features

- **Terminal**: full-screen VT100/xterm emulation, alternate screen,
  atomic TUI frames via synchronized output (DECSET 2026), 50k-line
  scrollback, pinch-zoom, selection + OSC52 copy
- **Connection**: SSH keepalive, automatic reconnect, hard-stop warning on
  TOFU pin change, experimental Mosh roaming
- **Sessions**: named tmux sessions, session cards, processes that keep
  running on the host even when the link drops
- **Files**: SFTP browser, upload/download/share, workspace + git diff
  view, HTTP preview
- **App**: Material 3 theming, font/size settings, key bar, agent
  notifications and approval dialogs

## Build from source

Requirements: Go (version in go.mod), JDK 17, Android SDK 34 + NDK/CMake,
Buf (for proto lint).

```bash
sudo ./scripts/setup-dev-environment.sh   # toolchain (one time)
source ./scripts/dev-env.sh
./scripts/verify-dev-environment.sh

make gates        # secret-scan + license + packaging + buf lint + canary
make go           # go test + vet + build
make android      # lintDebug + testDebugUnitTest + assembleDebug
make live-up && make live-test   # env-gated live suites (SSH/SFTP/gateway)
make npm-package  # dist/pocket-agent-cli-*.tgz
```

Artifacts: `apps/android/app/build/outputs/apk/debug/app-debug.apk`,
`dist/` (CLI tarballs + checksums).

## Repository layout

| Path | Contents |
|---|---|
| `apps/android/` | Kotlin + Compose app (ui/ transport/ data/ net/ service/ security/) |
| `cmd/pocket-agent-hook/` + `host/` | Go CLI + daemon (pairing, gateway, hooks, journal, ssh, tmux) |
| `backend/` | Go API (api/ approvals/ auth/ inbox/ store) + PostgreSQL |
| `protocol/` | Buf v2 proto contracts (v1 frozen) |
| `deploy/docker-compose/` | Backend + Caddy deployment, backup/restore |
| `packages/npm/` | npm package (`pocket-agent-cli`) |
| `native/mosh/` | Mosh source/build metadata + `libmoshclient.so` hashes |
| `scripts/` `tests/` | Gates, packaging, e2e/fuzz/perf/protocol tests |
| `docs/` | Install, security, backup, rollback docs |

## Security model

- **SSH TOFU** — the host key is pinned on first connect; a pin change
  hard-stops and requires explicit user approval
- **Keys** — Android Keystore-backed; private keys are never written to
  disk in plaintext and no secrets live in this repo (`make gates` scans)
- **Gateway** — loopback-only bind, 0600 token file, workspace jail,
  path-traversal and SSRF protection
- **Backend** — hash-only tokens, single-use short-TTL pairing codes,
  tenant isolation enforced on every query, 24h summary TTL
- **Signing** — CLI `update` accepts only signed manifests; release APKs
  are verified with `apksigner verify`. Debug APKs are not release-signed.

Details: [`docs/security/privacy.md`](docs/security/privacy.md),
[`threat-model.md`](docs/security/threat-model.md),
[`incident-response.md`](docs/security/incident-response.md).

## Status and limits

- Working and live-tested: SSH/PTY, SFTP, tmux, gateway, pairing,
  backend event/approval flow, npm CLI install
- **Mosh**: host bootstrap + native client packaged; real-device roaming
  field testing is still in progress
- **ET (Eternal Terminal)**: deferred
- Emulator/device-dependent tests (frame pacing, biometrics, some a11y
  flows) are partially covered in CI via Robolectric
- A production backend requires TLS + a domain; `network_security_config`
  only permits cleartext to localhost/emulator addresses
- Current versions: Android `v0.31.0`, npm CLI `0.1.0`

## Testing

```bash
make gates && make go && make android   # full gate set
make live-up && make live-test          # against a real sshd/backend/gateway
```

Live suites are env-gated (`PA_LIVE_SSH`, `PA_LIVE_BACKEND`, `PA_LIVE_GW`);
`scripts/live-env.sh` sets up the local fixture (test user, loopback
gateway, preview server).

## Contributing

Rules and test layers: [`CONTRIBUTING.md`](CONTRIBUTING.md).
Reproducible builds and hash verification: [`REPRODUCING.md`](REPRODUCING.md).
Third-party component list: [`NOTICE`](NOTICE).

Clean-room rule: no brand, code, or assets are copied from reference
products; `docs/reference/` only contains links to publicly available
behavioral sources and never ships in any release artifact.

## License

`GPL-3.0-or-later` — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
Upstream Mosh/ET sources under `native/` keep their own licenses.
