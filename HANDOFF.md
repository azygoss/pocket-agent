# HANDOFF — Pocket Agent

Tarih: 2026-09-03 (v0.9.4). Kaynak: `/root/dev/projects/pocket-agent`. Tek doğruluk kaynağı: `plan.md` (v2).
Hedef tamamlama: ~%92 (headless tavan: ekran-modeli terminal + SFTP + gateway tüneli + mosh bootstrap + backend sync). Emülatör/cihaz gerektiren işler açıkta (bkz. §7).

## 1. Proje özeti

Self-hosted Android terminal + agent companion (clean-room, `GPL-3.0-or-later`).
Üç parça: `apps/android` (Kotlin/Compose, minSdk 29), `cmd/pocket-agent-hook` + `host/`
(Go CLI/daemon), `backend/` + `deploy/docker-compose` (Go API + PostgreSQL + Caddy).
Sözleşmeler: `protocol/` (Buf v2, `host.proto` + `control.proto` v1 donduruldu).

## 2. Ortam

- Makine: uzak VPS, Ubuntu 24.04, 6 vCPU, 11GiB RAM, `/dev/kvm` YOK.
- Toolchain: Go 1.26.0 (`golang.org/x/crypto v0.56.0`), JDK 17, Gradle 8.7
  (`/tmp/gradle-8.7`, wrapper `apps/android/gradlew` bu dizine delege eder),
  Android SDK `/opt/android-sdk` (platform 34, build-tools 34.0.0, **NDK 29.0.14206865 + CMake 3.22.1**),
  Buf 1.72, `gh` (azygoss). Native derleme için `libtool-bin`, `bison`, `flex`, `texinfo` kurulu.
- Canlı test altyapısı: `pa-dev` kullanıcısı (ed25519 key `/tmp/pa-dev-key`), sshd :22,
  backend `go build -o /tmp/pa-backend ./backend/cmd/server && /tmp/pa-backend` (:8080, in-memory),
  gateway `POCKET_GATEWAY_TOKEN=tok123 /tmp/pa-hook gateway serve --root /tmp/pa-workspace` (:24543 loopback),
  preview için `python3 -m http.server 8899 --bind 127.0.0.1` (/tmp/pa-preview), `mosh-server` 1.4.0 kurulu.
- Sonuç: emülatör kurulmadı (`docs/emulator.md` gerekçesi). `connectedDebugAndroidTest`
  ve video kanıtları cihaz bekler. Telafi: Robolectric Compose UI testleri + 5 canlı env-gated süit.

## 3. Adım durumu (P00–P18)

