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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhDialogButton
import tj.app.quran_todo.ui.home.LessonCardState
import tj.app.quran_todo.ui.home.LessonCardUi
import tj.app.quran_todo.ui.home.LessonLineKind
import tj.app.quran_todo.ui.home.LessonLineUi
import tj.app.quran_todo.ui.home.LessonTomorrowUi
import tj.app.quran_todo.ui.home.ListeningUi
import tj.app.quran_todo.ui.home.OldListenUi
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.rememberLessonCardCopy
import tj.app.quran_todo.ui.i18n.rememberPauseCopy
import tj.app.quran_todo.ui.i18n.welcomeBackCopy
import tj.app.quran_todo.ui.lesson.formatDay
import tj.app.quran_todo.ui.i18n.ListenCopy
import tj.app.quran_todo.ui.i18n.rememberListenCopy
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
 * Opening the lesson grows the lesson screen out of the card (`SharedKeys.LessonCard`). A long press leads to "My plan"; the plan row
 * under the card (task U15) is the visible way there.
 */
@Composable
internal fun LessonCard(
    card: LessonCardUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Listen to the old": opens the queue; the button shows only when today's portion of the circle has passages. */
    onListenOld: () -> Unit = {},
    listening: ListeningUi? = null,
    /** "Continue now" of a paused plan (asked to confirm first). */
    onResumePlan: () -> Unit = {},
) {
    when (card.state) {
        LessonCardState.Paused -> PausedCard(card = card, onResume = onResumePlan, onOpenPlan = onOpenPlan, modifier = modifier)

        // The goal "read and listen": one calm line and a secondary button, not a call to action.
        LessonCardState.NoPlan -> if (card.quietInvite) {
            PlanInvitation(
                icon = Icons.Rounded.School,
                title = null,
                body = rememberLessonCardCopy().quietInvite,
                action = copy.lessonMakePlan,
                onAction = onOpenPlan,
                modifier = modifier,
                primary = false,
            )
        } else {
            PlanInvitation(
                icon = Icons.Rounded.School,
                title = copy.lessonInviteTitle,
                body = copy.lessonInviteBody,
                action = copy.lessonMakePlan,
                onAction = onOpenPlan,
                modifier = modifier,
            )
        }

        LessonCardState.PlanFinished -> PlanInvitation(
            icon = Icons.Rounded.Eco,
            title = copy.lessonPlanFinished,
            body = copy.lessonPlanFinishedBody,
            action = copy.lessonNewPlan,
            onAction = onOpenPlan,
            modifier = modifier,
        )

        LessonCardState.Start, LessonCardState.Continue, LessonCardState.Done, LessonCardState.Returning ->
            LessonBody(card, copy, onOpenLesson, onOpenPlan, modifier, onListenOld, listening)
    }
}

/**
 * The card of a paused plan (task F2b): "Plan paused until 11 November", when the lessons return, and "Continue now" (which asks to
 * confirm: it moves the calendar back). A tap on the card opens "My plan" (where the pause is extended). The quiz row stays under
 * it: checking yourself is possible during a pause.
 */
@Composable
private fun PausedCard(card: LessonCardUi, onResume: () -> Unit, onOpenPlan: () -> Unit, modifier: Modifier = Modifier) {
    val pause = rememberPauseCopy()
    val language = LocalAppLanguage.current
    val colors = QhTheme.colors
    var confirm by rememberSaveable { mutableStateOf(false) }
    PlanInvitation(
        icon = Icons.Rounded.Pause,
        title = pause.cardTitle(formatDay(card.pausedUntilEpochDay, language)),
        body = pause.cardBody(formatDay(card.pausedUntilEpochDay + 1, language)),
        action = pause.resumeNow,
        onAction = { confirm = true },
        modifier = modifier,
        // A tap on the card leads to "My plan", where the pause can be extended.
        onClick = onOpenPlan,
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = colors.surface,
            title = { Text(pause.resumeTitle, style = QhTheme.type.title1, color = colors.ink) },
            text = { Text(pause.resumeBody, style = QhTheme.type.body, color = colors.inkSecondary) },
            confirmButton = {
                QhDialogButton(text = pause.resumeConfirm, color = colors.primary, onClick = {
                    confirm = false
                    onResume()
                })
            },
            dismissButton = {
                QhDialogButton(text = pause.cancel, color = colors.inkSecondary, onClick = { confirm = false })
            },
        )
    }
}

