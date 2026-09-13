#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# APK sürümü yayınla: GitHub Releases asset'i + VPS indirme dizini.
# Kullanım: ./scripts/release-apk.sh v0.27.0 "sürüm notları"
# Önkoşul: dist/pocket-agent-<ver>-debug.apk hazır ve git push edilmiş.
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:?kullanım: release-apk.sh <vX.Y.Z> [notlar]}"
VER="${TAG#v}"
APK="dist/pocket-agent-${VER}-debug.apk"
NOTES="${2:-$TAG}"
[ -f "$APK" ] || { echo "yok: $APK" >&2; exit 1; }

# 1) GitHub Releases (uygulama içi güncellemenin baktığı yer)
gh release create "$TAG" "$APK" --title "$TAG" --notes "$NOTES"

# 2) VPS dağıtım sayfası (yedek kanal — index.html kartı ayrıca güncellenir)
SRV=/srv/pocket-agent-apk
cp "$APK" "$SRV/"
(cd "$SRV" && sha256sum "$(basename "$APK")" >> SHA256SUMS)
(cd dist && sha256sum "$(basename "$APK")" >> SHA256SUMS)

echo "yayınlandı: $TAG → github + $SRV"
