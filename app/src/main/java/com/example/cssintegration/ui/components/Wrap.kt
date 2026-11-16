package com.example.cssintegration.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Wrap  —  how a Div lays out its children, resolved from `display` & friends.
// ─────────────────────────────────────────────────────────────────────────────

sealed class Wrap

enum class FlexDirection {
    Row, RowReverse, Column, ColumnReverse;

    val isRow: Boolean get() = this == Row || this == RowReverse
    val isReverse: Boolean get() = this == RowReverse || this == ColumnReverse
}

/**
 * A flex container. Also used for `display: block` / `inline` / unset, which are
 * emulated as a column (block flow) or a wrapping row (inline flow).
 *
 * Alignment keywords are normalised to `start`, `center`, `end`, `stretch`
 * and `space-between` / `space-around` / `space-evenly`.
 */
@Immutable
data class FlexWrap(
    val direction: FlexDirection = FlexDirection.Row,
    val wrap: Boolean = false,
    val justifyContent: String = "start",
    val alignItems: String = "stretch",
    val alignContent: String = "start",
    val rowGap: Dp = 0.dp,
    val columnGap: Dp = 0.dp,
) : Wrap() {
    /** Gap along the main axis. */
    val mainGap: Dp get() = if (direction.isRow) columnGap else rowGap

    /** Gap between wrapped lines. */
    val crossGap: Dp get() = if (direction.isRow) rowGap else columnGap

    companion object {
        /** Block flow: children stack; block-level children fill the width themselves. */
        val Block = FlexWrap(direction = FlexDirection.Column, alignItems = "start")
    }
}

/** display: grid — fixed number of equal-width columns */
@Immutable
data class GridWrap(
    val columns: Int = 2,
    val columnGap: Dp = 0.dp,
    val rowGap: Dp = 0.dp,
) : Wrap()

/** display: box — overlay container (non-standard, maps to Compose `Box`) */
@Immutable
data class BoxWrap(
    val contentAlignment: Alignment = Alignment.TopStart,
) : Wrap()

/** display: none — composable is removed from the tree entirely */
data object NoneWrap : Wrap()
