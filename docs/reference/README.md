# docs/reference — davranışsal referans kanıtı (YAYIN DIŞI)

Bu klasör **yalnızca** herkese açık davranışsal bilgiyi kaynak linkiyle belgeler.
Amaç: kapsam matrisini denetlenebilir kılmak, clean-room sınırını korumak.

İzinli içerik:
- URL + erişim tarihi + kısa davranış notu (örn. "SSH/Mosh/ET desteği listeleniyor").
- Kendi cümlelerimizle yazılmış özellik matrisi (`feature-matrix.md`).

Yasak içerik:
- Moshi APK, decompile çıktısı, trafik dump'ı, ekran görüntüsü kopyası, metin/logo/varlık kopyası.
- `docs/reference/` altındaki hiçbir dosya release APK/AAB/CLI arşivi/container imajına **dahil edilemez**.
  Packaging testi bunu doğrular (`scripts/check-packaging.sh`).

Kaynaklar:
- https://play.google.com/store/apps/details?id=app.getmoshi.android
- https://getmoshi.app/docs/introduction
- https://getmoshi.app/docs/hooks
- https://getmoshi.app/privacy
- https://getmoshi.app/terms

Sonraki adım (P00): `feature-matrix.md` iskeletini çıkar — her satır: özellik | kaynak URL | Pocket Agent karşılığı (Pxx).
