package io.github.stagnant09.csscompose.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSNode

/**
 * Validates every `@CssStyle("…")` at compile time:
 * - malformed declarations (missing `:` or empty value) → error
 * - properties the runtime parser doesn't understand → warning
 *
 * Keep [KNOWN_PROPERTIES] in sync with `SUPPORTED_CSS_PROPERTIES` in css-integration's style/CssParser.kt
 * (CssMotionAndLayoutTest checks this).
 */
class CssStyleProcessor(
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(CSS_STYLE_FQN).forEach { symbol ->
            symbol.annotations
                .filter { it.shortName.asString() == "CssStyle" }
                .forEach { annotation -> validate(annotation, symbol) }
        }
        return emptyList()
    }

    private fun validate(annotation: KSAnnotation, symbol: KSNode) {
        val css = annotation.arguments
            .firstOrNull { it.name?.asString() == "value" }
            ?.value as? String
            ?: return

        val cleaned = css.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        splitDeclarations(cleaned).forEach { raw ->
            val declaration = raw.trim()
            if (declaration.isEmpty()) return@forEach
            val colon = declaration.indexOf(':')
            if (colon <= 0 || declaration.substring(colon + 1).isBlank()) {
                logger.error("@CssStyle: malformed declaration '$declaration' (expected 'property: value').", symbol)
                return@forEach
            }
            val property = declaration.substring(0, colon).trim().lowercase()
            if (property !in KNOWN_PROPERTIES && !property.startsWith("--")) {
                val hint = suggestion(property)?.let { " Did you mean '$it'?" }.orEmpty()
                logger.warn("@CssStyle: unsupported CSS property '$property' will be ignored.$hint", symbol)
            }
        }
    }

    /** Splits on `;` that aren't inside parentheses or quotes. */
    private fun splitDeclarations(css: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (ch in css) {
            when {
                quote != null -> { if (ch == quote) quote = null; current.append(ch) }
                ch == '"' || ch == '\'' -> { quote = ch; current.append(ch) }
                ch == '(' -> { depth++; current.append(ch) }
                ch == ')' -> { depth--; current.append(ch) }
                ch == ';' && depth == 0 -> { parts += current.toString(); current.clear() }
                else -> current.append(ch)
            }
        }
        parts += current.toString()
        return parts
    }

    /** Closest known property within edit distance 2, if any. */
    private fun suggestion(property: String): String? =
        KNOWN_PROPERTIES
            .map { it to levenshtein(property, it) }
            .filter { it.second <= 2 }
            .minByOrNull { it.second }
            ?.first

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    previous + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                previous = temp
            }
        }
        return dp[b.length]
    }

    companion object {
        const val CSS_STYLE_FQN = "io.github.stagnant09.csscompose.CssStyle"

        val KNOWN_PROPERTIES: Set<String> = setOf(
            // Typography
            "color", "font", "font-size", "font-weight", "font-style", "font-family", "text-align",
            "letter-spacing", "line-height", "text-decoration", "text-decoration-line", "text-transform",
            "text-shadow", "white-space", "text-overflow", "line-clamp", "-webkit-line-clamp",
            // Box model
            "margin", "padding", "width", "height", "min-width", "max-width", "min-height", "max-height",
            "aspect-ratio", "box-sizing", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding-top", "padding-right", "padding-bottom", "padding-left",
            // Decoration
            "background", "background-color", "background-image", "border", "border-width", "border-style",
            "border-color", "border-radius", "box-shadow", "opacity", "visibility", "overflow", "overflow-x",
            "overflow-y", "z-index", "transform", "filter", "border-top", "border-top-width",
            "border-top-style", "border-top-color", "border-right", "border-right-width",
            "border-right-style", "border-right-color", "border-bottom", "border-bottom-width",
            "border-bottom-style", "border-bottom-color", "border-left", "border-left-width",
            "border-left-style", "border-left-color", "border-top-left-radius", "border-top-right-radius",
            "border-bottom-right-radius", "border-bottom-left-radius",
            "outline", "outline-width", "outline-style", "outline-color", "outline-offset",
            // Layout
            "display", "flex-direction", "flex-wrap", "flex-flow", "justify-content", "align-items",
            "align-content", "gap", "row-gap", "column-gap", "grid-template-columns",
            // Flex items & positioning
            "flex", "flex-grow", "flex-shrink", "flex-basis", "align-self", "order", "position", "top",
            "right", "bottom", "left", "inset",
            // Motion
            "transition", "transition-property", "transition-duration", "transition-timing-function",
            "transition-delay", "animation", "animation-name", "animation-duration",
            "animation-timing-function", "animation-delay", "animation-iteration-count",
            "animation-direction", "animation-fill-mode", "animation-play-state",
        )
    }
}
