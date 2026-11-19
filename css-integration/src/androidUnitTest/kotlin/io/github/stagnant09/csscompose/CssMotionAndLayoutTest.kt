package io.github.stagnant09.csscompose

import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** New properties, transitions/animations, and the flex algorithm. */
class CssMotionAndLayoutTest {

    // ── New properties ──────────────────────────────────────────────────────

    @Test
    fun `per-side borders`() {
        val style = parseCssDeclaration(
            "border: 1px solid red; border-bottom: 4px dashed blue; border-left-style: dotted; border-color: green red"
        )
        val b = style.box.borders
        assertEquals(1.dp, b[0].width)
        assertEquals(4.dp, b[2].width)
        assertEquals(BorderStyle.Dashed, b[2].style)
        assertEquals(BorderStyle.Dotted, b[3].style)
        assertEquals(Color.Red, b[1].color) // border-color: green red → right = red
        assertEquals(0.dp, parseCssDeclaration("border: 2px none red").box.borders[0].resolvedWidth)
        assertEquals(4.dp, style.box.borderWidths.bottom)
    }

    @Test
    fun `outline shorthand and longhands`() {
        val o = parseCssDeclaration("outline: 2px dashed red; outline-offset: -4px").box.outline!!
        assertEquals(2.dp, o.width)
        assertEquals(BorderStyle.Dashed, o.resolvedStyle)
        assertEquals(Color.Red, o.color)
        assertEquals((-4).dp, o.offset)

        // The shorthand keeps an earlier offset; longhands work without a shorthand
        val longhands = parseCssDeclaration("outline-offset: 3px; outline: thick solid; outline-color: currentColor").box.outline!!
        assertEquals(5.dp, longhands.resolvedWidth)
        assertEquals(3.dp, longhands.offset)
        assertNull(longhands.color)

        // `auto` draws solid; a width alone implies solid (like borders); `none` draws nothing
        assertEquals(BorderStyle.Solid, parseCssDeclaration("outline: auto").box.outline!!.resolvedStyle)
        assertEquals(1.dp, parseCssDeclaration("outline-width: thin").box.outline!!.resolvedWidth)
        assertEquals(0.dp, parseCssDeclaration("outline: none; outline-width: 4px").box.outline!!.resolvedWidth)
        assertNull(parseCssDeclaration("color: red").box.outline)
    }

    @Test
    fun `outline animates width, color and offset`() {
        val from = encodeChannels(parseCssDeclaration("outline: 0px solid transparent"))
        val target = parseCssDeclaration("outline: 4px solid red; outline-offset: 6px")
        val to = encodeChannels(target)
        val mid = decodeChannels(target, FloatArray(Channel.COUNT) { (from[it] + to[it]) / 2 }).box.outline!!
        assertEquals(2.dp, mid.width)
        assertEquals(3.dp, mid.offset)
        assertEquals(1f, mid.color!!.red, 0.01f) // premultiplied: stays red while fading in
        assertEquals(0.5f, mid.color!!.alpha, 0.01f)

        // Fading out to `outline: none` keeps drawing until the width reaches 0
        val fading = decodeChannels(parseCssDeclaration("color: black"), FloatArray(Channel.COUNT) { (to[it] + 0f) / 2 })
        assertEquals(2.dp, fading.box.outline!!.resolvedWidth)
        assertEquals(setOf(Channel.OUTLINE_OFFSET), channelSetFor("outline-offset"))
    }

    @Test
    fun `flex item properties`() {
        val one = parseCssDeclaration("flex: 1").item
        assertEquals(1f, one.grow)
        assertEquals(CssLength.Fixed(0.dp), one.basis)
        val auto = parseCssDeclaration("flex: auto").item
        assertEquals(1f, auto.grow)
        assertNull(auto.basis)
        val none = parseCssDeclaration("flex: none").item
        assertEquals(0f, none.shrink)
        val full = parseCssDeclaration("flex: 2 0 120px; align-self: center; order: -1").item
        assertEquals(2f, full.grow)
        assertEquals(0f, full.shrink)
        assertEquals(CssLength.Fixed(120.dp), full.basis)
        assertEquals("center", full.alignSelf)
        assertEquals(-1, full.order)
        assertEquals(CssLength.Fixed(100.dp), parseCssDeclaration("flex: 100px").item.basis)
    }

