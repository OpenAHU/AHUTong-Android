plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 学习通 feature：作业日历、课程进度、自定义日程与登录。
 *
 * ViewModel 只依赖接口（ChaoxingSession / ChaoxingStore / ChaoxingReminders），
 * 界面只依赖 ViewModel 与设计系统；协议与提醒实现在 :data:chaoxing 与 :app。
 */
android {
    namespace = "com.ahu.ahutong.feature.xuexiaotong"
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
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:chaoxing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

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

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
