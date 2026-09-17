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
# kişisel değerler yalnız tracked içerikte taranır (ignore'lu yerel kopyalar sayılmaz)
if git grep -nE '169\.58\.192\.80' -- . >/dev/null 2>&1; then
  echo "FAIL: personal IP in tracked content" >&2; exit 1
fi
# iç geliştirme kayıtları public repoda tracked olmamalı
if git ls-files | grep -E '^(HANDOFF\.md|plan\.md|decisions\.tsv|environment\.yaml|apps/android/README\.md|assets/icon-source\.jpg|assets/icon-v2\.png|native/et/|web/diff-viewer/|.*\.log)$' ; then
  echo "FAIL: internal file tracked in public tree" >&2; exit 1
fi
# Moshi code/asset copy check: allow plan.md, feature-matrix/README links, decisions.tsv rationale
# rg yoksa (CI runner) grep'e düş — yoksa `command not found` if-false olur ve
# tarama sessizce hiç koşmaz.
if command -v rg >/dev/null 2>&1; then
  if rg -i 'moshi' --glob '!.git/' --glob '!plan.md' --glob '!decisions.tsv' --glob '!docs/reference/**' --glob '!README.md' --glob '!scripts/secret-scan.sh' . ; then
    echo "FAIL: unexpected 'moshi' reference outside allowed files" >&2; exit 1
  fi
else
  if grep -rIni 'moshi' --exclude-dir=.git --exclude-dir=build --exclude-dir=reference \
    --exclude=plan.md --exclude=decisions.tsv --exclude=README.md --exclude=secret-scan.sh . ; then
    echo "FAIL: unexpected 'moshi' reference outside allowed files" >&2; exit 1
  fi
fi
echo "[secret-scan] OK"
