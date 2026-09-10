# HANDOFF — Pocket Agent

Tarih: 2026-09-10 (v0.15.6). Kaynak: `/root/dev/projects/pocket-agent`. Tek doğruluk kaynağı: `plan.md` (v2).
Hedef tamamlama: ~%94 (headless tavan: ekran-modeli terminal + SFTP + gateway tüneli + mosh bootstrap + backend sync + kullanılabilirlik paketi). Emülatör/cihaz gerektiren işler açıkta (bkz. §7).

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
  **Tek komutla hepsi:** `./scripts/live-env.sh up` (mevcut süreçleri sahiplenir, `status`/`env`/`down` alt komutları; `make live-up`).
- Sonuç: emülatör kurulmadı (`docs/emulator.md` gerekçesi). `connectedDebugAndroidTest`
  ve video kanıtları cihaz bekler. Telafi: Robolectric Compose UI testleri + 5 canlı env-gated süit.

## 3. Adım durumu (P00–P18)

| Adım | Durum | Kanıt |
|---|---|---|
| P00 iskelet | ✅ | `p00-done`, secret-scan/license/packaging yeşil |
| P01 protokol v1 | ✅ | `buf lint` temiz, `go test ./protocol` 6/6, Kotlin parity unit |
| P02 backend | ✅ | tenant guard, TTL sweep (event+pairing+upload), pairing race 409, uploads 10MB cap, approvals CAS (401/202/409), canlı HTTP: healthz/202/400; `compose config` OK |
| P03 CLI/daemon | ✅ | full komut yüzeyi, 0600 socket ping, idempotent service, imzasız update ret, `status --json` schema=1 |
| P04 Easy Pair | ✅ uçtan uca | `pocket-agent pair` (tek-seferlik anahtar + marker + backend session + ASCII QR/PNG + XXXX-XXXX) → Android QR tara/kod gir → claim → Keystore'lu kayıt → otomatik bağlan; **PairingLiveTest E2E canlı**; QR `pa1\|…`, aynı-claim idempotent, farklı-claim 409, marker-only revoke, TOFU hard-stop, `tests/e2e/p04-flow.sh` OK |
| P05–P10 Android terminal | ✅ headless | Room v4, TerminalService (dataSync FGS + oturum sayılı bildirim + tümünü-kapat aksiyonu), gerçek SSH (`SshjConnector`/SSHJ + TOFU pin diyaloğu), **TerminalBuffer v2 ekran modeli** (CUP/alt-screen/DECSTBM/IL-DL/DEC-grafik/SGR bg+inverse — vim/htop/tmux kullanılabilir), viewport→PTY resize, `SessionManager` çoklu oturum, 10MB burst <10s |
| P11 gateway | ✅ + canlı | strict jail, loopback-only, binary guard, dosya 401/400/200, git diff 4 tür, `/ls` + `/diff` + `/preview` endpointleri, `gateway serve` + `service install-gateway` (systemd user unit); **Android: direct-tcpip tüneli (GatewayTunnel) + Workspace modu — GatewayTunnelLiveTest canlı yeşil (ls/file/401/traversal/preview)** |
| P12 hook'lar | ✅ | 12 agent merge (tekrar kurulumda dubl yok), Claude/Codex JSONL parser + fixture, ANSI-ban, journal-first emit |
| P07 Mosh | ⚠️ derleme + bootstrap | 3 ABI `libmoshclient.so` kaynaktan (D014) + **SSH exec bootstrap canlı kanıtlı** (`mosh-server new` → MOSH CONNECT parse, anahtar SSH içinde RAM'de; VPS'te mosh 1.4.0); UDP client spawn cihaz bekler |
| P13 inbox/onay | ✅ + canlı | onay bildirimi (0.8.1) + **Room kalıcılığı (0.9.2: yeniden başlatmada 24s TTL içi olaylar korunur, DB v5)**; session-merge + 24h, CAS ilk-kazanan, digest/rev bağlı, tenant gate; **BackendClient + EventSync (15s poll) gerçek backend'e bağlı — BackendLiveTest: event→özet→cursor→CAS 202/409→tenant izolasyonu** |
| P14 Chat | ✅ + canlı | gateway `/chat` endpointi (jail içi JSONL transcript → blok akışı, Claude/Codex parser); Workspace'te .jsonl → sohbet görünümü (ChatDialog) + **/chat-recent allowlist keşfi (~/.claude, ~/.codex; D028)**; içerik backend'e gitmez |
| P15 paylaşım | ✅ + SFTP | 10MB cap, 24h sweep, short-code invalidate; **Files sekmesi gerçek SFTP gezgini** (list/cd/preview/download/share/upload, FileProvider, SftpLiveTest canlı) |
| P16 ses/deeplink | ✅ | BYOK kapalı=varsayılan (unit), `pocketagent://tmux\|herdr` parse + MainActivity'de işlenip Terminal sekmesine yönleniyor (singleTask) |
| P17 sertleştirme | ⚠️ kısmi | fuzz seed corpus, canary, threat-model iskeleti; cihaz perf/a11y yok |
| P18 dağıtım | ⚠️ büyük ölçüde | 4-arch CLI tarball, SBOM (go+android), Dockerfile, operasyon belgeleri + **env-driven imzalı release APK+AAB, R8 proguard, REPRODUCING.md, CCS paketi (package-ccs.sh), build-release.sh tam kapı (apksigner verify + release-checksums)**; cosign/SLSA/CI yok |

