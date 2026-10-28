package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private const val DayMillis = 24L * 60 * 60 * 1000

/** What the ayah sheet shows about one ayah. */
class AyahSheetModel(
    val ayah: AyahEntity,
    val index: Int,
    val surahName: String,
    val arabic: String,
    val translation: String?,
    val status: AyahTodoStatus?,
    val weak: Boolean,
    val note: String,
    val review: AyahReviewEntity?,
)

/**
 * The sheet of one ayah (long press or the "..." button): listen from here, repeat it, its status, a note,
 * copy and share. Closing it saves a note that was typed but not saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahSheet(
    model: AyahSheetModel,
    arabicStyle: TextStyle,
    copy: ReaderCopy,
    onListenFromHere: () -> Unit,
    onRepeat: () -> Unit,
    onStatus: (AyahTodoStatus?) -> Unit,
    onSaveNote: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss)
    val number = model.ayah.numberInSurah

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .heightIn(max = 640.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                AyahRosette(number = number, status = model.status, weak = model.weak, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    SheetTitle("${model.surahName} · ${copy.ayah} $number")
                    reviewLine(model, copy)?.let { (text, due) ->
                        Text(
                            text = text,
                            style = QhTheme.type.callout,
                            color = if (due) colors.due else colors.inkSecondary,
                        )
                    }
                }
            }
            Text(
                text = model.arabic,
                style = arabicStyle,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QhSpacing.x3),
            )
            Spacer(Modifier.height(QhSpacing.x3))
            SheetActionRow(Icons.Rounded.PlayArrow, copy.listenFromHere) {
                onListenFromHere()
                close()
            }
            SheetActionRow(Icons.Rounded.Repeat, copy.repeatThisAyah) {
                onRepeat()
                close()
            }

            Spacer(Modifier.height(QhSpacing.x5))
            QhSectionHeader(copy.ayahStatus)
            Spacer(Modifier.height(QhSpacing.x2))
            val statuses = listOf<AyahTodoStatus?>(null, AyahTodoStatus.LEARNING, AyahTodoStatus.LEARNED)
            QhSegmentedControl(
                options = listOf(copy.notStarted, copy.learning, copy.learned),
                selectedIndex = statuses.indexOf(model.status).coerceAtLeast(0),
                onSelect = { onStatus(statuses[it]) },
                icons = listOf(Icons.Rounded.StarBorder, Icons.AutoMirrored.Rounded.StarHalf, Icons.Rounded.Star),
            )

            Spacer(Modifier.height(QhSpacing.x5))
            QhSectionHeader(copy.note)
            Spacer(Modifier.height(QhSpacing.x2))
            NoteEditor(
                initial = model.note,
                copy = copy,
                onSave = onSaveNote,
                onSavedAndClose = close,
            )

            Spacer(Modifier.height(QhSpacing.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                QhButton(
                    text = copy.copyAction,
                    onClick = {
                        onCopy()
                        close()
                    },
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.ContentCopy,
                )
                QhButton(
                    text = copy.share,
                    onClick = {
                        onShare()
                        close()
                    },
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Share,
                )
            }
        }
    }
}

/** (text, isDue) for the weak mark / review schedule under the title, or null when there is nothing to say. */
private fun reviewLine(model: AyahSheetModel, copy: ReaderCopy): Pair<String, Boolean>? {
    val review = model.review
    if (review != null) {
        val left = review.nextReviewAt - System.currentTimeMillis()
        if (left <= 0L) return copy.reviewDue to true
        val days = ((left + DayMillis - 1) / DayMillis).toInt().coerceAtLeast(1)
        return copy.reviewIn(days) to false
    }
    return if (model.weak) copy.weakAyah to true else null
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick = onClick, shape = QhTheme.shapes.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        QhIconTile(icon)
        Text(label, style = QhTheme.type.title2, color = QhTheme.colors.ink)
    }
}

/**
 * The note field. "Save" stores the note and closes the sheet; leaving the sheet any other way stores a note
 * that was changed, so typed text is never lost.
 */
@Composable
private fun NoteEditor(
    initial: String,
    copy: ReaderCopy,
    onSave: (String) -> Unit,
    onSavedAndClose: () -> Unit,
) {
    val colors = QhTheme.colors
    var text by remember { mutableStateOf(initial) }
    var saved by remember { mutableStateOf(initial) }
    val dirty = text.trim() != saved.trim()

    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(saved)
    val latestOnSave by rememberUpdatedState(onSave)
    DisposableEffect(Unit) {
        onDispose {
            if (latestText.trim() != latestSaved.trim()) latestOnSave(latestText)
        }
    }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = QhTheme.type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        maxLines = 6,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(QhTheme.shapes.md)
                    .background(colors.surfaceMuted)
                    .padding(QhSpacing.x4),
            ) {
                if (text.isEmpty()) {
                    Text(copy.noteHint, style = QhTheme.type.body, color = colors.inkTertiary)
                }
                inner()
            }
        },
    )
    AnimatedVisibility(
        visible = dirty,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        QhButton(
            text = copy.saveNote,
            onClick = {
                onSave(text)
                saved = text
                onSavedAndClose()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = QhSpacing.x3),
            size = QhButtonSize.M,
        )
    }
}
