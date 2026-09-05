import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.ahu.ahutong"
    compileSdk = 37

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
    //关闭PNG合法性检查
    // aaptOptions.useNewCruncher = false
    defaultConfig {
        applicationId = "com.ahu.ahutong"
        minSdk = 26
        targetSdk = 36
        versionCode = 330
        versionName = "3.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
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


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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

android.sourceSets.getByName("main").assets.srcDir(generatedThirdPartyAssets.get().asFile)
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

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.core.ktx)
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
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
