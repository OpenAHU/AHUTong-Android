import org.gradle.api.publish.maven.MavenPublication

plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

/*
 * 插件契约（plugin-api）：插件与宿主之间的唯一词汇表。
 *
 * 方案 A（插件静态自带全部依赖）要求本模块**零第三方依赖**：
 * 契约签名只允许 JDK / Android framework / 契约自身类型。
 * 一旦这里 `api(...)` 了 okhttp / compose，插件的 POM 就会把它们带进来，
 * 而插件自带的那一份与宿主那一份是不同的 Class —— 运行时必然炸。
 */
android {
    namespace = "com.ahu.ahutong.core.plugin"
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

    publishing {
        singleVariant("release")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.ahu.ahutong"
                artifactId = "plugin-api"
                // 与 PLUGIN_API_LEVEL 同步维护：minor 对应 apiLevel
                version = "1.0.0"
                from(components["release"])
            }
        }
        // 仓库由发布方注入（自建静态 Maven / GitHub Packages），密钥一律不入库
    }
}
