package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

// -- Shared pieces of the steps ----------------------------------------------------------------------

/** A screen-sized, scrolling column with the 20dp screen inset; most steps are one of these. */
@Composable
internal fun StepColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x2, bottom = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
    ) { content() }
}

/** Text that crossfades when [text] changes (a language switch re-renders the copy of the screen). */
@Composable
internal fun CrossfadeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val reduced = LocalReducedMotion.current
    Crossfade(
        targetState = text,
        modifier = modifier.animateContentSize(QhMotion.gentle<IntSize>().orSnap()),
        animationSpec = if (reduced) snap() else tween(QhMotion.Medium),
        label = "onboarding-copy",
    ) { shown ->
        Text(
            text = shown,
            style = style,
            color = color,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The title (a heading for screen readers) and the line under it: one question per screen. */
@Composable
internal fun StepHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        CrossfadeText(
            text = title,
            style = QhTheme.type.headline,
            color = QhTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        CrossfadeText(text = subtitle, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
    }
}

/**
 * A choice card (a goal, a preset): tinted with a primary outline when [selected], dimmed and not pressable when not [enabled].
 * [role] is a radio button for a single choice. The content is laid out in a column.
 */
@Composable
internal fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "choice-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "choice-outline",
    )
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, haptic = HapticKind.Segment, enabled = enabled, shape = shape, highlight = false, role = role)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
            .padding(QhSpacing.cardLarge),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        content = content,
    )
}

/** Index of the theme segment: 0 light, 1 dark, 2 follow the system. */
internal fun themeSegment(followsSystem: Boolean, mode: ThemeMode): Int = when {
    followsSystem -> 2
    mode == ThemeMode.DARK -> 1
    else -> 0
}
