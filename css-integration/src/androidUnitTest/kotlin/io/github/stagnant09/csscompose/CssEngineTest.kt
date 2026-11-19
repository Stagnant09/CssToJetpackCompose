package io.github.stagnant09.csscompose

import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selectors, cascade features, variables, math and media queries. */
class CssEngineTest {

    private val div = ElementInfo("div")
    private fun el(element: String = "div", classes: String = "", id: String = "", states: Set<CssState> = emptySet(), index: CssIndex? = null) =
        ElementInfo(element, classes.split(' ').filter { it.isNotEmpty() }.toSet(), id, states, index)

    // ── Selectors ───────────────────────────────────────────────────────────

    @Test
    fun `combinators parse`() {
        val selector = CssSelector.parse(".card > .body text.title")!!
        assertEquals(3, selector.parts.size)
        assertEquals(listOf(Combinator.Child, Combinator.Descendant), selector.combinators)
        assertEquals(CssSelector.parse("a>b"), CssSelector.parse("a > b"))
    }

    @Test
    fun `descendant and child matching`() {
        val card = el(classes = "card")
        val body = el(classes = "body")
        val title = el("text", classes = "title")
        assertTrue(CssSelector.parse(".card .title")!!.matches(title, listOf(card, body)))
        assertTrue(CssSelector.parse(".card > .body > .title")!!.matches(title, listOf(card, body)))
        assertFalse(CssSelector.parse(".card > .title")!!.matches(title, listOf(card, body)))
        assertFalse(CssSelector.parse(".other .title")!!.matches(title, listOf(card, body)))
        // Backtracking: `.a .b .c` where the nearest .b has no .a above it
        val a = el(classes = "a")
        val b = el(classes = "b")
        assertTrue(CssSelector.parse(".a .b .c")!!.matches(el(classes = "c"), listOf(a, b, el(classes = "b"))))
    }

    @Test
    fun `state pseudo-classes`() {
        val hover = CssSelector.parse(".btn:hover")!!
        assertFalse(hover.matches(el(classes = "btn"), emptyList()))
        assertTrue(hover.matches(el(classes = "btn", states = setOf(CssState.Hover)), emptyList()))
        assertTrue(hover.matches(el(classes = "btn"), emptyList(), ignoreStates = true))
        assertTrue(CssSelector.parse(":enabled")!!.matches(div, emptyList()))
        assertFalse(CssSelector.parse(":enabled")!!.matches(el(states = setOf(CssState.Disabled)), emptyList()))
        // Ancestor state: `.card:hover .title`
        val title = el("text", classes = "title")
        val hoveredCard = el(classes = "card", states = setOf(CssState.Hover))
        assertTrue(CssSelector.parse(".card:hover .title")!!.matches(title, listOf(hoveredCard)))
        assertFalse(CssSelector.parse(".card:hover .title")!!.matches(title, listOf(el(classes = "card"))))
    }

    @Test
    fun `structural pseudo-classes need an index`() {
        val first = CssSelector.parse(".item:first-child")!!
        assertTrue(first.matches(el(classes = "item", index = CssIndex(0, 3)), emptyList()))
        assertFalse(first.matches(el(classes = "item", index = CssIndex(1, 3)), emptyList()))
        assertFalse(first.matches(el(classes = "item"), emptyList())) // no index → never matches
        val last = CssSelector.parse(":last-child")!!
        assertTrue(last.matches(el(index = CssIndex(2, 3)), emptyList()))
        val odd = CssSelector.parse(":nth-child(odd)")!!
        assertTrue(odd.matches(el(index = CssIndex(0, 5)), emptyList()))
        assertFalse(odd.matches(el(index = CssIndex(1, 5)), emptyList()))
        val firstThree = CssSelector.parse(":nth-child(-n+3)")!!
        assertTrue(firstThree.matches(el(index = CssIndex(2, 5)), emptyList()))
        assertFalse(firstThree.matches(el(index = CssIndex(3, 5)), emptyList()))
        assertTrue(CssSelector.parse(":nth-child(3n+1)")!!.matches(el(index = CssIndex(3, 9)), emptyList()))
        assertTrue(CssSelector.parse(":only-child")!!.matches(el(index = CssIndex(0, 1)), emptyList()))
    }

    @Test
    fun `pseudo-classes count toward specificity`() {
        val sheet = CssStylesheet.parse(
            """
            .btn:hover { color: red; }
            .btn.primary { color: blue; }
            """
        )
        // Equal specificity (0,2,0): later rule wins
        assertEquals(Color.Blue, sheet.resolve("div", "btn primary", states = setOf(CssState.Hover)).color)
    }

    // ── Stylesheet features ────────────────────────────────────────────────

    @Test
    fun `hover rules apply only in that state`() {
        val sheet = CssStylesheet.parse(".btn { color: red; } .btn:hover { color: blue; }")
        assertEquals(Color.Red, sheet.resolve("div", "btn").color)
        assertEquals(Color.Blue, sheet.resolve("div", "btn", states = setOf(CssState.Hover)).color)
    }

    @Test
    fun `relevant states and motion detection`() {
        val sheet = CssStylesheet.parse(
            """
            .btn:hover { color: blue; }
            .card:active .title { color: red; }
            .fade:hover { transition: opacity 200ms; }
            """
        )
        assertEquals(setOf(CssState.Hover), sheet.relevantStates(el(classes = "btn"), MediaContext.Default))
        assertEquals(setOf(CssState.Active), sheet.relevantStates(el(classes = "card"), MediaContext.Default))
        assertTrue(sheet.relevantStates(el(classes = "title"), MediaContext.Default).isEmpty())
        // Transition declared only in a :hover rule still counts — keeps composition stable
        assertTrue(sheet.mayAnimate(el(classes = "fade"), emptyList(), MediaContext.Default, ""))
        assertFalse(sheet.mayAnimate(el(classes = "btn"), emptyList(), MediaContext.Default, ""))
        assertTrue(sheet.mayAnimate(div, emptyList(), MediaContext.Default, "transition: color 1s"))
    }

