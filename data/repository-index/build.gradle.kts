plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/**
 * 仓库索引与文档下载的对外接口：`RepositoryIndex` 与它交换的数据结构。
 *
 * 实现（RepositoryManager：GitHub/JsDelivr 协议、缓存分层、下载与导出）暂时仍在 :app，
 * P4 再搬进来。先立接口，是因为 feature 模块不能依赖 :app。
 */
android {
    namespace = "com.ahu.ahutong.data.repositoryindex"
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
    // Source health is observed by feature ViewModels through the repository contract.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // 数据结构用 @SerializedName 描述线上字段名（Gson 反射使用，属于契约的一部分）。
    implementation(libs.gson)
}
