package com.example.cssintegration.cssUtils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt

// ─────────────────────────────────────────────────────────────────────────────
// Top-level parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS declaration block — a semicolon-delimited set of `property: value`
 * pairs — into a fully resolved [StyleResult].
 *
 * This function is the single entry point for all CSS parsing. It handles:
 * - Whitespace normalisation
 * - Empty/malformed declarations gracefully (defaults to [StyleResult])
 * - Both text and layout properties in one pass
 *
 * @param css Raw CSS declaration string, e.g. `"color: red; font-size: 14;"`.
 *   Selector blocks and curly braces are **not** expected here.
 */
fun parseCssDeclaration(css: String): StyleResult {
    if (css.isBlank()) return StyleResult()

    var modifier : Modifier = Modifier
    var color = Color.Unspecified
    var fontSize = TextUnit.Unspecified
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textAlign: TextAlign? = null
    var letterSpacing = TextUnit.Unspecified
    var lineHeight = TextUnit.Unspecified
    var textDecoration: TextDecoration? = null
    var verticalAlignment: Alignment.Vertical? = null
    var horizontalAlignment: Alignment.Horizontal? = null

    css.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() && ':' in it }
        .forEach { declaration ->
            val key = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':').trim()

            when (key) {
                // ── Typography ──────────────────────────────────────────────
                "color" -> color = parseColor(value)
                "font-size" -> fontSize = parseLengthAsTextUnit(value)
                "font-weight" -> fontWeight = parseFontWeight(value)
                "font-style" -> fontStyle = parseFontStyle(value)
                "text-align" -> textAlign = parseTextAlign(value)
                "letter-spacing" -> letterSpacing = parseLengthAsTextUnit(value)
                "line-height" -> lineHeight = parseLengthAsTextUnit(value)
                "text-decoration" -> textDecoration = parseTextDecoration(value)

                // ── Layout / Box ─────────────────────────────────────────────
                "padding" -> modifier = modifier.then(parsePadding(value))
                "background-color",
                "background" -> parseColorOrNull(value)?.let { bg ->
                    modifier = modifier.background(bg)
                }

                "border" -> parseBorderOrNull(value)?.let { (width, clr) ->
                    modifier = modifier.border(width.dp, clr)
                }

                "margin" -> modifier = modifier.then(parseMargin(value))

                // ── Display ──────────────────────────────────────────────────
                "align-content" -> verticalAlignment = parseVerticalAlignment(value)
                "justify-content" -> horizontalAlignment = parseHorizontalAlignment(value)
            }
        }

    return StyleResult(
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textDecoration = textDecoration,
        verticalAlignment = verticalAlignment,
        horizontalAlignment = horizontalAlignment
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Selector-map parser (full stylesheet)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a minimal CSS stylesheet into a map of selector → [StyleResult].
 *
 * Only class selectors (`.foo`) and element selectors (`Text`, `Button`) are
 * supported. Combinators, pseudo-classes, and media queries are ignored.
 *
 * ```kotlin
 * val styles = parseCssStylesheet("""
 *     .title  { color: #212121; font-size: 24; font-weight: bold; }
 *     .body   { color: #616161; font-size: 14; line-height: 22; }
 * """.trimIndent())
 *
 * val titleStyle = styles[".title"]?.toTextStyle()
 * ```
 */
fun parseCssStylesheet(css: String): Map<String, StyleResult> {
    val ruleRegex = Regex("""([^{}]+)\{([^{}]*)}""")
    return ruleRegex.findAll(css)
        .associate { match ->
            val selector = match.groupValues[1].trim()
            val declarations = match.groupValues[2].trim()
            selector to parseCssDeclaration(declarations)
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Length / unit parsing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a CSS length value to a Compose [TextUnit] (always in `sp`).
 *
 * Supported units: `px`, `sp`, `em` (treated as sp), bare integers.
 * Returns [TextUnit.Unspecified] for unrecognised input.
 */
internal fun parseLengthAsTextUnit(value: String): TextUnit {
    val trimmed = value.trim().lowercase()
    return when {
        trimmed.endsWith("sp") -> trimmed.removeSuffix("sp").toFloatOrNull()?.sp
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").toFloatOrNull()?.sp   // 1:1 approximation
        trimmed.endsWith("em") -> trimmed.removeSuffix("em").toFloatOrNull()?.sp
        else                   -> trimmed.toFloatOrNull()?.sp
    } ?: TextUnit.Unspecified
}

// ─────────────────────────────────────────────────────────────────────────────
// Padding shorthand
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses CSS `padding` shorthand (1–4 values) into a [Modifier.padding] call.
 *
 * Follows standard CSS expansion rules:
 * - 1 value  → all sides
 * - 2 values → vertical | horizontal
 * - 3 values → top | horizontal | bottom
 * - 4 values → top | end | bottom | start
 */
internal fun parsePadding(value: String): Modifier {
    val parts = value.trim().split(Regex("\\s+"))
        .mapNotNull { token ->
            val v = token.lowercase().removeSuffix("px").removeSuffix("dp").toFloatOrNull()
            v?.dp
        }

    return when (parts.size) {
        1    -> Modifier.padding(all = parts[0])
        2    -> Modifier.padding(vertical = parts[0], horizontal = parts[1])
        3    -> Modifier.padding(top = parts[0], start = parts[1], end = parts[1], bottom = parts[2])
        4    -> Modifier.padding(top = parts[0], end = parts[1], bottom = parts[2], start = parts[3])
        else -> Modifier
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Margin handling
// ─────────────────────────────────────────────────────────────────────────────
fun parseMargin(value: String): Modifier {
    val parts = value.trim().split(Regex("\\s+"))
        .mapNotNull { token ->
            val v = token.lowercase().removeSuffix("px").removeSuffix("dp").toFloatOrNull()
            v?.dp
        }

    return when (parts.size) {
        1 -> Modifier.absolutePadding(parts[0], parts[0], parts[0], parts[0])
        2 -> Modifier.absolutePadding(parts[1], parts[0], parts[1], parts[0])
        3 -> Modifier.absolutePadding(parts[1], parts[0], parts[1], parts[2])
        4 -> Modifier.absolutePadding(parts[3], parts[0], parts[1], parts[2])
        else -> Modifier
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Border shorthand
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a simplified CSS `border` shorthand: `<width> <style> <color>`.
 * Only the width and color are extracted; border-style is intentionally ignored
 * since Compose's [Modifier.border] uses a single solid outline.
 *
 * Returns `null` if parsing fails, allowing the caller to skip the modifier.
 */
fun parseBorderOrNull(value: String): Pair<Float, Color>? {
    val parts = value.trim().split(Regex("\\s+"))
    val width = parts.firstOrNull()
        ?.lowercase()
        ?.removeSuffix("px")
        ?.removeSuffix("dp")
        ?.toFloatOrNull()
        ?: return null
    val colorToken = parts.lastOrNull() ?: return null
    val color = parseColorOrNull(colorToken) ?: return null
    return width to color
}

// ─────────────────────────────────────────────────────────────────────────────
// Color parsing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS color value into a Compose [Color].
 *
 * Supported formats:
 * - Hex: `#RGB`, `#RRGGBB`, `#AARRGGBB`
 * - `rgb(r, g, b)` and `rgba(r, g, b, a)`
 * - CSS named colors (see [cssNamedColors])
 *
 * Falls back to [Color.Black] on unrecognised input.
 */
fun parseColor(color: String): Color = parseColorOrNull(color) ?: Color.Black

/**
 * Like [parseColor] but returns `null` instead of a fallback on failure,
 * allowing call sites to choose their own default.
 */
fun parseColorOrNull(color: String): Color? {
    val raw = color.trim().lowercase()
    return when {
        raw.startsWith("#")    -> parseHexColor(raw)
        raw.startsWith("rgba") -> parseRgbaColor(raw)
        raw.startsWith("rgb")  -> parseRgbColor(raw)
        else                   -> colorByName(raw)
    }
}

private fun parseHexColor(hex: String): Color? = try {
    Color(hex.toColorInt())
} catch (_: IllegalArgumentException) { null }

private fun parseRgbColor(raw: String): Color? {
    val nums = raw.removePrefix("rgb(").removeSuffix(")").split(",")
        .mapNotNull { it.trim().toIntOrNull() }
    return if (nums.size == 3) Color(nums[0], nums[1], nums[2]) else null
}

private fun parseRgbaColor(raw: String): Color? {
    val nums = raw.removePrefix("rgba(").removeSuffix(")").split(",")
    val rgb = nums.take(3).mapNotNull { it.trim().toIntOrNull() }
    val alpha = nums.getOrNull(3)?.trim()?.toFloatOrNull()
    return if (rgb.size == 3 && alpha != null)
        Color(rgb[0], rgb[1], rgb[2], (alpha * 255).toInt())
    else null
}

// ─────────────────────────────────────────────────────────────────────────────
// Typography value parsers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS `font-weight` value.
 * Accepts numeric weights (`100`–`900`) and keywords (`normal`, `bold`, `bolder`, `lighter`).
 */
fun parseFontWeight(fWeight: String): FontWeight {
    val trimmed = fWeight.trim().lowercase()
    trimmed.toIntOrNull()?.let { return FontWeight(it) }
    return when (trimmed) {
        "normal"  -> FontWeight.Normal
        "bold"    -> FontWeight.Bold
        "bolder"  -> FontWeight.ExtraBold
        "lighter" -> FontWeight.Light
        else      -> FontWeight.Normal
    }
}

/**
 * Parses a CSS `font-style` value (`normal`, `italic`, `oblique`).
 */
fun parseFontStyle(fStyle: String): FontStyle = when (fStyle.trim().lowercase()) {
    "italic", "oblique" -> FontStyle.Italic
    else                -> FontStyle.Normal
}

/**
 * Parses a CSS `text-align` value (`left`, `right`, `center`, `justify`).
 */
fun parseTextAlign(tAlign: String): TextAlign = when (tAlign.trim().lowercase()) {
    "left"    -> TextAlign.Left
    "center"  -> TextAlign.Center
    "right"   -> TextAlign.Right
    "justify" -> TextAlign.Justify
    "end"     -> TextAlign.End
    else      -> TextAlign.Start
}

/**
 * Parses a CSS `text-decoration` value (`none`, `underline`, `line-through`).
 */
fun parseTextDecoration(tDecor: String): TextDecoration = when (tDecor.trim().lowercase()) {
    "underline"    -> TextDecoration.Underline
    "line-through" -> TextDecoration.LineThrough
    else           -> TextDecoration.None
}

fun parseVerticalAlignment(vAlign: String) : Alignment.Vertical? = when (vAlign.trim().lowercase()) {
    "flex-start" -> Alignment.Top
    "flex-end" -> Alignment.Bottom
    "center" -> Alignment.CenterVertically
    else -> null
}

fun parseHorizontalAlignment(hAlign: String) : Alignment.Horizontal? = when (hAlign.trim().lowercase()) {
    "flex-start" -> Alignment.Start
    "flex-end" -> Alignment.End
    "center" -> Alignment.CenterHorizontally
    else -> null
}