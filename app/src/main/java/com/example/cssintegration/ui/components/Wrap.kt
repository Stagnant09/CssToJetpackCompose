package com.example.cssintegration.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment

// ─────────────────────────────────────────────────────────────────────────────
// Wrap  —  sealed hierarchy representing all CSS display/layout modes
// ─────────────────────────────────────────────────────────────────────────────

sealed class Wrap

/** display: flex; flex-direction: column / column-reverse */
data class ColumnWrap(
    val inverse: Boolean = false,
    val verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    val horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) : Wrap()

/** display: flex; flex-direction: row / row-reverse (default flex behaviour) */
data class RowWrap(
    val inverse: Boolean = false,
    val horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    val verticalAlignment: Alignment.Vertical = Alignment.Top,
) : Wrap()

/** display: block — fills max width, children stacked vertically */
object BlockWrap : Wrap()

/** display: box / position overlay container */
data class BoxWrap(
    val contentAlignment: Alignment = Alignment.TopStart,
) : Wrap()

/** display: grid */
data class GridWrap(
    val columns: Int = 2,
    val horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    val verticalArrangement: Arrangement.Vertical = Arrangement.Top,
) : Wrap()

/** display: inline / inline-block — children wrap horizontally via FlowRow */
object InlineWrap : Wrap()

/** display: none — composable is removed from the tree entirely */
object NoneWrap : Wrap()