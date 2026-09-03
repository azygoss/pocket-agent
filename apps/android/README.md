# Android app (P05+)
Build: `cd apps/android && ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`
Requires: JDK17, SDK, NDK (see scripts/setup-dev-environment.sh). Gradle heap 3g (11GiB box).
applicationId: dev.pocketagent.android, minSdk 29, ABIs arm64-v8a/armeabi-v7a/x86_64.
Screens: Home, Connections, Active Sessions, Agents, Files, Settings.
Security: Keystore-backed keys, biometric CryptoObject gate, FLAG_SECURE, clipboard 60s clear.
