package tj.app.quran_todo.ui.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.GradeOptions
import tj.app.quran_todo.ui.hifz.RecallGrading
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/**
 * The controls of "Check yourself" on a page, at the foot of the screen: which ayah is being recalled and how many are left, "Show"
 * (the ayah is hidden behind a blur or bars) and then the two answers, "Hard" and "I remember", and the cross that leaves the mode.
 */
@Composable
internal fun CheckPanel(
    check: PageCheck,
    copy: HifzCopy,
    onReveal: () -> Unit,
    onMark: (AyahMark) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xl
    val current = check.current ?: return
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.x3)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(start = QhSpacing.x2, end = QhSpacing.x4, top = QhSpacing.x1, bottom = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QhIconButton(icon = Icons.Rounded.Close, contentDescription = copy.exit, onClick = onClose, filled = false)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${copy.ayah} ${current.key.ayah} · ${check.index + 1} / ${check.size}",
                    style = QhTheme.type.title2.tabular(),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = copy.reciteFromMemory,
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!check.revealed) {
            QhButton(text = copy.reveal, onClick = onReveal, modifier = Modifier.fillMaxWidth(), size = QhButtonSize.M)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                QhButton(
                    text = copy.hardMark,
                    onClick = { onMark(AyahMark.HARD) },
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
                QhButton(
                    text = copy.remembered,
                    onClick = { onMark(AyahMark.REMEMBERED) },
                    modifier = Modifier.weight(1f),
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

/** Hides [sheetState] with its slide, then runs [after]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHideThen(sheetState: SheetState): (after: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        { after -> scope.launch { sheetState.hide() }.invokeOnCompletion { after() } }
    }
}

/** "Leave Check yourself?": asked when some ayahs were answered and some remain. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckLeaveSheet(check: PageCheck, copy: HifzCopy, onKeepGoing: () -> Unit, onLeave: () -> Unit) {
    val colors = QhTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState)
    QhSheet(onDismiss = onKeepGoing, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = copy.exitTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.exitBody(check.markedCount, check.size), style = QhTheme.type.body, color = colors.inkSecondary)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QhButton(text = copy.keepGoing, onClick = { hideThen(onKeepGoing) }, modifier = Modifier.fillMaxWidth())
                QhButton(
                    text = copy.leave,
                    onClick = { hideThen(onLeave) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

/**
 * The grade sheet at the end of the check of a page: the grade of what was checked (the one that follows from the answers is
 * suggested) and the ayahs answered "hard" as chips that a tap unmarks or marks. [title] says what was checked ("Al-Baqarah · p. 3").
 * [onConfirm] gets the grade and the hard ayahs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PageGradeSheet(
    check: PageCheck,
    title: String,
    copy: HifzCopy,
    busy: Boolean,
    onConfirm: (grade: ReviewGrade, hard: Set<AyahKey>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hard by remember { mutableStateOf(check.hardAyahs) }
    var picked by remember { mutableStateOf<ReviewGrade?>(null) }
    val suggested = RecallGrading.suggest(check.size, hard.size)
    val grade = picked ?: suggested
    // The chips say "Ayah 3" for one surah and "112:3" when the page holds several surahs.
    val oneSurah = check.items.map { it.key.surah }.distinct().size == 1

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = copy.gradeTitle,
                    style = QhTheme.type.title1,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = title,
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    GradeOptions(selected = grade, copy = copy, onPick = { picked = it })
                }
                Text(
                    text = if (grade == suggested) copy.gradeSuggested else copy.gradeChosen,
                    style = QhTheme.type.label,
                    color = colors.inkSecondary,
                )
                Text(
                    text = copy.hardAyahsTitle,
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .semantics { heading() },
                )
                Text(text = copy.hardAyahsHint, style = QhTheme.type.callout, color = colors.inkSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    check.items.forEach { item ->
                        val on = item.key in hard
                        QhChip(
                            label = if (oneSurah) "${copy.ayah} ${item.key.ayah}" else item.key.text,
                            selected = on,
                            onClick = { hard = if (on) hard - item.key else hard + item.key },
                        )
                    }
                }
            }
            QhButton(
                text = copy.done,
                onClick = {
                    // Slide the sheet out first so the result does not make it vanish.
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(grade, hard) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                loading = busy,
                enabled = !busy,
            )
        }
    }
}
