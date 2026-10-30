package tj.app.quran_todo.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.ListenCopy
import tj.app.quran_todo.ui.i18n.ayahRangeLabel
import tj.app.quran_todo.ui.i18n.lessonCopy
import tj.app.quran_todo.ui.i18n.rememberListenCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The big play button of the sheet, with the ring of the echo silence around it. */
private val PlaySize = 64.dp

/**
 * "Listen to the old" (task F1b): the passages of today's portion of the manzil circle, played one after another with the reciter
 * and the speed of the person, once or with the echo ("Your turn" silences). The sound is a queue of the playback controller: it
 * goes on when this sheet is closed, with the screen off, with the notification showing the surah and the ayah. When it is over, or
 * on "Done", "Mark: I repeated it by ear" writes a "listened" record for each passage heard to the end. Listening is a help, not a
 * repetition: only "Recall" grades, and the circle does not move.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenOldSheet(onDismiss: () -> Unit) {
    val viewModel = koinViewModel<ListenOldViewModel>()
    val language = LocalAppLanguage.current
    val copy = rememberListenCopy(language)
    val ui by viewModel.ui.collectAsState()
    val names by viewModel.chapterNames.collectAsState()
    val toaster = LocalToaster.current
    val saveFailed = lessonCopy(language).saveFailed
    LaunchedEffect(language, viewModel) { viewModel.onLanguage(language) }
    LaunchedEffect(viewModel, copy) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ListenEvent.Marked -> {
                    onDismiss()
                    toaster.show(copy.marked(event.count), ToastKind.Success)
                }
                ListenEvent.MarkFailed -> toaster.show(saveFailed, ToastKind.Error)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Text(
                text = copy.oldTitle,
                style = QhTheme.type.title1,
                color = QhTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (!ui.loaded) {
                // The lesson is still being read: no "nothing here" for a moment.
                Skeleton(Modifier.fillMaxWidth().height(160.dp), QhTheme.shapes.lg)
            } else if (ui.rows.isEmpty()) {
                Text(
                    text = copy.sheetEmpty,
                    style = QhTheme.type.body,
                    color = QhTheme.colors.inkSecondary,
                    modifier = Modifier.padding(vertical = QhSpacing.x4),
                )
            } else {
                Text(
                    text = copy.oldSubtitle(ui.rows.size, ui.minutes),
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                )
                QhSegmentedControl(
                    options = listOf(copy.modeOnce, copy.modeEcho),
                    selectedIndex = if (ui.echo) 1 else 0,
                    onSelect = { viewModel.setEcho(it == 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Transport(ui = ui, viewModel = viewModel, copy = copy, language = language, names = names)
                Column {
                    ui.rows.forEachIndexed { position, row ->
                        PassageRow(
                            position = position,
                            row = row,
                            title = viewModel.surahTitle(row.surah, language, names),
                            queue = ui.queue,
                            copy = copy,
                            language = language,
                            onClick = { viewModel.play(position) },
                        )
                    }
                }
                Actions(ui = ui, viewModel = viewModel, copy = copy)
                Text(
                    text = copy.notRecallNote,
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                    modifier = Modifier.padding(top = QhSpacing.x1, bottom = QhSpacing.x2),
                )
            }
        }
    }
}

/** The passage that plays (or the first one), what is heard of it, and the previous / play / next buttons. */
@Composable
private fun Transport(
    ui: ListenOldUi,
    viewModel: ListenOldViewModel,
    copy: ListenCopy,
    language: AppLanguage,
    names: Map<Int, tj.app.quran_todo.domain.model.ChapterNameModel>,
) {
    val colors = QhTheme.colors
    val queue = ui.queue
    val row = ui.rows.getOrNull(queue?.position ?: 0) ?: return
    val playing = queue?.playing == true
    val title = viewModel.surahTitle(row.surah, language, names)
    val where = buildString {
        append(copy.passageOf((queue?.position ?: 0) + 1, ui.rows.size))
        if (queue != null && queue.ayah > 0 && !queue.finished) append(" · ").append(ayahRangeLabel(queue.ayah, queue.ayah, language))
    }
    QhCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = QhTheme.type.title2,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${ayahRangeLabel(row.startAyah, row.endAyah, language)} · $where",
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            YourTurnLine(
                yourTurn = copy.yourTurn,
                secondsShort = copy.secondsShort,
                remaining = queue?.silenceSeconds ?: 0,
                active = queue?.yourTurn == true,
                modifier = Modifier.padding(top = QhSpacing.x1),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QhIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = copy.previousPassage,
                    onClick = viewModel::previous,
                    enabled = queue != null,
                    iconSize = 28.dp,
                )
                Box(Modifier.size(PlaySize + 12.dp), contentAlignment = Alignment.Center) {
                    EchoRing(remaining = queue?.silenceSeconds ?: 0, total = queue?.silenceTotalSeconds ?: 0, active = queue?.yourTurn == true)
                    Box(
                        Modifier
                            .size(PlaySize)
                            .pressable(
                                onClick = viewModel::toggle,
                                shape = CircleShape,
                                onClickLabel = if (playing) copy.pause else copy.play,
                            )
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) copy.pause else copy.play,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                QhIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = copy.nextPassage,
                    onClick = viewModel::next,
                    enabled = queue != null,
                    iconSize = 28.dp,
                )
            }
        }
    }
}

/** One passage of the portion: its number (a check once heard in this run), the name, the minutes, and what is known of it today. */
@Composable
private fun PassageRow(
    position: Int,
    row: ListenRow,
    title: String,
    queue: ListenQueueUi?,
    copy: ListenCopy,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val current = queue != null && queue.position == position && !queue.finished
    val heard = queue != null && position in queue.heard
    val range = ayahRangeLabel(row.startAyah, row.endAyah, language)
    val status = buildList {
        add(copy.minutes(row.minutes))
        if (row.listened) add(copy.listenedBadge)
        if (row.recalled) add(copy.recalledBadge)
    }.joinToString(" · ")
    val description = "$title, $range. $status" + if (current) ". ${copy.nowPlayingLabel}" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .clip(QhTheme.shapes.md)
            .background(if (current) colors.primaryContainer else Color.Transparent)
            .pressable(onClick = onClick, shape = QhTheme.shapes.md, scale = 0.99f)
            .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = current
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (heard) colors.learned else colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            if (heard) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onLearned, modifier = Modifier.size(16.dp))
            } else {
                Text(text = "${position + 1}", style = QhTheme.type.label, color = colors.inkSecondary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "$title · $range",
                style = QhTheme.type.body,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * "Done" while the queue runs; when it is over (or after "Done"): "Mark: I repeated it by ear" with what was heard to the end, and
 * "Listen again".
 */
@Composable
private fun Actions(ui: ListenOldUi, viewModel: ListenOldViewModel, copy: ListenCopy) {
    val queue = ui.queue ?: return
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        if (!queue.finished) {
            QhButton(
                text = copy.done,
                onClick = viewModel::finish,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.M,
            )
        } else {
            if (queue.heard.isNotEmpty()) {
                Text(
                    text = copy.heardCount(queue.heard.size),
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                )
                QhButton(
                    text = copy.markHeard,
                    onClick = viewModel::mark,
                    modifier = Modifier.fillMaxWidth(),
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Check,
                )
            }
            QhButton(
                text = copy.startAgain,
                onClick = { viewModel.start() },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}
