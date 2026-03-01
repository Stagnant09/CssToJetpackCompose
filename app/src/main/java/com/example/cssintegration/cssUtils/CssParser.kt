package com.example.cssintegration.cssUtils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
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
import com.example.cssintegration.ui.components.BlockWrap
import com.example.cssintegration.ui.components.BoxWrap
import com.example.cssintegration.ui.components.ColumnWrap
import com.example.cssintegration.ui.components.GridWrap
import com.example.cssintegration.ui.components.InlineWrap
import com.example.cssintegration.ui.components.NoneWrap
import com.example.cssintegration.ui.components.RowWrap
import com.example.cssintegration.ui.components.Wrap

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

    var modifier: Modifier = Modifier
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

                // ── Dimensions ───────────────────────────────────────────────
                "height" -> modifier = modifier.then(parseHeight(height = value))
                "min-height" -> modifier = modifier.then(parseHeight(height = value, min = true))
                "max-height" -> modifier = modifier.then(parseHeight(height = value, max = true))
                "width" -> modifier = modifier.then(parseWidth(width = value))
                "min-width" -> modifier = modifier.then(parseWidth(width = value, min = true))
                "max-width" -> modifier = modifier.then(parseWidth(width = value, max = true))
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

fun parseCssDeclarationForDiv(css: String): DivStyleResult {
    if (css.isBlank()) return DivStyleResult()

    var modifier: Modifier = Modifier
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

    // ── Flex / Grid context ──────────────────────────────────────────────────
    var display = ""
    var flexDirection = ""
    var justifyContent = ""
    var alignItems = ""
    var alignContent = ""
    var gridTemplateColumns = ""

    css.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() && ':' in it }
        .forEach { declaration ->
            val key = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':').trim()

            when (key) {
                "color"            -> color = parseColor(value)
                "font-size"        -> fontSize = parseLengthAsTextUnit(value)
                "font-weight"      -> fontWeight = parseFontWeight(value)
                "font-style"       -> fontStyle = parseFontStyle(value)
                "text-align"       -> textAlign = parseTextAlign(value)
                "letter-spacing"   -> letterSpacing = parseLengthAsTextUnit(value)
                "line-height"      -> lineHeight = parseLengthAsTextUnit(value)
                "text-decoration"  -> textDecoration = parseTextDecoration(value)
                "padding"          -> modifier = modifier.then(parsePadding(value))
                "background-color",
                "background"       -> parseColorOrNull(value)?.let { modifier = modifier.background(it) }
                "border"           -> parseBorderOrNull(value)?.let { (w, c) -> modifier = modifier.border(w.dp, c) }
                "margin"           -> modifier = modifier.then(parseMargin(value))
                "height"           -> modifier = modifier.then(parseHeight(height = value))
                "min-height"       -> modifier = modifier.then(parseHeight(height = value, min = true))
                "max-height"       -> modifier = modifier.then(parseHeight(height = value, max = true))
                "width"            -> modifier = modifier.then(parseWidth(width = value))
                "min-width"        -> modifier = modifier.then(parseWidth(width = value, min = true))
                "max-width"        -> modifier = modifier.then(parseWidth(width = value, max = true))

                // Flex / Grid
                "display"                  -> display = value
                "flex-direction"           -> flexDirection = value
                "justify-content"          -> justifyContent = value
                "align-items"              -> alignItems = value
                "align-content"            -> alignContent = value
                "grid-template-columns"    -> gridTemplateColumns = value
            }
        }

    val wrap = parseDivDisplay(
        display = display,
        flexDirection = flexDirection,
        justifyContent = justifyContent,
        alignItems = alignItems,
        gridTemplateColumns = gridTemplateColumns,
        alignContent = alignContent,
    )

    return DivStyleResult(
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
        horizontalAlignment = horizontalAlignment,
        wrap = wrap,
    )
}

