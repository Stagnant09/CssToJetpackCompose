// lint/build.gradle.kts — lint API version must be AGP version + 23 (AGP 8.3.2 → 31.3.2)
plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.3.2")
    compileOnly("com.android.tools.lint:lint-checks:31.3.2")
}

tasks.jar {
    manifest {
        attributes["Lint-Registry-v2"] = "com.example.cssintegration.lint.CssLintRegistry"
    }
}