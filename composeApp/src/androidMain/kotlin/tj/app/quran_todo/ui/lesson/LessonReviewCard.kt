package tj.app.quran_todo.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.rememberListenCopy
import tj.app.quran_todo.ui.listen.ListenOldSheet
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * One repetition of the Recent or Old block ("2 of 5"): first the card of the passage with "listen first", then
 * "Recall". Grading it writes the repetition and the lesson moves to the next card.
 */
@Composable
internal fun ReviewCard(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working) {
    val card = work.card as LessonCard.Review
    if (work.progress.phase == CardPhase.Recall) {
        Column(Modifier.fillMaxSize()) {
            PassageHeader(viewModel = viewModel, copy = copy, work = work, overline = overline(card, copy))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                RecallStage(viewModel, copy, work)
            }
        }
    } else {
        ReviewIntro(viewModel = viewModel, copy = copy, work = work, card = card)
    }
}

private fun overline(card: LessonCard.Review, copy: LessonCopy): String {
    val block = if (card.block == LessonBlock.Old) copy.stepOld else copy.stepRecent
    return "$block · ${copy.stepProgress(card.position, card.total)}"
}

@Composable
private fun ReviewIntro(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working, card: LessonCard.Review) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val audio by viewModel.audioState.collectAsState()
    val listenCopy = rememberListenCopy()
    val listenedToday by viewModel.listenedToday.collectAsState()
    var listenOldOpen by remember { mutableStateOf(false) }
    val range = card.range
    val title = viewModel.surahTitle(range.surah, language, names)
    val arabic = viewModel.surahArabicName(range.surah, names)
    val family = getQuranFontFamily()
    val arabicStyle = remember(type, family) { type.quranHeader(family, 38.sp) }
    val item = card.item

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x4, bottom = QhSpacing.x3),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x5),
    ) {
        QhCard(
            modifier = Modifier.fillMaxWidth(),
            style = QhCardStyle.Hero,
            contentPadding = PaddingValues(QhSpacing.cardLarge),
        ) {
            Text(text = overline(card, copy).uppercase(), style = type.overline, color = colors.gold)
            if (arabic.isNotEmpty()) {
                Text(
                    text = arabic,
                    style = arabicStyle,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QhSpacing.x4),
                )
            }
            Text(
                text = title,
                style = type.headline,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QhSpacing.x2)
                    .semantics { heading() },
            )
            Text(
                text = "${copy.range(range.startAyah, range.endAyah)} · ${copy.ayahCount(range.ayahCount)}",
                style = type.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (card.block == LessonBlock.Old && item.passageId in listenedToday) {
                // Listening is a help: the card is still to be recalled, it only says the passage was heard today.
                Text(
                    text = listenCopy.listenedBadge,
                    style = type.callout,
                    color = colors.learned,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QhSpacing.x3),
                )
            }
            if (item.overdueDays > 0) {
                Text(
                    text = copy.overdue(item.overdueDays),
                    style = type.callout,
                    color = colors.due,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = QhSpacing.x3),
                )
            }
            if (item.hardAyahs.isNotEmpty()) {
                Text(
                    text = copy.hardCount(item.hardAyahs.size),
                    style = type.callout,
                    color = colors.due,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (item.overdueDays > 0) QhSpacing.x1 else QhSpacing.x3),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QhButton(
                text = if (audio.playing) copy.listenPause else copy.listenFirst,
                onClick = { viewModel.toggleListenOnce(work) },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.M,
                icon = if (audio.playing) Icons.Rounded.Pause else Icons.Rounded.Headphones,
            )
            if (card.block == LessonBlock.Old && work.plan.old.isNotEmpty()) {
                // "Listen to the old": the whole portion of the circle as audio, for the road; it grades nothing.
                val minutes = HifzConstants.minutes(work.plan.old.sumOf { it.listenSeconds }).coerceAtLeast(1)
                QhButton(
                    text = listenCopy.oldButton(minutes),
                    onClick = {
                        // The lesson's own listening gives the player to the queue.
                        viewModel.stopAudio()
                        listenOldOpen = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Ghost,
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Headphones,
                )
            }
            QhButton(
                text = copy.startRecall,
                onClick = { viewModel.next(work) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (listenOldOpen) ListenOldSheet(onDismiss = { listenOldOpen = false })
}
