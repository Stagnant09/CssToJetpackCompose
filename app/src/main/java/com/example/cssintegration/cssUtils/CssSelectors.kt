package com.example.cssintegration.cssUtils

import androidx.compose.runtime.Immutable

// ─────────────────────────────────────────────────────────────────────────────
// Elements as the selector engine sees them
// ─────────────────────────────────────────────────────────────────────────────

/** Interaction states that pseudo-classes can match. */
enum class CssState { Hover, Active, Focus, Disabled }

/**
 * An element's position among its siblings, for `:first-child`, `:nth-child()` …
 *
 * Compose can't discover sibling positions on its own, so pass it explicitly when you
 * render lists: `items.forEachIndexed { i, item -> Div(index = CssIndex(i, items.size)) { … } }`.
 *
 * @param position 0-based index among siblings.
 * @param count total number of siblings.
 */
@Immutable
data class CssIndex(val position: Int, val count: Int)

/** Everything selectors can match on for one element. */
@Immutable
data class ElementInfo(
    val element: String,
    val classes: Set<String> = emptySet(),
    val id: String = "",
    val states: Set<CssState> = emptySet(),
    val index: CssIndex? = null,
) {
    companion object {
        /** The synthetic element `:root` rules apply to (custom properties for the whole tree). */
        val Root = ElementInfo(element = ":root")
    }
}

internal fun parseClassNames(className: String): Set<String> =
    className.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

// ─────────────────────────────────────────────────────────────────────────────
// Pseudo-classes
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
sealed interface PseudoClass {
    data class State(val state: CssState, val negated: Boolean = false) : PseudoClass

    /** `:nth-child(an+b)`; `fromEnd` for `:nth-last-child`. `:first-child` is `(0, 1)`. */
    data class Nth(val a: Int, val b: Int, val fromEnd: Boolean = false) : PseudoClass

    data object OnlyChild : PseudoClass
    data object Root : PseudoClass

    fun matches(info: ElementInfo, ignoreStates: Boolean): Boolean = when (this) {
        is State -> ignoreStates || ((state in info.states) != negated)
        is Nth -> info.index?.let { index ->
            val position = if (fromEnd) index.count - index.position else index.position + 1
            nthMatches(a, b, position)
        } ?: false
        OnlyChild -> info.index?.count == 1
        Root -> info.element == ElementInfo.Root.element
    }

    companion object {
        fun parse(name: String, argument: String?): PseudoClass? = when (name) {
            "hover" -> State(CssState.Hover)
            "active" -> State(CssState.Active)
            "focus", "focus-visible" -> State(CssState.Focus)
            "disabled" -> State(CssState.Disabled)
            "enabled" -> State(CssState.Disabled, negated = true)
            "first-child" -> Nth(0, 1)
            "last-child" -> Nth(0, 1, fromEnd = true)
            "only-child" -> OnlyChild
            "nth-child" -> argument?.let(::parseNth)?.let { (a, b) -> Nth(a, b) }
            "nth-last-child" -> argument?.let(::parseNth)?.let { (a, b) -> Nth(a, b, fromEnd = true) }
            "root" -> Root
            else -> null
        }
    }
}

/** `true` if `position` (1-based) is `a·n + b` for some n ≥ 0. */
internal fun nthMatches(a: Int, b: Int, position: Int): Boolean {
    if (a == 0) return position == b
    val diff = position - b
    return diff % a == 0 && diff / a >= 0
}

