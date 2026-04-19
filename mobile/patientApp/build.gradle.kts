plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.medhistry.patient"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medhistry.patient"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))

    // Kotlinx Serialization (needed for JsonElement access in UI)
    implementation(libs.serialization.json)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // Material Symbols (vector icons) — used in bottom nav and elsewhere
    // so we don't have to rely on emoji that render differently per-OEM.
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.tooling)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.viewmodel)

    // QR code generation (patient app)
    implementation(libs.zxing.core)

    // DI
    implementation(libs.koin.android)
}
