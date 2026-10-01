plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/*
 * 课表的数据与策略（对应计划里 schedule feature 的数据侧）。
 *
 * 两件东西：
 *  - 纯策略：周次计算（TeachingWeekPolicy）与课表快照比较（ScheduleSnapshotComparator），
 *    它们没有 IO，因此能带着自己的测试一起搬过来，在 JVM 上被回归；
 *  - 数据端口：[ScheduleSource]——界面只问「有没有、变没变、什么时候取的」，
 *    协议与缓存分层留在 :app 的实现里。
 * 依赖方向由门禁 R19 看守。
 */
android {
    namespace = "com.ahu.ahutong.data.schedule"
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
    // 端口用 AhuResult 表达失败，返回值是 Course。
    api(project(":core:common"))
    api(project(":core:model"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test-junit"))
}

