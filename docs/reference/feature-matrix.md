# Özellik matrisi (iskelet — P00)

Her satır kendi cümlemizle yazılır. Moshi metni kopyalanmaz.

| # | Davranış (kendi ifademiz) | Kaynak | Pocket Agent karşılığı |
|---|---|---|---|
| 1 | SSH ile uzak shell'e bağlanma | Play Store açıklaması (URL + tarih eklenecek) | P06 SSH dikey dilim |
| 2 | Ağ değişiminde yaşayan terminal (roaming) | Moshi docs introduction | P07 Mosh, P08 ET fallback |
| 3 | Multiplexer oturumlarını listele/bağlan | Play Store + docs | P09 tmux/Zellij/Herdr |
| 4 | Agent olay akışı + onay | Hook docs | P12 adaptör, P13 inbox/approval |
| 5 | Mesaj görünümlü agent çıktısı | Hook docs | P14 Chat View |
| 6 | Diff/dosya/önizleme | Docs | P11 gateway |
| 7 | Kısa özetlerin 24h saklanması | Privacy + hooks docs | P02 TTL + P13 |
| 8 | Dosya paylaşımı | Docs | P15 uploads |
| 9 | Sesle yazma | Play Store | P16 whisper/BYOK |

TODO(P00): URL'lere erişim tarihi ekle, her satırı plan kimliğiyle (Pxx) eşle.
