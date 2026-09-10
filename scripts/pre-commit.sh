#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# pre-commit hook — hızlı kapılar. Kurulum:
#   git config core.hooksPath .githooks   (veya bu dosyayı .git/hooks/pre-commit'e bağla)
set -euo pipefail
cd "$(dirname "$0")/.."

echo "pre-commit: gofmt"
out=$(gofmt -l ./cmd ./host ./backend ./protocol ./tests 2>/dev/null || true)
[ -z "$out" ] || { echo "gofmt gerekli:"; echo "$out"; exit 1; }

echo "pre-commit: secret-scan"
./scripts/secret-scan.sh >/dev/null

echo "pre-commit: license-check"
./scripts/license-check.sh >/dev/null

echo "pre-commit: go vet"
go vet ./...

echo "pre-commit: hızlı go test"
go test ./host/... ./backend/... -count=1 >/dev/null

echo "pre-commit: OK"
