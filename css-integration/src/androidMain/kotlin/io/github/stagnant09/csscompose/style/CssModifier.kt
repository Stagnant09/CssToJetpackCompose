package io.github.stagnant09.csscompose.style

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// StyleResult → Modifier
//
// The single place a style becomes a Modifier, used for both static styles and
// animated frames so the two can never drift apart.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds the Compose [Modifier] for this style, in CSS box-model order:
 *
 * flex-item data → z-index → relative offset → margin → transform/opacity/filter
 * → [interaction] → size → outline → box-shadow → background → border → overflow clip
 * → border inset → scroll → padding → content
 *
 * @param verticalScroll / [horizontalScroll] scroll states for `overflow: auto | scroll`.
 * @param interaction click/hover/focus handling, placed on the border box.
 */
fun StyleResult.buildModifier(
    verticalScroll: ScrollState? = null,
    horizontalScroll: ScrollState? = null,
    interaction: Modifier = Modifier,
): Modifier {
    val b = box
    val shape = b.shape
    var m: Modifier = Modifier

    if (!item.isDefault) m = m.then(CssItemElement(item))
    b.zIndex?.let { m = m.zIndex(it) }
    if (item.position == CssPosition.Relative) {
        val insets = item.insets
        val x = insets.left.fixedDp() ?: insets.right.fixedDp()?.let { -it } ?: 0.dp
        val y = insets.top.fixedDp() ?: insets.bottom.fixedDp()?.let { -it } ?: 0.dp
        if (x != 0.dp || y != 0.dp) m = m.offset(x, y)
    }
    if (!b.margin.isZero) m = m.padding(b.margin)
    m = m.then(layerModifier(b)).then(interaction)

    // ── Size ──
    if (b.minWidth != null || b.maxWidth != null) {
        m = m.widthIn(min = b.minWidth ?: Dp.Unspecified, max = b.maxWidth ?: Dp.Unspecified)
    }
    if (b.minHeight != null || b.maxHeight != null) {
        m = m.heightIn(min = b.minHeight ?: Dp.Unspecified, max = b.maxHeight ?: Dp.Unspecified)
    }
    when (val w = b.width) {
        is CssLength.Fixed -> m = m.width(w.dp.coerceAtLeast(0.dp))
        is CssLength.Percent -> m = m.fillMaxWidth(w.fraction.coerceIn(0f, 1f))
        else -> Unit
    }
    when (val h = b.height) {
        is CssLength.Fixed -> m = m.height(h.dp.coerceAtLeast(0.dp))
        is CssLength.Percent -> m = m.fillMaxHeight(h.fraction.coerceIn(0f, 1f))
        else -> Unit
    }
    b.aspectRatio?.let { m = m.aspectRatio(it) }

    // ── Decoration ──
    val currentColor = if (color == Color.Unspecified) Color.Black else color
    // First in the chain so it paints last: over the shadow, background, border and content
    b.outline?.let { m = m.cssOutline(it, shape, currentColor) }
    if (b.shadows.isNotEmpty()) m = m.cssShadows(b.shadows, shape)
    b.backgroundColor?.let { m = m.background(it, shape) }
    b.backgroundBrush?.let { m = m.background(it, shape) }
    m = m.cssBorder(b.borders, shape, currentColor)
    if (b.overflowX != Overflow.Visible || b.overflowY != Overflow.Visible) m = m.clip(shape)

    // ── Content box ──
    val borderWidths = b.borderWidths
    if (!borderWidths.isZero) m = m.padding(borderWidths)
    verticalScroll?.let { m = m.verticalScroll(it) }
    horizontalScroll?.let { m = m.horizontalScroll(it) }
    if (!b.padding.isZero) m = m.padding(b.padding)
    return m
}

private fun CssLength?.fixedDp(): Dp? = (this as? CssLength.Fixed)?.dp

private fun Modifier.padding(edges: Edges): Modifier =
    absolutePadding(left = edges.left, top = edges.top, right = edges.right, bottom = edges.bottom)

/** Transform, opacity/visibility and `filter` on a single graphics layer. */
private fun layerModifier(b: BoxStyle): Modifier {
    val alpha = if (b.hidden) 0f else b.opacity
    val t = b.transform
    val hasEffect = b.blur > 0.dp || b.filters.isNotEmpty()
    if (alpha == 1f && t.isIdentity && !hasEffect) return Modifier
    val blur = b.blur
    val filters = b.filters
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationX = t.translateX.toPx()
        translationY = t.translateY.toPx()
        rotationZ = t.rotate
        scaleX = t.scaleX
        scaleY = t.scaleY
        if (hasEffect) renderEffect = buildRenderEffect(blur.toPx(), filters)
    }
}

