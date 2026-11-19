package io.github.stagnant09.csscompose.motion

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// Timing functions
// ─────────────────────────────────────────────────────────────────────────────

/** A CSS `<easing-function>`. Kept as data so styles stay comparable. */
@Immutable
sealed interface CssEasing {
    fun toEasing(): Easing

    data object Linear : CssEasing {
        override fun toEasing(): Easing = LinearEasing
    }

    data class CubicBezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : CssEasing {
        override fun toEasing(): Easing = CubicBezierEasing(x1, y1, x2, y2)
    }

    data class Steps(val count: Int, val jumpStart: Boolean) : CssEasing {
        override fun toEasing(): Easing = Easing { t ->
            val n = count.coerceAtLeast(1)
            val stepped = if (jumpStart) ceil(t * n) / n else floor(t * n) / n
            stepped.coerceIn(0f, 1f)
        }
    }

    companion object {
        val Ease = CubicBezier(0.25f, 0.1f, 0.25f, 1f)
        val EaseIn = CubicBezier(0.42f, 0f, 1f, 1f)
        val EaseOut = CubicBezier(0f, 0f, 0.58f, 1f)
        val EaseInOut = CubicBezier(0.42f, 0f, 0.58f, 1f)

        /** Parses `ease`, `linear`, `cubic-bezier(...)`, `steps(...)`, `step-start`, … */
        fun parse(value: String): CssEasing? {
            val v = value.trim().lowercase()
            return when {
                v == "linear" -> Linear
                v == "ease" -> Ease
                v == "ease-in" -> EaseIn
                v == "ease-out" -> EaseOut
                v == "ease-in-out" -> EaseInOut
                v == "step-start" -> Steps(1, jumpStart = true)
                v == "step-end" -> Steps(1, jumpStart = false)
                v.startsWith("cubic-bezier(") && v.endsWith(")") -> {
                    val n = v.substring(13, v.length - 1).split(',').map { it.trim().toFloatOrNull() ?: return null }
                    if (n.size == 4) CubicBezier(n[0].coerceIn(0f, 1f), n[1], n[2].coerceIn(0f, 1f), n[3]) else null
                }
                v.startsWith("steps(") && v.endsWith(")") -> {
                    val args = v.substring(6, v.length - 1).split(',').map { it.trim() }
                    val count = args[0].toIntOrNull() ?: return null
                    Steps(count, jumpStart = args.getOrNull(1) in setOf("start", "jump-start"))
                }
                else -> null
            }
        }
    }
}

/** Parses a CSS `<time>` (`200ms`, `0.3s`) into milliseconds. */
internal fun parseTime(value: String): Int? {
    val (number, unit) = splitNumber(value) ?: return null
    return when (unit) {
        "ms" -> number
        "s" -> number * 1000f
        else -> return null
    }.toInt().coerceAtLeast(0)
}

// ─────────────────────────────────────────────────────────────────────────────
// transition
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class CssTransition(
    val property: String = "all",
    val durationMs: Int = 0,
    val delayMs: Int = 0,
    val easing: CssEasing = CssEasing.Ease,
)

private fun parseTransitionItem(item: String): CssTransition? {
    var transition = CssTransition()
    var times = 0
    for (token in splitTokens(item.trim())) {
        val time = parseTime(token)
        val easing = if (time == null) CssEasing.parse(token) else null
        when {
            time != null && times == 0 -> { transition = transition.copy(durationMs = time); times++ }
            time != null -> transition = transition.copy(delayMs = time)
            easing != null -> transition = transition.copy(easing = easing)
            token.equals("none", ignoreCase = true) -> return null
            else -> transition = transition.copy(property = token.lowercase())
        }
    }
    return transition
}

/**
 * Resolves `transition` and its longhands (`transition-property`, `-duration`,
 * `-timing-function`, `-delay`). Longhand lists repeat cyclically, as in CSS.
 */
