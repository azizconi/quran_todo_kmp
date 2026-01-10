package tj.app.quran_todo.presentation.surah

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioPlayer
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.utils.ayahStableId
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus

@Composable
fun SurahScreen(
    surahWithAyahs: SurahWithAyahs,
    onDismiss: () -> Unit,
    viewModel: SurahViewModel = koinViewModel(),
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val todoList by viewModel.ayahTodos(surahWithAyahs.surah.number).collectAsState(emptyList())
    val todoByAyah = remember(todoList) { todoList.associateBy { it.ayahNumber } }
    val translations by viewModel.ayahTranslations.collectAsState()
    val ruSurahNames by viewModel.ruSurahNames.collectAsState()
    val chapterNames by viewModel.chapterNames.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val totalAyahs = surahWithAyahs.ayahs.size
    val surahNumber = surahWithAyahs.surah.number
    val audioList = surahWithAyahs.ayahs
    val audioCache = remember { AudioCache() }
    val audioPlayer = remember { AudioPlayer() }
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var canResume by remember { mutableStateOf(false) }
    var repeatCount by remember { mutableStateOf(UserSettingsStorage.getRepeatCount() ?: 1) }
    var playbackMode by remember {
        val saved = UserSettingsStorage.getPlaybackMode()
        mutableStateOf(PlaybackMode.fromStorage(saved))
    }
    var playbackSpeed by remember { mutableStateOf(UserSettingsStorage.getPlaybackSpeed() ?: 1.0f) }
    var loopStartAyahNumber by remember { mutableStateOf(UserSettingsStorage.getLoopStartAyahNumber()) }
    var loopEndAyahNumber by remember { mutableStateOf(UserSettingsStorage.getLoopEndAyahNumber()) }
    var translationMode by remember { mutableStateOf(UserSettingsStorage.isTranslationModeEnabled() ?: false) }
    var translationDelayMs by remember { mutableStateOf(UserSettingsStorage.getTranslationDelayMs() ?: 3000L) }
    var isTranslationPause by remember { mutableStateOf(false) }
    var translationFocusAyahNumber by remember { mutableStateOf<Int?>(null) }
    var translationPauseJob by remember { mutableStateOf<Job?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var currentAyahIndex by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var noteTarget by remember { mutableStateOf<AyahEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val playbackModeState = rememberUpdatedState(playbackMode)
    val repeatCountState = rememberUpdatedState(repeatCount)
    val playbackSpeedState = rememberUpdatedState(playbackSpeed)
    val ayahsState = rememberUpdatedState(audioList)
    val translationModeState = rememberUpdatedState(translationMode)
    val translationDelayState = rememberUpdatedState(translationDelayMs)
    val loopStartState = rememberUpdatedState(loopStartAyahNumber)
    val loopEndState = rememberUpdatedState(loopEndAyahNumber)

    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop() }
    }

    LaunchedEffect(language, surahWithAyahs.surah.number) {
        viewModel.loadTranslations(surahNumber, language, totalAyahs)
        viewModel.loadChapterNames(language)
    }

    LaunchedEffect(surahNumber) {
        viewModel.observeNotes(surahNumber)
        viewModel.observeReviews(surahNumber)
    }

    LaunchedEffect(surahNumber) {
        val lastSurah = UserSettingsStorage.getLastPlaybackSurah()
        val lastAyah = UserSettingsStorage.getLastPlaybackAyahNumber()
        if (lastSurah == surahNumber && lastAyah != null) {
            val index = audioList.indexOfFirst { it.number == lastAyah }
            if (index >= 0) {
                currentAyahIndex = index
            }
        }
    }

    LaunchedEffect(currentAyahIndex, surahNumber) {
        val ayahNumber = audioList.getOrNull(currentAyahIndex)?.number
        if (ayahNumber != null) {
            UserSettingsStorage.saveLastPlaybackPosition(surahNumber, ayahNumber)
        }
    }

    LaunchedEffect(playbackMode) {
        UserSettingsStorage.savePlaybackMode(playbackMode.storageValue)
    }

    LaunchedEffect(playbackSpeed) {
        UserSettingsStorage.savePlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(repeatCount) {
        UserSettingsStorage.saveRepeatCount(repeatCount)
    }

    LaunchedEffect(loopStartAyahNumber, loopEndAyahNumber) {
        UserSettingsStorage.saveLoopRange(loopStartAyahNumber, loopEndAyahNumber)
    }

    LaunchedEffect(translationMode) {
        UserSettingsStorage.saveTranslationModeEnabled(translationMode)
    }

    LaunchedEffect(translationDelayMs) {
        UserSettingsStorage.saveTranslationDelayMs(translationDelayMs)
    }

    LaunchedEffect(currentAyahIndex) {
        if (currentAyahIndex >= 0) {
            listState.animateScrollToItem((currentAyahIndex + 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(isPlaying, currentAyahIndex) {
        if (!isPlaying) return@LaunchedEffect
        while (isPlaying) {
            positionMs = audioPlayer.getPositionMs()
            durationMs = audioPlayer.getDurationMs()
            delay(200)
        }
    }

    fun cancelTranslationPause() {
        translationPauseJob?.cancel()
        translationPauseJob = null
        isTranslationPause = false
        translationFocusAyahNumber = null
    }

    fun stopPlayback() {
        cancelTranslationPause()
        audioPlayer.stop()
        isPlaying = false
        canResume = false
    }

    fun ayahAudioUrl(ayahNumber: Int): String {
        return "https://cdn.islamic.network/quran/audio/128/ar.alafasy/$ayahNumber.mp3"
    }

    fun resolveLoopRangeIndices(): Pair<Int, Int>? {
        val startNumber = loopStartState.value
        val endNumber = loopEndState.value
        if (startNumber == null || endNumber == null) return null
        val startIndex = audioList.indexOfFirst { it.number == startNumber }
        val endIndex = audioList.indexOfFirst { it.number == endNumber }
        if (startIndex < 0 || endIndex < 0) return null
        return if (startIndex <= endIndex) startIndex to endIndex else endIndex to startIndex
    }

    fun normalizeLoopRange() {
        val range = resolveLoopRangeIndices() ?: return
        val startAyah = audioList.getOrNull(range.first)?.number
        val endAyah = audioList.getOrNull(range.second)?.number
        if (startAyah != null && endAyah != null) {
            loopStartAyahNumber = startAyah
            loopEndAyahNumber = endAyah
        }
    }

    fun setLoopStartToCurrent() {
        val currentAyahNumber = audioList.getOrNull(currentAyahIndex)?.number ?: return
        loopStartAyahNumber = currentAyahNumber
        if (loopEndAyahNumber != null) {
            normalizeLoopRange()
        }
    }

    fun setLoopEndToCurrent() {
        val currentAyahNumber = audioList.getOrNull(currentAyahIndex)?.number ?: return
        loopEndAyahNumber = currentAyahNumber
        if (loopStartAyahNumber != null) {
            normalizeLoopRange()
            playbackMode = PlaybackMode.RANGE
        }
    }

    fun clearLoopRange() {
        loopStartAyahNumber = null
        loopEndAyahNumber = null
        if (playbackMode == PlaybackMode.RANGE) {
            playbackMode = PlaybackMode.SURAH
        }
    }

    fun playAyahAt(index: Int, remainingRepeats: Int) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val ayahs = ayahsState.value
            val range = resolveLoopRangeIndices()
            val targetIndex = if (playbackModeState.value == PlaybackMode.RANGE && range != null) {
                index.coerceIn(range.first, range.second)
            } else {
                index
            }
            val ayah = ayahs.getOrNull(targetIndex)
            if (ayah == null) {
                stopPlayback()
                return@launch
            }
            currentAyahIndex = targetIndex
            positionMs = 0L
            durationMs = 0L
            canResume = true
            cancelTranslationPause()
            audioPlayer.setPlaybackSpeed(playbackSpeedState.value)
            val remoteUrl = ayahAudioUrl(ayah.number)
            val cacheKey = "ayah_${ayahStableId(ayah.surahNumber, ayah.numberInSurah)}"
            val cachedPath = audioCache.getOrFetch(remoteUrl, cacheKey)
            val source = cachedPath ?: remoteUrl
            audioPlayer.play(source) {
                val mode = playbackModeState.value
                val delayMs = translationDelayState.value
                val nextAction = {
                    when (mode) {
                        PlaybackMode.AYAH -> {
                            if (remainingRepeats > 1) {
                                playAyahAt(targetIndex, remainingRepeats - 1)
                            } else {
                                stopPlayback()
                            }
                        }
                        PlaybackMode.SURAH -> {
                            if (remainingRepeats > 1) {
                                playAyahAt(targetIndex, remainingRepeats - 1)
                            } else {
                                playAyahAt(targetIndex + 1, repeatCountState.value)
                            }
                        }
                        PlaybackMode.RANGE -> {
                            val resolved = resolveLoopRangeIndices()
                            if (resolved == null) {
                                stopPlayback()
                            } else {
                                val nextIndex = if (remainingRepeats > 1) {
                                    targetIndex
                                } else if (targetIndex >= resolved.second) {
                                    resolved.first
                                } else {
                                    targetIndex + 1
                                }
                                val repeats = if (remainingRepeats > 1) remainingRepeats - 1 else repeatCountState.value
                                playAyahAt(nextIndex, repeats)
                            }
                        }
                    }
                }
                if (translationModeState.value) {
                    translationPauseJob?.cancel()
                    translationPauseJob = scope.launch {
                        isTranslationPause = true
                        translationFocusAyahNumber = ayah.number
                        delay(delayMs)
                        isTranslationPause = false
                        translationFocusAyahNumber = null
                        nextAction()
                    }
                } else {
                    nextAction()
                }
            }
            isPlaying = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = surahWithAyahs.surah.name,
                        style = MaterialTheme.typography.h6,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
            )
        },
        bottomBar = {
            val activeAyah = audioList.getOrNull(currentAyahIndex)
            if (activeAyah != null) {
                MiniPlayerBar(
                    isPlaying = isPlaying,
                    isPausedForTranslation = isTranslationPause,
                    currentLabel = "${strings.ayahsLabel} ${activeAyah.numberInSurah} / $totalAyahs",
                    progress = if (durationMs > 0) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onPrev = {
                        cancelTranslationPause()
                        val range = resolveLoopRangeIndices()
                        val prevIndex = if (playbackMode == PlaybackMode.RANGE && range != null) {
                            if (currentAyahIndex <= range.first) range.second else currentAyahIndex - 1
                        } else {
                            (currentAyahIndex - 1).coerceAtLeast(0)
                        }
                        canResume = false
                        playAyahAt(prevIndex, repeatCount)
                    },
                    onNext = {
                        cancelTranslationPause()
                        val range = resolveLoopRangeIndices()
                        val nextIndex = if (playbackMode == PlaybackMode.RANGE && range != null) {
                            if (currentAyahIndex >= range.second) range.first else currentAyahIndex + 1
                        } else {
                            (currentAyahIndex + 1).coerceAtMost(audioList.lastIndex)
                        }
                        canResume = false
                        playAyahAt(nextIndex, repeatCount)
                    },
                    onPlayPause = {
                        if (isTranslationPause) {
                            stopPlayback()
                        } else if (isPlaying) {
                            audioPlayer.pause()
                            isPlaying = false
                        } else if (canResume) {
                            audioPlayer.setPlaybackSpeed(playbackSpeed)
                            audioPlayer.resume()
                            isPlaying = true
                        } else {
                            playAyahAt(currentAyahIndex, repeatCount)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 0.dp,
                top = innerPadding.calculateTopPadding(),
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                val translated = chapterNames[surahNumber]?.translated ?: when (language) {
                    tj.app.quran_todo.common.i18n.AppLanguage.RU ->
                        ruSurahNames[surahNumber] ?: surahWithAyahs.surah.englishNameTranslation
                    else -> surahWithAyahs.surah.englishNameTranslation
                }
                SurahHeader(
                    arabicName = chapterNames[surahNumber]?.arabic ?: surahWithAyahs.surah.name,
                    transliteration = chapterNames[surahNumber]?.transliteration
                        ?: surahWithAyahs.surah.englishName,
                    translatedName = translated,
                    revelationPlace = localizeRevelationPlace(surahWithAyahs.surah.revelationType, strings),
                    totalAyahs = totalAyahs,
                    surahNumber = surahWithAyahs.surah.number.toString()
                )
            }
            item {
                val loopStartText = loopStartAyahNumber?.let { number ->
                    val index = audioList.indexOfFirst { it.number == number }
                    if (index >= 0) "${strings.ayahsLabel} ${audioList[index].numberInSurah}" else null
                }
                val loopEndText = loopEndAyahNumber?.let { number ->
                    val index = audioList.indexOfFirst { it.number == number }
                    if (index >= 0) "${strings.ayahsLabel} ${audioList[index].numberInSurah}" else null
                }
                AudioControls(
                    isPlaying = isPlaying,
                    isPausedForTranslation = isTranslationPause,
                    repeatCount = repeatCount,
                    playbackMode = playbackMode,
                    playbackSpeed = playbackSpeed,
                    currentLabel = "${strings.ayahsLabel} ${(audioList.getOrNull(currentAyahIndex)?.numberInSurah ?: 1)} / $totalAyahs",
                    loopStartLabel = loopStartText,
                    loopEndLabel = loopEndText,
                    translationMode = translationMode,
                    translationDelayMs = translationDelayMs,
                    onPlayPause = {
                        if (isTranslationPause) {
                            stopPlayback()
                        } else if (isPlaying) {
                            audioPlayer.pause()
                            isPlaying = false
                        } else if (canResume) {
                            audioPlayer.setPlaybackSpeed(playbackSpeed)
                            audioPlayer.resume()
                            isPlaying = true
                        } else {
                            if (playbackMode == PlaybackMode.RANGE && resolveLoopRangeIndices() == null) {
                                setLoopStartToCurrent()
                                setLoopEndToCurrent()
                            }
                            playAyahAt(currentAyahIndex, repeatCount)
                        }
                    },
                    onRepeatChange = {
                        repeatCount = when (repeatCount) {
                            1 -> 3
                            3 -> 5
                            else -> 1
                        }
                    },
                    onPlaybackModeChange = {
                        playbackMode = when (playbackMode) {
                            PlaybackMode.SURAH -> PlaybackMode.AYAH
                            PlaybackMode.AYAH -> PlaybackMode.RANGE
                            PlaybackMode.RANGE -> PlaybackMode.SURAH
                        }
                    },
                    onSpeedChange = {
                        playbackSpeed = when (playbackSpeed) {
                            0.75f -> 1.0f
                            1.0f -> 1.25f
                            else -> 0.75f
                        }
                        audioPlayer.setPlaybackSpeed(playbackSpeed)
                    },
                    onSetLoopStart = { setLoopStartToCurrent() },
                    onSetLoopEnd = { setLoopEndToCurrent() },
                    onClearLoop = { clearLoopRange() },
                    onToggleTranslationMode = { translationMode = !translationMode },
                    onTranslationDelayChange = {
                        translationDelayMs = when (translationDelayMs) {
                            2000L -> 3500L
                            3500L -> 5000L
                            else -> 2000L
                        }
                    }
                )
            }
            items(audioList, key = { ayahStableId(it.surahNumber, it.numberInSurah) }) { ayah ->
                val status = todoByAyah[ayah.number]?.status
                val translation = translations[ayah.number]
                val note = notes[ayah.number]?.note
                val review = reviews[ayah.number]
                val reviewDue = review != null && review.nextReviewAt <= getTimeMillis()
                val isActive = audioList.getOrNull(currentAyahIndex)?.number == ayah.number
                val isTranslationFocus = translationFocusAyahNumber == ayah.number
                AyahCard(
                    ayah = ayah,
                    status = status,
                    statusLabel = when (status) {
                        AyahTodoStatus.LEARNED -> strings.learnedLabel
                        AyahTodoStatus.LEARNING -> strings.learningLabel
                        null -> null
                    },
                    clearLabel = strings.clearLabel,
                    translation = translation,
                    note = note,
                    reviewDue = reviewDue,
                    isActive = isActive,
                    isTranslationFocus = isTranslationFocus,
                    playbackProgress = if (isActive && durationMs > 0) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onPlayFromHere = {
                        val index = audioList.indexOfFirst { it.number == ayah.number }
                        if (index >= 0) {
                            canResume = false
                            playAyahAt(index, repeatCount)
                        }
                    },
                    onNoteClick = {
                        noteTarget = ayah
                        noteDraft = note ?: ""
                    },
                    onReviewComplete = if (reviewDue) {
                        {
                            viewModel.completeReview(ayah.number, surahNumber)
                        }
                    } else {
                        null
                    },
                    onSwipeToLearning = {
                        viewModel.updateAyahStatus(
                            ayahNumber = ayah.number,
                            surahNumber = surahWithAyahs.surah.number,
                            totalAyahs = totalAyahs,
                            status = AyahTodoStatus.LEARNING
                        )
                    },
                    onSwipeToLearned = {
                        viewModel.updateAyahStatus(
                            ayahNumber = ayah.number,
                            surahNumber = surahWithAyahs.surah.number,
                            totalAyahs = totalAyahs,
                            status = AyahTodoStatus.LEARNED
                        )
                    },
                    onClearStatus = if (status != null) {
                        {
                            viewModel.clearAyahStatus(
                                ayahNumber = ayah.number,
                                surahNumber = surahWithAyahs.surah.number,
                                totalAyahs = totalAyahs
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    noteTarget?.let { ayah ->
        Dialog(
            onDismissRequest = { noteTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${strings.noteTitle} · ${strings.ayahsLabel} ${ayah.numberInSurah}",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(strings.notePlaceholder) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = strings.clearLabel,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable {
                                    viewModel.upsertNote(ayah.number, surahNumber, "")
                                    noteTarget = null
                                }
                        )
                        Text(
                            text = strings.saveLabel,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.clickable {
                                viewModel.upsertNote(ayah.number, surahNumber, noteDraft)
                                noteTarget = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SurahHeader(
    arabicName: String,
    transliteration: String,
    translatedName: String,
    revelationPlace: String,
    totalAyahs: Int,
    surahNumber: String
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = arabicName,
            style = MaterialTheme.typography.h5
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = transliteration,
            style = MaterialTheme.typography.body2
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = translatedName,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = revelationPlace,
            style = MaterialTheme.typography.caption
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderChip("${strings.ayahsLabel}: $totalAyahs")
            HeaderChip("${strings.surahLabel} #$surahNumber")
        }
    }
}

@Composable
private fun HeaderChip(text: String) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 1.dp) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AudioControls(
    isPlaying: Boolean,
    isPausedForTranslation: Boolean,
    repeatCount: Int,
    playbackMode: PlaybackMode,
    playbackSpeed: Float,
    currentLabel: String,
    loopStartLabel: String?,
    loopEndLabel: String?,
    translationMode: Boolean,
    translationDelayMs: Long,
    onPlayPause: () -> Unit,
    onRepeatChange: () -> Unit,
    onPlaybackModeChange: () -> Unit,
    onSpeedChange: () -> Unit,
    onSetLoopStart: () -> Unit,
    onSetLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onToggleTranslationMode: () -> Unit,
    onTranslationDelayChange: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying && !isPausedForTranslation) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )
                    }
                    Column {
                        Text(
                            text = if (isPlaying && !isPausedForTranslation) {
                                LocalAppStrings.current.focusStopLabel
                            } else {
                                LocalAppStrings.current.focusStartLabel
                            },
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onRepeatChange() }
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "${repeatCount}x", style = MaterialTheme.typography.caption)
                    }
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onSpeedChange() }
                    )
                    Text(
                        text = playbackMode.label(LocalAppStrings.current),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onPlaybackModeChange() }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = LocalAppStrings.current.loopSetStartLabel,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onSetLoopStart() }
                    )
                    Text(
                        text = LocalAppStrings.current.loopSetEndLabel,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onSetLoopEnd() }
                    )
                    Text(
                        text = LocalAppStrings.current.clearLabel,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.clickable { onClearLoop() }
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            loopStartLabel != null && loopEndLabel != null ->
                                "${loopStartLabel} → ${loopEndLabel}"
                            loopStartLabel != null ->
                                "${LocalAppStrings.current.loopStartLabel}: ${loopStartLabel}"
                            loopEndLabel != null ->
                                "${LocalAppStrings.current.loopEndLabel}: ${loopEndLabel}"
                            else -> LocalAppStrings.current.loopRangeEmptyLabel
                        },
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (translationMode) {
                        LocalAppStrings.current.translationModeOnLabel
                    } else {
                        LocalAppStrings.current.translationModeOffLabel
                    },
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.clickable { onToggleTranslationMode() }
                )
                Text(
                    text = "${LocalAppStrings.current.translationDelayLabel}: ${translationDelayMs / 1000}s",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.clickable { onTranslationDelayChange() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AyahCard(
    ayah: AyahEntity,
    status: AyahTodoStatus?,
    statusLabel: String?,
    clearLabel: String,
    translation: String?,
    note: String?,
    reviewDue: Boolean,
    isActive: Boolean,
    isTranslationFocus: Boolean,
    playbackProgress: Float,
    onPlayFromHere: () -> Unit,
    onNoteClick: () -> Unit,
    onReviewComplete: (() -> Unit)?,
    onSwipeToLearning: () -> Unit,
    onSwipeToLearned: () -> Unit,
    onClearStatus: (() -> Unit)?,
) {
    val dismissState = rememberDismissState(confirmStateChange = { value ->
        when (value) {
            DismissValue.DismissedToEnd -> {
                onSwipeToLearning()
                false
            }
            DismissValue.DismissedToStart -> {
                onSwipeToLearned()
                false
            }
            DismissValue.Default -> true
        }
    })

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        background = {
            val direction = dismissState.dismissDirection
            val baseColor = when (direction) {
                DismissDirection.StartToEnd -> MaterialTheme.colors.secondary
                DismissDirection.EndToStart -> MaterialTheme.colors.primary
                null -> Color.Transparent
            }
            val progress = dismissState.progress.fraction.coerceIn(0f, 1f)
            val alpha = 0.15f + (0.7f * progress)
            val label = when (direction) {
                DismissDirection.StartToEnd -> LocalAppStrings.current.learningLabel
                DismissDirection.EndToStart -> LocalAppStrings.current.learnedLabel
                null -> ""
            }
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
                null -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = alpha))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissContent = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onPlayFromHere() },
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                backgroundColor = if (isActive) {
                    MaterialTheme.colors.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colors.surface
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isActive) {
                        Text(
                            text = "${LocalAppStrings.current.nowReadingLabel} ${ayah.numberInSurah}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    } else if (isTranslationFocus) {
                        Text(
                            text = LocalAppStrings.current.translationPauseLabel,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colors.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ayah.numberInSurah.toString(),
                                style = MaterialTheme.typography.body1
                            )
                        }
                        IconButton(onClick = onPlayFromHere, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = ayah.text,
                                fontSize = 25.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = getQuranFontFamily()
                            )
                        }
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = playbackProgress,
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                            color = MaterialTheme.colors.primary
                        )
                    }

                    if (!translation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = translation,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    if (statusLabel != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${LocalAppStrings.current.statusLabel}: $statusLabel",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.primary
                            )
                            if (onClearStatus != null) {
                                Text(
                                    text = clearLabel,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.error,
                                    modifier = Modifier.clickable { onClearStatus() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onNoteClick() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (note.isNullOrBlank()) LocalAppStrings.current.notePlaceholder else note,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (reviewDue && onReviewComplete != null) {
                            Text(
                                text = LocalAppStrings.current.reviewNowLabel,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.clickable { onReviewComplete() }
                            )
                        }
                    }
                }
            }
        }
    )
}

private enum class PlaybackMode(val storageValue: String) {
    SURAH("surah"),
    AYAH("ayah"),
    RANGE("range");

    fun label(strings: tj.app.quran_todo.common.i18n.AppStrings): String = when (this) {
        SURAH -> strings.playbackModeSurah
        AYAH -> strings.playbackModeAyah
        RANGE -> strings.playbackModeRange
    }

    companion object {
        fun fromStorage(value: String?): PlaybackMode = when (value) {
            SURAH.storageValue -> SURAH
            AYAH.storageValue -> AYAH
            RANGE.storageValue -> RANGE
            else -> SURAH
        }
    }
}

@Composable
private fun MiniPlayerBar(
    isPlaying: Boolean,
    isPausedForTranslation: Boolean,
    currentLabel: String,
    progress: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Card(
        elevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                color = MaterialTheme.colors.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                if (isPausedForTranslation) {
                    Text(
                        text = LocalAppStrings.current.translationPauseLabel,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null)
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                    }
                }
            }
        }
    }
}
