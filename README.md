# Pocket Agent

Self-hosted Android terminal + agent companion. Clean-room implementation — no Moshi code, brand, text, or assets.

> Plan: [`plan.md`](plan.md) (v2 — tek doğruluk kaynağı). Önce planı okuyun.

## Üç parça

- `apps/android/` — Kotlin + Compose, minSdk 29 (P05+)
- `cmd/pocket-agent-hook/` + `host/` — Go CLI + daemon (P03+)
- `backend/` + `deploy/docker-compose/` — Go API + PostgreSQL + Caddy (P02+)

## Hızlı başlangıç (P00)

```bash
./scripts/secret-scan.sh
./scripts/license-check.sh
git log --oneline -5
```

Toolchain kurulumu (P00 sonrası, P01 öncesi):

```bash
sudo ./scripts/setup-dev-environment.sh
source ./scripts/dev-env.sh
./scripts/verify-dev-environment.sh
```

## Clean-room kuralı

- Moshi APK'sı decompile edilmez, trafiği MITM'lenmez.
- Moshi adı/logo/metin/asset/protokol/kod kopyalanmaz.
- Yalnızca herkese açık davranışsal kaynaklar: Play Store açıklaması, getmoshi.app/docs, /privacy, /terms.
- İhlal taraması: `rg -i moshi --glob '!plan.md' --glob '!.git/'` boş dönmelidir (plan.md'deki referans linkler hariç).

## Lisans

`GPL-3.0-or-later`. Bkz. `LICENSE`, `NOTICE`. Upstream Mosh/ET kaynakları `native/` altında, CCS paketi P18'de.

## Durum

P00 — iskelet. Adım kapıları için `plan.md §3` ve `decisions.tsv`'ye bakın.
