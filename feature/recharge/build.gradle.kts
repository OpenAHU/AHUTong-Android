plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 充值/缴费 feature：校园卡充值、电费、浴室、网费。
 *
 * ViewModel 只依赖数据侧的端口（CardRechargeSource 起头，其余三块随后并入），
 * 协议客户端、支付 SDK 与会话存储都在 :app——支付流程因此仍然只经组合根接线。
 * 依赖方向由门禁 R26 看守。
 */
android {
    namespace = "com.ahu.ahutong.feature.recharge"
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

    testOptions {
        unitTests {
            // 与 :app 的 JVM 单测保持一致：缴费流程会经过 LiveData 与 android.util.Log，
            // 默认的 mockable android.jar 会抛 "not mocked"，打开后这些调用是空实现。
            // 这条测试是从 :app 原样搬来的，跑在同一套假设下。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:personalization"))
    implementation(project(":data:recharge"))

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    // CardBalanceDepositViewModel 暴露 LiveData，测试里需要它在 JVM 上算作主线程。
    testImplementation(libs.androidx.arch.core.testing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // 浴室与电费的 ViewModel 仍自己解析协议响应（按协议顺序推进四步流程）。
    // 解析搬进 :data:recharge 的适配器是后续的一刀，届时这个依赖可以去掉。
    implementation(libs.gson)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.extended)

    // 设计系统的组件把它们写在签名里。
    implementation(libs.kyant0.backdrop)
    implementation(libs.miuix.android)
    implementation(libs.kyant0.capsule)
    implementation(libs.monet)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
