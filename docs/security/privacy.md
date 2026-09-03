# Privacy — veri sınırı (P00 iskeleti, P01'de şemayla zorlanır)

Backend **tutabilir:** kısa event özeti (≤256ch message), usage snapshot, onay yönlendirme kaydı.
Backend **asla almaz/loglamaz:** scrollback, tam transcript, kaynak kodu, dosya yolu,
diff içeriği, preview body, SSH parolası, özel anahtar, ses kaydı, Mosh anahtarı.

TTL: event 24h, onay sonuç/timeout'ta erken silinir, upload URL 24h.
FCM yükü yalnız eventId + başlık; detay TLS-pull.

Zorlama katmanları (defense in depth):
1. `protocol/` JSON Schema whitelist (P01 privacy-schema testi).
2. Backend validator: bilinmeyen/yasak alan → 400, loglamadan drop (P02).
3. Daemon flusher: yasak alanlı event'i kuyruktan düşür + sayaç (P03/P12).
4. CI: `secret-scan.sh` + pcap canary testi (P17).

Canary tokenlar: `POCKET_CANARY_TERMINAL_*`, `POCKET_CANARY_KEY_*` — log/pcap'te görülürse test kırmızı.
