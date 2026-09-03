# Pocket Agent — Tam Ürün Planı (v2, geliştirilmiş)

> Sürüm: v2.0 | Tarih: 2026-09-03 | Sahip: proje lideri | Durum: uygulama öncesi dondurulmuş plan
> Kaynak: `/root/dev/projects/pocket-agent/plan.md` — bu dosya tek doğruluk kaynağıdır.
> Okuma kılavuzu: Önce "Özet ve tamamlanma ölçütü"nü, sonra "Mimari ve sözleşmeler"i okuyun.
> Uygulamaya P00'dan başlayın, P01 olmadan P02+ koduna geçmeyin. Her adımın `Kabul kriterleri` geçmeden sonraki adıma geçmeyin.

## 0. Değişiklik günlüğü (bu iyileştirmenin özeti)

- v1'deki her P00–P18 adımı `Amaç / Kapsam / Kapsam-dışı / Çıktı / Kabul kriteri / Doğrulama / Güvenlik kapısı / Risk / Efor` şablonuna taşındı.
- Her adıma ölçülebilir Definition of Done, çalıştırılabilir komut ve beklenen artefakt yolu eklendi.
- P05–P10 (Android terminal) ve P11–P16 (agent özellikleri) grupları ayrı ayrı doğrulanabilir dikey dilimlere bölündü.
- Test planına izlenebilirlik matrisi (P → test → kanıt) ve privacy-canary zorunluluğu eklendi.
- Operasyonel hazırlık (backup/restore/rollback/incident) P18'den P02/P03'e de erken bağlandı.

## 1. Özet ve tamamlanma ölçütü

### 1.1 Hedef ve sınır

Hedef proje `/root/dev/projects/pocket-agent` altında kurulacak. Ana uygulama planı bu dosyada tutulacak.

