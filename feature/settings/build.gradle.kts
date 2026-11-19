plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 设置 feature：偏好设置页与设置类目入口。
 *
 * ViewModel 只依赖接口（SettingsStore / PersonalizationSettings / CourseReminderControl），
 * 界面只依赖 ViewModel 与设计系统；三者的实现留在 :app，由组合根接线。
 */
android {
    namespace = "com.ahu.ahutong.feature.settings"
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
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:personalization"))
    implementation(project(":data:chaoxing"))
    implementation(project(":data:repository-index"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.extended)
    // 贡献名单用 AsyncImage 显示头像。
    implementation(libs.coil.compose)
    // 设计系统的 SettingsBackdropContainer 在签名里用到 Backdrop，调用方必须看得见它。
    implementation(libs.kyant0.backdrop)
    // SmoothRoundedCornerShape 与设计系统的部分组件把 miuix 的形状类型写在签名里。
    implementation(libs.miuix.android)
    // SmoothRoundedCornerShape 的返回类型来自 capsule，属于调用方可见的签名。
    implementation(libs.kyant0.capsule)
    implementation(libs.monet)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
