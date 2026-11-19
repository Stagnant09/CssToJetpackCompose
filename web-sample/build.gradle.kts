// The showcase in the browser (Compose HTML): the same ShowcaseCards as the Android app,
// rendered as real DOM elements and styled by the same styles.css.
//
//   gradle :web-sample:jsBrowserDevelopmentRun        (dev server)
//   gradle :web-sample:jsBrowserDistribution          (static files in build/dist/js/productionExecutable)
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    js(IR) {
        browser { testTask { enabled = false } }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            // The stylesheet the Android sample ships as an asset, served next to index.html
            resources.srcDir("../app/src/main/java/io/github/stagnant09/csscompose/sample/ui/theme/css")
            dependencies {
                implementation(project(":sample-shared"))
            }
        }
    }
}
