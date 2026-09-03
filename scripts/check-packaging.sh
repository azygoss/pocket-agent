#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P00 gate: docs/reference must never be packaged.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "[check-packaging] ..."
if grep -R 'docs/reference' --include='*.gradle*' --include='Dockerfile*' --include='*.yml' --include='*.yaml' apps backend deploy 2>/dev/null | grep -v '^Binary'; then
  echo "FAIL: docs/reference referenced by packaging" >&2; exit 1
fi
if [ -f .gitattributes ]; then
  grep -q 'docs/reference.*export-ignore' .gitattributes || { echo "FAIL: .gitattributes must export-ignore docs/reference" >&2; exit 1; }
fi
echo "[check-packaging] OK"
