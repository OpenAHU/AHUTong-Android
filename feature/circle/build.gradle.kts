plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * 校园圈子插件（只读）：接入 zxs-bbs 校园 BBS 的公开只读接口。
 *
 * 契约示范模块：只依赖 plugin-api + designsystem + common + network，
 * 不碰 app 内部、不碰任何会话与凭据。只读流全程零鉴权。
 */
android {
    namespace = "com.ahu.ahutong.feature.circle"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        // CircleApi 用 android.util.LruCache；JVM 单测里按默认值返回即可。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:plugin-api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test-junit"))
    // Android's org.json classes are stubs in local JVM tests.
    testImplementation(libs.org.json)
}
