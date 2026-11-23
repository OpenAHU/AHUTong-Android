plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * 学习通的协议、凭据与本地存储（计划 §3.2 的 :data:chaoxing）。
 *
 * 这一刀是纯搬运：文件来自 :app 的 data/xuexiaotong，包名不变，因此调用方——界面、提醒调度、
 * 测试——一处 import 都不用改。协议层与界面之间的窄接口（计划里的 ChaoxingGateway）留到
 * feature 抽取时再立，届时有真实的使用者来定义它。
 * 依赖方向由门禁 R23 看守。
 */
android {
    namespace = "com.ahu.ahutong.data.chaoxing"
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
    // 传输层只经 :core:network 的工厂构造客户端。
    implementation(project(":core:network"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test-junit"))
}

