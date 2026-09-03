#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P17: fail if canary secrets appear in logs/pcap captures.
set -euo pipefail
if grep -R "POCKET_CANARY" --exclude-dir=.git . 2>/dev/null | grep -v "tests/security/canary.sh" | grep -v "docs/security/privacy.md"; then
  echo "FAIL: canary leak"; exit 1
fi
echo "[canary] OK (no leaks in tree)"