private fun buildRenderEffect(blurPx: Float, filters: List<FilterFunction>): RenderEffect? {
    var effect: android.graphics.RenderEffect? = null
    if (filters.isNotEmpty()) {
        effect = android.graphics.RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(filterColorMatrix(filters).values)
        )
    }
    if (blurPx > 0f) {
        effect = if (effect == null) {
            android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
        } else {
            android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, effect, Shader.TileMode.DECAL)
        }
    }
    return effect?.asComposeRenderEffect()
}

/**
 * Combines CSS filter functions into one color matrix, applied left to right.
 * Matrices follow the Filter Effects spec.
 */
internal fun filterColorMatrix(filters: List<FilterFunction>): ColorMatrix {
    var combined = ColorMatrix()
    for (f in filters) {
        val a = f.amount
        val matrix = when (f.name) {
            "grayscale" -> (1f - a.coerceIn(0f, 1f)).let { s ->
                ColorMatrix(
                    floatArrayOf(
                        0.2126f + 0.7874f * s, 0.7152f - 0.7152f * s, 0.0722f - 0.0722f * s, 0f, 0f,
                        0.2126f - 0.2126f * s, 0.7152f + 0.2848f * s, 0.0722f - 0.0722f * s, 0f, 0f,
                        0.2126f - 0.2126f * s, 0.7152f - 0.7152f * s, 0.0722f + 0.9278f * s, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            "sepia" -> (1f - a.coerceIn(0f, 1f)).let { s ->
                ColorMatrix(
                    floatArrayOf(
                        0.393f + 0.607f * s, 0.769f - 0.769f * s, 0.189f - 0.189f * s, 0f, 0f,
                        0.349f - 0.349f * s, 0.686f + 0.314f * s, 0.168f - 0.168f * s, 0f, 0f,
                        0.272f - 0.272f * s, 0.534f - 0.534f * s, 0.131f + 0.869f * s, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            "hue-rotate" -> {
                val rad = Math.toRadians(a.toDouble())
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                ColorMatrix(
                    floatArrayOf(
                        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
                        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
                        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            "invert" -> a.coerceIn(0f, 1f).let { v ->
                val k = 1f - 2f * v
                ColorMatrix(
                    floatArrayOf(
                        k, 0f, 0f, 0f, 255f * v,
                        0f, k, 0f, 0f, 255f * v,
                        0f, 0f, k, 0f, 255f * v,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            "contrast" -> ColorMatrix(
                floatArrayOf(
                    a, 0f, 0f, 0f, 255f * (0.5f - 0.5f * a),
                    0f, a, 0f, 0f, 255f * (0.5f - 0.5f * a),
                    0f, 0f, a, 0f, 255f * (0.5f - 0.5f * a),
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            "saturate" -> ColorMatrix().apply { setToSaturation(a) }
            "brightness" -> ColorMatrix().apply { setToScale(a, a, a, 1f) }
            "opacity" -> ColorMatrix().apply { setToScale(1f, 1f, 1f, a.coerceIn(0f, 1f)) }
            else -> continue
        }
        // Later filters apply to the output of earlier ones: combined = matrix × combined
        val next = ColorMatrix(matrix.values.copyOf())
        next.timesAssign(combined)
        combined = next
    }
    return combined
}

// ─────────────────────────────────────────────────────────────────────────────
// box-shadow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws outer `box-shadow`s with offset, blur and spread. Like CSS, the shadow is only
 * visible outside the box (it's clipped out underneath), and the first shadow is on top.
 */
private fun Modifier.cssShadows(shadows: List<CssShadow>, shape: Shape): Modifier = drawBehind {
    val boxPath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    clipPath(boxPath, ClipOp.Difference) {
        for (shadow in shadows.asReversed()) {
            val spread = shadow.spread.toPx()
            val shadowSize = Size(
                (size.width + 2 * spread).coerceAtLeast(0f),
                (size.height + 2 * spread).coerceAtLeast(0f),
            )
            val path = Path().apply {
                addOutline(shape.createOutline(shadowSize, layoutDirection, this@drawBehind))
                translate(Offset(shadow.offsetX.toPx() - spread, shadow.offsetY.toPx() - spread))
            }
            // CSS blur radius B means a Gaussian with σ = B/2; Skia uses σ ≈ 0.57735·r + 0.5
            val blurPx = shadow.blur.toPx()
            val radius = max(0.1f, (blurPx / 2f - 0.5f) / 0.57735f)
            drawIntoCanvas { canvas ->
                val paint = Paint().apply { color = shadow.color }
                if (blurPx > 0f) paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(path, paint)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Borders
// ─────────────────────────────────────────────────────────────────────────────

private fun BorderStyle.isDashed() = this == BorderStyle.Dashed || this == BorderStyle.Dotted

private fun BorderStyle.cap() = if (this == BorderStyle.Dotted) StrokeCap.Round else StrokeCap.Butt

private fun BorderStyle.pathEffect(strokeWidth: Float): PathEffect? = when (this) {
    BorderStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(max(strokeWidth * 3f, 6f), max(strokeWidth * 2f, 4f)))
    BorderStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(0.01f, strokeWidth * 2f))
    else -> null
}

/**
 * Draws the border. Uniform solid borders use Compose's `border()`. Uniform dashed/dotted
 * borders follow the shape; per-side borders are drawn as straight edges (corner radii
 * are not applied to borders that differ per side).
 */
private fun Modifier.cssBorder(borders: List<BorderSide>, shape: Shape, currentColor: Color): Modifier {
    val widths = borders.map { it.resolvedWidth }
    if (widths.all { it <= 0.dp }) return this
    val colors = borders.map { it.color ?: currentColor }
    val styles = borders.map { it.resolvedStyle }
    val uniform = widths.distinct().size == 1 && colors.distinct().size == 1 &&
        styles.map { if (it.isDashed()) it else BorderStyle.Solid }.distinct().size == 1
    if (uniform && !styles[0].isDashed()) return border(widths[0], colors[0], shape)

    return drawWithContent {
        drawContent()
        if (uniform) {
            val stroke = widths[0].toPx()
            val outline = shape.createOutline(
                Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                layoutDirection,
                this,
            )
            val path = Path().apply { addOutline(outline); translate(Offset(stroke / 2, stroke / 2)) }
            drawPath(
                path,
                colors[0],
                style = Stroke(width = stroke, cap = styles[0].cap(), pathEffect = styles[0].pathEffect(stroke)),
            )
        } else {
            for (side in 0..3) {
                val stroke = widths[side].toPx()
                if (stroke <= 0f) continue
                val half = stroke / 2
                val (start, end) = when (side) {
                    0 -> Offset(0f, half) to Offset(size.width, half)
                    1 -> Offset(size.width - half, 0f) to Offset(size.width - half, size.height)
                    2 -> Offset(0f, size.height - half) to Offset(size.width, size.height - half)
                    else -> Offset(half, 0f) to Offset(half, size.height)
                }
                drawLine(
                    colors[side], start, end, stroke,
                    cap = styles[side].cap(),
                    pathEffect = styles[side].pathEffect(stroke),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Outline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws `outline` around the border box, pushed out by `outline-offset` (or in, when negative).
 * It's drawn after the content, so it sits on top of the background, border and children, and it
 * takes no layout space. Rounded corners grow with the offset, as in browsers.
 */
private fun Modifier.cssOutline(outline: CssOutline, shape: Shape, currentColor: Color): Modifier {
    val width = outline.resolvedWidth
    if (width <= 0.dp) return this
    val style = outline.resolvedStyle
    val color = outline.color ?: currentColor
    val offset = outline.offset
    return drawWithContent {
        drawContent()
        val stroke = width.toPx()
        // Distance from the border edge to the middle of the outline's stroke
        val grow = offset.toPx() + stroke / 2
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent).grow(grow)) }
        drawPath(path, color, style = Stroke(width = stroke, cap = style.cap(), pathEffect = style.pathEffect(stroke)))
    }
}

/** Grows (or, for negative [d], shrinks) an outline by [d] on every side. Non-zero corner radii grow with it. */
private fun Outline.grow(d: Float): Outline = when (this) {
    is Outline.Rectangle -> Outline.Rectangle(rect.growBy(d))
    is Outline.Rounded -> {
        fun CornerRadius.grow() =
            if (x <= 0f || y <= 0f) CornerRadius.Zero else CornerRadius((x + d).coerceAtLeast(0f), (y + d).coerceAtLeast(0f))
        val r = roundRect
        Outline.Rounded(
            RoundRect(
                Rect(r.left, r.top, r.right, r.bottom).growBy(d),
                r.topLeftCornerRadius.grow(), r.topRightCornerRadius.grow(),
                r.bottomRightCornerRadius.grow(), r.bottomLeftCornerRadius.grow(),
            )
        )
    }
    is Outline.Generic -> Outline.Rectangle(path.getBounds().growBy(d))
}

/** [Rect.inflate] that collapses to the centre instead of turning inside out. */
private fun Rect.growBy(d: Float): Rect {
    val halfW = (width / 2 + d).coerceAtLeast(0f)
    val halfH = (height / 2 + d).coerceAtLeast(0f)
    return Rect(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH)
}

// ─────────────────────────────────────────────────────────────────────────────
// Flex-item parent data
// ─────────────────────────────────────────────────────────────────────────────

/** Attaches [FlexItemStyle] as parent data so a CSS flex container can read it. */
internal data class CssItemElement(val item: FlexItemStyle) : ModifierNodeElement<CssItemNode>() {
    override fun create() = CssItemNode(item)
    override fun update(node: CssItemNode) {
        node.item = item
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "cssItem"
        properties["item"] = item
    }
}

internal class CssItemNode(var item: FlexItemStyle) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = item
}
