# P18: release minification kuralları.
# SSHJ'nin EdDSA sağlayıcısı JDK-internal sınıfa referans verir; Android
# runtime'da bu yol kullanılmaz (BC sağlayıcı devrede) — yalnız uyarıyı sustur.
-dontwarn sun.security.x509.X509Key
# BouncyCastle içsel JDK referansları (Android'de mevcut olmayan yollar)
-dontwarn org.bouncycastle.jce.provider.**
