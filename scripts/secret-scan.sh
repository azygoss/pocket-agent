#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P00 gate: fail if Moshi brand/code/assets leak into repo (plan.md links excluded).
set -euo pipefail
cd "$(dirname "$0")/.."
echo "[secret-scan] secret + brand leak check..."
# quick secret patterns (exclude self to avoid self-match on pattern text)
if grep -RInE 'AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|xox[bpas]-|-----BEGIN (RSA )?PRIVATE KEY-----|sk-live-' \
  --exclude-dir=.git --exclude-dir=build --exclude='*.log' --exclude='secret-scan.sh' . ; then
  echo "FAIL: possible secret in tree" >&2; exit 1
fi
# Moshi code/asset copy check: allow plan.md, feature-matrix/README links, decisions.tsv rationale
if rg -i 'moshi' --glob '!.git/' --glob '!plan.md' --glob '!decisions.tsv' --glob '!docs/reference/**' --glob '!README.md' --glob '!scripts/secret-scan.sh' . ; then
  echo "FAIL: unexpected 'moshi' reference outside allowed files" >&2; exit 1
fi
echo "[secret-scan] OK"
