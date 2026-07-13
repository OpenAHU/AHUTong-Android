plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.ahu.ahutong"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    defaultConfig {
        applicationId = "com.ahu.ahutong"
        minSdk = 26
        targetSdk = 36
        versionCode = 319
        versionName = "3.1.9"
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
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

val rustSdkCargoToml = rootProject.file("sdk/Cargo.toml")
val guiXuCargoToml = rootProject.file("GuiXu-Rust/Cargo.toml")
val rustSdkSourcesAvailable = rustSdkCargoToml.exists() && guiXuCargoToml.exists()
val jniOutputDir = project.layout.projectDirectory.dir("src/main/jniLibs")
val jniOutputSo = project.layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libahutong_rs.so")

if (rustSdkSourcesAvailable) {
    val buildRustSdkArm64 by tasks.registering(Exec::class) {
        group = "build"
        description = "Build the Rust SDK arm64-v8a shared library into app jniLibs."

        workingDir = rootProject.file("sdk")
        inputs.dir(rootProject.file("sdk/src"))
        inputs.dir(rootProject.file("GuiXu-Rust/src"))
        inputs.file(guiXuCargoToml)
        inputs.file(rootProject.file("GuiXu-Rust/Cargo.lock"))
        inputs.file(rustSdkCargoToml)
        outputs.file(jniOutputSo)

        commandLine(
            "cargo",
            "ndk",
            "-t",
            "arm64-v8a",
            "-o",
            jniOutputDir.asFile.absolutePath,
            "build",
            "--release",
            "--features",
            "server"
        )
    }

    tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders" }
        .configureEach {
            dependsOn(buildRustSdkArm64)
        }
} else {
    logger.lifecycle(
        "Rust SDK submodule sources missing; using prebuilt jniLibs if present."
    )
}

dependencies {
    // Host app: Application + MainActivity + navigation/theme composition root
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:sdk-api"))
    implementation(project(":core:sdk"))

    implementation(project(":data:schedule"))
    implementation(project(":data:auth"))
    implementation(project(":data:grade"))
    implementation(project(":data:exam"))
    implementation(project(":data:campuscard"))
    implementation(project(":data:portal"))
    implementation(project(":data:payment"))
    implementation(project(":data:calendar"))
    implementation(project(":data:crawler"))

    implementation(project(":feature:login"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:home"))
    implementation(project(":feature:grade"))
    implementation(project(":feature:exam"))
    implementation(project(":feature:payment"))
    implementation(project(":feature:portal"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:classroom"))
    implementation(project(":feature:repository"))
    implementation(project(":feature:widget"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:update"))
    implementation(project(":feature:debug"))

    implementation(libs.crashreport)
    implementation(libs.ads.mobile.sdk)

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.mmkv.static)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.jsoup)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.zxing.android.embedded)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