## 4. Test raporu (son yeşil koşu, v0.15.6)

- Go: 18 paket `ok`, 0 FAIL (`go test ./...`), `go vet` + `go build ./...` temiz, `buf lint` temiz.
- Kapılar: `secret-scan`, `license-check`, `check-packaging`, `privacy-schema`, `canary`, `tests/e2e/p04-flow.sh` — hepsi yeşil.
- Android: **144 unit test yeşil** (`:app:testDebugUnitTest`), `lintDebug` 0 hata, `assembleDebug` yeşil.
  Yeni testler (0.15.0): `TerminalRenderTest` x5 (imleç sonda-bosluk regresyonu), `spacesAdvanceCursorPastTrimmedCells` + `bellFiresCallback`/`bellInsideOscDoesNotFire` (TerminalBufferTest), `SshConfigTest` x4, `AddHostLinkTest` x4, `KeyGenTest` x3, `BackupTest` x4 (Robolectric), `emptyConnectionsShowsFirstRunWizard`.
- Android: 6 canlı env-gated süit (SSH + backend + SFTP + gateway + mosh bootstrap + pairing E2E) — hepsi bu makinede kanıtlı; `scripts/live-env.sh up` tek komutla ortamı kurar/sahiplenir.
- Canlı SSH kanıtı: `PA_LIVE_SSH=1 PA_LIVE_USER=pa-dev PA_LIVE_PEM=/tmp/pa-dev-key ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.SshjLiveTest` → localhost sshd'ye TOFU hard-stop → pin → ed25519 key auth → PTY → `echo PA_ALIVE_42` okundu (test user `pa-dev` bu makinede hazır).
- Canlı backend kanıtı: `/tmp/pa-backend` ayaktayken `PA_LIVE_BACKEND=http://127.0.0.1:8080 ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.BackendLiveTest` → event post → özet çekme → cursor → CAS 202/409 → tenant izolasyonu.
- Canlı SFTP kanıtı: `PA_LIVE_SSH=1 … ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.SftpLiveTest` → list `/` + write/read `/tmp` + kota kesme + dizin-önce sıralama.
- Canlı gateway tüneli: gateway çalışırken (`POCKET_GATEWAY_TOKEN=tok123 pocket-agent gateway serve --root /tmp/pa-workspace`) `PA_LIVE_GW=1 PA_LIVE_SSH=1 … --tests dev.pocketagent.GatewayTunnelLiveTest` → SSH direct-tcpip → ls/file/401/traversal + preview (127.0.0.1:8899 marker).
- Canlı mosh bootstrap: `PA_LIVE_SSH=1 … --tests dev.pocketagent.MoshBootstrapTest` → SSH exec → `mosh-server new` → MOSH CONNECT port+key parse (anahtar yalnız RAM).
- Çıktılar: `apps/android/app/build/outputs/apk/debug/app-debug.apk` + kopya `dist/pocket-agent-0.15.6-debug.apk` (77.5MB v0.15.6 / versionCode 12, debug imzalı; SSHJ+bcprov+icons+mosh 3 ABI + 3 gömülü font dahil; sha256: `05d46ebb…3d29`, tam değer `dist/` içinde `sha256sum` ile doğrulanır). İndirilebilir sayfa: `http://<vps>:8090/` (`/srv/pocket-agent-apk`, ufw'da 8090 açık).
- CLI: `dist/` git-dışı (tarballs + `SHA256SUMS` + `sbom-go.json`).

## 5. Derleme komutları

```bash
# Kestirme: make help (tüm hedefler listeli)
make gates      # secret-scan + license + packaging + protocol + canary
make go         # go test + vet + build
make android    # lintDebug + testDebugUnitTest + assembleDebug
make live-up    # canlı test ortamı (backend+gateway+preview)
# Elle eşdeğerleri:
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
# Commit öncesi: ./scripts/pre-commit.sh (gofmt + secret-scan + license + vet + hızlı test)
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
6. WSL/macOS/Windows doğrulama; Homebrew tap; prod Postgres backup/restore + Caddy TLS (bootstrap.sh + verify-backup.sh eklendi; gerçek restore provası VPS'te yapılmadı).
7. Port-forward yönetici UI'ı; tmux/Zellij seçim UI'ı (capability probe cihazda); backend gerçek auth (X-Tenant iskeleti → passkey).
8. i18n: UI metinleri hâlâ Kotlin'e gömülü Türkçe — `strings.xml` extraction + `values-en` mekanik ama geniş iş; henüz başlanmadı.
9. `SshProbe` promiscuous verifier kullanır (salt-tanı) — TOFU pin'i yalnızca gerçek bağlantıda yapılır, tasarım gereği.
10. Cihazda doğrulanacaklar (0.15.0): IME'de sonda-boşluk görünürlüğü, BEL haptic, pinch-zoom, snippet tuşları, SAF dosya seçici, QR→wizard akışı.

## 7b. Günlük kullanım katmanı (0.11.0'a kadar eklendi)

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
- **Perf (0.9.5)**: TerminalBuffer eşik testleri — 1MB stilli feed 80ms, 100 snapshot 17ms, resize reflow 2ms (regresyon yakalar).
- **İlk izlenim (0.9.3)**: pencere/status/nav bar açılıştan itibaren TermBg (beyaz flaş yok); Home'da "Son bağlantılar" tek-dokunuş bağlan (onboarding yalnız host yokken).
- **Launcher ikonu (0.9.2)**: adaptive vector (">_" prompt, TermBg zemin, monochrome katman) — varsayılan Android ikonu yok.
- **Kopmada reconnect (0.9.1, varsayılan açık)**: beklenmedik kopmada üstel backoff (2s→32s, maks 5) ile otomatik yeniden bağlanma; çipte "Yeniden bağlanıyor n/5" rozeti; auth/host-key hatasında P08 hard-stop; kullanıcı kapatması tetiklemez.
- **Açılışta reconnect (0.8.0, opt-in)**: secret Keystore'da saklıysa son oturum otomatik bağlanır; değilse sessizce atlanır.
- **Klavye (0.8.2)**: donanım klavye desteği (Ctrl+harf→kontrol kodu, ok/Home/End/PgUp/PgDn/Esc doğrudan terminal yüzeyine); Agents'ta "Şimdi senkronla" (EventSync syncNow); ET spike: upstream repo 404, resmi Android yolu yok → ET ertelendi (D021).
- **Preview (0.8.0)**: gateway /preview ucu + Workspace "Dev server…" diyaloğu (loopback-only, SSRF korumalı, 1MB/5s).
- **Terminal redesign (0.12.0)**: oturum şeridi pill'leri, overlay aksiyon çubuğu, ghost tuşlar, ❯ prompt'lu borderless giriş — terminal artık tamamen ekrana yayılan modern yüzey.
- **Tema/font kataloğu (0.14.0)**: `theme/ConsoleThemes.kt`'de 18 tema (Pocket, Pocket AMOLED, Claude Dark/Light, Codex Dark, GitHub Dark/Light, Notion Light/Dark, Dracula, Nord, Gruvbox, One Dark, Tokyo Night, Catppuccin Mocha, Solarized Dark/Light, Monokai) ve `theme/Fonts.kt`'de 6 font (JetBrains Mono, IBM Plex Mono, Space Mono — OFL-1.1 gömülü; Sistem Mono/Sans/Serif). Her tema app renklerini + terminal ANSI-16 paletini taşır; `TermStyle` artık ANSI **indeksi** saklar, renk render anında aktif temadan çözülür — tema değişince mevcut terminal çıktısı da canlı renklenir. `PocketAgentTheme(theme, mono)` `LocalConsoleTheme`/`LocalMonoFont` yayar; tüm mono metin seçili fontu kullanır. Ayarlar > Görünüm'de tema kartları + font çipleri + ölçek.
- **Alt ekran imleç düzeltmesi (0.14.0)**: `resizeScreen` artık `savedRow/savedCol`'u koruyor. Önceden ajan (codex/claude) açıkken viewport değişince (klavye) `?1049l` çıkışında imleç satır 0'a düşüyor, kabuk prompt'u eski satırların üstüne yazıyordu — bu yüzden `clear` gerekıyordu. Ayrıca tuş şeridine `^l` (ekran temizle) eklendi.
- **Tasarım sistemi / genel modernizasyon (0.13.2)**: `theme/Theme.kt`'de ölçü tokenleri (`Space`), katmanlı yüzey paleti (surfaceContainer katmanları, shadow yok), 4/8/10/14dp köşe ölçeği ve tam tipografi ölçeği (mono başlık/label + sans gövde). `Components.kt` yeniden yazıldı: `ConsoleCard`/`CardHeader`/`ListRow`/`TagPill`/`EmptyState`/`SegmentedControl`/`SettingRow` + pill yerine 8dp köşeli `ConsoleButton` ailesi; tüm ekranlar bu dile taşındı (Home/Connections/Agents/Files/Settings). `PocketApp.kt` chrome: marka glifli 52dp üst bar + oturum pill'i, alt navigasyonda üst çizgi yerine yuvarlatılmış dolgu vurgusu (animated). FilterChip/FilledTonalButton/çift FAB gibi M3 varsayılanları kaldırıldı.
- **Terminal klavye/IME düzeltmesi (0.13.1)**: IME yakalayıcı alanı çıktı alanının üstünden altına taşındı; `android:windowSoftInputMode="adjustResize"` + viewport her değiştiğinde (`viewportEpoch`) aktif satıra kaydırma — klavye açılınca yazılan satır artık klavyenin altında kalmıyor. Klavye toggle'ı `clearFocus()` kullanıyor (sistem geri tuşuyla kapatılan IME'de takılmıyor). LazyColumn üst `contentPadding` ile overlay aksiyon çubuğu ilk satırları gizlemiyor.
- **Terminal giriş redesign (0.13.0)**: ayrı komut satırı kaldırıldı — terminal yüzeyine dokunup doğrudan yazılır (1dp görünmez, kontrollü IME alanı; her değişim `imeEdit` saf fonksiyonuyla PTY'ye delta olarak akar). Alt kısımda terminal yüzeyine bitişik tek satır tuş şeridi: `ctrl` mandalı + `esc/tab/^c/^d/^z` + oklar + `home/end/pgup/pgdn` + semboller; sağda yapıştır/klavye. Ripple yok, basılıyken hafif zemin; odakta ince yeşil border. Donanım okları artık gerçek CSI dizisi gönderir (eskiden yalnız ESC).
- **Console redesign (0.11.0)**: keskin köşeler (4-6dp), outlined kartlar, mono başlık tipografisi, özel ince topbar+altbar, ANSI palet uygulamayla uyumlu — M3 şablon görünümü tamamen gitti.
- **Tema (0.6.0-0.9.0)**: AMOLED saf-siyah seçeneği (kalıcı), koyu/aydınlık palet; **JetBrains Mono 2.304 bundle** (OFL-1.1, regular/bold/italic/bold-italic — sistem monospace yok, gerçek terminal fontu; lisans assets/licenses, Hakkında atfı).
- Terminal: komut geçmişi (↑↓, dedupe, 100 cap), Ctrl toggle, reconnect, sonda-otomatik kaydırma + alta-in FAB, scrollback arama (eşleşme vurgusu), clipboard yapıştır, 30s SSH keepalive, bilinen-hosts yönetimi (Ayarlar).
- Snackbar (bağlandı/kapandı), Agents tab okunmamış rozeti, sekmeye göre TopAppBar başlığı, deep-link yönlendirme.

## 7c. Kullanılabilirlik paketi (0.15.0, D037)

- **Terminal sonda-boşluk düzeltmesi**: `RowBuf.build()` sondaki boş hücreleri kırpar (kompakt snapshot — doğru davranış); `toAnnotatedString` imleci kırpılmış satır sonuna çiziyordu. Artık imleç gerçek sütununa kadar boşlukla doldurulur → boşluklar yazıldığı an görünür (TerminalRenderTest + buffer regresyonu).
- **BEL (\u0007) → haptic**: `TerminalBuffer.onBell` callback → `vm.bellCount` → `KEYBOARD_TAP` titreşimi; OSC sonlandırıcı BEL'ler zil sayılmaz.
- **Pinch-to-zoom**: terminal yüzeyinde iki parmak `detectTransformGestures` → `fontScale` (0.8–2.0 sınırı, DataStore'a kalıcı); PTY resize zinciri ölçümü zaten takip ediyor.
- **Tuş şeridi snippet'ları**: Ayarlar → Tuş şeridi (`etiket=komut` her satır) → şeridin sonunda tuş olarak belirir; Enter kullanıcıda (iptal şansı kalır).
- **İlk kurulum sihirbazı**: host yokken Bağlantılar'da 3 adım — backend URL kaydet → `pocket-agent onboard` kopyala → QR/kod eşle.
- **Bağlantı diyaloğu**: PEM "Dosyadan al" (SAF OpenDocument), "Anahtar üret" (ed25519 → PKCS8 PEM alanı + public key kopyalama diyaloğu, BouncyCastle; EdEC arayüzleri API 33 istediğinden yalnız encoded baytlar), "Bağlantıyı sına" (SshProbe: ağ + auth probe, TOFU pin'ine dokunmaz).
- **~/.ssh/config importu**: Bağlantılar ⋮ FAB'ı → dosya seç → önizleme (wildcard'lar atlanır) → toplu ekleme; secret taşınmaz.
- **`pocketagent://add?host=…&user=…&port=…&name=…`**: deeplink ile host ekleme diyaloğu (doldurulmuş, parola bekler). Yalnız alan doldurur — komut çalıştırmaz.
- **Yedekleme**: Ayarlar → Yedekleme kartı — bağlantılar+ayarlar JSON (secret'lar asla dahil değil, `Backup` codec; version+şema sabit); içe aktarım ekler, silmez; geçersiz kayıtlar atlanır.
- **Hata banner'ı aksiyonları**: terminal hata banner'ında "Yeniden dene" (reconnect mümkünse) + "Bağlantıya git".
- **Tam ekran tuş şeridi (0.15.1)**: tam ekranda artık tuş şeridi (ctrl/esc/oklar/snippet'lar/yapıştır/klavye) altta görünür; `onFullscreenChange` callback'iyle Scaffold nav bar'ı gizlenir, şerit onun yerini alır. Sekme değişiminde bayrak sıfırlanır. `fullscreenKeepsKeyBarAndNotifies` UI testi.
- **Kullanıcı-performans turu (0.15.2)**: overlay aksiyon çubuğu tam ekranda da görünür (ara/paylaş/tam-ekran-toggle/reconnect/kapat — ayrı çıkış FAB'ı kaldırıldı); geri tuşu sırayla arama → tam ekran → dizin üstü kapatır (`BackHandler`, Files'ta köke kadar üst dizine iner); Dosyalar'da dokunulabilir breadcrumb yol çubuğu (segment → o derinliğe cd, otomatik sona kayar); bağlantı kartının tamamı tıklanabilir (Bağlan düğmesi ipucu olarak kalır); tuş şeridi 46dp + 13sp + KEYBOARD_TAP haptic (tuşlar ripple'sız olduğundan dokunma onayı haptic'ten gelir); aramada önceki-eşleşme butonu; nav bar 64dp/22dp ikon/10sp etiket + `selectable(Role.Tab)`; üst bar 56dp; Ayarlar → Hakkında sürümü manifest'ten okur (hardcode drift'i bitti).
- **Görsel/edge-to-edge turu (0.15.3)**: `WindowCompat.setDecorFitsSystemWindows(false)` + transparent çubuk renkleri + `isNavigationBarContrastEnforced=false` (enableEdgeToEdge'in elle eşdeğeri — activity-ktx eklenmedi); `ConsoleTopBar`/`ConsoleNavBar` kendi statusBars/navigationBars inset padding'ini uygular, `Scaffold.contentWindowInsets` sıfırlandı (barlar yokken ölü boşluk kalmasın); sistem çubuğu ikon kontrastı `theme.dark`'ı takip eder (`isAppearanceLight*`); tam ekranda artık üst bar da gizlenir (gerçek immersive — geriye yalnız oturum pill'leri + terminal + tuş şeridi); API 31+ native splash (`values-v31` platform attr'ları, `>_` glifi term_bg'de, yeni bağımlılık yok — `postSplashScreenTheme` framework attr'ı değil, aktivite aynı tema ile devam eder); Bağlantılar FAB'ları dikey kolona çevrildi (ana aksiyon altta); `ConsoleCard`'a `borderColor` parametresi — aktif bağlantı kartı ve bağlı Home hero'su primary border alır; Dosyalar breadcrumb'ı artık `surfaceVariant` kapsayıcı içinde (kontrol görünümü).
- **Agents akışı uçtan uca (0.15.4 — kritik düzeltme)**: Agents sekmesi hiç olay gösteremiyordu çünkü host tarafında pipeline yoktu — `hooks install` yalnızca `#` yorum marker'ı basıyordu (JSON config'leri de bozuyordu), `emit`/`daemon` komutları yoktu, `journal.Append` ve `POST /v1/hosts/{id}/events` yalnızca testlerde çağrılıyordu, systemd unit var olmayan `daemon` komutuna işaret ediyordu. Şimdi: `pocket-agent emit <src> <cat> <id> <msg>` (normalize→journal→daemon bildirimi, daemon yoksa doğrudan POST), `emit-hook <src> <cat> [json]` (agent hook'larının çağırdığı komut; stdin/arg JSON'dan session/id/mesaj çıkarır, asla agent'ı kırmaz), `daemon` (0600 unix socket + 15s tick'te offset'li journal→backend flusher; `service install`'un işaret ettiği komut artık var). Gerçek wiring: claude → `~/.claude/settings.json` hooks objesi (SessionStart/SessionEnd/Stop/Notification → session_started/ended/task_complete/approval_required, kullanıcı hook'ları korunur, idempotent); codex → `~/.codex/config.toml` `notify` satırı (marker blok içinde). JSON config'lere marker yazımı durduruldu + eski marker'lar onarılıyor (`StripMarkers`). Config'e `tenant` alanı eklendi (`set tenant`, varsayılan "default" — uygulama tenantToken'ıyla eşleşmeli; `POCKET_TENANT` env override). `doctor` artık daemon.sock ping sondası yapar. `onboard` daemon'ı `systemctl --user enable --now` ile hemen başlatır. Agents boş durumu host kurulum ipucu gösterir. Canlı doğrulama: emit→backend→GET /v1/events ve daemon socket→flush yolu test edildi.
- **Tüm agent kapsamı + canlı host kurulumu (devam, 0.15.4)**: config-wire edilemeyen 7 agent (opencode/kimi/grok/pi/omp/hermes/antigravity) dahil tümü artık daemon içindeki `/proc` watcher ile izlenir — `watch.Scan` comm + argv0 basename eşleşmesi yapar, `Tracker.Diff` PID geçişlerini session_started/session_ended olayına çevirir (event_id `proc:<pid>`). Config wiring genişledi: gemini + qwen (`~/.gemini|qwen/settings.json`, claude ile aynı matcher/hooks şeması — generic `InstallJSONHooks`), cursor (`~/.cursor/hooks.json` — version/hooks/sessionStart+sessionEnd+stop). Uninstall karşılıkları tam. Bu cihazda host canlı kuruldu: `/usr/local/bin/pocket-agent`, backend `http://127.0.0.1:8080`, tenant `default`, `pocket-agent.service` systemd user unit aktif (systemctl --user enable --now). Doğrulama: `exec -a claude sleep 30` → backend'de SESSION_STARTED göründü.
- **Agents teşhis satırı (0.15.5)**: Agents ekranında poll edilen backend URL + tenant görünür — "boş akış" durumunda kullanıcı `backend.local` gibi eski QR'dan kalma yanlış URL'yi hemen fark eder. Not: pair `--backend` verilmezse config'teki `backend_url` kullanılır; config boşken QR'a `https://backend.local` yazılır ve uygulama EventSync'i oraya bağlar (sessiz hata) — bu yaygın boş-akış nedenidir. Çözüm: Ayarlar → Backend = gerçek backend + tenant = host `tenant` config'i (varsayılan `default`).
- **Aktif agent listesi + oturuma git (0.15.5 devamı)**: Agents ekranı artık "aktif agent'lar" bölümü gösterir — `activeSessions(rows)`: sessionId başına son olay SESSION_STARTED ise aktif (ended aynı session'ı kapatır). Dokun → `onOpenAgent`: opaque host `h:sha256(...)` kayıtlı bağlantıya eşleştirilir (Kotlin `shortHash` Go ile birebir), yoksa en son kullanılan bağlantı → `sessions.open` → tmux oturumuysa `tmux attach -t <ad>` gönderilir. Host tarafında watcher agent'ın ppid zincirini tırmanıp tmux pane'inden oturum adını bulur (`SessionFor`), session alanı `tmux:<ad>` RAW gider (`opaqueSess` kuralı: tmux:/proc: raw, diğerleri hash). Tracker ended olayı aynı session'ı hatırlar. Canlı: tmux'ta `exec -a claude sleep` → `sess:tmux:agenttest` STARTED/ENDED backend'de göründü.
- **Terminal üst şeridi birleşmesi (0.15.5 devamı)**: terminali örten yüzen aksiyon overlay'i kaldırıldı; ara/paylaş/tam-ekran/yeniden-bağlan/kapat düğmeleri + durum metni (badge·pencere başlığı, ellipsis) artık en üstteki SABİT oturum şeridiyle aynı satırda — pill'ler `weight(1f)` kaydırılabilir, aksiyonlar sağa sabit. `SessionPillsRow` composable'a çıkarıldı; `active==null` dalında aynı şerit tek başına kalır. Hata banner'ı/arama çubuğu overlay offset'i 40→8dp (artık yüzen bar yok).
- **Paralel oturumlar + ikon (0.15.6)**: `SessionManager.open(conn, secret, forceNew=true)` — aynı host'ta dedupe'siz paralel terminaller; bağlantı kartında "Yeni oturum" düğmesi (isActive iken) + menü maddesi; pill'ler aynı conn.id'de `ad·1`/`ad·2` etiketlenir. Launcher ikonu yenilendi: düz `>_` yerine terminal penceresi (rounded gövde + başlık çubuğu + 3 nokta) içinde `>_` — `ic_launcher_foreground.xml` vector, monochrome da aynı drawable. (Not: Grok Imagine'a bu makineden erişim yok — ikon vektörel olarak üretildi; Grok'ta üretilen bir asset verilirse `drawable/` altına alınabilir.) Sonrasında Grok Imagine kullanıldı: `grok-imagine-image-2.0` (xAI API, `~/.grok/auth.json` OAuth token'ıyla) ile cepten bakan neon robot + elinde '>' terminali ikonu üretildi (kaynak: assets/icon-source.jpg) → `drawable-nodpi/ic_launcher_fg.png` (432², PIL ile bbox kırpıldı) adaptive foreground oldu; monochrome için eski vektör `ic_launcher_monochrome.xml`'e taşındı; splash aynı vektörü kullanmaya devam eder.

### CLI (host tarafı)

- `pocket-agent help` / `-h` / `--help`: tam komut ağacı + örnekler; argümansız çağrı usage basar.
- `pocket-agent onboard`: tek komutla service install + gateway + hooks + pair QR.
- `completion bash|zsh|fish`: statik completion scriptleri.
- `doctor`: gerçek sondalar (binary varlığı, sshd :22, disk, tmux/mosh-server, backend erişim, gateway port) + `--json`; `status`/`service status`/`context`/`pair` `--json` çıktısı; `context` JSON bug'ı düzeltildi; `logs` artık journalctl/tail gerçek çıktı.
- `hooks.Merge` idempotent düzeltmesi + `config.DefaultPath()` `POCKET_HOME` saygısı.

### DX / operasyon

- `Makefile` (`make help`), `scripts/live-env.sh` (up/down/env/status; ayakta port'ları sahiplenir), `scripts/pre-commit.sh` (gofmt+secret+license+vet+hızlı test), `AGENTS.md`, `CONTRIBUTING.md`, `environment.yaml`, README güncellendi (P00 iskelet değil, gerçek özet).
- CI: `go-version` 1.22→1.26 (toolchain uyumu), `canary` kapısı, Android job (JDK17 + lint + unit).
- `deploy/docker-compose/bootstrap.sh` (interaktif .env + compose up + healthz), `verify-backup.sh` (yedek restore provası), `docs/install.md` quickstart.
- Backend `GET /v1/metrics`: uptime + işlem sayaçları (events/pairings/claims/uploads/approvals), tenant verisi yok.

## 8. Sıradaki iş (önerilen sıra)

1. ~~`SshTransport` fake→cbssh takma~~ → **yapıldı (SSHJ, D012)**; sırada: cihazda PTY/resize doğrulaması, tam VT100 için termlib.
2. Mosh: native client exec + UDP wiring (bootstrap kanıtlı, D018) + Wi-Fi/LTE resync ölçümü (cihaz).
3. ~~Gateway wiring~~ → **tamam (D017/D019: direct-tcpip + /ls + /diff + /preview + Workspace UI + systemd unit)**.
4. FCM + 2-cihaz onay yarışı + 24h inbox senkron (FCM creds gerekli); backend X-Tenant iskeleti → passkey auth.
5. ~~Release zinciri~~ → **imzalı APK+AAB + CCS + REPRODUCING.md + build-release.sh tamam (D025)**; sırada: gerçek upload keystore (kullanıcı), cosign/SLSA, CI job.
6. ~~Compose UI testleri~~ → **5 ekran smoke yeşil (Terminal/Agents/Connections/Files/Settings)**; sırada: derin akışlar (diyaloglar, paylaşım intent).

## 9. Kurallar

- Conventional commits (`feat(Pxx): …`), her P için test + kapı yeşili zorunlu.
- `decisions.tsv` append-only (D001–D041 yazıldı).
- Marka/kod taraması yalnızca izinli dosyalardaki referanslara izin verir (`secret-scan.sh` kuralı); ham kopya yasaktır.
- `docs/reference/**` yayın paketine girmez (`check-packaging.sh`).
