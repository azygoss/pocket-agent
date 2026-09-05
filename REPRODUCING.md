# REPRODUCING.md — Pocket Agent build yeniden üretimi

Bu belge, release artefaktlarının (APK/AAB/CLI tarball) sıfırdan ve doğrulanabilir
şekilde üretilmesini anlatır. Hedef: aynı commit + aynı toolchain → aynı davranış
(native bileşenlerde aynı SHA256; Java/Kotlin tarafında deterministic olmayan
imza/zaman damgası dışında eşdeğer çıktı).

## 1. Sabit toolchain

| Araç | Sürüm | Kaynak |
|---|---|---|
| JDK | 17 (Temurin) | apt `openjdk-17-jdk` |
| Gradle | 8.7 (wrapper sabitli) | `apps/android/gradle/wrapper/gradle-wrapper.properties` |
| Android SDK | platform 34, build-tools 34.0.0 | sdkmanager |
| NDK | 29.0.14206865 | sdkmanager `ndk;29.0.14206865` |
| CMake | 3.22.1 | sdkmanager |
| Go | 1.26.x | go.dev |
| Buf | 1.72 | buf.build |

Kurulum: `sudo ./scripts/setup-dev-environment.sh` (idempotent).

## 2. Kaynak

```bash
git clone https://github.com/azygoss/pocket-agent.git
cd pocket-agent && git checkout <release-commit-sha>
```

## 3. Native bileşen (mosh-client)

```bash
./scripts/build-mosh.sh        # 3 ABI: arm64-v8a, armeabi-v7a, x86_64
sha256sum -c native/mosh/SHA256SUMS
```

Upstream: `connectbot/mosh4android` @ `2de58be` (android dalı) — resmi NDK
derleme betiği, patch yok. Hashler `native/mosh/SHA256SUMS` içinde sabitli;
uyuşmazlık = build zinciri sapması (toolchain sürümünü kontrol edin).

## 4. Android

```bash
cd apps/android
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:lintRelease :app:testReleaseUnitTest
./gradlew :app:assembleRelease :app:bundleRelease
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

İmzalama yalnızca env üzerinden (repo'da keystore yok):
`POCKET_AGENT_UPLOAD_KEYSTORE`, `_ALIAS`, `_STORE_PASSWORD`, `_KEY_PASSWORD`.
Env yoksa release, debug anahtarıyla imzalanır — bu çıktı dağıtım için değildir
(uyarı `scripts/build-release.sh` tarafından basılır).

## 5. CLI + backend

```bash
./scripts/package-cli.sh   # dist/: linux/mac amd64+arm64 tarballs + SHA256SUMS
./scripts/sbom.sh          # dist/sbom-go.json (go modülleri) + imaj listesi
```

## 6. CCS (GPL Complete Corresponding Source)

```bash
./scripts/package-ccs.sh   # dist/ccs/: kaynak arşivi + native build bilgisi + font lisansı
```

## 7. Tam kapı dizisi (release gate)

```bash
./scripts/build-release.sh
# = secret-scan + license-check + check-packaging + buf lint + go vet/test
#   + lintRelease + testReleaseUnitTest + assembleRelease + bundleRelease
#   + apksigner verify + dist/release-checksums.txt
```
