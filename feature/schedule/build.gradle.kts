plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 课表 feature：课表页的状态（ViewModel）与它的界面构件。
 *
 * ViewModel 只依赖接口（ScheduleSource / ScheduleWeekConfig / SessionIdentity /
 * CourseReminderControl）；界面构件只依赖设计系统与 :core:model。课表页主体（1164 行）
 * 还留在 :app，它需要行为上报的 Compose 接缝与几个资源，属于下一刀。
 */
android {
    namespace = "com.ahu.ahutong.feature.schedule"
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
    implementation(project(":core:auth"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:schedule"))
    implementation(project(":data:personalization"))

    // ViewModel 与它的 LiveData（课表页主体仍在 :app，看的是同一份状态）。
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    // 课表页把 ViewModel 的 LiveData 读成 Compose 状态。
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.extended)

    // 设计系统的组件把这几家的类型写在签名里，调用方必须看得见。
    implementation(libs.kyant0.backdrop)
    implementation(libs.miuix.android)
    implementation(libs.kyant0.capsule)
    implementation(libs.monet)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    // ScheduleViewModel 暴露 LiveData，测试里需要它在 JVM 上算作主线程。
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}
