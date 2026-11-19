package io.github.stagnant09.csscompose

// ─────────────────────────────────────────────────────────────────────────────
// Helpers for the web target. They live in commonMain (pure string handling) so
// they're unit-tested on the JVM.
// ─────────────────────────────────────────────────────────────────────────────

/** Marks disabled elements on the web, since `:disabled` never matches a `<div>`. */
internal const val DisabledAttribute = "aria-disabled"

/**
 * Prepended to every stylesheet on the web so the browser behaves like the Android side:
 * - boxes size as `border-box`, as everywhere in this library;
 * - `text` (from [CssText]) stacks like a block. `:where()` keeps its specificity at 0, so
 *   any rule of yours wins.
 * - disabled elements ignore the pointer, so `:active` / `:hover` don't fire on them.
 */
internal val BrowserBaseCss = """
    *, *::before, *::after { box-sizing: border-box; }
    :where(text) { display: block; }
    :where([$DisabledAttribute="true"]) { pointer-events: none; }
""".trimIndent()

private val disabledPseudo = Regex(""":disabled(?![\w-])""")
private val enabledPseudo = Regex(""":enabled(?![\w-])""")

/**
 * Adapts a stylesheet written for this library to the browser:
 * `:disabled` / `:enabled` only match form controls in browsers, so they're rewritten to the
 * attribute `Div` / `CssText` set when `enabled = false`. Everything else is standard CSS.
 */
internal fun adaptForBrowser(css: String): String =
    BrowserBaseCss + "\n" + css
        .replace(disabledPseudo, """[$DisabledAttribute="true"]""")
        .replace(enabledPseudo, """:not([$DisabledAttribute="true"])""")

/**
 * Splits inline CSS (`"color: red; padding: 4px"`) into `(property, value)` pairs, ignoring
 * semicolons inside parentheses (`url(…)`) and quotes. `!important` is dropped: it means
 * nothing on an inline style that nothing else overrides.
 */
internal fun splitInlineDeclarations(css: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val current = StringBuilder()
    var depth = 0
    var quote: Char? = null
    fun flush() {
        val declaration = current.toString()
        current.clear()
        val colon = declaration.indexOf(':')
        if (colon <= 0) return
        val property = declaration.substring(0, colon).trim()
        val value = declaration.substring(colon + 1).replace("!important", "", ignoreCase = true).trim()
        if (property.isNotEmpty() && value.isNotEmpty()) {
            // Custom properties are case-sensitive; everything else isn't
            result += (if (property.startsWith("--")) property else property.lowercase()) to value
        }
    }
    for (ch in css) {
        when {
            quote != null -> { if (ch == quote) quote = null; current.append(ch) }
            ch == '"' || ch == '\'' -> { quote = ch; current.append(ch) }
            ch == '(' -> { depth++; current.append(ch) }
            ch == ')' -> { depth--; current.append(ch) }
            ch == ';' && depth == 0 -> flush()
            else -> current.append(ch)
        }
    }
    flush()
    return result
}

/** Splits a `className` string into the individual classes, as the `class` attribute would. */
internal fun splitClassNames(className: String): List<String> =
    className.split(' ', '\t', '\n').filter { it.isNotEmpty() }
