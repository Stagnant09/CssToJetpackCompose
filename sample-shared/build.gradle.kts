// Showcase cards written once against the cross-platform Div / CssText API,
// rendered by the Android app (app) and the browser app (web-sample).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    js(IR) {
        browser { testTask { enabled = false } }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":css-integration"))
        }
    }
}

android {
    namespace = "io.github.stagnant09.csscompose.sample.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
