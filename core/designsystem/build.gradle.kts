plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ahu.ahutong.core.designsystem"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)

    // ── UI stack re-export (api) ────────────────────────────────────────────
    // Feature / app modules should depend on :core:designsystem only for UI.
    // Do NOT re-declare Compose / Material3 / Monet / Capsule / Navigation here
    // in feature modules unless you need a special optional library.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.foundation)
    api(libs.androidx.material.icons.extended)
    api(libs.material3)
    api(libs.androidx.runtime.livedata)
    api(libs.androidx.activity.compose)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.hilt.navigation.compose)
    api(libs.coil.compose)
    api(libs.monet)
    api(libs.kyant0.backdrop)
    api(libs.kyant0.capsule)
}
