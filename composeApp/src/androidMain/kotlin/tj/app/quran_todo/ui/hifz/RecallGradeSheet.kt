package tj.app.quran_todo.ui.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhTheme

private val Grades = listOf(ReviewGrade.FORGOT, ReviewGrade.HARD, ReviewGrade.GOOD, ReviewGrade.EASY)

/** The width of the sheet from which the four grades share one row (Tajik "Фаромӯш" is the longest name). */
private val OneRowMinWidth = 340.dp

/** The font scale above which the grades go two by two. */
private const val LargeFontScale = 1.15f

/**
 * The sheet at the end of a recall: the passage grade (Forgot, Hard, Good, Easy) with the one that follows from
 * the answers already chosen ([HifzSession.suggestedGrade]), and the ayahs answered "hard" as chips that a tap
 * unmarks or marks. The person may pick another grade; until they do, the choice follows the chips.
 *
 * [onConfirm] gets the grade and the hard ayahs (numbers in the surah). [onDismiss] closes the sheet without a result.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecallGradeSheet(
    session: HifzSession,
    title: String,
    copy: HifzCopy,
    busy: Boolean,
    onConfirm: (grade: ReviewGrade, hard: Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hard by remember { mutableStateOf(session.hardAyahs) }
    var picked by remember { mutableStateOf<ReviewGrade?>(null) }
    val suggested = RecallGrading.suggest(session.size, hard.size)
    val grade = picked ?: suggested

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        // The button stays in view (landscape, large text); the rest of the sheet scrolls above it.
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
                Box(Modifier.padding(top = 8.dp)) {
                    GradeOptions(selected = grade, copy = copy, onPick = { picked = it })
                }
                Text(
                    text = if (grade == suggested) copy.gradeSuggested else copy.gradeChosen,
                    style = QhTheme.type.label,
                    color = colors.inkSecondary,
                )

                if (session.size > 0) {
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
                        session.items.forEach { item ->
                            val on = item.inSurah in hard
                            QhChip(
                                label = "${copy.ayah} ${item.inSurah}",
                                selected = on,
                                onClick = { hard = if (on) hard - item.inSurah else hard + item.inSurah },
                            )
                        }
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

@Composable
private fun GradeOptions(selected: ReviewGrade, copy: HifzCopy, onPick: (ReviewGrade) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val oneRow = fontScale <= LargeFontScale && maxWidth >= OneRowMinWidth
        if (oneRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Grades.forEach { GradePill(it, it == selected, copy, onPick, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Grades.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { GradePill(it, it == selected, copy, onPick, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** The fill, the text and an accent dot of a grade button. */
private class GradeLook(val container: Color, val content: Color, val accent: Color)

@Composable
private fun gradeLook(grade: ReviewGrade): GradeLook {
    val colors = QhTheme.colors
    return when (grade) {
        ReviewGrade.FORGOT -> GradeLook(colors.dueContainer, colors.ink, colors.due)
        ReviewGrade.HARD -> GradeLook(colors.learningContainer, colors.ink, colors.learning)
        ReviewGrade.GOOD -> GradeLook(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        ReviewGrade.EASY -> GradeLook(colors.learnedContainer, colors.ink, colors.learned)
    }
}

/**
 * A grade as a pill in the color of its meaning. The chosen one gets a ring and a check, so the choice does not
 * rest on color alone.
 */
@Composable
private fun GradePill(
    grade: ReviewGrade,
    selected: Boolean,
    copy: HifzCopy,
    onPick: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = gradeLook(grade)
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val label = copy.label(grade)
    Row(
        modifier
            .heightIn(min = 52.dp)
            .pressable(
                onClick = { onPick(grade) },
                haptic = if (grade == ReviewGrade.FORGOT) HapticKind.Reject else HapticKind.Segment,
                shape = shape,
                scale = 0.95f,
                role = Role.RadioButton,
                onClickLabel = label,
            )
            .semantics { this.selected = selected }
            .clip(shape)
            .background(look.container)
            .border(if (selected) 2.dp else 0.dp, if (selected) colors.ink else Color.Transparent, shape)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = look.content, modifier = Modifier.size(16.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(look.accent))
        }
        Text(
            text = label,
            style = QhTheme.type.label,
            color = look.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
