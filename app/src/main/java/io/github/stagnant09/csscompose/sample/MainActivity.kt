package io.github.stagnant09.csscompose.sample

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.stagnant09.csscompose.CssStyle
import io.github.stagnant09.csscompose.CssStylesheetProvider
import io.github.stagnant09.csscompose.rememberCssStyle
import io.github.stagnant09.csscompose.rememberParsedCss
import io.github.stagnant09.csscompose.CssText
import io.github.stagnant09.csscompose.Div
import io.github.stagnant09.csscompose.sample.ui.theme.CssIntegrationTheme
/** Asset name of ui/theme/css/styles.css (packaged by the assets srcDir in app/build.gradle.kts). */
private const val StylesheetAsset = "styles.css"

private fun Context.readStylesheet(): String =
    assets.open(StylesheetAsset).bufferedReader().use { it.readText() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appStylesheet = readStylesheet()
        enableEdgeToEdge()
        setContent {
            CssIntegrationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Showcase(
                        appStylesheet,
                        Modifier
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// One stylesheet for the whole showcase. The cards themselves are shared with the
// web sample (sample-shared/ShowcaseCards.kt); the ones below are Android-only.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Showcase(
    appStylesheet: String,
    modifier: Modifier = Modifier
) {
    CssStylesheetProvider(appStylesheet) {
        Div(modifier = modifier, className = "page") {
            ShowcaseCards()
            AnnotatedCard()
            LegacyHeadline("Hello World")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// @CssStyle on a style holder, read via rememberCssStyle<T>() (Android only:
// it reads the annotation through reflection)
// ─────────────────────────────────────────────────────────────────────────────

@CssStyle("padding: 16px; border-radius: 12px; border: 1px dashed #90A4AE; background-color: #ECEFF1")
private object AnnotatedBox

@Composable
private fun AnnotatedCard() {
    val style = rememberCssStyle<AnnotatedBox>()
    Div(modifier = style.modifier) {
        CssText("@CssStyle on an object, resolved with rememberCssStyle<AnnotatedBox>()", className = "muted")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The original rememberParsedCss API still works
// ─────────────────────────────────────────────────────────────────────────────

private const val HeadlineCss = "color: #f0ffff; font-size: 32px; font-weight: 700;" +
    " padding: 20px; letter-spacing: 5px; text-decoration: underline;" +
    " background-color: #001a1a; border: 6px solid #E91E63; border-radius: 12px"

@CssStyle(HeadlineCss)
@Composable
fun LegacyHeadline(text: String) {
    val style = rememberParsedCss(HeadlineCss)
    Text(text = text, style = style.toTextStyle(), modifier = style.modifier)
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
fun ShowcasePreview() {
    val context = LocalContext.current
    val appStylesheet = remember(context) { context.readStylesheet() }
    CssIntegrationTheme { Showcase(appStylesheet) }
}