fun parseTransitions(declarations: Map<String, String>): List<CssTransition> {
    var base = emptyList<CssTransition>()
    val longhands = LinkedHashMap<String, List<String>>()
    for ((property, value) in declarations) {
        when (property) {
            "transition" -> {
                base = if (value.trim().equals("none", true)) emptyList()
                else splitTopLevel(value, ',').mapNotNull(::parseTransitionItem)
                longhands.clear()
            }
            "transition-property", "transition-duration", "transition-timing-function", "transition-delay" ->
                longhands[property] = splitTopLevel(value, ',').map { it.trim() }
        }
    }
    longhands["transition-property"]?.let { names ->
        base = names.mapIndexed { i, name ->
            (base.getOrNull(i) ?: CssTransition()).copy(property = name.lowercase())
        }.filterNot { it.property == "none" }
    }
    if (base.isEmpty()) {
        // Longhands without a property list default to `all`
        if (longhands.isEmpty()) return emptyList()
        base = listOf(CssTransition())
    }
    fun <T> List<String>.cycled(i: Int, parse: (String) -> T?): T? = getOrNull(i % size)?.let(parse)
    return base.mapIndexed { i, t ->
        var r = t
        longhands["transition-duration"]?.cycled(i, ::parseTime)?.let { r = r.copy(durationMs = it) }
        longhands["transition-delay"]?.cycled(i, ::parseTime)?.let { r = r.copy(delayMs = it) }
        longhands["transition-timing-function"]?.cycled(i, CssEasing::parse)?.let { r = r.copy(easing = it) }
        r
    }.filter { it.durationMs > 0 }
}

// ─────────────────────────────────────────────────────────────────────────────
// animation
// ─────────────────────────────────────────────────────────────────────────────

enum class AnimationDirection { Normal, Reverse, Alternate, AlternateReverse }
enum class AnimationFillMode { None, Forwards, Backwards, Both }

@Immutable
data class CssAnimationRef(
    val name: String,
    val durationMs: Int = 0,
    val delayMs: Int = 0,
    val easing: CssEasing = CssEasing.Ease,
    /** May be [Float.POSITIVE_INFINITY] for `infinite`. */
    val iterations: Float = 1f,
    val direction: AnimationDirection = AnimationDirection.Normal,
    val fillMode: AnimationFillMode = AnimationFillMode.None,
    val paused: Boolean = false,
)

private fun parseDirection(token: String) = when (token.lowercase()) {
    "normal" -> AnimationDirection.Normal
    "reverse" -> AnimationDirection.Reverse
    "alternate" -> AnimationDirection.Alternate
    "alternate-reverse" -> AnimationDirection.AlternateReverse
    else -> null
}

private fun parseFillMode(token: String) = when (token.lowercase()) {
    "none" -> AnimationFillMode.None
    "forwards" -> AnimationFillMode.Forwards
    "backwards" -> AnimationFillMode.Backwards
    "both" -> AnimationFillMode.Both
    else -> null
}

private fun parseIterations(token: String): Float? =
    if (token.equals("infinite", ignoreCase = true)) Float.POSITIVE_INFINITY
    else token.toFloatOrNull()?.coerceAtLeast(0f)

private fun parseAnimationItem(item: String): CssAnimationRef? {
    var ref = CssAnimationRef(name = "")
    var times = 0
    for (token in splitTokens(item.trim())) {
        val lower = token.lowercase()
        val time = parseTime(lower)
        val easing = if (time == null) CssEasing.parse(lower) else null
        val iterations = if (time == null && easing == null) parseIterations(lower) else null
        when {
            time != null && times == 0 -> { ref = ref.copy(durationMs = time); times++ }
            time != null -> ref = ref.copy(delayMs = time)
            easing != null -> ref = ref.copy(easing = easing)
            iterations != null -> ref = ref.copy(iterations = iterations)
            lower != "normal" && parseDirection(lower) != null -> ref = ref.copy(direction = parseDirection(lower)!!)
            lower != "none" && parseFillMode(lower) != null -> ref = ref.copy(fillMode = parseFillMode(lower)!!)
            lower == "paused" -> ref = ref.copy(paused = true)
            lower == "running" || lower == "normal" -> Unit
            ref.name.isEmpty() -> ref = ref.copy(name = token)
        }
    }
    return ref.takeIf { it.name.isNotEmpty() && it.name != "none" }
}

