package io.github.stagnant09.csscompose

import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────────
// The cross-platform API. Code in commonMain that only uses these (plus Compose
// runtime state) renders on Android and, through Compose HTML, in the browser.
//
// - Android: CSS is parsed and resolved by this library into Compose layouts,
//   modifiers and animations. Android also has overloads that take a `Modifier`.
// - Web: `Div` renders a `<div>` and `CssText` a `<text>` element, with exactly the
//   `id` and `class` attributes passed here; the browser applies the stylesheet.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An HTML-like `<div>` styled with CSS.
 *
 * @param css inline declarations, applied over the stylesheet rules (like the `style` attribute).
 * @param id matched against `#id` rules; rendered as the `id` attribute on the web.
 * @param className space-separated class names matched against `.class` rules.
 * @param enabled when `false`, `:disabled` rules match and clicks are ignored.
 * @param index position among siblings for `:nth-child()` & co. (Android only; see [CssIndex]).
 * @param onClick makes the element clickable (and keyboard-focusable on the web).
 */
@Composable
expect fun Div(
    css: String = "",
    id: String = "",
    className: String = "",
    enabled: Boolean = true,
    index: CssIndex? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
)

/**
 * Text styled with CSS. Stylesheet rules match it as the element `text`.
 *
 * Parameters are as for [Div]. Note the order: `css`, `className`, then `id`.
 */
@Composable
expect fun CssText(
    text: String,
    css: String = "",
    className: String = "",
    id: String = "",
    enabled: Boolean = true,
    index: CssIndex? = null,
    onClick: (() -> Unit)? = null,
)

/**
 * Makes a stylesheet ([css]) apply to everything inside [content].
 * Nested providers add to the outer stylesheet.
 */
@Composable
expect fun CssStylesheetProvider(css: String, content: @Composable () -> Unit)
