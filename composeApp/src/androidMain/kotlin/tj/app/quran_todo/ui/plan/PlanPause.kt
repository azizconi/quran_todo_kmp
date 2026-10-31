package tj.app.quran_todo.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.schedule.PlanPauses
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.PauseCopy
import tj.app.quran_todo.ui.lesson.formatDay
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/**
 * The numbers of the pause sheet (task F2b): pure, so they are tested without a screen. A new pause is counted from today; an
 * extension keeps the days that are left as its lower bound (the sheet only lengthens it), and the end of a pause is never further than
 * thirteen days from today ([PlanPauses.MAX_DAYS] days counting today).
 */
internal object PauseSheetMath {
    /** The days a new pause starts at in the sheet. */
    const val DEFAULT_DAYS = 3

    /** The chips of the sheet. */
    val Presets = listOf(1, 3, 7, 14)

    /** The fewest days the sheet allows: 1 for a new pause, the days that are left (today included) for an extension. */
    fun minDays(pause: PlanPause?, today: Int): Int =
        if (pause == null) 1 else (pause.untilEpochDay - today + 1).coerceIn(1, PlanPauses.MAX_DAYS)

    /** The days the sheet opens with. */
    fun initialDays(pause: PlanPause?, today: Int): Int = if (pause == null) DEFAULT_DAYS else minDays(pause, today)

    fun clamp(days: Int, pause: PlanPause?, today: Int): Int = days.coerceIn(minDays(pause, today), PlanPauses.MAX_DAYS)

    /** The first day with a lesson again for a pause of [days] days from [today]. */
    fun resumeDay(today: Int, days: Int): Int = today + days

    /** By how many days the repetitions move: the whole pause when it is new, the difference of the ends when it is extended. */
    fun shiftDays(pause: PlanPause?, today: Int, days: Int): Int =
        if (pause == null) days else (today + days - 1) - pause.untilEpochDay

    /** An extension is possible only while the end is closer than the limit. */
    fun canExtend(pause: PlanPause, today: Int): Boolean = minDays(pause, today) < PlanPauses.MAX_DAYS
}

/**
 * The row "Pause" of "My plan": a calm line that opens the sheet; while the plan is paused it says "On pause until 11 November" with
 * "Extend" and "Continue now". Nothing here goes through "Save": a pause is an action of its own.
 */
@Composable
internal fun PauseSection(
    pause: PlanPause?,
    today: Int,
    copy: PauseCopy,
    onPause: () -> Unit,
    onExtend: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    if (pause == null || today !in pause) {
        QhCard(
            modifier = modifier.fillMaxWidth(),
            onClick = onPause,
            contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                QhIconTile(Icons.Rounded.Pause, tint = colors.gold)
                Column(Modifier.weight(1f)) {
                    Text(copy.rowTitle, style = QhTheme.type.title2, color = colors.ink)
                    Text(copy.rowSubtitle, style = QhTheme.type.callout, color = colors.inkSecondary)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
            }
        }
        return
    }
    QhCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.card)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            QhIconTile(Icons.Rounded.Pause, tint = colors.gold)
            Column(Modifier.weight(1f)) {
                Text(copy.rowActive(formatDay(pause.untilEpochDay, language)), style = QhTheme.type.title2, color = colors.ink)
                Text(copy.lessonBody(formatDay(pause.resumeEpochDay, language)), style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        // One under the other: "Continue now" is long in some languages and must never be cut.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhButton(
                text = copy.resumeNow,
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
                size = QhButtonSize.M,
            )
            if (PauseSheetMath.canExtend(pause, today)) {
                QhButton(
                    text = copy.extend,
                    onClick = onExtend,
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

/**
 * The sheet that sets a pause (or extends the one that is on): the days as chips 1, 3, 7, 14 and a step of one day, the line
 * "Lessons return on 12 November. Reviews will move by 7 days; your streak is kept." and the button. [onConfirm] gets the days
 * counted from today.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PauseSheet(
    pause: PlanPause?,
    today: Int,
    copy: PauseCopy,
    onConfirm: (days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    val extending = pause != null
    val minimum = PauseSheetMath.minDays(pause, today)
    var days by rememberSaveable { mutableIntStateOf(PauseSheetMath.initialDays(pause, today)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(after: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { after() }
    }
    val resumeDate = formatDay(PauseSheetMath.resumeDay(today, days), language)
    val shift = PauseSheetMath.shiftDays(pause, today, days)

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
            Text(
                text = if (extending) copy.extendTitle else copy.sheetTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.daysQuestion, style = QhTheme.type.callout, color = colors.inkSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
            ) {
                PauseSheetMath.Presets.forEach { preset ->
                    QhChip(
                        label = copy.days(preset),
                        selected = days == preset,
                        onClick = { days = PauseSheetMath.clamp(preset, pause, today) },
                        enabled = preset >= minimum,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QhIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = copy.less,
                    onClick = { days = PauseSheetMath.clamp(days - 1, pause, today) },
                    enabled = days > minimum,
                )
                Text(
                    text = copy.days(days),
                    style = QhTheme.type.headline.tabular(),
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = copy.days(days) },
                )
                QhIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = copy.more,
                    onClick = { days = PauseSheetMath.clamp(days + 1, pause, today) },
                    enabled = days < PlanPauses.MAX_DAYS,
                )
            }
            Text(
                text = when {
                    !extending -> copy.info(resumeDate, days)
                    shift > 0 -> copy.extendInfo(resumeDate, shift)
                    // Nothing is added yet: only say when the lessons are back.
                    else -> copy.cardBody(resumeDate)
                },
                style = QhTheme.type.body,
                color = colors.inkSecondary,
            )
            QhButton(
                text = if (extending) copy.confirmExtend else copy.confirmPause,
                onClick = { hideThen { onConfirm(days) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !extending || shift > 0,
            )
        }
    }
}
