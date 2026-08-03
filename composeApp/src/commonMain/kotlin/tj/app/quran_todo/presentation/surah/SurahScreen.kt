package tj.app.quran_todo.presentation.surah

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioPlayer
import tj.app.quran_todo.common.audio.ayahAudioCacheKey
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.presentation.designsystem.platformBottomSystemBarsPadding

/**
 * The focused, Arabic-first reader used by both native platform navigation hosts.
 * All reader-specific choices are intentionally kept in Settings, not on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahScreen(
    surahWithAyahs: SurahWithAyahs,
    onDismiss: () -> Unit,
    viewModel: SurahViewModel = koinViewModel(),
) {
    val language = LocalAppLanguage.current
    val strings = LocalAppStrings.current
    val settings = LocalAppSettings.current
    val copy = remember(language) { surahReaderCopy(language) }
    val colors = MaterialTheme.extendedColors
    val surahNumber = surahWithAyahs.surah.number
    val ayahs = remember(surahWithAyahs) { surahWithAyahs.ayahs.sortedBy { it.numberInSurah } }
    val persistedSurahStatus by viewModel.surahStatus(surahNumber).collectAsState(null)
    val translations by viewModel.ayahTranslations.collectAsState()
    val chapterNames by viewModel.chapterNames.collectAsState()
    val readerPreferences = remember { SurahReaderPreferences.load() }
    val audioPlayer = remember { AudioPlayer() }
    val audioCache = remember { AudioCache() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val statusSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)

    var currentAyahIndex by remember(surahNumber) { mutableStateOf(0) }
    var initialSelectionResolved by remember(surahNumber) { mutableStateOf(false) }
    var isPlaying by remember(surahNumber) { mutableStateOf(false) }
    var canResume by remember(surahNumber) { mutableStateOf(false) }
    var positionMs by remember(surahNumber) { mutableStateOf(0L) }
    var durationMs by remember(surahNumber) { mutableStateOf(0L) }
    var isShowingSurahStatus by remember { mutableStateOf(false) }
    var hasPendingSurahStatus by remember(surahNumber) { mutableStateOf(false) }
    var pendingSurahStatus by remember(surahNumber) { mutableStateOf<SurahTodoStatus?>(null) }
    var statusRequestId by remember(surahNumber) { mutableStateOf(0L) }
    var statusUpdateError by remember(surahNumber) { mutableStateOf<String?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    val translatedTitle = chapterNames[surahNumber]?.transliteration
        ?: surahWithAyahs.surah.englishName
    val topSubtitle = "${ayahs.size} ${copy.ayahs} · " +
        localizeRevelationPlace(surahWithAyahs.surah.revelationType, strings)
    val currentAyah = ayahs.getOrNull(currentAyahIndex)
    val surahStatus = if (hasPendingSurahStatus) pendingSurahStatus else persistedSurahStatus

    LaunchedEffect(persistedSurahStatus, hasPendingSurahStatus, pendingSurahStatus) {
        if (hasPendingSurahStatus && persistedSurahStatus == pendingSurahStatus) {
            hasPendingSurahStatus = false
        }
    }
    LaunchedEffect(statusUpdateError) {
        if (statusUpdateError != null) {
            delay(3_500)
            statusUpdateError = null
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        audioPlayer.stop()
        isPlaying = false
        canResume = false
        positionMs = 0L
        durationMs = 0L
    }

    fun selectAyah(index: Int) {
        val safeIndex = index.coerceIn(0, ayahs.lastIndex.coerceAtLeast(0))
        if (safeIndex != currentAyahIndex) {
            stopPlayback()
            currentAyahIndex = safeIndex
        }
    }

    fun audioUrl(ayah: AyahEntity): String =
        "https://cdn.islamic.network/quran/audio/128/ar.alafasy/${ayah.number}.mp3"

    fun playAyahAt(index: Int, remainingRepeats: Int = readerPreferences.repeatCount) {
        val targetIndex = index.coerceIn(0, ayahs.lastIndex.coerceAtLeast(0))
        val ayah = ayahs.getOrNull(targetIndex) ?: return
        playbackJob?.cancel()
        playbackJob = scope.launch {
            currentAyahIndex = targetIndex
            canResume = false
            isPlaying = true
            positionMs = 0L
            durationMs = 0L
            audioPlayer.setPlaybackSpeed(readerPreferences.playbackSpeed)
            val cacheKey = ayahAudioCacheKey(ayah.surahNumber, ayah.numberInSurah)
            val source = audioCache.getOrFetch(audioUrl(ayah), cacheKey) ?: audioUrl(ayah)
            audioPlayer.play(source) {
                when {
                    remainingRepeats == SurahReaderPreferences.RepeatForever -> {
                        playAyahAt(targetIndex, SurahReaderPreferences.RepeatForever)
                    }

                    remainingRepeats > 1 -> playAyahAt(targetIndex, remainingRepeats - 1)
                    readerPreferences.autoAdvanceAyahs && targetIndex < ayahs.lastIndex -> {
                        playAyahAt(targetIndex + 1, readerPreferences.repeatCount)
                    }

                    else -> {
                        isPlaying = false
                        canResume = false
                        positionMs = durationMs
                    }
                }
            }
            AppTelemetry.logEvent(
                name = "surah_playback_started",
                params = mapOf(
                    "surah_number" to surahNumber.toString(),
                    "ayah_number" to ayah.number.toString(),
                    "speed" to readerPreferences.playbackSpeed.toString(),
                ),
            )
        }
    }

    LaunchedEffect(surahNumber, language) {
        viewModel.loadChapterNames(language)
    }
    LaunchedEffect(surahNumber, language, readerPreferences.showTranslation, ayahs.size) {
        if (readerPreferences.showTranslation) {
            viewModel.loadTranslations(surahNumber, language, ayahs.size)
        }
    }
    LaunchedEffect(surahNumber, ayahs) {
        val lastAyahNumber = UserSettingsStorage
            .takeIf { it.getLastPlaybackSurah() == surahNumber }
            ?.getLastPlaybackAyahNumber()
        val restoredAyahIndex = ayahs.indexOfFirst { it.number == lastAyahNumber }
            .takeIf { it >= 0 }
            ?: 0
        currentAyahIndex = restoredAyahIndex
        // Item zero is the reader header. Restoring the selected ayah alone
        // leaves it off-screen, which made “Continue reading” appear broken.
        listState.scrollToItem(index = restoredAyahIndex + 1)
        initialSelectionResolved = true
    }
    LaunchedEffect(initialSelectionResolved, readerPreferences.autoplayOnOpen) {
        if (initialSelectionResolved && readerPreferences.autoplayOnOpen && ayahs.isNotEmpty()) {
            playAyahAt(currentAyahIndex)
        }
    }
    LaunchedEffect(currentAyah?.number, surahNumber) {
        currentAyah?.let { UserSettingsStorage.saveLastPlaybackPosition(surahNumber, it.number) }
    }
    // Reading is not limited to playback or tapping an ayah. Persist the first
    // ayah currently crossing the reader viewport, so Continue reading returns
    // to the place where the person actually stopped scrolling.
    LaunchedEffect(initialSelectionResolved, listState, ayahs, surahNumber) {
        if (!initialSelectionResolved || ayahs.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.index > 0 && item.offset + item.size > 0 }
                ?.index
        }
            .distinctUntilChanged()
            .collect { listIndex ->
                ayahs.getOrNull((listIndex ?: 0) - 1)?.let { ayah ->
                    UserSettingsStorage.saveLastPlaybackPosition(surahNumber, ayah.number)
                }
            }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = audioPlayer.getPositionMs()
            durationMs = audioPlayer.getDurationMs()
            delay(200)
        }
    }
    DisposableEffect(audioPlayer) {
        onDispose { stopPlayback() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            backgroundColor = colors.readingBackground,
            topBar = {
                Column {
                    PlatformSurahReaderTopBar(
                        title = translatedTitle,
                        subtitle = topSubtitle,
                        onBack = onDismiss,
                    )
                    ReaderFixedSurahStatusButton(
                        status = surahStatus,
                        copy = copy,
                        onClick = { isShowingSurahStatus = true },
                    )
                }
            },
            bottomBar = {
                ReaderMiniPlayer(
                    currentAyahNumber = currentAyah?.numberInSurah ?: 1,
                    totalAyahs = ayahs.size,
                    isPlaying = isPlaying,
                    canResume = canResume,
                    playbackSpeed = readerPreferences.playbackSpeed,
                    progress = if (durationMs > 0L) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    positionText = formatDuration(positionMs),
                    durationText = formatDuration(durationMs),
                    copy = copy,
                    onPrevious = {
                        playAyahAt((currentAyahIndex - 1).coerceAtLeast(0))
                    },
                    onNext = {
                        playAyahAt((currentAyahIndex + 1).coerceAtMost(ayahs.lastIndex.coerceAtLeast(0)))
                    },
                    onPlayPause = {
                        when {
                            isPlaying -> {
                                audioPlayer.pause()
                                isPlaying = false
                                canResume = true
                                AppTelemetry.logEvent("surah_playback_paused")
                            }

                            canResume -> {
                                audioPlayer.setPlaybackSpeed(readerPreferences.playbackSpeed)
                                audioPlayer.resume()
                                isPlaying = true
                                AppTelemetry.logEvent("surah_playback_resumed")
                            }

                            else -> playAyahAt(currentAyahIndex)
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 18.dp,
                ),
            ) {
                item(key = "header") {
                    ReaderHeader(
                        arabicName = chapterNames[surahNumber]?.arabic ?: surahWithAyahs.surah.name,
                    )
                }
                itemsIndexed(ayahs, key = { _, ayah -> ayah.number }) { index, ayah ->
                    ReaderAyahRow(
                        ayah = ayah,
                        translation = if (readerPreferences.showTranslation) {
                            translations[ayah.number]
                        } else {
                            null
                        },
                        readingFontSize = settings.readingFontSize,
                        isCurrent = readerPreferences.highlightPlayingAyah &&
                            currentAyahIndex == index && (isPlaying || canResume),
                        onSelect = { selectAyah(index) },
                    )
                    if (index != ayahs.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(start = 68.dp),
                            color = colors.border,
                        )
                    }
                }
            }
        }

        if (isShowingSurahStatus) {
            SurahStatusSheet(
                currentStatus = surahStatus,
                copy = copy,
                onDismiss = { isShowingSurahStatus = false },
                onStatusSelected = { status ->
                    isShowingSurahStatus = false
                    val requestId = statusRequestId + 1
                    statusRequestId = requestId
                    hasPendingSurahStatus = true
                    pendingSurahStatus = status
                    statusUpdateError = null
                    viewModel.setSurahStatus(
                        surahNumber = surahNumber,
                        status = status,
                        onError = { message ->
                            if (statusRequestId == requestId) {
                                hasPendingSurahStatus = false
                                statusUpdateError = message
                            }
                        },
                    )
                },
                sheetState = statusSheetState,
            )
        }

        statusUpdateError?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colors.error,
                contentColor = MaterialTheme.colors.onError,
                elevation = 6.dp,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    arabicName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = arabicName,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onBackground,
            fontFamily = getQuranFontFamily(),
            fontSize = 29.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReaderFixedSurahStatusButton(
    status: SurahTodoStatus?,
    copy: SurahReaderCopy,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val shape = RoundedCornerShape(16.dp)
    val statusLabel = when (status) {
        SurahTodoStatus.LEARNED -> copy.learned
        SurahTodoStatus.LEARNING -> copy.learning
        null -> copy.notStarted
    }
    val statusColor = when (status) {
        SurahTodoStatus.LEARNED -> colors.success
        SurahTodoStatus.LEARNING -> MaterialTheme.colors.primary
        null -> colors.textSecondary
    }
    val background = when (status) {
        SurahTodoStatus.LEARNED -> colors.success.copy(alpha = 0.12f)
        SurahTodoStatus.LEARNING -> colors.primaryWeak
        null -> colors.readingMutedSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
            .clip(shape)
            .adaptiveClickable(
                shape = shape,
                onClickLabel = copy.status,
                onClick = onClick,
            ),
        shape = shape,
        color = background,
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copy.status,
                color = MaterialTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun ReaderAyahRow(
    ayah: AyahEntity,
    translation: String?,
    readingFontSize: Int,
    isCurrent: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val activeShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AyahNumberMarker(number = ayah.numberInSurah)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(activeShape)
                .background(if (isCurrent) colors.primaryWeak.copy(alpha = 0.72f) else colors.readingBackground)
                .adaptiveClickable(
                    shape = activeShape,
                    onClickLabel = "Select ayah ${ayah.numberInSurah}",
                    onClick = onSelect,
                )
                .padding(horizontal = 10.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = ayah.text,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.onBackground,
                fontFamily = getQuranFontFamily(),
                fontSize = readingFontSize.sp,
                lineHeight = (readingFontSize * 1.65f).sp,
                textAlign = TextAlign.End,
            )
            translation?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun AyahNumberMarker(
    number: Int,
) {
    val colors = MaterialTheme.extendedColors
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = colors.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReaderMiniPlayer(
    currentAyahNumber: Int,
    totalAyahs: Int,
    isPlaying: Boolean,
    canResume: Boolean,
    playbackSpeed: Float,
    progress: Float,
    positionText: String,
    durationText: String,
    copy: SurahReaderCopy,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .platformBottomSystemBarsPadding(),
        color = colors.readingSurface,
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BookmarkBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${copy.reciter} · ${copy.ayah} $currentAyahNumber ${copy.of} $totalAyahs",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playbackSpeed}×",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(positionText, color = colors.textSecondary, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colors.primary,
                    backgroundColor = colors.readingMutedSurface,
                )
                Text(durationText, color = colors.textSecondary, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = copy.previous,
                        tint = MaterialTheme.colors.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .adaptiveClickable(
                            shape = CircleShape,
                            onClickLabel = if (isPlaying) copy.pause else copy.play,
                            onClick = onPlayPause,
                        ),
                    shape = CircleShape,
                    color = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    elevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) copy.pause else copy.play,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = copy.next,
                        tint = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahStatusSheet(
    currentStatus: SurahTodoStatus?,
    copy: SurahReaderCopy,
    onDismiss: () -> Unit,
    onStatusSelected: (SurahTodoStatus?) -> Unit,
    sheetState: com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState,
) {
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = copy.status,
                color = MaterialTheme.colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = copy.statusHint,
                color = MaterialTheme.extendedColors.textSecondary,
                fontSize = 14.sp,
            )
            listOf(
                null to copy.notStarted,
                SurahTodoStatus.LEARNING to copy.learning,
                SurahTodoStatus.LEARNED to copy.learned,
            ).forEach { (status, label) ->
                val isSelected = status == currentStatus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .adaptiveClickable(
                            shape = RoundedCornerShape(14.dp),
                            onClick = { onStatusSelected(status) },
                        )
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusChoiceIcon(status)
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChoiceIcon(status: SurahTodoStatus?) {
    val colors = MaterialTheme.extendedColors
    when (status) {
        SurahTodoStatus.LEARNED -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(20.dp),
        )

        SurahTodoStatus.LEARNING -> Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.size(20.dp),
        )

        null -> Icon(
            imageVector = Icons.Filled.BookmarkBorder,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private data class SurahReaderCopy(
    val ayah: String,
    val ayahs: String,
    val status: String,
    val of: String,
    val reciter: String,
    val previous: String,
    val next: String,
    val play: String,
    val pause: String,
    val notStarted: String,
    val learning: String,
    val learned: String,
    val statusHint: String,
)

private fun surahReaderCopy(language: AppLanguage): SurahReaderCopy = when (language) {
    AppLanguage.RU -> SurahReaderCopy(
        ayah = "Аят", ayahs = "аятов", status = "Статус суры", of = "из",
        reciter = "Аль-Афаси", previous = "Предыдущий", next = "Следующий",
        play = "Воспроизвести", pause = "Пауза", notStarted = "Не начал",
        learning = "Заучиваю", learned = "Выучил", statusHint = "Выберите текущий статус суры.",
    )
    AppLanguage.TG -> SurahReaderCopy(
        ayah = "Оят", ayahs = "оят", status = "Ҳолати сура", of = "аз",
        reciter = "Ал-Афасӣ", previous = "Пешина", next = "Баъдӣ",
        play = "Навохтан", pause = "Таваққуф", notStarted = "Шурӯъ накардаам",
        learning = "Ҳифз мекунам", learned = "Ҳифз кардам", statusHint = "Ҳолати ҷории сураро интихоб кунед.",
    )
    AppLanguage.UZ -> SurahReaderCopy(
        ayah = "Oyat", ayahs = "oyat", status = "Sura holati", of = "dan",
        reciter = "Al-Afasi", previous = "Oldingi", next = "Keyingi",
        play = "Tinglash", pause = "Pauza", notStarted = "Boshlamaganman",
        learning = "Yodlayapman", learned = "Yod oldim", statusHint = "Suraning joriy holatini tanlang.",
    )
    AppLanguage.TR -> SurahReaderCopy(
        ayah = "Ayet", ayahs = "ayet", status = "Sure durumu", of = "/",
        reciter = "El-Afasi", previous = "Önceki", next = "Sonraki",
        play = "Oynat", pause = "Duraklat", notStarted = "Başlamadım",
        learning = "Ezberliyorum", learned = "Ezberledim", statusHint = "Surenin mevcut durumunu seçin.",
    )
    AppLanguage.EN -> SurahReaderCopy(
        ayah = "Ayah", ayahs = "ayahs", status = "Surah status", of = "of",
        reciter = "Al-Afasy", previous = "Previous", next = "Next",
        play = "Play", pause = "Pause", notStarted = "Not started",
        learning = "Learning", learned = "Learned", statusHint = "Choose the current memorisation status for this surah.",
    )
}
