plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供，这里只引用 id，避免改动共享的版本目录。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/**
 * 领域模型模块：只放"全局共享的数据结构"，不依赖 Android 框架能力，也不依赖任何业务模块。
 * 依赖方向见 AGENTS.md §2（由门禁 R31 看守，core 不得依赖 feature/data/ui）。
 */
android {
    namespace = "com.ahu.ahutong.core.model"
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
    // data/model 中 3 个 Java 文件使用了 @NonNull，仅此一项 Android 依赖。
    implementation(libs.androidx.annotation)
    // 模型用 @SerializedName 描述线上字段名（Gson 反射使用，属于模型契约的一部分）。
    implementation(libs.gson)

    // ElectricityController 的枚举取值是协议契约（电控编号与层级），测试随模型同模块。
    testImplementation(kotlin("test-junit"))
}
