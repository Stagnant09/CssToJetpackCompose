package com.example.cssintegration.cssUtils

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Resolved, density-independent style data.
//
// Everything here is plain data so it can be compared, cached and — crucially —
// interpolated for `transition` / `@keyframes`. A Modifier is only built from it
// at the very end (see CssModifier.kt).
// ─────────────────────────────────────────────────────────────────────────────

/** Four box edges in CSS order. */
@Immutable
data class Edges(
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val left: Dp = 0.dp,
) {
    val isZero: Boolean get() = top == 0.dp && right == 0.dp && bottom == 0.dp && left == 0.dp

    operator fun get(side: Int): Dp = when (side) {
        0 -> top
        1 -> right
        2 -> bottom
        else -> left
    }

    fun with(side: Int, value: Dp): Edges = when (side) {
        0 -> copy(top = value)
        1 -> copy(right = value)
        2 -> copy(bottom = value)
        else -> copy(left = value)
    }

    companion object {
        val Zero = Edges()
        fun of(values: List<Dp>) = Edges(values[0], values[1], values[2], values[3])
    }
}

/** CSS `border-style`. Everything except dashed/dotted is rendered solid. */
enum class BorderStyle {
    None, Hidden, Solid, Dashed, Dotted, Double, Groove, Ridge, Inset, Outset;

    val isVisible: Boolean get() = this != None && this != Hidden

    companion object {
        fun parse(value: String): BorderStyle? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * One side of a border. A side with a width or color but no style is drawn solid
 * (browsers would draw nothing — we're lenient on purpose).
 */
@Immutable
data class BorderSide(
    val width: Dp? = null,
    val style: BorderStyle? = null,
    /** `null` means `currentColor` (the text color, or black). */
    val color: Color? = null,
) {
    val resolvedStyle: BorderStyle
        get() = style ?: if (width != null || color != null) BorderStyle.Solid else BorderStyle.None

    /** Width actually drawn (0 when the side is not visible). */
    val resolvedWidth: Dp
        get() = if (!resolvedStyle.isVisible) 0.dp else (width ?: 3.dp).coerceAtLeast(0.dp)
}

/** A `border-radius` corner: absolute dp, or a percentage of the box size. */
@Immutable
data class CornerValue(val value: Float, val isPercent: Boolean = false) {
    fun toCornerSize(): CornerSize =
        if (isPercent) CornerSize(value.roundToInt().coerceIn(0, 50)) else CornerSize(value.coerceAtLeast(0f).dp)

    companion object {
        val Zero = CornerValue(0f)
    }
}

/**
 * CSS `transform`, normalised to translate → rotate → scale so it can be
 * interpolated. (Arbitrary function order and skew/matrix are not supported.)
 */
@Immutable
data class TransformSpec(
    val translateX: Dp = 0.dp,
    val translateY: Dp = 0.dp,
    val rotate: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
) {
    val isIdentity: Boolean
        get() = translateX == 0.dp && translateY == 0.dp && rotate == 0f && scaleX == 1f && scaleY == 1f
}

enum class Overflow { Visible, Hidden, Scroll }

enum class CssPosition { Static, Relative, Absolute }

/** A parsed `box-shadow` / `text-shadow`, in density-independent units. */
@Immutable
data class CssShadow(
    val offsetX: Dp,
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
    val spread: Dp = 0.dp,
    val inset: Boolean = false,
)

/** One `filter` function other than `blur()`, e.g. `grayscale(0.5)` or `hue-rotate(90)`. */
@Immutable
data class FilterFunction(val name: String, val amount: Float)

/** Box (non-text) styling of an element. */
@Immutable
data class BoxStyle(
    val margin: Edges = Edges.Zero,
    val padding: Edges = Edges.Zero,
    val width: CssLength? = null,
    val height: CssLength? = null,
    val minWidth: Dp? = null,
    val maxWidth: Dp? = null,
    val minHeight: Dp? = null,
    val maxHeight: Dp? = null,
    val aspectRatio: Float? = null,
    val backgroundColor: Color? = null,
    val backgroundBrush: Brush? = null,
    /** top, right, bottom, left */
    val borders: List<BorderSide> = DefaultBorders,
    /** top-left, top-right, bottom-right, bottom-left */
    val radii: List<CornerValue> = DefaultRadii,
    val shadows: List<CssShadow> = emptyList(),
    val opacity: Float = 1f,
    val hidden: Boolean = false,
    val transform: TransformSpec = TransformSpec(),
    val overflowX: Overflow = Overflow.Visible,
    val overflowY: Overflow = Overflow.Visible,
    val zIndex: Float? = null,
    val blur: Dp = 0.dp,
    val filters: List<FilterFunction> = emptyList(),
) {
    val hasRadius: Boolean get() = radii.any { it.value != 0f }

    val shape: Shape
        get() = if (!hasRadius) RectangleShape else AbsoluteRoundedCornerShape(
            topLeft = radii[0].toCornerSize(),
            topRight = radii[1].toCornerSize(),
            bottomRight = radii[2].toCornerSize(),
            bottomLeft = radii[3].toCornerSize(),
        )

    /** Border widths as edges (used to inset the content, like CSS). */
    val borderWidths: Edges
        get() = Edges(
            borders[0].resolvedWidth, borders[1].resolvedWidth,
            borders[2].resolvedWidth, borders[3].resolvedWidth,
        )

    companion object {
        val DefaultBorders = List(4) { BorderSide() }
        val DefaultRadii = List(4) { CornerValue.Zero }
    }
}

/** `top` / `right` / `bottom` / `left` offsets for positioned elements. */
@Immutable
data class Insets(
    val top: CssLength? = null,
    val right: CssLength? = null,
    val bottom: CssLength? = null,
    val left: CssLength? = null,
)

/**
 * How an element participates in its parent's layout: flex item properties and
 * positioning. Passed to the parent [com.example.cssintegration.ui.components.Div]
 * as parent data.
 */
@Immutable
data class FlexItemStyle(
    val grow: Float = 0f,
    val shrink: Float = 1f,
    /** `null` = `auto` (content size). */
    val basis: CssLength? = null,
    /** Normalised `align-self` (`start`, `center`, `end`, `stretch`) or `null` for `auto`. */
    val alignSelf: String? = null,
    val order: Int = 0,
    val position: CssPosition = CssPosition.Static,
    val insets: Insets = Insets(),
    /** Whether the element declares its own width / height (so it isn't stretched). */
    val hasWidth: Boolean = false,
    val hasHeight: Boolean = false,
) {
    /** `true` when the parent needs to know nothing beyond the defaults. */
    val isDefault: Boolean get() = this == Default

    companion object {
        val Default = FlexItemStyle()
    }
}
