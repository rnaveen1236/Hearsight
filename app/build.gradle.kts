import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

// Intel's Android wrapper is not published to Maven; build it with scripts/build_librealsense_aar.sh.
// Without it the app still builds and runs in demo mode (simulated depth camera).
val realsenseAar = file("libs/librealsense.aar")
val hasRealSense = realsenseAar.exists()

android {
    namespace = "com.hearsight.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hearsight.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "HAS_REALSENSE", hasRealSense.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDir(if (hasRealSense) "src/realsense/kotlin" else "src/norealsense/kotlin")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":navcore"))
    if (hasRealSense) implementation(files(realsenseAar))
}