    @Test
    fun `positioning`() {
        val item = parseCssDeclaration("position: absolute; top: 8px; right: 10%").item
        assertEquals(CssPosition.Absolute, item.position)
        assertEquals(CssLength.Fixed(8.dp), item.insets.top)
        assertEquals(CssLength.Percent(0.1f), item.insets.right)
        val inset = parseCssDeclaration("position: absolute; inset: 0 4px").item.insets
        assertEquals(CssLength.Fixed(4.dp), inset.left)
        // Absolutely positioned blocks don't stretch to the container width
        assertTrue(!parseCssDeclaration("display: block; position: absolute").fillsWidth)
    }

    @Test
    fun `font shorthand`() {
        val style = parseCssDeclaration("font: italic bold 18px/1.5 Georgia, serif")
        assertEquals(18.sp, style.fontSize)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, style.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, style.fontStyle)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Serif, style.fontFamily)
        assertEquals(androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Em), style.lineHeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(700), parseCssDeclaration("font: 700 12px sans-serif").fontWeight)
    }

    @Test
    fun `filters, transforms, shadows, overflow`() {
        val style = parseCssDeclaration(
            "filter: blur(4px) grayscale(50%) hue-rotate(0.25turn);" +
                " transform: translate(4px, 2px) rotate(10deg) rotate(5deg) scale(2) scaleY(1.5);" +
                " box-shadow: 0 2px 4px red, inset 0 0 2px blue, 1px 1px 0 3px green;" +
                " overflow-y: auto; border-top-left-radius: 50%"
        )
        val box = style.box
        assertEquals(4.dp, box.blur)
        assertEquals(listOf(FilterFunction("grayscale", 0.5f), FilterFunction("hue-rotate", 90f)), box.filters)
        assertEquals(15f, box.transform.rotate)
        assertEquals(2f, box.transform.scaleX)
        assertEquals(3f, box.transform.scaleY)
        assertEquals(4.dp, box.transform.translateX)
        assertEquals(2, box.shadows.size) // inset shadows are skipped
        assertEquals(3.dp, box.shadows[1].spread)
        assertEquals(Overflow.Scroll, box.overflowY)
        assertEquals(Overflow.Visible, box.overflowX)
        assertTrue(box.radii[0].isPercent)
    }

    @Test
    fun `grayscale matrix uses luminance weights`() {
        val m = filterColorMatrix(listOf(FilterFunction("grayscale", 1f)))
        assertEquals(0.2126f, m[0, 0], 1e-4f)
        assertEquals(0.7152f, m[1, 1], 1e-4f)
        // Filters chain left-to-right: invert twice is identity
        val identity = filterColorMatrix(listOf(FilterFunction("invert", 1f), FilterFunction("invert", 1f)))
        assertEquals(1f, identity[0, 0], 1e-4f)
        assertEquals(0f, identity[0, 4], 1e-3f)
    }

    // ── Transitions ─────────────────────────────────────────────────────────

    @Test
    fun `easing functions`() {
        assertEquals(CssEasing.Ease, CssEasing.parse("ease"))
        assertEquals(CssEasing.CubicBezier(0.1f, 0.7f, 1f, 0.1f), CssEasing.parse("cubic-bezier(0.1, 0.7, 1.0, 0.1)"))
        val steps = CssEasing.parse("steps(4, jump-end)")!!.toEasing()
        assertEquals(0.25f, steps.transform(0.3f), 1e-4f)
        assertEquals(0.5f, CssEasing.parse("steps(2, start)")!!.toEasing().transform(0.2f), 1e-4f)
        assertEquals(0.3f, CssEasing.Linear.toEasing().transform(0.3f), 1e-4f)
    }

    @Test
    fun `transition shorthand and longhands`() {
        val list = parseTransitions(mapOf("transition" to "opacity 200ms ease-in 50ms, transform .5s"))
        assertEquals(2, list.size)
        assertEquals("opacity", list[0].property)
        assertEquals(200, list[0].durationMs)
        assertEquals(50, list[0].delayMs)
        assertEquals(CssEasing.EaseIn, list[0].easing)
        assertEquals(500, list[1].durationMs)

        val longhands = parseTransitions(
            linkedMapOf(
                "transition-duration" to "100ms, 300ms",
                "transition-property" to "color, background-color, opacity",
            )
        )
        assertEquals(listOf(100, 300, 100), longhands.map { it.durationMs }) // lists cycle
        assertTrue(parseTransitions(mapOf("transition" to "none")).isEmpty())
        assertTrue(parseTransitions(mapOf("transition" to "opacity 0s")).isEmpty())
    }

    @Test
    fun `channels round-trip and map properties`() {
        val style = parseCssDeclaration(
            "color: red; background: blue; padding: 4px 8px; width: 120px; opacity: 0.5;" +
                " transform: rotate(30deg); border: 2px solid green; border-radius: 6px; font-size: 14px"
        )
        val decoded = decodeChannels(style, encodeChannels(style))
        assertEquals(style.color, decoded.color)
        assertEquals(style.box.backgroundColor, decoded.box.backgroundColor)
        assertEquals(style.box.padding, decoded.box.padding)
        assertEquals(style.box.width, decoded.box.width)
        assertEquals(style.box.opacity, decoded.box.opacity)
        assertEquals(style.box.transform, decoded.box.transform)
        assertEquals(style.box.radii, decoded.box.radii)
        assertEquals(style.fontSize, decoded.fontSize)
        assertEquals(style.box.borders[0].width, decoded.box.borders[0].width)
        // A style without background must not gain one
        assertNull(decodeChannels(parseCssDeclaration("color: red"), encodeChannels(parseCssDeclaration("color: red"))).box.backgroundColor)
        // Property → channel mapping
        assertEquals(setOf(Channel.OPACITY), channelSetFor("opacity"))
        assertEquals(setOf(Channel.PADDING + 3), channelSetFor("padding-left"))
        assertEquals(5, channelSetFor("border-top").size) // 4 color components + width
        assertTrue(channelSetFor("display").isEmpty())
    }

    @Test
    fun `premultiplied colors fade without darkening`() {
        val transparent = encodeChannels(parseCssDeclaration("background: transparent"))
        val red = encodeChannels(parseCssDeclaration("background: red"))
        val half = FloatArray(Channel.COUNT) { (transparent[it] + red[it]) / 2 }
        val mid = decodeChannels(parseCssDeclaration("background: red"), half).box.backgroundColor!!
        assertEquals(1f, mid.red, 0.01f) // stays pure red, only alpha changes
        assertEquals(0.5f, mid.alpha, 0.01f)
    }

    // ── Animations ──────────────────────────────────────────────────────────

    @Test
    fun `animation shorthand`() {
        val ref = parseAnimationRefs(mapOf("animation" to "spin 2s linear 500ms infinite alternate-reverse both paused")).single()
        assertEquals("spin", ref.name)
        assertEquals(2000, ref.durationMs)
        assertEquals(500, ref.delayMs)
        assertEquals(CssEasing.Linear, ref.easing)
        assertTrue(ref.iterations.isInfinite())
        assertEquals(AnimationDirection.AlternateReverse, ref.direction)
        assertEquals(AnimationFillMode.Both, ref.fillMode)
        assertTrue(ref.paused)
        assertTrue(parseAnimationRefs(mapOf("animation" to "none")).isEmpty())
    }

    private fun animated(css: String) = CssStylesheet.parse(
        """
        @keyframes fade { from { opacity: 0; } to { opacity: 1; } }
        @keyframes mid { 50% { opacity: 0; } }
        .x { $css }
        """
    ).resolve("div", "x").animations.single()

    @Test
    fun `progress honours delay, iterations, direction and fill`() {
        val basic = animated("animation: fade 1000ms linear")
        assertEquals(0.25f, basic.progressAt(250f)!!, 1e-4f)
        assertNull(basic.progressAt(1500f)) // finished, no fill
        val delayed = animated("animation: fade 1000ms linear 500ms backwards")
        assertEquals(0f, delayed.progressAt(100f)!!, 1e-4f) // fill backwards during delay
        val alternate = animated("animation: fade 1000ms linear 2 alternate forwards")
        assertEquals(0.75f, alternate.progressAt(1250f)!!, 1e-4f) // 2nd iteration runs backwards
        assertEquals(0f, alternate.progressAt(5000f)!!, 1e-4f) // ends at the start after alternating
        val partial = animated("animation: fade 1000ms linear 1.5 forwards")
        assertEquals(0.5f, partial.progressAt(9999f)!!, 1e-4f)
    }

    @Test
    fun `keyframe sampling interpolates declared channels`() {
        val fade = animated("animation: fade 1s linear")
        val out = FloatArray(Channel.COUNT)
        fade.sampleInto(0.25f, out)
        assertEquals(0.25f, out[Channel.OPACITY], 1e-4f)
        // Implicit 0% / 100% keyframes use the element's own value
        val mid = animated("opacity: 0.8; animation: mid 1s linear")
        mid.sampleInto(0.25f, out)
        assertEquals(0.4f, out[Channel.OPACITY], 1e-4f)
        mid.sampleInto(1f, out)
        assertEquals(0.8f, out[Channel.OPACITY], 1e-4f)
    }

    // ── Flex algorithm ──────────────────────────────────────────────────────

    private fun item(main: Int, grow: Float = 0f, shrink: Float = 1f, min: Int = 0, cross: Int = 10, align: String? = null, stretchable: Boolean = true) =
        FlexItemInput(grow, shrink, main, { min }, align, stretchable, { cross })

    @Test
    fun `grow distributes free space`() {
        val plan = planFlex(listOf(item(100, grow = 1f), item(100, grow = 2f)), FlexWrap(), 0, 0, 400, 400, 0, 1000)
        assertArrayEquals(intArrayOf(167, 233), plan.mainSizes)
        assertArrayEquals(intArrayOf(0, 167), plan.mainPositions)
    }

    @Test
    fun `shrink respects min-content`() {
        val plan = planFlex(listOf(item(300, min = 250), item(300)), FlexWrap(), 0, 0, 400, 400, 0, 1000)
        assertEquals(250, plan.mainSizes[0])
        assertEquals(150, plan.mainSizes[1])
    }

    @Test
    fun `wrapping, gaps and justify`() {
        val spec = FlexWrap(wrap = true, justifyContent = "space-between")
        val plan = planFlex(List(3) { item(100, cross = 20) }, spec, 10, 5, 250, 250, 0, 1000)
        assertArrayEquals(intArrayOf(0, 0, 1), plan.lineOf)
        assertArrayEquals(intArrayOf(0, 150, 0), plan.mainPositions)
        assertArrayEquals(intArrayOf(0, 25), plan.lineOffsets)
        assertEquals(45, plan.containerCross)
    }

    @Test
    fun `reverse, stretch and content-sized containers`() {
        val reversed = planFlex(
            listOf(item(50, cross = 10), item(30, cross = 40, align = "start"), item(20, cross = 5, stretchable = false)),
            FlexWrap(direction = FlexDirection.RowReverse),
            0, 0, 0, 1000, 0, 1000,
        )
        assertEquals(100, reversed.containerMain) // shrink-wraps when not exact
        assertArrayEquals(intArrayOf(50, 20, 0), reversed.mainPositions)
        assertArrayEquals(intArrayOf(40, -1, -1), reversed.stretchedCross)

        val centered = planFlex(listOf(item(100)), FlexWrap(justifyContent = "center"), 0, 0, 300, 300, 0, 1000)
        assertArrayEquals(intArrayOf(100), centered.mainPositions)
    }

    // ── Tooling ─────────────────────────────────────────────────────────────

    @Test
    fun `ksp validator knows every supported property`() {
        val source = File("../css-processor/src/main/java/io/github/stagnant09/csscompose/processor/CssStyleProcessor.kt").readText()
        val missing = SUPPORTED_CSS_PROPERTIES.filterNot { property ->
            source.contains("\"$property\"")
        }
        assertTrue("Missing from KSP KNOWN_PROPERTIES: $missing", missing.isEmpty())
    }
}
