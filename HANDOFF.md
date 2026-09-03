# HANDOFF — Pocket Agent

Tarih: 2026-09-03. Kaynak: `/root/dev/projects/pocket-agent`. Tek doğruluk kaynağı: `plan.md` (v2).
Hedef tamamlama: ~%82-85 (headless tavan). Emülatör/cihaz gerektiren işler açıkta (bkz. §7).

## 1. Proje özeti

Self-hosted Android terminal + agent companion (clean-room, `GPL-3.0-or-later`).
Üç parça: `apps/android` (Kotlin/Compose, minSdk 29), `cmd/pocket-agent-hook` + `host/`
(Go CLI/daemon), `backend/` + `deploy/docker-compose` (Go API + PostgreSQL + Caddy).
Sözleşmeler: `protocol/` (Buf v2, `host.proto` + `control.proto` v1 donduruldu).

## 2. Ortam

- Makine: uzak VPS, Ubuntu 24.04, 6 vCPU, 11GiB RAM, `/dev/kvm` YOK.
- Toolchain: Go 1.26.0 (`golang.org/x/crypto v0.56.0`), JDK 17, Gradle 8.7
  (`/tmp/gradle-8.7`, wrapper `apps/android/gradlew` bu dizine delege eder),
  Android SDK `/opt/android-sdk` (platform 34, build-tools 34.0.0), Buf 1.72, `gh` (azygoss).
- Sonuç: emülatör kurulmadı (`docs/emulator.md` gerekçesi). `connectedDebugAndroidTest`
  ve video kanıtları cihaz bekler. Telafi: Robolectric JVM-UI testi yeşil.

## 3. Adım durumu (P00–P18)

| Adım | Durum | Kanıt |
|---|---|---|
| P00 iskelet | ✅ | `p00-done`, secret-scan/license/packaging yeşil |
| P01 protokol v1 | ✅ | `buf lint` temiz, `go test ./protocol` 6/6, Kotlin parity unit |
| P02 backend | ✅ | tenant guard, TTL sweep (event+pairing+upload), pairing race 409, uploads 10MB cap, approvals CAS (401/202/409), canlı HTTP: healthz/202/400; `compose config` OK |
| P03 CLI/daemon | ✅ | full komut yüzeyi, 0600 socket ping, idempotent service, imzasız update ret, `status --json` schema=1 |
| P04 Easy Pair | ✅ | QR `pa1\|…`, aynı-claim idempotent, farklı-claim 409, marker-only revoke, TOFU hard-stop, `tests/e2e/p04-flow.sh` OK |
| P05–P10 Android terminal | ✅ headless | Room entities/DAO, TerminalService (dataSync FGS), `SshTransport` arayüzü + `TransportManager`, `FakeSshTransport` yankı, 6-fallback matrisi, `SavedConnection` secretsiz validasyon |
| P11 gateway | ✅ | strict jail, loopback-only, binary guard, dosya sunucusu 401/400/200, git diff 4 tür, preview fetch (1MB/5s), hepsi canlı testli |
| P12 hook'lar | ✅ | 12 agent merge (tekrar kurulumda dubl yok), Claude/Codex JSONL parser + fixture, ANSI-ban, journal-first emit |
| P13 inbox/onay | ✅ | session-merge + 24h, CAS ilk-kazanan, digest/rev bağlı, tenant gate |
| P14 Chat | ✅ | `ChatViewModel` aynı `SessionId`, MiniDiff gateway-only, unsupported→terminale |
| P15 paylaşım | ✅ | 10MB cap, 24h sweep, short-code invalidate, Files jail VM |
| P16 ses/deeplink | ✅ | BYOK kapalı=varsayılan (unit), `pocketagent://tmux\|herdr` parse |
| P17 sertleştirme | ⚠️ kısmi | fuzz seed corpus, canary, threat-model iskeleti; cihaz perf/a11y yok |
| P18 dağıtım | ⚠️ kısmi | 4-arch CLI tarball + SHA256 + win exe, SBOM (go), Dockerfile, 4 operasyon belgesi; AAB/imza/FCM-creds/cosign/CCS yok |

