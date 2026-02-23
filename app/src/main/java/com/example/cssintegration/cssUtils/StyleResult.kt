package com.example.cssintegration.cssUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.cssintegration.annotation.CssStyle

// ─────────────────────────────────────────────────────────────────────────────
// StyleResult  —  immutable container for all resolved styling
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolved output of a CSS declaration block.
 *
 * Separates concerns between layout/box properties ([modifier]) and
 * typographic properties exposed via [toTextStyle].
 *
 * Use the convenience extensions [toTextStyle] and [modifier] when binding
 * to Compose components.
 */
data class StyleResult(
    val modifier: Modifier = Modifier,
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit.Unspecified,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textAlign: TextAlign? = null,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val lineHeight: TextUnit = TextUnit.Unspecified,
    val textDecoration: TextDecoration? = null,
    val verticalAlignment: Alignment.Vertical? = null,
    val horizontalAlignment: Alignment.Horizontal? = null
) {
    /**
     * Converts the typographic fields of this [StyleResult] into a Compose [TextStyle].
     * Only non-default fields are included, preserving theme-level defaults for anything
     * not explicitly declared in the source CSS.
     */
    fun toTextStyle(): TextStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight ?: FontWeight.Normal,
        fontStyle = fontStyle ?: FontStyle.Normal,
        textAlign = textAlign ?: TextAlign.Unspecified,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textDecoration = textDecoration ?: TextDecoration.None,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// rememberCssStyle  —  Compose-idiomatic entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads the [CssStyle] annotation on the calling composable's class and returns
 * a remembered [StyleResult], recomputed only when [css] changes.
 *
 * ```kotlin
 * @CssStyle("color: #E91E63; font-size: 20; font-weight: 700;")
 * @Composable
 * fun Headline(text: String) {
 *     val style = rememberCssStyle()
 *     Text(text, style = style.toTextStyle(), modifier = style.modifier)
 * }
 * ```
 */
@Composable
inline fun <reified T : Any> T.rememberCssStyle(): StyleResult {
    val annotation = T::class.annotations
        .filterIsInstance<CssStyle>()
        .firstOrNull()
    val css = annotation?.value ?: return StyleResult()
    return remember(css) { parseCssDeclaration(css) }
}

/**
 * Parses a raw CSS declaration string (no selector, no braces) into a [StyleResult].
 *
 * ```kotlin
 * val result = rememberParsedCss("font-size: 16; color: #333; padding: 12px;")
 * Text("Hello", style = result.toTextStyle(), modifier = result.modifier)
 * ```
 */
@Composable
fun rememberParsedCss(css: String): StyleResult = remember(css) { parseCssDeclaration(css) }