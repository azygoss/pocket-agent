#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# npm host CLI paketi: node wrapper + 5 platform Go binary'si → dist/*.tgz.
set -euo pipefail
cd "$(dirname "$0")/.."

VER=${VER:-0.1.0}
PKGVER=$(node -p "require('./packages/npm/package.json').version")
if [ "$PKGVER" != "$VER" ]; then
  echo "package-npm: packages/npm/package.json version=$PKGVER, VER=$VER ile uyuşmuyor" >&2
  exit 1
fi

STAGE=dist/npm/pocket-agent-cli
rm -rf "$STAGE"
mkdir -p "$STAGE/bin" "$STAGE/vendor"
cp packages/npm/package.json LICENSE "$STAGE/"
cp packages/npm/bin/pocket-agent.js "$STAGE/bin/"

npm test --prefix packages/npm

build() { # $1=GOOS $2=GOARCH $3=vendor binary adı
  echo "building $1/$2 → vendor/$3"
  CGO_ENABLED=0 GOOS=$1 GOARCH=$2 go build -trimpath \
    -ldflags "-s -w -X main.version=$VER" \
    -o "$STAGE/vendor/$3" ./cmd/pocket-agent-hook
}
build linux   amd64 pocket-agent-linux-amd64
build linux   arm64 pocket-agent-linux-arm64
build darwin  amd64 pocket-agent-darwin-amd64
build darwin  arm64 pocket-agent-darwin-arm64
build windows amd64 pocket-agent-windows-amd64.exe

npm pack "$STAGE" --pack-destination dist
echo "dist/pocket-agent-cli-$VER.tgz"
