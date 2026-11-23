plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 分级存储的第一档：设置（不能丢，丢了回默认值）。
 *
 * 这里放接口与它的数据形状，实现留在 :app——PreferencesManager 用 DataStore 落盘、
 * 用 SharedPreferences 维持启动镜像。凭据与按用户缓存各有自己的档位，见 ADR 0003；
 * 依赖方向由门禁 R16 看守。
 */
android {
    namespace = "com.ahu.ahutong.core.storage"
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
    // 接口里出现 Flow，因此协程是 API 依赖，而不是实现细节。
    api(libs.kotlinx.coroutines.core)
    api(project(":core:common"))
    // AppUiTheme / AppThemeMode 是设置的值类型。
    api(project(":core:model"))

    // ADR 0003 的读写失败策略在这里落地，因此它有自己的契约测试。
    testImplementation(kotlin("test-junit"))
}
