package io.github.stagnant09.csscompose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebCssTest {

    @Test
    fun disabledAndEnabledBecomeTheAriaAttribute() {
        val css = adaptForBrowser(".button:disabled { opacity: .5 } .a:enabled:hover { color: red } .b:disabled-ish { }")
        assertTrue(""".button[aria-disabled="true"] { opacity: .5 }""" in css)
        assertTrue(""".a:not([aria-disabled="true"]):hover""" in css)
        assertTrue(".b:disabled-ish" in css) // only the exact pseudo-class is rewritten
        assertTrue(css.startsWith(BrowserBaseCss))
    }

    @Test
    fun inlineDeclarationsSplitOnTopLevelSemicolons() {
        val parsed = splitInlineDeclarations(
            "Color: red; background: url('a;b.png') ; --Accent: #E91E63; margin: 4px !important;; bad"
        )
        assertEquals(
            listOf("color" to "red", "background" to "url('a;b.png')", "--Accent" to "#E91E63", "margin" to "4px"),
            parsed,
        )
    }

    @Test
    fun classNamesSplitOnWhitespace() {
        assertEquals(listOf("button", "ghost"), splitClassNames(" button  ghost "))
        assertEquals(emptyList(), splitClassNames(""))
    }
}
