package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.imageResource
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.player_guide_art
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.playerFeatureCopy
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.ui.components.AyahWheel
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.playback.SleepTimer
import tj.app.quran_todo.ui.reader.PlayerUi
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.reader.formatAudioTime
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

private const val GuideKey = "reader_dock"

/**
 * The expanded part of the dock: the current ayah large, its position, the transport and the practice
 * controls (passage repeat, pause between ayahs, speed, sleep timer). It scrolls inside the dock; the dock
 * hands over to it through nested scrolling.
 */
@Composable
internal fun DockBody(
    viewModel: ReaderViewModel,
    ui: PlayerUi,
    prefs: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    surah: SurahWithAyahs,
    showTranslation: Boolean,
    copy: ReaderCopy,
    navInset: Dp,
    onPlay: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x2, bottom = navInset + QhSpacing.x8),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x6),
    ) {
        PlayerGuide()
        NowPlayingCard(viewModel = viewModel, ui = ui, surah = surah, showTranslation = showTranslation, copy = copy)
        TransportRow(viewModel = viewModel, ui = ui, practice = practice, copy = copy, onPlay = onPlay)
        RepeatSection(
            viewModel = viewModel,
            currentNumber = ui.index + 1,
            practice = practice,
            total = surah.ayahs.size,
            copy = copy,
        )
        ChipSection(
            title = copy.pauseBetween,
            options = listOf(0, 3, 5, 10).map { seconds ->
                seconds to if (seconds == 0) copy.none else "$seconds ${copy.secondsShort}"
            },
            selected = practice.pauseSeconds,
            onSelect = viewModel::setPauseSeconds,
            hint = copy.pauseHint,
        )
        ChipSection(
            title = copy.speed,
            options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { it to speedLabel(it) },
            selected = prefs.playbackSpeed,
            onSelect = viewModel::setPlaybackSpeed,
        )
        SleepSection(viewModel = viewModel, copy = copy)
    }
}

// -- Now playing --------------------------------------------------------------------------------------------

@Composable
private fun NowPlayingCard(
    viewModel: ReaderViewModel,
    ui: PlayerUi,
    surah: SurahWithAyahs,
    showTranslation: Boolean,
    copy: ReaderCopy,
) {
    val colors = QhTheme.colors
    val fontSize = LocalAppSettings.current.readingFontSize.coerceAtMost(30)
    val arabicStyle = QhTheme.type.quranBody(getQuranFontFamily(), fontSize.sp)
    val translations by viewModel.translations.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(QhTheme.shapes.md)
            .background(colors.readerBg)
            .padding(QhSpacing.card),
    ) {
        AnimatedContent(
            targetState = ui.index,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "dock-ayah",
        ) { index ->
            val ayah = surah.ayahs.getOrNull(index)
            if (ayah != null) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = splitBismillah(surah.surah.number, ayah.numberInSurah, ayah.text),
                        style = arabicStyle,
                        color = colors.ink,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val translation = if (showTranslation) translations[ayah.number] else null
                    if (!translation.isNullOrBlank()) {
                        Text(
                            text = translation,
                            style = QhTheme.type.body,
                            color = colors.inkSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = QhSpacing.x2),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(QhSpacing.x4))
        AudioPositionRow(viewModel = viewModel, copy = copy)
    }
}

/** "0:12 ━━━━ 0:35": the only part of the dock that follows the audio clock. */
@Composable
private fun AudioPositionRow(viewModel: ReaderViewModel, copy: ReaderCopy) {
    val colors = QhTheme.colors
    val time by viewModel.audioTime.collectAsState()
    val progress = viewModel.audioProgress.collectAsState()
    val style = QhTheme.type.callout.tabular()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatAudioTime(time.positionSeconds), style = style, color = colors.inkSecondary)
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .semantics {
                    contentDescription = copy.audioPosition
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.value, 0f..1f)
                }
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(colors.surfaceStrong, cornerRadius = radius)
                    val width = size.width * progress.value
                    if (width > 0f) {
                        drawRoundRect(colors.primary, size = Size(width.coerceAtLeast(size.height), size.height), cornerRadius = radius)
                    }
                },
        )
        Text(formatAudioTime(time.durationSeconds), style = style, color = colors.inkSecondary)
    }
}

@Composable
private fun TransportRow(
    viewModel: ReaderViewModel,
    ui: PlayerUi,
    practice: ReaderPracticeSettings,
    copy: ReaderCopy,
    onPlay: () -> Unit,
) {
    val pauseRemaining = viewModel.pauseRemainingSeconds.collectAsState()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = copy.previous,
            onClick = viewModel::previousAyah,
            iconSize = 28.dp,
        )
        Spacer(Modifier.width(QhSpacing.x6))
        DockPlayButton(
            ui = ui,
            pauseRemaining = pauseRemaining,
            pauseTotal = practice.pauseSeconds,
            size = 72.dp,
            copy = copy,
            shared = false,
            onClick = onPlay,
        )
        Spacer(Modifier.width(QhSpacing.x6))
        QhIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = copy.next,
            onClick = viewModel::nextAyah,
            iconSize = 28.dp,
        )
    }
}

// -- Practice ------------------------------------------------------------------------------------------------

