#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# verify-backup.sh — en son (veya verilen) backup'ın gerçekten restore
# edilebilir olduğunu doğrular: geçici container'a yükler, tablo sayar.
set -euo pipefail
cd "$(dirname "$0")"

f="${1:-$(ls -t backup-*.sql 2>/dev/null | head -1 || true)}"
[ -n "$f" ] && [ -f "$f" ] || { echo "hata: backup dosyası yok (usage: verify-backup.sh [dosya])" >&2; exit 1; }

echo "[verify] $f → geçici postgres"
cid=$(docker run -d --rm -e POSTGRES_USER=pocket -e POSTGRES_PASSWORD=verify -e POSTGRES_DB=pocket postgres:16-alpine)
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true' EXIT

for i in $(seq 1 30); do
    docker exec "$cid" pg_isready -U pocket >/dev/null 2>&1 && break
    [ "$i" = 30 ] && { echo "postgres açılmadı"; exit 1; }
    sleep 1
done

docker exec -i "$cid" psql -U pocket pocket < "$f" >/dev/null
tables=$(docker exec "$cid" psql -U pocket pocket -tAc \
    "select count(*) from information_schema.tables where table_schema='public'")
echo "[verify] OK — $f restore edildi, $tables tablo"
