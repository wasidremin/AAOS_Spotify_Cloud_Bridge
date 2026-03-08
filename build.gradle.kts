// Root build file for AAOS Spotify Cloud-Bridge
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.agp)
        classpath(libs.kotlin.gradle)
        classpath(libs.kotlin.compose.gradle)
        classpath(libs.ksp.gradle)
        // Compose compiler plugin is built into Kotlin 2.0+ — applied via
        // id("org.jetbrains.kotlin.plugin.compose") in the app module.
    }
}