| Adım | Durum | Kanıt |
|---|---|---|
| P00 iskelet | ✅ | `p00-done`, secret-scan/license/packaging yeşil |
| P01 protokol v1 | ✅ | `buf lint` temiz, `go test ./protocol` 6/6, Kotlin parity unit |
| P02 backend | ✅ | tenant guard, TTL sweep (event+pairing+upload), pairing race 409, uploads 10MB cap, approvals CAS (401/202/409), canlı HTTP: healthz/202/400; `compose config` OK |
| P03 CLI/daemon | ✅ | full komut yüzeyi, 0600 socket ping, idempotent service, imzasız update ret, `status --json` schema=1 |
| P04 Easy Pair | ✅ | QR `pa1\|…`, aynı-claim idempotent, farklı-claim 409, marker-only revoke, TOFU hard-stop, `tests/e2e/p04-flow.sh` OK |
| P05–P10 Android terminal | ✅ headless | Room v4, TerminalService (dataSync FGS + oturum sayılı bildirim + tümünü-kapat aksiyonu), gerçek SSH (`SshjConnector`/SSHJ + TOFU pin diyaloğu), **TerminalBuffer v2 ekran modeli** (CUP/alt-screen/DECSTBM/IL-DL/DEC-grafik/SGR bg+inverse — vim/htop/tmux kullanılabilir), viewport→PTY resize, `SessionManager` çoklu oturum, 10MB burst <10s |
| P11 gateway | ✅ + canlı | strict jail, loopback-only, binary guard, dosya 401/400/200, git diff 4 tür, `/ls` + `/diff` + `/preview` endpointleri, `gateway serve` + `service install-gateway` (systemd user unit); **Android: direct-tcpip tüneli (GatewayTunnel) + Workspace modu — GatewayTunnelLiveTest canlı yeşil (ls/file/401/traversal/preview)** |
| P12 hook'lar | ✅ | 12 agent merge (tekrar kurulumda dubl yok), Claude/Codex JSONL parser + fixture, ANSI-ban, journal-first emit |
| P07 Mosh | ⚠️ derleme + bootstrap | 3 ABI `libmoshclient.so` kaynaktan (D014) + **SSH exec bootstrap canlı kanıtlı** (`mosh-server new` → MOSH CONNECT parse, anahtar SSH içinde RAM'de; VPS'te mosh 1.4.0); UDP client spawn cihaz bekler |
| P13 inbox/onay | ✅ + canlı | onay bildirimi (0.8.1) + **Room kalıcılığı (0.9.2: yeniden başlatmada 24s TTL içi olaylar korunur, DB v5)**; session-merge + 24h, CAS ilk-kazanan, digest/rev bağlı, tenant gate; **BackendClient + EventSync (15s poll) gerçek backend'e bağlı — BackendLiveTest: event→özet→cursor→CAS 202/409→tenant izolasyonu** |
| P14 Chat | ✅ + canlı | gateway `/chat` endpointi (jail içi JSONL transcript → blok akışı, Claude/Codex parser); Workspace'te .jsonl → sohbet görünümü (ChatDialog: mesaj balonu/tool çipi/sonuç-hata); içerik backend'e gitmez |
| P15 paylaşım | ✅ + SFTP | 10MB cap, 24h sweep, short-code invalidate; **Files sekmesi gerçek SFTP gezgini** (list/cd/preview/download/share/upload, FileProvider, SftpLiveTest canlı) |
| P16 ses/deeplink | ✅ | BYOK kapalı=varsayılan (unit), `pocketagent://tmux\|herdr` parse + MainActivity'de işlenip Terminal sekmesine yönleniyor (singleTask) |
| P17 sertleştirme | ⚠️ kısmi | fuzz seed corpus, canary, threat-model iskeleti; cihaz perf/a11y yok |
| P18 dağıtım | ⚠️ büyük ölçüde | 4-arch CLI tarball, SBOM (go+android), Dockerfile, operasyon belgeleri + **env-driven imzalı release APK+AAB, R8 proguard, REPRODUCING.md, CCS paketi (package-ccs.sh), build-release.sh tam kapı (apksigner verify + release-checksums)**; cosign/SLSA/CI yok |

## 4. Test raporu (son yeşil koşu)

