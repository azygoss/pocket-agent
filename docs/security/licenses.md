# Lisans envanteri (P00 iskeleti — P05/P07'de pinlenir)

Ayrıntılı tarama P18'de (SBOM + REPRODUCING.md). Bu dosya erken uyarı içindir.

| Bileşen | Beklenen lisans | Pin durumu |
|---|---|---|
| Mosh upstream | GPL-3.0-only | P07'de commit pinlenecek |
| mosh4android tabanı | GPL-3.0-or-later (doğrulanacak) | P07 |
| ConnectBot cbssh | Apache-2.0/GPL karışık (doğrulanacak) | P06 |
| ConnectBot termlib | Apache-2.0 (doğrulanacak) | P06 |
| EternalTerminal | GPL-3.0-or-later (doğrulanacak) | P08 |
| whisper.cpp | MIT (doğrulanacak) | P16 |
| Go modülleri | BSD/MIT/Apache | P02'de `go-licenses` raporu |
| Gradle/Kotlin libs | Apache-2.0 çoğunluk | P05'te rapor |

Kural: `scripts/license-check.sh` yeşil olmadan merge yok.
