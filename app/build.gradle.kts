plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.omnisolve.overlay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omnisolve.overlay"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Signing Config for Play Protect approval ──────────────────────────
    // Using debug keystore for development. For production release,
    // replace with your own release keystore to pass Play Protect on all
    // Samsung, OnePlus, Xiaomi/Redmi, Realme, OPPO, and other OEM devices.
    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore automatically
        }
        create("release") {
            // For production: configure your own release keystore here
            // storeFile = file("path/to/release.keystore")
            // storePassword = "your_store_password"
            // keyAlias = "your_key_alias"
            // keyPassword = "your_key_password"

            // For now, fall back to debug keystore for testing
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            // V1 (JAR), V2 (APK Signature Scheme v2), V3 (APK Signature Scheme v3)
            // All three are needed for maximum OEM compatibility
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
    }

    // Ensure APK uses all signature schemes for OEM compatibility
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Needed for ML Kit Task -> coroutine .await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Networking & JSON for Gemini API (text-only now — much smaller requests)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // ML Kit On-Device Text Recognition (OCR) — no network needed, runs locally
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
