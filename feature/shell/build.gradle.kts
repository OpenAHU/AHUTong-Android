plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.ahu.ahutong.feature.shell"
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
        buildConfig = false
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    api(project(":core:designsystem"))
    api(project(":core:datastore"))
    api(project(":core:network"))
    api(project(":core:sdk-api"))
    api(project(":core:sdk"))
    api(project(":data:schedule"))
    api(project(":data:auth"))
    api(project(":data:grade"))
    api(project(":data:exam"))
    api(project(":data:campuscard"))
    api(project(":data:portal"))
    api(project(":data:payment"))
    api(project(":data:calendar"))
    api(project(":data:crawler"))
    api(project(":feature:login"))
    api(project(":feature:schedule"))
    api(project(":feature:home"))
    api(project(":feature:grade"))
    api(project(":feature:exam"))
    api(project(":feature:payment"))
    api(project(":feature:portal"))
    api(project(":feature:calendar"))
    api(project(":feature:tools"))
    api(project(":feature:settings"))
    api(project(":feature:weather"))
    api(project(":feature:classroom"))
    api(project(":feature:repository"))

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.mmkv.static)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.jsoup)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.monet)
    implementation(libs.kyant0.backdrop)
    implementation(libs.kyant0.capsule)
    implementation(libs.zxing.android.embedded)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
}
