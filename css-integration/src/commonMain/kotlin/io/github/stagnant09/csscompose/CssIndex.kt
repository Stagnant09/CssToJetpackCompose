package io.github.stagnant09.csscompose

import androidx.compose.runtime.Immutable

/**
 * An element's position among its siblings, for `:first-child`, `:nth-child()` …
 *
 * Compose can't discover sibling positions on its own, so pass it explicitly when you
 * render lists: `items.forEachIndexed { i, item -> Div(index = CssIndex(i, items.size)) { … } }`.
 *
 * On the web it's ignored: the browser counts siblings itself, and it counts every sibling
 * element. Wrap list items in their own `Div` so both platforms count the same elements.
 *
 * @param position 0-based index among siblings.
 * @param count total number of siblings.
 */
@Immutable
data class CssIndex(val position: Int, val count: Int)
