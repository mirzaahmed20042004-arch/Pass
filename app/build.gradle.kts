plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nofilter.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nofilter.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("traineddata", "wasm")
    }
}

dependencies {
    // Zero external cloud SDK dependencies — runs 100% with Android platform + Kotlin stdlib + bundled Tesseract WASM
}
