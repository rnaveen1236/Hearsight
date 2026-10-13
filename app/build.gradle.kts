import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

// The app navigates from the live RealSense feed only, so Intel's librealsense Android library
// is required. It isn't published to Maven: build it with scripts/build_librealsense_aar.sh
// (CI does this automatically). Gradle sync works without it; building fails with this hint.
val realsenseAar = file("libs/librealsense.aar")

android {
    namespace = "com.hearsight.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hearsight.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    sourceSets["main"].java.srcDir("src/realsense/kotlin")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":navcore"))
    // Runs the segmentation model (app/src/main/assets/segmentation.onnx, see scripts/).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation(files(realsenseAar))
}

tasks.named("preBuild") {
    doFirst {
        if (!realsenseAar.exists()) {
            throw GradleException(
                "app/libs/librealsense.aar is missing. HearSight runs on the live RealSense feed only: " +
                    "build the library with scripts/build_librealsense_aar.sh (see README), " +
                    "or download the APK built by GitHub Actions.",
            )
        }
    }
}
