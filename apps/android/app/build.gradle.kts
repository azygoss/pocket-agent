import java.io.File
plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.pocketagent.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.pocketagent.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 56
        versionName = "0.35.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    signingConfigs {
        // P18: upload keystore yalnız env'den gelir; repo'ya asla girmez (0600, gitignored).
        // Env yoksa release debug anahtarıyla imzalanır (headless CI/doğrulama için).
        create("upload") {
            val ks = System.getenv("POCKET_AGENT_UPLOAD_KEYSTORE")
            if (ks != null && File(ks).exists()) {
                storeFile = file(ks)
                keyAlias = System.getenv("POCKET_AGENT_UPLOAD_ALIAS")
                storePassword = System.getenv("POCKET_AGENT_UPLOAD_STORE_PASSWORD")
                keyPassword = System.getenv("POCKET_AGENT_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val uploadKs = System.getenv("POCKET_AGENT_UPLOAD_KEYSTORE")
            signingConfig = if (uploadKs != null && File(uploadKs).exists()) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    // Dağıtım APK'sı (GitHub Releases + uygulama içi güncelleme). Debug
    // build debuggable=true olduğundan ART, Compose kütüphanelerinin baseline
    // profillerini yok sayar ve kodu yorumlar → kaydırma/açılış takılır.
    // dist: debuggable=false (profileinstaller profilleri kurar, AOT
    // derlenir) ama R8 kapalı — sshj/BouncyCastle yansıma yolları cihazda
    // doğrulanmadan küçültülmez. Mevcut kurulumlar debug anahtarıyla
    // imzalı olduğu için güncelleme zinciri kopmasın diye aynı anahtar.
    buildTypes {
        create("dist") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric sandbox başına sınıf-yükleyici + font kaynakları birikir;
                // yetersiz heap'te Compose idle bekleyişi GC baskısıyla zaman aşımına düşer.
                it.maxHeapSize = "2g"
                it.forkEvery = 6
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.animation:animation")
    implementation(libs.compose.tooling.preview)
    implementation(libs.androidx.activity.compose)
    // Sideload kurulumda (Play yok) kütüphane baseline profillerini
    // ilk açılıştan önce ART'a kurar.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.biometric)
    implementation(libs.compose.icons)
    implementation(libs.sshj)
    implementation(libs.bcprov)
    implementation(libs.slf4j.nop)
    implementation(libs.zxing.embedded) // P04: QR tarama (Apache-2.0)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Release unit testleri de Robolectric manifest'ine ComponentActivity ister
    // (debugImplementation yalnız debug varyantına girer). Tek fark: manifestte
    // intent-filter'sız ComponentActivity satırı.
    releaseImplementation("androidx.compose.ui:ui-test-manifest")
}
