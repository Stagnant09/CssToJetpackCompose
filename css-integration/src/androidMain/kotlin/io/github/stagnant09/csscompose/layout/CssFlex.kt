package io.github.stagnant09.csscompose.layout

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Flexbox layout
//
// planFlex() is a pure function over main/cross sizes so it can be unit-tested;
// CssFlexMeasurePolicy feeds it intrinsic measurements and places the children.
// ─────────────────────────────────────────────────────────────────────────────

/** What the flex algorithm needs to know about one in-flow item. */
internal class FlexItemInput(
    val grow: Float,
    val shrink: Float,
    /** flex-basis, or the max-content size when `auto`. */
    val hypotheticalMain: Int,
    /** The min-content size: items never shrink below it. */
    val minMain: () -> Int,
    /** Normalised `align-self`, or `null` for `auto`. */
    val alignSelf: String?,
    /** `false` when the item has its own cross size (so `stretch` doesn't apply). */
    val stretchable: Boolean,
    /** The item's natural cross size when laid out at a given main size. */
    val crossAt: (main: Int) -> Int,
)

internal class FlexPlan(
    val containerMain: Int,
    val containerCross: Int,
    val mainSizes: IntArray,
    val mainPositions: IntArray,
    val lineOf: IntArray,
    val lineOffsets: IntArray,
    val lineSizes: IntArray,
    /** Cross size to force for stretched items, or -1. */
    val stretchedCross: IntArray,
)

private const val UNBOUNDED = Constraints.Infinity

/** Runs the flexbox sizing & positioning algorithm along abstract main/cross axes. */
internal fun planFlex(
    items: List<FlexItemInput>,
    spec: FlexWrap,
    mainGap: Int,
    crossGap: Int,
    mainMin: Int,
    mainMax: Int,
    crossMin: Int,
    crossMax: Int,
): FlexPlan {
    val n = items.size
    val boundedMain = mainMax != UNBOUNDED

    // 1. Break into lines
    val lines = mutableListOf<IntRange>()
    if (spec.wrap && boundedMain) {
        var start = 0
        var used = 0
        for (i in 0 until n) {
            val add = items[i].hypotheticalMain + if (i > start) mainGap else 0
            if (i > start && used + add > mainMax) {
                lines += start until i
                start = i
                used = items[i].hypotheticalMain
            } else {
                used += add
            }
        }
        if (n > 0) lines += start until n
    } else if (n > 0) {
        lines += 0 until n
    }

    // 2. Container main size
    val contentMain = lines.maxOfOrNull { line ->
        line.sumOf { items[it].hypotheticalMain } + mainGap * (line.count() - 1)
    } ?: 0
    val containerMain = if (boundedMain && mainMin == mainMax) mainMax
    else contentMain.coerceIn(mainMin, if (boundedMain) mainMax else Int.MAX_VALUE)

    // 3. Resolve flexible lengths per line
    val mainSizes = IntArray(n)
    val lineOf = IntArray(n)
    lines.forEachIndexed { lineIndex, line ->
        for (i in line) lineOf[i] = lineIndex
        val sizes = resolveFlexibleLengths(line.map { items[it] }, containerMain - mainGap * (line.count() - 1))
        line.forEachIndexed { k, i -> mainSizes[i] = sizes[k].roundToInt().coerceAtLeast(0) }
    }

    // 4. Line cross sizes and container cross size
    val lineSizes = IntArray(lines.size) { l -> lines[l].maxOf { items[it].crossAt(mainSizes[it]) } }
    val crossExact = crossMax != UNBOUNDED && crossMin == crossMax
    if (lines.size == 1) lineSizes[0] = if (crossExact) crossMax else max(lineSizes[0], crossMin)
    val contentCross = lineSizes.sum() + crossGap * (lines.size - 1).coerceAtLeast(0)
    val containerCross = if (crossExact) crossMax
    else contentCross.coerceIn(crossMin, if (crossMax == UNBOUNDED) Int.MAX_VALUE else crossMax)

    // 5. align-content: distribute lines along the cross axis
    val lineOffsets = IntArray(lines.size)
    run {
        val free = (containerCross - contentCross).toFloat()
        val count = lines.size
        var cursor = 0f
        var between = crossGap.toFloat()
        when (spec.alignContent) {
            "center" -> cursor = free / 2
            "end" -> cursor = free
            "space-between" -> if (count > 1 && free > 0) between += free / (count - 1)
            "space-around" -> if (free > 0) { cursor = free / count / 2; between += free / count }
            "space-evenly" -> if (free > 0) { cursor = free / (count + 1); between += free / (count + 1) }
        }
        for (l in 0 until count) {
            lineOffsets[l] = cursor.roundToInt()
            cursor += lineSizes[l] + between
        }
    }

    // 6. justify-content along the main axis
    val mainPositions = IntArray(n)
    for (line in lines) {
        val count = line.count()
        val used = line.sumOf { mainSizes[it] } + mainGap * (count - 1)
        val free = (containerMain - used).toFloat()
        var cursor = 0f
        var between = mainGap.toFloat()
        when (spec.justifyContent) {
            "center" -> cursor = free / 2
            "end" -> cursor = free
            "space-between" -> if (count > 1 && free > 0) between += free / (count - 1)
            "space-around" -> if (free > 0) { cursor = free / count / 2; between += free / count }
            "space-evenly" -> if (free > 0) { cursor = free / (count + 1); between += free / (count + 1) }
        }
        for (i in line) {
            val position = cursor.roundToInt()
            mainPositions[i] = if (spec.direction.isReverse) containerMain - position - mainSizes[i] else position
            cursor += mainSizes[i] + between
        }
    }

    // 7. Stretch
    val stretchedCross = IntArray(n) { i ->
        val align = items[i].alignSelf ?: spec.alignItems
        if (align == "stretch" && items[i].stretchable) lineSizes[lineOf[i]] else -1
    }
    return FlexPlan(containerMain, containerCross, mainSizes, mainPositions, lineOf, lineOffsets, lineSizes, stretchedCross)
}

