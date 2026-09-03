# Threat model (iskelet — P00, STRIDE)

Kapsam: Android app, Go CLI/daemon, backend, FCM, host gateway.
Güven: cihaz Keystore/TEE, host UID izolasyonu, backend TLS, Caddy.

| Alan | Spoofing | Tampering | Repudiation | Info disclosure | DoS | Elevation |
|---|---|---|---|---|---|---|
| SSH/auth | sahte host key → TOFU pin + hard-stop (P04/P06) | auth downgrade → fallback yasak (P08) | — | parola/key loga düşmez (canary) | throttle | — |
| Pairing QR | replay → tek-kullanım+TTL (P04) | claim race → CAS 200/409 | audit log | secret loglanmaz | rate-limit | — |
| Approval | forged imza → cihaz-key verify (P13) | replay/expired → nonce+revision CAS | receipt zorunlu | tam metin backend'e gitmez | timeout → EXPIRED | tenant guard |
| Gateway | SSRF → loopback-only (P11) | traversal/symlink → root-jail | audit | diff/dosya backend'e gitmez (pcap) | chunk cap 1MB | UID jail |
| Backend | tenant-escape → tenant_id guard (P02) | — | audit append-only | whitelist şema (P01) | rate-limit | RLS |
| Supply chain | imzasız update → cosign (P03/P18) | — | provenance | — | rollback N-1 | — |

P17'de her satır otomatik teste bağlanır. Detay: `docs/security/privacy.md`.
