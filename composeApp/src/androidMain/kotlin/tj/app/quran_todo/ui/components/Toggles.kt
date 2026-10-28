package tj.app.quran_todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** A 32dp rounded tile with [icon] in [tint] on a 14% wash of the same color: the leading icon of settings rows. */
@Composable
fun QhIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = QhTheme.colors.primary,
) {
    Box(
        modifier
            .size(32.dp)
            .clip(QhTheme.shapes.sm)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Hairline between rows of one group. [startIndent] aligns it with the text after the icon tile. */
@Composable
fun QhRowDivider(modifier: Modifier = Modifier, startIndent: Dp = 60.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(QhTheme.colors.hairline),
    )
}

/**
 * A settings row with a switch on the right. The whole row toggles, with the segment haptic.
 * Put several rows in a [QhCard] (zero content padding) separated by [QhRowDivider].
 */
@Composable
fun QhSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = QhTheme.colors.primary,
    enabled: Boolean = true,
) {
    val colors = QhTheme.colors
    SettingsRow(
        title = title,
        description = description,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = modifier
            .pressable(
                onClick = { onCheckedChange(!checked) },
                haptic = HapticKind.Segment,
                enabled = enabled,
                scale = 0.99f,
                role = Role.Switch,
            )
            .semantics(mergeDescendants = true) { toggleableState = ToggleableState(checked) },
        trailing = {
            // Purely visual: the row itself handles the click and the semantics.
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    checkedBorderColor = colors.primary,
                    uncheckedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.surfaceStrong,
                    uncheckedBorderColor = colors.surfaceStrong,
                    disabledCheckedThumbColor = colors.surface,
                    disabledCheckedTrackColor = colors.primary.copy(alpha = 0.4f),
                    disabledCheckedBorderColor = Color.Transparent,
                    disabledUncheckedThumbColor = colors.surface,
                    disabledUncheckedTrackColor = colors.surfaceMuted,
                    disabledUncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

/** A settings row that opens something: a chevron on the right. */
@Composable
fun QhNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = QhTheme.colors.primary,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        description = description,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = modifier.pressable(onClick = onClick, enabled = enabled, scale = 0.99f),
        trailing = { Chevron() },
    )
}

/** A settings row that shows a [value] on the right; with [onClick] it is pressable and gets a chevron. */
@Composable
fun QhValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = QhTheme.colors.primary,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        description = description,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = if (onClick != null) {
            modifier.pressable(onClick = onClick, enabled = enabled, scale = 0.99f)
        } else {
            modifier
        },
        trailing = {
            Text(
                text = value,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                maxLines = 1,
            )
            if (onClick != null) {
                Spacer(Modifier.width(QhSpacing.x1))
                Chevron()
            }
        },
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = QhTheme.colors.inkTertiary,
        modifier = Modifier.size(QhSpacing.iconBar),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String?,
    icon: ImageVector?,
    iconTint: Color,
    enabled: Boolean,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = QhTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) QhIconTile(icon, tint = if (enabled) iconTint else colors.inkTertiary)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = QhTheme.type.title2,
                color = if (enabled) colors.ink else colors.inkTertiary,
            )
            if (description != null) {
                Text(text = description, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/**
 * A Material 3 slider in our colors that plays the step haptic whenever the value crosses a step.
 * With [steps] > 0 every discrete value is a step; a continuous slider ticks every 5% of its range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QhSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = QhTheme.colors
    val haptics = LocalHaptics.current
    val divisions = if (steps > 0) steps + 1 else 20

    fun stepOf(v: Float): Int {
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) return 0
        return (((v - valueRange.start) / span) * divisions).roundToInt()
    }

    val lastStep = remember { intArrayOf(stepOf(value)) }

    Slider(
        value = value,
        onValueChange = {
            val step = stepOf(it)
            if (step != lastStep[0]) haptics.perform(HapticKind.Step)
            lastStep[0] = step
            onValueChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.surfaceStrong,
            activeTickColor = colors.onPrimary.copy(alpha = 0.6f),
            inactiveTickColor = colors.inkTertiary.copy(alpha = 0.6f),
            disabledThumbColor = colors.inkTertiary,
            disabledActiveTrackColor = colors.inkTertiary,
            disabledInactiveTrackColor = colors.surfaceMuted,
        ),
    )
}
