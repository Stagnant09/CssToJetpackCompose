package com.example.cssintegration.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cssintegration.cssUtils.parseCssDeclarationForDiv

/**
 * A layout composable that mirrors the HTML `<div>` element.
 *
 * The [css] string drives both styling (padding, background, border, …) and
 * layout structure via `display`, `flex-direction`, `justify-content`,
 * `align-items`, and `grid-template-columns`.
 *
 * Supported display modes:
 * | CSS value          | Compose equivalent         |
 * |--------------------|----------------------------|
 * | flex / row         | Row                        |
 * | flex / column      | Column                     |
 * | flex / row-reverse | Row (reversed)             |
 * | flex / col-reverse | Column (reversed)          |
 * | grid               | LazyVerticalGrid           |
 * | block              | Column (fillMaxWidth)      |
 * | inline / inline-*  | FlowRow                    |
 * | box                | Box                        |
 * | none               | not rendered               |
 * | (unset)            | Box (passthrough default)  |
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Div(
    modifier: Modifier = Modifier,
    css: String,
    id: String = "",
    className: String = "",
    content: @Composable () -> Unit,
) {
    val style = parseCssDeclarationForDiv(css)
    val combined = modifier.then(style.modifier)

    when (val wrap = style.wrap) {

        is RowWrap -> Row(
            horizontalArrangement = if (wrap.inverse) {
                reverseHorizontal(wrap.horizontalArrangement)
            } else {
                wrap.horizontalArrangement
            },
            verticalAlignment = wrap.verticalAlignment,
            modifier = combined,
        ) { content() }

        is ColumnWrap -> Column(
            verticalArrangement = if (wrap.inverse) {
                reverseVertical(wrap.verticalArrangement)
            } else {
                wrap.verticalArrangement
            },
            horizontalAlignment = wrap.horizontalAlignment,
            modifier = combined,
        ) { content() }

        is GridWrap -> LazyVerticalGrid(
            columns = GridCells.Fixed(wrap.columns),
            horizontalArrangement = wrap.horizontalArrangement,
            verticalArrangement = wrap.verticalArrangement,
            modifier = combined,
        ) { item { content() } }

        is BlockWrap -> Column(
            modifier = combined.fillMaxWidth(),
        ) { content() }

        is BoxWrap -> Box(
            contentAlignment = wrap.contentAlignment,
            modifier = combined,
        ) { content() }

        is InlineWrap -> FlowRow(
            modifier = combined,
        ) { content() }

        is NoneWrap -> { /* display: none — render nothing */ }

        null -> Box(modifier = combined) { content() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers for reversing arrangements (flex-direction: *-reverse)
// ─────────────────────────────────────────────────────────────────────────────

private fun reverseHorizontal(base: androidx.compose.foundation.layout.Arrangement.Horizontal) =
    when (base) {
        androidx.compose.foundation.layout.Arrangement.Start -> androidx.compose.foundation.layout.Arrangement.End
        androidx.compose.foundation.layout.Arrangement.End   -> androidx.compose.foundation.layout.Arrangement.Start
        else -> base
    }

private fun reverseVertical(base: androidx.compose.foundation.layout.Arrangement.Vertical) =
    when (base) {
        androidx.compose.foundation.layout.Arrangement.Top    -> androidx.compose.foundation.layout.Arrangement.Bottom
        androidx.compose.foundation.layout.Arrangement.Bottom -> androidx.compose.foundation.layout.Arrangement.Top
        else -> base
    }