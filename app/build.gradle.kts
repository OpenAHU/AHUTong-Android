plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    // Thin application shell: all product UI/data wiring lives in :feature:shell
    implementation(project(":feature:shell"))
    implementation(project(":core:common"))
    implementation(project(":core:sdk"))
    implementation(project(":core:datastore"))
    implementation(project(":data:crawler"))

    implementation(libs.crashreport)
    implementation(libs.ads.mobile.sdk)

    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