Pocket Agent, Moshi'nin Android'de sunduğu **işleri** bağımsız kod ve yeni bir marka ile yeniden uygulayacak.
**Kopyalanmayacaklar:** Moshi adı, logosu, metinleri, ekran varlıkları, özel protokolü, uygulama kodu.
Bu sınır, Moshi'nin [kullanım şartlarındaki fikri mülkiyet maddesi](https://getmoshi.app/terms) nedeniyle zorunludur.
Clean-room kuralı: Moshi APK'sı decompile edilmeyecek, trafiği MITM ile çözülmeyecek, metin/varlık kopyalanmayacak.
Yalnızca herkese açık davranışsal bilgi kullanılabilir:
[Play Store açıklaması](https://play.google.com/store/apps/details?id=app.getmoshi.android),
[Moshi belgeleri](https://getmoshi.app/docs/introduction),
[gizlilik açıklaması](https://getmoshi.app/privacy),
[hook belgeleri](https://getmoshi.app/docs/hooks).

### 1.2 Ürünün üç parçası

1. **Android 10+ (minSdk 29) Kotlin + Jetpack Compose uygulaması.** SSH/Mosh/ET terminal, tmux/Zellij/Herdr, inbox/onay, Chat View, diff/dosya/preview, ses, paylaşım.
2. **Tek Go CLI + daemon (`pocket-agent-hook` + `pocket-agent` symlink).** Linux, macOS, WSL tam destek; Windows native deneysel. Hook adaptörleri, SQLite journal, outbound TLS, host gateway.
3. **Self-hosted Go backend + PostgreSQL + Caddy + dosya depolama.** Docker Compose ile tek komut kurulum. Çok kullanıcılı, davetli, Passkey-first.

### 1.3 Tamamlanma ölçütü (hepsi birlikte sağlanmadan "bitti" denemez)

| # | Ölçüt | Nasıl kanıtlanır |
|---|---|---|
| T1 | SSH+Mosh+ET ile gerçek hosta bağlanma | P06–P08 canlı test videosu + `doctor` çıktısı |
| T2 | tmux/Zellij/Herdr listele/aç/yeniden-bağlan | P09 matris videosu, process-kill sonrası resume logu |
| T3 | Keystore-dışı anahtar yok; host-key değişiminde hard-stop | P05+P06 güvenlik testi, canary raporu |
| T4 | Agent olay/onay/kota/Chat/diff/dosya/preview/bildirim/ses/ek akışları uçtan uca | P12–P16 on canlı yol (yol 7–10) |
| T5 | Terminal/transcript/diff/dosya/preview-body/SSH secret/ses backend'e gitmiyor | Privacy pcap + schema testi + canary testi |
| T6 | Kısa özet ≤24h TTL, onay kaydı sonuç/timeout'ta erken siliniyor, paylaşım URL 24h | DB TTL testi + temizleyici logu |
| T7 | Ağ değişimi / process ölümü / daemon restart sonrası güvenli toparlanma | P07/P08/P10 chaos testi |
| T8 | APK + CLI arşivleri + imzalı imajlar + SBOM + GPL kaynak paketi + kurulum belgesi üretilebiliyor | P18 release checklist + checksum + provenance |

Risk sınıfı: **yüksek.** SSH anahtarı + uzaktan komut onayı yönetildiği için güvenlik kapıları ertelenemez.
Tahmin: küçük ekip 7–10 ay; tek geliştirici 18–30 ay. P00'da iş-kırılım ve hız (velocity) ölçümüyle revize edilecek.

---

## 2. Mimari ve sözleşmeler

### 2.1 Depo yapısı (dondurulmuş)

```text
pocket-agent/
├── plan.md
├── README.md
├── LICENSE              # GPL-3.0-or-later
├── NOTICE               # 3. parti lisanslar
├── decisions.tsv        # append-only ADRs: id \t tarih \t karar \t gerekçe \t kanıt \t sonuç
├── apps/android/        # gradlew + app/
├── cmd/pocket-agent-hook/
├── host/                # daemon, gateway, journal, adapters
├── backend/             # Go API + migrations + Caddy
├── protocol/            # host.proto, control.proto, golden fixtures
├── native/mosh/         # upstream + yamalar (binary YOK)
├── native/et/
├── web/diff-viewer/
├── deploy/docker-compose/
├── docs/security/       # threat-model.md, privacy.md, licenses.md
├── scripts/             # setup-dev-environment.sh, verify-dev-environment.sh, dev-env.sh
└── tests/               # e2e, fuzz corpus, pcap, race logs
```

Kurallar:

- Lisans `GPL-3.0-or-later`. Upstream Mosh kaynağı + Android yamaları kaynak paketine dahil.
  Upstream koşulları için [resmî Mosh deposu](https://github.com/mobile-shell/mosh).
- Üçüncü taraf lisansları `NOTICE` + uygulama içi lisans ekranında.
- `docs/reference/` yalnızca kaynaklı referans kanıtı tutar, yayın paketine **dahil edilmez** (P00'da `.gitignore` + packaging testi).
- `local.properties`, keystore, `*.jks`, `.env` repoya girmez (pre-commit hook + CI secret-scan).

### 2.2 Veri yolları ve privacy sınırları

```text
Android ── SSH ───────────────► Host (terminal, SFTP, forward)
Android ── SSH bootstrap + UDP ► mosh-server (roaming)
Android ── SSH bootstrap + TCP ► etserver (genelde :2022)
Android ── SSH local forward ──► 127.0.0.1:24543 host gateway (diff/dosya/preview/chat)

Agent hook ── 0600 Unix socket ─► Host daemon (journal önce, gönderim sonra)
Host daemon ── outbound TLS ──► Backend ─► FCM ─► Android (yalnız özet + eventId)
```

Zorunlu kurallar:

1. Terminal baytları backend'den geçmez.
2. Diff/dosya/Chat View/preview yalnız SSH local-forward içindeki host gateway'den taşınır.
3. CLI hosttan backend'e yalnız outbound TLS açar; hostta inbound port açılmaz.
4. Backend **tutabilir:** kısa event özeti, usage snapshot, onay yönlendirme kaydı.
5. Backend **asla kabul etmez / loglamaz:** scrollback, tam transcript, kaynak kodu, dosya yolu, diff içeriği, preview HTTP body, SSH parolası, özel anahtar, ses kaydı. Bu, şema + runtime validator + pcap testiyle zorlanır.
6. TTL: event özeti 24h, onay kaydı sonuç/timeout'ta erken silinir, paylaşım URL 24h sonra geçersiz + dosya silinir.
7. FCM yükü yalnız `eventId` + minimal başlık taşır; detay her zaman backend'den TLS ile çekilir.

### 2.3 Android teknik yapısı (geliştirilmiş kararlar)

- Dil/stack: Kotlin, Compose (BOM sabitli), Coroutines/Flow, Room, DataStore, Hilt (veya manuel DI — P05'te `decisions.tsv`'ye yazılacak).
- minSdk 29, targetSdk güncel stabil, `compileSdk` sabitli. ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- SSH: önce ConnectBot `cbssh` pinli kaynak; OpenSSH/Dropbear/ProxyJump/SFTP/port-forward uyumluluk kapısını geçemezse aynı `SshTransport` arayüzü arkasında SSHJ. Karar P06'da otomatik testle verilir, `decisions.tsv`'ye işlenir.
- Terminal render: [ConnectBot termlib](https://github.com/connectbot/termlib) + libvterm; 10 MB burst'te bounded queue + input/resize önceliği.
- Mosh: `connectbot/mosh4android` tabanından kaynaktan derleme; doğrulanamayan hazır `.so` yasak. Reproducible build hashleri P07'de saklanır.
- ET: Android CMake hedefi kaynaktan; davranış [resmî ET protokol belgesi](https://github.com/MisterTea/EternalTerminal/blob/master/docs/protocol.md)'ne karşı test edilir.
- Bağlantılar foreground service'te; process ölümü sonrası komut **yeniden çalıştırılmaz**, yalnızca multiplexer session'a re-attach yapılır.
- Cihaz kimliği: donanım destekli StrongBox/TEE varsa Ed25519, yoksa P-256 (Android 10 varsayılanı). Biyometrik iptal → anahtar okunmaz, bağlantı başlamaz.
- `SavedConnection`'da secret/parola/özel anahtar **yok**; yalnızca referans + transport sırası + jump + UDP aralığı + ET portu + agent-forwarding flag'i.

Public yüzey (küçük tutulacak, P01'de `protocol/` ile uyumlu):

```kotlin
interface PocketClient {
  val voice: VoiceService
  suspend fun connect(profileId: ProfileId): HostConnection
  suspend fun openTerminal(target: TerminalTarget, policy: TransportPolicy): TerminalSession
}
interface HostConnection : AutoCloseable {
  val capabilities: HostCapabilities
  fun events(after: EventCursor?): Flow<AgentEvent>
  suspend fun perform(action: HostAction): ActionReceipt
  fun open(resource: HostResource): Flow<ResourceChunk>
}
interface TerminalSession : AutoCloseable {
  val selectedTransport: StateFlow<TerminalTransport>
  val frames: Flow<TerminalFrame>
  suspend fun send(input: TerminalInput)
  suspend fun resize(size: TerminalSize)
}
```

Branded ID'ler + sealed state'ler (P01'de dondurulur):

```text
HostId, DeviceId, ProfileId, SessionId, EventId, ApprovalId, CommandId,
EventCursor, Revision, ContentDigest
TerminalTransport = SSH | MOSH | ET
SessionProvider = SHELL | TMUX | ZELLIJ | HERDR
ApprovalState = PENDING | RESOLVED | EXPIRED
ConnectionState = CONNECTING | ACTIVE | SUSPENDED | RECONNECTING | CLOSED | FAILED
```

### 2.4 CLI ve host daemon (komut sözleşmesi dondurulmuş)

Binary: `pocket-agent-hook` + `pocket-agent` symlink. Komut ağacı (Cobra):

```text
pocket-agent [directory]
pocket-agent completion | context | cwd-list | diff | logs | probe
pocket-agent host setup|list|revoke|enable-ssh
pocket-agent hooks install|uninstall
pocket-agent pair|unpair
pocket-agent servers|servers kill
pocket-agent service install|status|uninstall
pocket-agent set | status | update | usage | version | doctor
```

Davranış sözleşmesi:

- `pocket-agent <dir>` idempotent tmux aç/bağlan; ikinci çağrı yeni session yaratmaz.
- `host setup` 5 dk tek kullanımlık Easy Pair QR; `hooks install/uninstall` yalnızca kendi işaretli bloklarını yönetir.
- `service install`: systemd user / launchd agent / Windows kullanıcı başlangıcı; `status --json` sürümlü kararlı şema.
- `doctor`: SSH, mosh-server, ET, tmux, Zellij, Herdr, gateway, backend, dosya izinleri için ayrı exit-code'lu kontroller.
- Config `~/.config/pocket-agent/config.toml`; secret Linux'ta `0600` dosya, macOS'ta Keychain; socket `0600` + aynı UID zorunluluğu.
- Journal-first: hook olayı önce SQLite WAL'a, sonra retry'li gönderim. Her mutation `CommandId` + expiry + expected `Revision`; aynı `CommandId` tekrarı aynı receipt'i döner (idempotency-key).
- `update` imzasız/checksumsuz uygulanmaz; rollback için son 3 sürüm tutulur.

Agent kapsamı v1 (Moshi hook matrisiyle eşleşir, yeniden yazım yok):

- Native Chat View: Claude Code, Codex, OpenCode, Cursor, Kimi, Grok Build, Pi, OMP, Hermes.
- Agent-aware event: Gemini CLI, Antigravity, Qwen.
- Diğerleri düz terminalde çalışır. **ANSI tarayarak agent semantiği üretilmez** — yalnızca resmî hook/transcript/adapter çıktısı.

### 2.5 Backend (tek Go servis + PostgreSQL, Redis yok)

Kimlik: ilk kullanıcı admin (bootstrap token tek kullanımlık), sonra davet; Passkey birincil, recovery code zorunlu, OIDC-PKCE opsiyonel; Android Credential Manager; access 15 dk, refresh dönen 30 gün; host/webhook tokenları `prefix + secret`, DB'de yalnız `argon2id/bcrypt` hash.

Tablolar: `users, passkey_credentials, recovery_codes, devices, hosts, host_memberships, host_credentials, pairing_sessions, agent_events, approval_requests, approval_actions, usage_snapshots, webhook_tokens, uploads, audit_log`. Her sorguda `tenant_id` zorunlu (RLS veya repository guard + test).

API (sürümlü `/v1`, OpenAPI P02'de dondurulur):

```text
POST /v1/auth/passkey/options
POST /v1/auth/passkey/verify
POST /v1/auth/refresh
POST /v1/devices
DELETE /v1/devices/{deviceId}
POST /v1/pairing-sessions
POST /v1/pairing-sessions/{code}/claim
GET  /v1/pairing-sessions/{code}
GET  /v1/hosts
DELETE /v1/hosts/{hostId}
POST /v1/hosts/{hostId}/events
GET  /v1/events?after={cursor}
POST /v1/approvals/{approvalId}/actions
GET  /v1/usages
POST /v1/webhooks/{token}
POST /v1/uploads
GET  /i/{shortCode}
DELETE /v1/uploads/{uploadId}
STREAM /v1/hosts/connect   # host→backend outbound, onay/olay multiplex
```

`AgentEventSummary` (whitelist — fazlası 400):

```text
eventId, opaqueHostId, opaqueSessionId, source, category,
title, message(≤256 chars), projectLabel, modelLabel, toolLabel,
contextPercent, requestDigest, revision, createdAt, expiresAt
```

Onay imzası `eventId|requestDigest|revision|decision|expiresAt|nonce`'u cihaz anahtarıyla bağlar; backend host stream'ine iletir; daemon atomic CAS ile yalnız `PENDING`'i sonuçlandırır.

### 2.6 Eşleme akışı (Easy Pair, geliştirilmiş)

1. `pocket-agent host setup` ön-koşul + SSH kontrolü yapar (açık hata mesajı + `doctor` önerisi).
2. Backend'de 5 dk TTL'li pairing session açılır (rate-limitli).
3. QR = `version|backendURL|code|hostID|ssh{user,host,port}|oneTimeSecret` (secret QR'da tek kez, loglanmaz).
4. Android tarar → ayrı SSH anahtarı + cihaz imza anahtarı üretir (Keystore, auth-bound).
5. Public anahtarlar pairing session'a `claim` edilir (idempotent aynı-claim, farklı-claim reddi).
6. CLI doğrular, `authorized_keys`'e ` # pocket-agent:<deviceId>:<tarih>` marker'lı tek satır ekler.
7. Host secret güvenli depoya yazılır; pairing session tüketilir (tekrar kullanılamaz).
8. İlk SSH'de TOFU fingerprint ekranı (SHA256 + görsel hash); kullanıcı onayı pinlenir.
9. Capability negotiation (major/minor) + `whoami/pwd/tmux probe` smoke testi.
10. Revoke: backend üyeliği + host `authorized_keys` satırı atomik kaldırılır; audit log yazılır.

---

## 3. Uygulama programı (genel bakış)

| Kimlik | Teslim | Bağımlılık | Kullanıcının gördüğü sonuç | Çıkış kapısı |
|---|---|---|---|---|
| P00 | Proje, plan, clean-room kaydı | — | İncelenebilir depo + kapsam matrisi | CI yeşil + lisans taraması temiz |
| P01 | Domain tipleri + protokol v1 | P00 | Kotlin/Go golden uyumu | Buf breaking-check + privacy schema testi yeşil |
| P02 | Backend kimlik + host dizini | P01 | Self-hosted giriş + cihaz kaydı | Tek-komut compose up + backup/restore kanıtı |
| P03 | CLI + daemon + servis | P01 | Kurulan/yeniden başlayan daemon | Reboot sonrası daemon ayakta + idempotent install |
| P04 | Easy Pair | P02,P03 | QR ile ilk bağlantı | Replay/race testleri yeşil + hard-stop kanıtı |
| P05 | Android temel app | P01 | Home/bağlantılar/güvenli kayıt | Lint+unit yeşil, secret-scan temiz |
| P06 | SSH dikey dilim | P05 | Gerçek hostta terminal | Gerçek cihazda `htop` çalışıyor videosu |
| P07 | Mosh | P06 | Ağ değişiminde süren terminal | Wi-Fi→LTE geçişte <3s resync |
| P08 | ET + fallback | P06 | UDP engelinde devam | Fallback matrisi yeşil |
| P09 | Multiplexer | P03,P06 | tmux/Zellij/Herdr seçimi | Kill+resume testi |
| P10 | Mobil terminal UX | P06 | Klavye/gesture/tema | ANR yok + p95 frame <32ms |
| P11 | Host gateway | P03,P06 | Diff/dosya/preview | Traversal/SSRF testleri yeşil |
| P12 | Hook adaptörleri | P03 | Event journal | Dedupe + kategori testi |
| P13 | Inbox/push/usage/approval | P02,P04,P12 | Çok cihazlı pano | İki-cihaz race + FCM p95 <15s |
| P14 | Chat View | P09,P11,P12,P13 | Mesaj görünümü | Terminal-chat aynı session kanıtı |
| P15 | SFTP/forward/paylaşım | P06,P11,P13 | Dosya + kısa URL | 10MB/24h sınır testi |
| P16 | Ses/autocomplete/deeplink | P10,P13,P14 | Dikte + kısayol | Uçak modunda dikte çalışıyor |
| P17 | Sertleştirme | P07–P16 | RC | Fuzz+perf+a11y raporu |
| P18 | Dağıtım + belgeler | P17 | Mağaza dışı release | İmzalı artefakt + SBOM + CCS paketi |

> Kural: her P, aşağıdaki şablonu doldurur. Şablonsuz PR merge edilmez.

**Adım şablonu:** Amaç → Önkoşul → Kapsam → Kapsam-dışı → Çıktılar → Kabul kriterleri → Doğrulama komutları → Güvenlik kapısı → Risk → Efor.

---

### P00 — Projeyi ve kanıt düzenini oluştur

**Amaç:** Boş ama derlenebilir, lisanslı, CI'lı monorepo + clean-room kanıtı.
**Önkoşul:** Yok.
**Kapsam:**

- `/root/dev/projects/pocket-agent` + `git init`, `main` korumalı, conventional-commit.
- `plan.md(v2), README.md, LICENSE(GPL-3.0-or-later), NOTICE, .gitignore, .gitattributes, decisions.tsv` ekle.
- `docs/reference/README.md`: Play Store + Moshi docs linkleri, ekran görüntüleri **yok-yayın** notu, `deploy`/`app` paketlemesinden exclude testi.
- `docs/security/threat-model.md` iskeleti (STRIDE: spoofing SSH/FCM, tampering approval, info-disclosure transcript, DoS gateway).
- `docs/security/licenses.md` envanter iskeleti (Mosh GPLv3, cbssh, SSHJ, ET, whisper.cpp).
- CI iskeleti: `lint / secret-scan / license-check` (gitleaks + reuse + fossa/ort veya `go-licenses`).
- `decisions.tsv` ilk 5 kayıt: marka-kopya-yok, GPL, monorepo, minSdk29, tek-replica backend.

**Kapsam-dışı:** Herhangi bir feature kodu.
**Çıktılar:** Yukarıdaki dosyalar + yeşil CI run linki.
**Kabul kriterleri:**

- [ ] `git log` ilk commit + tag `p00-done`.
- [ ] CI'da secret-scan + lisans kontrolü yeşil.
- [ ] `docs/reference/*` release paketine dahil değil (packaging testi).
- [ ] `decisions.tsv` append-only (force-push koruması açık).

**Doğrulama:**

```bash
git log --oneline -5 && git tag | grep p00
./scripts/secret-scan.sh && ./scripts/license-check.sh
```

**Güvenlik kapısı:** Moshi kodu/varlığı repoda yok (`rg -i moshi --glob '!plan.md'` boş).
**Risk:** Kapsam kayması → azaltma: özellik matrisi dondurulur, değişiklik ADR gerektirir.
**Efor:** 2–3 gün.

### P01 — Sözleşmeleri koddan önce sabitle

**Amaç:** Kotlin↔Go↔Backend aynı dili konuşsun; breaking change erken yakalansın.
**Önkoşul:** P00.
**Kapsam:**

- `protocol/host.proto, control.proto` (Buf v1, `buf.yaml`, breaking-check `against: main`).
- Branded ID'ler (string newtype + regex), sealed state'ler, `HostCapabilities{ssh,mosh,et,tmux,zellij,herdr,gateway,chat}` + `unknown → ignore` kuralı.
- Negotiation: `major` eşit değilse structured özellikler kapalı, SSH terminal açık; `minor` farkı warning.
- `ResourceChunk{offset,length,sha256,eof}` + 1MB chunk cap + resume.
- `AgentEventSummary` whitelist validator (Go + Kotlin aynı JSON Schema'dan üretilir).
- `protocol/fixtures/golden-*.json` + iki tarafta da çalışan golden test.
- Privacy schema testi: yasak alan (`transcript, code, diff, filePath, previewBody, password, privateKey, audio`) içeren payload şema seviyesinde reddedilir.
- OpenAPI taslağı `backend/openapi.yaml` iskeleti.

**Kapsam-dışı:** Gerçek transport implementasyonu.
**Çıktılar:** `protocol/*`, generated `gen/kotlin + gen/go`, golden test raporu.
**Kabul kriterleri:**

- [ ] `buf lint + buf breaking` yeşil.
- [ ] Kotlin ve Go golden fixture'ı bayt-bayt aynı yorumluyor.
- [ ] Unknown capability testi yeşil; major-mismatch fallback testi yeşil.
- [ ] Privacy schema testi yasak alanı reddediyor.

**Doğrulama:**

```bash
buf lint && buf breaking --against '.git#branch=main'
go test ./protocol/... -v
cd apps/android && ./gradlew :protocol:testDebugUnitTest
```

**Güvenlik kapısı:** Validator client-side değil, backend + daemon'da da zorunlu (defense in depth).
**Risk:** Proto churn → azaltma: `experimental` field'lar `v1alpha` namespace'inde.
**Efor:** 1 hafta.

### P02 — Self-hosted backend temelini kur

**Amaç:** Tek komutla ayağa kalkan, tenant-izole, TTL'li backend.
**Önkoşul:** P01.
**Kapsam:**

- Go API (chi/echo — P02 başında ADR), PostgreSQL 16 migrations (golang-migrate), Caddy TLS (otomatik HTTPS).
- Auth: admin bootstrap (tek kullanımlık token) → invite → Passkey (webauthn) → recovery codes (argon2id hash) → opsiyonel OIDC-PKCE.
- `tenant_id` her tabloda + her sorguda guard; RLS + repository testi (tenant-A tenant-B'yi okuyamaz).
- Cihaz/host/üyelik + token (`prefix_secret`, DB'de hash, prefix ile lookup).
- TTL temizleyiciler (1 dk cron): `agent_events`, `approval_requests`, `uploads`; silinen kullanıcıda cascade wipe + audit.
- `deploy/docker-compose/{docker-compose.yml,Caddyfile,.env.example}` + `backup.sh/restore.sh` (pg_dump + volume snapshot).
- Rate-limit (pairing claim, webhook, upload) + audit log append-only.
- FCM iskeleti: service-account mount, topic `events-{tenant}`, payload minimal.

**Kapsam-dışı:** Gerçek agent iş mantığı, S3 (sonraya adapter).
**Çıktılar:** Çalışan `docker compose up`, OpenAPI dondurulmuş, backup/restore videosu.
**Kabul kriterleri:**

- [ ] `docker compose up -d` sonrası `GET /v1/healthz` 200.
- [ ] Admin bootstrap → invite → passkey-verify uçtan uca (e2e script).
- [ ] Tenant izolasyon testi yeşil; token plaintext DB'de yok (`SELECT` denetimi).
- [ ] TTL testi: 24h+1sn kaydı temizleyici siliyor.
- [ ] Backup → wipe → restore sonrası login çalışıyor.

**Doğrulama:**

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d --build
go test ./backend/... -run 'TestTenant|TestTTL|TestAuth' -v
./deploy/docker-compose/backup.sh && ./deploy/docker-compose/restore.sh --dry-run
```

**Güvenlik kapısı:** `POST /v1/hosts/{id}/events` whitelist dışı alanı 400 + loglamadan drop.
**Risk:** Tek replica SPOF → kabul edildi, P18'de HA notu; pg data volume yedekleniyor.
**Efor:** 2–3 hafta.

### P03 — CLI ve daemon temelini kur

**Amaç:** Güvenilir, idempotent, journal-first host ajanı.
**Önkoşul:** P01 (P02 ile paralel başlanabilir, entegrasyon P04'te).
**Kapsam:**

- Cobra komut ağacı (yukarıdaki liste) + `--json --non-interactive` global flag'ler.
- TOML config + `0600` secret + `0600` Unix socket (`~/.pocket-agent/daemon.sock`), symlink attack koruması (`O_NOFOLLOW`, UID check).
- SQLite WAL journal (`events, outbox, approvals`) + exponential backoff'lu flusher.
- Servis adaptörleri: systemd user unit, launchd plist, Windows Startup (deneysel flag `--experimental-windows`).
- Idempotency: `install/status/probe/logs/set/service/update` ikinci çalışta no-op veya converge; yarım kurulum testi (`kill -9` ortasında → rerun yeşil).
- `update`: signed manifest (cosign/ed25519) + sha256; imzasız update reddi; son 3 sürüm `/opt/pocket-agent/versions/` altında.
- `doctor --json` 9 ayrı check + exit code bitmask.

**Çıktılar:** `pocket-agent-hook` binary (linux/mac), `doctor` JSON örneği, servis logları.
**Kabul kriterleri:**

- [ ] Temiz VM'de `install → status → probe` yeşil, reboot sonrası daemon ayakta.
- [ ] Yarım-kurulum rerun testi yeşil.
- [ ] İmzasız `update` reddediliyor; rollback son sürüme dönüyor.
- [ ] Socket başka UID'den `EACCES` veriyor.

**Doğrulama:**

```bash
go test ./host/... -v
./pocket-agent doctor --json | jq .
./pocket-agent service install && sudo reboot # + manuel: status
```

**Güvenlik kapısı:** Hook command injection testi (zararlı cwd/config) + 10MB payload cap.
**Risk:** macOS launchd/Windows farkları → per-OS e2e matrix.
**Efor:** 2–3 hafta.

### P04 — Easy Pair ve manuel bağlantıyı tamamla

**Amaç:** 60 saniyede ilk bağlantı; replay/race'e dayanıklı.
**Önkoşul:** P02 + P03.
**Kapsam:**

- QR state machine `PENDING→CLAIMED→CONSUMED/EXPIRED`; 5 dk TTL, tek kullanım, aynı-claim idempotent, farklı-claim 409.
- `authorized_keys` yönetimi: `command="pocket-agent-gate",no-agent-forwarding?` değil — düz pubkey + `# pocket-agent:<deviceId>` marker; `revoke` yalnız o satırı siler (regex + backup).
- Manuel form: password (geçici, RAM-only, zeroize), key import (encrypted PEM parola sorar), ProxyJump (`user@jump:port`), özel port.
- TOFU pinleme: `~/.pocket-agent/known_hosts` ayrı dosyası; fingerprint değişiminde **hard-stop** + "revoke + re-pair" yönergesi, fallback yok.
- E2E: QR tara → `whoami/pwd/tmux probe` smoke → session tüketildi kanıtı.

**Kapsam-dışı:** Mosh/ET auto-tune (P07/P08).
**Çıktılar:** QR ekranı videosu, `host list/revoke` çıktıları.
**Kabul kriterleri:**

- [ ] QR replay 2. kullanımda reddediliyor.
- [ ] Concurrent double-claim'de biri 200-diğeri 409 (race testi).
- [ ] `revoke` sonrası SSH `Permission denied` + backend üyeliği yok.
- [ ] Host-key değişiminde bağlantı başlamıyor + kullanıcı uyarısı.

**Doğrulama:**

```bash
go test ./backend/... -run TestPairingRace -count=20
go test ./host/... -run TestAuthorizedKeys -v
# cihazda: QR tara → smoke test logu
```

**Güvenlik kapısı:** QR secret loglanmıyor (canary taraması) + 5 denemeden sonra pairing throttle.
**Risk:** Kullanıcı `authorized_keys`'i elle bozarsa → `doctor` onarım önerir, otomatik overwrite yok.
**Efor:** 1–2 hafta.

### P05 — Android temel uygulama

**Amaç:** Güvenli yerel kayıt + navigasyon iskeleti.
**Önkoşul:** P01.
**Kapsam:**

- Compose Nav: Home, Connections, Active Sessions, Agents, Files, Settings (placeholder içerikle ama gezilebilir).
- CRUD: bağlantı ekle/düzenle/sil/sırala (Room + DataStore, secret yok).
- Key yönetimi: Keystore-backed AES-GCM ile sarılı SSH anahtarı (StrongBox varsa), parola autofill kapalı, ekran görüntüsü `FLAG_SECURE` (ayarlanabilir), clipboard 60sn temizleme.
- Biyometrik gate (`BiometricPrompt`, cryptoObject-bound); iptal → anahtar kapalı.
- `applicationId` dondur (örn. `dev.pocketagent.android`), imza config iskeleti, `lint + unit` yeşil.
- Erişilebilirlik iskeleti: TalkBack label'ları, min dokunma 48dp.

**Kabul kriterleri:**

- [ ] `:app:lintDebug + testDebugUnitTest` yeşil.
- [ ] Anahtar Keystore dışında bulunamıyor (heap-dump canary testi).
- [ ] Biyometrik iptalde bağlantı başlamıyor.

**Doğrulama:**

```bash
cd apps/android && ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Güvenlik kapısı:** Secret-scan + `strictMode` disk-write ihlali yok.
**Efor:** 2 hafta.

### P06 — SSH terminal dikey dilimi (ilk çalışan değer)

**Amaç:** Gerçek hostta gerçek terminal — ürünün "hello world"ü.
**Önkoşul:** P05.
**Kapsam:**

- `SshTransport` arayüzü + cbssh implementasyonu (+SSHJ fallback kararı testi: OpenSSH/Dropbear/ProxyJump/SFTP/forward matrisi).
- PTY (`xterm-256color`), resize, stdin/stdout/stderr, keepalive, SFTP client iskeleti, local-forward API.
- Foreground service + notification + WakeLock yok (pil dostu).
- Hata taksonomisi: `AUTH_FAILED / HOST_KEY_CHANGED / TIMEOUT / REFUSED` — yalnızca son ikisinde retry; ilk ikisinde **fallback yok**.
- Smoke: `echo $TERM && whoami && pwd && ls`.

**Kabul kriterleri:**

- [ ] Gerçek cihazdan gerçek hostta `htop` açılıp scroll yapılabiliyor (video).
- [ ] Host-key değişimi hard-stop veriyor.
- [ ] Yanlış parola açık hata + 3 denemede throttle.

**Doğrulama:** P05 komutları + `connectedDebugAndroidTest` + manuel video.
**Güvenlik kapısı:** Parola/özel anahtar loga düşmüyor (canary).
**Efor:** 3–4 hafta (en riskli Android adımı).

### P07 — Mosh transport

**Amaç:** IP değişiminde yaşayan terminal.
**Önkoşul:** P06.
**Kapsam:**

- SSH ile `mosh-server new -p <range>` bootstrap, anahtar SSH içinde taşınır (diske yazılmaz, zeroize).
- `connectbot/mosh4android` jni kaynaktan; 3 ABI; reproducible hash kaydı.
- Sıra: Mosh → (başarısızsa) ET → SSH. UDP engeli/eksik binary'de otomatik düş.
- Başlıkta `MOSH • roaming` + fallback nedeni (snackbar + log).
- Resync SLO: <3s güncel ekrana dönüş (ölçümlü).

**Kabul kriterleri:**

- [ ] Wi-Fi→LTE geçiş videosu + resync <3s.
- [ ] `%packet-loss 20` altında kullanılabilir (mosh test harness).
- [ ] Mosh key heap/log/crash'te yok.

**Efor:** 2–3 hafta.

### P08 — ET transport ve fallback orkestrasyonu

**Amaç:** UDP kapalıyken bile kopmayan terminal.
**Önkoşul:** P06 (P07 ile paralel yürüyebilir).
**Kapsam:**

- ET bootstrap (`etserver -p 2022` veya özel port), TLS değil SSH-tunneled başlatma.
- `TransportPolicy{preferredOrder, timeoutMs, allowFallback}` + `TransportSelector` (auth/host-key hatasında fallback **yasak**).
- Fallback matrisi testi: `mosh-ok / udp-blocked / no-mosh-server / et-ok / tcp-blocked / ssh-only` 6 senaryo.
- Başlık rozeti + `adb logcat` structured fallback nedeni.

**Kabul kriterleri:**

- [ ] 6'lı matris yeşil; SSH fallback <10s; ET reconnect <5s.
- [ ] ET protokol uyumluluk testi [resmî belge](https://github.com/MisterTea/EternalTerminal/blob/master/docs/protocol.md)'ye karşı yeşil.

**Efor:** 2 hafta.

### P09 — Oturumlar ve multiplexer

**Amaç:** Ölümden dönen oturumlar.
**Önkoşul:** P03 + P06.
**Kapsam:**

- Adapter arayüzü `SessionProvider{list, attach, create, kill}`; tmux + Zellij tam, Herdr **entegrasyon** (yeniden yazım yok, yoksa gizlenir).
- Capability bazlı UI: desteklenmeyen provider gösterilmez.
- Switcher/close/reconnect/last-session-resume; process ölümü sonrası re-attach (komut tekrarı yok).
- tmux kısayolları (`C-b ...`) + Herdr deep-link.

**Kabul kriterleri:**

- [ ] `pkill -9 tmux-client` sonrası resume aynı panele dönüyor.
- [ ] Herdr yoksa UI'da Herdr seçeneği yok (capability testi).

**Efor:** 2 hafta.

### P10 — Mobil terminal deneyimi

**Amaç:** Günlük kullanılabilir mobil terminal.
**Önkoşul:** P06.
**Kapsam:**

- Klavye: Ctrl/Esc/Tab/Alt/oklar/F1–F12, tmux prefix, custom shortcut editor, hardware keyboard map.
- Gesture: pinch-zoom (font 8–24sp), swipe-scroll, double-tap seçim; IME/CJK/emoji/true-color/selection/link/OSC52.
- Tema/font/palette ayarları + scrollback limiti (varsayılan 50k satır, ayarlanabilir).
- Performans: bounded queue, 10MB burst'te ANR yok, p95 frame <32ms, RSS <250MB.

**Kabul kriterleri:**

- [ ] 10MB ANSI burst testi ANR'siz + input öncelikli.
- [ ] Büyük font + TalkBack ile terminal okunabilir.
- [ ] Fold/tablet düzeni kırılmıyor.

**Efor:** 3 hafta.

### P11 — Host gateway (diff/dosya/preview)

**Amaç:** Kodu gör, dosyayı aç, localhost'u telefonda göster — hepsi SSH içinde.
**Önkoşul:** P03 + P06.
**Kapsam:**

- Gateway yalnız `127.0.0.1:24543`, token `0600` dosyadan; Android yalnız local-forward ile erişir (doğrudan TCP reddedilir).
- Diff: working-tree/staged/unstaged/untracked + geçmiş commit; binary render yok (boyut + tip rozeti).
- Dosya tarayıcı: workspace-root + normalized relative path; `..`, symlink-escape, `/etc` dışı reddi.
- Preview: yalnız loopback listener'lar (`127.0.0.1/::1`); picker'da process/PID/bind/port/framework; WebView device-loopback'e, dış navigasyon onaya.
- Rate-limit + 1MB chunk + audit.

**Kabul kriterleri:**

- [ ] Traversal (`../../etc/passwd`) + symlink-escape + SSRF (`169.254.169.254`) testleri yeşil.
- [ ] Gateway internetsiz (host firewall block) çalışıyor.

**Efor:** 2–3 hafta.

### P12 — Agent hook adaptörleri

**Amaç:** Her agent'tan tek tip event.
**Önkoşul:** P03.
**Kapsam:**

- 9 native + 3 aware adaptör (Claude/Codex/OpenCode/Cursor/Kimi/Grok/Pi/OMP/Hermes + Gemini/Antigravity/Qwen); sahipli bloklarla config'e yazım (`# pocket-agent begin/end`).
- Normalize kategoriler: `approval_required, task_complete, session_started, session_ended, tool_running, tool_finished, error`.
- Dedupe `sourceEventId` + session-bazlı inbox birleştirme; oversize payload (>256ch message) kırp + `truncated:true`.
- Journal-first + offline retry; adapter crash'i hostu öldürmez (supervisor).

**Kabul kriterleri:**

- [ ] Codex + Claude uçtan uca event üretiyor (fixture replay testi).
- [ ] Aynı event 2 kez işlenmiyor; ANSI taraması yok (grep yasağı testi).

**Efor:** 2–3 hafta.

### P13 — Inbox, push, usage ve approvals

**Amaç:** Çok cihazlı agent panosu.
**Önkoşul:** P02 + P04 + P12.
**Kapsam:**

- Inbox: session-bazlı gruplama, okundu/çözüldü, 24h TTL geri sayımı.
- Push: FCM data-message (`eventId`), detay TLS-pull; iki-cihaz race: ilk karar kazanır, diğeri `RESOLVED` görür (CAS).
- Approval: `revision+digest` imzalı karar, expiry + nonce; timeout'ta `EXPIRED` + host'a iptal.
- Usage: agent/hesap/host/pencere/%/reset; snapshot 24h.
- Offline kuyruk + `EventCursor` ile catch-up.

**Kabul kriterleri:**

- [ ] İki cihaz aynı onaya basınca tek kazanan (race logu).
- [ ] Event→FCM p95 <15s (test ortamı).
- [ ] Expired onay hostta çalışmıyor.

**Efor:** 2–3 hafta.

### P14 — Chat View

**Amaç:** Terminaldeki agentı mesaj gibi oku.
**Önkoşul:** P09 + P11 + P12 + P13.
**Kapsam:**

- Modeller: message/thinking/tool-card/plan/question/error/mini-diff/working-state; terminal-chat aynı `SessionId`.
- Desteklenmeyen içerik → "Terminalde aç" deep-link (veri kaybı yok).
- Gateway üzerinden streaming; backend'e tam metin gitmez (yalnız özet).
- Performans: 1000 mesajda jank yok (lazy list + diffing).

**Kabul kriterleri:**

- [ ] Aynı session terminal ve chat'te senkron (video).
- [ ] Mini-diff gateway'den geliyor, backend pcap'te yok.

**Efor:** 3 hafta.

### P15 — SFTP, forwarding ve paylaşım

**Amaç:** Dosyayı al/gönder/paylaș.
**Önkoşul:** P06 + P11 + P13.
**Kapsam:**

- SFTP upload `~/.pocket-agent/uploads/` (10MB cap, MIME sniff, executable bit korunmaz).
- Kamera/galeri/dosya/clipboard ekleri; paylaşım `POST /v1/uploads → /i/{short}` 24h TTL + tek-tık revoke.
- Port-forward yöneticisi (kayıtlı loopback hedefler, çakışma uyarısı).
- Kotalar + audit.

**Kabul kriterleri:**

- [ ] 10MB+1B reddediliyor; 24h sonra URL 410 + dosya silinmiş.
- [ ] SFTP traversali reddediliyor.

**Efor:** 2 hafta.

### P16 — Ses, autocomplete ve deep link

**Amaç:** Telefondan hızlı prompt.
**Önkoşul:** P10 + P13 + P14.
**Kapsam:**

- whisper.cpp cihaz-içi dikte (model ilk açılışta indirilir, uçak modunda çalışır); BYOK OpenAI-compatible endpoint **opt-in** olmadan ağa çıkmaz (kill-switch testi).
- Dikte düzeltmeleri + terminal/chat modu + dosya/slash-command autocomplete (gateway'den, debounce'lu).
- Deep-link: `pocketagent://tmux?...`, `pocketagent://herdr?...` + App Links doğrulaması.

**Kabul kriterleri:**

- [ ] Uçak modunda dikte çalışıyor; BYOK kapalıysa pcap'te ses paketi yok.
- [ ] Deep-link imzasız parametreyle shell çalıştırmıyor.

**Efor:** 2–3 hafta.

### P17 — Güvenlik, performans ve erişilebilirlik (RC kapısı)

**Amaç:** Release adayı sertliği.
**Önkoşul:** P07–P16.
**Kapsam:**

- Threat-model'deki her sınır için otomatik test (tenant-escape, forged sig, replay, pairing-race, traversal, SSRF, injection, canary-leak, imza hatası).
- Fuzz: proto + adapter parser'lar (libFuzzer/go-fuzz, 1h corpus, crash yok).
- A11y: TalkBack, büyük font, Fold/tablet; performans SLO'ları (aşağıdaki tablo) ölç + `docs/perf.md`'ye işle.
- RC işaretleme + dondurma (string-freeze, proto-freeze).

**Kabul kriterleri:** Aşağıdaki eşiklerin hepsi yeşil + raporlar `tests/` altında.

| Metrik | Eşik |
|---|---|
| Terminal render p95 | <32ms |
| 10MB burst | ANR yok |
| App RSS (aktif terminal) | <250MB |
| Daemon idle CPU / RSS | <%1 / <75MB |
| Backend p95 @100rps | <300ms |
| Mosh resync | <3s |
| ET reconnect | <5s |
| SSH fallback | <10s |
| Event→FCM p95 | <15s |

**Efor:** 3–4 hafta.

### P18 — Dağıtım ve belgeler (GA kapısı)

**Amaç:** Kurulabilir, güncellenebilir, denetlenebilir release.
**Önkoşul:** P17.
**Kapsam:**

- Android: upload keystore repo-dışı `0600`, env ile imza, AAB+APK, internal testing kanalı; `apksigner verify + sha256` raporu.
- CLI: linux/mac amd64+arm64 arşivleri, WSL doğrulama, Windows deneysel etiket, Homebrew tap, shell + PowerShell installer.
- İmajlar: cosign imzalı, SBOM (CycloneDX), provenance (SLSA), checksum.
- GPL CCS paketi (Mosh+ET+yamalar dahil) + `REPRODUCING.md`.
- Belgeler: `install.md, backup-restore.md, update-rollback.md, revoke.md, incident-response.md, threat-model.md, privacy.md`.
- Moshi config/pairing **otomatik taşınmaz** — temiz kurulum + yeni eşleme (kullanıcıya açık not).

**Kabul kriterleri:**

- [ ] Temiz VM + temiz telefonda belgelerle kurulum 30 dk içinde (timed run).
- [ ] SBOM + imza + provenance doğrulanabiliyor.
- [ ] Rollback testi: N→N-1→N veri kaybısız.

**Doğrulama:**

```bash
cd apps/android && ./gradlew :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease :app:bundleRelease
apksigner verify --verbose --print-certs apps/android/app/build/outputs/apk/release/app-release.apk
sha256sum apps/android/app/build/outputs/apk/release/app-release.apk
cosign verify $IMAGE && syft $IMAGE -o cyclonedx-json | head
```

**Efor:** 2–3 hafta.

---

## 4. Geliştirme ortamı ve APK üretme

Headless derleme (uzak makine derler/test eder/APK üretir); Android Studio yalnızca yerel preview için.

### 4.1 Araç zinciri

```bash
cd /root/dev/projects/pocket-agent
sudo ./scripts/setup-dev-environment.sh   # idempotent: JDK, Go, SDK, NDK, CMake, Buf, protoc
source /root/dev/projects/pocket-agent/scripts/dev-env.sh  # mevcut shell
./scripts/verify-dev-environment.sh       # java+go+proto+3 ABI .so + imzalı smoke APK
# çıktı: build/toolchain-smoke/toolchain-smoke.apk (yalnız toolchain kanıtı)
```

### 4.2 Android projesi (P05'te oluşur)

```text
apps/android/{gradlew,gradle/wrapper,settings.gradle.kts,build.gradle.kts,app/build.gradle.kts,app/src/}
```

Sürümler sabitli (AGP/Kotlin/Compose-BOM/compileSdk/Build-Tools/NDK/CMake); sistem Gradle'ı yasak, yalnız `./gradlew`.
`local.properties` repoya girmez (`sdk.dir=/opt/android-sdk` yerelde).

### 4.3 Günlük komutlar

```bash
cd /root/dev/projects/pocket-agent/apps/android
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
./gradlew clean :app:assembleDebug   # temiz derleme
# gradle.properties: org.gradle.jvmargs=-Xmx3g (11GiB makinede artırma ölçüsüz yasak)
```

Cihaza kur:

```bash
adb devices -l
./gradlew :app:installDebug
# uzak makineyse: scp <sunucu>:/root/dev/projects/pocket-agent/apps/android/app/build/outputs/apk/debug/app-debug.apk . && adb install -r app-debug.apk
# wireless debugging yalnız aynı LAN; WAN'a ADB açma
./gradlew :app:connectedDebugAndroidTest
adb logcat  # P05 sonrası: adb logcat --pid="$(adb shell pidof -s "$APPLICATION_ID")"
```

Release (P18, secret'lar env'den, komut satırına parola yazma):

```bash
./gradlew :app:lintRelease :app:testReleaseUnitTest
./gradlew :app:assembleRelease :app:bundleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk app/build/outputs/bundle/release/app-release.aab
```

Env: `POCKET_AGENT_UPLOAD_KEYSTORE, _ALIAS, _STORE_PASSWORD, _KEY_PASSWORD`. Başarısız imza/test → yayın yok; raporda commit SHA + görevler + checksum + native hashler.

---

## 5. Test ve kabul planı (izlenebilirlik)

Her PR: unit + canlı + privacy + perf'ten ilgili olanlar zorunlu; "derlendi" yeterli değil. Her P'nin kabul kutuları işaretlenmeden merge yok.

### 5.1 On canlı yol (her RC'de koşulur)

1. Temiz kurulum + QR Easy Pair.
2. Password SSH + yanlış parola.
3. Keystore key + biyometrik iptal + host-key değişimi.
4. Mosh'ta Wi-Fi↔mobil geçiş.
5. UDP kapalı → ET→SSH fallback.
6. tmux/Zellij/Herdr seç + kill sonrası resume.
7. Codex/Claude hook + iki-cihaz onay yarışı + timeout.
8. Chat View + diff + dosya.
9. Preview + SFTP ek + kısa URL + FCM.
10. Yerel dikte + BYOK opt-in + tema/büyük-font/CJK/hardware-klavye.

### 5.2 Güvenlik testleri (P17'de otomatik, her PR'da ilgili subset)

- Tenant-escape (cihaz/host/event), forged/iptal cihaz imzası, replay/expired approval.
- Pairing QR replay + race, path traversal + symlink-escape + preview SSRF, hook injection + oversize.
- Secret canary: log/analytics/crash + backend pcap (terminal/transcript/diff/dosya/SSH-secret/ses) + Mosh-key disk/log sızıntısı.
- Installer/update imza/checksum hatası.

### 5.3 Performans eşikleri

Bkz. P17 tablosu. Ölçüm komutları `tests/perf/README.md`'de; sonuçlar `tests/perf/<tarih>/` altında video+log+pcap ile saklanır.

### 5.4 Kabul artefaktları (her RC)

- Emulator + gerçek cihaz videoları, ağ-değişim/packet-loss kayıtları, privacy pcap raporu.
- Fuzz corpus+sonuç, iki-cihaz race logu, reproducible native hashler, SBOM+lisans çıktısı.
- Her PR: test komutları + loglar + ekran görüntüleri + kesin head SHA.

### 5.5 İzlenebilirlik (P → test → kanıt örneği)

| P | Test | Kanıt yolu |
|---|---|---|
| P01 | golden + privacy-schema | `tests/protocol/` |
| P04 | pairing-race (20x) | `tests/e2e/pairing-race.log` |
| P06 | host-key hard-stop | `tests/security/hostkey.log` |
| P07 | wifi-lte resync | `tests/perf/mosh-resync.mp4` |
| P11 | traversal/ssrf | `tests/security/gateway.log` |
| P13 | approval-race | `tests/e2e/approval-race.log` |
| P18 | reproducible+sbom | `dist/checksums.txt, sbom.json` |

---

## 6. Varsayımlar ve karar gerekçesi (geliştirilmiş)

- **Tek mobil hedef Android.** iOS/Watch kapsam-dışı (P00 ADR).
- **Self-hosted + çok kullanıcılı;** abonelik/Play Billing/paywall yok.
- **Backend tek replica;** PostgreSQL tek kaynak; Redis yok (işletme basitliği).
- **FCM için** kurucu kendi Firebase service-account'unu verir; FCM yoksa polling fallback (P13'te flag).
- **Caddy** alan adı + TLS yönetir; upload varsayılan Docker volume, S3 adapter sonra.
- **Herdr yeniden yazılmaz;** varsa entegrasyon, yoksa gizlenir.
- **Moshi token/pairing/backend kullanılmaz;** mağaza ekranları yalnızca davranışsal kanıt, üründe yeni marka/tasarım/metin.
- **Kapsamdan çıkarılmayacaklar** (full Android eşliği): BYOK dikte, ET, çok-cihaz inbox, 24h senkron.
- Tasarım ilkeleri: Foundational Thinking (protokol+privacy önce) → Experience First (ilk dilim SSH) → Boundary Discipline (QR/CLI/RPC/dosya/hook/ağ girişlerinde validate) → Type Discipline (branded ID + sealed state) → Idempotent Ops (installer/pairing/event/approval retry-güvenli) → Exhaust Design Space (relay vs direkt; karar: direkt terminal+gateway, backend'de kısa özet) → Verifiable Units (P00–P18 bağımlı dilimler) → Prove It Works (gerçek cihaz + pcap kanıtı).

**Açık riskler ve sahipleri:**

- Native derleme karmaşası (Mosh/ET/whisper) → sahip: platform; erken P07 spike.
- OEM Keystore farkları → P05'te 3 cihaz matrisi.
- Tek-replica HA yok → P18'de "scale-out yok" belgesi + backup SLO.
- FCM gecikmesi → P13'te fallback polling + ölçü.

---
