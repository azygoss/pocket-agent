#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P18: GPL "Complete Corresponding Source" (CCS) paketi.
# İçerik: repo kaynak arşivi (git archive) + native build bilgisi/hashler
# (mosh NDK zinciri, D014) + bundle font lisansları (OFL).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=dist/ccs
rm -rf "$OUT" && mkdir -p "$OUT"

TAG=$(git rev-parse --short HEAD)
git archive --format=tar.gz -o "$OUT/pocket-agent-src-${TAG}.tar.gz" HEAD

mkdir -p "$OUT/native"
[ -f native/mosh/SHA256SUMS ] && cp native/mosh/SHA256SUMS "$OUT/native/"
[ -f native/mosh/BUILD-INFO.txt ] && cp native/mosh/BUILD-INFO.txt "$OUT/native/"
cp scripts/build-mosh.sh "$OUT/native/"

mkdir -p "$OUT/fonts"
[ -f apps/android/app/src/main/assets/licenses/JetBrainsMono-OFL.txt ] && \
  cp apps/android/app/src/main/assets/licenses/JetBrainsMono-OFL.txt "$OUT/fonts/"

cat > "$OUT/README.md" <<EOF
# Pocket Agent CCS paketi (commit ${TAG})

GPL-3.0-or-later kapsamında Complete Corresponding Source:

- pocket-agent-src-${TAG}.tar.gz — tam kaynak (git archive HEAD)
- native/ — mosh-client NDK derleme betiği + toolchain bilgisi + SHA256 pinleri
  (upstream: connectbot/mosh4android @ android dalı, bkz. BUILD-INFO.txt)
- fonts/ — JetBrains Mono 2.304 OFL-1.1 lisans metni

Yeniden üretme adımları: REPRODUCING.md
EOF

echo "[ccs] $OUT hazır:"; ls -R "$OUT" | head -20
