package io.github.stagnant09.csscompose.motion

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

/**
 * Returns [target] with `transition`s and `@keyframes` animations applied for the
 * current frame. Call it unconditionally for an element that may animate: the set of
 * composable calls inside is stable whatever the style declares.
 *
 * - Transitions: every animatable channel has an [Animatable]; when the target value of
 *   a channel changes and a transition covers it, it animates from its *current* value
 *   (so interrupted transitions reverse smoothly, like CSS). Channels without a
 *   transition snap immediately.
 * - Animations: each `animation` runs on the frame clock and overrides the channels its
 *   keyframes touch.
 */
@Composable
fun animateStyle(target: StyleResult): StyleResult {
    val targetValues = remember(target) { encodeChannels(target) }
    val specs = remember(target.transitions) { transitionPerChannel(target.transitions) }
    val animatables = remember { Array(Channel.COUNT) { Animatable(targetValues[it]) } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetValues) {
        for (i in 0 until Channel.COUNT) {
            val animatable = animatables[i]
            val to = targetValues[i]
            if (animatable.targetValue == to || (animatable.targetValue.isNaN() && to.isNaN())) continue
            val spec = specs[i]
            // Launched in the composition scope (not this effect's) so a later target
            // change doesn't cancel transitions of channels that didn't change.
            scope.launch {
                if (spec == null || animatable.value.isNaN() || to.isNaN()) {
                    animatable.snapTo(to)
                } else {
                    animatable.animateTo(to, tween(spec.durationMs, spec.delayMs, spec.easing.toEasing()))
                }
            }
        }
    }

    val values = FloatArray(Channel.COUNT) { i ->
        if (specs[i] == null) targetValues[i] else animatables[i].value
    }

    for (animation in target.animations) {
        key(animation.ref.copy(paused = false)) {
            val elapsed = animationElapsedMs(animation)
            animation.progressAt(elapsed)?.let { animation.sampleInto(it, values) }
        }
    }
    return decodeChannels(target, values)
}

/** Milliseconds since [animation] started, driven by the frame clock. Pausing keeps the time. */
@Composable
private fun animationElapsedMs(animation: ResolvedAnimation): Float {
    val elapsed = remember { mutableFloatStateOf(0f) }
    val ref = animation.ref
    LaunchedEffect(ref.paused) {
        if (ref.paused) return@LaunchedEffect
        val end = ref.delayMs + animation.activeDurationMs
        val resumeFrom = elapsed.floatValue
        val start = withFrameNanos { it }
        while (elapsed.floatValue < end) {
            withFrameNanos { now -> elapsed.floatValue = resumeFrom + (now - start) / 1_000_000f }
        }
    }
    return elapsed.floatValue
}
