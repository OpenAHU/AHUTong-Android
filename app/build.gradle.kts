import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.sentry.jvm.gradle")
}

// DSN 是客户端公开标识，默认值随仓库提供；可用 -Psentry.dsn=... 或环境变量 SENTRY_DSN 覆盖。
val sentryDsn = providers.gradleProperty("sentry.dsn").orNull
    ?: providers.environmentVariable("SENTRY_DSN").orNull
    ?: "https://c42e7dfe9dae88a5c6fbdb60805e9b83@o4512053994848256.ingest.us.sentry.io/4512054683435008"

// Bugly appid 同样是客户端公开标识：默认值随仓库提供，可用 -Pbugly.appId=... 或环境变量覆盖。
// 它从 Java 源码搬到这里，源码里不再出现字面量（密钥扫描因此能盯住"标识不该散落在代码里"这条）。
val buglyAppId = providers.gradleProperty("bugly.appId").orNull
    ?: providers.environmentVariable("BUGLY_APP_ID").orNull
    ?: "2c2ccadcad"

sentry {
    org.set("openahu")
    projectName.set("ahutong-android")
    // 上传混淆映射需要认证令牌，只从环境变量读取，不写入仓库；缺失时插件会跳过上传任务。
    authToken.set(providers.environmentVariable("SENTRY_AUTH_TOKEN").orNull)
    // 关闭构建期遥测，避免构建信息被上报。
    telemetry.set(false)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.ahu.ahutong"
    compileSdk = 36

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests {
            // JVM 单测里 android.jar 的方法默认抛 "not mocked"。数据层与登录流程大量使用
            // android.util.Log，默认行为会让"任何带日志的代码路径"都无法在 JVM 上被覆盖——
            // 契约测试只能绕开它们（这正是登录分类一直没被测试的原因）。
            // 打开后这些调用变成空实现；真正的 Android 依赖（MMKV、Keystore）仍然需要设备。
            isReturnDefaultValues = true
        }
    }
    //关闭PNG合法性检查
    // aaptOptions.useNewCruncher = false
    defaultConfig {
        applicationId = "com.ahu.ahutong"
        minSdk = 26
        targetSdk = 36
        versionCode = 304000
        versionName = "3.4.0"
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        buildConfigField("String", "BUGLY_APP_ID", "\"$buglyAppId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
//            signingConfig = signingConfigs.getByName("my_custom_debug_sign")
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
//            signingConfig = signingConfigs.getByName("my_custom_debug_sign")
        }
    }
//    packagingOptions {
//        resources {
//            excludes += ['META-INF/ASL2.0', 'META-INF/LICENSE', 'META-INF/NOTICE', 'META-INF/MANIFEST.MF']
//        }
//    }
    // 插件 ServiceLoader 索引：多插件各带一份 META-INF/services，内容合并而非冲突报错
    packaging {
        resources.merges += "META-INF/services/**"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xlambdas=class",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val generatedThirdPartyAssets = layout.buildDirectory.dir("generated/thirdPartyAssets")
val generateThirdPartyAssets by tasks.registering(Sync::class) {
    from(rootProject.file("GuiXu-Rust/LICENSE")) {
        into("licenses/guixu")
    }
    from(rootProject.file("GuiXu-Rust/NOTICE")) {
        into("licenses/guixu")
    }
    into(generatedThirdPartyAssets)
}

android.sourceSets.getByName("main").assets.srcDir(generatedThirdPartyAssets)
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn(generateThirdPartyAssets)
    }

tasks.matching { it.name.contains("Lint", ignoreCase = true) }
    .configureEach {
        dependsOn(generateThirdPartyAssets)
    }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:storage"))
    implementation(project(":core:auth"))
    implementation(project(":data:personalization"))
    implementation(project(":data:schedule"))
    implementation(project(":data:grade"))
    implementation(project(":data:chaoxing"))
    implementation(project(":data:recharge"))
    implementation(project(":data:update"))
    implementation(project(":background"))
    implementation(project(":data:repository-index"))
    implementation(project(":feature:repository-index"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:grade"))
    implementation(project(":feature:xuexiaotong"))
    implementation(project(":feature:recharge"))
    implementation(project(":core:plugin-api"))
    implementation(libs.sentry.android)
    implementation(libs.crashreport)
    implementation(libs.ads.mobile.sdk)

    implementation(libs.persistentcookiejar)
    implementation(libs.mmkv.static)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.jsoup)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.miuix.android)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.monet)
    implementation(libs.kyant0.backdrop)
    implementation(libs.kyant0.capsule)
    implementation(libs.markwon.core)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.core.ktx)
    // 插件相机能力：CameraX 只进宿主（插件经父 ClassLoader 共享），OpenCV 留在插件包
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(kotlin("test-junit"))

    implementation(libs.zxing.android.embedded)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.conscrypt)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    // LiveData 的 setValue 要求"主线程"；InstantTaskExecutorRule 让它在 JVM 单测里成立。
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
