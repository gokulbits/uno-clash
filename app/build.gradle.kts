plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the environment (CI secrets, or exported locally). See README.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val releaseKeystore = env("CC_KEYSTORE_PATH")

android {
    namespace = "dev.colorclash.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.colorclash"
        minSdk = 26
        targetSdk = 37
        versionCode = env("CC_VERSION_CODE")?.toInt() ?: 1
        versionName = env("CC_VERSION_NAME") ?: "0.5.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = env("CC_KEYSTORE_PASSWORD")
                keyAlias = env("CC_KEY_ALIAS")
                keyPassword = env("CC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val key = signingConfigs.findByName("release")
            if (key != null) {
                signingConfig = key
            } else {
                // The debug key is public, and it's also what gates in-app updates: an APK signed
                // with it can be replaced by anyone. Fine for a local install, never for release.
                logger.warn("CC_KEYSTORE_PATH is unset: this release build is debug-signed. Don't distribute it.")
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/DEPENDENCIES",
            )
        }
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

// A debug-signed "release" must never leave CI, where it would be mistaken for a shippable build.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (releaseKeystore == null && providers.environmentVariable("CI").isPresent) {
            error("Release build without CC_KEYSTORE_PATH; refusing to produce a debug-signed artifact.")
        }
    }
}

dependencies {
    implementation(project(":net"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    testImplementation(kotlin("test-junit5"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
