package com.example.cssintegration.cssUtils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.example.cssintegration.annotation.CssStyle
import com.example.cssintegration.ui.components.NoneWrap
import com.example.cssintegration.ui.components.Wrap

// ─────────────────────────────────────────────────────────────────────────────
// StyleResult  —  immutable container for all resolved styling
// ─────────────────────────────────────────────────────────────────────────────

/** CSS `text-transform`. */
enum class TextTransform { None, Uppercase, Lowercase, Capitalize }

/**
 * Resolved output of a CSS declaration block.
 *
 * - [box] holds the box model and decoration as plain data; [modifier] turns it into a
 *   Compose `Modifier` in CSS box-model order.
 * - Typographic properties are exposed via [toTextStyle] and stay unset
 *   (`null` / `Unspecified`) when not declared, so they merge cleanly with the theme.
 * - [wrap] describes how a [com.example.cssintegration.ui.components.Div] lays out children,
 *   and [item] how this element behaves inside a flex parent.
 * - [transitions] and [animations] drive motion in `Div` / `CssText`.
 */
@Immutable
data class StyleResult(
    val box: BoxStyle = BoxStyle(),
    val item: FlexItemStyle = FlexItemStyle.Default,
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit.Unspecified,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val fontFamily: FontFamily? = null,
    val textAlign: TextAlign? = null,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val lineHeight: TextUnit = TextUnit.Unspecified,
    val textDecoration: TextDecoration? = null,
    val textShadow: CssShadow? = null,
    val textTransform: TextTransform = TextTransform.None,
    val maxLines: Int? = null,
    val softWrap: Boolean = true,
    val textOverflow: TextOverflow = TextOverflow.Clip,
    val wrap: Wrap? = null,
    /**
     * `true` for block-level boxes (`display: block | flex | grid`) without an explicit
     * `width`: like in CSS they stretch to the available width.
     */
    val fillsWidth: Boolean = false,
    val transitions: List<CssTransition> = emptyList(),
    /** `animation` declarations as written (names not yet resolved to keyframes). */
    val animationRefs: List<CssAnimationRef> = emptyList(),
    /** Animations whose `@keyframes` were found in the stylesheet. */
    val animations: List<ResolvedAnimation> = emptyList(),
    /** Custom properties visible to this element and inherited by its children. */
    val variables: Map<String, String> = emptyMap(),
) {
    /** The box styling as a Compose `Modifier` (static: no scrolling, interaction or motion). */
    val modifier: Modifier by lazy { buildModifier() }

    /** `true` when the CSS declared `display: none`. */
    val isDisplayNone: Boolean get() = wrap is NoneWrap

    /** `true` when a transition or keyframe animation may change this style over time. */
    val hasMotion: Boolean get() = transitions.isNotEmpty() || animations.isNotEmpty()

    /** `true` when any inheritable text property was declared. */
    val hasTextStyle: Boolean
        get() = color != Color.Unspecified || fontSize != TextUnit.Unspecified ||
            fontWeight != null || fontStyle != null || fontFamily != null ||
            textAlign != null || letterSpacing != TextUnit.Unspecified ||
            lineHeight != TextUnit.Unspecified || textDecoration != null || textShadow != null

    /**
     * Converts the typographic fields into a Compose [TextStyle]. Undeclared fields stay
     * unspecified, so `LocalTextStyle.current.merge(style.toTextStyle())` keeps theme
     * defaults for anything the CSS didn't mention.
     *
     * @param density used to convert `text-shadow` offsets from dp to pixels;
     *   pass `LocalDensity.current` from composition for accurate shadows.
     */
    fun toTextStyle(density: Density = Density(1f)): TextStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        textAlign = textAlign ?: TextAlign.Unspecified,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textDecoration = textDecoration,
        shadow = textShadow?.let { s ->
            with(density) {
                Shadow(
                    color = s.color,
                    offset = Offset(s.offsetX.toPx(), s.offsetY.toPx()),
                    blurRadius = s.blur.toPx(),
                )
            }
        },
    )

    /** Applies CSS `text-transform` to [text]. */
    fun transformText(text: String): String = when (textTransform) {
        TextTransform.None -> text
        TextTransform.Uppercase -> text.uppercase()
        TextTransform.Lowercase -> text.lowercase()
        TextTransform.Capitalize -> text.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose-idiomatic entry points
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a raw CSS declaration string (no selector, no braces) into a [StyleResult],
 * re-parsing only when [css] changes.
 *
 * ```kotlin
 * val result = rememberParsedCss("font-size: 16; color: #333; padding: 12px;")
 * Text("Hello", style = result.toTextStyle(), modifier = result.modifier)
 * ```
 */
@Composable
fun rememberParsedCss(css: String): StyleResult = remember(css) { parseCssDeclaration(css) }

/**
 * Reads the [CssStyle] annotation on class [T] and returns the remembered [StyleResult].
 * Handy for declaring reusable styles as annotated objects:
 *
 * ```kotlin
 * @CssStyle("color: #E91E63; font-size: 20; font-weight: 700;")
 * object Headline
 *
 * @Composable
 * fun Title(text: String) {
 *     val style = rememberCssStyle<Headline>()
 *     Text(text, style = style.toTextStyle(), modifier = style.modifier)
 * }
 * ```
 *
 * Uses Java reflection, so `kotlin-reflect` is not required.
 */
@Composable
inline fun <reified T : Any> rememberCssStyle(): StyleResult {
    val css = T::class.java.getAnnotation(CssStyle::class.java)?.value.orEmpty()
    return remember(css) { parseCssDeclaration(css) }
}
