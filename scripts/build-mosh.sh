#!/usr/bin/env bash
# P07: mosh-client'ı kaynaktan, ABI başına NDK ile derler.
# Upstream: github.com/mobile-shell/mosh `android` branch (connectbot/mosh4android ile aynı kaynak).
# Çıktı: apps/android/app/src/main/jniLibs/<abi>/libmoshclient.so
#   (Android 10+ W^X: app-home'dan exec yasak; jniLibs içindeki lib*.so
#   nativeLibraryDir'e çıkarılır ve çalıştırılabilir — Termux yöntemi.)
# Reproducible: sabit ref + derleme sonrası SHA256 loglanır.
set -euo pipefail

REF="${MOSH_REF:-2de58be90449bfee4041c5d798f921f84d10dc0b}" # android branch HEAD (2026-09)
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK_DIR:-$ROOT/build/mosh}"
SRC="$WORK/mosh-src"

mkdir -p "$WORK"
if [[ ! -d "$SRC/.git" ]]; then
  git clone --branch android https://github.com/connectbot/mosh4android "$SRC"
fi
git -C "$SRC" fetch --depth 1 origin "$REF" || git -C "$SRC" fetch origin "$REF"
git -C "$SRC" checkout --detach "$REF"

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
ABIS="$ABIS" WORK_DIR="$WORK/out" "$SRC/android/build-android-release-assets.sh"

# Paketle: executable → strip → jniLibs/libmoshclient.so + terminfo
STRIP="$ANDROID_SDK_ROOT/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
DEST="$ROOT/apps/android/app/src/main/jniLibs"
for abi in $ABIS; do
  zip="$WORK/out/mosh-android-$abi.zip"
  [[ -f "$zip" ]] || zip="$WORK/out/package-$abi/mosh-android-$abi.zip"
  [[ -f "$zip" ]] || { echo "missing $zip" >&2; exit 1; }
  tmp="$(mktemp -d)"
  unzip -q "$zip" -d "$tmp"
  mkdir -p "$DEST/$abi" "$ROOT/apps/android/app/src/main/assets"
  "$STRIP" "$tmp/mosh-client" -o "$DEST/$abi/libmoshclient.so"
  cp "$tmp/terminfo.zip" "$ROOT/apps/android/app/src/main/assets/terminfo.zip"
  sha256sum "$DEST/$abi/libmoshclient.so" | tee -a "$WORK/mosh-hashes.txt"
  rm -rf "$tmp"
done
echo "mosh hashes -> $WORK/mosh-hashes.txt"
