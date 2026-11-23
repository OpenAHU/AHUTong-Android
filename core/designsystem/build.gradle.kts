plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 设计系统模块：主题、通用组件、动效令牌与交互工具。
 *
 * 只依赖 :core:model（主题模型）与 Compose / Miuix / Monet 这类 UI 库；
 * 不依赖任何数据、导航或业务代码——依赖方向由门禁 R11 看守。
 * 这也是 P3 抽出 feature 模块的前提：界面代码不该逼着每个 feature 都依赖 :app。
 */
android {
    namespace = "com.ahu.ahutong.core.designsystem"
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
}

dependencies {
    // 主题模型（AppUiTheme / AppThemeMode / DEFAULT_THEME_COLOR）来自这里。
    api(project(":core:model"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.androidx.core.ktx)
    // AppComponents 的返回手势（BackHandler）来自这里。
    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.android)
    implementation(libs.monet)
    implementation(libs.kyant0.backdrop)
    implementation(libs.kyant0.capsule)

    testImplementation(kotlin("test"))
}
