@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.home.LessonCardState
import tj.app.quran_todo.ui.home.LessonCardUi
import tj.app.quran_todo.ui.home.LessonLineKind
import tj.app.quran_todo.ui.home.LessonLineUi
import tj.app.quran_todo.ui.home.LessonTomorrowUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "Today's lesson" (docs/redesign/PRODUCT_PLAN.md, 3.4): up to three lines with checkmarks (New, Recent,
 * Old), the time estimate and one button that starts, continues or (when everything is done) only reports
 * the lesson; with no plan it invites to make one, with a finished plan it offers a new one.
 *
 * Opening the lesson grows the lesson screen out of the card (`SharedKeys.LessonCard`). A long press or the
 * "..." button leads to "My plan".
 */
@Composable
internal fun LessonCard(
    card: LessonCardUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card.state) {
        LessonCardState.NoPlan -> PlanInvitation(
            icon = Icons.Rounded.School,
            title = copy.lessonInviteTitle,
            body = copy.lessonInviteBody,
            action = copy.lessonMakePlan,
            onAction = onOpenPlan,
            modifier = modifier,
        )

        LessonCardState.PlanFinished -> PlanInvitation(
            icon = Icons.Rounded.Eco,
            title = copy.lessonPlanFinished,
            body = copy.lessonPlanFinishedBody,
            action = copy.lessonNewPlan,
            onAction = onOpenPlan,
            modifier = modifier,
        )

        LessonCardState.Start, LessonCardState.Continue, LessonCardState.Done ->
            LessonBody(card, copy, onOpenLesson, onOpenPlan, modifier)
    }
}

@Composable
private fun LessonBody(
    card: LessonCardUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val done = card.state == LessonCardState.Done
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .sharedBoundsKey(SharedKeys.LessonCard, SharedResize.Remeasure),
        onClick = onOpenLesson,
        onLongClick = onOpenPlan,
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(start = QhSpacing.cardLarge, top = QhSpacing.cardLarge, end = QhSpacing.x2, bottom = QhSpacing.cardLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = copy.lessonTitle.uppercase(),
                style = type.overline,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!done && card.minutes > 0) {
                Text(
                    text = if (card.state == LessonCardState.Continue) copy.lessonMinutesLeft(card.minutes) else copy.lessonMinutes(card.minutes),
                    style = type.callout,
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
            }
            PlanMenu(copy, onOpenPlan)
        }
        Column(Modifier.padding(end = QhSpacing.x3)) {
            Spacer(Modifier.height(QhSpacing.x1))
            card.lines.forEach { line -> LessonLine(line, copy) }
            if (card.catchingUp) {
                Spacer(Modifier.height(QhSpacing.x2))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = colors.due, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(copy.lessonCatchingUp, style = type.callout, color = colors.inkSecondary)
                }
            }
            Spacer(Modifier.height(QhSpacing.x4))
            if (done) {
                QhButton(
                    text = copy.lessonDone,
                    onClick = onOpenLesson,
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    icon = Icons.Rounded.Check,
                )
                card.tomorrow?.let { tomorrow ->
                    Spacer(Modifier.height(QhSpacing.x2))
                    Text(
                        text = tomorrowText(tomorrow, copy),
                        style = type.callout,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = QhSpacing.x1),
                    )
                }
            } else {
                QhButton(
                    text = if (card.state == LessonCardState.Continue) copy.lessonContinue else copy.lessonStart,
                    onClick = onOpenLesson,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PlayArrow,
                )
            }
        }
    }
}

/** The "..." button of the card and its menu with one entry, "My plan". */
@Composable
private fun PlanMenu(copy: HomeCopy, onOpenPlan: () -> Unit) {
    val colors = QhTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        QhIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = copy.lessonMore,
            onClick = { open = true },
            filled = false,
            tint = colors.inkSecondary,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surface,
        ) {
            DropdownMenuItem(
                text = { Text(copy.myPlan, style = QhTheme.type.title2, color = colors.ink) },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.primary) },
                onClick = {
                    open = false
                    onOpenPlan()
                },
            )
        }
    }
}

