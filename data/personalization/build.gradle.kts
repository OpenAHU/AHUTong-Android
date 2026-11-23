plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/**
 * 个性化层的对外接口与共享词汇：`BehaviorRecorder`（行为上报）、动作 id 与语义桶枚举。
 *
 * 端侧训练与推理的**实现**暂时仍在 :app，P4 再搬进来。先立接口是因为 feature 模块需要它：
 * 界面不该认识端侧模型，只该知道自己上报了什么（计划 §3.2 的 :data:personalization 对外收窄）。
 */
android {
    namespace = "com.ahu.ahutong.data.personalization"
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
    // 接口暴露 StateFlow，语义词汇里的 SemanticEventRecorder 带 @Inject：两者都在 API 面上。
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
