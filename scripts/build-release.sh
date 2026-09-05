#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P18: tam release akışı — kapılar + Go testler + Android lint/test/release
# (APK+AAB imzalı) + checksum raporu. Upload keystore yalnız env'den okunur;
# env yoksa release debug anahtarıyla imzalanır (CI doğrulaması).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"

./scripts/secret-scan.sh && ./scripts/license-check.sh && ./scripts/check-packaging.sh
buf lint
go vet ./... && go test ./...

cd apps/android
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
./gradlew :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease :app:bundleRelease

APK=app/build/outputs/apk/release/app-release.apk
AAB=app/build/outputs/bundle/release/app-release.aab
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose --print-certs "$APK"

cd "$ROOT"
mkdir -p dist
{
  echo "# Pocket Agent release checksums ($(git rev-parse --short HEAD))"
  sha256sum "apps/android/$APK" "apps/android/$AAB"
  [ -f native/mosh/SHA256SUMS ] && cat native/mosh/SHA256SUMS
} > dist/release-checksums.txt

if [ -z "${POCKET_AGENT_UPLOAD_KEYSTORE:-}" ]; then
  echo "[release] UYARI: POCKET_AGENT_UPLOAD_* env yok — artefaktlar DEBUG anahtarıyla imzalı (dağıtım için değil)"
fi
echo "[release] OK — rapor: dist/release-checksums.txt"