## 4. Test raporu (son yeşil koşu)

- Go: 18 paket `ok`, 0 FAIL (`go test ./... -count=1`), `go vet` + `go build ./...` temiz.
- Android: 29/29 unit (0 fail) + `lintDebug` + `assembleDebug` yeşil. Robolectric `MainActivity` launch dahil.
- Çıktılar: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (26.3MB, debug imzalı).
  İndirme: `https://tmpfiles.org/dl/wGw2hgRhrlhi/app-debug.apk` (yedek: `https://files.catbox.moe/mg2ik0.apk`).
- CLI: `dist/` git-dışı (tarballs + `SHA256SUMS` + `sbom-go.json`).

## 5. Derleme komutları

```bash
# Go + proto + kapılar
go test ./... && go vet ./... && go build ./...
buf lint
./scripts/secret-scan.sh && ./scripts/license-check.sh && ./scripts/check-packaging.sh
./tests/protocol/privacy-schema.sh && ./tests/security/canary.sh && ./tests/e2e/p04-flow.sh
# Android (headless; connectedTest emülatör ister → atlanır)
cd apps/android && export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# Backend canlı: go build -o /tmp/pa-backend ./backend/cmd/server && /tmp/pa-backend
# CLI paketleri: ./scripts/package-cli.sh (dist/)
```

## 6. Hassas konumlar (repoya girmez)

- `apps/android/local.properties` (`sdk.dir`), `*.jks/keystore`, `.env`, `service-account*.json`
  (hepsi `.gitignore`).
- Upload keystore env: `POCKET_AGENT_UPLOAD_{KEYSTORE,ALIAS,STORE_PASSWORD,KEY_PASSWORD}` (P18 release'te).
- QR secret loglanmaz; parola RAM-only; tokenlar DB'de hash-only (`auth.Mint/Verify`).

## 7. Bilinen eksikler (cihaz/ağ/kimlik bilgisi ister)

1. Mosh/ET `.so` + roaming kanıtı; termlib render + IME/CJK/OSC52 + gesture.
2. Keystore StrongBox + Biometric CryptoObject; cbssh/SSHJ `SshTransport` takma.
3. FCM service-account push; Passkey/OIDC turu; whisper model; S3 adapter.
4. AAB + Play kanalı + upload-keystore imza; cosign/SLSA/syft-CycloneDX; CCS paketi.
5. pcap privacy kanıtı; perf SLO ölçümleri; 10 canlı yol videosu; 2-cihaz race.
6. WSL/macOS/Windows doğrulama; Homebrew tap; prod Postgres backup/restore + Caddy TLS.

## 8. Sıradaki iş (önerilen sıra)

1. `SshTransport` fake→cbssh takma + gerçek cihazda PTY/resize + tmux re-attach.
2. Mosh bootstrap (SSH `mosh-server new`) + ET fallback + Wi-Fi/LTE resync ölçümü.
3. Keystore sarmalama + biyometrik gate + host-key ekranı (TOFU pin UI).
4. FCM + 2-cihaz onay yarışı + 24h inbox senkron (FCM creds gerekli).
5. Release: AAB + tarballs imza + SBOM + CCS + `REPRODUCING.md`.

## 9. Kurallar

- Conventional commits (`feat(Pxx): …`), her P için test + kapı yeşili zorunlu.
- `decisions.tsv` append-only (D001–D011 yazıldı).
- `rg -i moshi` yalnızca izinli dosyalarda (`plan.md`, `decisions.tsv`, `docs/reference/**`, `README.md`); `secret-scan.sh` bunu zorlar.
- `docs/reference/**` yayın paketine girmez (`check-packaging.sh`).
