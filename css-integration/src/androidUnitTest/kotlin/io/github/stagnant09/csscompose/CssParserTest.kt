package io.github.stagnant09.csscompose

import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CssParserTest {

    // ── Declarations & cascade ──────────────────────────────────────────────

    @Test
    fun `declarations skip comments and malformed entries`() {
        val result = parseDeclarations("/* hi */ color: red; nonsense; ;font-size : 12px ;")
        assertEquals(
            listOf(CssDeclaration("color", "red"), CssDeclaration("font-size", "12px")),
            result,
        )
    }

    @Test
    fun `semicolons inside parentheses and quotes do not split`() {
        val result = parseDeclarations("""background: url("a;b"); color: rgb(1, 2, 3)""")
        assertEquals(2, result.size)
        assertEquals("url(\"a;b\")", result[0].value)
    }

    @Test
    fun `important is detected and stripped`() {
        val result = parseDeclarations("color: red !important")
        assertEquals(CssDeclaration("color", "red", important = true), result.single())
    }

    @Test
    fun `cascade is last-wins and important beats normal`() {
        val map = cascade(
            listOf(
                CssDeclaration("color", "red", important = true),
                CssDeclaration("color", "blue"),
                CssDeclaration("padding", "1"),
                CssDeclaration("padding", "2"),
            )
        )
        assertEquals("red", map["color"])
        assertEquals("2", map["padding"])
    }

    @Test
    fun `cascade preserves order of winning declarations`() {
        val map = cascade(
            listOf(
                CssDeclaration("padding-top", "4"),
                CssDeclaration("margin", "1"),
                CssDeclaration("padding-top", "8"),
            )
        )
        assertEquals(listOf("margin", "padding-top"), map.keys.toList())
    }

    // ── Colors ──────────────────────────────────────────────────────────────

    @Test
    fun `hex colors follow CSS semantics`() {
        assertEquals(Color(0xFFFF0000), parseColorOrNull("#f00"))
        assertEquals(Color(0xFFFF0000), parseColorOrNull("#FF0000"))
        // CSS #RRGGBBAA puts alpha last
        assertEquals(Color(255, 0, 0, 0x80), parseColorOrNull("#ff000080"))
        assertEquals(Color(255, 0, 0, 0x88), parseColorOrNull("#f008"))
        assertNull(parseColorOrNull("#ggg"))
        assertNull(parseColorOrNull("#12345"))
    }

    @Test
    fun `rgb and rgba accept legacy and modern syntax`() {
        assertEquals(Color(255, 128, 0), parseColorOrNull("rgb(255, 128, 0)"))
        assertEquals(Color(255, 128, 0), parseColorOrNull("rgb(255 128 0)"))
        val withAlpha = parseColorOrNull("rgba(0, 0, 255, 0.5)")!!
        assertEquals(0.5f, withAlpha.alpha, 0.01f)
        val modernAlpha = parseColorOrNull("rgb(0 0 255 / 25%)")!!
        assertEquals(0.25f, modernAlpha.alpha, 0.01f)
        assertEquals(1f, parseColorOrNull("rgb(100%, 0%, 0%)")!!.red, 0.01f)
    }

    @Test
    fun `hsl is converted to rgb`() {
        val red = parseColorOrNull("hsl(0, 100%, 50%)")!!
        assertEquals(1f, red.red, 0.01f)
        assertEquals(0f, red.green, 0.01f)
        val green = parseColorOrNull("hsl(120deg 100% 25%)")!!
        assertEquals(0.5f, green.green, 0.01f)
        assertEquals(0.5f, parseColorOrNull("hsla(240, 100%, 50%, 0.5)")!!.alpha, 0.01f)
    }

    @Test
    fun `named colors and unknown names`() {
        assertEquals(Color.Transparent, parseColorOrNull("transparent"))
        assertNotNull(parseColorOrNull("RebeccaPurple"))
        assertNull(parseColorOrNull("notacolor"))
        assertNull(parseColorOrNull("none"))
    }

    // ── Lengths ─────────────────────────────────────────────────────────────

    @Test
    fun `box lengths`() {
        assertEquals(CssLength.Fixed(12.dp), parseLength("12px"))
        assertEquals(CssLength.Fixed(12.dp), parseLength("12"))
        assertEquals(CssLength.Fixed(24.dp), parseLength("1.5rem"))
        assertEquals(CssLength.Percent(0.5f), parseLength("50%"))
        assertEquals(CssLength.Auto, parseLength("auto"))
        assertNull(parseLength("12furlongs"))
    }

    @Test
    fun `text lengths`() {
        assertEquals(14.sp, parseLengthAsTextUnit("14px"))
        assertEquals(14.sp, parseLengthAsTextUnit("14"))
        assertEquals(32.sp, parseLengthAsTextUnit("2rem"))
        assertEquals(1.5.em, parseLengthAsTextUnit("1.5em"))
        assertEquals(TextUnit.Unspecified, parseLengthAsTextUnit("big"))
    }

    @Test
    fun `unitless line-height is a multiplier`() {
        assertEquals(1.5.em, parseLineHeight("1.5"))
        assertEquals(22.sp, parseLineHeight("22px"))
        assertEquals(TextUnit.Unspecified, parseLineHeight("normal"))
    }

    // ── Typography ──────────────────────────────────────────────────────────

    @Test
    fun `typography properties`() {
        val style = parseCssDeclaration(
            "color: #E91E63; font-size: 20px; font-weight: 700; font-style: italic;" +
                " text-align: center; letter-spacing: 2px; text-decoration: underline line-through;" +
                " font-family: 'Unknown Font', monospace; text-transform: uppercase;" +
                " white-space: nowrap; text-overflow: ellipsis; line-clamp: 2"
        )
        assertEquals(Color(0xFFE91E63), style.color)
        assertEquals(20.sp, style.fontSize)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertEquals(TextAlign.Center, style.textAlign)
        assertEquals(2.sp, style.letterSpacing)
        assertEquals(
            TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)),
            style.textDecoration,
        )
        assertEquals(FontFamily.Monospace, style.fontFamily)
        assertEquals(TextTransform.Uppercase, style.textTransform)
        assertEquals(false, style.softWrap)
        assertEquals(TextOverflow.Ellipsis, style.textOverflow)
        assertEquals(2, style.maxLines)
        assertEquals("HELLO", style.transformText("hello"))
    }

    @Test
    fun `undeclared text properties stay unspecified`() {
        val textStyle = parseCssDeclaration("padding: 4px").toTextStyle()
        assertNull(textStyle.fontWeight)
        assertNull(textStyle.fontStyle)
        assertNull(textStyle.textDecoration)
        assertEquals(TextUnit.Unspecified, textStyle.fontSize)
        assertTrue(!parseCssDeclaration("padding: 4px").hasTextStyle)
    }

    @Test
    fun `capitalize transforms each word`() {
        val style = parseCssDeclaration("text-transform: capitalize")
        assertEquals("Hello Compose World", style.transformText("hello compose world"))
    }

    @Test
    fun `text shadow is parsed`() {
        val shadow = parseCssDeclaration("text-shadow: 1px 2px 3px red").textShadow!!
        assertEquals(1.dp, shadow.offsetX)
        assertEquals(2.dp, shadow.offsetY)
        assertEquals(3.dp, shadow.blur)
        assertEquals(Color.Red, shadow.color)
    }

    // ── Robustness ──────────────────────────────────────────────────────────

    @Test
    fun `values that used to crash are handled`() {
        // Previously height/width used String.toInt() and threw on units.
        parseCssDeclaration("height: 100px; width: 50%; min-width: 10rem; max-height: auto")
        parseCssDeclaration("margin: -10px; padding: -4px; border: none; background: none")
        parseCssDeclaration("border-radius: 8px 4px; box-shadow: 0 2px 4px rgba(0,0,0,.2)")
        parseCssDeclaration("transform: rotate(45deg) scale(1.2) translate(4px, 2px)")
        parseCssDeclaration("background: linear-gradient(to right, red, blue 80%, green)")
        parseCssDeclaration("background-image: radial-gradient(circle, white, black)")
        parseCssDeclaration("aspect-ratio: 16 / 9; opacity: 50%; z-index: 3; overflow: hidden")
    }

    @Test
    fun `empty css yields default style`() {
        val style = parseCssDeclaration("   ")
        assertEquals(Color.Unspecified, style.color)
        assertNull(style.wrap)
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    @Test
    fun `display resolves to the right wrap`() {
        val flex = parseCssDeclaration("display: flex").wrap as FlexWrap
        assertEquals(FlexDirection.Row, flex.direction)
        assertEquals("stretch", flex.alignItems) // CSS default
        assertEquals(FlexDirection.Column, (parseCssDeclaration("display: flex; flex-direction: column").wrap as FlexWrap).direction)
        assertTrue((parseCssDeclaration("display: flex; flex-wrap: wrap").wrap as FlexWrap).wrap)
        val flow = parseCssDeclaration("display: flex; flex-flow: column-reverse wrap").wrap as FlexWrap
        assertEquals(FlexDirection.ColumnReverse, flow.direction)
        assertTrue(flow.wrap)
        assertTrue((parseCssDeclaration("display: inline-block").wrap as FlexWrap).wrap)
        assertEquals(FlexWrap.Block, parseCssDeclaration("display: block").wrap)
        assertEquals(NoneWrap, parseCssDeclaration("display: none").wrap)
        assertTrue(parseCssDeclaration("display: none").isDisplayNone)
    }

    @Test
    fun `block-level displays fill width unless a width is set`() {
        assertTrue(parseCssDeclaration("display: flex").fillsWidth)
        assertTrue(parseCssDeclaration("display: block").fillsWidth)
        assertTrue(parseCssDeclaration("display: grid").fillsWidth)
        assertTrue(!parseCssDeclaration("display: inline-flex").fillsWidth)
        assertTrue(!parseCssDeclaration("display: flex; width: 120px").fillsWidth)
        assertTrue(!parseCssDeclaration("padding: 4px").fillsWidth)
    }

    @Test
    fun `flex alignment and gap`() {
        val row = parseCssDeclaration(
            "display: flex; justify-content: center; align-items: flex-end; gap: 8px 4px"
        ).wrap as FlexWrap
        assertEquals("center", row.justifyContent)
        assertEquals("end", row.alignItems)
        assertEquals(4.dp, row.mainGap)   // column-gap is the main axis of a row
        assertEquals(8.dp, row.crossGap)

        val column = parseCssDeclaration(
            "display: flex; flex-direction: column; justify-content: space-between; align-items: center"
        ).wrap as FlexWrap
        assertEquals("space-between", column.justifyContent)
        assertEquals("center", column.alignItems)
    }

    @Test
    fun `grid columns and gaps`() {
        assertEquals(3, (parseCssDeclaration("display: grid; grid-template-columns: repeat(3, 1fr)").wrap as GridWrap).columns)
        assertEquals(4, (parseCssDeclaration("display: grid; grid-template-columns: 1fr 2fr 1fr auto").wrap as GridWrap).columns)
        val grid = parseCssDeclaration("display: grid; gap: 4px 12px").wrap as GridWrap
        assertEquals(4.dp, grid.rowGap)
        assertEquals(12.dp, grid.columnGap)
        val override = parseCssDeclaration("display: grid; gap: 4px; column-gap: 20px").wrap as GridWrap
        assertEquals(4.dp, override.rowGap)
        assertEquals(20.dp, override.columnGap)
    }

    @Test
    fun `box alignment from justify and align`() {
        val box = parseCssDeclaration("display: box; justify-content: center; align-items: center").wrap as BoxWrap
        assertEquals(Alignment.Center, box.contentAlignment)
        val legacy = parseCssDeclaration("display: box; align-content: bottom-center").wrap as BoxWrap
        assertEquals(Alignment.BottomCenter, legacy.contentAlignment)
    }

    // ── Stylesheets ─────────────────────────────────────────────────────────

    @Test
    fun `selectors parse and reject unsupported forms`() {
        assertEquals(CssSelector(element = "div", classes = setOf("card", "active")), CssSelector.parse("div.card.active"))
        assertEquals(CssSelector(id = "hero"), CssSelector.parse("#hero"))
        assertEquals(CssSelector(), CssSelector.parse("*"))
        assertNull(CssSelector.parse("a + b"))
        assertNull(CssSelector.parse("a ~ b"))
        assertNull(CssSelector.parse("a[href]"))
        assertNull(CssSelector.parse("p::before"))
        assertNull(CssSelector.parse(".btn:visited"))
    }

    @Test
    fun `cascade honours specificity, source order and inline css`() {
        val sheet = CssStylesheet.parse(
            """
            #main      { color: green; }
            .card      { color: red; font-size: 10px; }
            div        { color: blue; font-size: 99px; }
            .card.big  { font-size: 30px; }
            .late      { color: purple !important; }
            """
        )
        assertEquals(Color.Red, sheet.resolve("div", className = "card").color)
        assertEquals(30.sp, sheet.resolve("div", className = "card big").fontSize)
        assertEquals(parseColorOrNull("green"), sheet.resolve("div", className = "card", id = "main").color)
        assertEquals(Color.Yellow, sheet.resolve("div", className = "card", inline = "color: yellow").color)
        // !important in the sheet beats normal inline css
        assertEquals(
            parseColorOrNull("purple"),
            sheet.resolve("div", className = "late", inline = "color: yellow").color,
        )
        // Element selector doesn't match other elements
        assertEquals(10.sp, sheet.resolve("text", className = "card").fontSize)
    }

    @Test
    fun `selector lists, comments and at-rules`() {
        val sheet = CssStylesheet.parse(
            """
            /* shared */
            .a, .b { color: red; }
            @font-face { font-family: X; }
            .a + .sibling { color: green; }
            """
        )
        assertEquals(Color.Red, sheet.resolve("div", className = "a").color)
        assertEquals(Color.Red, sheet.resolve("div", className = "b").color)
    }

    @Test
    fun `stylesheets combine in order`() {
        val combined = CssStylesheet.parse(".x { color: red; }") + CssStylesheet.parse(".x { color: blue; }")
        assertEquals(Color.Blue, combined.resolve("div", className = "x").color)
    }

    @Test
    fun `legacy parseCssStylesheet map`() {
        val styles = parseCssStylesheet(".title { color: #212121; font-size: 24; } .body { line-height: 22px }")
        assertEquals(24.sp, styles[".title"]?.fontSize)
        assertEquals(22.sp, styles[".body"]?.lineHeight)
    }
}
