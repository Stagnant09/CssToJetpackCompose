package io.github.stagnant09.csscompose.sample.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.stagnant09.csscompose.CssStylesheetProvider
import io.github.stagnant09.csscompose.Div
import io.github.stagnant09.csscompose.sample.ShowcaseCards
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        // styles.css is the same file the Android app reads from its assets
        var stylesheet by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            window.fetch("styles.css").then { response -> response.text().then { stylesheet = it } }
        }
        stylesheet?.let { css ->
            CssStylesheetProvider(css) {
                Div(className = "page") { ShowcaseCards() }
            }
        }
    }
}