/** Resolves `animation` and its longhands into references to `@keyframes`. */
fun parseAnimationRefs(declarations: Map<String, String>): List<CssAnimationRef> {
    var base = emptyList<CssAnimationRef>()
    val longhands = LinkedHashMap<String, List<String>>()
    for ((property, value) in declarations) {
        when {
            property == "animation" -> {
                base = if (value.trim().equals("none", true)) emptyList()
                else splitTopLevel(value, ',').mapNotNull(::parseAnimationItem)
                longhands.clear()
            }
            property.startsWith("animation-") -> longhands[property] = splitTopLevel(value, ',').map { it.trim() }
        }
    }
    longhands["animation-name"]?.let { names ->
        base = names.mapIndexed { i, name ->
            (base.getOrNull(i) ?: CssAnimationRef(name = name)).copy(name = name)
        }.filterNot { it.name == "none" }
    }
    fun <T> List<String>.cycled(i: Int, parse: (String) -> T?): T? = getOrNull(i % size)?.let(parse)
    return base.mapIndexed { i, a ->
        var r = a
        longhands["animation-duration"]?.cycled(i, ::parseTime)?.let { r = r.copy(durationMs = it) }
        longhands["animation-delay"]?.cycled(i, ::parseTime)?.let { r = r.copy(delayMs = it) }
        longhands["animation-timing-function"]?.cycled(i, CssEasing::parse)?.let { r = r.copy(easing = it) }
        longhands["animation-iteration-count"]?.cycled(i, ::parseIterations)?.let { r = r.copy(iterations = it) }
        longhands["animation-direction"]?.cycled(i, ::parseDirection)?.let { r = r.copy(direction = it) }
        longhands["animation-fill-mode"]?.cycled(i, ::parseFillMode)?.let { r = r.copy(fillMode = it) }
        longhands["animation-play-state"]?.cycled(i) { it.lowercase() }?.let { r = r.copy(paused = it == "paused") }
        r
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Channels: the animatable part of a StyleResult as a flat FloatArray.
//
// Colors are stored premultiplied (CSS interpolates premultiplied, so fading from
// `transparent` to red never passes through grey). NaN marks a value that can't be
// interpolated (e.g. `width: 50%`, an inherited text color); such channels snap.
// ─────────────────────────────────────────────────────────────────────────────

internal object Channel {
    const val COLOR = 0           // 4
    const val BACKGROUND = 4      // 4
    const val BORDER_COLOR = 8    // 4 sides × 4
    const val BORDER_WIDTH = 24   // 4
    const val MARGIN = 28         // 4
    const val PADDING = 32        // 4
    const val WIDTH = 36
    const val HEIGHT = 37
    const val RADIUS = 38         // 4
    const val OPACITY = 42
    const val TRANSLATE_X = 43
    const val TRANSLATE_Y = 44
    const val ROTATE = 45
    const val SCALE_X = 46
    const val SCALE_Y = 47
    const val BLUR = 48
    const val SHADOW = 49         // x, y, blur, spread, color×4
    const val FONT_SIZE = 57
    const val LETTER_SPACING = 58
    const val OUTLINE_COLOR = 59  // 4
    const val OUTLINE_WIDTH = 63
    const val OUTLINE_OFFSET = 64
    const val COUNT = 65
}

private val sideNames = listOf("top", "right", "bottom", "left")
private val cornerNames = listOf("top-left", "top-right", "bottom-right", "bottom-left")

/** The channels a CSS property animates. */
internal fun channelsFor(property: String): IntRange? = with(Channel) {
    when (property) {
        "all" -> 0 until COUNT
        "color" -> COLOR until COLOR + 4
        "background", "background-color" -> BACKGROUND until BACKGROUND + 4
        "border-color" -> BORDER_COLOR until BORDER_COLOR + 16
        "border-width" -> BORDER_WIDTH until BORDER_WIDTH + 4
        "margin" -> MARGIN until MARGIN + 4
        "padding" -> PADDING until PADDING + 4
        "width" -> WIDTH..WIDTH
        "height" -> HEIGHT..HEIGHT
        "border-radius" -> RADIUS until RADIUS + 4
        "opacity" -> OPACITY..OPACITY
        "transform" -> TRANSLATE_X..SCALE_Y
        "filter" -> BLUR..BLUR
        "box-shadow" -> SHADOW until SHADOW + 8
        "font-size", "font" -> FONT_SIZE..FONT_SIZE
        "letter-spacing" -> LETTER_SPACING..LETTER_SPACING
        "outline" -> OUTLINE_COLOR..OUTLINE_OFFSET
        "outline-color" -> OUTLINE_COLOR until OUTLINE_COLOR + 4
        "outline-width" -> OUTLINE_WIDTH..OUTLINE_WIDTH
        "outline-offset" -> OUTLINE_OFFSET..OUTLINE_OFFSET
        else -> null
    }
}

/** Like [channelsFor] but also handles per-side / per-corner longhands and `border`. */
internal fun channelSetFor(property: String): Set<Int> {
    channelsFor(property)?.let { return it.toSet() }
    val result = mutableSetOf<Int>()
    when {
        property == "border" -> {
            result += (Channel.BORDER_COLOR until Channel.BORDER_COLOR + 16)
            result += (Channel.BORDER_WIDTH until Channel.BORDER_WIDTH + 4)
        }
        property.startsWith("margin-") -> sideNames.indexOf(property.removePrefix("margin-"))
            .takeIf { it >= 0 }?.let { result += Channel.MARGIN + it }
        property.startsWith("padding-") -> sideNames.indexOf(property.removePrefix("padding-"))
            .takeIf { it >= 0 }?.let { result += Channel.PADDING + it }
        property.startsWith("border-") && property.endsWith("-radius") ->
            cornerNames.indexOf(property.removePrefix("border-").removeSuffix("-radius"))
                .takeIf { it >= 0 }?.let { result += Channel.RADIUS + it }
        property.startsWith("border-") -> {
            val rest = property.removePrefix("border-")
            val side = sideNames.indexOf(rest.substringBefore('-'))
            if (side >= 0) {
                val part = rest.substringAfter('-', "")
                if (part == "" || part == "color") result += (Channel.BORDER_COLOR + side * 4 until Channel.BORDER_COLOR + side * 4 + 4)
                if (part == "" || part == "width") result += Channel.BORDER_WIDTH + side
            }
        }
    }
    return result
}

private fun FloatArray.putColor(index: Int, color: Color?) {
    if (color == null || color == Color.Unspecified) {
        for (k in 0..3) this[index + k] = Float.NaN
        return
    }
    this[index] = color.red * color.alpha
    this[index + 1] = color.green * color.alpha
    this[index + 2] = color.blue * color.alpha
    this[index + 3] = color.alpha
}

private fun FloatArray.getColor(index: Int): Color? {
    val a = this[index + 3]
    if (a.isNaN()) return null
    if (a <= 0f) return Color.Transparent
    return Color(
        red = (this[index] / a).coerceIn(0f, 1f),
        green = (this[index + 1] / a).coerceIn(0f, 1f),
        blue = (this[index + 2] / a).coerceIn(0f, 1f),
        alpha = a.coerceIn(0f, 1f),
    )
}

private fun TextUnit.spOrNaN(): Float = if (type == TextUnitType.Sp) value else Float.NaN

/** Encodes the animatable properties of [style]. */
internal fun encodeChannels(style: StyleResult): FloatArray {
    val v = FloatArray(Channel.COUNT)
    val box = style.box
    val currentColor = style.color.takeIf { it != Color.Unspecified } ?: Color.Black
    v.putColor(Channel.COLOR, style.color)
    v.putColor(Channel.BACKGROUND, box.backgroundColor ?: Color.Transparent)
    for (side in 0..3) {
        v.putColor(Channel.BORDER_COLOR + side * 4, box.borders[side].color ?: currentColor)
        v[Channel.BORDER_WIDTH + side] = box.borders[side].resolvedWidth.value
        v[Channel.MARGIN + side] = box.margin[side].value
        v[Channel.PADDING + side] = box.padding[side].value
        val corner = box.radii[side]
        v[Channel.RADIUS + side] = if (corner.isPercent) Float.NaN else corner.value
    }
    v[Channel.WIDTH] = (box.width as? CssLength.Fixed)?.dp?.value ?: Float.NaN
    v[Channel.HEIGHT] = (box.height as? CssLength.Fixed)?.dp?.value ?: Float.NaN
    v[Channel.OPACITY] = box.opacity
    v[Channel.TRANSLATE_X] = box.transform.translateX.value
    v[Channel.TRANSLATE_Y] = box.transform.translateY.value
    v[Channel.ROTATE] = box.transform.rotate
    v[Channel.SCALE_X] = box.transform.scaleX
    v[Channel.SCALE_Y] = box.transform.scaleY
    v[Channel.BLUR] = box.blur.value
    val shadow = box.shadows.firstOrNull()
    v[Channel.SHADOW] = shadow?.offsetX?.value ?: 0f
    v[Channel.SHADOW + 1] = shadow?.offsetY?.value ?: 0f
    v[Channel.SHADOW + 2] = shadow?.blur?.value ?: 0f
    v[Channel.SHADOW + 3] = shadow?.spread?.value ?: 0f
    v.putColor(Channel.SHADOW + 4, shadow?.color ?: Color.Transparent)
    v[Channel.FONT_SIZE] = style.fontSize.spOrNaN()
    v[Channel.LETTER_SPACING] = style.letterSpacing.spOrNaN()
    val outline = box.outline
    v.putColor(Channel.OUTLINE_COLOR, outline?.color ?: currentColor)
    v[Channel.OUTLINE_WIDTH] = outline?.resolvedWidth?.value ?: 0f
    v[Channel.OUTLINE_OFFSET] = outline?.offset?.value ?: 0f
    return v
}

/** Writes interpolated channel [values] back onto [base]. */
internal fun decodeChannels(base: StyleResult, values: FloatArray): StyleResult {
    val box = base.box
    fun dp(i: Int) = values[i].coerceAtLeast(0f).dp
    val borders = box.borders.mapIndexed { side, border ->
        val width = values[Channel.BORDER_WIDTH + side]
        val color = values.getColor(Channel.BORDER_COLOR + side * 4)
        when {
            width <= 0f -> border.copy(width = 0.dp, color = color ?: border.color)
            border.resolvedStyle.isVisible -> border.copy(width = width.dp, color = color ?: border.color)
            // Animating out of / into `border: none`: keep drawing until the width reaches 0
            else -> BorderSide(width = width.dp, style = BorderStyle.Solid, color = color)
        }
    }
    val shadowColor = values.getColor(Channel.SHADOW + 4) ?: Color.Transparent
    val shadowIsEmpty = shadowColor.alpha <= 0f && box.shadows.isEmpty()
    val firstShadow = CssShadow(
        offsetX = values[Channel.SHADOW].dp,
        offsetY = values[Channel.SHADOW + 1].dp,
        blur = dp(Channel.SHADOW + 2),
        spread = values[Channel.SHADOW + 3].dp,
        color = shadowColor,
    )
    val background = values.getColor(Channel.BACKGROUND)
    val outlineWidth = values[Channel.OUTLINE_WIDTH]
    val outlineColor = values.getColor(Channel.OUTLINE_COLOR)
    val outlineOffset = values[Channel.OUTLINE_OFFSET].dp // may be negative
    val outline = box.outline.let { o ->
        when {
            outlineWidth <= 0f -> o?.copy(width = 0.dp, offset = outlineOffset)
            o != null && o.resolvedStyle.isVisible ->
                o.copy(width = outlineWidth.dp, color = outlineColor ?: o.color, offset = outlineOffset)
            // Animating out of / into `outline: none`: keep drawing until the width reaches 0
            else -> CssOutline(outlineWidth.dp, BorderStyle.Solid, outlineColor, outlineOffset)
        }
    }
    return base.copy(
        color = values.getColor(Channel.COLOR) ?: base.color,
        fontSize = values[Channel.FONT_SIZE].takeUnless { it.isNaN() }?.sp ?: base.fontSize,
        letterSpacing = values[Channel.LETTER_SPACING].takeUnless { it.isNaN() }?.sp ?: base.letterSpacing,
        box = box.copy(
            backgroundColor = if (box.backgroundColor == null && (background == null || background.alpha <= 0f)) null else background,
            borders = borders,
            margin = Edges(dp(Channel.MARGIN), dp(Channel.MARGIN + 1), dp(Channel.MARGIN + 2), dp(Channel.MARGIN + 3)),
            padding = Edges(dp(Channel.PADDING), dp(Channel.PADDING + 1), dp(Channel.PADDING + 2), dp(Channel.PADDING + 3)),
            width = values[Channel.WIDTH].takeUnless { it.isNaN() }?.let { CssLength.Fixed(it.coerceAtLeast(0f).dp) } ?: box.width,
            height = values[Channel.HEIGHT].takeUnless { it.isNaN() }?.let { CssLength.Fixed(it.coerceAtLeast(0f).dp) } ?: box.height,
            radii = box.radii.mapIndexed { i, corner ->
                values[Channel.RADIUS + i].takeUnless { it.isNaN() }?.let { CornerValue(it.coerceAtLeast(0f)) } ?: corner
            },
            opacity = values[Channel.OPACITY].coerceIn(0f, 1f),
            transform = TransformSpec(
                translateX = values[Channel.TRANSLATE_X].dp,
                translateY = values[Channel.TRANSLATE_Y].dp,
                rotate = values[Channel.ROTATE],
                scaleX = values[Channel.SCALE_X],
                scaleY = values[Channel.SCALE_Y],
            ),
            blur = dp(Channel.BLUR),
            shadows = if (shadowIsEmpty) box.shadows else listOf(firstShadow) + box.shadows.drop(1),
            outline = outline,
        ),
    )
}

/** For each channel, the transition that animates it (last matching wins), or `null`. */
internal fun transitionPerChannel(transitions: List<CssTransition>): Array<CssTransition?> {
    val result = arrayOfNulls<CssTransition>(Channel.COUNT)
    for (t in transitions) for (c in channelSetFor(t.property)) result[c] = t
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyframes
// ─────────────────────────────────────────────────────────────────────────────

/** One block inside `@keyframes`, e.g. `0%, 100% { … }`. */
@Immutable
data class KeyframeRule(val offsets: List<Float>, val declarations: List<CssDeclaration>)

@Immutable
data class CssKeyframes(val name: String, val rules: List<KeyframeRule>)

/** The interpolation track of one channel: sorted keyframe offsets and values. */
internal class Track(
    val channel: Int,
    val offsets: FloatArray,
    val values: FloatArray,
    /** Easing applied from each keyframe to the next (`null` = the animation's). */
    val easings: Array<Easing?>,
)

/** An `animation` resolved against its `@keyframes`, ready to be sampled. */
class ResolvedAnimation internal constructor(
    val ref: CssAnimationRef,
    internal val tracks: List<Track>,
) {
    override fun equals(other: Any?): Boolean =
        other is ResolvedAnimation && other.ref == ref && other.tracks.size == tracks.size &&
            tracks.indices.all { i ->
                tracks[i].channel == other.tracks[i].channel &&
                    tracks[i].offsets.contentEquals(other.tracks[i].offsets) &&
                    tracks[i].values.contentEquals(other.tracks[i].values)
            }

    override fun hashCode(): Int = ref.hashCode() * 31 + tracks.sumOf { it.values.contentHashCode() }

    /** Total active time in ms (infinite for `infinite` iterations). */
    val activeDurationMs: Float get() = ref.durationMs * ref.iterations

    /**
     * Maps elapsed time (ms since the animation was applied) to keyframe progress
     * 0..1, honouring delay, iterations, direction and fill mode. `null` = no effect.
     */
    fun progressAt(elapsedMs: Float): Float? {
        val t = elapsedMs - ref.delayMs
        val duration = ref.durationMs.toFloat()
        val fillsBackwards = ref.fillMode == AnimationFillMode.Backwards || ref.fillMode == AnimationFillMode.Both
        val fillsForwards = ref.fillMode == AnimationFillMode.Forwards || ref.fillMode == AnimationFillMode.Both
        if (t < 0f) return if (fillsBackwards) directed(0, 0f) else null
        if (duration <= 0f || t >= activeDurationMs) {
            if (!fillsForwards) return null
            val iterations = ref.iterations
            if (iterations <= 0f) return directed(0, 0f)
            val whole = floor(iterations)
            return if (iterations == whole) directed(whole.toInt() - 1, 1f) else directed(whole.toInt(), iterations - whole)
        }
        val iteration = floor(t / duration).toInt()
        return directed(iteration, (t - iteration * duration) / duration)
    }

    private fun directed(iteration: Int, fraction: Float): Float = when (ref.direction) {
        AnimationDirection.Normal -> fraction
        AnimationDirection.Reverse -> 1f - fraction
        AnimationDirection.Alternate -> if (iteration % 2 == 0) fraction else 1f - fraction
        AnimationDirection.AlternateReverse -> if (iteration % 2 == 0) 1f - fraction else fraction
    }

    /** Writes the sampled values at [progress] into [out]. */
    internal fun sampleInto(progress: Float, out: FloatArray) {
        val defaultEasing = ref.easing.toEasing()
        for (track in tracks) {
            val offsets = track.offsets
            var i = 0
            while (i < offsets.size - 2 && progress > offsets[i + 1]) i++
            val from = track.values[i]
            val to = track.values[i + 1]
            val span = offsets[i + 1] - offsets[i]
            val local = if (span <= 0f) 1f else ((progress - offsets[i]) / span).coerceIn(0f, 1f)
            val eased = (track.easings[i] ?: defaultEasing).transform(local)
            out[track.channel] = when {
                from.isNaN() || to.isNaN() -> if (eased < 0.5f) from else to
                else -> from + (to - from) * eased
            }
        }
    }
}

/**
 * Resolves [refs] against [keyframes]. Each keyframe is applied on top of the element's
 * own computed declarations ([baseDeclarations]) so shorthands such as `transform`
 * replace the element's value; channels a keyframe doesn't mention are interpolated
 * only between the keyframes that do (with the element's value at 0% / 100%).
 */
internal fun resolveAnimations(
    refs: List<CssAnimationRef>,
    keyframes: Map<String, CssKeyframes>,
    baseDeclarations: Map<String, String>,
    baseStyle: StyleResult,
    variables: Map<String, String>,
): List<ResolvedAnimation> {
    if (refs.isEmpty()) return emptyList()
    val baseValues = encodeChannels(baseStyle)
    return refs.mapNotNull { ref ->
        val sheet = keyframes[ref.name] ?: return@mapNotNull null
        // offset → (values, declared channels, easing); later rules win at the same offset
        data class Frame(val offset: Float, val values: FloatArray, val mask: Set<Int>, val easing: Easing?)
        val frames = mutableListOf<Frame>()
        for (rule in sheet.rules) {
            val cascaded = cascade(rule.declarations)
            val easing = cascaded["animation-timing-function"]?.let(CssEasing::parse)?.toEasing()
            val computed = computeValues(cascaded.filterKeys { !it.startsWith("animation") }, variables)
            val merged = LinkedHashMap(baseDeclarations).apply { putAll(computed) }
            val values = encodeChannels(buildStyle(merged))
            val mask = computed.keys.flatMap { channelSetFor(it) }.toSet()
            for (offset in rule.offsets) frames += Frame(offset, values, mask, easing)
        }
        val affected = frames.flatMap { it.mask }.toSortedSet()
        val tracks = affected.map { channel ->
            val points = sortedMapOf<Float, Pair<Float, Easing?>>()
            for (frame in frames) if (channel in frame.mask) points[frame.offset] = frame.values[channel] to frame.easing
            if (0f !in points) points[0f] = baseValues[channel] to null
            if (1f !in points) points[1f] = baseValues[channel] to null
            Track(
                channel = channel,
                offsets = points.keys.toFloatArray(),
                values = points.values.map { it.first }.toFloatArray(),
                easings = points.values.map { it.second }.toTypedArray(),
            )
        }
        ResolvedAnimation(ref, tracks)
    }
}
