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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.AyahWheel
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Pages of the sheet behind the "more" button and the status pill. */
enum class SurahSheetPage { Menu, Status, GoTo }

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
 * the "more" button; the status pill of the header opens it directly on the status page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahSheet(
    initialPage: SurahSheetPage,
    surahName: String,
    status: SurahTodoStatus?,
    totalAyahs: Int,
    currentAyah: Int,
    copy: ReaderCopy,
    onStatus: (SurahTodoStatus?) -> Unit,
    onGoTo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss)
    var page by remember { mutableStateOf(initialPage) }
    var ayah by remember { mutableIntStateOf(currentAyah.coerceIn(1, totalAyahs.coerceAtLeast(1))) }

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
                        SheetTitle(if (current == SurahSheetPage.Status) copy.surahStatusTitle else copy.goToTitle)
                    }
                } else if (current == SurahSheetPage.Menu) {
                    SheetTitle(surahName)
                } else if (current == SurahSheetPage.Status) {
                    SheetTitle(copy.surahStatusTitle)
                } else if (current == SurahSheetPage.GoTo) {
                    SheetTitle(copy.goToTitle)
                }
                when (current) {
                    SurahSheetPage.Menu -> MenuPage(
                        statusLabel = status.label(copy),
                        copy = copy,
                        onStatus = { page = SurahSheetPage.Status },
                        onGoTo = { page = SurahSheetPage.GoTo },
                    )
                    SurahSheetPage.Status -> StatusPage(
                        status = status,
                        copy = copy,
                        onSelect = { selected ->
                            onStatus(selected)
                            close()
                        },
                    )
                    SurahSheetPage.GoTo -> GoToPage(
                        value = ayah,
                        total = totalAyahs,
                        copy = copy,
                        onValueChange = { ayah = it },
                        onGo = {
                            onGoTo(ayah)
                            close()
                        },
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
            icon = Icons.Rounded.StarBorder,
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
private fun StatusPage(status: SurahTodoStatus?, copy: ReaderCopy, onSelect: (SurahTodoStatus?) -> Unit) {
    Column(Modifier.padding(top = QhSpacing.x1)) {
        Text(copy.surahStatusHint, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            StatusOption(
                icon = Icons.Rounded.StarBorder,
                label = copy.notStarted,
                selected = status == null,
                onClick = { onSelect(null) },
                modifier = Modifier.weight(1f),
            )
            StatusOption(
                icon = Icons.AutoMirrored.Rounded.StarHalf,
                label = copy.learning,
                selected = status == SurahTodoStatus.LEARNING,
                onClick = { onSelect(SurahTodoStatus.LEARNING) },
                modifier = Modifier.weight(1f),
            )
            StatusOption(
                icon = Icons.Rounded.Star,
                label = copy.learned,
                selected = status == SurahTodoStatus.LEARNED,
                onClick = { onSelect(SurahTodoStatus.LEARNED) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A big status card: an outline, a half and a full star. */
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

@Composable
private fun GoToPage(
    value: Int,
    total: Int,
    copy: ReaderCopy,
    onValueChange: (Int) -> Unit,
    onGo: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = QhSpacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A wheel that has reached its end must not pass the drag on to the sheet and dismiss it.
        Box(Modifier.fillMaxWidth(0.5f).nestedScroll(ConsumeLeftoverScroll)) {
            AyahWheel(
                label = copy.ayah,
                minimum = 1,
                maximum = total.coerceAtLeast(1),
                value = value,
                onValueChange = onValueChange,
            )
        }
        Spacer(Modifier.height(QhSpacing.x4))
        QhButton(text = copy.goTo, onClick = onGo, modifier = Modifier.fillMaxWidth())
    }
}
