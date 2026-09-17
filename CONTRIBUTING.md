# Katkı — Pocket Agent

## Hızlı akış

1. `make gates && make go` — Go tarafı yeşil mi?
2. `make android` — lint + unit + debug APK yeşil mi?
3. UI değişikliği yaptıysan ilgili `*UiTest` Robolectric süitü çalıştır.
4. Canlı özellik (SSH/backend/gateway/SFTP) değiştirdiysen:
   `make live-up && make live-test`.
5. Conventional commit: `feat(Pxx): …`, `fix(Pxx): …`.
6. Davranış/mimari değişikliği PR açıklamasında gerekçe ve test kanıtı taşımalı.

## Test katmanları

| Katman | Nerede | Ne zaman |
|---|---|---|
| Go unit | `go test ./...` | her değişiklik |
| Kotlin unit | `:app:testDebugUnitTest` | her değişiklik |
| Robolectric UI | `*UiTest` sınıfları | UI değişikliği |
| Canlı (env-gated) | `*LiveTest`, `PairingLiveTest`, `MoshBootstrapTest` | transport/net değişikliği |
| Kapılar | `make gates` + `tests/e2e/p04-flow.sh` | PR öncesi |

## Güvenlik kuralları (PR'da bloklayıcı)

- Secret/parola/token asla loglanmaz, plaintext diske yazılmaz.
- Gateway yalnız loopback bind eder; token 0600 dosyada.
- Backend tokenları hash-only; pairing kodları tek-kullanım + kısa TTL.
- `docs/reference/**` hiçbir yayın artefaktına girmez.

## Sürüm akışı

- `apps/android` versionCode/versionName bump + `scripts/build-release.sh`
  (imzalı APK+AAB, `apksigner verify`, `release-checksums`).
- CLI paketleri: `make package` → `dist/`; npm paketi: `make npm-package`.
