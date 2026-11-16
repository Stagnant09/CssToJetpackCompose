package com.example.cssintegration.cssUtils

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** The environment `@media` queries are evaluated against. Sizes are in dp. */
@Immutable
data class MediaContext(
    val widthDp: Float = 411f,
    val heightDp: Float = 891f,
    val darkTheme: Boolean = false,
    val reducedMotion: Boolean = false,
) {
    companion object {
        /** A typical phone in portrait, light theme. Used outside composition. */
        val Default = MediaContext()
    }
}

/** Reads the current window size, theme and animation settings. */
@Composable
fun rememberMediaContext(): MediaContext {
    val configuration = LocalConfiguration.current
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, dark, reducedMotion) {
        MediaContext(
            widthDp = configuration.screenWidthDp.toFloat(),
            heightDp = configuration.screenHeightDp.toFloat(),
            darkTheme = dark,
            reducedMotion = reducedMotion,
        )
    }
}

/**
 * A comma-separated media query list, e.g.
 * `(min-width: 600px) and (orientation: landscape), (prefers-color-scheme: dark)`.
 *
 * Supported features: `width`, `height` (with `min-`/`max-` prefixes or range syntax
 * like `(width >= 600px)`), `orientation`, `aspect-ratio` (min/max), `prefers-color-scheme`,
 * `prefers-reduced-motion`. Media types `all` and `screen` match; others (`print`) don't.
 */
@Immutable
data class MediaQueryList(val queries: List<MediaQuery>) {
    fun matches(context: MediaContext): Boolean = queries.isEmpty() || queries.any { it.matches(context) }

    companion object {
        fun parse(text: String): MediaQueryList =
            MediaQueryList(splitTopLevel(text, ',').map { MediaQuery.parse(it.trim()) })
    }
}

@Immutable
data class MediaQuery(
    val negated: Boolean,
    val type: String?,
    val features: List<MediaFeature>,
    /** `true` when the query couldn't be understood; it then never matches (CSS: "not all"). */
    val invalid: Boolean = false,
) {
    fun matches(context: MediaContext): Boolean {
        if (invalid) return false
        val typeMatches = type == null || type == "all" || type == "screen"
        return (typeMatches && features.all { it.matches(context) }) != negated
    }

    companion object {
        private val featureRegex = Regex("""\(([^()]*)\)""")

        fun parse(text: String): MediaQuery {
            val lower = text.lowercase()
            val features = featureRegex.findAll(lower).map { MediaFeature.parse(it.groupValues[1]) }.toList()
            if (features.any { it == null }) return MediaQuery(false, null, emptyList(), invalid = true)
            val words = featureRegex.replace(lower, " ").split(Regex("\\s+")).filter { it.isNotEmpty() && it != "and" }
            val negated = words.firstOrNull() == "not"
            val type = words.firstOrNull { it != "not" && it != "only" }
            return MediaQuery(negated, type, features.filterNotNull())
        }
    }
}

/** One `(feature: value)` or `(feature op value)` test. */
@Immutable
data class MediaFeature(val name: String, val op: String, val value: String) {

    fun matches(context: MediaContext): Boolean = when (name) {
        "width" -> compare(context.widthDp, parseMediaLength(value))
        "height" -> compare(context.heightDp, parseMediaLength(value))
        "aspect-ratio" -> compare(context.widthDp / context.heightDp, parseRatio(value))
        "orientation" -> (if (context.widthDp > context.heightDp) "landscape" else "portrait") == value
        "prefers-color-scheme" -> (if (context.darkTheme) "dark" else "light") == value
        "prefers-reduced-motion" -> (if (context.reducedMotion) "reduce" else "no-preference") == value
        else -> false
    }

    private fun compare(actual: Float, expected: Float?): Boolean {
        if (expected == null) return false
        return when (op) {
            ">=" -> actual >= expected
            "<=" -> actual <= expected
            ">" -> actual > expected
            "<" -> actual < expected
            else -> actual == expected
        }
    }

    companion object {
        private val rangeRegex = Regex("""^\s*([^<>=\s]+)\s*(<=|>=|<|>|=)\s*([^<>=\s]+)\s*$""")
        private val flipped = mapOf(">=" to "<=", "<=" to ">=", ">" to "<", "<" to ">", "=" to "=")
        private val rangeFeatures = setOf("width", "height", "aspect-ratio")

        fun parse(text: String): MediaFeature? {
            rangeRegex.matchEntire(text)?.let { m ->
                val (left, op, right) = m.destructured
                return when {
                    left in rangeFeatures -> MediaFeature(left, op, right)
                    right in rangeFeatures -> MediaFeature(right, flipped.getValue(op), left)
                    else -> null
                }
            }
            val name = text.substringBefore(':').trim()
            val value = text.substringAfter(':', "").trim()
            return when {
                name.startsWith("min-") && name.removePrefix("min-") in rangeFeatures ->
                    MediaFeature(name.removePrefix("min-"), ">=", value)
                name.startsWith("max-") && name.removePrefix("max-") in rangeFeatures ->
                    MediaFeature(name.removePrefix("max-"), "<=", value)
                name in rangeFeatures || name in setOf("orientation", "prefers-color-scheme", "prefers-reduced-motion") ->
                    MediaFeature(name, "=", value)
                else -> null
            }
        }

        private fun parseMediaLength(value: String): Float? = when (val length = parseLength(value)) {
            is CssLength.Fixed -> length.dp.value
            else -> null
        }

        private fun parseRatio(value: String): Float? {
            val parts = value.split('/').map { it.trim().toFloatOrNull() ?: return null }
            return when (parts.size) {
                1 -> parts[0]
                2 -> if (parts[1] != 0f) parts[0] / parts[1] else null
                else -> null
            }
        }
    }
}
