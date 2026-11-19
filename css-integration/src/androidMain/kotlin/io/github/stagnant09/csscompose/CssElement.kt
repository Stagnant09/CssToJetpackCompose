package io.github.stagnant09.csscompose

import io.github.stagnant09.csscompose.style.*
import io.github.stagnant09.csscompose.motion.*
import io.github.stagnant09.csscompose.layout.*

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** The resolved, possibly animated, style of one element plus everything needed to render it. */
internal class CssElementState(
    val style: StyleResult,
    /** Box modifier including scrolling and interaction handling. */
    val modifier: Modifier,
    /** Ancestor chain to provide to children. */
    val childAncestors: List<ElementInfo>,
)

/**
 * The shared pipeline behind `Div` and `CssText`:
 * 1. work out which interaction states any rule cares about, and track only those;
 * 2. resolve the style for the current state (cached in the stylesheet);
 * 3. run transitions / keyframe animations if any matching rule declares them;
 * 4. build the modifier with scrolling and click/hover/press/focus handling.
 */
@Composable
internal fun rememberCssElement(
    element: String,
    className: String,
    id: String,
    inline: String,
    enabled: Boolean,
    index: CssIndex?,
    onClick: (() -> Unit)?,
): CssElementState {
    val sheet = LocalCssStylesheet.current
    val ancestors = LocalCssAncestors.current
    val inheritedVariables = LocalCssVariables.current
    val media = rememberMediaContext()

    val baseInfo = remember(element, className, id, index) {
        ElementInfo(element, className.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet(), id.trim(), index = index)
    }
    val relevantStates = remember(sheet, baseInfo, media) { sheet.relevantStates(baseInfo, media) }
    val mayAnimate = remember(sheet, baseInfo, ancestors, media, inline) {
        sheet.mayAnimate(baseInfo, ancestors, media, inline)
    }

    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val states = buildSet {
        if (hovered && CssState.Hover in relevantStates) add(CssState.Hover)
        if (pressed && CssState.Active in relevantStates) add(CssState.Active)
        if (focused && CssState.Focus in relevantStates) add(CssState.Focus)
        if (!enabled) add(CssState.Disabled)
    }
    val info = if (states.isEmpty()) baseInfo else baseInfo.copy(states = states)

    val resolved = remember(sheet, info, ancestors, inheritedVariables, media, inline) {
        sheet.resolve(info, ancestors, inheritedVariables, media, inline)
    }
    val style = if (mayAnimate) animateStyle(resolved) else resolved

    val verticalScroll = if (style.box.overflowY == Overflow.Scroll) rememberScrollState() else null
    val horizontalScroll = if (style.box.overflowX == Overflow.Scroll) rememberScrollState() else null

    val indication = LocalIndication.current
    var interaction: Modifier = when {
        onClick != null -> Modifier.clickable(
            interactionSource = source,
            // If the CSS styles :active itself, don't draw a ripple on top of it
            indication = if (CssState.Active in relevantStates) null else indication,
            enabled = enabled,
            onClick = onClick,
        )
        CssState.Active in relevantStates && enabled -> Modifier.pointerInput(source) {
            detectTapGestures(onPress = { offset ->
                val press = PressInteraction.Press(offset)
                source.emit(press)
                val released = tryAwaitRelease()
                source.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
            })
        }
        else -> Modifier
    }
    if (CssState.Hover in relevantStates) interaction = interaction.hoverable(source, enabled)
    if (CssState.Focus in relevantStates && onClick == null) interaction = interaction.focusable(enabled, source)

    val modifier = style.buildModifier(verticalScroll, horizontalScroll, interaction)
    val childAncestors = remember(ancestors, info) { ancestors + info }
    return CssElementState(style, modifier, childAncestors)
}
