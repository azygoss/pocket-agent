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
        versionCode = 37
        versionName = "0.28.1"
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