    @Test
    fun `media queries gate rules`() {
        val sheet = CssStylesheet.parse(
            """
            .card { padding: 8px; }
            @media (min-width: 600px) { .card { padding: 24px; } }
            @media (prefers-color-scheme: dark) {
                .card { color: white; }
                @media (orientation: landscape) { .card { color: yellow; } }
            }
            """
        )
        val phone = MediaContext(widthDp = 400f, heightDp = 800f)
        val tablet = MediaContext(widthDp = 800f, heightDp = 1200f)
        assertEquals(8.dp, sheet.resolve("div", "card", media = phone).box.padding.top)
        assertEquals(24.dp, sheet.resolve("div", "card", media = tablet).box.padding.top)
        assertEquals(Color.White, sheet.resolve("div", "card", media = phone.copy(darkTheme = true)).color)
        assertEquals(
            Color.Yellow,
            sheet.resolve("div", "card", media = MediaContext(900f, 400f, darkTheme = true)).color,
        )
    }

    @Test
    fun `media query syntax`() {
        val ctx = MediaContext(widthDp = 500f, heightDp = 900f)
        fun matches(q: String) = MediaQueryList.parse(q).matches(ctx)
        assertTrue(matches("(max-width: 600px)"))
        assertTrue(matches("screen and (min-width: 400px) and (orientation: portrait)"))
        assertTrue(matches("(width >= 500px)"))
        assertTrue(matches("(400px < width)"))
        assertFalse(matches("(width > 500px)"))
        assertFalse(matches("print"))
        assertTrue(matches("not print"))
        assertTrue(matches("(min-width: 900px), (orientation: portrait)"))
        assertFalse(matches("(hover: hover)")) // unknown feature never matches
        assertTrue(matches("(prefers-reduced-motion: no-preference)"))
        assertTrue(matches("(max-aspect-ratio: 1/1)"))
    }

    @Test
    fun `root variables, inheritance and fallbacks`() {
        val sheet = CssStylesheet.parse(
            """
            :root { --accent: #E91E63; --pad: 12px; }
            .card { --pad: 20px; padding: var(--pad); border: 2px solid var(--accent); }
            .missing { color: var(--nope, blue); background: var(--nope); }
            """
        )
        val root = sheet.rootVariables()
        assertEquals("#E91E63", root["--accent"])
        val card = sheet.resolve("div", "card", variables = root)
        assertEquals(20.dp, card.box.padding.top)
        assertEquals(parseColorOrNull("#E91E63"), card.box.borders[0].color)
        assertEquals("20px", card.variables["--pad"]) // passed on to children
        val missing = sheet.resolve("div", "missing")
        assertEquals(Color.Blue, missing.color)
        assertNull(missing.box.backgroundColor) // unresolvable var → declaration dropped
    }

    @Test
    fun `variables work in inline css and resolve recursively`() {
        val style = parseCssDeclaration("--base: 8px; --double: calc(var(--base) * 2); padding: var(--double)")
        assertEquals(16.dp, style.box.padding.left)
        assertNull(computeValue("var(--a)", mapOf("--a" to "var(--b)", "--b" to "var(--a)"))) // cycle
    }

    @Test
    fun `math functions`() {
        assertEquals("42px", computeValue("calc(10px + 2rem)", emptyMap()))
        assertEquals("30px", computeValue("calc((10px + 5px) * 2)", emptyMap()))
        assertEquals("5px", computeValue("calc(20px / 4)", emptyMap()))
        assertEquals("1 2px 3", computeValue("1 calc(1px * 2) 3", emptyMap()))
        assertEquals("10px", computeValue("min(10px, 20px)", emptyMap()))
        assertEquals("20px", computeValue("max(10px, 1rem, 20px)", emptyMap()))
        assertEquals("16px", computeValue("clamp(8px, 1rem, 32px)", emptyMap()))
        assertEquals("8px", computeValue("clamp(8px, -4px, 32px)", emptyMap()))
        assertNull(computeValue("calc(100% - 10px)", emptyMap())) // needs layout to resolve
        assertNull(computeValue("calc(2px * 3px)", emptyMap()))
        assertEquals("minmax(1px, 2px)", computeValue("minmax(1px, 2px)", emptyMap())) // not a math fn
    }

    @Test
    fun `css-wide keywords reset properties`() {
        val sheet = CssStylesheet.parse(".a { color: red; padding: 8px; }")
        val style = sheet.resolve("div", "a", inline = "color: initial; padding: unset")
        assertEquals(Color.Unspecified, style.color)
        assertEquals(0.dp, style.box.padding.top)
    }

    @Test
    fun `keyframes are parsed and resolved`() {
        val sheet = CssStylesheet.parse(
            """
            @keyframes pulse {
                from { opacity: 0.2; }
                50% { transform: scale(1.5); animation-timing-function: linear; }
                to { opacity: 1; }
            }
            .dot { animation: pulse 1s infinite alternate; font-size: 12px; }
            """
        )
        assertEquals(3, sheet.keyframes["pulse"]!!.rules.size)
        val style = sheet.resolve("div", "dot")
        assertEquals(12.sp, style.fontSize)
        val animation = style.animations.single()
        assertEquals("pulse", animation.ref.name)
        assertTrue(animation.ref.iterations.isInfinite())
        // Unknown keyframes are dropped
        assertTrue(sheet.resolve("div", inline = "animation: nope 1s").animations.isEmpty())
    }
}
