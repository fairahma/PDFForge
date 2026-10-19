plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.pdfforge.app"
    compileSdk = 35
    defaultConfig { applicationId = "com.pdfforge.app"; minSdk = 29; targetSdk = 35; versionCode = 8; versionName = "0.8.0" }
    signingConfigs {
        create("stableRelease") {
            storeFile = file(System.getenv("PDF_FORGE_KEYSTORE_PATH") ?: "signing-placeholder.jks")
            storePassword = System.getenv("PDF_FORGE_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("PDF_FORGE_KEY_ALIAS") ?: "pdfforge"
            keyPassword = System.getenv("PDF_FORGE_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stableRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
