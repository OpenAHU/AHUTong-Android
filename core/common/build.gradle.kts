plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/**
 * 共享基础设施模块：错误模型与结果类型（ADR 0001）、应用级环境与应用扩展。
 * 只放"所有模块都会用到、且不依赖任何业务"的东西——依赖方向由门禁 R10 看守，
 * 这里只允许依赖 :core:model 与 Gson、协程这类通用库。
 */
android {
    namespace = "com.ahu.ahutong.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    // ext/ 与 DebugClock 从 :app 搬来之后引入的三项依赖：
    // JSON 扩展用 Gson、launchSafe 用协程、UserExt 的扩展接收者是 :core:model 的领域模型。
    implementation(libs.gson)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api(project(":core:model"))
    testImplementation(kotlin("test-junit"))
}
