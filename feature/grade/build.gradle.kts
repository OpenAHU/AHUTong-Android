plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/*
 * 成绩 feature：成绩页的状态、学期选择策略与界面。
 *
 * ViewModel 只依赖接口（BehaviorRecorder / PresetSuggestions / GradeSource /
 * SessionIdentity / ScheduleWeekConfig），界面只依赖 ViewModel 与设计系统。
 */
android {
    namespace = "com.ahu.ahutong.feature.grade"
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
            // ViewModel 每个分支都打日志；这是它们此前没有单测的原因之一（android.util.Log 在单测里会抛）。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:grade"))
    implementation(project(":data:schedule"))
    implementation(project(":data:personalization"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    // 成绩与考试页把 ViewModel 的 LiveData 读成 Compose 状态。
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.kotlinx.coroutines.android)

    // 预设载荷是 JSON。
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

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    // ExamViewModel 暴露 LiveData，测试里需要它在 JVM 上算作主线程。
    testImplementation(libs.androidx.arch.core.testing)
}
