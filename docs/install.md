# Kurulum (P18)
## Backend (self-hosted)
```bash
cp deploy/docker-compose/.env.example deploy/docker-compose/.env  # POSTGRES_PASSWORD + DOMAIN doldur
docker compose -f deploy/docker-compose/docker-compose.yml up -d --build
curl https://$DOMAIN/v1/healthz  # ok
```
## CLI (host)
```bash
tar xzf dist/pocket-agent-0.1.0-linux-amd64.tar.gz
./pocket-agent-hook-linux-amd64 service install
./pocket-agent-hook-linux-amd64 doctor
./pocket-agent-hook-linux-amd64 host setup  # QR tara
```
## Android
`apps/android`: `./gradlew :app:assembleDebug` (debug) veya upload keystore ile `:app:bundleRelease`.
Temiz kurulum + QR Easy Pair; eski kurulum otomatik taşınmaz.