// Overload
fun parseCssDeclaration(css: String, div: Boolean, clickable: Boolean): StyleResult {
    val parsed = parseCssDeclaration(css)
    if (div) {
        return parseCssDeclarationForDiv(css)
    }
    if (!clickable) {
        return parsed
    }
    return ClickableStyleResult(
        modifier = parsed.modifier,
        color = parsed.color,
        fontSize = parsed.fontSize,
        fontWeight = parsed.fontWeight,
        fontStyle = parsed.fontStyle,
        textAlign = parsed.textAlign,
        letterSpacing = parsed.letterSpacing,
        lineHeight = parsed.lineHeight,
        textDecoration = parsed.textDecoration,
        verticalAlignment = parsed.verticalAlignment,
        horizontalAlignment = parsed.horizontalAlignment,
        onHover = {},
        onClick = {}
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
        else -> trimmed.toFloatOrNull()?.sp
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
        1 -> Modifier.padding(all = parts[0])
        2 -> Modifier.padding(vertical = parts[0], horizontal = parts[1])
        3 -> Modifier.padding(top = parts[0], start = parts[1], end = parts[1], bottom = parts[2])
        4 -> Modifier.padding(top = parts[0], end = parts[1], bottom = parts[2], start = parts[3])
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
        raw.startsWith("#") -> parseHexColor(raw)
        raw.startsWith("rgba") -> parseRgbaColor(raw)
        raw.startsWith("rgb") -> parseRgbColor(raw)
        else -> colorByName(raw)
    }
}

private fun parseHexColor(hex: String): Color? = try {
    Color(hex.toColorInt())
} catch (_: IllegalArgumentException) {
    null
}

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
        "normal" -> FontWeight.Normal
        "bold" -> FontWeight.Bold
        "bolder" -> FontWeight.ExtraBold
        "lighter" -> FontWeight.Light
        else -> FontWeight.Normal
    }
}

/**
 * Parses a CSS `font-style` value (`normal`, `italic`, `oblique`).
 */
fun parseFontStyle(fStyle: String): FontStyle = when (fStyle.trim().lowercase()) {
    "italic", "oblique" -> FontStyle.Italic
    else -> FontStyle.Normal
}

/**
 * Parses a CSS `text-align` value (`left`, `right`, `center`, `justify`).
 */