@Composable
private fun LessonBody(
    card: LessonCardUi,
    copy: HomeCopy,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier,
    onListenOld: () -> Unit,
    listening: ListeningUi?,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val listen = rememberListenCopy()
    val cardCopy = rememberLessonCardCopy()
    val language = LocalAppLanguage.current
    val welcome = remember(language) { welcomeBackCopy(language) }
    val done = card.state == LessonCardState.Done
    val returning = card.state == LessonCardState.Returning
    // A tap on the body does what the main control does: starts or continues the lesson, or (when done) opens its summary,
    // the same as the link "Lesson summary".
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
        }
        Column(Modifier.padding(end = QhSpacing.x3)) {
            if (returning) {
                Spacer(Modifier.height(QhSpacing.x1))
                Text(welcome.title, style = type.title1, color = colors.ink)
                Text(welcome.missed(card.missedDays), style = type.callout, color = colors.inkSecondary)
            }
            Spacer(Modifier.height(QhSpacing.x1))
            card.lines.forEach { line -> LessonLine(line, copy, listen) }
            if (card.catchingUp) {
                Spacer(Modifier.height(QhSpacing.x2))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = colors.due, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(copy.lessonCatchingUp, style = type.callout, color = colors.inkSecondary)
                }
            }
            card.lighterUntilEpochDay?.let { until ->
                Spacer(Modifier.height(QhSpacing.x2))
                Text(
                    text = welcome.lighterLine(formatDay(until, language)),
                    style = type.callout,
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(QhSpacing.x4))
            if (done) {
                // "Lesson done" is a status, not a button: no ripple, no click. The summary is the link under "Tomorrow".
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { contentDescription = copy.lessonDone },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(22.dp))
                    Text(copy.lessonDone, style = type.title2, color = colors.ink)
                }
                card.tomorrow?.let { tomorrow ->
                    Spacer(Modifier.height(QhSpacing.x2))
                    Text(
                        text = tomorrowText(tomorrow, copy),
                        style = type.callout,
                        color = colors.inkSecondary,
                    )
                }
                QhButton(
                    text = cardCopy.lessonSummary,
                    onClick = onOpenLesson,
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Ghost,
                    size = QhButtonSize.M,
                )
            } else {
                QhButton(
                    text = when (card.state) {
                        LessonCardState.Continue -> copy.lessonContinue
                        LessonCardState.Returning -> cardCopy.continueMemorizing
                        else -> copy.lessonStart
                    },
                    onClick = onOpenLesson,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PlayArrow,
                )
            }
            card.oldListen?.let { old -> ListenOldButton(old, listening, listen, onListenOld) }
        }
    }
}

/**
 * "Listen to the old - about 10 min" (task F1b): today's portion of the circle as audio, for the road or with the screen off. It is
 * a help, not a repetition: it never moves the lesson or the circle. While the queue plays it says where it is.
 */
@Composable
private fun ListenOldButton(old: OldListenUi, listening: ListeningUi?, listen: ListenCopy, onClick: () -> Unit) {
    Spacer(Modifier.height(QhSpacing.x2))
    QhButton(
        text = if (listening != null) listen.oldPlaying(listening.position, listening.total) else listen.oldButton(old.minutes),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = QhButtonVariant.Ghost,
        size = QhButtonSize.M,
        icon = Icons.Rounded.Headphones,
    )
}

@Composable
private fun LessonLine(line: LessonLineUi, copy: HomeCopy, listen: ListenCopy) {
    val colors = QhTheme.colors
    // "Old: Al-Mulk 1-15 +2 - Listened": listening is shown, it does not make the line done.
    val text = lineText(line, copy).let { if (line.kind == LessonLineKind.Old && line.listened) "$it · ${listen.listenedBadge}" else it }
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
    title: String?,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** False: the button is secondary (tonal), for an invitation that must stay calm. */
    primary: Boolean = true,
) {
    val colors = QhTheme.colors
    QhCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
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
                if (title != null) {
                    Text(title, style = QhTheme.type.title1, color = colors.ink)
                    Spacer(Modifier.height(QhSpacing.x1))
                }
                Text(body, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        Spacer(Modifier.height(QhSpacing.x4))
        QhButton(
            text = action,
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            variant = if (primary) QhButtonVariant.Primary else QhButtonVariant.Tonal,
        )
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
