package io.github.stagnant09.csscompose

import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A layout composable that mirrors the HTML `<div>` element.
 *
 * Its style is resolved from the current stylesheet (rules matching `div`, `.className`,
 * `#id`, ancestors, interaction state and `@media`) followed by the inline [css]. That
 * style drives decoration, layout (`display: flex | grid | block | inline | box | none`),
 * `transition` / `animation`, scrolling (`overflow: auto`) and positioning.
 *
 * Inheritable text properties (`color`, `font-*`, `text-align`, …) and custom properties
 * (`--x`) are passed down to descendants, like CSS inheritance.
 *
 * | CSS                                  | Compose equivalent                    |
 * |--------------------------------------|---------------------------------------|
 * | flex (+ direction, wrap, gap, …)     | CSS flexbox layout                    |
 * | grid                                 | equal-width column grid               |
 * | block / unset                        | block flow (children stack)           |
 * | inline / inline-block                | wrapping row                          |
 * | box                                  | Box                                   |
 * | none                                 | not rendered                          |
 *
 * Children with `position: absolute` are taken out of the flow and placed using
 * `top` / `right` / `bottom` / `left` relative to this `Div`.
 *
 * @param className space-separated class names matched against `.class` rules.
 * @param id matched against `#id` rules.
 * @param enabled when `false`, `:disabled` rules match and clicks are ignored.
 * @param index this element's position among its siblings, for `:nth-child()` & co.
 * @param onClick makes the element clickable (and `:active` / `:focus` aware).
 *
 * This Android-only overload also takes a [modifier]. Without one, calls go to the
 * cross-platform `Div` (declared in commonMain), which lands here with `Modifier`.
 */
@Composable
fun Div(
    modifier: Modifier,
    css: String = "",
    id: String = "",
    className: String = "",
    enabled: Boolean = true,
    index: CssIndex? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val element = rememberCssElement("div", className, id, css, enabled, index, onClick)
    val style = element.style
    if (style.isDisplayNone) return
    // Block-level boxes stretch like in CSS; margins (inside the element modifier) still apply.
    val outer = if (style.fillsWidth) modifier.fillMaxWidth() else modifier
    CompositionLocalProvider(
        LocalCssAncestors provides element.childAncestors,
        LocalCssVariables provides style.variables,
    ) {
        DivLayout(style, outer.then(element.modifier)) {
            ProvideInheritedTextStyle(style, content)
        }
    }
}

@Composable
actual fun Div(
    css: String,
    id: String,
    className: String,
    enabled: Boolean,
    index: CssIndex?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) = Div(
    modifier = Modifier,
    css = css,
    id = id,
    className = className,
    enabled = enabled,
    index = index,
    onClick = onClick,
    content = content,
)

/** Pushes the inheritable text properties of [style] down to descendants. */
@Composable
internal fun ProvideInheritedTextStyle(style: StyleResult, content: @Composable () -> Unit) {
    if (!style.hasTextStyle) {
        content()
        return
    }
    val density = LocalDensity.current
    ProvideTextStyle(LocalTextStyle.current.merge(style.toTextStyle(density)), content)
}

@Composable
private fun DivLayout(style: StyleResult, modifier: Modifier, content: @Composable () -> Unit) {
    when (val wrap = style.wrap) {
        is FlexWrap -> CssFlexLayout(wrap, modifier, content)
        is GridWrap -> CssGrid(wrap.columns, wrap.columnGap, wrap.rowGap, modifier, content)
        is BoxWrap -> Box(modifier = modifier, contentAlignment = wrap.contentAlignment) { content() }
        is NoneWrap -> Unit
        null -> CssFlexLayout(FlexWrap.Block, modifier, content)
    }
}

/**
 * A non-lazy grid: every child becomes one cell, laid out left-to-right in [columns]
 * equal-width tracks (`1fr` each). Row height is the tallest cell in that row.
 *
 * Unlike `LazyVerticalGrid`, this can be nested inside scrolling containers and
 * gives each child its own cell.
 */
@Composable
internal fun CssGrid(
    columns: Int,
    columnGap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val cols = columns.coerceAtLeast(1)
        val gapX = columnGap.roundToPx()
        val gapY = rowGap.roundToPx()

        val cellWidth: Int? = if (constraints.hasBoundedWidth) {
            ((constraints.maxWidth - gapX * (cols - 1)) / cols).coerceAtLeast(0)
        } else null

        val placeables = measurables.map { measurable ->
            measurable.measure(
                if (cellWidth != null) Constraints(minWidth = cellWidth, maxWidth = cellWidth)
                else Constraints()
            )
        }
        val trackWidth = cellWidth ?: (placeables.maxOfOrNull { it.width } ?: 0)
        val rows = placeables.chunked(cols)
        val rowHeights = rows.map { row -> row.maxOf { it.height } }

        val contentWidth = trackWidth * cols + gapX * (cols - 1)
        val contentHeight = rowHeights.sum() + gapY * (rows.size - 1).coerceAtLeast(0)
        val width = contentWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { col, placeable ->
                    placeable.placeRelative(x = col * (trackWidth + gapX), y = y)
                }
                y += rowHeights[rowIndex] + gapY
            }
        }
    }
}
