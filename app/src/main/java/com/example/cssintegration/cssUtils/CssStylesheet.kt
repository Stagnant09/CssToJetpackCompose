package com.example.cssintegration.cssUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** A rule: one selector plus its declarations, remembering source [order] and enclosing `@media`. */
@Immutable
data class CssRule(
    val selector: CssSelector,
    val declarations: List<CssDeclaration>,
    val order: Int,
    val media: List<MediaQueryList> = emptyList(),
    val selectorText: String = "",
) {
    fun mediaMatches(context: MediaContext): Boolean = media.all { it.matches(context) }
}

private val motionProperty = Regex("^(transition|animation)")

/**
 * A parsed stylesheet. Resolve the style for an element with [resolve], which applies
 * the CSS cascade: matching rules sorted by specificity then source order, followed by
 * the element's inline CSS, with `!important` taking precedence. Custom properties,
 * `var()`, `calc()`, `@media` and `@keyframes` are resolved along the way.
 *
 * ```kotlin
 * val sheet = CssStylesheet.parse("""
 *     :root        { --accent: #E91E63; }
 *     .card        { padding: 16px; border-radius: 12px; background: #fff; }
 *     .card:hover  { border: 2px solid var(--accent); }
 *     .card > text { font-size: 20px; }
 *     @media (min-width: 600px) { .card { padding: 24px; } }
 * """)
 * val style = sheet.resolve(element = "div", className = "card")
 * ```
 */
