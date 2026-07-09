plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cloudbridge.spotify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cloudbridge.spotify"
        minSdk = 30
        targetSdk = 35
        versionCode = 20
        versionName = "2.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        /**
         * Single local signing identity for debug + release so `adb install -r`
         * never hits INSTALL_FAILED_UPDATE_INCOMPATIBLE and never needs an
         * uninstall (which would wipe OAuth tokens / profiles).
         *
         * Preference order:
         * 1. repo `release.keystore` (if present)
         * 2. `signing/cloudbridge-local.keystore` (generated once on this machine)
         * 3. SDK default `~/.android/debug.keystore` (stable Android Debug identity)
         */
        create("local") {
            val releaseKey = rootProject.file("release.keystore")
            val projectLocalKey = rootProject.file("signing/cloudbridge-local.keystore")
            val sdkDebugKey = file(
                "${System.getProperty("user.home")}/.android/debug.keystore"
            )
            when {
                releaseKey.isFile -> {
                    storeFile = releaseKey
                    storePassword = "password123"
                    keyAlias = "release"
                    keyPassword = "password123"
                }
                projectLocalKey.isFile -> {
                    storeFile = projectLocalKey
                    storePassword = "cloudbridge"
                    keyAlias = "cloudbridge"
                    keyPassword = "cloudbridge"
                }
                sdkDebugKey.isFile -> {
                    storeFile = sdkDebugKey
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
                else -> error(
                    "No signing keystore found. Create signing/cloudbridge-local.keystore " +
                        "or install the Android SDK debug keystore."
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Same cert as debug so upgrades never force data wipe.
            signingConfig = signingConfigs.getByName("local")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("local")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true   // retained for legacy SetupActivity
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // ── Jetpack Compose (BOM-managed) ────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // ── Image Loading — Coil ─────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation("com.google.zxing:core:3.5.3")

    // ── AndroidX Media3 (kept for MediaSession steering-wheel ctrl) ──
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // ── AndroidX Core ────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.material)

    // ── Networking — Retrofit + OkHttp + Moshi ───────────────────────
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.core)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // ── Coroutines ───────────────────────────────────────────────────
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)

    // ── Room (local cache for offline library) ───────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Unit Testing ─────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