/** Parses `odd`, `even`, `3`, `2n+1`, `-n+3`, `n`. */
internal fun parseNth(text: String): Pair<Int, Int>? {
    val t = text.replace(" ", "").lowercase()
    return when {
        t == "odd" -> 2 to 1
        t == "even" -> 2 to 0
        'n' !in t -> t.toIntOrNull()?.let { 0 to it }
        else -> {
            val aPart = t.substringBefore('n')
            val a = when (aPart) {
                "", "+" -> 1
                "-" -> -1
                else -> aPart.toIntOrNull() ?: return null
            }
            val bPart = t.substringAfter('n')
            val b = if (bPart.isEmpty()) 0 else bPart.toIntOrNull() ?: return null
            a to b
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Selectors
// ─────────────────────────────────────────────────────────────────────────────

/** One compound selector, e.g. `div.card.active:hover` or `#hero`. */
@Immutable
data class CompoundSelector(
    val element: String? = null,
    val id: String? = null,
    val classes: Set<String> = emptySet(),
    val pseudos: List<PseudoClass> = emptyList(),
) {
    /** (ids, classes + pseudo-classes, elements) packed into one comparable number. */
    val specificity: Int
        get() = (if (id != null) 1_000_000 else 0) + (classes.size + pseudos.size) * 1_000 +
            (if (element != null) 1 else 0)

    val states: Set<CssState>
        get() = pseudos.filterIsInstance<PseudoClass.State>().map { it.state }.toSet()

    fun matches(info: ElementInfo, ignoreStates: Boolean = false): Boolean =
        (element == null || element.equals(info.element, ignoreCase = true)) &&
            (id == null || id == info.id) &&
            info.classes.containsAll(classes) &&
            pseudos.all { it.matches(info, ignoreStates) }

    companion object {
        private val pattern = Regex("""^(\*|[a-zA-Z][\w-]*)?((?:[.#][\w-]+|:[\w-]+(?:\([^)]*\))?)*)$""")
        private val part = Regex("""([.#])([\w-]+)|:([\w-]+)(?:\(([^)]*)\))?""")

        fun parse(text: String): CompoundSelector? {
            val match = pattern.matchEntire(text.trim()) ?: return null
            val elementText = match.groupValues[1]
            val element = elementText.takeIf { it.isNotEmpty() && it != "*" }?.lowercase()
            var id: String? = null
            val classes = mutableSetOf<String>()
            val pseudos = mutableListOf<PseudoClass>()
            for (p in part.findAll(match.groupValues[2])) {
                when {
                    p.groupValues[1] == "#" -> id = p.groupValues[2]
                    p.groupValues[1] == "." -> classes += p.groupValues[2]
                    else -> pseudos += PseudoClass.parse(
                        p.groupValues[3].lowercase(),
                        p.groupValues[4].takeIf { it.isNotEmpty() },
                    ) ?: return null
                }
            }
            if (element == null && id == null && classes.isEmpty() && pseudos.isEmpty() && elementText != "*") return null
            return CompoundSelector(element, id, classes, pseudos)
        }
    }
}

enum class Combinator { Descendant, Child }

/**
 * A complex selector: compound selectors joined by descendant (` `) or child (`>`)
 * combinators, e.g. `.card > .title:hover` or `#main .item`.
 *
 * Sibling combinators (`+`, `~`), attribute selectors and pseudo-elements are not
 * supported; rules using them are skipped when the stylesheet is parsed.
 */
@Immutable
data class CssSelector(
    val parts: List<CompoundSelector>,
    /** `combinators[i]` joins `parts[i]` and `parts[i + 1]`. */
    val combinators: List<Combinator> = emptyList(),
) {
    /** Convenience constructor for a single compound selector. */
    constructor(
        element: String? = null,
        id: String? = null,
        classes: Set<String> = emptySet(),
    ) : this(listOf(CompoundSelector(element, id, classes)))

    val subject: CompoundSelector get() = parts.last()

    val specificity: Int get() = parts.sumOf { it.specificity }

    /**
     * Matches [info] whose ancestors (outermost first) are [ancestors].
     * With [ignoreStates] all state pseudo-classes are treated as matching.
     */
    fun matches(info: ElementInfo, ancestors: List<ElementInfo>, ignoreStates: Boolean = false): Boolean {
        if (!subject.matches(info, ignoreStates)) return false
        return matchAncestors(parts.size - 2, ancestors.size - 1, ancestors, ignoreStates)
    }

    private fun matchAncestors(part: Int, from: Int, ancestors: List<ElementInfo>, ignoreStates: Boolean): Boolean {
        if (part < 0) return true
        return when (combinators[part]) {
            Combinator.Child ->
                from >= 0 && parts[part].matches(ancestors[from], ignoreStates) &&
                    matchAncestors(part - 1, from - 1, ancestors, ignoreStates)
            Combinator.Descendant -> (from downTo 0).any { j ->
                parts[part].matches(ancestors[j], ignoreStates) &&
                    matchAncestors(part - 1, j - 1, ancestors, ignoreStates)
            }
        }
    }

    companion object {
        /** Parses a complex selector, or returns `null` if it uses unsupported syntax. */
        fun parse(text: String): CssSelector? {
            val parts = mutableListOf<CompoundSelector>()
            val combinators = mutableListOf<Combinator>()
            var pending: Combinator? = null
            val current = StringBuilder()
            var depth = 0

            fun flushCompound(): Boolean {
                if (current.isEmpty()) return true
                val compound = CompoundSelector.parse(current.toString()) ?: return false
                current.clear()
                if (parts.isNotEmpty()) combinators += pending ?: Combinator.Descendant
                pending = null
                parts += compound
                return true
            }

            for (ch in text.trim()) {
                when {
                    ch == '(' -> { depth++; current.append(ch) }
                    ch == ')' -> { depth--; current.append(ch) }
                    depth > 0 -> current.append(ch)
                    ch.isWhitespace() -> {
                        if (!flushCompound()) return null
                        if (parts.isNotEmpty() && pending == null) pending = Combinator.Descendant
                    }
                    ch == '>' -> {
                        if (!flushCompound() || parts.isEmpty()) return null
                        pending = Combinator.Child
                    }
                    ch == '+' || ch == '~' || ch == '[' || ch == ',' -> return null
                    else -> current.append(ch)
                }
            }
            if (!flushCompound() || parts.isEmpty() || combinators.size != parts.size - 1) return null
            return CssSelector(parts, combinators)
        }
    }
}
