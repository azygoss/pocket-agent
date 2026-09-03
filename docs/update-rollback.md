# Update / Rollback (P18)
- `update` imzasız/checksumsuz uygulanmaz (CLI exit 3).
- Son 3 sürüm tutulur; rollback N→N-1 veri kaybısız (journal-first).
- Android: `bundleRelease` + `apksigner verify` + sha256 raporu.