/**
 * CSS "resolve flexible lengths" for one line: grow into free space, or shrink
 * (weighted by shrink × basis). Items that would shrink below their min-content size
 * are frozen at it and the remaining overflow is redistributed among the others.
 */
private fun resolveFlexibleLengths(line: List<FlexItemInput>, available: Int): FloatArray {
    val sizes = FloatArray(line.size) { line[it].hypotheticalMain.toFloat() }
    val initialFree = available - sizes.sum()
    if (initialFree > 0) {
        val totalGrow = line.sumOf { it.grow.toDouble() }.toFloat()
        if (totalGrow > 0f) line.forEachIndexed { i, item -> sizes[i] += initialFree * item.grow / totalGrow }
        return sizes
    }
    if (initialFree == 0f) return sizes
    val frozen = BooleanArray(line.size) { line[it].shrink <= 0f }
    val minSizes = FloatArray(line.size) { -1f }
    repeat(line.size + 1) {
        val free = available - line.indices.sumOf { i ->
            (if (frozen[i]) sizes[i] else line[i].hypotheticalMain.toFloat()).toDouble()
        }.toFloat()
        val totalShrink = line.indices.filterNot { frozen[it] }
            .sumOf { (line[it].shrink * line[it].hypotheticalMain).toDouble() }.toFloat()
        if (free >= 0f || totalShrink <= 0f) return sizes
        var clamped = false
        for (i in line.indices) {
            if (frozen[i]) continue
            val item = line[i]
            val target = item.hypotheticalMain + free * (item.shrink * item.hypotheticalMain) / totalShrink
            if (minSizes[i] < 0f) minSizes[i] = item.minMain().toFloat().coerceAtMost(item.hypotheticalMain.toFloat())
            if (target < minSizes[i]) {
                sizes[i] = minSizes[i]
                frozen[i] = true
                clamped = true
            } else {
                sizes[i] = target
            }
        }
        if (!clamped) return sizes
    }
    return sizes
}

