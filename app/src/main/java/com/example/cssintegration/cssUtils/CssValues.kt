package com.example.cssintegration.cssUtils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Value-level parsers shared by the declaration parser.
//
// Unit conventions (Compose has no CSS pixel, so we pick the closest analogue):
// - `px`, `dp` and bare numbers      → dp (or sp for text)
// - `rem`                            → multiples of 16 (the CSS root font size)
// - `em`                             → 16 dp for boxes, Compose `em` for text
// - `%`                              → fraction of the parent (width / height only)
// ─────────────────────────────────────────────────────────────────────────────

/** Font size CSS uses as the root (`1rem`). */
private const val ROOT_FONT_SIZE = 16f

/** A CSS length resolved for box properties (width, height, padding, …). */
sealed interface CssLength {
    data class Fixed(val dp: Dp) : CssLength
    /** A percentage, stored as a 0..1 fraction. */
    data class Percent(val fraction: Float) : CssLength
    data object Auto : CssLength
}

private val numberWithUnit = Regex("""^([+-]?(?:\d+\.?\d*|\.\d+))([a-z%]*)$""")

/** Splits `"12.5px"` into `12.5f to "px"`. Returns `null` for non-numeric input. */
internal fun splitNumber(value: String): Pair<Float, String>? {
    val match = numberWithUnit.matchEntire(value.trim().lowercase()) ?: return null
    val number = match.groupValues[1].toFloatOrNull() ?: return null
    return number to match.groupValues[2]
}

/** Parses a box length (`12px`, `1.5rem`, `50%`, `auto`). */
fun parseLength(value: String): CssLength? {
    val trimmed = value.trim().lowercase()
    if (trimmed == "auto") return CssLength.Auto
    val (number, unit) = splitNumber(trimmed) ?: return null
    return when (unit) {
        "", "px", "dp" -> CssLength.Fixed(number.dp)
        "rem", "em" -> CssLength.Fixed((number * ROOT_FONT_SIZE).dp)
        "%" -> CssLength.Percent(number / 100f)
        else -> null
    }
}

/** Parses a length that must resolve to an absolute [Dp] (padding, border width, radius…). */
fun parseDp(value: String): Dp? = (parseLength(value) as? CssLength.Fixed)?.dp

/**
 * Converts a CSS length to a Compose [TextUnit].
 *
 * - `px`, `sp`, `dp`, bare numbers → `sp`
 * - `rem` → `sp` × 16
 * - `em` → Compose `em` (relative to the current font size)
 * - `%` → Compose `em` (`150%` → `1.5.em`)
 *
 * Returns [TextUnit.Unspecified] for unrecognised input.
 */
fun parseLengthAsTextUnit(value: String): TextUnit {
    val (number, unit) = splitNumber(value) ?: return TextUnit.Unspecified
    return when (unit) {
        "", "px", "sp", "dp" -> number.sp
        "rem" -> (number * ROOT_FONT_SIZE).sp
        "em" -> number.em
        "%" -> (number / 100f).em
        else -> TextUnit.Unspecified
    }
}

/**
 * CSS `line-height`: a unitless number is a multiplier of the font size
 * (`1.5` → `1.5.em`), anything else follows [parseLengthAsTextUnit].
 */
fun parseLineHeight(value: String): TextUnit {
    val trimmed = value.trim().lowercase()
    if (trimmed == "normal") return TextUnit.Unspecified
    val (number, unit) = splitNumber(trimmed) ?: return TextUnit.Unspecified
    return if (unit.isEmpty()) number.em else parseLengthAsTextUnit(trimmed)
}

/**
 * Expands a 1–4 value box shorthand (`padding`, `margin`, `border-radius`…)
 * into `[top, right, bottom, left]` using the standard CSS rules.
 */
internal fun <T> expandBoxShorthand(values: List<T>): List<T>? = when (values.size) {
    1 -> listOf(values[0], values[0], values[0], values[0])
    2 -> listOf(values[0], values[1], values[0], values[1])
    3 -> listOf(values[0], values[1], values[2], values[1])
    4 -> values
    else -> null
}

