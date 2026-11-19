package io.github.stagnant09.csscompose.style

import io.github.stagnant09.csscompose.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// Computed values: custom properties, var(), calc() and CSS-wide keywords.
// Runs after the cascade and before buildStyle().
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_VAR_DEPTH = 16
private val cssWideKeywords = setOf("initial", "inherit", "unset", "revert", "revert-layer")

/** `true` for custom property names (`--brand-color`). */
internal fun isCustomProperty(property: String) = property.startsWith("--")

/**
 * Turns cascaded declarations into computed values:
 * - custom properties are dropped from the result (they are returned via [variables]),
 * - `var(--x, fallback)` references are substituted from [variables],
 * - `calc()`, `min()`, `max()` and `clamp()` are evaluated,
 * - CSS-wide keywords (`initial`, `inherit`, `unset`, `revert`) reset the property.
 *   Text properties still inherit through `LocalTextStyle`, so `inherit` behaves as
 *   expected for them; for box properties it behaves like `initial`.
 *
 * Declarations whose `var()` cannot be resolved are dropped ("invalid at computed-value time").
 */
fun computeValues(declarations: Map<String, String>, variables: Map<String, String>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((property, raw) in declarations) {
        if (isCustomProperty(property)) continue
        if (raw.trim().lowercase() in cssWideKeywords) continue
        val value = computeValue(raw, variables) ?: continue
        result[property] = value
    }
    return result
}

/** Substitutes `var()` and evaluates math functions in a single value. Returns `null` if invalid. */
fun computeValue(value: String, variables: Map<String, String>): String? {
    val substituted = substituteVars(value, variables, 0) ?: return null
    return evaluateMathFunctions(substituted)
}

private fun substituteVars(value: String, variables: Map<String, String>, depth: Int): String? {
    if (depth > MAX_VAR_DEPTH) return null // reference cycle
    if (!value.contains("var(", ignoreCase = true)) return value
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val start = value.indexOf("var(", i, ignoreCase = true)
        if (start < 0) {
            out.append(value, i, value.length)
            break
        }
        out.append(value, i, start)
        val close = matchingParen(value, start + 3) ?: return null
        val inner = value.substring(start + 4, close)
        val comma = splitTopLevel(inner, ',')
        val name = comma[0].trim()
        val fallback = if (comma.size > 1) inner.substring(inner.indexOf(',') + 1).trim() else null
        val replacement = variables[name]?.let { substituteVars(it, variables, depth + 1) }
            ?: fallback?.let { substituteVars(it, variables, depth + 1) }
            ?: return null
        out.append(replacement)
        i = close + 1
    }
    return out.toString().trim()
}

/** Index of the `)` matching the `(` at [openIndex]. */
private fun matchingParen(text: String, openIndex: Int): Int? {
    var depth = 0
    for (j in openIndex until text.length) {
        when (text[j]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return j
            }
        }
    }
    return null
}

private val mathFunctionRegex = Regex("""(?<![\w-])(calc|min|max|clamp)\(""", RegexOption.IGNORE_CASE)

/** Replaces every top-level `calc()/min()/max()/clamp()` with its computed value. */
internal fun evaluateMathFunctions(value: String): String? {
    if (!mathFunctionRegex.containsMatchIn(value)) return value
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val match = mathFunctionRegex.find(value, i)
        if (match == null) {
            out.append(value, i, value.length)
            break
        }
        out.append(value, i, match.range.first)
        val open = match.range.last
        val close = matchingParen(value, open) ?: return null
        val result = MathParser(value.substring(match.range.first, close + 1)).parseFunctionOrNull() ?: return null
        out.append(result.format())
        i = close + 1
    }
    return out.toString()
}

/** A typed numeric value inside calc(). Lengths are normalised to px (= dp). */
internal data class MathValue(val value: Float, val kind: Kind) {
    enum class Kind { Number, Length, Percent }