@Composable
private fun RepeatSection(
    viewModel: ReaderViewModel,
    currentNumber: Int,
    practice: ReaderPracticeSettings,
    total: Int,
    copy: ReaderCopy,
) {
    val colors = QhTheme.colors
    val counts = remember { listOf(1, 3, 5, -1) }
    var from by remember { mutableIntStateOf(if (practice.rangeEnabled) practice.startAyah else currentNumber.coerceIn(1, total)) }
    var to by remember { mutableIntStateOf(if (practice.rangeEnabled) practice.endAyah else currentNumber.coerceIn(1, total)) }
    var count by remember { mutableIntStateOf(practice.rangeRepeatCount.takeIf { it in counts } ?: 5) }
    // The passage can also be set from elsewhere (the ayah sheet): the wheels follow it.
    LaunchedEffect(practice.rangeEnabled, practice.startAyah, practice.endAyah, practice.rangeRepeatCount) {
        if (practice.rangeEnabled) {
            from = practice.startAyah
            to = practice.endAyah
            count = practice.rangeRepeatCount
        }
    }
    val applied = practice.rangeEnabled && practice.startAyah == from && practice.endAyah == to &&
        practice.rangeRepeatCount == count

    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        QhSectionHeader(copy.repeatPassage)
        Row(
            Modifier
                .fillMaxWidth()
                .nestedScroll(ConsumeLeftoverScroll),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x6),
        ) {
            AyahWheel(
                label = copy.fromAyah,
                minimum = 1,
                maximum = total,
                value = from,
                onValueChange = {
                    from = it
                    if (to < it) to = it
                },
                modifier = Modifier.weight(1f),
            )
            AyahWheel(
                label = copy.toAyah,
                minimum = from,
                maximum = total,
                value = to.coerceAtLeast(from),
                onValueChange = { to = it },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${copy.ayah} $from–${to.coerceAtLeast(from)}",
            style = QhTheme.type.title2,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        QhSegmentedControl(
            options = counts.map(::repeatLabel),
            selectedIndex = counts.indexOf(count).coerceAtLeast(0),
            onSelect = { count = counts[it] },
        )
        QhButton(
            text = if (applied) copy.stopRepeat else copy.startRepeat,
            onClick = {
                if (applied) viewModel.stopPassage() else viewModel.applyPassage(from, to.coerceAtLeast(from), count)
            },
            modifier = Modifier.fillMaxWidth(),
            variant = if (applied) QhButtonVariant.Tonal else QhButtonVariant.Primary,
            size = QhButtonSize.M,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSection(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    hint: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        QhSectionHeader(title)
        if (hint != null) {
            Text(hint, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        ) {
            options.forEach { (value, label) ->
                QhChip(label = label, selected = value == selected, onClick = { onSelect(value) })
            }
        }
    }
}

@Composable
private fun SleepSection(viewModel: ReaderViewModel, copy: ReaderCopy) {
    val timer by viewModel.sleepTimer.collectAsState()
    val options = listOf(
        SleepTimer.Off to copy.sleepOff,
        SleepTimer.EndOfSurah to copy.sleepEndOfSurah,
        SleepTimer.Minutes(15) to copy.sleepMinutes(15),
        SleepTimer.Minutes(30) to copy.sleepMinutes(30),
    )
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        ChipSection(
            title = copy.sleepTimer,
            options = options,
            selected = timer,
            onSelect = viewModel::setSleepTimer,
        )
        if (timer is SleepTimer.Minutes) {
            SleepCountdown(viewModel.sleepRemainingSeconds.collectAsState(), copy)
        }
    }
}

@Composable
private fun SleepCountdown(remaining: State<Int>, copy: ReaderCopy) {
    Text(
        text = copy.sleepLeft(formatAudioTime(remaining.value)),
        style = QhTheme.type.callout.tabular(),
        color = QhTheme.colors.inkSecondary,
    )
}

// -- One-time hint -------------------------------------------------------------------------------------------

private fun guideSeen(): Boolean =
    UserSettingsStorage.isPlayerGuideSeen(GuideKey) == true ||
        // People who dismissed both guides of the old repeat and pause sheets know it all already.
        (UserSettingsStorage.isPlayerGuideSeen("Range") == true && UserSettingsStorage.isPlayerGuideSeen("Pause") == true)

/** A card that explains repeat, pause and the controls once, with the illustration of the old guide. */
@Composable
private fun PlayerGuide() {
    var seen by remember { mutableStateOf(guideSeen()) }
    AnimatedVisibility(
        visible = !seen,
        exit = shrinkVertically(tween(260)) + fadeOut(tween(160)),
    ) {
        PlayerGuideCard(
            onFinish = {
                UserSettingsStorage.savePlayerGuideSeen(true, GuideKey)
                seen = true
            },
        )
    }
}

@Composable
private fun PlayerGuideCard(onFinish: () -> Unit) {
    val copy = playerFeatureCopy(LocalAppLanguage.current)
    var step by remember { mutableIntStateOf(0) }
    val last = step >= 2
    QhCard(contentPadding = PaddingValues(QhSpacing.card)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuideArt(step = step, modifier = Modifier.size(96.dp))
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "guide-text",
            ) { current ->
                Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                    Text(copy.guideTitle(current), style = QhTheme.type.title2, color = QhTheme.colors.ink)
                    Text(copy.guideBody(current), style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = QhSpacing.x2), horizontalArrangement = Arrangement.End) {
            QhButton(
                text = if (last) copy.done else copy.next,
                onClick = { if (last) onFinish() else step++ },
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.S,
            )
        }
    }
}

/** One of the three frames of the `player_guide_art` atlas. */
@Composable
private fun GuideArt(step: Int, modifier: Modifier = Modifier) {
    val atlas = imageResource(Res.drawable.player_guide_art)
    Canvas(modifier) {
        val side = minOf(size.width, size.height).toInt()
        val frameWidth = atlas.width / 3
        drawImage(
            image = atlas,
            srcOffset = IntOffset(step.coerceIn(0, 2) * frameWidth, 0),
            srcSize = IntSize(frameWidth, atlas.height),
            dstOffset = IntOffset((size.width.toInt() - side) / 2, 0),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.High,
        )
    }
}
