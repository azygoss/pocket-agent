#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P18: CLI release tarballs (linux/darwin x amd64/arm64). Windows experimental.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p dist
VER="${VER:-0.1.0}"
for os in linux darwin; do for arch in amd64 arm64; do
  echo "building $os/$arch..."
  GOOS=$os GOARCH=$arch go build -trimpath -o "dist/pocket-agent-hook-$os-$arch" ./cmd/pocket-agent-hook
  tar -czf "dist/pocket-agent-$VER-$os-$arch.tar.gz" -C dist "pocket-agent-hook-$os-$arch"
done; done
GOOS=windows GOARCH=amd64 go build -trimpath -o "dist/pocket-agent-hook-windows-amd64.exe" ./cmd/pocket-agent-hook || echo "windows experimental: skipped on failure"
sha256sum dist/* > dist/SHA256SUMS 2>/dev/null || true
ls -la dist/