    fun format(): String {
        val number = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
        return when (kind) {
            Kind.Number -> number
            Kind.Length -> "${number}px"
            Kind.Percent -> "$number%"
        }
    }
}

/**
 * Recursive-descent evaluator for CSS math functions. Supports `+ - * /`, parentheses,
 * nested `calc/min/max/clamp`, and the units `px dp sp rem em %`. Mixing lengths with
 * percentages can't be resolved without layout information, so it yields `null`.
 */
internal class MathParser(private val text: String) {
    private var pos = 0

    fun parseFunctionOrNull(): MathValue? = try {
        val v = parseFunction()
        skipSpaces()
        if (pos == text.length) v else null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseFunction(): MathValue {
        skipSpaces()
        val nameStart = pos
        while (pos < text.length && text[pos].isLetter()) pos++
        val name = text.substring(nameStart, pos).lowercase()
        expect('(')
        val args = mutableListOf(parseSum())
        while (peek() == ',') {
            pos++
            args += parseSum()
        }
        expect(')')
        return when (name) {
            "calc" -> args.single()
            "min" -> args.reduce { a, b -> combineSame(a, b) { x, y -> min(x, y) } }
            "max" -> args.reduce { a, b -> combineSame(a, b) { x, y -> max(x, y) } }
            "clamp" -> {
                require(args.size == 3)
                val lower = combineSame(args[1], args[0]) { x, y -> max(x, y) }
                combineSame(lower, args[2]) { x, y -> min(x, y) }
            }
            else -> throw IllegalArgumentException("unknown function $name")
        }
    }

    private fun parseSum(): MathValue {
        var left = parseProduct()
        while (true) {
            skipSpaces()
            val op = peek()
            if (op != '+' && op != '-') return left
            pos++
            val right = parseProduct()
            left = combineSame(left, right) { a, b -> if (op == '+') a + b else a - b }
        }
    }

    private fun parseProduct(): MathValue {
        var left = parseUnary()
        while (true) {
            skipSpaces()
            val op = peek()
            if (op != '*' && op != '/') return left
            pos++
            val right = parseUnary()
            left = if (op == '*') {
                when {
                    left.kind == MathValue.Kind.Number -> MathValue(left.value * right.value, right.kind)
                    right.kind == MathValue.Kind.Number -> MathValue(left.value * right.value, left.kind)
                    else -> throw IllegalArgumentException("cannot multiply two dimensions")
                }
            } else {
                require(right.kind == MathValue.Kind.Number && right.value != 0f)
                MathValue(left.value / right.value, left.kind)
            }
        }
    }

    private fun parseUnary(): MathValue {
        skipSpaces()
        val c = peek() ?: throw IllegalArgumentException("unexpected end")
        if (c == '(') {
            pos++
            val v = parseSum()
            expect(')')
            return v
        }
        if (c.isLetter()) return parseFunction()
        // Number with optional sign and unit
        val start = pos
        if (c == '-' || c == '+') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
        while (pos < text.length && (text[pos].isLetter() || text[pos] == '%')) pos++
        val (number, unit) = splitNumber(text.substring(start, pos))
            ?: throw IllegalArgumentException("bad number")
        return when (unit) {
            "" -> MathValue(number, MathValue.Kind.Number)
            "px", "dp", "sp" -> MathValue(number, MathValue.Kind.Length)
            "rem", "em" -> MathValue(number * 16f, MathValue.Kind.Length)
            "%" -> MathValue(number, MathValue.Kind.Percent)
            else -> throw IllegalArgumentException("unsupported unit $unit")
        }
    }

    private fun combineSame(a: MathValue, b: MathValue, op: (Float, Float) -> Float): MathValue {
        require(a.kind == b.kind) { "mixed units" }
        return MathValue(op(a.value, b.value), a.kind)
    }

    private fun skipSpaces() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? {
        skipSpaces()
        return text.getOrNull(pos)
    }

    private fun expect(c: Char) {
        require(peek() == c) { "expected $c" }
        pos++
    }
}
