# Pocket Agent

[![CI](https://github.com/azygoss/pocket-agent/actions/workflows/ci.yml/badge.svg)](https://github.com/azygoss/pocket-agent/actions)
[![npm](https://img.shields.io/npm/v/pocket-agent-cli)](https://www.npmjs.com/package/pocket-agent-cli)
[![Lisans: GPL-3.0-or-later](https://img.shields.io/badge/lisans-GPL--3.0--or--later-blue)](LICENSE)

Self-hosted Android terminal ve agent companion. Telefondan host'una SSH ile
bağlan, tmux oturumlarını yönet, dosyalara SFTP ile eriş, agent onaylarını
cebinden karşıla — üçüncü taraf bulut yok, terminal ve dosya trafiği doğrudan
senin SSH bağlantın üzerinden akar.

Backend yalnızca agent olay özetlerini ve onay metadatasını görür; terminal
çıktısı, dosya içeriği, diff ve sohbet backend'e uğramaz.

## Ne yapar?

- **Gerçek SSH terminali** — TOFU host-key pinning, ed25519/RSA key auth,
  tam ANSI/VT parser (alternate screen, scroll region, synchronized output,
  DSR/DA/DECRQM sorguları); Codex, vim, htop, tmux gibi TUI'larla çalışır
- **Kalıcı tmux oturumları** — isimli oturumlar, çoklu oturum, bağlantı
  kopsa bile oturum host'ta yaşar, yeniden attach
- **SFTP dosya yöneticisi** — listeleme, okuma, yazma, indirme ve paylaşma
- **Gateway + workspace** — SSH loopback üzerinden workspace dosyaları,
  git diff ve HTTP preview tüneli (jail'li, token'lı)
- **Agent hook'ları** — Claude/Codex event akışı: bildirimler, onay
  istekleri, mesaj görünümlü çıktı, 24 saat TTL'li özet inbox
- **Android UX** — temalar, fontlar, pinch-zoom, OSC52 clipboard,
  OSC8 hyperlink, tuş şeridi, çoklu terminal sekmesi
- **Mosh bootstrap** — host'ta `mosh-server` paketlenir; roaming istemci
  tarafı deneysel (bkz. Durum ve sınırlar)

## Mimari ve veri sınırı

```
Android uygulaması ──SSH/PTY+SFTP──► Host (sshd + tmux + gateway :loopback)
       │                                 │
       └──HTTPS──► Backend ◄──flusher────┘ pocket-agent daemon
                   (event özeti,             (journal → özet POST)
                    onay, inbox)
```

| Backend'e **giden** | Backend'e **asla gitmeyen** |
|---|---|
| Agent olay özeti (kaynak, kategori, mesaj) | Terminal çıktısı / keystroke |
| Onay isteği + karar metadatası | Dosya içeriği, diff, sohbet |
| Pairing claim, tenant sınırı | SSH anahtarı, gateway token |

Backend özetleri 24 saat TTL ile saklar; tokenlar hash-only tutulur.

## Hızlı kurulum

Gereksinimler: host'ta Node 18+ (npm kurulumu için) ve sshd; backend için
Docker + Compose; Android 10+ (minSdk 29).

```bash
# 1) Backend (opsiyonel ama önerilir — agent event/onay akışı için)
cd deploy/docker-compose && cp .env.example .env   # alan adını doldur
docker compose up -d                               # veya ./bootstrap.sh

# 2) Host CLI — Go gerekmez, binary paketten gelir
npm install -g pocket-agent-cli
pocket-agent onboard --backend https://<alan-adın>

# 3) Android APK
# https://github.com/azygoss/pocket-agent/releases/latest → app-release.apk
# Uygulamada QR'ı tara veya XXXX-XXXX kodunu gir
```

npm paketi Linux/macOS x64 + arm64 ve Windows x64 (deneysel) binary içerir;
`pocket-agent` ve `pocket-agent-hook` komutları kurulur.

## Günlük CLI komutları

| Komut | İş |
|---|---|
| `pocket-agent onboard` | Daemon + gateway + hook'lar + pair QR tek komutta |
| `pocket-agent pair / unpair` | QR + XXXX-XXXX üret / eşleşmeyi kaldır |
| `pocket-agent doctor --json` | sshd, port, disk, backend kontrolleri |
| `pocket-agent status --json` | Sürüm + sağlık özeti |
| `pocket-agent hooks install` | Claude/Codex hook'larını config'e işle |
| `pocket-agent service install` | systemd user unit (daemon + gateway) |
| `pocket-agent servers` | Aktif tmux oturumlarını listele |
| `pocket-agent gateway serve` | Dosya/diff sunucusu (yalnız loopback) |
| `pocket-agent completion bash` | Kabuk tamamlama (bash/zsh/fish) |
| `pocket-agent version` | Sürüm |

Tam liste: `pocket-agent help`.

## Android özellikleri

- **Terminal**: tam ekran VT100/xterm emülasyonu, alternate screen,
  synchronized output (DECSET 2026) ile atomik TUI çizimi, 50k satır
  scrollback, pinch-zoom, seçim + OSC52 kopyalama
- **Bağlantı**: SSH keepalive, otomatik reconnect, TOFU pin değişiminde
  hard-stop uyarısı, Mosh deneysel roaming
- **Oturumlar**: isimli tmux oturumları, oturum kartları, bağlantı
  kesilse bile host'ta yaşayan süreçler
- **Dosyalar**: SFTP browser, upload/download/paylaş, workspace +
  git diff görünümü, HTTP preview
- **Uygulama**: Material 3 tema, font/boyut ayarları, tuş şeridi,
  agent bildirimleri ve onay diyalogları

## Kaynak koddan derleme

Gereksinimler: Go (go.mod'daki sürüm), JDK 17, Android SDK 34 + NDK/CMake,
Buf (proto lint için).

```bash
sudo ./scripts/setup-dev-environment.sh   # toolchain (bir kez)
source ./scripts/dev-env.sh
./scripts/verify-dev-environment.sh

make gates        # secret-scan + license + packaging + buf lint + canary
make go           # go test + vet + build
make android      # lintDebug + testDebugUnitTest + assembleDebug
make live-up && make live-test   # env-gated canlı süitler (SSH/SFTP/gateway)
make npm-package  # dist/pocket-agent-cli-*.tgz
```

Çıktılar: `apps/android/app/build/outputs/apk/debug/app-debug.apk`,
`dist/` (CLI tarball + checksums).

## Depo yapısı

| Yol | İçerik |
|---|---|
| `apps/android/` | Kotlin + Compose uygulama (ui/ transport/ data/ net/ service/ security/) |
| `cmd/pocket-agent-hook/` + `host/` | Go CLI + daemon (pairing, gateway, hooks, journal, ssh, tmux) |
| `backend/` | Go API (api/ approvals/ auth/ inbox/ store) + PostgreSQL |
| `protocol/` | Buf v2 proto sözleşmeleri (v1 donduruldu) |
| `deploy/docker-compose/` | Backend + Caddy kurulumu, backup/restore |
| `packages/npm/` | npm paketi (`pocket-agent-cli`) |
| `native/mosh/` | Mosh kaynak/build metadata + `libmoshclient.so` hashleri |
| `scripts/` `tests/` | Kapılar, paketleme, e2e/fuzz/perf/protocol testleri |
| `docs/` | Kurulum, güvenlik, yedekleme, rollback belgeleri |

## Güvenlik modeli

- **SSH TOFU** — ilk bağlantıda host key pin'lenir; değişimde hard-stop,
  kullanıcı onayı olmadan devam edilmez
- **Anahtarlar** — Android Keystore-backed; özel anahtar plaintext diske
  yazılmaz, repoda hiçbir secret bulunmaz (`make gates` tarar)
- **Gateway** — yalnız loopback bind, 0600 token dosyası, workspace jail,
  path-traversal ve SSRF koruması
- **Backend** — tokenlar hash-only, pairing kodu tek kullanım + kısa TTL,
  tenant sınırı her sorguda zorlanır, özetler 24 saat TTL
- **İmza** — CLI `update` yalnız imzalı manifest kabul eder; release APK
  `apksigner verify` ile doğrulanır. Debug APK üretim imzalı değildir.

Ayrıntı: [`docs/security/privacy.md`](docs/security/privacy.md),
[`threat-model.md`](docs/security/threat-model.md),
[`incident-response.md`](docs/security/incident-response.md).

## Durum ve sınırlar

- Çalışır ve canlı testli: SSH/PTY, SFTP, tmux, gateway, pairing,
  backend event/onay akışı, npm CLI kurulumu
- **Mosh**: host bootstrap + native client paketli; roaming istemcinin
  gerçek cihaz saha testi sürüyor
- **ET (Eternal Terminal)**: ertelendi
- Emülatör/cihaz-gerektiren testler (frame pacing, biyometrik, bazı a11y
  akışları) CI'da Robolectric ile kısmen kapsanır
- Üretim backend'i TLS + alan adı ister; `network_security_config`
  cleartext'i yalnız localhost/emülatöre açar
- Sürümler: Android `v0.31.0`, npm CLI `0.1.0`

## Testler

```bash
make gates && make go && make android   # tam kapı seti
make live-up && make live-test          # gerçek sshd/backend/gateway'e karşı
```

Canlı süitler env-gated'dir (`PA_LIVE_SSH`, `PA_LIVE_BACKEND`, `PA_LIVE_GW`);
`scripts/live-env.sh` yerel fixture'ı kurar (test kullanıcısı, loopback
gateway, preview sunucu).

## Katkı

Kurallar ve test katmanları: [`CONTRIBUTING.md`](CONTRIBUTING.md).
Reproducible build ve hash doğrulaması: [`REPRODUCING.md`](REPRODUCING.md).
Üçüncü taraf bileşen listesi: [`NOTICE`](NOTICE).

Clean-room kuralı: referans ürünlerin marka/kod/asset'i kopyalanmaz;
`docs/reference/` yalnız herkese açık davranışsal kaynak linki içerir ve
hiçbir release artefaktına girmez.

## Lisans

`GPL-3.0-or-later` — bkz. [`LICENSE`](LICENSE) ve [`NOTICE`](NOTICE).
Upstream Mosh/ET kaynakları `native/` altında kendi lisanslarıyla.
