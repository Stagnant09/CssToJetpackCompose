package io.github.stagnant09.csscompose.style

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Declarations
// ─────────────────────────────────────────────────────────────────────────────

/** A single `property: value` pair. [property] is lower-case (custom properties keep their case). */
data class CssDeclaration(
    val property: String,
    val value: String,
    val important: Boolean = false,
)

private val commentRegex = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val importantRegex = Regex("""\s*!\s*important\s*$""", RegexOption.IGNORE_CASE)

/** Removes `/* … */` comments. */
internal fun stripComments(css: String): String = css.replace(commentRegex, " ")

/**
 * Tokenises a declaration block into [CssDeclaration]s.
 *
 * Handles comments, `!important`, and semicolons inside parentheses or quotes
 * (`background: url("a;b")`). Malformed declarations are skipped.
 */
fun parseDeclarations(css: String): List<CssDeclaration> {
    val cleaned = stripComments(css)
    val result = mutableListOf<CssDeclaration>()
    val current = StringBuilder()
    var depth = 0
    var quote: Char? = null

    fun flush() {
        val declaration = current.toString().trim()
        current.clear()
        val colon = declaration.indexOf(':')
        if (colon <= 0) return
        val rawProperty = declaration.substring(0, colon).trim()
        val property = if (isCustomProperty(rawProperty)) rawProperty else rawProperty.lowercase()
        var value = declaration.substring(colon + 1).trim()
        val important = importantRegex.containsMatchIn(value)
        if (important) value = value.replace(importantRegex, "").trim()
        if (property.isNotEmpty() && value.isNotEmpty()) {
            result += CssDeclaration(property, value, important)
        }
    }

    for (ch in cleaned) {
        when {
            quote != null -> { if (ch == quote) quote = null; current.append(ch) }
            ch == '"' || ch == '\'' -> { quote = ch; current.append(ch) }
            ch == '(' -> { depth++; current.append(ch) }
            ch == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(ch) }
            ch == ';' && depth == 0 -> flush()
            else -> current.append(ch)
        }
    }
    flush()
    return result
}

/**
 * Applies the CSS cascade to declarations given in source order (lowest priority first):
 * later declarations win, and `!important` beats anything that isn't.
 *
 * The returned map iterates in the order the *winning* declarations appeared, so
 * shorthand/longhand interplay (`padding` vs `padding-top`) resolves the CSS way.
 */
fun cascade(declarations: Iterable<CssDeclaration>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val importantProps = HashSet<String>()
    for (d in declarations) {
        if (d.property in importantProps && !d.important) continue
        if (d.important) importantProps += d.property
        result.remove(d.property)
        result[d.property] = d.value
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-level parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS declaration block — a semicolon-delimited set of `property: value`
 * pairs — into a fully resolved [StyleResult].
 *
 * ```kotlin
 * val style = parseCssDeclaration("color: red; font-size: 14px; padding: 8px 16px;")
 * ```
 *
 * Custom properties declared in the block can be used with `var()` in the same block.
 * Selector blocks and curly braces are **not** expected here; see [CssStylesheet].
 */
fun parseCssDeclaration(css: String): StyleResult {
    if (css.isBlank()) return StyleResult()
    val cascaded = cascade(parseDeclarations(css))
    val variables = cascaded.filterKeys(::isCustomProperty)
    return buildStyle(computeValues(cascaded, variables)).copy(variables = variables)
}

private val sides = listOf("top", "right", "bottom", "left")
private val corners = listOf("top-left", "top-right", "bottom-right", "bottom-left")

/** The set of properties [buildStyle] understands. Anything else is ignored. */
val SUPPORTED_CSS_PROPERTIES: Set<String> = buildSet {
    // Typography
    addAll(
        listOf(
            "color", "font", "font-size", "font-weight", "font-style", "font-family", "text-align",
            "letter-spacing", "line-height", "text-decoration", "text-decoration-line",
            "text-transform", "text-shadow", "white-space", "text-overflow", "line-clamp",
            "-webkit-line-clamp",
        )
    )
    // Box model
    addAll(listOf("margin", "padding", "width", "height", "min-width", "max-width",
        "min-height", "max-height", "aspect-ratio", "box-sizing"))
    sides.forEach { add("margin-$it"); add("padding-$it") }
    // Decoration
    addAll(
        listOf(
            "background", "background-color", "background-image",
            "border", "border-width", "border-style", "border-color", "border-radius",
            "box-shadow", "opacity", "visibility", "overflow", "overflow-x", "overflow-y",
            "z-index", "transform", "filter",
            "outline", "outline-width", "outline-style", "outline-color", "outline-offset",
        )
    )
    sides.forEach { side ->
        add("border-$side"); add("border-$side-width"); add("border-$side-style"); add("border-$side-color")
    }
    corners.forEach { add("border-$it-radius") }
    // Layout (consumed by Div)
    addAll(
        listOf(
            "display", "flex-direction", "flex-wrap", "flex-flow", "justify-content", "align-items",
            "align-content", "gap", "row-gap", "column-gap", "grid-template-columns",
        )
    )
    // Flex items & positioning
    addAll(listOf("flex", "flex-grow", "flex-shrink", "flex-basis", "align-self", "order",
        "position", "top", "right", "bottom", "left", "inset"))
    // Motion
    addAll(
        listOf(
            "transition", "transition-property", "transition-duration", "transition-timing-function",
            "transition-delay", "animation", "animation-name", "animation-duration",
            "animation-timing-function", "animation-delay", "animation-iteration-count",
            "animation-direction", "animation-fill-mode", "animation-play-state",
        )
    )
}

/** Builds a [StyleResult] from already-cascaded, computed declarations. */
fun buildStyle(declarations: Map<String, String>): StyleResult {
    var box = BoxStyle()
    val margin = Array(4) { 0.dp }
    val padding = Array(4) { 0.dp }
    val borders = MutableList(4) { BorderSide() }
    val radii = MutableList(4) { CornerValue.Zero }
    var item = FlexItemStyle()
    var insets = Insets()
    var background: Brush? = null
    var backgroundColor: Color? = null

    var outline: CssOutline? = null

    var color = Color.Unspecified
    var fontSize = TextUnit.Unspecified
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var fontFamily: FontFamily? = null
    var textAlign: TextAlign? = null
    var letterSpacing = TextUnit.Unspecified
    var lineHeight = TextUnit.Unspecified
    var textDecoration: TextDecoration? = null
    var textShadow: CssShadow? = null
    var textTransform = TextTransform.None
    var maxLines: Int? = null
    var softWrap = true
    var textOverflow = TextOverflow.Clip

    for ((property, value) in declarations) {
        val v = value.trim()
        when (property) {
            // ── Typography ──────────────────────────────────────────────────
            "color" -> parseColorOrNull(v)?.let { color = it }
            "font" -> parseFontShorthand(v)?.let { font ->
                fontStyle = font.style
                fontWeight = font.weight
                fontSize = font.size
                font.lineHeight?.let { lineHeight = it }
                font.family?.let { fontFamily = it }
            }
            "font-size" -> fontSize = parseLengthAsTextUnit(v)
            "font-weight" -> fontWeight = parseFontWeight(v)
            "font-style" -> fontStyle = parseFontStyle(v)
            "font-family" -> fontFamily = parseFontFamily(v)
            "text-align" -> textAlign = parseTextAlign(v)
            "letter-spacing" -> letterSpacing = parseLengthAsTextUnit(v)
            "line-height" -> lineHeight = parseLineHeight(v)
            "text-decoration", "text-decoration-line" -> textDecoration = parseTextDecoration(v)
            "text-transform" -> textTransform = parseTextTransform(v)
            "text-shadow" -> textShadow = parseShadows(v).firstOrNull()
            "white-space" -> softWrap = v.lowercase() !in setOf("nowrap", "pre")
            "text-overflow" -> textOverflow =
                if (v.lowercase() == "ellipsis") TextOverflow.Ellipsis else TextOverflow.Clip
            "line-clamp", "-webkit-line-clamp" -> maxLines = v.toIntOrNull()?.coerceAtLeast(1)

            // ── Box model ───────────────────────────────────────────────────
            "margin" -> applyBoxShorthand(v, margin)
            "padding" -> applyBoxShorthand(v, padding)
            "width" -> box = box.copy(width = parseLength(v))
            "height" -> box = box.copy(height = parseLength(v))
            "min-width" -> box = box.copy(minWidth = parseDp(v))
            "max-width" -> box = box.copy(maxWidth = parseDp(v))
            "min-height" -> box = box.copy(minHeight = parseDp(v))
            "max-height" -> box = box.copy(maxHeight = parseDp(v))
            "aspect-ratio" -> box = box.copy(aspectRatio = parseAspectRatio(v))

            // ── Decoration ──────────────────────────────────────────────────
            "background" -> {
                val bg = parseBackground(v)
                background = bg.brush
                backgroundColor = bg.color
            }
            "background-color" -> backgroundColor = parseColorOrNull(v)
            "background-image" -> background = parseBackground(v).brush
            "border" -> parseBorderSide(v).let { side -> for (i in 0..3) borders[i] = side }
            "border-width" -> expandTokens(v, ::parseBorderWidth)?.forEachIndexed { i, w ->
                borders[i] = borders[i].copy(width = w)
            }
            "border-style" -> expandTokens(v, BorderStyle::parse)?.forEachIndexed { i, s ->
                borders[i] = borders[i].copy(style = s)
            }
            "border-color" -> expandTokens(v, ::parseColorOrNull)?.forEachIndexed { i, c ->
                borders[i] = borders[i].copy(color = c)
            }
            "border-radius" -> parseCornerRadii(v)?.forEachIndexed { i, c -> radii[i] = c }
            "box-shadow" -> box = box.copy(shadows = parseShadows(v).filterNot { it.inset })
            "opacity" -> parseFraction(v)?.let { box = box.copy(opacity = it) }
            "visibility" -> box = box.copy(hidden = v.lowercase() in setOf("hidden", "collapse"))
            "overflow" -> {
                val tokens = splitTokens(v.lowercase())
                val x = parseOverflow(tokens.getOrNull(0))
                val y = parseOverflow(tokens.getOrNull(1) ?: tokens.getOrNull(0))
                box = box.copy(overflowX = x, overflowY = y)
            }
            "overflow-x" -> box = box.copy(overflowX = parseOverflow(v.lowercase()))
            "overflow-y" -> box = box.copy(overflowY = parseOverflow(v.lowercase()))
            "z-index" -> box = box.copy(zIndex = v.toFloatOrNull())
            "transform" -> box = box.copy(transform = parseTransform(v))
            "filter" -> parseFilter(v).let { (blur, filters) -> box = box.copy(blur = blur, filters = filters) }

            // ── Flex item & positioning ─────────────────────────────────────
            "flex" -> parseFlexShorthand(v)?.let { (grow, shrink, basis) ->
                item = item.copy(grow = grow, shrink = shrink, basis = basis)
            }
            "flex-grow" -> v.toFloatOrNull()?.let { item = item.copy(grow = it.coerceAtLeast(0f)) }
            "flex-shrink" -> v.toFloatOrNull()?.let { item = item.copy(shrink = it.coerceAtLeast(0f)) }
            "flex-basis" -> item = item.copy(basis = parseLength(v)?.takeUnless { it == CssLength.Auto })
            "align-self" -> item = item.copy(alignSelf = normalizePosition(v, default = "").ifEmpty { null })
            "order" -> v.toIntOrNull()?.let { item = item.copy(order = it) }
            "position" -> item = item.copy(
                position = when (v.lowercase()) {
                    "relative", "sticky" -> CssPosition.Relative
                    "absolute", "fixed" -> CssPosition.Absolute
                    else -> CssPosition.Static
                }
            )
            "top" -> insets = insets.copy(top = parseInset(v))
            "right" -> insets = insets.copy(right = parseInset(v))
            "bottom" -> insets = insets.copy(bottom = parseInset(v))
            "left" -> insets = insets.copy(left = parseInset(v))
            "inset" -> expandBoxShorthand(splitTokens(v).map { parseInset(it) })?.let { values ->
                insets = Insets(top = values[0], right = values[1], bottom = values[2], left = values[3])
            }

            // Outline. The shorthand resets width/style/color but, as in CSS, not the offset.
            "outline" -> outline = parseOutline(v).copy(offset = outline?.offset ?: 0.dp)
            "outline-width" -> parseBorderWidth(v)?.let { outline = (outline ?: CssOutline()).copy(width = it) }
            "outline-style" -> parseOutlineStyle(v)?.let { outline = (outline ?: CssOutline()).copy(style = it) }
            "outline-color" -> parseOutlineColor(v)?.let { outline = (outline ?: CssOutline()).copy(color = it.color) }
            "outline-offset" -> parseDp(v)?.let { outline = (outline ?: CssOutline()).copy(offset = it) }

            else -> when {
                property.startsWith("margin-") -> sideIndex(property.removePrefix("margin-"))?.let { i ->
                    parseBoxLength(v)?.let { margin[i] = it }
                }
                property.startsWith("padding-") -> sideIndex(property.removePrefix("padding-"))?.let { i ->
                    parseBoxLength(v)?.let { padding[i] = it }
                }
                property.startsWith("border-") && property.endsWith("-radius") -> {
                    val corner = corners.indexOf(property.removePrefix("border-").removeSuffix("-radius"))
                    if (corner >= 0) parseCornerValue(v)?.let { radii[corner] = it }
                }
                property.startsWith("border-") -> applyBorderLonghand(property.removePrefix("border-"), v, borders)
            }
        }
    }

    box = box.copy(
        margin = Edges.of(margin.toList()),
        padding = Edges.of(padding.toList()),
        borders = borders.toList(),
        radii = radii.toList(),
        outline = outline,
        backgroundColor = backgroundColor,
        backgroundBrush = background,
    )
    val hasWidth = box.width != null && box.width != CssLength.Auto
    val hasHeight = box.height != null && box.height != CssLength.Auto
    item = item.copy(insets = insets, hasWidth = hasWidth, hasHeight = hasHeight)

    val display = declarations["display"]?.trim()?.lowercase()
    return StyleResult(
        box = box,
        item = item,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textDecoration = textDecoration,
        textShadow = textShadow,
        textTransform = textTransform,
        maxLines = maxLines,
        softWrap = softWrap,
        textOverflow = textOverflow,
        wrap = parseDisplay(declarations),
        fillsWidth = !hasWidth && item.position != CssPosition.Absolute &&
            display in setOf("block", "flex", "grid", "flow-root", "list-item"),
        transitions = parseTransitions(declarations),
        animationRefs = parseAnimationRefs(declarations),
    )
}

private fun sideIndex(side: String): Int? = sides.indexOf(side).takeIf { it >= 0 }

/** A margin/padding length; `auto` counts as 0 and negative values are clamped. */
private fun parseBoxLength(token: String): Dp? =
    if (token.equals("auto", ignoreCase = true)) 0.dp else parseDp(token)?.coerceAtLeast(0.dp)

/** Expands a padding/margin shorthand into [target] (`[top, right, bottom, left]`). */
private fun applyBoxShorthand(value: String, target: Array<Dp>) {
    val parts = splitTokens(value).map { parseBoxLength(it) ?: return }
    expandBoxShorthand(parts)?.forEachIndexed { i, dp -> target[i] = dp }
}

/** Parses each whitespace token with [parse] and expands the 1–4 value shorthand. */
private fun <T : Any> expandTokens(value: String, parse: (String) -> T?): List<T>? =
    expandBoxShorthand(splitTokens(value).map { parse(it) ?: return null })

private fun parseInset(value: String): CssLength? = parseLength(value)?.takeUnless { it == CssLength.Auto }

private fun parseOverflow(value: String?): Overflow = when (value) {
    "hidden", "clip" -> Overflow.Hidden
    "scroll", "auto" -> Overflow.Scroll
    else -> Overflow.Visible
}

private fun parseAspectRatio(value: String): Float? {
    val parts = value.split('/').map { it.trim().toFloatOrNull() ?: return null }
    val ratio = when (parts.size) {
        1 -> parts[0]
        2 -> if (parts[1] != 0f) parts[0] / parts[1] else return null
        else -> return null
    }
    return ratio.takeIf { it > 0f }
}

/** Parses `0.5` or `50%` into a 0..1 fraction. */
internal fun parseFraction(value: String): Float? {
    val (number, unit) = splitNumber(value) ?: return null
    return when (unit) {
        "" -> number
        "%" -> number / 100f
        else -> null
    }?.coerceIn(0f, 1f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Border
// ─────────────────────────────────────────────────────────────────────────────

internal fun parseBorderWidth(token: String): Dp? = when (token.trim().lowercase()) {
    "thin" -> 1.dp
    "medium" -> 3.dp
    "thick" -> 5.dp
    else -> parseDp(token)?.coerceAtLeast(0.dp)
}

/** Parses a border shorthand (`<width> <style> <color>` in any order) for one side. */
internal fun parseBorderSide(value: String): BorderSide {
    val trimmed = value.trim().lowercase()
    if (trimmed == "none" || trimmed == "0") return BorderSide(style = BorderStyle.None)
    var side = BorderSide()
    for (token in splitTokens(trimmed)) {
        val style = BorderStyle.parse(token)
        val width = if (style == null) parseBorderWidth(token) else null
        when {
            style != null -> side = side.copy(style = style)
            width != null -> side = side.copy(width = width)
            else -> parseColorOrNull(token)?.let { side = side.copy(color = it) }
        }
    }
    return side
}

// ─────────────────────────────────────────────────────────────────────────────
// Outline
// ─────────────────────────────────────────────────────────────────────────────

private val autoKeyword = Regex("""\bauto\b""", RegexOption.IGNORE_CASE)

/** Parses the `outline` shorthand (`<width> <style> <color>` in any order). `auto` draws solid. */
internal fun parseOutline(value: String): CssOutline {
    val side = parseBorderSide(value.replace(autoKeyword, "solid"))
    return CssOutline(width = side.width, style = side.style, color = side.color)
}

private fun parseOutlineStyle(value: String): BorderStyle? =
    if (value.trim().equals("auto", ignoreCase = true)) BorderStyle.Solid else BorderStyle.parse(value)

/** Wraps the result so `currentColor` (stored as `null`) can be told apart from a parse failure. */
private class ParsedColor(val color: Color?)

private fun parseOutlineColor(value: String): ParsedColor? {
    val v = value.trim().lowercase()
    if (v == "currentcolor" || v == "invert") return ParsedColor(null)
    return parseColorOrNull(v)?.let { ParsedColor(it) }
}

/** Handles `border-<side>`, `border-<side>-width|style|color`. */
private fun applyBorderLonghand(rest: String, value: String, borders: MutableList<BorderSide>) {
    val side = sides.indexOf(rest.substringBefore('-'))
    if (side < 0) return
    when (rest.substringAfter('-', missingDelimiterValue = "")) {
        "" -> borders[side] = parseBorderSide(value)
        "width" -> parseBorderWidth(value)?.let { borders[side] = borders[side].copy(width = it) }
        "style" -> BorderStyle.parse(value)?.let { borders[side] = borders[side].copy(style = it) }
        "color" -> parseColorOrNull(value)?.let { borders[side] = borders[side].copy(color = it) }
    }
}

/** Legacy helper: `"2px solid red"` → `2f to Color.Red`. */
fun parseBorderOrNull(value: String): Pair<Float, Color>? {
    val side = parseBorderSide(value)
    val width = side.resolvedWidth.takeIf { it > 0.dp } ?: return null
    return width.value to (side.color ?: Color.Black)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shadows, transforms, filters, flex
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a comma-separated `box-shadow` / `text-shadow` list:
 * `[inset] <x> <y> [blur] [spread] <color>`.
 */
internal fun parseShadows(value: String): List<CssShadow> {
    if (value.trim().equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').mapNotNull { part ->
        val lengths = mutableListOf<Dp>()
        var color = Color.Black.copy(alpha = 0.3f)
        var inset = false
        for (token in splitTokens(part.trim().lowercase())) {
            if (token == "inset") { inset = true; continue }
            val dp = parseDp(token)
            if (dp != null) lengths += dp else parseColorOrNull(token)?.let { color = it }
        }
        if (lengths.size < 2) return@mapNotNull null
        CssShadow(
            offsetX = lengths[0],
            offsetY = lengths[1],
            blur = lengths.getOrElse(2) { 0.dp }.coerceAtLeast(0.dp),
            spread = lengths.getOrElse(3) { 0.dp },
            color = color,
            inset = inset,
        )
    }
}

/** Backwards-compatible single-shadow parser. */
internal fun parseShadow(value: String): CssShadow? = parseShadows(value).firstOrNull()

/** Splits on [separator] while ignoring separators inside parentheses. */
internal fun splitTopLevel(value: String, separator: Char): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    value.forEachIndexed { i, ch ->
        when (ch) {
            '(' -> depth++
            ')' -> depth--
            separator -> if (depth == 0) { parts += value.substring(start, i); start = i + 1 }
        }
    }
    parts += value.substring(start)
    return parts
}

private val functionRegex = Regex("""([a-zA-Z-]+)\s*\(([^)]*)\)""")

/**
 * Parses `transform` into a [TransformSpec]: `translate(x[, y])`, `translateX/Y()`,
 * `rotate()`, `scale(x[, y])`, `scaleX/Y()`. Translations add up, rotations add up,
 * scales multiply; the result is applied as translate → rotate → scale.
 */
internal fun parseTransform(value: String): TransformSpec {
    if (value.trim().equals("none", ignoreCase = true)) return TransformSpec()
    var spec = TransformSpec()
    for (match in functionRegex.findAll(value)) {
        val name = match.groupValues[1].lowercase()
        val args = match.groupValues[2].split(',', ' ').filter { it.isNotBlank() }
        fun dp(i: Int) = args.getOrNull(i)?.let(::parseDp) ?: 0.dp
        fun num(i: Int) = args.getOrNull(i)?.toFloatOrNull()
        when (name) {
            "translate" -> spec = spec.copy(translateX = spec.translateX + dp(0), translateY = spec.translateY + dp(1))
            "translatex" -> spec = spec.copy(translateX = spec.translateX + dp(0))
            "translatey" -> spec = spec.copy(translateY = spec.translateY + dp(0))
            "rotate", "rotatez" -> args.firstOrNull()?.let(::parseAngle)?.let { spec = spec.copy(rotate = spec.rotate + it) }
            "scale" -> {
                val x = args.getOrNull(0)?.toFloatOrNull() ?: continue
                val y = args.getOrNull(1)?.toFloatOrNull() ?: x
                spec = spec.copy(scaleX = spec.scaleX * x, scaleY = spec.scaleY * y)
            }
            "scalex" -> num(0)?.let { spec = spec.copy(scaleX = spec.scaleX * it) }
            "scaley" -> num(0)?.let { spec = spec.copy(scaleY = spec.scaleY * it) }
        }
    }
    return spec
}

/** Parses `filter` into a blur radius plus color-matrix functions. */
internal fun parseFilter(value: String): Pair<Dp, List<FilterFunction>> {
    var blur = 0.dp
    val functions = mutableListOf<FilterFunction>()
    if (value.trim().equals("none", ignoreCase = true)) return blur to functions
    for (match in functionRegex.findAll(value)) {
        val name = match.groupValues[1].lowercase()
        val arg = match.groupValues[2].trim()
        when (name) {
            "blur" -> blur = (parseDp(arg) ?: 0.dp).coerceAtLeast(0.dp)
            "hue-rotate" -> parseAngle(arg)?.let { functions += FilterFunction(name, it) }
            "grayscale", "sepia", "invert", "opacity", "saturate", "brightness", "contrast" -> {
                val amount = splitNumber(arg)?.let { (n, unit) -> if (unit == "%") n / 100f else n } ?: 1f
                functions += FilterFunction(name, amount.coerceAtLeast(0f))
            }
        }
    }
    return blur to functions
}

/** Parses the `flex` shorthand into `(grow, shrink, basis)`. */
internal fun parseFlexShorthand(value: String): Triple<Float, Float, CssLength?>? {
    when (value.trim().lowercase()) {
        "none" -> return Triple(0f, 0f, null)
        "auto" -> return Triple(1f, 1f, null)
        "initial" -> return Triple(0f, 1f, null)
    }
    val tokens = splitTokens(value)
    val numbers = mutableListOf<Float>()
    var basis: CssLength? = CssLength.Fixed(0.dp)
    for (token in tokens) {
        val number = token.toFloatOrNull()
        when {
            number != null && numbers.size < 2 -> numbers += number
            token.equals("auto", true) || token.equals("content", true) -> basis = null
            else -> basis = parseLength(token) ?: return null
        }
    }
    if (numbers.isEmpty() && tokens.size == 1) return Triple(1f, 1f, basis)
    return Triple(numbers.getOrElse(0) { 1f }.coerceAtLeast(0f), numbers.getOrElse(1) { 1f }.coerceAtLeast(0f), basis)
}

/** Parses a CSS angle (`45deg`, `0.25turn`, `1.57rad`, bare degrees) into degrees. */
internal fun parseAngle(value: String): Float? {
    val (number, unit) = splitNumber(value) ?: return null
    return when (unit) {
        "", "deg" -> number
        "turn" -> number * 360f
        "rad" -> Math.toDegrees(number.toDouble()).toFloat()
        "grad" -> number * 0.9f
        else -> null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typography value parsers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a CSS `font-weight` value.
 * Accepts numeric weights (`1`–`1000`) and keywords (`normal`, `bold`, `bolder`, `lighter`).
 */
fun parseFontWeight(fWeight: String): FontWeight {
    val trimmed = fWeight.trim().lowercase()
    trimmed.toIntOrNull()?.let { return FontWeight(it.coerceIn(1, 1000)) }
    return when (trimmed) {
        "bold" -> FontWeight.Bold
        "bolder" -> FontWeight.ExtraBold
        "lighter" -> FontWeight.Light
        else -> FontWeight.Normal
    }
}

/** Parses a CSS `font-style` value (`normal`, `italic`, `oblique`). */
fun parseFontStyle(fStyle: String): FontStyle = when (fStyle.trim().lowercase().substringBefore(' ')) {
    "italic", "oblique" -> FontStyle.Italic
    else -> FontStyle.Normal
}

/**
 * Parses a CSS `font-family` list, returning the first family that resolves:
 * fonts registered with [CssFonts], then the generic families
 * `serif`, `sans-serif`, `monospace`, `cursive` and `system-ui`.
 */
fun parseFontFamily(value: String): FontFamily? =
    splitTopLevel(value, ',').firstNotNullOfOrNull { raw ->
        val name = raw.trim().trim('"', '\'')
        CssFonts[name] ?: when (name.lowercase()) {
            "serif" -> FontFamily.Serif
            "sans-serif" -> FontFamily.SansSerif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            "system-ui", "default" -> FontFamily.Default
            else -> null
        }
    }

internal data class FontShorthand(
    val style: FontStyle,
    val weight: FontWeight,
    val size: TextUnit,
    val lineHeight: TextUnit?,
    val family: FontFamily?,
)

/** Parses `font: [style] [variant] [weight] <size>[/<line-height>] <family>`. */
internal fun parseFontShorthand(value: String): FontShorthand? {
    val tokens = value.trim().split(Regex("\\s+"))
    var style = FontStyle.Normal
    var weight = FontWeight.Normal
    for ((index, token) in tokens.withIndex()) {
        val lower = token.lowercase()
        val sizePart = lower.substringBefore('/')
        val size = parseLengthAsTextUnit(sizePart)
        if (size != TextUnit.Unspecified && sizePart.toIntOrNull()?.let { it % 100 == 0 && it in 100..900 } != true) {
            val lineHeight = lower.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let(::parseLineHeight)
            val family = tokens.drop(index + 1).joinToString(" ").takeIf { it.isNotBlank() }?.let(::parseFontFamily)
            return FontShorthand(style, weight, size, lineHeight, family)
        }
        when {
            lower == "italic" || lower == "oblique" -> style = FontStyle.Italic
            lower == "bold" || lower == "bolder" || lower == "lighter" || lower.toIntOrNull() != null ->
                weight = parseFontWeight(lower)
        }
    }
    return null
}

/** Parses a CSS `text-align` value (`left`, `right`, `center`, `justify`, `start`, `end`). */
fun parseTextAlign(tAlign: String): TextAlign = when (tAlign.trim().lowercase()) {
    "left" -> TextAlign.Left
    "center" -> TextAlign.Center
    "right" -> TextAlign.Right
    "justify" -> TextAlign.Justify
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

/** Parses `text-decoration`; `underline` and `line-through` may be combined. */
fun parseTextDecoration(tDecor: String): TextDecoration {
    val tokens = splitTokens(tDecor.lowercase())
    val decorations = buildList {
        if ("underline" in tokens) add(TextDecoration.Underline)
        if ("line-through" in tokens) add(TextDecoration.LineThrough)
    }
    return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
}

/** Parses `text-transform` (`uppercase`, `lowercase`, `capitalize`, `none`). */
fun parseTextTransform(value: String): TextTransform = when (value.trim().lowercase()) {
    "uppercase" -> TextTransform.Uppercase
    "lowercase" -> TextTransform.Lowercase
    "capitalize" -> TextTransform.Capitalize
    else -> TextTransform.None
}
