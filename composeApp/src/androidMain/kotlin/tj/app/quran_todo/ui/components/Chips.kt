package tj.app.quran_todo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The statuses a surah or an ayah can show. */
enum class QhStatus {
    NotStarted,
    Learning,
    Learned,

    /** Time to review, weak ayah. */
    Due,
}

/** Maps the stored surah status (`null` = not started) to [QhStatus]. */
fun SurahTodoStatus?.toQhStatus(): QhStatus = when (this) {
    SurahTodoStatus.LEARNED -> QhStatus.Learned
    SurahTodoStatus.LEARNING -> QhStatus.Learning
    null -> QhStatus.NotStarted
}

/**
 * Status of a surah or an ayah: an icon and a [label] (pass the localized text). The status is carried
 * by the icon's shape as well as by color. A change of status morphs the pill's width and colors. With
 * [onClick] it is pressable and shows a drop-down chevron.
 */
@Composable
fun StatusPill(
    status: QhStatus,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val (tint, containerTarget) = when (status) {
        QhStatus.NotStarted -> colors.inkSecondary to colors.surfaceMuted
        QhStatus.Learning -> colors.learning to colors.learningContainer
        QhStatus.Learned -> colors.learned to colors.learnedContainer
        QhStatus.Due -> colors.due to colors.dueContainer
    }
    val reduced = LocalReducedMotion.current
    val container by animateColorAsState(containerTarget, QhMotion.snappy<Color>().orSnap(), label = "pill-container")
    val iconTint by animateColorAsState(tint, QhMotion.snappy<Color>().orSnap(), label = "pill-tint")

    Row(
        modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick = onClick, shape = shape, highlight = false)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(container)
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
            .heightIn(min = 28.dp)
            .padding(start = 10.dp, end = if (onClick != null) 6.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(QhMotion.Short)) togetherWith fadeOut(tween(QhMotion.Short))
                }
            },
            label = "pill-icon",
        ) { target ->
            Icon(target.icon(), contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        // Text stays `ink`: the status colors are too light for 13sp text on their containers.
        Text(label, style = QhTheme.type.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClick != null) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.inkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun QhStatus.icon(): ImageVector = when (this) {
    QhStatus.NotStarted -> Icons.Rounded.StarBorder
    QhStatus.Learning -> Icons.AutoMirrored.Rounded.StarHalf
    QhStatus.Learned -> Icons.Rounded.Star
    QhStatus.Due -> Icons.Rounded.Refresh
}

/**
 * A selectable chip (filter, choice). When [selected] it is tinted `primaryContainer` and a check
 * slides in. Plays the segment haptic. Visually 36dp high, 48dp to touch.
 */
@Composable
fun QhChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val reduced = LocalReducedMotion.current
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceMuted,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "chip-container",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.inkTertiary
            selected -> colors.onPrimaryContainer
            else -> colors.inkSecondary
        },
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "chip-content",
    )

    Row(
        modifier
            .minimumInteractiveComponentSize()
            .pressable(
                onClick = onClick,
                haptic = HapticKind.Segment,
                enabled = enabled,
                shape = shape,
                highlight = false,
                role = Role.Checkbox,
            )
            .semantics { this.selected = selected }
            .clip(shape)
            .background(container)
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                expandHorizontally(QhMotion.snappy(), expandFrom = Alignment.Start) +
                    fadeIn(QhMotion.snappy()) + scaleIn(QhMotion.snappy(), initialScale = 0.4f)
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                shrinkHorizontally(QhMotion.snappy(), shrinkTowards = Alignment.Start) +
                    fadeOut(QhMotion.snappy()) + scaleOut(QhMotion.snappy(), targetScale = 0.4f)
            },
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(QhSpacing.iconInline - 4.dp),
            )
        }
        if (!selected && leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(QhSpacing.iconInline - 4.dp),
            )
        }
        Text(label, style = QhTheme.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
