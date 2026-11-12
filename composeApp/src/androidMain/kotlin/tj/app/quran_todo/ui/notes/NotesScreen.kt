package tj.app.quran_todo.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** How long the "Note deleted - Undo" bar stays. */
private const val UndoMillis = 5_000L

/**
 * "My notes" (`Dest.Notes`, task F5a): every note of an ayah, newest first, with a search over the text. A tap opens the reader at the
 * ayah (through the single entry point that knows the mode "Pages"), a swipe deletes the note and offers "Undo".
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
) {
    val viewModel = koinViewModel<NotesViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { analyticsCopy(language) }
    LaunchedEffect(viewModel, language) { viewModel.setLanguage(language) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var undo by remember { mutableStateOf<NoteItem?>(null) }
    val today = remember { EpochDays.now() }
    val plan = remember(language) { planCopy(language) }

    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val topBarState = rememberQhTopBarState(listState)
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) { (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx() }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val topPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(QhTheme.colors.bg)) {
        when {
            !ui.ready -> Skeleton(
                Modifier.fillMaxWidth().padding(top = topPadding).padding(horizontal = QhSpacing.screen).height(96.dp),
                QhTheme.shapes.lg,
            )

            ui.total == 0 -> Box(Modifier.fillMaxSize().padding(top = topPadding), contentAlignment = Alignment.Center) {
                EmptyState(title = copy.notesEmptyTitle, message = copy.notesEmptyBody)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topPadding,
                    bottom = navigationBottom + QhSpacing.x8 + 64.dp,
                    start = QhSpacing.screen,
                    end = QhSpacing.screen,
                ),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                item(key = "search") {
                    QhSearchField(
                        value = ui.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = copy.notesSearch,
                        clearContentDescription = copy.notesSearchClear,
                    )
                }
                if (ui.items.isEmpty()) {
                    item(key = "no-match") {
                        EmptyState(title = copy.notesNoMatchTitle, message = copy.notesNoMatchBody)
                    }
                }
                items(ui.items, key = { it.key }) { note ->
                    NoteRow(
                        note = note,
                        title = copy.notesAyah(ui.nameOf(note.surah), note.ayah),
                        date = plan.dateLabel(EpochDays.ofMillis(note.updatedAtMillis, TimeZone.currentSystemDefault()), today),
                        deleteDescription = copy.notesDeleteDescription,
                        onOpen = { onOpenReader(Dest.Reader(surah = note.surah, ayah = note.ayah, origin = Origin.SEARCH)) },
                        onDelete = {
                            viewModel.delete(note)
                            undo = note
                        },
                    )
                }
            }
        }
        QhLargeTopBar(
            title = copy.notesTitle,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) expandedBarPx = size.height
            },
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = copy.back,
                    onClick = onBack,
                    filled = false,
                )
            },
        )
        UndoBar(
            note = undo,
            copy = copy,
            onUndo = {
                undo?.let(viewModel::restore)
                undo = null
            },
            onTimeout = { undo = null },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navigationBottom + QhSpacing.x4, start = QhSpacing.screen, end = QhSpacing.screen),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteRow(
    note: NoteItem,
    title: String,
    date: String,
    deleteDescription: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(colors.danger),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = colors.onDanger,
                    modifier = Modifier.padding(end = QhSpacing.x5),
                )
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QhCard(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$title. ${note.text}. $date"
                    },
                onClick = onOpen,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = QhTheme.type.title2,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = date, style = QhTheme.type.label, color = colors.inkSecondary, modifier = Modifier.padding(start = QhSpacing.x2))
                }
                Spacer(Modifier.height(QhSpacing.x1))
                Text(
                    text = note.text,
                    style = QhTheme.type.body,
                    color = colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QhIconButton(
                icon = Icons.Rounded.Delete,
                contentDescription = deleteDescription,
                onClick = onDelete,
                filled = false,
                tint = colors.inkSecondary,
            )
        }
    }
}

@Composable
private fun UndoBar(
    note: NoteItem?,
    copy: AnalyticsCopy,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    LaunchedEffect(note) {
        if (note != null) {
            delay(UndoMillis)
            onTimeout()
        }
    }
    AnimatedVisibility(
        visible = note != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(QhSpacing.x4))
                .background(colors.ink)
                .padding(start = QhSpacing.x4, end = QhSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copy.notesDeleted,
                style = QhTheme.type.callout,
                color = colors.bg,
                modifier = Modifier.weight(1f).padding(vertical = QhSpacing.x3),
            )
            TextButton(onClick = onUndo) { Text(text = copy.notesUndo, style = QhTheme.type.label, color = colors.gold) }
        }
    }
}
