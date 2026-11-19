A library that bridges CSS with Jetpack Compose. Write familiar CSS — inline, in stylesheets with selectors, media queries and variables, or in a `@CssStyle` annotation — and it resolves into native `Modifier`s, `TextStyle`s, a flexbox layout, and Compose animations.

<img width="877" height="708" alt="Στιγμιότυπο οθόνης 2026-02-22 133926" src="https://github.com/user-attachments/assets/d98db566-6416-4b10-baa9-af194c539594" />

## Modules

| Module | Contents |
|--------|----------|
| `css-integration` | The library, Kotlin Multiplatform. **Android**: parser, cascade and stylesheets, motion, flex layout, `Div`/`CssText`. **Web** (Compose HTML): `Div`/`CssText` as real DOM elements (see [Web](#web)). **Common**: the shared `Div`/`CssText`/`CssStylesheetProvider` API, `CssIndex` and `@CssStyle`. |
| `css-processor` | KSP processor that validates `@CssStyle` strings at compile time (optional). |
| `lint` | Lint check for `rememberParsedCss()` without `@CssStyle` (optional). |
| `sample-shared` | The showcase cards, written once against the shared API (Android + web). |
| `app` | Android example app (`io.github.stagnant09.csscompose.sample`): the shared cards plus two Android-only ones, styled by `ui/theme/css/styles.css`, which is packaged as an asset. |
| `web-sample` | Browser example app: the same shared cards and the same `styles.css`, as real DOM. |

### Packages

| Package | Contents |
|---------|----------|
| `io.github.stagnant09.csscompose` | The public entry points: `Div`, `CssText`, `@CssStyle`, `CssStylesheetProvider`, `CssStylesheet`, `rememberParsedCss`, `rememberCssStyle`, `rememberCss`, `CssIndex`, `CssFonts`, `MediaContext`, `StyleResult`. |
| `…csscompose.style` | Parsing and values: declarations, cascade, colors, lengths, `var()`/`calc()`, the style model and the `Modifier` builder. |
| `…csscompose.motion` | Transitions, `@keyframes`, timing functions and `animateStyle`. |
| `…csscompose.layout` | `display` parsing, the flexbox algorithm and the `Wrap` layout types. |

Planned publishing coordinates are `io.github.stagnant09:css-compose`, `:css-compose-processor` and `:css-compose-lint`. Publishing isn't configured yet.

To use the library from another module:

```kotlin
dependencies {
    implementation(project(":css-integration"))
    ksp(project(":css-processor"))     // optional: compile-time @CssStyle validation
    lintChecks(project(":lint"))       // optional
}
```

## Quick start

```kotlin
import io.github.stagnant09.csscompose.*

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
  - Effects: `opacity`, `transform`, `filter: blur()`, the first `box-shadow`, `outline` (width, color, offset), `font-size`, `letter-spacing`.
  - Colors interpolate premultiplied, like browsers, so fading from `transparent` doesn't go grey. Anything else changes instantly.
- **Performance**: as on the web, prefer animating `transform` and `opacity`, which only redraw. Animating `margin`, `padding` or `width` changes layout on every frame.

## Supported properties

| Group | Properties |
|-------|------------|
| Typography | `color`, `font` (shorthand), `font-size`, `font-weight`, `font-style`, `font-family`, `text-align`, `letter-spacing`, `line-height`, `text-decoration`, `text-transform`, `text-shadow`, `white-space`, `text-overflow`, `line-clamp` |
| Box model | `margin`, `padding` (and `-top/-right/-bottom/-left`), `width`, `height`, `min-/max-width`, `min-/max-height`, `aspect-ratio` |
| Borders | `border`, `border-width/style/color` (1–4 values), `border-top/right/bottom/left` (+ `-width/-style/-color`), `border-radius` and per-corner `border-*-radius`. `solid`, `dashed` and `dotted` are drawn as such; other styles are drawn solid. |
| Outline | `outline`, `outline-width`, `outline-style`, `outline-color`, `outline-offset` (negative values draw inside). Drawn on top of the content, takes no space, and follows the corner radii. |
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
- A border or outline with only a width or a color is drawn solid. Browsers draw nothing without a style. `outline-style: auto` is drawn solid.
- When a border differs per side, it's drawn as straight edges and ignores corner radii.
- Negative margins and padding are clamped to 0.
- `transform` is normalised to translate → rotate → scale. `skew()`, `matrix()` and percentage translations aren't supported.
- Inset `box-shadow`s are ignored.
- Grid supports equal-width columns only.
- `position: fixed` behaves like `absolute`, and `sticky` like `relative`.

## Web

On the web (Kotlin/JS with [Compose HTML](https://github.com/JetBrains/compose-multiplatform#compose-html)) the library doesn't parse or lay anything out. It renders real DOM and lets the browser apply your CSS:

| You write | The browser gets |
|-----------|------------------|
| `Div(id = "hero", className = "card accent")` | `<div id="hero" class="card accent">`. Exactly those attributes: nothing added, and none when blank. |
| `CssText("Hi", className = "title")` | `<text class="title">Hi</text>`, a `text` element, so `text { … }` rules match on both platforms. |
| `css = "color: red; padding: 4px"` | The element's inline `style`. |
| `onClick = { … }` | A click handler, plus `tabindex="0"` and `role="button"`, so Tab focuses it and Enter or Space activates it. |
| `enabled = false` | `aria-disabled="true"` (no click handler). |
| `CssStylesheetProvider(css) { … }` | A `<style>` element in `<head>`, removed when the provider leaves the composition. |

Code that only uses `Div`, `CssText`, `CssStylesheetProvider`, `CssIndex` and Compose runtime state (`remember`, `mutableStateOf`…) can live in `commonMain` and render on both platforms. `sample-shared` does exactly that. Android also keeps overloads of `Div`/`CssText` that take a `Modifier`.

How the stylesheet is adapted for the browser:
- `:disabled` and `:enabled` only match form controls in browsers, so they're rewritten to `[aria-disabled="true"]` and its negation.
- A small base sheet goes first:
  - `box-sizing: border-box`, as on Android.
  - `text` displays as a block, so text stacks like it does in a `Div`.
  - Disabled elements get `pointer-events: none`.
  - The `text` and disabled rules use `:where()`, which has zero specificity, so any rule of yours wins. The `box-sizing` rule is plain `*` (also zero specificity) because pseudo-elements can't go inside `:where()`.

Differences from Android:
- **`index`/`CssIndex` is ignored.** The browser counts siblings itself, including every sibling element. Give list items their own parent `Div` so both platforms count the same elements.
- **Layout and drawing follow the browser,** including the places where this library deliberately differs from CSS (see [Differences from browser CSS](#differences-from-browser-css)). For example, a `Div` without `display` is full width in the browser.
- **Not available on the web:** `rememberParsedCss`, `rememberCssStyle`, `StyleResult` and the other Android APIs. Use `Div`/`CssText` with `css` or `className` instead.

## Tooling

- **KSP validator** (`css-processor`): checks every `@CssStyle` string at compile time. A malformed declaration is an error. An unknown property is a warning with a suggestion (`'colr'` → *did you mean 'color'?*). A unit test keeps its property list in sync with the runtime parser.
- **Lint check** (`lint`): `MissingCssStyleAnnotation` flags composables that call `rememberParsedCss()` without `@CssStyle`.

## Building

Use Gradle 8.13 (AGP 8.3.2 doesn't work with Gradle 9) with JDK 17 or 21. Point `JAVA_HOME` at one of those, or use Android Studio. Unit tests, including the shared ones, run on the JVM:

```bash
gradle :css-integration:testDebugUnitTest
```

- Android example app: `gradle :app:installDebug`
- Web example app: `gradle :web-sample:jsBrowserDevelopmentRun` starts a dev server. `gradle :web-sample:jsBrowserDistribution` writes static files to `web-sample/build/dist/js/productionExecutable`.

Kotlin/JS keeps the npm dependency lock in `kotlin-js-store/yarn.lock`. Commit it. Its Node.js and Yarn download repositories are declared in `settings.gradle.kts`, which is why it uses `RepositoriesMode.PREFER_SETTINGS`.
