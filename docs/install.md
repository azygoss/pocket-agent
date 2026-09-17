# Kurulum (P18)

5 dakikalık akış: backend → host → Android.

## 1) Backend (self-hosted)

```bash
cd deploy/docker-compose
./bootstrap.sh        # .env yoksa interaktif doldurur, compose up, healthz bekler
```

Elle tercih edersen:

```bash
cp .env.example .env   # POSTGRES_PASSWORD + DOMAIN doldur
docker compose up -d --build
curl https://$DOMAIN/v1/healthz   # ok
```

Yedekleme: `./backup.sh` → `backup-YYYY-MM-DD.sql`. Geri yükleme:
`./restore.sh <dosya>`. Yedeğin gerçekten açıldığını doğrula:
`./verify-backup.sh` (geçici Postgres'te restore dener).

## 2) Host (pocket-agent CLI)

```bash
npm install -g pocket-agent-cli
pocket-agent onboard          # tek komut: daemon+gateway+hooks+QR
```

Node.js 18+ gerekir. Paket Linux/macOS x64-arm64 ve Windows x64 binary'lerini içerir; hostta Go gerekmez. Tarball ile çevrimdışı kurulum alternatifi:

```bash
tar xzf dist/pocket-agent-*-linux-amd64.tar.gz
./pocket-agent-hook-linux-amd64 onboard
```

`onboard` şunları yapar (hepsi idempotent):

- backend URL'i config'e yazar (`--backend url` ile override)
- `pocket-agent.service` + `pocket-agent-gateway.service` user unit'leri kurar
- 12 agent hook bloğunu config dosyalarına merge eder (tekrar koşuda dubl yok)
- eşleştirme QR'ı + XXXX-XXXX kodu basar (5dk TTL, tek kullanım)

Adım adım istersen: `service install` → `service install-gateway` →
`hooks install` → `pair`. Tanı: `doctor` (sshd/port/disk/backend sondaları,
`--json` destekli). Kabuk tamamlama: `source <(pocket-agent completion bash)`.

## 3) Android

`apps/android`: `./gradlew :app:assembleDebug` (debug) veya upload keystore ile
`scripts/build-release.sh` (imzalı APK+AAB).

Telefonda: Bağlantılar → QR ile bağlan (veya XXXX-XXXX kodu gir). Temiz kurulum
gerekir; eski sürümlerden otomatik taşıma yok.

## Sorun giderme

| Belirti | Çare |
|---|---|
| `pair` "backend erişilemedi" | `pocket-agent set backend_url http://<host>:8080` sonra tekrar |
| Gateway 401 | token dosyası `~/.config/pocket-agent/gateway.token` — uygulama SFTP ile okur |
| `doctor` sshd FAIL | `openssh-server` kurulu ve :22 dinliyor olmalı |
| Kod elle girilemiyor | Ayarlar → Backend URL önce girilmeli |
