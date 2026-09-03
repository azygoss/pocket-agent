#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
cd "$(dirname "$0")"
f="${1:?usage: restore.sh backup-YYYY-MM-DD.sql}"
docker compose exec -T db psql -U pocket pocket < "$f"
echo OK