/** Splits a value on whitespace while keeping parenthesised groups (`rgb(1, 2, 3)`) intact. */
internal fun splitTokens(value: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (ch in value.trim()) {
        when {
            ch == '(' -> { depth++; current.append(ch) }
            ch == ')' -> { depth--; current.append(ch) }
            ch.isWhitespace() && depth == 0 -> {
                if (current.isNotEmpty()) tokens += current.toString()
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS color value into a Compose [Color].
 *
 * Supported formats:
 * - Hex: `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA` (CSS order: alpha last)
 * - `rgb()` / `rgba()` — comma or space separated, numbers or percentages,
 *   with optional alpha (`rgb(255 0 0 / 50%)`)
 * - `hsl()` / `hsla()`
 * - CSS named colors (see [colorByName]) and `transparent`
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
        raw.startsWith("#") -> parseHexColor(raw.substring(1))
        raw.startsWith("rgb") -> parseRgbFunction(raw)
        raw.startsWith("hsl") -> parseHslFunction(raw)
        else -> colorByName(raw)
    }
}

private fun parseHexColor(hex: String): Color? {
    if (hex.any { it.digitToIntOrNull(16) == null }) return null
    val expanded = when (hex.length) {
        3, 4 -> hex.map { "$it$it" }.joinToString("")
        6, 8 -> hex
        else -> return null
    }
    val r = expanded.substring(0, 2).toInt(16)
    val g = expanded.substring(2, 4).toInt(16)
    val b = expanded.substring(4, 6).toInt(16)
    val a = if (expanded.length == 8) expanded.substring(6, 8).toInt(16) else 255
    return Color(r, g, b, a)
}

/** Extracts the arguments of `name(a, b, c / d)` as `[a, b, c, d]`. */
private fun functionArgs(raw: String): List<String>? {
    val open = raw.indexOf('(')
    if (open < 0 || !raw.endsWith(")")) return null
    return raw.substring(open + 1, raw.length - 1)
        .replace("/", " ")
        .replace(",", " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
}

/** Parses an alpha component: `0.5` or `50%`. */
private fun parseAlpha(token: String?): Float? {
    if (token == null) return 1f
    val (number, unit) = splitNumber(token) ?: return null
    return when (unit) {
        "" -> number
        "%" -> number / 100f
        else -> null
    }?.coerceIn(0f, 1f)
}

private fun parseRgbFunction(raw: String): Color? {
    val args = functionArgs(raw) ?: return null
    if (args.size !in 3..4) return null
    val channels = args.take(3).map { token ->
        val (number, unit) = splitNumber(token) ?: return null
        when (unit) {
            "" -> number / 255f
            "%" -> number / 100f
            else -> return null
        }.coerceIn(0f, 1f)
    }
    val alpha = parseAlpha(args.getOrNull(3)) ?: return null
    return Color(channels[0], channels[1], channels[2], alpha)
}

private fun parseHslFunction(raw: String): Color? {
    val args = functionArgs(raw) ?: return null
    if (args.size !in 3..4) return null
    val (hueNumber, hueUnit) = splitNumber(args[0]) ?: return null
    val hue = when (hueUnit) {
        "", "deg" -> hueNumber
        "turn" -> hueNumber * 360f
        "rad" -> Math.toDegrees(hueNumber.toDouble()).toFloat()
        else -> return null
    }
    val saturation = splitNumber(args[1])?.first?.div(100f)?.coerceIn(0f, 1f) ?: return null
    val lightness = splitNumber(args[2])?.first?.div(100f)?.coerceIn(0f, 1f) ?: return null
    val alpha = parseAlpha(args.getOrNull(3)) ?: return null
    return hslToColor(hue, saturation, lightness, alpha)
}

private fun hslToColor(hue: Float, s: Float, l: Float, alpha: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Corners
// ─────────────────────────────────────────────────────────────────────────────

/** Parses one corner radius (`8px`, `50%`). Elliptical radii are not supported. */
fun parseCornerValue(token: String): CornerValue? = when (val length = parseLength(token.substringBefore('/').trim())) {
    is CssLength.Fixed -> CornerValue(length.dp.value.coerceAtLeast(0f))
    is CssLength.Percent -> CornerValue(length.fraction * 100f, isPercent = true)
    else -> null
}

/**
 * Parses CSS `border-radius` (1–4 values) into corners in CSS order:
 * top-left, top-right, bottom-right, bottom-left.
 */
fun parseCornerRadii(value: String): List<CornerValue>? {
    val corners = splitTokens(value.substringBefore('/')).map { parseCornerValue(it) ?: return null }
    return expandBoxShorthand(corners)
}