@Composable
private fun LessonLine(line: LessonLineUi, copy: HomeCopy) {
    val colors = QhTheme.colors
    val text = lineText(line, copy)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = QhSpacing.x1)
            .semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        CheckDot(done = line.done)
        Text(
            text = text,
            style = QhTheme.type.body,
            color = if (line.done) colors.inkSecondary else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 22dp circle: empty until the line is done, then solid with a checkmark (shape and fill, not only color). */
@Composable
private fun CheckDot(done: Boolean) {
    val colors = QhTheme.colors
    val fill by animateColorAsState(
        targetValue = if (done) colors.learned else Color.Transparent,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "lesson-dot-fill",
    )
    val outline by animateColorAsState(
        targetValue = if (done) colors.learned else colors.inkTertiary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "lesson-dot-outline",
    )
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onLearned, modifier = Modifier.size(14.dp))
    }
}

/** The invitation to make a plan, and the "plan complete" card: an icon, a title, a line and one button. */
@Composable
private fun PlanInvitation(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    QhCard(
        modifier = modifier.fillMaxWidth(),
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(QhSpacing.cardLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.goldContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = QhTheme.type.title1, color = colors.ink)
                Spacer(Modifier.height(QhSpacing.x1))
                Text(body, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        Spacer(Modifier.height(QhSpacing.x4))
        QhButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The same lesson as one calm line, for people whose goal is reading and listening: an icon tile, "Today's
 * lesson", what it is right now, and a chevron. Opens the lesson (or the plan when there is none).
 */
@Composable
internal fun LessonInviteRow(
    card: LessonCardUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val opensLesson = card.state == LessonCardState.Start || card.state == LessonCardState.Continue || card.state == LessonCardState.Done
    val subtitle = when (card.state) {
        LessonCardState.Start -> copy.lessonMinutes(card.minutes)
        LessonCardState.Continue -> "${copy.lessonContinue} · ${copy.lessonMinutesLeft(card.minutes)}"
        LessonCardState.Done -> copy.lessonDone
        LessonCardState.NoPlan -> copy.lessonInviteTitle
        LessonCardState.PlanFinished -> copy.lessonPlanFinished
    }
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (opensLesson) Modifier.sharedBoundsKey(SharedKeys.LessonCard, SharedResize.Remeasure) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "${copy.lessonTitle}. $subtitle" },
        onClick = if (opensLesson) onOpenLesson else onOpenPlan,
        onLongClick = onOpenPlan,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            QhIconTile(Icons.Rounded.School, tint = colors.gold)
            Column(Modifier.weight(1f)) {
                Text(copy.lessonTitle, style = QhTheme.type.title2, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.inkTertiary,
                modifier = Modifier.size(QhSpacing.iconBar),
            )
        }
    }
}

/** "New: An-Naba 1–5", "Recent: 3 passages", "Old: Al-Mulk 1–15 +2". */
internal fun lineText(line: LessonLineUi, copy: HomeCopy): String {
    val range = passageRangeLabel(line.surahName, line.startAyah, line.endAyah)
    return when (line.kind) {
        LessonLineKind.New -> copy.lessonNew(range)
        LessonLineKind.Recent -> copy.lessonRecent(line.count, line.doneCount)
        LessonLineKind.Old -> copy.lessonOld(if (line.count > 1) "$range +${line.count - 1}" else range)
    }
}

/** "Tomorrow: An-Naba 6–10 · 2 to review". */
internal fun tomorrowText(tomorrow: LessonTomorrowUi, copy: HomeCopy): String = copy.lessonTomorrow(
    newRange = if (tomorrow.hasNew) passageRangeLabel(tomorrow.surahName, tomorrow.startAyah, tomorrow.endAyah) else null,
    dueCount = tomorrow.dueCount,
)