fun parseTextAlign(tAlign: String): TextAlign = when (tAlign.trim().lowercase()) {
    "left" -> TextAlign.Left
    "center" -> TextAlign.Center
    "right" -> TextAlign.Right
    "justify" -> TextAlign.Justify
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

/**
 * Parses a CSS `text-decoration` value (`none`, `underline`, `line-through`).
 */
fun parseTextDecoration(tDecor: String): TextDecoration = when (tDecor.trim().lowercase()) {
    "underline" -> TextDecoration.Underline
    "line-through" -> TextDecoration.LineThrough
    else -> TextDecoration.None
}

fun parseVerticalAlignment(vAlign: String): Alignment.Vertical? = when (vAlign.trim().lowercase()) {
    "flex-start" -> Alignment.Top
    "flex-end" -> Alignment.Bottom
    "center" -> Alignment.CenterVertically
    else -> null
}

fun parseHorizontalAlignment(hAlign: String): Alignment.Horizontal? = when (hAlign.trim().lowercase()) {
    "flex-start" -> Alignment.Start
    "flex-end" -> Alignment.End
    "center" -> Alignment.CenterHorizontally
    else -> null
}

// Handle hover effect
fun parseHover(css: String) {

}

fun parseHeight(height: String, min: Boolean = false, max: Boolean = false): Modifier {
    return when {
        min && !max -> Modifier.heightIn(min = height.toInt().dp)
        !min && max -> Modifier.heightIn(max = height.toInt().dp)
        else -> Modifier.height(height.toInt().dp)
    }
}

fun parseWidth(width: String, min: Boolean = false, max: Boolean = false): Modifier {
    return when {
        min && !max -> Modifier.widthIn(min = width.toInt().dp)
        !min && max -> Modifier.widthIn(max = width.toInt().dp)
        else -> Modifier.width(width.toInt().dp)
    }
}

/**
 * Resolves a CSS `display` + flex/grid context into a [Wrap] instance.
 *
 * CSS properties consumed:
 * - `display`: flex | grid | block | inline | inline-block | none
 * - `flex-direction`: row | row-reverse | column | column-reverse
 * - `justify-content`: flex-start | flex-end | center | space-between | space-around | space-evenly
 * - `align-items`: flex-start | flex-end | center
 * - `grid-template-columns`: repeat(N, ...) or bare integer for column count
 * - `align-content`: for Box content alignment
 */
fun parseDivDisplay(
    display: String,
    flexDirection: String = "",
    justifyContent: String = "",
    alignItems: String = "",
    gridTemplateColumns: String = "",
    alignContent: String = "",
): Wrap? {
    val hArrangement = parseHorizontalArrangement(justifyContent)
    val vArrangement = parseVerticalArrangement(justifyContent)
    val hAlignment = parseHorizontalAlignmentFromItems(alignItems)
    val vAlignment = parseVerticalAlignmentFromItems(alignItems)

    return when (display.trim().lowercase()) {

        "flex" -> when (flexDirection.trim().lowercase()) {
            "column"         -> ColumnWrap(
                inverse = false,
                verticalArrangement = vArrangement,
                horizontalAlignment = hAlignment,
            )
            "column-reverse" -> ColumnWrap(
                inverse = true,
                verticalArrangement = vArrangement,
                horizontalAlignment = hAlignment,
            )
            "row-reverse"    -> RowWrap(
                inverse = true,
                horizontalArrangement = hArrangement,
                verticalAlignment = vAlignment,
            )
            else             -> RowWrap(   // "row" is CSS default
                inverse = false,
                horizontalArrangement = hArrangement,
                verticalAlignment = vAlignment,
            )
        }

        "grid"         -> GridWrap(
            columns = parseGridColumns(gridTemplateColumns),
            horizontalArrangement = hArrangement,
            verticalArrangement = vArrangement,
        )

        "block"        -> BlockWrap

        "inline",
        "inline-block",
        "inline-flex"  -> InlineWrap

        "box"          -> BoxWrap(
            contentAlignment = parseBoxAlignment(alignContent)
        )

        "none"         -> NoneWrap

        else           -> null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Arrangement / Alignment helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Maps CSS `justify-content` to a horizontal [Arrangement] (used in Row / Grid). */
private fun parseHorizontalArrangement(value: String): Arrangement.Horizontal =
    when (value.trim().lowercase()) {
        "flex-end"      -> Arrangement.End
        "center"        -> Arrangement.Center
        "space-between" -> Arrangement.SpaceBetween
        "space-around"  -> Arrangement.SpaceAround
        "space-evenly"  -> Arrangement.SpaceEvenly
        else            -> Arrangement.Start   // flex-start / default
    }

/** Maps CSS `justify-content` to a vertical [Arrangement] (used in Column). */
private fun parseVerticalArrangement(value: String): Arrangement.Vertical =
    when (value.trim().lowercase()) {
        "flex-end"      -> Arrangement.Bottom
        "center"        -> Arrangement.Center
        "space-between" -> Arrangement.SpaceBetween
        "space-around"  -> Arrangement.SpaceAround
        "space-evenly"  -> Arrangement.SpaceEvenly
        else            -> Arrangement.Top
    }

/** Maps CSS `align-items` to a horizontal [Alignment] (cross-axis of Column). */
private fun parseHorizontalAlignmentFromItems(value: String): Alignment.Horizontal =
    when (value.trim().lowercase()) {
        "flex-end", "end" -> Alignment.End
        "center"          -> Alignment.CenterHorizontally
        else              -> Alignment.Start
    }

/** Maps CSS `align-items` to a vertical [Alignment] (cross-axis of Row). */
private fun parseVerticalAlignmentFromItems(value: String): Alignment.Vertical =
    when (value.trim().lowercase()) {
        "flex-end", "end" -> Alignment.Bottom
        "center"          -> Alignment.CenterVertically
        else              -> Alignment.Top
    }

/** Maps CSS `align-content` to a Compose [Alignment] for [BoxWrap]. */
private fun parseBoxAlignment(value: String): Alignment =
    when (value.trim().lowercase()) {
        "center"                    -> Alignment.Center
        "flex-end", "end"           -> Alignment.BottomEnd
        "flex-start", "start"       -> Alignment.TopStart
        "top-center"                -> Alignment.TopCenter
        "bottom-center"             -> Alignment.BottomCenter
        "center-start"              -> Alignment.CenterStart
        "center-end"                -> Alignment.CenterEnd
        else                        -> Alignment.TopStart
    }

/**
 * Parses `grid-template-columns` into a column count for [GridWrap].
 *
 * Handles:
 * - `repeat(3, 1fr)` → 3
 * - `repeat(4, auto)` → 4
 * - bare integer `"2"` → 2
 */
private fun parseGridColumns(value: String): Int {
    if (value.isBlank()) return 2
    val repeatMatch = Regex("""repeat\s*\(\s*(\d+)""").find(value)
    if (repeatMatch != null) return repeatMatch.groupValues[1].toIntOrNull() ?: 2
    return value.trim().toIntOrNull() ?: 2
}