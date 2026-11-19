package io.github.stagnant09.csscompose

/**
 * Attaches a CSS declaration string to a composable function or a style holder class.
 *
 * The [value] string accepts standard CSS property declarations (without a selector block).
 * At build time the `css-processor` KSP module validates it and warns about unknown
 * properties, so typos like `colr: red` are caught before the app runs.
 *
 * ## On a style holder (read at runtime)
 *
 * ```kotlin
 * @CssStyle("color: #FF5722; font-size: 18; font-weight: bold;")
 * object Greeting
 *
 * @Composable
 * fun GreetingText(name: String) {
 *     val style = rememberCssStyle<Greeting>()
 *     Text("Hello, $name", style = style.toTextStyle(), modifier = style.modifier)
 * }
 * ```
 *
 * ## On a composable (documents the CSS it uses)
 *
 * ```kotlin
 * private const val TitleCss = "font-size: 24px; font-weight: 700"
 *
 * @CssStyle(TitleCss)
 * @Composable
 * fun Title(text: String) {
 *     val style = rememberParsedCss(TitleCss)
 *     Text(text, style = style.toTextStyle())
 * }
 * ```
 *
 * @param value A semicolon-delimited list of CSS property declarations.
 * @param targetElement Optional semantic hint for the target composable type.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CssStyle(
    val value: String,
    val targetElement: CssTarget = CssTarget.AUTO
)

/**
 * Hints the CSS parser toward a specific Compose styling context.
 *
 * - [AUTO]   — infers based on annotated function or type name (default)
 * - [TEXT]   — routes text-specific properties (color, font-size, …) to `TextStyle`
 * - [BOX]    — routes layout properties (padding, background, …) to `Modifier`
 * - [MIXED]  — applies both text and layout properties (most permissive)
 */
enum class CssTarget { AUTO, TEXT, BOX, MIXED }
