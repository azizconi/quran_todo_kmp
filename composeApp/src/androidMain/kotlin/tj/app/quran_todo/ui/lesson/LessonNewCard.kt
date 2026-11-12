package tj.app.quran_todo.ui.lesson

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.RecallGradeSheet
import tj.app.quran_todo.ui.hifz.RecallHost
import tj.app.quran_todo.ui.hifz.RecallPassage
import tj.app.quran_todo.ui.hifz.VoiceProtoHook
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.rememberLessonListenCopy
import tj.app.quran_todo.ui.i18n.rememberListenCopy
import tj.app.quran_todo.ui.reader.components.TranslationMissingNote
import tj.app.quran_todo.ui.listen.EchoRing
import tj.app.quran_todo.ui.listen.YourTurnLine
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.playback.FollowedArabicText
import tj.app.quran_todo.ui.playback.PrepareWordTimings
import tj.app.quran_todo.ui.playback.WordFollow
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.recording.PassageKey
import tj.app.quran_todo.ui.recording.VoiceEntryRow
import tj.app.quran_todo.ui.recording.VoiceSheetHost
import tj.app.quran_todo.ui.recording.rememberVoiceSheetState
import tj.app.quran_todo.ui.reader.displayWords
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.similar.SimilarLessonHint
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.LESSON_AYAH_REPEATS_RANGE
import tj.app.quran_todo.ui.state.LESSON_PASSAGE_REPEATS_RANGE
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tajweed.TajweedArabicText
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The size of the play button of the "Listen" phase. */
private val PlayButtonSize = 64.dp

/**
 * The new passage: "Listen", "Read", "Recall". The phase chips at the top move between them; "Next" goes on. The
 * result is written when the passage is graded at the end of "Recall".
 */
@Composable
internal fun NewPassageCard(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working) {
    val progress = work.progress
    Column(Modifier.fillMaxSize()) {
        PassageHeader(viewModel = viewModel, copy = copy, work = work, overline = copy.stepNew)
        PhaseSwitch(copy = copy, phase = progress.phase, onSelect = { viewModel.goTo(work, it) })
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                work.ayahs.isEmpty() -> PassageLoading()
                progress.phase == CardPhase.Listen -> ListenPhase(viewModel, copy, work)
                progress.phase == CardPhase.Read -> ReadPhase(viewModel, copy, work)
                progress.phase == CardPhase.Recall -> RecallStage(viewModel, copy, work)
            }
        }
    }
}

/** The surah and the range of the card: the name, the Arabic name on the other side, "Ayahs 1–5 · 5 ayahs". */
@Composable
internal fun PassageHeader(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working, overline: String?) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val range = work.card.range
    val title = viewModel.surahTitle(range.surah, language, names)
    val arabic = viewModel.surahArabicName(range.surah, names)
    val family = getQuranFontFamily()
    val type = QhTheme.type
    val arabicStyle = remember(type, family) { type.quranInline(family, 26.sp) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x2, bottom = QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(text = overline.uppercase(), style = type.overline, color = colors.gold)
            }
            Text(
                text = title,
                style = type.title1,
                color = colors.ink,
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${copy.range(range.startAyah, range.endAyah)} · ${copy.ayahCount(range.ayahCount)}",
                style = type.callout,
                color = colors.inkSecondary,
            )
        }
        if (arabic.isNotEmpty()) {
            Text(
                text = arabic,
                style = arabicStyle,
                color = colors.primary,
                modifier = Modifier.padding(start = QhSpacing.x3),
            )
        }
    }
}

