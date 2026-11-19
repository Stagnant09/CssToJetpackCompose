// The CSS → Compose library.
//
// - Android: parser, cascade, stylesheet, motion, flex layout and the Div / CssText components.
// - JS (Compose HTML): Div / CssText render real DOM elements with the given id and classes,
//   and the browser applies the stylesheet itself.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val composeMultiplatform = "1.7.1"

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    js(IR) {
        browser {
            testTask { enabled = false } // JS tests would need a headless browser; shared tests run on the JVM
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:$composeMultiplatform")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // `api`: the public API exposes Modifier, TextStyle, Color, and Div/CssText build on Material3's Text.
            api(project.dependencies.platform("androidx.compose:compose-bom:2024.09.00"))
            api("androidx.compose.ui:ui")
            api("androidx.compose.ui:ui-graphics")
            api("androidx.compose.foundation:foundation")
            api("androidx.compose.material3:material3")
        }
        getByName("androidUnitTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
        jsMain.dependencies {
            api("org.jetbrains.compose.html:html-core:$composeMultiplatform")
        }
    }
}

android {
    namespace = "io.github.stagnant09.csscompose"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