@Immutable
class CssStylesheet(
    val rules: List<CssRule>,
    val keyframes: Map<String, CssKeyframes> = emptyMap(),
) {
    private data class ResolveKey(
        val info: ElementInfo,
        val ancestors: List<ElementInfo>,
        val variables: Map<String, String>,
        val media: MediaContext,
        val inline: String,
    )

    private val cache = object : LinkedHashMap<ResolveKey, StyleResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, StyleResult>?) = size > 512
    }

    /** Declarations from every rule matching the element, lowest priority first. */
    fun declarationsFor(
        info: ElementInfo,
        ancestors: List<ElementInfo> = emptyList(),
        media: MediaContext = MediaContext.Default,
    ): List<CssDeclaration> {
        if (rules.isEmpty()) return emptyList()
        return rules
            .filter { it.mediaMatches(media) && it.selector.matches(info, ancestors) }
            .sortedWith(compareBy({ it.selector.specificity }, { it.order }))
            .flatMap { it.declarations }
    }

    /**
     * Resolves the full [StyleResult] for an element.
     *
     * @param ancestors enclosing elements, outermost first (for `a b` / `a > b`).
     * @param inheritedVariables custom properties inherited from the parent.
     * @param inline the element's own CSS, applied after all rules.
     */
    fun resolve(
        info: ElementInfo,
        ancestors: List<ElementInfo> = emptyList(),
        inheritedVariables: Map<String, String> = emptyMap(),
        media: MediaContext = MediaContext.Default,
        inline: String = "",
    ): StyleResult {
        val key = ResolveKey(info, ancestors, inheritedVariables, media, inline)
        synchronized(cache) { cache[key] }?.let { return it }
        val result = computeStyle(info, ancestors, inheritedVariables, media, inline)
        synchronized(cache) { cache[key] = result }
        return result
    }

    /** Convenience overload taking a space-separated [className]. */
    fun resolve(
        element: String,
        className: String = "",
        id: String = "",
        inline: String = "",
        states: Set<CssState> = emptySet(),
        index: CssIndex? = null,
        ancestors: List<ElementInfo> = emptyList(),
        variables: Map<String, String> = emptyMap(),
        media: MediaContext = MediaContext.Default,
    ): StyleResult = resolve(
        info = ElementInfo(element, parseClassNames(className), id.trim(), states, index),
        ancestors = ancestors,
        inheritedVariables = variables,
        media = media,
        inline = inline,
    )

    private fun computeStyle(
        info: ElementInfo,
        ancestors: List<ElementInfo>,
        inheritedVariables: Map<String, String>,
        media: MediaContext,
        inline: String,
    ): StyleResult {
        val declarations = declarationsFor(info, ancestors, media) + parseDeclarations(inline)
        if (declarations.isEmpty()) return StyleResult(variables = inheritedVariables)
        val cascaded = cascade(declarations)
        val own = cascaded.filterKeys(::isCustomProperty)
        val variables = if (own.isEmpty()) inheritedVariables else inheritedVariables + own
        val computed = computeValues(cascaded, variables)
        val base = buildStyle(computed)
        val animations = resolveAnimations(base.animationRefs, keyframes, computed, base, variables)
        return base.copy(variables = variables, animations = animations)
    }

    /**
     * The interaction states (`:hover`, `:active`, …) any rule could react to for this
     * element — used to decide which pointer/focus listeners an element needs.
     */
    fun relevantStates(info: ElementInfo, media: MediaContext = MediaContext.Default): Set<CssState> {
        val states = mutableSetOf<CssState>()
        for (rule in rules) {
            if (!rule.mediaMatches(media)) continue
            for (part in rule.selector.parts) {
                val partStates = part.states
                if (partStates.isNotEmpty() && part.matches(info, ignoreStates = true)) states += partStates
            }
        }
        return states
    }

    /**
     * `true` if a rule matching this element in *any* interaction state (or its inline CSS)
     * declares a transition or animation. Stable across state changes.
     */
    fun mayAnimate(
        info: ElementInfo,
        ancestors: List<ElementInfo>,
        media: MediaContext,
        inline: String,
    ): Boolean {
        if (inline.contains("transition", ignoreCase = true) || inline.contains("animation", ignoreCase = true)) return true
        return rules.any { rule ->
            rule.mediaMatches(media) &&
                rule.declarations.any { motionProperty.containsMatchIn(it.property) } &&
                rule.selector.matches(info, ancestors, ignoreStates = true)
        }
    }

    /** Custom properties declared on `:root`. */
    fun rootVariables(media: MediaContext = MediaContext.Default): Map<String, String> =
        resolve(ElementInfo.Root, media = media).variables

    /** Combines two stylesheets; rules in [other] come later in source order. */
    operator fun plus(other: CssStylesheet): CssStylesheet {
        if (other.rules.isEmpty() && other.keyframes.isEmpty()) return this
        if (rules.isEmpty() && keyframes.isEmpty()) return other
        val offset = (rules.maxOfOrNull { it.order } ?: -1) + 1
        return CssStylesheet(
            rules = rules + other.rules.map { it.copy(order = it.order + offset) },
            keyframes = keyframes + other.keyframes,
        )
    }

    companion object {
        val Empty = CssStylesheet(emptyList())

        /**
         * Parses a stylesheet. Supports selector lists, comments, `@media` (nestable),
         * `@supports` (assumed true), `@keyframes`, and `:root` custom properties.
         * Other at-rules and unsupported selectors are skipped.
         */
        fun parse(css: String): CssStylesheet {
            val rules = mutableListOf<CssRule>()
            val keyframes = LinkedHashMap<String, CssKeyframes>()
            parseBlock(stripComments(css), emptyList(), rules, keyframes)
            return CssStylesheet(rules, keyframes)
        }

        private fun parseBlock(
            css: String,
            media: List<MediaQueryList>,
            rules: MutableList<CssRule>,
            keyframes: MutableMap<String, CssKeyframes>,
        ) {
            for ((prelude, body) in splitRules(css)) {
                val lower = prelude.lowercase()
                when {
                    lower.startsWith("@media") ->
                        parseBlock(body, media + MediaQueryList.parse(prelude.substring(6)), rules, keyframes)
                    lower.startsWith("@supports") -> parseBlock(body, media, rules, keyframes)
                    lower.startsWith("@keyframes") || lower.startsWith("@-webkit-keyframes") -> {
                        val name = prelude.substringAfter(' ').trim().trim('"', '\'')
                        val frames = splitRules(body).mapNotNull { (selector, declarations) ->
                            val offsets = selector.split(',').mapNotNull(::parseKeyframeOffset)
                            if (offsets.isEmpty()) null else KeyframeRule(offsets, parseDeclarations(declarations))
                        }
                        keyframes[name] = CssKeyframes(name, frames)
                    }
                    lower.startsWith("@") -> Unit // @font-face, @import, @page … not supported
                    else -> {
                        val declarations = parseDeclarations(body)
                        for (selectorText in splitTopLevel(prelude, ',')) {
                            val selector = CssSelector.parse(selectorText) ?: continue
                            rules += CssRule(selector, declarations, rules.size, media, selectorText.trim())
                        }
                    }
                }
            }
        }

        private fun parseKeyframeOffset(text: String): Float? = when (val t = text.trim().lowercase()) {
            "from" -> 0f
            "to" -> 1f
            else -> splitNumber(t)?.takeIf { it.second == "%" }?.first?.div(100f)?.coerceIn(0f, 1f)
        }

        /** Splits CSS into `(prelude, body)` pairs at the top level, keeping nested blocks intact. */
        internal fun splitRules(css: String): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            var i = 0
            while (i < css.length) {
                val open = css.indexOf('{', i)
                if (open < 0) break
                // Drop block-less statements such as `@import url(x);`
                val prelude = css.substring(i, open).substringAfterLast(';').trim()
                var depth = 1
                var j = open + 1
                while (j < css.length && depth > 0) {
                    when (css[j]) {
                        '{' -> depth++
                        '}' -> depth--
                    }
                    j++
                }
                result += prelude to css.substring(open + 1, (j - 1).coerceAtLeast(open + 1))
                i = j
            }
            return result
        }
    }
}

