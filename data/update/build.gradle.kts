plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 更新的数据侧（计划 §3.2 的 :data:update）：版本检查、下载、校验与安装意图。
 *
 * 这一刀先搬"谁都不能绕过"的两块：更新元数据的策略（校验 URL、sha256、版本单调性）与安装前校验
 * （体积、路径可信、包名、版本、签名指纹）。校验住在模块里，任何安装路径都得先过它——
 * 以前它在 Activity 里，新调用点可以绕开。
 *
 * 元数据与策略按原包名搬来（data.server / data.server.model），调用点零改动；
 * 下载器与安装编排随后再进来（见计划 P4）。
 */
android {
    namespace = "com.ahu.ahutong.data.update"
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

    testOptions {
        unitTests {
            // 校验要打日志（Android framework 调用在单测里默认抛异常）。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // 校验要读应用自己的包信息与文件目录，因此认识 AppEnvironmentHolder。
    implementation(project(":core:common"))
    // 下载走 :core:network 的工厂（专属 dispatcher + 不跟随重定向，见 ApkDownloadApi）。
    implementation(project(":core:network"))
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    // 下载器自己带作用域与事件流。
    implementation(libs.kotlinx.coroutines.android)
    // 下载器是"实现也在模块里"的那种端口：组合根靠 @Inject 构造它。
    implementation(libs.javax.inject)
    // 元数据模型与策略不依赖 Android 之外的任何东西。
    implementation(libs.androidx.annotation)

    testImplementation(kotlin("test-junit"))
}
