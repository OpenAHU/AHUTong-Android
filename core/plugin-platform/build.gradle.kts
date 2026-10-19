import org.gradle.api.publish.maven.MavenPublication

/*
 * Plugin platform (BOM for plugin developers).
 *
 * Plugins are compiled with compileOnly against the HOST's libraries, so they must be built
 * against exactly the versions the host ships. This platform pins those versions, which means
 * a plugin project never needs to look at the host repository.
 *
 * Bump this together with the host's Compose/Kotlin/Coil bumps.
 */
plugins {
    `java-platform`
    `maven-publish`
}

group = "com.ahu.ahutong"
version = "1.0.0"

javaPlatform {
    allowDependencies()
}

dependencies {
    // Compose versions come from the same BOM the host uses.
    api(platform("androidx.compose:compose-bom:2025.10.01"))

    constraints {
        api("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        api("io.coil-kt:coil-compose:2.7.0")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "plugin-platform"
            from(components["javaPlatform"])
        }
    }
}
