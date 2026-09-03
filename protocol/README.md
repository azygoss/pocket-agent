# protocol/ — P01 sözleşmeleri

Buf root: repo kökündeki `buf.yaml` (`modules: protocol`).

- `protocol/pocketagent/host/v1/host.proto` — Android ↔ host (capabilities, negotiation, ResourceChunk).
- `protocol/pocketagent/control/v1/control.proto` — daemon ↔ backend ↔ Android (AgentEventSummary whitelist).
- `protocol/fixtures/golden-v1.json` — Kotlin ve Go'nun bayt-bayt aynı yorumlaması gereken golden.

Kurallar:
- `major` uyuşmazsa structured kapalı, SSH açık. `minor` farkı warning.
- Unknown capability/field yok sayılır (test: golden `unknown_capability`).
- `ResourceChunk.data` ≤1MiB; `message` ≤256ch.
- Yasak alanlar şemada ifade edilemez → `tests/protocol/privacy-schema.sh`.

Komutlar:
```bash
buf lint
buf build
./tests/protocol/privacy-schema.sh
```
