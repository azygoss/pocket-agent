#!/usr/bin/env bash
# P00 gate: SPDX identifiers + NOTICE presence.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "[license-check] ..."
test -f LICENSE || { echo "FAIL: LICENSE missing" >&2; exit 1; }
test -f NOTICE || { echo "FAIL: NOTICE missing" >&2; exit 1; }
grep -q 'GPL-3.0-or-later' LICENSE || { echo "FAIL: LICENSE must mention GPL-3.0-or-later" >&2; exit 1; }
# every new source file should carry SPDX (warn-only in P00, enforcing from P01)
missing=$(grep -rL 'SPDX-License-Identifier' --include='*.go' --include='*.kt' --include='*.sh' \
  --exclude-dir=.git --exclude-dir=build . 2>/dev/null || true)
if [ -n "$missing" ]; then
  echo "WARN (P00): files without SPDX:"; echo "$missing"
fi
echo "[license-check] OK"
