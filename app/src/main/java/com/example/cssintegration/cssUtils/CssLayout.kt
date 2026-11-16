package com.example.cssintegration.cssUtils

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cssintegration.ui.components.BoxWrap
import com.example.cssintegration.ui.components.FlexDirection
import com.example.cssintegration.ui.components.FlexWrap
import com.example.cssintegration.ui.components.GridWrap
import com.example.cssintegration.ui.components.NoneWrap
import com.example.cssintegration.ui.components.Wrap

// ─────────────────────────────────────────────────────────────────────────────
// display / flex / grid  →  Wrap
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolves the layout-related declarations of a block into a [Wrap].
 *
 * CSS properties consumed:
 * - `display`: flex | inline-flex | grid | inline-grid | block | inline | inline-block | box | none
 * - `flex-direction`, `flex-wrap`, `flex-flow`
 * - `justify-content`, `align-items`, `align-content`
 * - `gap`, `row-gap`, `column-gap`
 * - `grid-template-columns`: `repeat(N, …)`, a track list (`1fr 2fr 1fr`) or a bare count
 *
 * Returns `null` when no `display` is declared (a `Div` then uses block flow).
 */
fun parseDisplay(declarations: Map<String, String>): Wrap? {
    val display = declarations["display"]?.trim()?.lowercase() ?: return null

    var direction = declarations["flex-direction"]?.trim()?.lowercase() ?: "row"
    var wraps = declarations["flex-wrap"]?.trim()?.lowercase()?.startsWith("wrap") == true
    declarations["flex-flow"]?.let { flow ->
        splitTokens(flow.lowercase()).forEach { token ->
            when {
                token.startsWith("row") || token.startsWith("column") -> direction = token
                token.startsWith("wrap") -> wraps = true
                token == "nowrap" -> wraps = false
            }
        }
    }
    val (rowGap, columnGap) = parseGaps(declarations)
    val justify = normalizePosition(declarations["justify-content"].orEmpty(), default = "start")
    val alignItems = normalizePosition(declarations["align-items"].orEmpty(), default = "stretch")
    val alignContent = normalizePosition(declarations["align-content"].orEmpty(), default = "start")

    return when (display) {
        "flex", "inline-flex" -> FlexWrap(
            direction = when (direction) {
                "row-reverse" -> FlexDirection.RowReverse
                "column" -> FlexDirection.Column
                "column-reverse" -> FlexDirection.ColumnReverse
                else -> FlexDirection.Row
            },
            wrap = wraps,
            justifyContent = justify,
            alignItems = alignItems,
            alignContent = if (alignContent == "stretch") "start" else alignContent,
            rowGap = rowGap,
            columnGap = columnGap,
        )

        "grid", "inline-grid" -> GridWrap(
            columns = parseGridColumns(declarations["grid-template-columns"].orEmpty()),
            columnGap = columnGap,
            rowGap = rowGap,
        )

        "block", "flow-root", "list-item" -> FlexWrap.Block.copy(rowGap = rowGap)

        "inline", "inline-block" -> FlexWrap(
            direction = FlexDirection.Row,
            wrap = true,
            justifyContent = justify,
            alignItems = "start",
            rowGap = rowGap,
            columnGap = columnGap,
        )

        "box" -> BoxWrap(
            contentAlignment = declarations["align-content"]?.let(::parseBoxAlignment)
                ?: boxAlignment(horizontal = justify, vertical = alignItems),
        )

        "none" -> NoneWrap

        else -> null
    }
}

/** Returns `(rowGap, columnGap)` from `gap` / `row-gap` / `column-gap`. */
private fun parseGaps(declarations: Map<String, String>): Pair<Dp, Dp> {
    var rowGap = 0.dp
    var columnGap = 0.dp
    declarations["gap"]?.let { gap ->
        val parts = splitTokens(gap).mapNotNull(::parseDp)
        if (parts.isNotEmpty()) {
            rowGap = parts[0]
            columnGap = parts.getOrElse(1) { parts[0] }
        }
    }
    declarations["row-gap"]?.let(::parseDp)?.let { rowGap = it }
    declarations["column-gap"]?.let(::parseDp)?.let { columnGap = it }
    return rowGap to columnGap
}

/**
 * Collapses CSS positional keywords into `start` / `center` / `end` / `stretch` /
 * `space-*`. Unknown or empty values return [default].
 */
internal fun normalizePosition(value: String, default: String = "start"): String =
    when (val v = value.trim().lowercase()) {
        "flex-start", "start", "left", "top", "self-start", "baseline", "first baseline" -> "start"
        "flex-end", "end", "right", "bottom", "self-end", "last baseline" -> "end"
        "center" -> "center"
        "stretch" -> "stretch"
        "space-between", "space-around", "space-evenly" -> v
        else -> default // "", normal, auto
    }

/** Combines a horizontal and vertical position into a 2D [Alignment] for `display: box`. */
private fun boxAlignment(horizontal: String, vertical: String): Alignment {
    val h = horizontal.takeIf { it == "center" || it == "end" } ?: "start"
    val v = vertical.takeIf { it == "center" || it == "end" } ?: "start"
    return when (v to h) {
        "start" to "center" -> Alignment.TopCenter
        "start" to "end" -> Alignment.TopEnd
        "center" to "start" -> Alignment.CenterStart
        "center" to "center" -> Alignment.Center
        "center" to "end" -> Alignment.CenterEnd
        "end" to "start" -> Alignment.BottomStart
        "end" to "center" -> Alignment.BottomCenter
        "end" to "end" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }
}

/** Maps the non-standard `align-content` keywords used by `display: box`. */
private fun parseBoxAlignment(value: String): Alignment =
    when (value.trim().lowercase()) {
        "center" -> Alignment.Center
        "flex-end", "end" -> Alignment.BottomEnd
        "top-center" -> Alignment.TopCenter
        "bottom-center" -> Alignment.BottomCenter
        "center-start" -> Alignment.CenterStart
        "center-end" -> Alignment.CenterEnd
        else -> Alignment.TopStart
    }

/**
 * Parses `grid-template-columns` into a column count:
 * `repeat(3, 1fr)` → 3, `1fr 2fr 1fr` → 3, `"2"` → 2. Defaults to 2.
 */
internal fun parseGridColumns(value: String): Int {
    if (value.isBlank()) return 2
    Regex("""repeat\s*\(\s*(\d+)""").find(value)?.let { return it.groupValues[1].toInt().coerceAtLeast(1) }
    val tokens = splitTokens(value)
    if (tokens.size == 1) tokens[0].toIntOrNull()?.let { return it.coerceAtLeast(1) }
    return tokens.size.coerceAtLeast(1)
}
