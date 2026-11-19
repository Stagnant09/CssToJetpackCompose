package io.github.stagnant09.csscompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.dom.ElementBuilder
import org.jetbrains.compose.web.dom.TagElement
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.jetbrains.compose.web.dom.Div as HtmlDiv
import org.jetbrains.compose.web.dom.Text as HtmlText

// ─────────────────────────────────────────────────────────────────────────────
// Web (Compose HTML): real DOM elements, styled by the browser.
//
// The element gets exactly the `id` and `class` you pass, the inline `css` becomes its
// `style`, and CssStylesheetProvider adds a <style> element to the page. Nothing is
// parsed or laid out by this library on the web.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun Div(
    css: String,
    id: String,
    className: String,
    enabled: Boolean,
    index: CssIndex?, // the browser counts siblings itself
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    HtmlDiv(attrs = { cssAttributes(css, id, className, enabled, onClick) }) { content() }
}

/** `<text>`: an element of its own, so `text { … }` rules match it just like on Android. */
private val textElement = ElementBuilder.createBuilder<HTMLElement>("text")

@Composable
actual fun CssText(
    text: String,
    css: String,
    className: String,
    id: String,
    enabled: Boolean,
    index: CssIndex?, // the browser counts siblings itself
    onClick: (() -> Unit)?,
) {
    TagElement(
        elementBuilder = textElement,
        applyAttrs = { cssAttributes(css, id, className, enabled, onClick) },
    ) { HtmlText(text) }
}

@Composable
actual fun CssStylesheetProvider(css: String, content: @Composable () -> Unit) {
    DisposableEffect(css) {
        val style = document.createElement("style")
        style.setAttribute("data-css-compose", "")
        style.textContent = adaptForBrowser(css)
        document.head?.appendChild(style)
        onDispose { style.parentNode?.removeChild(style) }
    }
    content()
}

private fun <T : Element> AttrsScope<T>.cssAttributes(
    css: String,
    elementId: String,
    className: String,
    enabled: Boolean,
    handler: (() -> Unit)?,
) {
    if (elementId.isNotBlank()) id(elementId)
    val classes = splitClassNames(className)
    if (classes.isNotEmpty()) classes(*classes.toTypedArray())
    val declarations = splitInlineDeclarations(css)
    if (declarations.isNotEmpty()) style { declarations.forEach { (name, value) -> property(name, value) } }

    if (!enabled) {
        attr(DisabledAttribute, "true")
    } else if (handler != null) {
        // A clickable div behaves like a button: focusable with Tab, activated with Enter / Space
        tabIndex(0)
        attr("role", "button")
        onClick { handler() }
        onKeyDown { event ->
            if (event.key == "Enter" || event.key == " ") {
                event.preventDefault()
                handler()
            }
        }
    }
}
