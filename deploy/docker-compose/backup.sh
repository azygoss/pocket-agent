#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P02 backup/restore skeleton (pg_dump + volume note).
set -euo pipefail
cd "$(dirname "$0")"
echo "[backup] pg_dump > backup.sql (DB must be running)"
docker compose exec -T db pg_dump -U pocket pocket > "backup-$(date +%F).sql"
echo OK
