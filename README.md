# Pocket Agent

Self-hosted Android terminal + agent companion. Clean-room implementation — no Moshi code, brand, text, or assets.

Telefondan SSH ile host'una bağlan, tmux oturumlarını yönet, agent onaylarını
cebinden karşıla, workspace dosyalarına ve git diff'lerine eriş — hepsi
kendi sunucun üzerinden, üçüncü taraf bulut yok.

> Plan: [`plan.md`](plan.md) (v2 — tek doğruluk kaynağı). Durum: [`HANDOFF.md`](HANDOFF.md).

## Üç parça

- `apps/android/` — Kotlin + Compose, minSdk 29 (terminal, SFTP, workspace, chat)
- `cmd/pocket-agent-hook/` + `host/` — Go CLI + daemon (pairing, gateway, hooks)
- `backend/` + `deploy/docker-compose/` — Go API + PostgreSQL + Caddy

## Hızlı başlangıç

```bash
make help          # tüm hedefler
make gates         # repo kapıları (secret/license/packaging/buf/canary)
make go            # go test + vet + build
make android       # lintDebug + testDebugUnitTest + assembleDebug
make live-up       # canlı test ortamı (backend :8080, gateway :24543, preview :8899)
```

İlk kurulum (toolchain):

```bash
sudo ./scripts/setup-dev-environment.sh
source ./scripts/dev-env.sh
./scripts/verify-dev-environment.sh
```

## Kullanıcı olarak kurulum

1. **Backend**: `deploy/docker-compose`'ta `.env` doldur → `docker compose up -d`
   (ayrıntı: `docs/install.md`, tek komutluk kurulum: `deploy/docker-compose/bootstrap.sh`)
2. **Host**: `pocket-agent onboard` — daemon + gateway + agent hook'ları + eşleştirme QR'ı
3. **Android**: QR'ı tara ya da XXXX-XXXX kodu gir — bitti.

## Clean-room kuralı

- Moshi APK'sı decompile edilmez, trafiği MITM'lenmez.
- Moshi adı/logo/metin/asset/protokol/kod kopyalanmaz.
- Yalnızca herkese açık davranışsal kaynaklar: Play Store açıklaması, getmoshi.app/docs, /privacy, /terms.
- İhlal taraması: `rg -i moshi --glob '!plan.md' --glob '!.git/'` boş dönmelidir (plan.md'deki referans linkler hariç).

## Lisans

`GPL-3.0-or-later`. Bkz. `LICENSE`, `NOTICE`. Upstream Mosh/ET kaynakları `native/` altında, CCS paketi P18'de.
