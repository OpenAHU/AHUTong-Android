plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Glance 的 provideContent 是一个 Compose 组合，需要编译器插件。
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 后台组件（计划 §3.2 的 :background）：小组件、通知与提醒的渲染与调度。
 *
 * 纪律：只依赖**只读**的东西（缓存快照、设置、提醒计划），不触发登录、不发起网络写请求。
 * 冷启动被系统拉起时（进程已死）它必须只靠本地缓存把内容画出来——这条是本阶段的验收标准。
 *
 * 依赖方向由门禁 R28 看守。
 */
android {
    namespace = "com.ahu.ahutong.background"
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
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    // 提醒开关读的是设置（:core:storage 的 SettingsStore）。
    implementation(project(":core:storage"))
    implementation(project(":data:schedule"))
    implementation(project(":data:chaoxing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    // 小组件用 Monet 调色板取色（与 :app 里的用法一致）。
    implementation(libs.monet)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(kotlin("test-junit"))
}
