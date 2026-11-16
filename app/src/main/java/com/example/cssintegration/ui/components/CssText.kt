package com.example.cssintegration.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.example.cssintegration.cssUtils.CssIndex

/**
 * A [Text] styled with CSS — the `<span>` / `<p>` counterpart to [Div].
 *
 * The style is resolved from the current stylesheet (matching `text`, `.className`,
 * `#id`, ancestors, interaction state and `@media`) followed by the inline [css], and
 * merged over the inherited [LocalTextStyle] — so a parent `Div`'s `color` or
 * `font-size` carries through. Transitions and animations work too.
 *
 * Beyond the usual text properties it honours `text-transform`, `white-space: nowrap`,
 * `text-overflow: ellipsis`, `line-clamp`, `text-shadow`, and every box property.
 *
 * ```kotlin
 * CssText("Breaking news", css = "text-transform: uppercase; letter-spacing: 2px; color: #E91E63")
 * ```
 */
@Composable
fun CssText(
    text: String,
    modifier: Modifier = Modifier,
    css: String = "",
    className: String = "",
    id: String = "",
    enabled: Boolean = true,
    index: CssIndex? = null,
    onClick: (() -> Unit)? = null,
) {
    val element = rememberCssElement("text", className, id, css, enabled, index, onClick)
    val style = element.style
    if (style.isDisplayNone) return
    val density = LocalDensity.current
    Text(
        text = style.transformText(text),
        modifier = modifier.then(element.modifier),
        style = LocalTextStyle.current.merge(style.toTextStyle(density)),
        softWrap = style.softWrap,
        overflow = style.textOverflow,
        maxLines = style.maxLines ?: if (style.softWrap) Int.MAX_VALUE else 1,
    )
}
