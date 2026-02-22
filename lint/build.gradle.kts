// lint/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:32.0.1")
    compileOnly("com.android.tools.lint:lint-checks:32.0.1")
}

tasks.jar {
    manifest {
        attributes["Lint-Registry-v2"] = "com.example.cssintegration.lint.CssLintRegistry"
    }
}