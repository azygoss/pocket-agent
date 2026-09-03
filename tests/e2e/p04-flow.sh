#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P04 e2e (headless, no emulator): store race + pairing file ops + CLI smoke.
set -euo pipefail
cd "$(dirname "$0")/../.."
go test ./backend/internal/store/ -run 'TestPairingRace|TestTTLSweep' -v 2>&1 | tail -n 4
go test ./host/pairing/ -v 2>&1 | tail -n 5
export POCKET_HOME=$(mktemp -d)
go run ./cmd/pocket-agent-hook host setup 2>/dev/null | grep -q '^pa1|' && echo "[e2e] QR payload OK"
go run ./cmd/pocket-agent-hook hooks install >/dev/null && echo "[e2e] hooks install OK"
echo "[e2e P04] OK"
