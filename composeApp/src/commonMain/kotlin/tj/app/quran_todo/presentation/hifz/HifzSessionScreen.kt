package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioPlayer
import tj.app.quran_todo.common.audio.ayahAudioCacheKey
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.presentation.designsystem.HifzPanel
import tj.app.quran_todo.presentation.designsystem.HifzPrimaryButton
import tj.app.quran_todo.presentation.designsystem.HifzProgressCapsule
import tj.app.quran_todo.presentation.designsystem.HifzSecondaryButton
import tj.app.quran_todo.presentation.designsystem.HifzToggleRow
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.surah.SurahViewModel

internal enum class HifzSessionMode {
    LEARN,
    REVIEW,
}

internal data class HifzSessionRequest(
    val mode: HifzSessionMode,
    val startAyah: Int? = null,
    val endAyah: Int? = null,
    val ayahNumbers: List<Int>? = null,
)

@OptIn(KoinExperimentalAPI::class)
@Composable
internal fun HifzSessionScreen(
    surahWithAyahs: SurahWithAyahs,
    request: HifzSessionRequest,
    onDismiss: () -> Unit,
    viewModel: SurahViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel(),
) {
    val language = LocalAppLanguage.current
    val settings = LocalAppSettings.current
    val copy = remember(language) { hifzSessionCopy(language) }
    val colors = MaterialTheme.extendedColors
    val surahNumber = surahWithAyahs.surah.number
    val allAyahs = remember(surahWithAyahs) {
        surahWithAyahs.ayahs.sortedBy { it.numberInSurah }
    }
    val startAyah = request.startAyah
        ?.coerceIn(1, allAyahs.size.coerceAtLeast(1))
        ?: 1
    val endAyah = request.endAyah
        ?.coerceIn(startAyah, allAyahs.size.coerceAtLeast(startAyah))
        ?: allAyahs.size
    val ayahs = remember(allAyahs, startAyah, endAyah, request.ayahNumbers) {
        val requestedNumbers = request.ayahNumbers?.toSet()
        val selected = allAyahs.filter {
            requestedNumbers?.let { numbers -> it.number in numbers }
                ?: (it.numberInSurah in startAyah..endAyah)
        }
        if (requestedNumbers == null && selected.isEmpty()) allAyahs else selected
    }
    val translations by viewModel.ayahTranslations.collectAsState()
    val chapterNames by viewModel.chapterNames.collectAsState()
    val todoList by viewModel.ayahTodos(surahNumber).collectAsState(emptyList())
    val todoByAyah = remember(todoList) { todoList.associateBy { it.ayahNumber } }
    val audioCache = remember { AudioCache() }
    val audioPlayer = remember { AudioPlayer() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sessionKey = remember(surahNumber, request) {
        "$surahNumber:$startAyah:$endAyah:${request.mode}"
    }
    var currentIndex by remember(sessionKey) { mutableStateOf(0) }
    var revealed by remember(sessionKey) {
        mutableStateOf(request.mode == HifzSessionMode.LEARN)
    }
    var showTranslation by remember(sessionKey) { mutableStateOf(true) }
    var isPlaying by remember(sessionKey) { mutableStateOf(false) }
    var canResume by remember(sessionKey) { mutableStateOf(false) }
    var pendingAudioAyah by remember(sessionKey) { mutableStateOf<Int?>(null) }
    var isSubmitting by remember(sessionKey) { mutableStateOf(false) }
    var finished by remember(sessionKey) { mutableStateOf(false) }
    var reviewedCount by remember(sessionKey) { mutableStateOf(0) }
    val currentAyah = ayahs.getOrNull(currentIndex)

    LaunchedEffect(surahNumber, language, allAyahs.size) {
        viewModel.loadTranslations(surahNumber, language, allAyahs.size)
        viewModel.loadChapterNames(language)
    }
    LaunchedEffect(currentAyah?.number) {
        audioPlayer.stop()
        isPlaying = false
        canResume = false
        pendingAudioAyah = null
        isSubmitting = false
        scrollState.scrollTo(0)
    }
    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.stop() }
    }

    fun advance() {
        audioPlayer.stop()
        isPlaying = false
        canResume = false
        pendingAudioAyah = null
        if (currentIndex >= ayahs.lastIndex) {
            finished = true
        } else {
            currentIndex += 1
            revealed = request.mode == HifzSessionMode.LEARN
        }
    }

    fun markLearning(markWeak: Boolean) {
        val ayah = currentAyah ?: return
        if (isSubmitting) return
        isSubmitting = true
        viewModel.updateAyahStatus(
            ayahNumber = ayah.number,
            surahNumber = surahNumber,
            totalAyahs = allAyahs.size,
            status = AyahTodoStatus.LEARNING,
            weakState = if (markWeak) true else null
        )
        advance()
    }

    fun markKnown() {
        val ayah = currentAyah ?: return
        if (isSubmitting) return
        isSubmitting = true
        viewModel.updateAyahStatus(
            ayahNumber = ayah.number,
            surahNumber = surahNumber,
            totalAyahs = allAyahs.size,
            status = AyahTodoStatus.LEARNED,
            weakState = false
        )
        advance()
    }

    fun rateReview(quality: ReviewQuality, forgot: Boolean = false) {
        val ayah = currentAyah ?: return
        if (isSubmitting) return
        isSubmitting = true
        homeViewModel.completeReview(
            ayahNumber = ayah.number,
            surahNumber = surahNumber,
            quality = quality
        )
        if (forgot) {
            viewModel.updateAyahStatus(
                ayahNumber = ayah.number,
                surahNumber = surahNumber,
                totalAyahs = allAyahs.size,
                status = AyahTodoStatus.LEARNING,
                scheduleNextReview = false
            )
        }
        reviewedCount += 1
        advance()
    }

    val knownInRange = ayahs.count {
        todoByAyah[it.number]?.status == AyahTodoStatus.LEARNED
    }
    val progress = when {
        ayahs.isEmpty() -> 0f
        finished -> 1f
        else -> (currentIndex + 1).toFloat() / ayahs.size.toFloat()
    }
    val displayedIndex = when {
        ayahs.isEmpty() -> 0
        finished -> ayahs.size
        else -> currentIndex + 1
    }
    val chapterName = chapterNames[surahNumber]?.transliteration
        ?: surahWithAyahs.surah.englishName
    val visibleStartAyah = ayahs.minOfOrNull { it.numberInSurah } ?: startAyah
    val visibleEndAyah = ayahs.maxOfOrNull { it.numberInSurah } ?: endAyah
    val rangeLabel = if (visibleStartAyah == visibleEndAyah) {
        "$chapterName · $visibleStartAyah"
    } else {
        "$chapterName · $visibleStartAyah–$visibleEndAyah"
    }
    val summary = if (request.mode == HifzSessionMode.LEARN) {
        "$knownInRange/${ayahs.size} ${copy.learnedSummary}"
    } else {
        "$reviewedCount/${ayahs.size} ${copy.reviewedSummary}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = colors.surfaceAlt,
                elevation = 0.dp
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = copy.close,
                        tint = MaterialTheme.colors.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (request.mode == HifzSessionMode.LEARN) {
                        copy.learning
                    } else {
                        copy.review
                    },
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = rangeLabel,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$displayedIndex/${ayahs.size.coerceAtLeast(1)}",
                    color = MaterialTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HifzProgressCapsule(
            progress = progress,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (finished || currentAyah == null) {
                SessionFinishedPanel(
                    copy = copy,
                    summary = summary,
                    hasAyahs = ayahs.isNotEmpty(),
                    onDismiss = onDismiss
                )
            } else {
                SessionReadingStage(
                    ayah = currentAyah,
                    translation = translations[currentAyah.number],
                    revealed = revealed,
                    showTranslation = showTranslation,
                    readingFontSize = settings.readingFontSize,
                    copy = copy
                )
                HifzPanel {
                    if (request.mode == HifzSessionMode.LEARN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SessionIconButton(
                                contentDescription = copy.play,
                                onClick = {
                                    if (isPlaying) {
                                        audioPlayer.pause()
                                        isPlaying = false
                                        canResume = true
                                    } else if (canResume) {
                                        audioPlayer.resume()
                                        isPlaying = true
                                    } else if (pendingAudioAyah == null) {
                                        val requestedAyah = currentAyah
                                        pendingAudioAyah = requestedAyah.number
                                        coroutineScope.launch {
                                            val remoteUrl = audioUrl(requestedAyah.number)
                                            val cacheKey = ayahAudioCacheKey(
                                                surahNumber = requestedAyah.surahNumber,
                                                ayahNumberInSurah = requestedAyah.numberInSurah
                                            )
                                            val source = audioCache.getOrFetch(
                                                url = remoteUrl,
                                                cacheKey = cacheKey
                                            ) ?: remoteUrl
                                            if (pendingAudioAyah != requestedAyah.number) {
                                                return@launch
                                            }
                                            pendingAudioAyah = null
                                            isPlaying = true
                                            audioPlayer.play(
                                                url = source,
                                                onComplete = {
                                                    isPlaying = false
                                                    canResume = false
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            SessionIconButton(
                                contentDescription = if (revealed) copy.hide else copy.reveal,
                                onClick = { revealed = !revealed }
                            ) {
                                Icon(
                                    imageVector = if (revealed) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HifzToggleRow(
                                label = copy.translation,
                                checked = showTranslation,
                                onCheckedChange = { showTranslation = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HifzSecondaryButton(
                                label = copy.unsure,
                                onClick = { markLearning(markWeak = false) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting
                            )
                            HifzSecondaryButton(
                                label = copy.mistake,
                                onClick = { markLearning(markWeak = true) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting
                            )
                        }
                        HifzPrimaryButton(
                            label = copy.know,
                            onClick = ::markKnown,
                            enabled = !isSubmitting
                        )
                    } else if (revealed) {
                        Text(
                            text = copy.rateReview,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReviewRatingButton(
                                label = copy.easy,
                                tint = colors.success,
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting,
                                onClick = { rateReview(ReviewQuality.EASY) }
                            )
                            ReviewRatingButton(
                                label = copy.good,
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting,
                                onClick = { rateReview(ReviewQuality.GOOD) }
                            )
                            ReviewRatingButton(
                                label = copy.hard,
                                tint = colors.warning,
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting,
                                onClick = { rateReview(ReviewQuality.HARD) }
                            )
                            ReviewRatingButton(
                                label = copy.forgot,
                                tint = colors.danger,
                                modifier = Modifier.weight(1f),
                                enabled = !isSubmitting,
                                onClick = {
                                    rateReview(ReviewQuality.FORGOT, forgot = true)
                                }
                            )
                        }
                    } else {
                        HifzPrimaryButton(
                            label = copy.revealText,
                            onClick = { revealed = true }
                        )
                    }
                    if (request.mode == HifzSessionMode.REVIEW) {
                        HifzSecondaryButton(
                            label = copy.markMistake,
                            onClick = {
                                rateReview(ReviewQuality.FORGOT, forgot = true)
                            },
                            enabled = !isSubmitting
                        )
                    }
                    Text(
                        text = summary,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionReadingStage(
    ayah: AyahEntity,
    translation: String?,
    revealed: Boolean,
    showTranslation: Boolean,
    readingFontSize: Int,
    copy: HifzSessionCopy,
) {
    val colors = MaterialTheme.extendedColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.readingSurface,
        border = BorderStroke(1.dp, colors.border),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${copy.ayah} ${ayah.numberInSurah}",
                        color = MaterialTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (revealed) copy.visibleHint else copy.hiddenHint,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colors.utilitySurface,
                    elevation = 0.dp
                ) {
                    Text(
                        text = if (revealed) copy.visible else copy.hidden,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = if (revealed) {
                            MaterialTheme.colors.primary
                        } else {
                            colors.textSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (revealed) {
                Text(
                    text = ayah.text.ifBlank { copy.noAyah },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp),
                    color = MaterialTheme.colors.onSurface,
                    fontSize = (readingFontSize + 2).sp,
                    lineHeight = (readingFontSize + 18).sp,
                    fontFamily = getQuranFontFamily(),
                    textAlign = TextAlign.End
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp)
                        .background(
                            color = colors.readingMutedSurface,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = copy.reciteFromMemory,
                        color = MaterialTheme.colors.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = copy.revealAfterRecall,
                        modifier = Modifier.padding(top = 8.dp),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (revealed && showTranslation && !translation.isNullOrBlank()) {
                Text(
                    text = translation,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SessionFinishedPanel(
    copy: HifzSessionCopy,
    summary: String,
    hasAyahs: Boolean,
    onDismiss: () -> Unit,
) {
    HifzPanel {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(42.dp)
        )
        Text(
            text = if (hasAyahs) copy.complete else copy.noAyahsAvailable,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = summary,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.extendedColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        HifzPrimaryButton(label = copy.done, onClick = onDismiss)
    }
}

@Composable
private fun SessionIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription }
            .adaptiveClickable(
                shape = CircleShape,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = MaterialTheme.extendedColors.utilitySurface,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.utilityBorder),
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ReviewRatingButton(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .adaptiveClickable(
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = if (enabled) 0.12f else 0.06f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (enabled) 0.22f else 0.1f)),
        elevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 3.dp),
                color = tint.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun audioUrl(ayahNumber: Int): String =
    "https://cdn.islamic.network/quran/audio/128/ar.alafasy/$ayahNumber.mp3"

private data class HifzSessionCopy(
    val learning: String,
    val review: String,
    val close: String,
    val ayah: String,
    val visibleHint: String,
    val hiddenHint: String,
    val visible: String,
    val hidden: String,
    val noAyah: String,
    val reciteFromMemory: String,
    val revealAfterRecall: String,
    val play: String,
    val reveal: String,
    val hide: String,
    val translation: String,
    val unsure: String,
    val mistake: String,
    val know: String,
    val rateReview: String,
    val easy: String,
    val good: String,
    val hard: String,
    val forgot: String,
    val revealText: String,
    val markMistake: String,
    val learnedSummary: String,
    val reviewedSummary: String,
    val complete: String,
    val noAyahsAvailable: String,
    val done: String,
)

private fun hifzSessionCopy(language: AppLanguage): HifzSessionCopy =
    when (language) {
        AppLanguage.RU -> HifzSessionCopy(
            learning = "Заучивание",
            review = "Повторение",
            close = "Закрыть",
            ayah = "Аят",
            visibleHint = "Прочитайте, скройте, повторите и оцените.",
            hiddenHint = "Повторите по памяти перед открытием текста.",
            visible = "Открыт",
            hidden = "Скрыт",
            noAyah = "Текст аята не загружен",
            reciteFromMemory = "Повторите по памяти",
            revealAfterRecall = "Откройте текст только после попытки.",
            play = "Воспроизвести",
            reveal = "Показать текст",
            hide = "Скрыть текст",
            translation = "Перевод",
            unsure = "Не уверен",
            mistake = "Ошибка",
            know = "Знаю",
            rateReview = "Оцените повторение",
            easy = "Легко",
            good = "Хорошо",
            hard = "Трудно",
            forgot = "Забыл",
            revealText = "Показать текст",
            markMistake = "Отметить ошибку",
            learnedSummary = "выучено",
            reviewedSummary = "повторено",
            complete = "Сессия завершена",
            noAyahsAvailable = "Аяты недоступны",
            done = "Готово"
        )
        AppLanguage.TG -> HifzSessionCopy(
            learning = "Азёдкунӣ",
            review = "Такрор",
            close = "Пӯшидан",
            ayah = "Оят",
            visibleHint = "Бихонед, пӯшонед, такрор кунед ва баҳо диҳед.",
            hiddenHint = "Пеш аз кушодани матн аз ёд такрор кунед.",
            visible = "Кушода",
            hidden = "Пӯшида",
            noAyah = "Матни оят бор нашуд",
            reciteFromMemory = "Аз ёд такрор кунед",
            revealAfterRecall = "Матнро танҳо пас аз кӯшиш кушоед.",
            play = "Пахш кардан",
            reveal = "Матнро нишон додан",
            hide = "Матнро пинҳон кардан",
            translation = "Тарҷума",
            unsure = "Номуайян",
            mistake = "Хато",
            know = "Медонам",
            rateReview = "Такрорро баҳо диҳед",
            easy = "Осон",
            good = "Хуб",
            hard = "Душвор",
            forgot = "Фаромӯш",
            revealText = "Матнро нишон диҳед",
            markMistake = "Хаторо қайд кунед",
            learnedSummary = "азёд шуд",
            reviewedSummary = "такрор шуд",
            complete = "Машқ анҷом ёфт",
            noAyahsAvailable = "Оятҳо дастрас нестанд",
            done = "Тайёр"
        )
        AppLanguage.UZ -> HifzSessionCopy(
            learning = "Yodlash",
            review = "Takror",
            close = "Yopish",
            ayah = "Oyat",
            visibleHint = "O'qing, yashiring, takrorlang va baholang.",
            hiddenHint = "Matnni ochishdan oldin yoddan takrorlang.",
            visible = "Ochiq",
            hidden = "Yashirin",
            noAyah = "Oyat matni yuklanmadi",
            reciteFromMemory = "Yoddan takrorlang",
            revealAfterRecall = "Matnni faqat urinishdan keyin oching.",
            play = "Eshittirish",
            reveal = "Matnni ko'rsatish",
            hide = "Matnni yashirish",
            translation = "Tarjima",
            unsure = "Ishonchim komil emas",
            mistake = "Xato",
            know = "Bilaman",
            rateReview = "Takrorni baholang",
            easy = "Oson",
            good = "Yaxshi",
            hard = "Qiyin",
            forgot = "Unutdim",
            revealText = "Matnni ko'rsatish",
            markMistake = "Xatoni belgilash",
            learnedSummary = "yodlandi",
            reviewedSummary = "takrorlandi",
            complete = "Mashg'ulot tugadi",
            noAyahsAvailable = "Oyatlar mavjud emas",
            done = "Tayyor"
        )
        AppLanguage.TR -> HifzSessionCopy(
            learning = "Ezberleme",
            review = "Tekrar",
            close = "Kapat",
            ayah = "Ayet",
            visibleHint = "Oku, gizle, tekrar et ve değerlendir.",
            hiddenHint = "Metni açmadan önce ezberden oku.",
            visible = "Açık",
            hidden = "Gizli",
            noAyah = "Ayet metni yüklenmedi",
            reciteFromMemory = "Ezberden oku",
            revealAfterRecall = "Metni yalnızca denemeden sonra aç.",
            play = "Oynat",
            reveal = "Metni göster",
            hide = "Metni gizle",
            translation = "Çeviri",
            unsure = "Emin değilim",
            mistake = "Hata",
            know = "Biliyorum",
            rateReview = "Tekrarı değerlendir",
            easy = "Kolay",
            good = "İyi",
            hard = "Zor",
            forgot = "Unuttum",
            revealText = "Metni göster",
            markMistake = "Hatayı işaretle",
            learnedSummary = "ezberlendi",
            reviewedSummary = "tekrarlandı",
            complete = "Oturum tamamlandı",
            noAyahsAvailable = "Ayetler kullanılamıyor",
            done = "Bitti"
        )
        AppLanguage.EN -> HifzSessionCopy(
            learning = "Learning",
            review = "Review",
            close = "Close",
            ayah = "Ayah",
            visibleHint = "Read, hide, recite, then mark recall.",
            hiddenHint = "Recite from memory before revealing.",
            visible = "Visible",
            hidden = "Hidden",
            noAyah = "No ayah loaded",
            reciteFromMemory = "Recite from memory",
            revealAfterRecall = "Reveal only after recall.",
            play = "Play",
            reveal = "Reveal text",
            hide = "Hide text",
            translation = "Translation",
            unsure = "Unsure",
            mistake = "Mistake",
            know = "Know",
            rateReview = "Rate this review",
            easy = "Easy",
            good = "Good",
            hard = "Hard",
            forgot = "Forgot",
            revealText = "Reveal text",
            markMistake = "Mark mistake",
            learnedSummary = "learned",
            reviewedSummary = "reviewed",
            complete = "Session complete",
            noAyahsAvailable = "No ayahs available",
            done = "Done"
        )
    }
