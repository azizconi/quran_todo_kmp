package tj.app.quran_todo.presentation.surah

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.Button
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
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
import androidx.compose.material.rememberModalBottomSheetState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioPlayer
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
import tj.app.quran_todo.common.recitation.AyahRecitationMatcher
import tj.app.quran_todo.common.recitation.RecitationErrorCode
import tj.app.quran_todo.common.recitation.RecitationMatch
import tj.app.quran_todo.common.recitation.RecitationPerformanceStore
import tj.app.quran_todo.common.recitation.RecitationRecognizer
import tj.app.quran_todo.common.recitation.RecitationIssue
import tj.app.quran_todo.common.recitation.RecitationIssueType
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.AyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyle
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.progressTrack
import tj.app.quran_todo.common.theme.softSurface
import tj.app.quran_todo.common.theme.tintedSurface
import tj.app.quran_todo.common.utils.ayahStableId
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.presentation.review.ReviewQuality

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SurahScreen(
    surahWithAyahs: SurahWithAyahs,
    onDismiss: () -> Unit,
    viewModel: SurahViewModel = koinViewModel(),
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val ayahCardStyle = LocalAyahCardStyle.current
    val appSettings = LocalAppSettings.current
    val setAppSettings = LocalAppSettingsSetter.current
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
    val recitationStatusSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
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
    var mode by remember { mutableStateOf(SurahMode.LEARN) }
    var examMode by remember { mutableStateOf(appSettings.examModeEnabled) }
    var revealedAyahNumbers by remember { mutableStateOf(setOf<Int>()) }
    var showWeakOnly by remember { mutableStateOf(false) }
    var reviewWeakOnly by remember { mutableStateOf(false) }
    var reviewShowTranslation by remember { mutableStateOf(false) }
    var reviewMarkWeak by remember { mutableStateOf(false) }
    var voiceCheckAyahNumber by remember { mutableStateOf<Int?>(null) }
    var abCompareAyahNumber by remember { mutableStateOf<Int?>(null) }
    var weakAyahKeys by remember {
        mutableStateOf(UserSettingsStorage.getWeakAyahKeys() ?: emptySet())
    }
    val recitationRecognizer = remember { RecitationRecognizer() }
    var recitationEnabled by remember { mutableStateOf(false) }
    var recitationStatus by remember { mutableStateOf<String?>(null) }
    var recitationTranscript by remember { mutableStateOf("") }
    var recitationMatch by remember { mutableStateOf<RecitationMatch?>(null) }
    var recitationHighlightAyahNumber by remember { mutableStateOf<Int?>(null) }
    var recitationHighlightWordIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var recitationProgressiveRevealMode by remember { mutableStateOf(false) }
    var recitationRevealedWordIndexes by remember {
        mutableStateOf<Map<Int, Set<Int>>>(emptyMap())
    }
    var recitationSessionDraft by remember { mutableStateOf(RecitationSessionDraft()) }
    var recitationAyahLevels by remember {
        mutableStateOf<Map<Int, RecitationPassLevel>>(emptyMap())
    }
    var recitationCursorIndex by remember { mutableStateOf(0) }
    var recitationTokenBuffer by remember { mutableStateOf<List<String>>(emptyList()) }
    val listState = rememberLazyListState()
    val playbackModeState = rememberUpdatedState(playbackMode)
    val repeatCountState = rememberUpdatedState(repeatCount)
    val playbackSpeedState = rememberUpdatedState(playbackSpeed)
    val ayahsState = rememberUpdatedState(audioList)
    val translationModeState = rememberUpdatedState(translationMode)
    val translationDelayState = rememberUpdatedState(translationDelayMs)
    val loopStartState = rememberUpdatedState(loopStartAyahNumber)
    val loopEndState = rememberUpdatedState(loopEndAyahNumber)
    val visibleAyahs = remember(showWeakOnly, weakAyahKeys, audioList) {
        if (!showWeakOnly) {
            audioList
        } else {
            audioList.filter { ayah ->
                weakAyahKeys.contains("${ayah.surahNumber}:${ayah.number}")
            }
        }
    }
    val dueReviewAyahNumbers = remember(reviews) {
        reviews.values
            .filter { it.nextReviewAt <= getTimeMillis() }
            .map { it.ayahNumber }
            .toSet()
    }
    val modeAyahs = remember(
        mode,
        visibleAyahs,
        audioList,
        dueReviewAyahNumbers,
        reviewWeakOnly,
        weakAyahKeys,
        currentAyahIndex
    ) {
        when (mode) {
            SurahMode.LEARN -> visibleAyahs
            SurahMode.REVIEW -> {
                audioList.filter { ayah ->
                    dueReviewAyahNumbers.contains(ayah.number) &&
                        (!reviewWeakOnly || weakAyahKeys.contains("${ayah.surahNumber}:${ayah.number}"))
                }
            }
            SurahMode.RECITE -> recitationFocusedAyahs(audioList, currentAyahIndex, radius = 5)
        }
    }
    LaunchedEffect(appSettings.examModeEnabled) {
        examMode = appSettings.examModeEnabled
    }
    LaunchedEffect(mode) {
        if (mode != SurahMode.RECITE) {
            recitationStatusSheetState.hide()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
            recitationRecognizer.stop()
            if (recitationSessionDraft.attempts > 0) {
                RecitationPerformanceStore.recordSession(
                    surahNumber = surahNumber,
                    attempts = recitationSessionDraft.attempts,
                    matched = recitationSessionDraft.matched,
                    confidenceSum = recitationSessionDraft.confidenceSum,
                    issueCount = recitationSessionDraft.issueCount,
                    bestStreak = recitationSessionDraft.bestStreak
                )
            }
        }
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
        AppTelemetry.logScreen(
            screenName = "surah_detail",
            params = mapOf("surah_number" to surahNumber.toString())
        )
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

    fun refreshWeakAyahs() {
        weakAyahKeys = UserSettingsStorage.getWeakAyahKeys() ?: emptySet()
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
                        PlaybackMode.IMAM -> {
                            if (targetIndex >= ayahs.lastIndex) {
                                stopPlayback()
                            } else {
                                playAyahAt(targetIndex + 1, 1)
                            }
                        }
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
                if (translationModeState.value && mode != PlaybackMode.IMAM) {
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

    fun playFromIndex(index: Int, speedOverride: Float? = null) {
        if (speedOverride != null) {
            playbackSpeed = speedOverride
            audioPlayer.setPlaybackSpeed(speedOverride)
        }
        val ayah = audioList.getOrNull(index)
        AppTelemetry.logEvent(
            name = "surah_playback_started",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "ayah_number" to (ayah?.number?.toString() ?: "-1"),
                "mode" to playbackMode.name.lowercase(),
                "speed" to playbackSpeed.toString()
            )
        )
        canResume = false
        val repeats = if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount
        playAyahAt(index, repeats)
    }

    fun toggleRecitation() {
        fun flushRecitationSessionStats() {
            val draft = recitationSessionDraft
            if (draft.attempts <= 0) return
            RecitationPerformanceStore.recordSession(
                surahNumber = surahNumber,
                attempts = draft.attempts,
                matched = draft.matched,
                confidenceSum = draft.confidenceSum,
                issueCount = draft.issueCount,
                bestStreak = draft.bestStreak
            )
            recitationSessionDraft = RecitationSessionDraft()
        }

        if (recitationEnabled) {
            recitationRecognizer.stop()
            flushRecitationSessionStats()
            recitationEnabled = false
            recitationMatch = null
            recitationHighlightAyahNumber = null
            recitationHighlightWordIndexes = emptySet()
            recitationRevealedWordIndexes = emptyMap()
            recitationStatus = strings.recitationStoppedLabel
            AppTelemetry.logEvent(
                name = "surah_recitation_stopped",
                params = mapOf("surah_number" to surahNumber.toString())
            )
            return
        }

        if (isPlaying || isTranslationPause) {
            stopPlayback()
        }
        recitationStatus = strings.recitationListeningLabel
        recitationMatch = null
        recitationTranscript = ""
        recitationHighlightAyahNumber = null
        recitationHighlightWordIndexes = emptySet()
        recitationRevealedWordIndexes = emptyMap()
        recitationSessionDraft = RecitationSessionDraft()
        recitationCursorIndex = currentAyahIndex.coerceIn(0, audioList.lastIndex.coerceAtLeast(0))
        recitationTokenBuffer = emptyList()
        val started = recitationRecognizer.start(
            languageCode = "ar",
            onResult = { text, isFinal ->
                recitationTranscript = text
                val anchorIndex = recitationCursorIndex.coerceIn(0, audioList.lastIndex.coerceAtLeast(0))
                val focusedAyahs = recitationFocusedAyahs(
                    allAyahs = audioList,
                    anchorIndex = anchorIndex,
                    radius = if (isFinal) 6 else 3
                )
                val nearbyMatch = AyahRecitationMatcher.findBestAyah(text, focusedAyahs)
                val match = nearbyMatch ?: if (isFinal) {
                    AyahRecitationMatcher.findBestAyah(text, audioList)
                } else {
                    null
                }
                recitationMatch = match
                recitationHighlightWordIndexes = match?.matchedWordIndexes ?: emptySet()
                val rawMatchIndex = match?.let { matched ->
                    audioList.indexOfFirst { it.number == matched.ayahNumber }.takeIf { it >= 0 }
                }

                if (match != null) {
                    recitationHighlightAyahNumber = match.ayahNumber
                    if (recitationProgressiveRevealMode) {
                        val existingIndexes = recitationRevealedWordIndexes[match.ayahNumber].orEmpty()
                        val mergedIndexes = (existingIndexes + match.matchedWordIndexes)
                            .filter { it >= 0 }
                            .toSet()
                        recitationRevealedWordIndexes =
                            recitationRevealedWordIndexes + (match.ayahNumber to mergedIndexes)
                    }
                    val issuePreview = match.issues.firstOrNull()?.let { describeIssueLocalized(it, strings) }
                    recitationStatus = if (issuePreview == null) {
                        "${strings.ayahsLabel} ${match.ayahNumberInSurah} ${strings.recitationAyahMatchedLabel}"
                    } else {
                        "${strings.ayahsLabel} ${match.ayahNumberInSurah}: $issuePreview"
                    }
                } else if (isFinal) {
                    recitationStatus = strings.recitationNoMatchLabel
                    recitationHighlightAyahNumber = null
                    recitationHighlightWordIndexes = emptySet()
                }

                if (!isFinal) return@start

                val cursorBeforeAdvance = recitationCursorIndex.coerceIn(0, audioList.lastIndex.coerceAtLeast(0))
                val alignedMatchIndex = resolveSequentialMatchIndex(
                    currentCursor = cursorBeforeAdvance,
                    matchIndex = rawMatchIndex,
                    matchConfidence = match?.confidence ?: 0f,
                    lastIndex = audioList.lastIndex
                )
                if (alignedMatchIndex != null) {
                    recitationCursorIndex = alignedMatchIndex
                    currentAyahIndex = alignedMatchIndex
                    if (examMode) {
                        audioList.getOrNull(alignedMatchIndex)?.let { alignedAyah ->
                            revealedAyahNumbers = revealedAyahNumbers + alignedAyah.number
                        }
                    }
                }

                recitationSessionDraft = recitationSessionDraft.registerFinalAttempt(match)
                recitationTokenBuffer = appendRecitationTokens(recitationTokenBuffer, text)

                var buffer = recitationTokenBuffer
                var cursor = recitationCursorIndex.coerceIn(0, audioList.lastIndex.coerceAtLeast(0))
                var latestAdvance: RecitationAdvanceResult? = null

                while (cursor in 0..audioList.lastIndex) {
                    val ayah = audioList[cursor]
                    val completion = evaluateAyahCompletion(ayah, buffer)
                    if (!completion.shouldAdvance) break

                    val previousLevel = recitationAyahLevels[ayah.number]
                    if (previousLevel == null || completion.passLevel.priority > previousLevel.priority) {
                        recitationAyahLevels = recitationAyahLevels + (ayah.number to completion.passLevel)
                    }

                    viewModel.completeReview(
                        ayahNumber = ayah.number,
                        surahNumber = surahNumber,
                        quality = completion.passLevel.reviewQuality
                    )
                    refreshWeakAyahs()

                    if (examMode) {
                        revealedAyahNumbers = revealedAyahNumbers + ayah.number
                    }
                    if (recitationProgressiveRevealMode) {
                        recitationRevealedWordIndexes = recitationRevealedWordIndexes +
                            (ayah.number to allWordIndexes(ayah.text))
                    }

                    latestAdvance = RecitationAdvanceResult(cursor, completion.passLevel)
                    val consumed = completion.consumedTokens.coerceAtLeast(1)
                    buffer = if (consumed >= buffer.size) emptyList() else buffer.drop(consumed)
                    cursor += 1
                }

                recitationTokenBuffer = buffer.takeLast(160)

                if (latestAdvance != null) {
                    val advancedAyah = audioList[latestAdvance.index]
                    val baseStatus =
                        "${strings.ayahsLabel} ${advancedAyah.numberInSurah} ${strings.recitationAyahMatchedLabel} • ${latestAdvance.passLevel.label(strings)}"

                    if (cursor <= audioList.lastIndex) {
                        recitationCursorIndex = cursor
                        currentAyahIndex = cursor
                        recitationHighlightAyahNumber = audioList[cursor].number
                        recitationHighlightWordIndexes = emptySet()
                        recitationStatus = "$baseStatus → ${strings.ayahsLabel} ${audioList[cursor].numberInSurah}"
                    } else {
                        recitationCursorIndex = audioList.lastIndex
                        currentAyahIndex = audioList.lastIndex
                        recitationHighlightAyahNumber = advancedAyah.number
                        recitationHighlightWordIndexes = emptySet()
                        recitationStatus = baseStatus
                    }
                    voiceCheckAyahNumber = null
                } else if (match != null) {
                    val passLevel = recitationPassLevelFor(match)
                    val previousLevel = recitationAyahLevels[match.ayahNumber]
                    if (previousLevel == null || passLevel.priority > previousLevel.priority) {
                        recitationAyahLevels = recitationAyahLevels + (match.ayahNumber to passLevel)
                    }
                    val sequentialMatchIndex = alignedMatchIndex ?: rawMatchIndex
                    val canAdvanceByMatch = passLevel != RecitationPassLevel.HARD &&
                        sequentialMatchIndex != null &&
                        sequentialMatchIndex in cursorBeforeAdvance..(cursorBeforeAdvance + 1)

                    if (canAdvanceByMatch) {
                        val matchedIndex = sequentialMatchIndex
                            ?.coerceIn(0, audioList.lastIndex.coerceAtLeast(0))
                            ?: cursorBeforeAdvance
                        val matchedAyah = audioList[matchedIndex]
                        viewModel.completeReview(
                            ayahNumber = matchedAyah.number,
                            surahNumber = surahNumber,
                            quality = passLevel.reviewQuality
                        )
                        refreshWeakAyahs()
                        if (examMode) {
                            revealedAyahNumbers = revealedAyahNumbers + matchedAyah.number
                        }
                        if (recitationProgressiveRevealMode) {
                            recitationRevealedWordIndexes = recitationRevealedWordIndexes +
                                (matchedAyah.number to allWordIndexes(matchedAyah.text))
                        }
                        recitationTokenBuffer = emptyList()
                        val nextIndex = (matchedIndex + 1).coerceAtMost(audioList.lastIndex)
                        val baseStatus =
                            "${strings.ayahsLabel} ${matchedAyah.numberInSurah} ${strings.recitationAyahMatchedLabel} • ${passLevel.label(strings)}"
                        if (nextIndex > matchedIndex) {
                            recitationCursorIndex = nextIndex
                            currentAyahIndex = nextIndex
                            recitationHighlightAyahNumber = audioList[nextIndex].number
                            recitationHighlightWordIndexes = emptySet()
                            recitationStatus = "$baseStatus → ${strings.ayahsLabel} ${audioList[nextIndex].numberInSurah}"
                        } else {
                            recitationCursorIndex = matchedIndex
                            currentAyahIndex = matchedIndex
                            recitationHighlightAyahNumber = matchedAyah.number
                            recitationHighlightWordIndexes = emptySet()
                            recitationStatus = baseStatus
                        }
                        voiceCheckAyahNumber = null
                    } else {
                        voiceCheckAyahNumber = match.ayahNumber
                        val issuePreview = match.issues.firstOrNull()?.let { describeIssueLocalized(it, strings) }
                        recitationStatus = if (issuePreview == null) {
                            "${strings.ayahsLabel} ${match.ayahNumberInSurah} ${strings.recitationAyahMatchedLabel} • ${passLevel.label(strings)}"
                        } else {
                            "${strings.ayahsLabel} ${match.ayahNumberInSurah}: $issuePreview • ${passLevel.label(strings)}"
                        }
                    }
                } else {
                    voiceCheckAyahNumber = null
                }
            },
            onError = { message ->
                flushRecitationSessionStats()
                recitationStatus = localizeRecitationError(message, strings)
                recitationEnabled = false
                recitationHighlightAyahNumber = null
                recitationHighlightWordIndexes = emptySet()
                recitationRevealedWordIndexes = emptyMap()
                AppTelemetry.logEvent(
                    name = "surah_recitation_error",
                    params = mapOf(
                        "surah_number" to surahNumber.toString(),
                        "message" to message
                    )
                )
            }
        )
        recitationEnabled = started
        AppTelemetry.logEvent(
            name = if (started) "surah_recitation_started" else "surah_recitation_start_failed",
            params = mapOf("surah_number" to surahNumber.toString())
        )
        if (!started) {
            recitationStatus = recitationStatus ?: strings.recitationUnavailableLabel
        }
    }

    ModalBottomSheetLayout(
        sheetState = recitationStatusSheetState,
        sheetBackgroundColor = MaterialTheme.colors.surface,
        sheetElevation = 4.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContent = {
            RecitationStatusSheet(
                isListening = recitationEnabled,
                status = recitationStatus ?: strings.recitationStoppedLabel,
                ayahLabel = recitationMatch?.let { "${strings.ayahsLabel} ${it.ayahNumberInSurah}" },
                transcript = recitationTranscript,
                match = recitationMatch,
                progressiveRevealMode = recitationProgressiveRevealMode,
                onToggleListen = { toggleRecitation() },
                onToggleProgressiveReveal = {
                    recitationProgressiveRevealMode = !recitationProgressiveRevealMode
                    recitationRevealedWordIndexes = emptyMap()
                },
                onClose = { scope.launch { recitationStatusSheetState.hide() } }
            )
        }
    ) {
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
            if (mode == SurahMode.RECITE) {
                RecitationMiniBar(
                    label = recitationStatus ?: if (recitationEnabled) {
                        strings.recitationListeningLabel
                    } else {
                        strings.recitationStoppedLabel
                    },
                    ayahLabel = recitationMatch?.let { "${strings.ayahsLabel} ${it.ayahNumberInSurah}" },
                    onStop = if (recitationEnabled) {
                        { toggleRecitation() }
                    } else {
                        null
                    },
                    onOpenDetails = { scope.launch { recitationStatusSheetState.show() } }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (recitationEnabled) {
                        RecitationMiniBar(
                            label = recitationStatus ?: strings.recitationListeningLabel,
                            ayahLabel = recitationMatch?.let { "${strings.ayahsLabel} ${it.ayahNumberInSurah}" },
                            onStop = { toggleRecitation() },
                            onOpenDetails = { scope.launch { recitationStatusSheetState.show() } }
                        )
                    }
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
                                playAyahAt(prevIndex, if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount)
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
                                playAyahAt(nextIndex, if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount)
                            },
                            onPlayPause = {
                                if (isTranslationPause) {
                                    stopPlayback()
                                    AppTelemetry.logEvent(
                                        name = "surah_playback_stopped",
                                        params = mapOf("surah_number" to surahNumber.toString())
                                    )
                                } else if (isPlaying) {
                                    audioPlayer.pause()
                                    isPlaying = false
                                    AppTelemetry.logEvent(
                                        name = "surah_playback_paused",
                                        params = mapOf("surah_number" to surahNumber.toString())
                                    )
                                } else if (canResume) {
                                    audioPlayer.setPlaybackSpeed(playbackSpeed)
                                    audioPlayer.resume()
                                    isPlaying = true
                                    AppTelemetry.logEvent(
                                        name = "surah_playback_resumed",
                                        params = mapOf("surah_number" to surahNumber.toString())
                                    )
                                } else {
                                    AppTelemetry.logEvent(
                                        name = "surah_playback_started",
                                        params = mapOf(
                                            "surah_number" to surahNumber.toString(),
                                            "ayah_number" to activeAyah.number.toString(),
                                            "mode" to playbackMode.name.lowercase()
                                        )
                                    )
                                    playAyahAt(currentAyahIndex, if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount)
                                }
                            }
                        )
                    }
                }
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
                SurahModeSegmented(
                    mode = mode,
                    onModeSelected = { selected ->
                        mode = selected
                        AppTelemetry.logEvent(
                            name = "surah_mode_changed",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "mode" to selected.name.lowercase()
                            )
                        )
                        if (selected != SurahMode.REVIEW) {
                            reviewShowTranslation = false
                            reviewMarkWeak = false
                            reviewWeakOnly = false
                        }
                    }
                )
            }
            if (mode == SurahMode.LEARN) {
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
                    examMode = examMode,
                    showWeakOnly = showWeakOnly,
                    showExamAndWeakToggles = false,
                    onPlayPause = {
                        if (isTranslationPause) {
                            stopPlayback()
                            AppTelemetry.logEvent(
                                name = "surah_playback_stopped",
                                params = mapOf("surah_number" to surahNumber.toString())
                            )
                        } else if (isPlaying) {
                            audioPlayer.pause()
                            isPlaying = false
                            AppTelemetry.logEvent(
                                name = "surah_playback_paused",
                                params = mapOf("surah_number" to surahNumber.toString())
                            )
                        } else if (canResume) {
                            audioPlayer.setPlaybackSpeed(playbackSpeed)
                            audioPlayer.resume()
                            isPlaying = true
                            AppTelemetry.logEvent(
                                name = "surah_playback_resumed",
                                params = mapOf("surah_number" to surahNumber.toString())
                            )
                        } else {
                            if (playbackMode == PlaybackMode.RANGE && resolveLoopRangeIndices() == null) {
                                setLoopStartToCurrent()
                                setLoopEndToCurrent()
                            }
                            val ayahNumber = audioList.getOrNull(currentAyahIndex)?.number
                            AppTelemetry.logEvent(
                                name = "surah_playback_started",
                                params = mapOf(
                                    "surah_number" to surahNumber.toString(),
                                    "ayah_number" to (ayahNumber?.toString() ?: "-1"),
                                    "mode" to playbackMode.name.lowercase()
                                )
                            )
                            playAyahAt(currentAyahIndex, if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount)
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
                            PlaybackMode.RANGE -> PlaybackMode.IMAM
                            PlaybackMode.IMAM -> PlaybackMode.SURAH
                        }
                        AppTelemetry.logEvent(
                            name = "surah_playback_mode_changed",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "mode" to playbackMode.name.lowercase()
                            )
                        )
                    },
                    onSpeedChange = {
                        playbackSpeed = when (playbackSpeed) {
                            0.75f -> 1.0f
                            1.0f -> 1.25f
                            else -> 0.75f
                        }
                        audioPlayer.setPlaybackSpeed(playbackSpeed)
                        AppTelemetry.logEvent(
                            name = "surah_playback_speed_changed",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "speed" to playbackSpeed.toString()
                            )
                        )
                    },
                    onSetLoopStart = { setLoopStartToCurrent() },
                    onSetLoopEnd = { setLoopEndToCurrent() },
                    onClearLoop = { clearLoopRange() },
                    onToggleTranslationMode = {
                        translationMode = !translationMode
                        AppTelemetry.logEvent(
                            name = "surah_translation_mode_toggled",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "enabled" to translationMode.toString()
                            )
                        )
                    },
                    onTranslationDelayChange = {
                        translationDelayMs = when (translationDelayMs) {
                            2000L -> 3500L
                            3500L -> 5000L
                            else -> 2000L
                        }
                        AppTelemetry.logEvent(
                            name = "surah_translation_delay_changed",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "delay_ms" to translationDelayMs.toString()
                            )
                        )
                    },
                    onToggleExamMode = {},
                    onToggleWeakOnly = {
                        showWeakOnly = !showWeakOnly
                        AppTelemetry.logEvent(
                            name = "surah_weak_filter_toggled",
                            params = mapOf(
                                "surah_number" to surahNumber.toString(),
                                "enabled" to showWeakOnly.toString()
                            )
                        )
                    }
                )
            }
            }
            if (mode == SurahMode.REVIEW) {
                item {
                    ReviewHeaderCard(
                        queueSize = modeAyahs.size,
                        weakOnly = reviewWeakOnly,
                        examMode = examMode,
                        showTranslation = reviewShowTranslation,
                        markWeak = reviewMarkWeak,
                        onToggleWeakOnly = {
                            reviewWeakOnly = !reviewWeakOnly
                            AppTelemetry.logEvent(
                                name = "surah_review_weak_only_toggled",
                                params = mapOf(
                                    "surah_number" to surahNumber.toString(),
                                    "enabled" to reviewWeakOnly.toString()
                                )
                            )
                        },
                        onToggleExamMode = {
                            val next = !examMode
                            examMode = next
                            if (!next) revealedAyahNumbers = emptySet()
                            setAppSettings(appSettings.copy(examModeEnabled = next))
                            AppTelemetry.logEvent(
                                name = "surah_exam_mode_toggled",
                                params = mapOf(
                                    "surah_number" to surahNumber.toString(),
                                    "enabled" to next.toString()
                                )
                            )
                        },
                        onToggleTranslation = {
                            reviewShowTranslation = !reviewShowTranslation
                            AppTelemetry.logEvent(
                                name = "surah_review_translation_toggled",
                                params = mapOf(
                                    "surah_number" to surahNumber.toString(),
                                    "enabled" to reviewShowTranslation.toString()
                                )
                            )
                        },
                        onToggleMarkWeak = {
                            reviewMarkWeak = !reviewMarkWeak
                            AppTelemetry.logEvent(
                                name = "surah_review_mark_weak_toggled",
                                params = mapOf(
                                    "surah_number" to surahNumber.toString(),
                                    "enabled" to reviewMarkWeak.toString()
                                )
                            )
                        }
                    )
                }
            }
            if (mode == SurahMode.LEARN && showWeakOnly && visibleAyahs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        elevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = strings.noWeakAyahsLabel,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.mutedText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            if (mode != SurahMode.LEARN && modeAyahs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        elevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (mode == SurahMode.REVIEW) strings.reviewEmptyLabel else strings.recitationNoDataLabel,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.mutedText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            items(modeAyahs, key = { ayahStableId(it.surahNumber, it.numberInSurah) }) { ayah ->
                val status = todoByAyah[ayah.number]?.status
                val translation = translations[ayah.number]
                val note = notes[ayah.number]?.note
                val review = reviews[ayah.number]
                val reviewDue = mode == SurahMode.REVIEW && review != null && review.nextReviewAt <= getTimeMillis()
                val isActive = audioList.getOrNull(currentAyahIndex)?.number == ayah.number
                val isTranslationFocus = translationFocusAyahNumber == ayah.number
                val weakKey = "${ayah.surahNumber}:${ayah.number}"
                val isWeak = weakAyahKeys.contains(weakKey)
                val isRevealed = revealedAyahNumbers.contains(ayah.number)
                AyahCard(
                    ayah = ayah,
                    cardStyle = ayahCardStyle,
                    baseAyahFontSize = appSettings.readingFontSize,
                    focusMode = mode == SurahMode.REVIEW,
                    status = status,
                    statusLabel = when (status) {
                        AyahTodoStatus.LEARNED -> strings.learnedLabel
                        AyahTodoStatus.LEARNING -> strings.learningLabel
                        null -> null
                    },
                    clearLabel = strings.clearLabel,
                    translation = if (mode == SurahMode.REVIEW && !reviewShowTranslation) null else translation,
                    note = note,
                    reviewDue = reviewDue,
                    isActive = isActive,
                    isTranslationFocus = isTranslationFocus,
                    isExamMode = mode != SurahMode.LEARN && examMode,
                    isRevealed = isRevealed,
                    isWeak = isWeak,
                    voiceCheckActive = voiceCheckAyahNumber == ayah.number,
                    abCompareActive = abCompareAyahNumber == ayah.number,
                    recitationHighlighted = recitationHighlightAyahNumber == ayah.number,
                    recitationPassLevel = recitationAyahLevels[ayah.number],
                    progressiveRevealMode = mode == SurahMode.RECITE && recitationProgressiveRevealMode,
                    progressiveRevealWordIndexes = if (mode == SurahMode.RECITE && recitationProgressiveRevealMode) {
                        recitationRevealedWordIndexes[ayah.number].orEmpty()
                    } else {
                        emptySet()
                    },
                    recitationHighlightWordIndexes = if (recitationHighlightAyahNumber == ayah.number) {
                        recitationHighlightWordIndexes
                    } else {
                        emptySet()
                    },
                    playbackProgress = if (isActive && durationMs > 0) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onPlayFromHere = {
                        val index = audioList.indexOfFirst { it.number == ayah.number }
                        if (index >= 0) {
                            canResume = false
                            playAyahAt(index, if (playbackMode == PlaybackMode.IMAM) 1 else repeatCount)
                        }
                    },
                    onNoteClick = {
                        noteTarget = ayah
                        noteDraft = note ?: ""
                    },
                    onToggleReveal = if (mode != SurahMode.LEARN && examMode) {
                        {
                            revealedAyahNumbers = if (isRevealed) {
                                revealedAyahNumbers - ayah.number
                            } else {
                                revealedAyahNumbers + ayah.number
                            }
                        }
                    } else {
                        null
                    },
                    onReviewQuality = if (reviewDue) {
                        { quality ->
                            viewModel.completeReview(ayah.number, surahNumber, quality)
                            if (reviewMarkWeak) {
                                val updated = weakAyahKeys + weakKey
                                weakAyahKeys = updated
                                UserSettingsStorage.saveWeakAyahKeys(updated)
                            }
                            refreshWeakAyahs()
                        }
                    } else {
                        null
                    },
                    onVoiceCheckToggle = {
                        voiceCheckAyahNumber = if (voiceCheckAyahNumber == ayah.number) null else ayah.number
                    },
                    onVoiceRated = { quality ->
                        viewModel.completeReview(ayah.number, surahNumber, quality)
                        refreshWeakAyahs()
                        val nextLevel = quality.toRecitationPassLevel()
                        val currentLevel = recitationAyahLevels[ayah.number]
                        if (currentLevel == null || nextLevel.priority > currentLevel.priority) {
                            recitationAyahLevels = recitationAyahLevels + (ayah.number to nextLevel)
                        }
                        voiceCheckAyahNumber = null
                    },
                    onABToggle = {
                        abCompareAyahNumber = if (abCompareAyahNumber == ayah.number) null else ayah.number
                    },
                    onABPlayA = {
                        val index = audioList.indexOfFirst { it.number == ayah.number }
                        if (index >= 0) {
                            playFromIndex(index, 0.8f)
                        }
                    },
                    onABPlayB = {
                        val index = audioList.indexOfFirst { it.number == ayah.number }
                        if (index >= 0) {
                            playFromIndex(index, 1.2f)
                        }
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

private enum class SurahMode {
    LEARN,
    REVIEW,
    RECITE,
}

@Composable
private fun SurahModeSegmented(
    mode: SurahMode,
    onModeSelected: (SurahMode) -> Unit,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SurahMode.values().forEach { item ->
            val selected = item == mode
            val label = when (item) {
                SurahMode.LEARN -> strings.surahModeLearnLabel
                SurahMode.REVIEW -> strings.surahModeReviewLabel
                SurahMode.RECITE -> strings.surahModeReciteLabel
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) {
                    MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.16f)
                } else {
                    MaterialTheme.colors.softSurface
                },
                modifier = Modifier.clickable { onModeSelected(item) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption,
                    color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewHeaderCard(
    queueSize: Int,
    weakOnly: Boolean,
    examMode: Boolean,
    showTranslation: Boolean,
    markWeak: Boolean,
    onToggleWeakOnly: () -> Unit,
    onToggleExamMode: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleMarkWeak: () -> Unit,
) {
    val strings = LocalAppStrings.current
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${strings.reviewSessionTitle} • $queueSize",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineChip(
                    label = if (weakOnly) strings.weakOnlyOnLabel else strings.weakOnlyOffLabel,
                    tint = MaterialTheme.colors.primary,
                    onClick = onToggleWeakOnly
                )
                InlineChip(
                    label = if (examMode) strings.examModeOnLabel else strings.examModeOffLabel,
                    tint = MaterialTheme.colors.secondary,
                    onClick = onToggleExamMode
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineChip(
                    label = strings.reviewRevealTranslationLabel,
                    tint = if (showTranslation) MaterialTheme.colors.primary else MaterialTheme.colors.mutedText,
                    onClick = onToggleTranslation
                )
                InlineChip(
                    label = strings.reviewMarkWeakLabel,
                    tint = if (markWeak) MaterialTheme.colors.error else MaterialTheme.colors.mutedText,
                    onClick = onToggleMarkWeak
                )
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
            color = MaterialTheme.colors.mutedText
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
    examMode: Boolean,
    showWeakOnly: Boolean,
    showExamAndWeakToggles: Boolean = true,
    onPlayPause: () -> Unit,
    onRepeatChange: () -> Unit,
    onPlaybackModeChange: () -> Unit,
    onSpeedChange: () -> Unit,
    onSetLoopStart: () -> Unit,
    onSetLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onToggleTranslationMode: () -> Unit,
    onTranslationDelayChange: () -> Unit,
    onToggleExamMode: () -> Unit,
    onToggleWeakOnly: () -> Unit,
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
                            color = MaterialTheme.colors.mutedText
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
                        color = MaterialTheme.colors.mutedText
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

            if (showExamAndWeakToggles) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (examMode) LocalAppStrings.current.examModeOnLabel else LocalAppStrings.current.examModeOffLabel,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onToggleExamMode() }
                    )
                    Text(
                        text = if (showWeakOnly) LocalAppStrings.current.weakOnlyOnLabel else LocalAppStrings.current.weakOnlyOffLabel,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.clickable { onToggleWeakOnly() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReciteModePanel(
    strings: tj.app.quran_todo.common.i18n.AppStrings,
    isListening: Boolean,
    status: String?,
    transcript: String,
    match: RecitationMatch?,
    examMode: Boolean,
    onToggleExamMode: () -> Unit,
    onToggleListen: () -> Unit,
    onPermissionAction: () -> Unit,
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.surahModeReciteLabel,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.SemiBold
                )
                InlineChip(
                    label = if (examMode) strings.examModeOnLabel else strings.examModeOffLabel,
                    tint = MaterialTheme.colors.primary,
                    onClick = onToggleExamMode
                )
            }

            Button(
                onClick = onToggleListen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isListening) strings.stopMicLabel else strings.startMicLabel)
            }

            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
            }

            val needsMicPermission = status == strings.recitationMicPermissionLabel
            val needsTroubleshoot = status == strings.recitationUnavailableLabel
            if (needsMicPermission || needsTroubleshoot) {
                Surface(
                    color = MaterialTheme.colors.softSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (needsMicPermission) {
                                strings.permissionMicReason
                            } else {
                                strings.recitationUnavailableLabel
                            },
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (needsMicPermission) {
                                strings.permissionAllowMicLabel
                            } else {
                                strings.permissionTroubleshootLabel
                            },
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.clickable { onPermissionAction() }
                        )
                    }
                }
            }

            if (match != null) {
                Text(
                    text = "${strings.reciteMostLikelyAyahLabel}: ${match.ayahNumberInSurah} • ${(match.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.primary
                )
                if (match.issues.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        match.issues.take(4).forEach { issue ->
                            val issueLabel = when (issue.type) {
                                RecitationIssueType.MISSING -> strings.reciteIssuesMissingLabel
                                RecitationIssueType.EXTRA -> strings.reciteIssuesExtraLabel
                                RecitationIssueType.REPLACED -> strings.reciteIssuesReplacedLabel
                            }
                            Text(
                                text = "$issueLabel: ${issue.expected ?: issue.actual ?: "?"}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.error
                            )
                        }
                    }
                }
            }

            if (transcript.isNotBlank()) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun RecitationCoachCard(
    isListening: Boolean,
    status: String?,
    transcript: String,
    match: RecitationMatch?,
    progressiveRevealMode: Boolean,
    onToggle: () -> Unit,
    onToggleProgressiveReveal: () -> Unit,
) {
    val strings = LocalAppStrings.current
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recitationCoachTitle,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = if (isListening) {
                        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.error, 0.22f)
                    } else {
                        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.18f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { onToggle() }
                ) {
                    Text(
                        text = if (isListening) strings.stopMicLabel else strings.startMicLabel,
                        style = MaterialTheme.typography.caption,
                        color = if (isListening) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
            }

            InlineChip(
                label = "${strings.revealLabel}: ${if (progressiveRevealMode) strings.enabledLabel else strings.disabledLabel}",
                tint = if (progressiveRevealMode) MaterialTheme.colors.primary else MaterialTheme.colors.mutedText,
                onClick = onToggleProgressiveReveal
            )

            if (match != null) {
                val confidencePercent = (match.confidence * 100).toInt()
                Text(
                    text = "${strings.ayahsLabel} ${match.ayahNumberInSurah} • $confidencePercent%",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                if (match.issues.isNotEmpty()) {
                    match.issues.take(3).forEach { issue ->
                        Text(
                            text = describeIssueLocalized(issue, strings),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }

            if (transcript.isNotBlank()) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun RecitationMiniBar(
    label: String,
    ayahLabel: String?,
    onStop: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    Card(
        elevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .let { base ->
                    if (onOpenDetails != null) {
                        base.clickable { onOpenDetails() }
                    } else {
                        base
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface
                )
                if (!ayahLabel.isNullOrBlank()) {
                    Text(
                        text = ayahLabel,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
            if (onStop != null) {
                Text(
                    text = strings.stopMicLabel,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.clickable { onStop() }
                )
            } else if (onOpenDetails != null) {
                Text(
                    text = strings.statusLabel,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Composable
private fun RecitationStatusSheet(
    isListening: Boolean,
    status: String,
    ayahLabel: String?,
    transcript: String,
    match: RecitationMatch?,
    progressiveRevealMode: Boolean,
    onToggleListen: () -> Unit,
    onToggleProgressiveReveal: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.recitationCoachTitle,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }

        Text(
            text = "${strings.statusLabel}: $status",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.mutedText
        )

        if (!ayahLabel.isNullOrBlank()) {
            Text(
                text = ayahLabel,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary
            )
        }

        Surface(
            color = if (isListening) {
                MaterialTheme.colors.tintedSurface(MaterialTheme.colors.error, 0.22f)
            } else {
                MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.2f)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable { onToggleListen() }
        ) {
            Text(
                text = if (isListening) strings.stopMicLabel else strings.startMicLabel,
                style = MaterialTheme.typography.body2,
                color = if (isListening) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        InlineChip(
            label = "${strings.revealLabel}: ${if (progressiveRevealMode) strings.enabledLabel else strings.disabledLabel}",
            tint = if (progressiveRevealMode) MaterialTheme.colors.primary else MaterialTheme.colors.mutedText,
            onClick = onToggleProgressiveReveal
        )

        if (match != null) {
            Text(
                text = "${strings.reciteMostLikelyAyahLabel}: ${match.ayahNumberInSurah} • ${(match.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary
            )
            if (match.issues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    match.issues.take(4).forEach { issue ->
                        Text(
                            text = describeIssueLocalized(issue, strings),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }

        if (transcript.isNotBlank()) {
            Text(
                text = transcript,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AyahCard(
    ayah: AyahEntity,
    cardStyle: AyahCardStyle,
    baseAyahFontSize: Int,
    focusMode: Boolean,
    status: AyahTodoStatus?,
    statusLabel: String?,
    clearLabel: String,
    translation: String?,
    note: String?,
    reviewDue: Boolean,
    isActive: Boolean,
    isTranslationFocus: Boolean,
    isExamMode: Boolean,
    isRevealed: Boolean,
    isWeak: Boolean,
    voiceCheckActive: Boolean,
    abCompareActive: Boolean,
    recitationHighlighted: Boolean,
    recitationPassLevel: RecitationPassLevel?,
    progressiveRevealMode: Boolean,
    progressiveRevealWordIndexes: Set<Int>,
    recitationHighlightWordIndexes: Set<Int>,
    playbackProgress: Float,
    onPlayFromHere: () -> Unit,
    onNoteClick: () -> Unit,
    onToggleReveal: (() -> Unit)?,
    onReviewQuality: ((ReviewQuality) -> Unit)?,
    onVoiceCheckToggle: () -> Unit,
    onVoiceRated: (ReviewQuality) -> Unit,
    onABToggle: () -> Unit,
    onABPlayA: () -> Unit,
    onABPlayB: () -> Unit,
    onSwipeToLearning: () -> Unit,
    onSwipeToLearned: () -> Unit,
    onClearStatus: (() -> Unit)?,
) {
    val hiddenByExam = isExamMode && !isRevealed && !progressiveRevealMode
    val strings = LocalAppStrings.current
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
    val cardShape = when (cardStyle) {
        AyahCardStyle.CLASSIC -> RoundedCornerShape(16.dp)
        AyahCardStyle.COMPACT -> RoundedCornerShape(12.dp)
        AyahCardStyle.FOCUS -> RoundedCornerShape(20.dp)
    }
    val cardElevation = when (cardStyle) {
        AyahCardStyle.CLASSIC -> 4.dp
        AyahCardStyle.COMPACT -> 1.dp
        AyahCardStyle.FOCUS -> 6.dp
    }
    val cardHorizontalPadding = when (cardStyle) {
        AyahCardStyle.CLASSIC -> 16.dp
        AyahCardStyle.COMPACT -> 12.dp
        AyahCardStyle.FOCUS -> 16.dp
    }
    val cardContentPadding = when (cardStyle) {
        AyahCardStyle.CLASSIC -> 16.dp
        AyahCardStyle.COMPACT -> 12.dp
        AyahCardStyle.FOCUS -> 18.dp
    }
    val resolvedBase = baseAyahFontSize.coerceIn(18, 34)
    val resolvedFont = (if (focusMode) resolvedBase + 2 else resolvedBase).coerceAtMost(36)
    val ayahFontSize = resolvedFont.sp
    val ayahLineHeight = (resolvedFont * if (focusMode) 1.75f else 1.6f).sp
    val numberBadgeSize = when (cardStyle) {
        AyahCardStyle.CLASSIC -> 32.dp
        AyahCardStyle.COMPACT -> 28.dp
        AyahCardStyle.FOCUS -> 36.dp
    }
    val translationMaxLines = when (cardStyle) {
        AyahCardStyle.COMPACT -> 2
        AyahCardStyle.CLASSIC, AyahCardStyle.FOCUS -> Int.MAX_VALUE
    }
    val inactiveCardColor = when (cardStyle) {
        AyahCardStyle.CLASSIC -> MaterialTheme.colors.surface
        AyahCardStyle.COMPACT -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.06f)
        AyahCardStyle.FOCUS -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.secondary, 0.1f)
    }
    val activeCardColor = when (cardStyle) {
        AyahCardStyle.CLASSIC -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, emphasis = 0.14f)
        AyahCardStyle.COMPACT -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, emphasis = 0.2f)
        AyahCardStyle.FOCUS -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, emphasis = 0.24f)
    }

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
            val alpha = if (direction == null) 0f else (0.16f + (0.54f * progress))
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
                    .padding(horizontal = cardHorizontalPadding)
                    .clip(cardShape)
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
                    .padding(horizontal = cardHorizontalPadding)
                    .clickable { onPlayFromHere() },
                shape = cardShape,
                elevation = cardElevation,
                border = if (isWeak) BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.5f)) else null,
                backgroundColor = if (isActive) {
                    activeCardColor
                } else {
                    inactiveCardColor
                }
            ) {
                Column(modifier = Modifier.padding(cardContentPadding)) {
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
                                .size(numberBadgeSize)
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
                            if (progressiveRevealMode) {
                                val progressiveText = buildProgressiveRevealText(
                                    ayahText = ayah.text,
                                    revealedWordIndexes = progressiveRevealWordIndexes,
                                    highlightWordIndexes = recitationHighlightWordIndexes,
                                    highlightBackground = MaterialTheme.colors.tintedSurface(
                                        tint = MaterialTheme.colors.primary,
                                        emphasis = 0.24f
                                    ),
                                    highlightTextColor = MaterialTheme.colors.primary
                                )
                                Text(
                                    text = progressiveText,
                                    fontSize = ayahFontSize,
                                    lineHeight = ayahLineHeight,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = getQuranFontFamily()
                                )
                            } else if (hiddenByExam) {
                                Text(
                                    text = "••••••••••••••••••••",
                                    fontSize = ayahFontSize,
                                    lineHeight = ayahLineHeight,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = getQuranFontFamily()
                                )
                            } else {
                                val highlightedText = if (recitationHighlighted && recitationHighlightWordIndexes.isNotEmpty()) {
                                    buildAyahHighlightedText(
                                        ayahText = ayah.text,
                                        highlightWordIndexes = recitationHighlightWordIndexes,
                                        highlightBackground = MaterialTheme.colors.tintedSurface(
                                            tint = MaterialTheme.colors.primary,
                                            emphasis = 0.24f
                                        ),
                                        highlightTextColor = MaterialTheme.colors.primary
                                    )
                                } else {
                                    null
                                }
                                if (highlightedText != null) {
                                    Text(
                                        text = highlightedText,
                                        fontSize = ayahFontSize,
                                        lineHeight = ayahLineHeight,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontFamily = getQuranFontFamily()
                                    )
                                } else {
                                    Text(
                                        text = ayah.text,
                                        fontSize = ayahFontSize,
                                        lineHeight = ayahLineHeight,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontFamily = getQuranFontFamily()
                                    )
                                }
                            }
                        }
                    }

                    if (isWeak || isExamMode || recitationPassLevel != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isWeak) {
                                InlineChip(
                                    label = strings.weakLabel,
                                    tint = MaterialTheme.colors.error
                                )
                            }
                            if (isExamMode && onToggleReveal != null) {
                                InlineChip(
                                    label = if (isRevealed) strings.hideLabel else strings.revealLabel,
                                    tint = MaterialTheme.colors.primary,
                                    onClick = onToggleReveal
                                )
                            }
                            if (recitationPassLevel != null) {
                                InlineChip(
                                    label = recitationPassLevel.label(strings),
                                    tint = recitationPassLevel.tint(
                                        primary = MaterialTheme.colors.primary,
                                        secondary = MaterialTheme.colors.secondary,
                                        error = MaterialTheme.colors.error
                                    )
                                )
                            }
                        }
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = playbackProgress,
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.progressTrack,
                            color = MaterialTheme.colors.primary
                        )
                    }

                    if (!translation.isNullOrBlank() && !hiddenByExam && !progressiveRevealMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = translation,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface,
                            maxLines = translationMaxLines,
                            overflow = if (translationMaxLines == Int.MAX_VALUE) {
                                TextOverflow.Clip
                            } else {
                                TextOverflow.Ellipsis
                            }
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

                    if (reviewDue && onReviewQuality != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InlineChip(strings.hardLabel, MaterialTheme.colors.error) {
                                onReviewQuality(ReviewQuality.HARD)
                            }
                            InlineChip(strings.goodLabel, MaterialTheme.colors.primary) {
                                onReviewQuality(ReviewQuality.GOOD)
                            }
                            InlineChip(strings.easyLabel, MaterialTheme.colors.secondary) {
                                onReviewQuality(ReviewQuality.EASY)
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
                                tint = MaterialTheme.colors.mutedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (note.isNullOrBlank()) LocalAppStrings.current.notePlaceholder else note,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (voiceCheckActive) "${strings.voiceLabel} ✓" else strings.voiceLabel,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.clickable { onVoiceCheckToggle() }
                            )
                            Text(
                                text = if (abCompareActive) "${strings.abLabel} ✓" else strings.abLabel,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary,
                                modifier = Modifier.clickable { onABToggle() }
                            )
                        }
                    }

                    if (voiceCheckActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InlineChip(strings.hardLabel, MaterialTheme.colors.error) {
                                onVoiceRated(ReviewQuality.HARD)
                            }
                            InlineChip(strings.goodLabel, MaterialTheme.colors.primary) {
                                onVoiceRated(ReviewQuality.GOOD)
                            }
                            InlineChip(strings.easyLabel, MaterialTheme.colors.secondary) {
                                onVoiceRated(ReviewQuality.EASY)
                            }
                        }
                    }

                    if (abCompareActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InlineChip("${strings.abLabel} A 0.8x", MaterialTheme.colors.primary, onClick = onABPlayA)
                            InlineChip("${strings.abLabel} B 1.2x", MaterialTheme.colors.secondary, onClick = onABPlayB)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun InlineChip(
    label: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.tintedSurface(tint, 0.16f),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun buildAyahHighlightedText(
    ayahText: String,
    highlightWordIndexes: Set<Int>,
    highlightBackground: Color,
    highlightTextColor: Color,
): AnnotatedString {
    val indexSet = highlightWordIndexes.filter { it >= 0 }.toSet()
    if (indexSet.isEmpty()) return AnnotatedString(ayahText)

    val words = ayahText.split(" ")
    return buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val shouldHighlight = indexSet.contains(index)
            if (shouldHighlight) {
                pushStyle(
                    SpanStyle(
                        background = highlightBackground,
                        color = highlightTextColor
                    )
                )
            }
            append(word)
            if (shouldHighlight) {
                pop()
            }
            if (index < words.lastIndex) append(" ")
        }
    }
}

private fun buildProgressiveRevealText(
    ayahText: String,
    revealedWordIndexes: Set<Int>,
    highlightWordIndexes: Set<Int>,
    highlightBackground: Color,
    highlightTextColor: Color,
): AnnotatedString {
    val revealedSet = revealedWordIndexes.filter { it >= 0 }.toSet()
    val highlightSet = highlightWordIndexes.filter { it >= 0 }.toSet()
    val words = ayahText.split(" ")
    if (words.isEmpty()) return AnnotatedString(ayahText)

    return buildAnnotatedString {
        words.forEachIndexed { index, rawWord ->
            val word = rawWord.trim()
            val revealed = revealedSet.contains(index)
            val displayWord = if (revealed) {
                rawWord
            } else {
                maskedWord(word)
            }
            val shouldHighlight = revealed && highlightSet.contains(index)
            if (shouldHighlight) {
                pushStyle(
                    SpanStyle(
                        background = highlightBackground,
                        color = highlightTextColor
                    )
                )
            }
            append(displayWord)
            if (shouldHighlight) {
                pop()
            }
            if (index < words.lastIndex) append(" ")
        }
    }
}

private fun maskedWord(word: String): String {
    if (word.isBlank()) return "•••"
    return "•".repeat(word.length.coerceIn(3, 12))
}

private fun allWordIndexes(ayahText: String): Set<Int> {
    val words = ayahText.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
    return words.indices.toSet()
}

private fun recitationFocusedAyahs(
    allAyahs: List<AyahEntity>,
    anchorIndex: Int,
    radius: Int = 9,
): List<AyahEntity> {
    if (allAyahs.isEmpty()) return emptyList()
    val safeAnchor = anchorIndex.coerceIn(0, allAyahs.lastIndex)
    val start = (safeAnchor - radius).coerceAtLeast(0)
    val endExclusive = (safeAnchor + radius + 1).coerceAtMost(allAyahs.size)
    return allAyahs.subList(start, endExclusive)
}

private fun resolveSequentialMatchIndex(
    currentCursor: Int,
    matchIndex: Int?,
    matchConfidence: Float,
    lastIndex: Int,
): Int? {
    if (lastIndex < 0 || matchIndex == null) return null
    val safeCursor = currentCursor.coerceIn(0, lastIndex)
    if (matchIndex < safeCursor || matchIndex > lastIndex) return null

    val strictForwardLimit = (safeCursor + 2).coerceAtMost(lastIndex)
    if (matchIndex <= strictForwardLimit) return matchIndex

    val relaxedForwardLimit = (safeCursor + 5).coerceAtMost(lastIndex)
    return if (matchConfidence >= 0.78f && matchIndex <= relaxedForwardLimit) {
        matchIndex
    } else {
        null
    }
}

private fun localizeRecitationError(
    errorCode: String,
    strings: tj.app.quran_todo.common.i18n.AppStrings,
): String {
    return when (errorCode) {
        RecitationErrorCode.MICROPHONE_PERMISSION -> strings.recitationMicPermissionLabel
        RecitationErrorCode.SPEECH_PERMISSION -> strings.recitationSpeechPermissionLabel
        RecitationErrorCode.RECOGNIZER_UNAVAILABLE -> strings.recitationUnavailableLabel
        RecitationErrorCode.AUDIO_SESSION_FAILED,
        RecitationErrorCode.CAPTURE_START_FAILED,
        RecitationErrorCode.NETWORK,
        RecitationErrorCode.TIMEOUT,
        RecitationErrorCode.SERVER,
        RecitationErrorCode.GENERIC -> strings.recitationGenericErrorLabel
        else -> strings.recitationGenericErrorLabel
    }
}

private data class RecitationAdvanceResult(
    val index: Int,
    val passLevel: RecitationPassLevel,
)

private data class AyahCompletionResult(
    val passLevel: RecitationPassLevel,
    val consumedTokens: Int,
) {
    val shouldAdvance: Boolean get() = passLevel != RecitationPassLevel.HARD && consumedTokens > 0
}

private fun appendRecitationTokens(
    existingTokens: List<String>,
    transcript: String,
): List<String> {
    val normalized = AyahRecitationMatcher.normalizeArabic(transcript)
    if (normalized.isBlank()) return existingTokens
    val incoming = normalized.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
    if (incoming.isEmpty()) return existingTokens
    if (existingTokens.isEmpty()) return incoming.takeLast(180)

    var overlap = minOf(existingTokens.size, incoming.size)
    while (overlap > 0) {
        if (existingTokens.takeLast(overlap) == incoming.take(overlap)) break
        overlap -= 1
    }
    return (existingTokens + incoming.drop(overlap)).takeLast(180)
}

private fun evaluateAyahCompletion(
    ayah: AyahEntity,
    transcriptTokens: List<String>,
): AyahCompletionResult {
    if (transcriptTokens.isEmpty()) {
        return AyahCompletionResult(passLevel = RecitationPassLevel.HARD, consumedTokens = 0)
    }
    val ayahTokens = AyahRecitationMatcher.normalizeArabic(ayah.text)
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (ayahTokens.isEmpty()) {
        return AyahCompletionResult(passLevel = RecitationPassLevel.HARD, consumedTokens = 0)
    }

    val maxWindow = minOf(
        transcriptTokens.size,
        (ayahTokens.size * 3).coerceAtLeast(12)
    )
    var ayahCursor = 0
    var bestMatched = 0
    var bestConsumed = 0
    for (tokenIndex in 0 until maxWindow) {
        if (ayahCursor >= ayahTokens.size) break
        val source = transcriptTokens[tokenIndex]
        val target = ayahTokens[ayahCursor]
        val similarity = tokenSimilarity(source, target)
        if (similarity >= 0.66f) {
            ayahCursor += 1
        }
        if (ayahCursor > bestMatched) {
            bestMatched = ayahCursor
            bestConsumed = tokenIndex + 1
        }
    }

    if (bestMatched <= 0 || bestConsumed <= 0) {
        return AyahCompletionResult(passLevel = RecitationPassLevel.HARD, consumedTokens = 0)
    }

    val score = bestMatched.toFloat() / ayahTokens.size.toFloat()
    val passLevel = when {
        score >= 0.88f && bestMatched >= ayahTokens.size - 1 -> RecitationPassLevel.SUCCESS
        score >= 0.62f && bestMatched >= minOf(ayahTokens.size, 3) -> RecitationPassLevel.GOOD
        else -> RecitationPassLevel.HARD
    }
    val consumed = bestConsumed.coerceAtMost((ayahTokens.size * 2 + 6).coerceAtLeast(bestMatched))
    return AyahCompletionResult(
        passLevel = passLevel,
        consumedTokens = if (passLevel == RecitationPassLevel.HARD) 0 else consumed
    )
}

private fun tokenSimilarity(left: String, right: String): Float {
    if (left == right) return 1f
    if (left.isBlank() || right.isBlank()) return 0f
    val maxLength = maxOf(left.length, right.length)
    if (maxLength == 0) return 1f
    val distance = levenshteinChars(left, right)
    return (1f - distance.toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
}

private fun levenshteinChars(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    val previous = IntArray(right.length + 1) { it }
    val current = IntArray(right.length + 1)
    for (i in left.indices) {
        current[0] = i + 1
        for (j in right.indices) {
            val cost = if (left[i] == right[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }
        for (j in previous.indices) {
            previous[j] = current[j]
        }
    }
    return previous[right.length]
}

private enum class RecitationPassLevel(
    val priority: Int,
    val reviewQuality: ReviewQuality,
) {
    HARD(priority = 0, reviewQuality = ReviewQuality.HARD),
    GOOD(priority = 1, reviewQuality = ReviewQuality.GOOD),
    SUCCESS(priority = 2, reviewQuality = ReviewQuality.EASY);

    fun label(strings: tj.app.quran_todo.common.i18n.AppStrings): String = when (this) {
        HARD -> strings.hardLabel
        GOOD -> strings.goodLabel
        SUCCESS -> strings.easyLabel
    }

    fun tint(primary: Color, secondary: Color, error: Color): Color = when (this) {
        HARD -> error
        GOOD -> primary
        SUCCESS -> secondary
    }
}

private fun recitationPassLevelFor(match: RecitationMatch): RecitationPassLevel {
    val issueCount = match.issues.size
    val confidence = match.confidence
    return when {
        confidence >= 0.82f && issueCount <= 1 -> RecitationPassLevel.SUCCESS
        confidence >= 0.58f && issueCount <= 3 -> RecitationPassLevel.GOOD
        else -> RecitationPassLevel.HARD
    }
}

private fun ReviewQuality.toRecitationPassLevel(): RecitationPassLevel = when (this) {
    ReviewQuality.HARD -> RecitationPassLevel.HARD
    ReviewQuality.GOOD -> RecitationPassLevel.GOOD
    ReviewQuality.EASY -> RecitationPassLevel.SUCCESS
}

private data class RecitationSessionDraft(
    val attempts: Int = 0,
    val matched: Int = 0,
    val confidenceSum: Float = 0f,
    val issueCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
) {
    fun registerFinalAttempt(match: RecitationMatch?): RecitationSessionDraft {
        return if (match == null) {
            copy(
                attempts = attempts + 1,
                currentStreak = 0
            )
        } else {
            val nextStreak = currentStreak + 1
            copy(
                attempts = attempts + 1,
                matched = matched + 1,
                confidenceSum = confidenceSum + match.confidence,
                issueCount = issueCount + match.issues.size,
                currentStreak = nextStreak,
                bestStreak = maxOf(bestStreak, nextStreak)
            )
        }
    }
}

private fun describeIssueLocalized(
    issue: RecitationIssue,
    strings: tj.app.quran_todo.common.i18n.AppStrings,
): String {
    return when (issue.type) {
        RecitationIssueType.MISSING -> {
            val token = issue.expected ?: "?"
            "${strings.recitationAyahIssueLabel}: -$token"
        }
        RecitationIssueType.EXTRA -> {
            val token = issue.actual ?: "?"
            "${strings.recitationAyahIssueLabel}: +$token"
        }
        RecitationIssueType.REPLACED -> {
            val expected = issue.expected ?: "?"
            val actual = issue.actual ?: "?"
            "${strings.recitationAyahIssueLabel}: $expected -> $actual"
        }
    }
}

private enum class PlaybackMode(val storageValue: String) {
    SURAH("surah"),
    AYAH("ayah"),
    RANGE("range"),
    IMAM("imam");

    fun label(strings: tj.app.quran_todo.common.i18n.AppStrings): String = when (this) {
        SURAH -> strings.playbackModeSurah
        AYAH -> strings.playbackModeAyah
        RANGE -> strings.playbackModeRange
        IMAM -> strings.playbackModeImam
    }

    companion object {
        fun fromStorage(value: String?): PlaybackMode = when (value) {
            SURAH.storageValue -> SURAH
            AYAH.storageValue -> AYAH
            RANGE.storageValue -> RANGE
            IMAM.storageValue -> IMAM
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
                backgroundColor = MaterialTheme.colors.progressTrack,
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
                    color = MaterialTheme.colors.mutedText
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
