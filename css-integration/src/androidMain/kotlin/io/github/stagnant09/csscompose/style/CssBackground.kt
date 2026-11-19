package io.github.stagnant09.csscompose.style

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// background / background-color / background-image
// ─────────────────────────────────────────────────────────────────────────────

/** A resolved background layer: a solid [color], a gradient [brush], or both. */
internal data class BackgroundSpec(val color: Color? = null, val brush: Brush? = null)

/**
 * Parses a `background` value. Supports solid colors, `linear-gradient(...)`,
 * `radial-gradient(...)` and `none`. Images (`url(...)`) are ignored.
 */
internal fun parseBackground(value: String): BackgroundSpec {
    val trimmed = value.trim()
    if (trimmed.equals("none", ignoreCase = true)) return BackgroundSpec()
    parseColorOrNull(trimmed)?.let { return BackgroundSpec(color = it) }

    var color: Color? = null
    var brush: Brush? = null
    for (token in splitTokens(trimmed)) {
        val lower = token.lowercase()
        when {
            lower.startsWith("linear-gradient(") -> brush = parseLinearGradient(lower)
            lower.startsWith("radial-gradient(") -> brush = parseRadialGradient(lower)
            else -> parseColorOrNull(lower)?.let { color = it }
        }
    }
    return BackgroundSpec(color, brush)
}

private fun gradientArgs(raw: String): List<String> =
    splitTopLevel(raw.substringAfter('(').removeSuffix(")"), ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** `linear-gradient([<angle> | to <side>,] <color-stop>, <color-stop>, …)` */
internal fun parseLinearGradient(raw: String): Brush? {
    var args = gradientArgs(raw)
    if (args.isEmpty()) return null
    var angle = 180f // CSS default: "to bottom"
    val first = args.first()
    when {
        first.startsWith("to ") -> {
            angle = directionToAngle(first.removePrefix("to ").trim()) ?: return null
            args = args.drop(1)
        }
        parseAngle(first) != null -> {
            angle = parseAngle(first)!!
            args = args.drop(1)
        }
    }
    val (colors, stops) = parseColorStops(args) ?: return null
    return CssLinearGradient(angle, colors, stops)
}

/** `radial-gradient([shape/position,] <color-stop>, …)` — always centred. */
internal fun parseRadialGradient(raw: String): Brush? {
    var args = gradientArgs(raw)
    // Drop a leading shape/size/position clause ("circle", "ellipse at center", …)
    if (args.isNotEmpty() && parseColorOrNull(splitTokens(args[0]).first()) == null) args = args.drop(1)
    val (colors, stops) = parseColorStops(args) ?: return null
    return Brush.radialGradient(*stops.zip(colors).toTypedArray())
}

private fun directionToAngle(direction: String): Float? {
    val parts = splitTokens(direction).toSet()
    return when (parts) {
        setOf("top") -> 0f
        setOf("top", "right") -> 45f
        setOf("right") -> 90f
        setOf("bottom", "right") -> 135f
        setOf("bottom") -> 180f
        setOf("bottom", "left") -> 225f
        setOf("left") -> 270f
        setOf("top", "left") -> 315f
        else -> null
    }
}

/**
 * Parses `<color> [<percentage>]` stops. Missing positions are distributed
 * evenly between their neighbours, following the CSS rules.
 */
private fun parseColorStops(args: List<String>): Pair<List<Color>, List<Float>>? {
    if (args.size < 2) return null
    val colors = mutableListOf<Color>()
    val positions = mutableListOf<Float?>()
    for (arg in args) {
        val tokens = splitTokens(arg)
        colors += parseColorOrNull(tokens[0]) ?: return null
        positions += tokens.getOrNull(1)?.let { splitNumber(it) }
            ?.takeIf { it.second == "%" }
            ?.let { it.first / 100f }
    }
    if (positions.first() == null) positions[0] = 0f
    if (positions.last() == null) positions[positions.lastIndex] = 1f
    var i = 0
    while (i < positions.size) {
        if (positions[i] == null) {
            val startIndex = i - 1
            var endIndex = i
            while (positions[endIndex] == null) endIndex++
            val start = positions[startIndex]!!
            val end = positions[endIndex]!!
            for (k in i until endIndex) {
                positions[k] = start + (end - start) * (k - startIndex) / (endIndex - startIndex)
            }
            i = endIndex
        }
        i++
    }
    // Stops must be non-decreasing
    var previous = 0f
    val resolved = positions.map { p -> max(p!!.coerceIn(0f, 1f), previous).also { previous = it } }
    return colors to resolved
}

private fun max(a: Float, b: Float) = if (a > b) a else b

/**
 * A linear gradient with CSS angle semantics: `0deg` points up, angles grow clockwise,
 * and the gradient line is sized so the corners receive the first/last colors.
 */
private data class CssLinearGradient(
    val angleDegrees: Float,
    val colors: List<Color>,
    val stops: List<Float>,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val halfLength = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * halfLength, cy - dy * halfLength),
            to = Offset(cx + dx * halfLength, cy + dy * halfLength),
            colors = colors,
            colorStops = stops,
        )
    }
}
