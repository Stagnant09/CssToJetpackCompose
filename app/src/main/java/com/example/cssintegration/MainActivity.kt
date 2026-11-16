package com.example.cssintegration

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.cssintegration.annotation.CssStyle
import com.example.cssintegration.cssUtils.CssIndex
import com.example.cssintegration.cssUtils.CssStylesheetProvider
import com.example.cssintegration.cssUtils.rememberCssStyle
import com.example.cssintegration.cssUtils.rememberParsedCss
import com.example.cssintegration.ui.components.CssText
import com.example.cssintegration.ui.components.Div
import com.example.cssintegration.ui.theme.CssIntegrationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CssIntegrationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Showcase(
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
// One stylesheet for the whole showcase
// ─────────────────────────────────────────────────────────────────────────────

private const val AppStylesheet = """
    :root {
        --accent: #E91E63;
        --accent-dark: #AD1457;
        --accent-soft: #FCE4EC;
        --radius: 16px;
        --gap: 16px;
    }

    div  { color: #263238; }
    text { font-size: 15px; }

    .page { display: flex; flex-direction: column; gap: var(--gap); padding: 16px; }
    @media (min-width: 600px) { .page { --gap: 24px; padding: 32px; } }

    .card {
        display: block;
        padding: 16px;
        border-radius: var(--radius);
        background: #ffffff;
        box-shadow: 0 6px 16px rgba(0, 0, 0, 0.10), 0 1px 3px rgba(0, 0, 0, 0.08);
    }
    .card.accent { border: 2px solid var(--accent); }
    .card > .title { font: 700 20px sans-serif; margin-bottom: 4px; }
    .muted { color: #78909c; font-size: 13px; }
    .label { margin: 12px 0 8px 0; }
    .chip  { padding: 6px 12px; border-radius: 50%; background: var(--accent-soft);
             color: var(--accent-dark); font-weight: 600; }

    #hero {
        display: flex; flex-direction: column; gap: 6px;
        padding: 24px;
        border-radius: 20px;
        background: linear-gradient(135deg, #E91E63, #673AB7);
        color: white;
    }

    /* ── :active / :disabled + transitions ── */
    .button {
        display: flex; justify-content: center;
        padding: 14px 20px;
        border-radius: 12px;
        background: var(--accent);
        color: white;
        font-weight: 700;
        box-shadow: 0 4px 10px rgba(233, 30, 99, 0.45);
        transition: background-color 250ms ease, transform 150ms ease-out, box-shadow 250ms;
    }
    .button:active   { background: var(--accent-dark); transform: scale(0.95);
                       box-shadow: 0 1px 2px rgba(233, 30, 99, 0.3); }
    .button:disabled { background: #B0BEC5; box-shadow: none; }
    .button.ghost    { background: transparent; color: var(--accent); box-shadow: none;
                       border: 2px solid var(--accent); }
    .button.ghost:active { background: var(--accent-soft); }

    /* ── @keyframes ── */
    @keyframes pulse {
        0%, 100% { transform: scale(1); opacity: 1; }
        50%      { transform: scale(1.4); opacity: 0.5; }
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    @keyframes slide {
        from { margin-left: 0; background: #FF9800; }
        to   { margin-left: 120px; background: #673AB7; }
    }
    .dot     { width: 16px; height: 16px; border-radius: 50%; background: var(--accent);
               animation: pulse 1.2s ease-in-out infinite; }
    .spinner { width: 32px; height: 32px; border-radius: 50%; border: 4px dotted var(--accent);
               animation: spin 1.5s linear infinite; }
    .slider  { width: 40px; height: 16px; border-radius: 8px;
               animation: slide 1.6s ease-in-out infinite alternate; }

    /* ── Structural pseudo-classes ── */
    .row { display: flex; justify-content: space-between; padding: 10px 12px; }
    .row:nth-child(odd) { background: #F5F7F8; }
    .row:first-child { border-radius: 8px 8px 0 0; }
    .row:last-child  { border-radius: 0 0 8px 8px; border-bottom: 2px solid var(--accent); }
    .row > .price { font-weight: 700; }
    .row:active { background: var(--accent-soft); }  /* press feedback without onClick */

    /* ── flex-grow, align-self, position: absolute ── */
    .toolbar { display: flex; align-items: center; gap: 8px; }
    .search  { flex: 1; padding: 10px 12px; border-radius: 10px; background: #F1F3F4; color: #90A4AE; }
    .icon    { position: relative; width: 40px; height: 40px; border-radius: 10px;
               background: var(--accent-soft); }
    .badge   { position: absolute; top: -6px; right: -6px; padding: 1px 6px; border-radius: 50%;
               background: var(--accent); color: white; font-size: 11px; font-weight: 700; }

    /* ── filter ── */
    .photos { display: flex; gap: 8px; }
    .photo  { flex: 1; height: 56px; border-radius: 10px;
              background: linear-gradient(135deg, #FF9800, #E91E63 50%, #3F51B5); }
    .photo.gray  { filter: grayscale(1); }
    .photo.hue   { filter: hue-rotate(140deg) saturate(1.4); }
    .photo.blur  { filter: blur(4px); }
    .photo.sepia { filter: sepia(0.9); }

    /* ── overflow + dashed borders ── */
    .scroller { height: 96px; overflow-y: auto; padding: 8px 12px; border-radius: 10px;
                border: 2px dashed #B0BEC5; }
"""

@Composable
fun Showcase(modifier: Modifier = Modifier) {
    CssStylesheetProvider(AppStylesheet) {
        Div(modifier = modifier, className = "page") {
            Hero()
            InteractionCard()
            AnimationCard()
            ListCard()
            LayoutCard()
            FilterCard()
            ScrollCard()
            GridCard()
            AnnotatedCard()
            LegacyHeadline("Hello World")
        }
    }
}

@Composable
private fun Hero() {
    Div(id = "hero") {
        CssText("CSS → Compose", css = "font-size: 28px; font-weight: 800; letter-spacing: 1px")
        CssText("Selectors, media queries, variables, flexbox, transitions and keyframes.", css = "opacity: 0.85")
    }
}

@Composable
private fun InteractionCard() {
    var taps by remember { mutableIntStateOf(0) }
    Div(className = "card") {
        CssText("Interaction", className = "title")
        CssText(":active, :disabled and transition", className = "muted label")
        Div(css = "display: flex; flex-direction: column; gap: 10px") {
            Div(className = "button", onClick = { taps++ }) { CssText("Tapped $taps times") }
            Div(className = "button ghost", onClick = { taps = 0 }) { CssText("Reset") }
            Div(className = "button", enabled = false, onClick = {}) { CssText("Disabled") }
        }
    }
}

@Composable
private fun AnimationCard() {
    Div(className = "card") {
        CssText("@keyframes", className = "title")
        CssText("pulse · spin · alternate slide", className = "muted label")
        Div(css = "display: flex; align-items: center; gap: 28px; padding: 8px") {
            Div(className = "dot") {}
            Div(className = "spinner") {}
            Div(css = "flex: 1") { Div(className = "slider") {} }
        }
    }
}

@Composable
private fun ListCard() {
    val items = listOf("Espresso" to "€2.10", "Flat white" to "€3.40", "Cortado" to "€2.80", "Matcha latte" to "€4.20")
    Div(className = "card") {
        CssText("Structural selectors", className = "title")
        CssText(":nth-child(odd), :first-child, :last-child", className = "muted label")
        items.forEachIndexed { i, (name, price) ->
            Div(className = "row", index = CssIndex(i, items.size)) {
                CssText(name)
                CssText(price, className = "price")
            }
        }
    }
}

@Composable
private fun LayoutCard() {
    Div(className = "card") {
        CssText("Flex & positioning", className = "title")
        CssText("flex: 1 search, absolute badge, row-reverse", className = "muted label")
        Div(className = "toolbar") {
            Div(className = "search") { CssText("Search…") }
            Div(className = "icon") { CssText("3", className = "badge") }
            Div(className = "icon") { CssText("12", className = "badge") }
        }
        Div(css = "display: flex; flex-direction: row-reverse; justify-content: space-between; margin-top: 12px") {
            listOf("1", "2", "3").forEach { CssText(it, css = "font-weight: 700") }
        }
    }
}

@Composable
private fun FilterCard() {
    Div(className = "card") {
        CssText("filter", className = "title")
        CssText("none · grayscale · hue-rotate · blur · sepia", className = "muted label")
        Div(className = "photos") {
            listOf("", "gray", "hue", "blur", "sepia").forEach { Div(className = "photo $it") {} }
        }
    }
}

@Composable
private fun ScrollCard() {
    Div(className = "card") {
        CssText("overflow-y: auto", className = "title")
        Div(className = "scroller") {
            repeat(8) { CssText("Scrollable line ${it + 1}", css = "padding: 4px 0") }
        }
    }
}

@Composable
private fun GridCard() {
    Div(className = "card accent") {
        CssText("Grid", className = "title", css = "margin-bottom: 12px")
        Div(css = "display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px") {
            repeat(6) { i ->
                CssText(
                    "Cell ${i + 1}",
                    css = "padding: 12px; border-radius: 8px; text-align: center;" +
                        " background: hsl(${200 + i * 20}, 70%, 92%)",
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// @CssStyle on a style holder, read via rememberCssStyle<T>()
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
    CssIntegrationTheme { Showcase() }
}
