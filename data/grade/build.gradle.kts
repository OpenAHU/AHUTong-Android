plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 成绩的数据与端口（对应计划里 grade feature 的数据侧）。
 *
 * 只放端口：界面问「有没有缓存的成绩、绩点排名是多少、这学期有哪些档案」，
 * 协议（教务成绩页解析）与缓存分层留在 :app 的实现里。学年与学期那部分不在这里——
 * 它已经由 :data:schedule 的 ScheduleWeekConfig 表达过，不重复造。
 * 依赖方向由门禁 R21 看守。
 */
android {
    namespace = "com.ahu.ahutong.data.grade"
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
    // 端口用 AhuResult 表达失败，返回值是成绩与档案。
    api(project(":core:common"))
    api(project(":core:model"))
    // 端口里有 Flow（mock 刷新计数）。
    api(libs.kotlinx.coroutines.core)
}
