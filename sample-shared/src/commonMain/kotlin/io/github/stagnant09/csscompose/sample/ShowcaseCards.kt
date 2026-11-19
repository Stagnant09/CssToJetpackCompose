package io.github.stagnant09.csscompose.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.stagnant09.csscompose.CssIndex
import io.github.stagnant09.csscompose.CssText
import io.github.stagnant09.csscompose.Div

// ─────────────────────────────────────────────────────────────────────────────
// The showcase, written once. Only the cross-platform API is used (Div, CssText,
// CssIndex and Compose runtime state), so the same calls render on Android and,
// as real <div>/<text> elements with these ids and classes, in the browser.
// Styles come from ui/theme/css/styles.css in the app module.
// ─────────────────────────────────────────────────────────────────────────────

/** All shared showcase cards, in order. Put it inside a `CssStylesheetProvider`. */
@Composable
fun ShowcaseCards() {
    Hero()
    InteractionCard()
    OutlineCard()
    AnimationCard()
    ListCard()
    LayoutCard()
    FilterCard()
    ScrollCard()
    GridCard()
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
private fun OutlineCard() {
    Div(className = "card") {
        CssText("outline", className = "title")
        CssText("solid · offset 4px · dashed · offset -6px", className = "muted label")
        Div(className = "outlines") {
            listOf("solid", "offset", "dashed", "inset").forEach { Div(className = "swatch $it") {} }
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
        // The rows get their own parent so the browser counts the same siblings as CssIndex
        // (in the card, the title and label would count too). `display: block` makes the
        // wrapper full width on Android, as a <div> is in the browser.
        Div(css = "display: block") {
            items.forEachIndexed { i, (name, price) ->
                Div(className = "row", index = CssIndex(i, items.size)) {
                    CssText(name)
                    CssText(price, className = "price")
                }
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