@Composable
private fun PhaseSwitch(copy: LessonCopy, phase: CardPhase, onSelect: (CardPhase) -> Unit) {
    val phases = listOf(CardPhase.Listen, CardPhase.Read, CardPhase.Recall)
    QhSegmentedControl(
        options = listOf(copy.phaseListen, copy.phaseRead, copy.phaseRecall),
        selectedIndex = phases.indexOf(phase).coerceAtLeast(0),
        onSelect = { onSelect(phases[it]) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(bottom = QhSpacing.x2),
    )
}

@Composable
internal fun PassageLoading() {
    Column(
        Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
    ) {
        Skeleton(Modifier.fillMaxWidth().size(width = 300.dp, height = 70.dp))
        Skeleton(Modifier.fillMaxWidth().size(width = 300.dp, height = 70.dp))
    }
}

// -- Text ---------------------------------------------------------------------------------------------------

/** The Arabic text of the passage, a little larger than in the reader. */
@Composable
internal fun rememberLessonArabicStyle(): TextStyle {
    val settings = LocalAppSettings.current
    val family = getQuranFontFamily()
    val type = QhTheme.type
    val size = (settings.readingFontSize + 2).coerceAtMost(40)
    return remember(type, family, size) {
        type.quranBody(family, size.sp).copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
}

// -- Listen -------------------------------------------------------------------------------------------------

@Composable
private fun ListenPhase(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working) {
    val colors = QhTheme.colors
    val uiPrefs = koinInject<UiPrefs>()
    val audio by viewModel.audioState.collectAsState()
    val ayahRepeats by uiPrefs.lessonAyahRepeats.collectAsState()
    val passageRepeats by uiPrefs.lessonPassageRepeats.collectAsState()
    val echo by uiPrefs.lessonEcho.collectAsState()
    val listenCopy = rememberListenCopy()
    var settingsOpen by remember { mutableStateOf(false) }
    val lessonListenCopy = rememberLessonListenCopy()
    val progress by viewModel.listenProgress.collectAsState()
    val translationOn by viewModel.translationOn.collectAsState()
    val translation = rememberPassageTranslation(viewModel, work)
    // The whole sequence has played to its end by itself: on to "Read" (leaving the phase, pausing or restarting never gets here).
    val finished = progress?.done == true
    LaunchedEffect(finished) {
        if (finished) viewModel.onListenFinished(work)
    }

    Column(Modifier.fillMaxSize()) {
        PhaseTopLine(
            text = progress?.takeIf { !it.done }?.place?.label(lessonListenCopy) ?: copy.listenHint(ayahRepeats, passageRepeats),
            copy = lessonListenCopy,
            translationOn = translationOn,
            onTranslation = viewModel::setTranslationOn,
            progress = progress?.takeIf { !it.done }?.fraction,
        )
        PassageList(
            ayahs = work.ayahs,
            current = audio.ayah.takeIf { audio.active },
            arabicStyle = rememberLessonArabicStyle(),
            onTap = null,
            modifier = Modifier.weight(1f),
            follow = remember(audio.playing) { WordFollow(playing = audio.playing) },
            similarHints = true,
            translation = translation,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x2, bottom = QhSpacing.x3)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            // The echo ("I repeat after the reciter"): while the reciter is silent it is the person's turn, with a thin ring.
            YourTurnLine(
                yourTurn = listenCopy.yourTurn,
                secondsShort = listenCopy.secondsShort,
                remaining = audio.silenceSeconds,
                active = audio.yourTurn,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.Rounded.Tune, copy.listenSettings, onClick = { settingsOpen = true })
                Box(Modifier.size(PlayButtonSize + 12.dp), contentAlignment = Alignment.Center) {
                    EchoRing(remaining = audio.silenceSeconds, total = audio.silenceTotalSeconds, active = audio.yourTurn)
                    PlayButton(
                        playing = audio.playing,
                        label = if (audio.playing) copy.listenPause else copy.listenPlay,
                        onClick = { viewModel.toggleListen(work) },
                    )
                }
                RoundAction(Icons.Rounded.Replay, copy.listenReplay, onClick = { viewModel.replayListen(work) })
            }
            QhButton(
                text = copy.next,
                onClick = { viewModel.next(work) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (settingsOpen) {
        ListenSettingsSheet(
            copy = copy,
            ayahRepeats = ayahRepeats,
            passageRepeats = passageRepeats,
            echo = echo,
            onAyahRepeats = uiPrefs::setLessonAyahRepeats,
            onPassageRepeats = uiPrefs::setLessonPassageRepeats,
            onEcho = uiPrefs::setLessonEcho,
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
private fun PlayButton(playing: Boolean, label: String, onClick: () -> Unit) {
    val colors = QhTheme.colors
    Box(
        Modifier
            .size(PlayButtonSize)
            .pressable(onClick = onClick, shape = CircleShape, onClickLabel = label)
            .clip(CircleShape)
            .background(colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = label,
            tint = colors.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = QhTheme.colors
    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .pressable(onClick = onClick, shape = CircleShape, onClickLabel = label)
            .clip(CircleShape)
            .background(colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = colors.ink, modifier = Modifier.size(QhSpacing.iconBar))
    }
}

/** The small sheet of the lesson: how many times every ayah and the whole passage sound in "Listen". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListenSettingsSheet(
    copy: LessonCopy,
    ayahRepeats: Int,
    passageRepeats: Int,
    echo: Boolean,
    onAyahRepeats: (Int) -> Unit,
    onPassageRepeats: (Int) -> Unit,
    onEcho: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val listenCopy = rememberListenCopy()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Text(
                text = copy.settingsTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.ayahRepeatsLabel, style = QhTheme.type.title2, color = colors.ink)
            val ayahOptions = LESSON_AYAH_REPEATS_RANGE.toList()
            QhSegmentedControl(
                options = ayahOptions.map { it.toString() },
                selectedIndex = ayahOptions.indexOf(ayahRepeats).coerceAtLeast(0),
                onSelect = { onAyahRepeats(ayahOptions[it]) },
            )
            Text(
                text = copy.passageRepeatsLabel,
                style = QhTheme.type.title2,
                color = colors.ink,
                modifier = Modifier.padding(top = QhSpacing.x2),
            )
            val passageOptions = LESSON_PASSAGE_REPEATS_RANGE.toList()
            QhSegmentedControl(
                options = passageOptions.map { it.toString() },
                selectedIndex = passageOptions.indexOf(passageRepeats).coerceAtLeast(0),
                onSelect = { onPassageRepeats(passageOptions[it]) },
            )
            Text(
                text = copy.listenHint(ayahRepeats, passageRepeats),
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = QhSpacing.x1),
            )
            // "I repeat after the reciter": silence for the person after every reading (task F1b).
            QhCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(top = QhSpacing.x2, bottom = QhSpacing.x2)) {
                QhSwitchRow(
                    title = listenCopy.echoSwitch,
                    checked = echo,
                    onCheckedChange = onEcho,
                    description = listenCopy.echoSwitchHint,
                )
            }
        }
    }
}

// -- Read ---------------------------------------------------------------------------------------------------

@Composable
private fun ReadPhase(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working) {
    val colors = QhTheme.colors
    val audio by viewModel.audioState.collectAsState()
    val translationOn by viewModel.translationOn.collectAsState()
    val translation = rememberPassageTranslation(viewModel, work)
    Column(Modifier.fillMaxSize()) {
        PhaseTopLine(
            text = copy.readHint,
            copy = rememberLessonListenCopy(),
            translationOn = translationOn,
            onTranslation = viewModel::setTranslationOn,
        )
        PassageList(
            ayahs = work.ayahs,
            current = audio.ayah.takeIf { audio.active },
            arabicStyle = rememberLessonArabicStyle(),
            onTap = { viewModel.toggleListenAyah(work, it) },
            modifier = Modifier.weight(1f),
            follow = remember(audio.playing) { WordFollow(playing = audio.playing) },
            similarHints = true,
            translation = translation,
        )
        QhButton(
            text = copy.next,
            onClick = { viewModel.next(work) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x2, bottom = QhSpacing.x3)
                .navigationBarsPadding(),
        )
    }
}

/**
 * The passage as the reader shows it: a rosette with the number, the Arabic text. [current] is the ayah being heard
 * (a wash and a gold bar, and the list scrolls it into the middle); a tap on an ayah calls [onTap] when it is given.
 */
@Composable
internal fun PassageList(
    ayahs: List<AyahEntity>,
    current: Int?,
    arabicStyle: TextStyle,
    onTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    /** F1a: the word that sounds is washed in the text of the [current] ayah (null: the passage is drawn without it). */
    follow: WordFollow? = null,
    /** F6: under an ayah that looks like one that is already memorized, the line "Similar to Al-A'raf 19 - compare" (Listen and Read only). */
    similarHints: Boolean = false,
    /** U8: the translation under every ayah while the chip is on (null: none). */
    translation: PassageTranslation? = null,
) {
    // The ayahs of this passage are being learned: they are not "already memorized" for the hints.
    val passage = remember(ayahs) { ayahs.mapTo(HashSet()) { AyahKey(it.surahNumber, it.numberInSurah) } }
    // The timings of the surah are read now, so that they are there when the first ayah sounds.
    if (follow != null) ayahs.firstOrNull()?.let { PrepareWordTimings(it.surahNumber) }
    val reduced = LocalReducedMotion.current
    val listState = rememberLazyListState()
    val latestReduced by rememberUpdatedState(reduced)
    LaunchedEffect(current) {
        val index = ayahs.indexOfFirst { it.numberInSurah == current }
        if (index >= 0) listState.centerItem(index, animate = !latestReduced)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = QhSpacing.x4),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(ayahs, key = { it.number }) { ayah ->
            val marked = ayah.numberInSurah == current
            PassageRow(
                ayah = ayah,
                marked = marked,
                arabicStyle = arabicStyle,
                onTap = onTap,
                follow = if (marked) follow else null,
                similarHint = if (similarHints) passage else null,
                translation = translation?.byAyah?.get(ayah.number),
                translationMissing = translation?.takeIf { it.missing && ayah === ayahs.first() },
            )
        }
    }
}

@Composable
private fun PassageRow(
    ayah: AyahEntity,
    marked: Boolean,
    arabicStyle: TextStyle,
    onTap: ((Int) -> Unit)?,
    follow: WordFollow? = null,
    similarHint: Set<AyahKey>? = null,
    translation: String? = null,
    translationMissing: PassageTranslation? = null,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val highlight by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(200),
        label = "passage-highlight",
    )
    val number = ayah.numberInSurah
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onTap != null) {
                    Modifier.pressable(onClick = { onTap(number) }, scale = 0.995f)
                } else {
                    Modifier
                },
            )
            .semantics { selected = marked }
            .drawBehind {
                if (highlight > 0f) {
                    drawRect(colors.readerHighlight.copy(alpha = colors.readerHighlight.alpha * highlight))
                    val barWidth = 3.dp.toPx()
                    val barHeight = size.height * highlight
                    drawRoundRect(
                        color = colors.gold,
                        topLeft = Offset(0f, (size.height - barHeight) / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
            .heightIn(min = QhSpacing.minTouch)
            .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        // U9: the number sits in the line of the text (at the start side), so a short ayah takes a short row.
        AyahRosette(number = number, size = 30.dp, modifier = Modifier.padding(top = QhSpacing.x2))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
            if (follow != null) {
                FollowedArabicText(
                    surah = ayah.surahNumber,
                    ayahInSurah = number,
                    words = remember(ayah) { displayWords(ayah.surahNumber, number, ayah.text) },
                    follow = follow,
                    style = arabicStyle,
                    color = colors.ink,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Tajweed (F3b): the colours of the rules over this very text when the setting is on.
                TajweedArabicText(
                    surah = ayah.surahNumber,
                    ayahInSurah = number,
                    text = remember(ayah) { splitBismillah(ayah.surahNumber, number, ayah.text) },
                    style = arabicStyle,
                    color = colors.ink,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!translation.isNullOrBlank()) {
                Text(text = translation, style = QhTheme.type.callout, color = colors.inkSecondary)
            } else if (translationMissing != null) {
                TranslationMissingNote(copy = translationMissing.copy, onRetry = translationMissing.onRetry)
            }
            if (similarHint != null) SimilarLessonHint(surah = ayah.surahNumber, ayah = number, passage = similarHint)
        }
    }
}

/** Scrolls so that the list item [index] sits in the middle of the list. */
private suspend fun LazyListState.centerItem(index: Int, animate: Boolean) {
    if (layoutInfo.viewportSize.height <= 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        if (animate) animateScrollToItem(index) else scrollToItem(index)
    }
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val middle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val delta = item.offset + item.size / 2f - middle
    if (abs(delta) < 8f) return
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}
