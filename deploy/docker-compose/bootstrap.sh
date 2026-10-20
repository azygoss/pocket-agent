#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# bootstrap.sh — self-hosted backend tek komut kurulumu.
# .env yoksa interaktif doldurur, compose'u kaldırır, healthz bekler.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v docker >/dev/null; then
    echo "hata: docker yok — https://docs.docker.com/engine/install" >&2; exit 1
fi
docker compose version >/dev/null 2>&1 || {
    echo "hata: docker compose plugin yok" >&2; exit 1; }

if [ ! -f .env ]; then
    echo "== Pocket Agent backend kurulumu =="
    # Telefon yalnız HTTPS'e bağlanır (düz HTTP engelli). Domain yoksa genel
    # IP'den nip.io adı önerilir: 1.2.3.4 → 1-2-3-4.nip.io (Let's Encrypt alır).
    ip=$(curl -s -m 5 https://api.ipify.org 2>/dev/null || true)
    suggest=localhost
    [ -n "$ip" ] && suggest="${ip//./-}.nip.io"
    read -r -p "Domain (örn. api.example.com; boş bırakırsan $suggest): " domain
    domain=${domain:-$suggest}
    pass=$(openssl rand -hex 16 2>/dev/null || head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
    enroll=$(openssl rand -hex 16 2>/dev/null || head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')
    cat > .env <<EOF
POSTGRES_PASSWORD=$pass
DOMAIN=$domain
POCKET_ENROLL_TOKEN=$enroll
EOF
    chmod 600 .env
    echo "  .env yazıldı (POSTGRES_PASSWORD üretildi, DOMAIN=$domain)"
else
    echo ".env mevcut — atlanıyor"
fi

# Host kaydı enroll token'ı olmadan herkese açık olur: eski .env'lere ekle.
if ! grep -q '^POCKET_ENROLL_TOKEN=.' .env; then
    echo "POCKET_ENROLL_TOKEN=$(openssl rand -hex 16 2>/dev/null || head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n')" >> .env
    echo "  POCKET_ENROLL_TOKEN üretildi (.env)"
fi
if grep -q 'change-me' .env; then
    echo "uyarı: .env'de varsayılan parola duruyor — production'da değiştir" >&2
fi

echo "== compose up =="
docker compose up -d --build

echo "== healthz bekleniyor =="
domain=$(grep '^DOMAIN=' .env | cut -d= -f2)
url="http://127.0.0.1:8080/v1/healthz"
[ "$domain" != "localhost" ] && url="https://$domain/v1/healthz"
for i in $(seq 1 30); do
    if curl -sfk "$url" >/dev/null 2>&1 || curl -sf http://127.0.0.1:8080/v1/healthz >/dev/null 2>&1; then
        echo "  ✓ backend sağlıklı: $url"
        break
    fi
    [ "$i" = 30 ] && { echo "  ✗ healthz 60s içinde gelmedi — docker compose logs api"; exit 1; }
    sleep 2
done

cat <<EOF

Kurulum tamam. Sıradaki adımlar:
  1) Host'ta:  POCKET_ENROLL_TOKEN=$(grep '^POCKET_ENROLL_TOKEN=' .env | cut -d= -f2) \\
               pocket-agent onboard --backend https://$domain
  2) Telefonda: Oturumlar → QR simgesi → kodu gir ya da QR'ı tara.
     Telefon kendi SSH anahtarını üretir; host'ta cihazı onayla.
     Backend token'ı eşlemeyle otomatik gelir.

Yedekleme: ./backup.sh   Geri yükleme: ./restore.sh <dosya>   Doğrulama: ./verify-backup.sh
EOF
