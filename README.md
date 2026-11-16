A library that bridges CSS with Jetpack Compose. Write familiar CSS — inline, in stylesheets with selectors, media queries and variables, or in a `@CssStyle` annotation — and it resolves into native `Modifier`s, `TextStyle`s, a flexbox layout, and Compose animations.

<img width="877" height="708" alt="Στιγμιότυπο οθόνης 2026-02-22 133926" src="https://github.com/user-attachments/assets/d98db566-6416-4b10-baa9-af194c539594" />

## Quick start

```kotlin
CssStylesheetProvider("""
    :root  { --accent: #E91E63; }
    .card  { padding: 16px; border-radius: 16px; background: #fff;
             box-shadow: 0 4px 12px rgba(0, 0, 0, .15); }
    .card > .title { font: 700 20px sans-serif; }
    .button { padding: 12px 20px; border-radius: 12px; background: var(--accent); color: white;
              transition: background-color 250ms, transform 150ms ease-out; }
    .button:active { background: #AD1457; transform: scale(.95); }
    @keyframes pulse { 50% { transform: scale(1.3); opacity: .5; } }
    .dot { width: 12px; height: 12px; border-radius: 50%; background: var(--accent);
           animation: pulse 1.2s ease-in-out infinite; }
    @media (min-width: 600px) { .card { padding: 32px; } }
""") {
    Div(className = "card") {
        CssText("Hello", className = "title")
        Div(className = "dot") {}
        Div(className = "button", onClick = { /* … */ }) { CssText("Tap me") }
    }
}
```

## APIs

| API | What it does |
|-----|--------------|
| `Div(css, className, id, enabled, index, onClick) { … }` | HTML-like container: flexbox / grid / block / inline / box layout, decoration, interaction states, transitions and animations. It passes inheritable text styles and CSS variables down to its children. |
| `CssText(text, css, className, id, enabled, index, onClick)` | `Text` styled with CSS, including `text-transform`, `text-overflow` and `line-clamp`. |
| `CssStylesheetProvider(css) { … }` | Makes a stylesheet available below it. Nested providers extend the parent sheet. |
| `rememberParsedCss(css)` | Parses inline CSS into a `StyleResult` (`.modifier`, `.toTextStyle()`). |
| `rememberCssStyle<T>()` | Reads `@CssStyle("…")` from class/object `T`. |
| `rememberCss(element, className, id, inline)` | Resolves a static style against the current stylesheet. |
| `animateStyle(style)` | Runs a `StyleResult`'s transitions and keyframes (used by `Div`/`CssText`). |
| `CssStylesheet.parse(css).resolve(…)` | Non-composable stylesheet API, handy for tests. |
| `CssFonts.register(name, family)` | Lets `font-family` refer to your own fonts. |

## Selectors and cascade

Styles resolve the CSS way:
1. Matching rules are sorted by specificity (`#id` > `.class`/pseudo-class > element), then by source order.
2. The element's inline `css` is applied on top.
3. `!important` wins over any declaration that isn't `!important`.

Element names are `div` (for `Div`) and `text` (for `CssText`).

| Supported | Examples |
|-----------|----------|
| Compound selectors | `div.card.active`, `#hero`, `*` |
| Descendant and child combinators | `.card .title`, `.list > .item` |
| Interaction states | `:hover`, `:active`, `:focus`, `:disabled`, `:enabled` |
| Structural | `:first-child`, `:last-child`, `:only-child`, `:nth-child(2n+1)`, `:nth-last-child()` |
| Selector lists, comments | `.a, .b { … }`, `/* … */` |
| At-rules | `@media` (nestable), `@keyframes`, `@supports` (assumed true) |

`Div`/`CssText` only listen for hover, press and focus when a rule actually styles that state. Passing `onClick` also makes an element clickable. When the CSS styles `:active`, no ripple is drawn on top.

**Structural pseudo-classes need an index.** Compose can't see an element's siblings, so pass the position yourself:

```kotlin
items.forEachIndexed { i, item -> Div(className = "row", index = CssIndex(i, items.size)) { … } }
```

Not supported: the sibling combinators `+` and `~`, attribute selectors, pseudo-elements (`::before`), and `:not()`.

### Media queries, variables and math

- **`@media`**:
  - Features: `width`, `height` and `aspect-ratio` (with `min-`/`max-` or range syntax such as `(width >= 600px)`), `orientation`, `prefers-color-scheme`, `prefers-reduced-motion`.
  - Logic: `not`, `and`, and comma (or).
  - Evaluated against the current window size, the system dark theme, and the system animation scale.
- **Custom properties**:
  - `--name: value` declarations inherit down the `Div` tree.
  - `:root { … }` defines them for everything inside a `CssStylesheetProvider`.
  - Use them with `var(--name, fallback)`. If a `var()` can't be resolved and has no fallback, the declaration is dropped.
- **`calc()`, `min()`, `max()`, `clamp()`**: supported with `px`/`dp`/`rem`/`em` and plain numbers. Mixing a length with `%` (`calc(100% - 8px)`) can't be resolved without layout, so that declaration is dropped.
- **`initial`, `unset`, `revert`**: reset a property. `inherit` works for text properties (they inherit anyway) and acts like `initial` for box properties.

## Motion

```css
.chip        { background: #eee; transition: background-color 200ms ease, transform 150ms; }
.chip:active { background: #ddd; transform: scale(0.97); }

@keyframes spin { to { transform: rotate(360deg); } }
.spinner { animation: spin 1s linear infinite; }
```