/**
 * Parses a minimal stylesheet into a map of selector text → [StyleResult].
 * Prefer [CssStylesheet] for cascade-aware lookup.
 *
 * ```kotlin
 * val styles = parseCssStylesheet(".title { color: #212121; font-size: 24; font-weight: bold; }")
 * val titleStyle = styles[".title"]?.toTextStyle()
 * ```
 */
fun parseCssStylesheet(css: String): Map<String, StyleResult> {
    val sheet = CssStylesheet.parse(css)
    return sheet.rules
        .groupBy { it.selectorText }
        .mapValues { (_, rules) ->
            val cascaded = cascade(rules.flatMap { it.declarations })
            buildStyle(computeValues(cascaded, cascaded.filterKeys(::isCustomProperty)))
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composition
// ─────────────────────────────────────────────────────────────────────────────

/** The stylesheet that `Div` and `CssText` resolve against. */
val LocalCssStylesheet = staticCompositionLocalOf { CssStylesheet.Empty }

/** Enclosing elements (outermost first), provided by each `Div` for combinator matching. */
val LocalCssAncestors = compositionLocalOf { emptyList<ElementInfo>() }

/** Custom properties inherited from enclosing elements. */
val LocalCssVariables = compositionLocalOf { emptyMap<String, String>() }

/**
 * Makes the rules in [css] available to every `Div` / `CssText` in [content].
 * Nested providers extend (rather than replace) the enclosing stylesheet, and `:root`
 * custom properties become available to `var()` everywhere inside.
 */
@Composable
fun CssStylesheetProvider(css: String, content: @Composable () -> Unit) {
    val parent = LocalCssStylesheet.current
    val sheet = remember(css, parent) { parent + CssStylesheet.parse(css) }
    val media = rememberMediaContext()
    val inherited = LocalCssVariables.current
    val variables = remember(sheet, media, inherited) { inherited + sheet.rootVariables(media) }
    CompositionLocalProvider(
        LocalCssStylesheet provides sheet,
        LocalCssVariables provides variables,
        content = content,
    )
}

/**
 * Resolves the (static) style of an element against the current stylesheet, ancestors,
 * variables and media, plus its [inline] CSS. For interaction states and motion use
 * `Div` / `CssText`, which build on this.
 */
@Composable
fun rememberCss(element: String, className: String = "", id: String = "", inline: String = ""): StyleResult {
    val sheet = LocalCssStylesheet.current
    val ancestors = LocalCssAncestors.current
    val variables = LocalCssVariables.current
    val media = rememberMediaContext()
    return remember(sheet, element, className, id, inline, ancestors, variables, media) {
        sheet.resolve(ElementInfo(element, parseClassNames(className), id.trim()), ancestors, variables, media, inline)
    }
}
