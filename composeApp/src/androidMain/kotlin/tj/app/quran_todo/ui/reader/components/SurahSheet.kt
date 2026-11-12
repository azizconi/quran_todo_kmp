package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.home.SurahStatusChange
import tj.app.quran_todo.ui.home.SurahTarget
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Pages of the sheet behind the "more" button and the status pill. */
enum class SurahSheetPage { Menu, Status }

/** Slides the sheet out before reporting the dismissal, so a button press does not make it vanish. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSheetCloser(sheetState: SheetState, onDismiss: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, onDismiss) {
        { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    }
}

@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = QhTheme.type.title1,
        color = QhTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The sheet of the surah: a small menu (status, go to ayah) whose pages slide into one another, opened by
 * the "more" button; the status pill of the header opens it directly on the status page. The row "Go to ayah..." hands over to the sheet
 * "Go to" ([GoToSheet]) through [onGoTo].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahSheet(
    initialPage: SurahSheetPage,
    surahName: String,
    status: SurahTodoStatus?,
    /** How many ayahs of the surah are covered by passages; "not started" with some asks first. */
    learnedAyahs: Int,
    copy: ReaderCopy,
    onChoice: (SurahTarget) -> Unit,
    onGoTo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss)
    var page by remember { mutableStateOf(initialPage) }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val distance: (Int) -> Int = { width -> if (forward) width / 5 else -width / 5 }
                (slideInHorizontally(tween(260)) { distance(it) } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -distance(it) } + fadeOut(tween(120)))
            },
            label = "surah-sheet-page",
        ) { current ->
            Column {
                if (current != SurahSheetPage.Menu && initialPage == SurahSheetPage.Menu) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QhIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = copy.back,
                            onClick = { page = SurahSheetPage.Menu },
                            filled = false,
                            modifier = Modifier.offset(x = (-12).dp),
                        )
                        SheetTitle(copy.surahStatusTitle)
                    }
                } else if (current == SurahSheetPage.Menu) {
                    SheetTitle(surahName)
                } else {
                    SheetTitle(copy.surahStatusTitle)
                }
                when (current) {
                    SurahSheetPage.Menu -> MenuPage(
                        statusLabel = status.label(copy),
                        copy = copy,
                        onStatus = { page = SurahSheetPage.Status },
                        onGoTo = onGoTo,
                    )
                    SurahSheetPage.Status -> StatusPage(
                        status = status,
                        learnedAyahs = learnedAyahs,
                        copy = copy,
                        onSelect = { choice ->
                            onChoice(choice)
                            close()
                        },
                        onCancel = close,
                    )
                }
            }
        }
    }
}

internal fun SurahTodoStatus?.label(copy: ReaderCopy): String = when (this) {
    SurahTodoStatus.LEARNED -> copy.learned
    SurahTodoStatus.LEARNING -> copy.learning
    null -> copy.notStarted
}

@Composable
private fun MenuPage(statusLabel: String, copy: ReaderCopy, onStatus: () -> Unit, onGoTo: () -> Unit) {
    QhCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = QhSpacing.x2),
        contentPadding = PaddingValues(0.dp),
    ) {
        QhValueRow(
            title = copy.surahStatusTitle,
            value = statusLabel,
            onClick = onStatus,
            icon = Icons.Rounded.RadioButtonUnchecked,
            iconTint = QhTheme.colors.gold,
        )
        QhRowDivider()
        QhNavRow(
            title = copy.goToAyah,
            onClick = onGoTo,
            icon = Icons.Rounded.Tag,
        )
    }
}

@Composable
private fun StatusPage(
    status: SurahTodoStatus?,
    learnedAyahs: Int,
    copy: ReaderCopy,
    onSelect: (SurahTarget) -> Unit,
    onCancel: () -> Unit,
) {
    // The choices mean what they mean on Home: SurahStatusChange is the one place that says when something is lost.
    val current = when (status) {
        null -> null
        SurahTodoStatus.LEARNING -> SurahHifzStatus.LEARNING
        SurahTodoStatus.LEARNED -> SurahHifzStatus.LEARNED
    }
    var confirming by remember { mutableStateOf<SurahTarget?>(null) }
    confirming?.let { target ->
        RemovalPage(
            lostAyahs = SurahStatusChange.lostAyahs(current, learnedAyahs, target),
            copy = copy,
            onRemove = { onSelect(target) },
            onCancel = onCancel,
        )
        return
    }
    fun choose(choice: SurahTarget) {
        when {
            SurahStatusChange.isNoop(current, choice) -> onCancel()
            SurahStatusChange.needsConfirmation(current, learnedAyahs, choice) -> confirming = choice
            else -> onSelect(choice)
        }
    }
    Column(Modifier.padding(top = QhSpacing.x1)) {
        Text(copy.surahStatusHint, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            StatusOption(
                icon = Icons.Rounded.RadioButtonUnchecked,
                label = copy.notStarted,
                selected = status == null,
                onClick = { choose(SurahTarget.NotStarted) },
                modifier = Modifier.weight(1f),
            )
            StatusOption(
                icon = Icons.Rounded.Contrast,
                label = copy.choiceLearning,
                selected = status == SurahTodoStatus.LEARNING,
                onClick = { choose(SurahTarget.Learning) },
                modifier = Modifier.weight(1f),
            )
            StatusOption(
                icon = Icons.Rounded.CheckCircle,
                label = copy.choiceKnown,
                selected = status == SurahTodoStatus.LEARNED,
                onClick = { choose(SurahTarget.Known) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Remove 12 learned ayahs from the repetitions?": the one status change that throws progress away. */
@Composable
private fun RemovalPage(lostAyahs: Int, copy: ReaderCopy, onRemove: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(top = QhSpacing.x1), verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        Text(copy.removeLearned(lostAyahs), style = QhTheme.type.title2, color = QhTheme.colors.ink)
        Text(copy.removeLearnedHint, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x1))
        QhButton(
            text = copy.removeAction,
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Danger,
            size = QhButtonSize.M,
        )
        QhButton(
            text = copy.cancelAction,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.M,
        )
    }
}

/** A big status card: a circle, a half circle and a check. */
@Composable
private fun StatusOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    Column(
        modifier
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceMuted)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.hairline, shape)
            .padding(horizontal = QhSpacing.x2, vertical = QhSpacing.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.inkSecondary, modifier = Modifier.size(30.dp))
        Text(
            text = label,
            style = QhTheme.type.label,
            color = if (selected) colors.onPrimaryContainer else colors.inkSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
