//// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
    id("io.sentry.android.gradle") version "6.21.0" apply false
}

/*
 * 依赖版本锁定：libs.versions.toml 只钉直接依赖，锁文件把传递依赖的版本也钉住
 * （校验和由 gradle/verification-metadata.xml 单独钉住）。
 *
 * 改依赖后跑一次 ./gradlew --write-locks 重新生成；锁文件与依赖不一致时构建会失败，
 * 这是有意的：版本漂移应当是一次显式提交，而不是悄悄发生。
 */
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
