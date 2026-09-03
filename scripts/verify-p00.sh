#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P00 verifier: runs the three gates + git sanity.
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/secret-scan.sh
./scripts/license-check.sh
./scripts/check-packaging.sh
git log --oneline -3 2>/dev/null || echo "(no commits yet)"
git status --short || true
echo "[verify P00] OK"
