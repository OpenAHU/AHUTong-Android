plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 传输层：OkHttp / Retrofit 的唯一构造点、日志脱敏、第一方客户端的共享装配。
 *
 * 它只认识 HTTP，以及"会话失效时应该问会话层"这件事——续期能力经 SessionExpiryHook 注入，
 * 实现留在会话层（data/session），因此本模块不认识任何业务类型。
 * 依赖方向由门禁 R4、R6–R9 与 R15 看守。
 */
android {
    namespace = "com.ahu.ahutong.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        // NetworkLogging 依赖自己的 BuildConfig.DEBUG：release 变体下日志代码路径根本不存在。
        // library 的 DEBUG 跟随宿主的构建类型，因此语义与原 :app 里的用法一致。
        buildConfig = true
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
    // okhttp 原先只经 retrofit 传递而来；显式声明，避免传递依赖变化时静默失去编译期可见性。
    implementation(libs.okhttp)
    implementation(libs.dnsoverhttps)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // SessionRefreshPolicy 是模块内部的策略，测试跟着策略走（:app 看不到 internal）。
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.mockwebserver)
}