- Go: 18 paket `ok`, 0 FAIL (`go test ./... -count=1`), `go vet` + `go build ./...` temiz.
- Android: 100/100 unit (5 canlı env-gated: SSH + backend + SFTP + gateway + mosh bootstrap — hepsi bu makinede kanıtlı; + 2 Robolectric Compose UI süiti) + `lintDebug` (0 hata) + `assembleDebug` yeşil.
- Canlı SSH kanıtı: `PA_LIVE_SSH=1 PA_LIVE_USER=pa-dev PA_LIVE_PEM=/tmp/pa-dev-key ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.SshjLiveTest` → localhost sshd'ye TOFU hard-stop → pin → ed25519 key auth → PTY → `echo PA_ALIVE_42` okundu (test user `pa-dev` bu makinede hazır).
- Canlı backend kanıtı: `/tmp/pa-backend` ayaktayken `PA_LIVE_BACKEND=http://127.0.0.1:8080 ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.BackendLiveTest` → event post → özet çekme → cursor → CAS 202/409 → tenant izolasyonu.
- Canlı SFTP kanıtı: `PA_LIVE_SSH=1 … ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.SftpLiveTest` → list `/` + write/read `/tmp` + kota kesme + dizin-önce sıralama.
- Canlı gateway tüneli: gateway çalışırken (`POCKET_GATEWAY_TOKEN=tok123 pocket-agent gateway serve --root /tmp/pa-workspace`) `PA_LIVE_GW=1 PA_LIVE_SSH=1 … --tests dev.pocketagent.GatewayTunnelLiveTest` → SSH direct-tcpip → ls/file/401/traversal + preview (127.0.0.1:8899 marker).
- Canlı mosh bootstrap: `PA_LIVE_SSH=1 … --tests dev.pocketagent.MoshBootstrapTest` → SSH exec → `mosh-server new` → MOSH CONNECT port+key parse (anahtar yalnız RAM).
- Çıktılar: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (74MB v0.9.4, debug imzalı; SSHJ+bcprov+icons+mosh 3 ABI dahil).
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
# Gateway canlı: go build -o /tmp/pa-hook ./cmd/pocket-agent-hook && POCKET_GATEWAY_TOKEN=… /tmp/pa-hook gateway serve --root <ws>
# CLI paketleri: ./scripts/package-cli.sh (dist/)
# Mosh (kaynaktan, 3 ABI): ./scripts/build-mosh.sh → jniLibs/<abi>/libmoshclient.so + native/mosh/SHA256SUMS
```

## 6. Hassas konumlar (repoya girmez)

- `apps/android/local.properties` (`sdk.dir`), `*.jks/keystore`, `.env`, `service-account*.json`
  (hepsi `.gitignore`).
- Upload keystore env: `POCKET_AGENT_UPLOAD_{KEYSTORE,ALIAS,STORE_PASSWORD,KEY_PASSWORD}` (P18 release'te).
- QR secret loglanmaz; parola RAM-only; tokenlar DB'de hash-only (`auth.Mint/Verify`).

## 7. Bilinen eksikler (cihaz/ağ/kimlik bilgisi ister)

1. Mosh runtime: native client exec (libmoshclient.so pakette, nativeLibraryDir'den) + UDP wiring + Wi-Fi/LTE resync ölçümü (cihaz); bootstrap kanıtlı (D018). ET ertelendi (D021: upstream repo 404, resmi Android yolu yok). termlib tam VT100 + IME/CJK + gesture.
2. Keystore StrongBox + Biometric CryptoObject; biyometrik app-kilit.
3. FCM service-account push; Passkey/OIDC turu; whisper model; S3 adapter.
4. AAB + Play kanalı + upload-keystore imza; cosign/SLSA/syft-CycloneDX; CCS paketi.
5. pcap privacy kanıtı; perf SLO ölçümleri; 10 canlı yol videosu; 2-cihaz race.
6. WSL/macOS/Windows doğrulama; Homebrew tap; prod Postgres backup/restore + Caddy TLS.
7. Port-forward yönetici UI'ı; tmux/Zellij seçim UI'ı (capability probe cihazda); backend gerçek auth (X-Tenant iskeleti → passkey).

## 7b. Günlük kullanım katmanı (0.9.4'e kadar eklendi)

- Ayarlar DataStore'da kalıcı (tema + font ölçeği + backend URL/tenant); `SettingsViewModel(store, scope)`.
- Secret'lar: RAM-only varsayılan, "Keystore ile sakla" opt-in → AES-256-GCM (`KeystoreSecretStore`); plaintext diskte yok.
- Bağlantı düzenleme diyaloğu, `lastConnectedAt` sıralaması, eksik secret'ta bağlan → düzenleme diyaloğu.
- **TerminalBuffer v2 (0.6.0)**: gerçek ekran modeli — rows×cols hücre, imleç konumlama, alternate screen (?1049/47/48), DECSTBM kaydırma bölgesi, IL/DL/ICH/DCH/ECH, ED/EL 0-3, DEC grafik charset (tmux çerçeveleri), SGR fg+bg (8/16/256/truecolor) + inverse, pending-wrap, resize'da üst satırlar scrollback'e; LF gerçek PTY davranışı (yalnız aşağı iner); snapshot önbelleği (scrollback sürüm-lenmiş).
- **Terminal UX (0.6.0)**: viewport ölçüsünden otomatik PTY boyutu, tam ekran modu (immersive + yüzen çıkış), Home/End/PgUp/PgDn tuşları, bg renk render'ı, arama eşleşme seti (O(1)).
- **SessionManager (0.4.0)**: çoklu eşzamanlı oturum, çip ile geçiş, aynı profilde reuse/reconnect, oturum başına kapatma; FGS herhangi oturum aktifken ayakta.
- **Backend sync (0.5.0)**: `BackendClient` + `EventSync` (15s poll, üstel backoff); Agents ekranı canlı (kategori ikonları, göreli zaman, onay→backend CAS); Ayarlar'da backend kartı + health testi.
- **Mosh (0.5.0)**: 3 ABI kaynaktan derleme, jniLibs paketleme, `MoshRuntime` (nativeLibraryDir + terminfo açma).
- **Dosyalar (0.6.0–0.7.0)**: SFTP gezgini (dizin gezinme, önizleme <64KB, indirme→paylaşım FileProvider, upload, 10MB cap) + **Workspace modu** (diff çipleri + dev-server preview diyaloğu — canlı kanıtlı): gateway jail'i içinde ls/dosya/git-diff (staged/unstaged/working/son-commit), token 0600 dosyadan SFTP ile RAM'e; gateway yoksa kurulum talimatı kartı.
- **Bağlantılar (0.7.0)**: host başına "tmux'a otomatik bağlan" (`tmux new-session -A -s main`, kopmaya dayanıklı oturum); Room DB v4 (destructive fallback — debug aşamasında).
- **İmleç (0.7.0)**: terminalde imleç bloğu render'ı (palette.cursor), ?25h/l görünürlüğü saygılanır.
- **Terminal (0.7.1-0.7.2)**: OSC 52 uzaktan kopyalama → cihaz panosu, OSC 0/2 pencere başlığı durum çipinde, bracketed paste (?2004) — çok satırlı yapıştırma korunur, imleç bloğu, tam ekran, viewport→PTY resize, scrollback paylaşımı (FileProvider), **OSC 8 tıklanabilir linkler**.
- **FGS (0.7.2-0.8.0)**: bildirimde aktif oturum sayısı + "Tümünü kapat" aksiyonu, dokununca uygulamaya döner; POST_NOTIFICATIONS runtime izni (API 33+).
- **İlk izlenim (0.9.3)**: pencere/status/nav bar açılıştan itibaren TermBg (beyaz flaş yok); Home'da "Son bağlantılar" tek-dokunuş bağlan (onboarding yalnız host yokken).
- **Launcher ikonu (0.9.2)**: adaptive vector (">_" prompt, TermBg zemin, monochrome katman) — varsayılan Android ikonu yok.
- **Kopmada reconnect (0.9.1, varsayılan açık)**: beklenmedik kopmada üstel backoff (2s→32s, maks 5) ile otomatik yeniden bağlanma; çipte "Yeniden bağlanıyor n/5" rozeti; auth/host-key hatasında P08 hard-stop; kullanıcı kapatması tetiklemez.
- **Açılışta reconnect (0.8.0, opt-in)**: secret Keystore'da saklıysa son oturum otomatik bağlanır; değilse sessizce atlanır.
- **Klavye (0.8.2)**: donanım klavye desteği (Ctrl+harf→kontrol kodu, ok/Home/End/PgUp/PgDn/Esc doğrudan terminal yüzeyine); Agents'ta "Şimdi senkronla" (EventSync syncNow); ET spike: upstream repo 404, resmi Android yolu yok → ET ertelendi (D021).
- **Preview (0.8.0)**: gateway /preview ucu + Workspace "Dev server…" diyaloğu (loopback-only, SSRF korumalı, 1MB/5s).
- **Tema (0.6.0-0.9.0)**: AMOLED saf-siyah seçeneği (kalıcı), koyu/aydınlık palet; **JetBrains Mono 2.304 bundle** (OFL-1.1, regular/bold/italic/bold-italic — sistem monospace yok, gerçek terminal fontu; lisans assets/licenses, Hakkında atfı).
- Terminal: komut geçmişi (↑↓, dedupe, 100 cap), Ctrl toggle, reconnect, sonda-otomatik kaydırma + alta-in FAB, scrollback arama (eşleşme vurgusu), clipboard yapıştır, 30s SSH keepalive, bilinen-hosts yönetimi (Ayarlar).
- Snackbar (bağlandı/kapandı), Agents tab okunmamış rozeti, sekmeye göre TopAppBar başlığı, deep-link yönlendirme.

## 8. Sıradaki iş (önerilen sıra)

1. ~~`SshTransport` fake→cbssh takma~~ → **yapıldı (SSHJ, D012)**; sırada: cihazda PTY/resize doğrulaması, tam VT100 için termlib.
2. Mosh: native client exec + UDP wiring (bootstrap kanıtlı, D018) + Wi-Fi/LTE resync ölçümü (cihaz).
3. ~~Gateway wiring~~ → **tamam (D017/D019: direct-tcpip + /ls + /diff + /preview + Workspace UI + systemd unit)**.
4. FCM + 2-cihaz onay yarışı + 24h inbox senkron (FCM creds gerekli); backend X-Tenant iskeleti → passkey auth.
5. ~~Release zinciri~~ → **imzalı APK+AAB + CCS + REPRODUCING.md + build-release.sh tamam (D025)**; sırada: gerçek upload keystore (kullanıcı), cosign/SLSA, CI job.
6. ~~Compose UI testleri~~ → **5 ekran smoke yeşil (Terminal/Agents/Connections/Files/Settings)**; sırada: derin akışlar (diyaloglar, paylaşım intent).

## 9. Kurallar

- Conventional commits (`feat(Pxx): …`), her P için test + kapı yeşili zorunlu.
- `decisions.tsv` append-only (D001–D027 yazıldı).
- Marka/kod taraması yalnızca izinli dosyalardaki referanslara izin verir (`secret-scan.sh` kuralı); ham kopya yasaktır.
- `docs/reference/**` yayın paketine girmez (`check-packaging.sh`).
