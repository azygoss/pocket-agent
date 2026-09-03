#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P18: minimal SBOM (go modules + base images). Full CycloneDX via syft in CI release job.
set -euo pipefail
cd "$(dirname "$0")/.."
go list -m -json all > dist/sbom-go.json 2>/dev/null || go list -m all > dist/sbom-go.txt
cat > dist/sbom-images.txt <<'IEOF'
postgres:16-alpine
caddy:2-alpine
golang:1.22-alpine (build)
alpine:3.20 (runtime)
IEOF
echo "modules: $(go list -m all 2>/dev/null | wc -l), files in dist:"; ls dist/ | head