- **`transition`**: the shorthand and its longhands are supported, with comma lists, `all`, durations, delays and timing functions. An interrupted transition reverses from its current value, as in CSS.
- **`animation` / `@keyframes`**:
  - The shorthand and its longhands: duration, delay, timing function, iteration count (`infinite` too), direction (`alternate`…), fill mode and play state.
  - Keyframes can use `from`/`to`/percentages and lists like `0%, 100%`.
  - Each keyframe can set its own `animation-timing-function`.
- **Timing functions**: `linear`, `ease`, `ease-in`, `ease-out`, `ease-in-out`, `cubic-bezier()`, `steps()`, `step-start`, `step-end`.
- **What animates**:
  - Colors: `color`, `background-color`, border colors.
  - Sizes: border widths, `margin`, `padding`, fixed `width`/`height`, fixed `border-radius`.
  - Effects: `opacity`, `transform`, `filter: blur()`, the first `box-shadow`, `font-size`, `letter-spacing`.
  - Colors interpolate premultiplied, like browsers, so fading from `transparent` doesn't go grey. Anything else changes instantly.
- **Performance**: as on the web, prefer animating `transform` and `opacity`, which only redraw. Animating `margin`, `padding` or `width` changes layout on every frame.

## Supported properties

| Group | Properties |
|-------|------------|
| Typography | `color`, `font` (shorthand), `font-size`, `font-weight`, `font-style`, `font-family`, `text-align`, `letter-spacing`, `line-height`, `text-decoration`, `text-transform`, `text-shadow`, `white-space`, `text-overflow`, `line-clamp` |
| Box model | `margin`, `padding` (and `-top/-right/-bottom/-left`), `width`, `height`, `min-/max-width`, `min-/max-height`, `aspect-ratio` |
| Borders | `border`, `border-width/style/color` (1–4 values), `border-top/right/bottom/left` (+ `-width/-style/-color`), `border-radius` and per-corner `border-*-radius`. `solid`, `dashed` and `dotted` are drawn as such; other styles are drawn solid. |
| Decoration | `background` / `background-color` / `background-image` (colors, `linear-gradient`, `radial-gradient`), `box-shadow` (multiple, with spread), `opacity`, `visibility`, `filter` (`blur`, `grayscale`, `sepia`, `saturate`, `hue-rotate`, `invert`, `brightness`, `contrast`, `opacity`), `transform` (`translate`, `rotate`, `scale`), `z-index` |
| Overflow | `overflow`, `overflow-x`, `overflow-y`: `hidden` clips to the border radius, `auto`/`scroll` scroll. |
| Layout (`Div`) | `display` (`flex`, `inline-flex`, `grid`, `block`, `inline`, `inline-block`, `box`, `none`), `flex-direction`, `flex-wrap`, `flex-flow`, `justify-content`, `align-items`, `align-content`, `gap`, `row-gap`, `column-gap`, `grid-template-columns` |
| Flex items | `flex`, `flex-grow`, `flex-shrink`, `flex-basis`, `align-self`, `order` |
| Positioning | `position: relative` (offset) and `absolute` (placed against the parent `Div`), `top`, `right`, `bottom`, `left`, `inset` |
| Motion | `transition*`, `animation*` |

**Colors:** `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`/`rgba()` (comma or space syntax, `/ alpha`), `hsl()`/`hsla()`, all CSS named colors, `transparent`, `currentColor`.

**Units:** `px`, `dp` and bare numbers map to `dp` (`sp` for text). `rem` is 16× the number. `em` is `16dp` for boxes and Compose `em` for text. `%` works for `width`/`height`, `flex-basis`, insets, `border-radius`, `opacity` and text sizes. A unitless `line-height` is a multiplier, as in CSS.

### Layout model

`Div` runs a real flexbox algorithm:
- `flex-grow` and `flex-shrink` (items never shrink below their min-content size).
- `flex-basis`, wrapping into lines, and `gap`.
- `justify-content` and `align-content` distribution, `align-items`/`align-self` (default `stretch`), `order`, and `*-reverse`.

Block flow (`display: block`, or no `display`) stacks children. Block-level children (`display: block | flex | grid`) stretch to the full width; text and inline content don't. A `Div` without a `display` shrinks to fit its content.

### Differences from browser CSS

- Sizes behave like `box-sizing: border-box`.
- A border with only a width or a color is drawn solid. Browsers draw nothing without a style.
- When a border differs per side, it's drawn as straight edges and ignores corner radii.
- Negative margins and padding are clamped to 0.
- `transform` is normalised to translate → rotate → scale. `skew()`, `matrix()` and percentage translations aren't supported.
- Inset `box-shadow`s are ignored.
- Grid supports equal-width columns only.
- `position: fixed` behaves like `absolute`, and `sticky` like `relative`.

## Tooling

- **KSP validator** (`css-processor`): checks every `@CssStyle` string at compile time. A malformed declaration is an error. An unknown property is a warning with a suggestion (`'colr'` → *did you mean 'color'?*). A unit test keeps its property list in sync with the runtime parser.
- **Lint check** (`lint`): `MissingCssStyleAnnotation` flags composables that call `rememberParsedCss()` without `@CssStyle`.

## Building

Gradle 8.13 needs JDK 17 or 21. Point `JAVA_HOME` at one of those, or use Android Studio's bundled JBR. Unit tests run on the JVM:

```bash
gradle :app:testDebugUnitTest
```
