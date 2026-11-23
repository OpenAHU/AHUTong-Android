plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 会话与凭据的接口（计划 §3.2 的 :core:auth）。
 *
 * 凭据保险箱的读写原语（AES-GCM + Android Keystore 的 SecureStorage）已经在本模块里；
 * 仍留在 :app 的是设备侧装配：SecureBoxStore 的迁移链要经原生服务的 KV 与旧 MMKV 明文库，
 * 会话实现要用仓库登录。这一步的顺序正是 P2 收尾时记下的「先立端口，再搬实现」。
 * 依赖方向由门禁 R18 看守。
 */
android {
    namespace = "com.ahu.ahutong.core.auth"
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
    // AhuResult / AhuError 是会话结果的类型；LoginOutcome 与 User 是它的词汇。
    api(project(":core:common"))
    api(project(":core:model"))
    // 接口里有 StateFlow；这是 API 面上的类型，不能只做实现依赖。
    api(libs.kotlinx.coroutines.core)
}
