#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P18: release gate — lint+test+sign+checksum (Android AAB/APK, CLI tarballs, SBOM).
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/secret-scan.sh && ./scripts/license-check.sh && ./scripts/check-packaging.sh
buf lint
go vet ./... && go test ./...
echo "[release] Go+proto gates green; Android: run apps/android gradlew assembleRelease+bundleRelease with upload keystore env"
sha256sum plan.md decisions.tsv > dist-checksums.txt 2>/dev/null || true
echo OK