/** A CSS flex container (also used for block and inline flow). */
@Composable
internal fun CssFlexLayout(spec: FlexWrap, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val policy = remember(spec) { CssFlexMeasurePolicy(spec) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

/** Runs [block], returning `null` for layouts that don't support intrinsics (e.g. lazy lists). */
private inline fun safeIntrinsic(block: () -> Int): Int? = try {
    block()
} catch (_: IllegalStateException) {
    null
}

private class CssFlexMeasurePolicy(private val spec: FlexWrap) : MeasurePolicy {

    private fun IntrinsicMeasurable.item() = (parentData as? FlexItemStyle) ?: FlexItemStyle.Default

    /** In-flow children (absolute ones excluded), in `order`. */
    private fun <M : IntrinsicMeasurable> inFlow(measurables: List<M>): List<Pair<M, FlexItemStyle>> =
        measurables.map { it to it.item() }
            .filter { it.second.position != CssPosition.Absolute }
            .sortedBy { it.second.order }

    private fun Density.flowInputs(
        flow: List<Pair<IntrinsicMeasurable, FlexItemStyle>>,
        row: Boolean,
        mainMax: Int,
        crossMax: Int,
    ): List<FlexItemInput> = flow.map { (measurable, item) ->
        fun intrinsicMain(): Int = safeIntrinsic {
            if (row) measurable.maxIntrinsicWidth(crossMax) else measurable.maxIntrinsicHeight(crossMax)
        } ?: if (mainMax != UNBOUNDED) mainMax else 0
        val hypothetical = when (val basis = item.basis) {
            is CssLength.Fixed -> basis.dp.roundToPx()
            is CssLength.Percent -> if (mainMax != UNBOUNDED) (basis.fraction * mainMax).roundToInt() else intrinsicMain()
            else -> intrinsicMain()
        }.coerceAtLeast(0)
        FlexItemInput(
            grow = item.grow,
            shrink = item.shrink,
            hypotheticalMain = hypothetical,
            minMain = {
                safeIntrinsic {
                    if (row) measurable.minIntrinsicWidth(crossMax) else measurable.minIntrinsicHeight(crossMax)
                } ?: 0
            },
            alignSelf = item.alignSelf,
            stretchable = if (row) !item.hasHeight else !item.hasWidth,
            crossAt = { main ->
                safeIntrinsic {
                    if (row) measurable.maxIntrinsicHeight(main) else measurable.maxIntrinsicWidth(main)
                } ?: 0
            },
        )
    }

    // ── Intrinsics ──
    // Compose's default intrinsics would run measure() with a minimum size of 0, so a
    // flex row asked "how tall are you at width W?" would shrink-wrap and give its
    // `flex: 1` children no width. CSS treats W as the container's definite width, so do that.

    /** Height (row) or width (column) of the container when its main size is [main]. */
    private fun Density.crossSizeFor(measurables: List<IntrinsicMeasurable>, main: Int): Int {
        val row = spec.direction.isRow
        val flow = inFlow(measurables)
        if (flow.isEmpty()) return 0
        val bounded = main != UNBOUNDED
        val plan = planFlex(
            items = flowInputs(flow, row, main, UNBOUNDED),
            spec = spec,
            mainGap = spec.mainGap.roundToPx(),
            crossGap = spec.crossGap.roundToPx(),
            mainMin = if (bounded) main else 0,
            mainMax = main,
            crossMin = 0,
            crossMax = UNBOUNDED,
        )
        return plan.containerCross
    }

    /** Main size of the container (height of a column) when its cross size is [cross]. */
    private fun Density.mainSizeFor(measurables: List<IntrinsicMeasurable>, cross: Int): Int {
        val row = spec.direction.isRow
        val flow = inFlow(measurables)
        if (flow.isEmpty()) return 0
        val bounded = cross != UNBOUNDED
        val plan = planFlex(
            items = flowInputs(flow, row, UNBOUNDED, cross),
            spec = spec,
            mainGap = spec.mainGap.roundToPx(),
            crossGap = spec.crossGap.roundToPx(),
            mainMin = 0,
            mainMax = UNBOUNDED,
            crossMin = if (bounded) cross else 0,
            crossMax = cross,
        )
        return plan.containerMain
    }

    /** Max- or min-content size along the main axis of a row (width) or cross axis of a column (width). */
    private fun Density.contentWidth(measurables: List<IntrinsicMeasurable>, height: Int, max: Boolean): Int {
        val flow = inFlow(measurables)
        if (flow.isEmpty()) return 0
        val widths = flow.map { (m, item) ->
            (item.basis as? CssLength.Fixed)?.takeIf { spec.direction.isRow }?.dp?.roundToPx()
                ?: safeIntrinsic { if (max) m.maxIntrinsicWidth(height) else m.minIntrinsicWidth(height) } ?: 0
        }
        val gap = spec.columnGap.roundToPx()
        return if (spec.direction.isRow && !(spec.wrap && !max)) widths.sum() + gap * (widths.size - 1) else widths.max()
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        contentWidth(measurables, height, max = true)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        contentWidth(measurables, height, max = false)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (spec.direction.isRow) crossSizeFor(measurables, width) else mainSizeFor(measurables, width)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        maxIntrinsicHeight(measurables, width)

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val row = spec.direction.isRow
        val absolute = measurables.map { it to it.item() }.filter { it.second.position == CssPosition.Absolute }
        val flow = inFlow(measurables)

        val mainMin = if (row) constraints.minWidth else constraints.minHeight
        val mainMax = if (row) constraints.maxWidth else constraints.maxHeight
        val crossMin = if (row) constraints.minHeight else constraints.minWidth
        val crossMax = if (row) constraints.maxHeight else constraints.maxWidth

        // Fast path — a column with unbounded height in a container of definite width (scrolling
        // pages, block flow). Nothing can grow, shrink or wrap there, and the line's cross size is
        // the container width, so children can be measured directly. Skipping intrinsics matters:
        // Compose remeasures a parent whenever a child whose intrinsics it read changes size, so
        // intrinsic queries would make every layout animation re-run the whole ancestor chain.
        val fastColumn = !row && mainMax == UNBOUNDED && !spec.wrap && crossMin == crossMax &&
            crossMax != UNBOUNDED && flow.none { it.second.basis != null } &&
            (mainMin == 0 || flow.none { it.second.grow > 0f })

        val inputs: List<FlexItemInput>
        val fastPlaceables: List<Placeable>?
        if (fastColumn) {
            fastPlaceables = flow.map { (measurable, item) ->
                val stretch = (item.alignSelf ?: spec.alignItems) == "stretch" && !item.hasWidth
                measurable.measure(Constraints(minWidth = if (stretch) crossMax else 0, maxWidth = crossMax))
            }
            inputs = flow.mapIndexed { i, (_, item) ->
                val placeable = fastPlaceables[i]
                FlexItemInput(
                    grow = 0f, shrink = 0f,
                    hypotheticalMain = placeable.height,
                    minMain = { placeable.height },
                    alignSelf = item.alignSelf,
                    stretchable = !item.hasWidth,
                    crossAt = { placeable.width },
                )
            }
        } else {
            fastPlaceables = null
            inputs = flowInputs(flow, row, mainMax, crossMax)
        }

        val plan = planFlex(
            items = inputs,
            spec = spec,
            mainGap = spec.mainGap.roundToPx(),
            crossGap = spec.crossGap.roundToPx(),
            mainMin = mainMin,
            mainMax = mainMax,
            crossMin = crossMin,
            crossMax = crossMax,
        )

        val placeables = fastPlaceables ?: flow.mapIndexed { i, (measurable, _) ->
            val main = plan.mainSizes[i]
            val stretched = plan.stretchedCross[i]
            val minCross = if (stretched >= 0) stretched else 0
            val maxCross = if (stretched >= 0) stretched else plan.containerCross
            measurable.measure(
                if (row) Constraints(main, main, minCross, maxCross)
                else Constraints(minCross, maxCross, main, main)
            )
        }

        val width = if (row) plan.containerMain else plan.containerCross
        val height = if (row) plan.containerCross else plan.containerMain

        val positioned = absolute.map { (measurable, item) ->
            val insets = item.insets
            fun CssLength?.px(total: Int): Int? = when (this) {
                is CssLength.Fixed -> dp.roundToPx()
                is CssLength.Percent -> (fraction * total).roundToInt()
                else -> null
            }
            val left = insets.left.px(width)
            val right = insets.right.px(width)
            val top = insets.top.px(height)
            val bottom = insets.bottom.px(height)
            val w = if (left != null && right != null) max(0, width - left - right) else null
            val h = if (top != null && bottom != null) max(0, height - top - bottom) else null
            val placeable = measurable.measure(
                Constraints(
                    minWidth = w ?: 0, maxWidth = w ?: width,
                    minHeight = h ?: 0, maxHeight = h ?: height,
                )
            )
            val x = left ?: right?.let { width - it - placeable.width } ?: 0
            val y = top ?: bottom?.let { height - it - placeable.height } ?: 0
            Triple(placeable, x, y)
        }

        return layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val line = plan.lineOf[i]
                val lineSize = plan.lineSizes[line]
                val itemCross = if (row) placeable.height else placeable.width
                val align = inputs[i].alignSelf ?: spec.alignItems
                val crossPosition = plan.lineOffsets[line] + when (align) {
                    "center" -> (lineSize - itemCross) / 2
                    "end" -> lineSize - itemCross
                    else -> 0
                }
                if (row) placeable.placeRelative(plan.mainPositions[i], crossPosition)
                else placeable.placeRelative(crossPosition, plan.mainPositions[i])
            }
            // Absolutely positioned children are placed last, so they paint on top
            positioned.forEach { (placeable, x, y) -> placeable.place(x, y) }
        }
    }
}
