# AGENTS — Pocket Agent

Agent/agentik oturumlar için proje gerçekleri. Public doğruluk kaynağı:
`README.md` + `docs/`. `HANDOFF.md` / `plan.md` / `decisions.tsv` gibi iç
geliştirme kayıtları `.gitignore`'dür — cihazda bulunabilir ama asla
commit edilmez/push edilmez.

## Derleme / test

```bash
make gates        # secret-scan + license + packaging + buf lint + privacy-schema + canary
make go           # go test ./... && go vet ./... && go build ./...
make android      # lintDebug + testDebugUnitTest + assembleDebug
make live-up      # env-gated canlı testler için backend/gateway/preview
make live-test    # canlı süitler (SSH/backend/SFTP/gateway/mosh/pairing)
```

- Android SDK: `/opt/android-sdk` (ANDROID_HOME), Gradle wrapper `apps/android/gradlew`.
- `connectedDebugAndroidTest` emülatör ister — bu makinede YOK, atlanır.
- Canlı testler env-gated: `PA_LIVE_SSH`, `PA_LIVE_BACKEND`, `PA_LIVE_GW` —
  `eval $(./scripts/live-env.sh env)` ile ayarlanır.
- Emülatör olmadan UI doğrulaması: Robolectric süitleri (`*UiTest`).

## Kurallar

- Conventional commits: `feat(Pxx): …` / `fix(Pxx): …`.
- Davranış/mimari karar commit mesajı + PR açıklamasında gerekçeyle belgelenir.
- Clean-room: referans projenin marka/kod/asset'ini kopyalamak yasak; `scripts/secret-scan.sh` kapısı (ayrıntı README).
- `docs/reference/**` yayın paketine girmez (`check-packaging.sh`).
- Secret'lar asla loglanmaz/diskte plaintext durmaz; tokenlar hash-only.
- Her P adımı için test + kapı yeşili zorunlu.

## Yapı

- `apps/android/app/src/main/java/dev/pocketagent/` — ui/ transport/ data/ net/ service/ security/
- `cmd/pocket-agent-hook/` — CLI giriş noktası (main.go, pair_cmd.go, gateway_cmd.go, help_cmd.go)
- `host/` — daemon, doctor, gateway, hooks, journal, pairing, service, session, ssh, tmux, update
- `backend/` — cmd/server + internal/{api,approvals,auth,inbox,store}
- `protocol/` — Buf v2 (host.proto + control.proto v1 donduruldu)
- `tests/` — e2e, fuzz, perf, protocol, security
