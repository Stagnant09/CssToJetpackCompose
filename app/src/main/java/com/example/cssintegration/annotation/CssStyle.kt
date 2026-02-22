package com.example.cssintegration.annotation

/**
 * Marks a composable function or type to be styled using a CSS-like declaration string.
 *
 * The [value] string accepts standard CSS property declarations (without a selector block).
 * Supported properties are parsed at runtime and applied as Compose [StyleResult].
 *
 * ## Usage
 *
 * ```kotlin
 * @CssStyle("color: #FF5722; font-size: 18; font-weight: bold;")
 * @Composable
 * fun GreetingText(name: String) {
 *     val style = rememberCssStyle()
 *     Text(
 *         text = "Hello, $name",
 *         style = style.toTextStyle(),
 *         modifier = style.modifier
 *     )
 * }
 * ```
 *
 * @param value A semicolon-delimited list of CSS property declarations.
 * @param targetElement Optional semantic hint for the target composable type.
 *   Defaults to [CssTarget.AUTO], which infers context from the annotated function name.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class CssStyle(
    val value: String,
    val targetElement: CssTarget = CssTarget.AUTO
)

/**
 * Hints the CSS parser toward a specific Compose styling context.
 *
 * - [AUTO]   — infers based on annotated function or type name (default)
 * - [TEXT]   — routes text-specific properties (color, font-size, …) to [TextStyle]
 * - [BOX]    — routes layout properties (padding, background, …) to [Modifier]
 * - [MIXED]  — applies both text and layout properties (most permissive)
 */
enum class CssTarget { AUTO, TEXT, BOX, MIXED }