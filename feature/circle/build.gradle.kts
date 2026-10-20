plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * 校园圈子插件（只读）：接入 zxs-bbs 校园 BBS 的公开只读接口。
 *
 * 契约示范模块：只依赖 plugin-api + designsystem + common + network，
 * 不碰 app 内部、不碰任何会话与凭据。只读流全程零鉴权。
 */
android {
    namespace = "com.ahu.ahutong.feature.circle"
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
        // CircleApi 用 android.util.LruCache；JVM 单测里按默认值返回即可。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:plugin-api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation(kotlin("test-junit"))
    // Android's org.json classes are stubs in local JVM tests.
    testImplementation("org.json:json:20240303")
}


/*
 * .ahup 打包：assembleRelease → AAR classes.jar → D8 → 签名 → zip。
 * 运行：gradlew :feature:circle:packageAhup
 * 配置 AHUP_KEYSTORE 时要求完整签名环境；未配置时产未签名包。
 */
tasks.register("packageAhup", Exec::class) {
    group = "ahup"
    description = "打包运行期插件（.ahup）"
    dependsOn("assembleRelease")
    val keystore = providers.environmentVariable("AHUP_KEYSTORE").orNull?.let(::File)
    val signing = if (keystore == null) emptyList() else {
        require(keystore.isFile) { "AHUP_KEYSTORE must point to a signing file" }
        fun credential(name: String) = providers.environmentVariable(name).orNull
            ?.takeIf { it.isNotEmpty() } ?: error("Missing signing environment: $name")
        listOf(
            "--keystore", keystore.absolutePath,
            "--storepass", credential("AHUP_STORE_PASSWORD"),
            "--alias", credential("AHUP_KEY_ALIAS"),
            "--keypass", credential("AHUP_KEY_PASSWORD")
        )
    }
    commandLine(listOf(
        providers.environmentVariable("AHUP_PYTHON").getOrElse("python3"),
        rootProject.file("scripts/package_ahup.py").absolutePath,
        projectDir.absolutePath,
        layout.buildDirectory.file("outputs/aar/circle-release.aar").get().asFile.absolutePath,
        rootProject.file("dist/ahup/campus_circle.ahup").absolutePath,
        "--id", "campus_circle",
        "--title", "校园圈子",
        "--summary", "安大 BBS 只读浏览",
        "--tint", "0xFF5C6BC0",
        "--version", "0.1.0",
        "--author", "AHUTong",
        "--entry", "com.ahu.ahutong.feature.circle.CirclePlugin",
        "--capabilities", "NETWORK,PLUGIN_STORAGE"
    ) + signing)
}
