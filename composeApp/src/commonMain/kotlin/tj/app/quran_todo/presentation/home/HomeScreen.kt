package tj.app.quran_todo.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import kotlin.math.ceil
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.AppStrings
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.theme.faintText
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.progressTrack
import tj.app.quran_todo.common.theme.softSurface
import tj.app.quran_todo.common.theme.softSurfaceStrong
import tj.app.quran_todo.common.theme.tintedSurface
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.daysBetween
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.surah.SurahScreen

@OptIn(ExperimentalFoundationApi::class, KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current

    val uiState by viewModel.uiState.collectAsState()
    val todoByNumber = uiState.todoSurahs.associateBy { it.surahNumber }
    val selectionMode = uiState.selectedSurahNumbers.isNotEmpty()
    var showMistakesOnly by remember { mutableStateOf(false) }
    var showReviewSession by remember { mutableStateOf(false) }

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDueReviewsNow()
    }

    LaunchedEffect(uiState.restoredSettings) {
        val restored = uiState.restoredSettings ?: return@LaunchedEffect
        setSettings(mergeSettings(settings, restored))
        viewModel.consumeRestoredSettings()
    }

    val englishNameByNumber = remember(uiState.completeQuran) {
        uiState.completeQuran.associate { it.surah.number to it.surah.englishNameTranslation }
    }
    val arabicNameByNumber = remember(uiState.completeQuran) {
        uiState.completeQuran.associate { it.surah.number to it.surah.name }
    }
    val transliterationByNumber = remember(uiState.completeQuran) {
        uiState.completeQuran.associate { it.surah.number to it.surah.englishName }
    }
    val ayahTextByNumber = remember(uiState.completeQuran) {
        uiState.completeQuran
            .flatMap { it.ayahs }
            .associate { it.number to it.text }
    }

    val weakSurahNumbers = remember(uiState.weakAyahKeys) {
        uiState.weakAyahKeys.mapNotNull { key ->
            key.substringBefore(":").toIntOrNull()
        }.toSet()
    }
    val filteredByStatus = if (uiState.filter == null) {
        uiState.surahList
    } else {
        uiState.surahList.filter { element ->
            todoByNumber[element.surahNumber]?.status == uiState.filter
        }
    }
    val filteredSurahs = if (showMistakesOnly) {
        filteredByStatus.filter { weakSurahNumbers.contains(it.surahNumber) }
    } else {
        filteredByStatus
    }

    val learnedCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNED }
    val learningCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNING }
    val learnedAyahs = uiState.ayahTodos.count { it.status == AyahTodoStatus.LEARNED }

    val focusSurahNumber = uiState.todoSurahs
        .firstOrNull { it.status == SurahTodoStatus.LEARNING }
        ?.surahNumber
    val today = remember { currentLocalDate() }
    val todayProgress = uiState.ayahTodos.count {
        it.updatedAt > 0 && localDateFromEpoch(it.updatedAt) == today
    }
    val lastActivityDays = uiState.lastActivityAt?.let {
        daysBetween(localDateFromEpoch(it), today)
    }
    val showReminder = settings.remindersEnabled &&
        lastActivityDays != null &&
        lastActivityDays >= 2
    var showFocus by remember { mutableStateOf(false) }
    val todayEpochDay = remember(today) { today.toEpochDays() }
    val daysLeft = (settings.targetEpochDay - todayEpochDay).coerceAtLeast(1)
    val remainingAyahs = (settings.targetAyahs - learnedAyahs).coerceAtLeast(0)
    val pace = if (remainingAyahs == 0) 0 else ceil(remainingAyahs.toDouble() / daysLeft).toInt()
    val smartPlan = remember(
        settings.focusMinutes,
        settings.dailyGoal,
        uiState.dueReviews.size,
        pace
    ) {
        recommendDailyPlan(
            focusMinutes = settings.focusMinutes,
            dueReviews = uiState.dueReviews.size,
            paceAyahs = pace,
            dailyGoal = settings.dailyGoal
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.homeSubtitle,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.mutedText
                        )
                    }

                    if (selectionMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SelectionBar(
                            selectedCount = uiState.selectedSurahNumbers.size,
                            onLearned = { viewModel.markSelected(SurahTodoStatus.LEARNED) },
                            onLearning = { viewModel.markSelected(SurahTodoStatus.LEARNING) },
                            onClear = { viewModel.clearSelection() }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatsRow(
                            learned = learnedCount,
                            learning = learningCount,
                            total = uiState.surahList.size
                        )
                    }
                }
            }

            if (showReminder) {
                item {
                    ReminderCard(
                        title = strings.reminderTitle,
                        body = strings.reminderBody
                    )
                }
            }

            item {
                PlanCard(
                    title = strings.planTitle,
                    subtitle = strings.planSubtitle,
                    todayLabel = strings.todayProgressLabel,
                    todayProgress = todayProgress,
                    dailyGoal = settings.dailyGoal,
                    recommendedNewAyahs = smartPlan.newAyahs,
                    recommendedReviewAyahs = smartPlan.reviewAyahs,
                    focusMinutes = settings.focusMinutes,
                    learnedAyahs = learnedAyahs,
                    targetAyahs = settings.targetAyahs,
                    targetEpochDay = settings.targetEpochDay
                )
            }

            item {
                DueReviewsCard(
                    title = strings.reviewDueTitle,
                    emptyLabel = strings.reviewEmptyLabel,
                    dueReviews = uiState.dueReviews.take(3),
                    dueTotal = uiState.dueReviews.size,
                    openSessionLabel = strings.reviewNowLabel,
                    onOpenSession = { showReviewSession = true },
                    onComplete = { ayahNumber, surahNumber, quality ->
                        viewModel.completeReview(ayahNumber, surahNumber, quality)
                    }
                )
            }

            item {
                FocusCard(
                    title = strings.focusTitle,
                    subtitle = if (focusSurahNumber == null) strings.noLearningYet else strings.focusSubtitle,
                    actionLabel = strings.startFocus,
                    enabled = uiState.completeQuran.isNotEmpty(),
                    onClick = { showFocus = true }
                )
            }

            item {
                OfflinePackageCard(
                    isRunning = uiState.offlineDownloadRunning,
                    done = uiState.offlineDownloadDone,
                    total = uiState.offlineDownloadTotal,
                    status = uiState.offlineDownloadStatus,
                    onStart = { viewModel.startOfflinePackage(language) }
                )
            }

            item {
                CloudSyncCard(
                    status = uiState.syncStatusMessage,
                    providerLabel = uiState.syncProviderLabel,
                    hasSnapshot = uiState.hasCloudSnapshot,
                    lastSyncAt = uiState.lastSyncAt,
                    onSync = { viewModel.syncProgressToCloud(settings) },
                    onRestore = { viewModel.restoreProgressFromCloud() }
                )
            }

            item {
                FilterRow(
                    strings = strings,
                    learnedCount = learnedCount,
                    learningCount = learningCount,
                    filter = uiState.filter,
                    weakCount = weakSurahNumbers.size,
                    showMistakesOnly = showMistakesOnly,
                    onFilterChange = viewModel::setFilter,
                    onToggleMistakes = { showMistakesOnly = !showMistakesOnly }
                )
            }

            itemsIndexed(
                items = filteredSurahs,
                key = { _, surah -> surah.surahNumber }
            ) { index, surah ->
                val isSelected = uiState.selectedSurahNumbers.contains(surah.surahNumber)
                val inTodoSaved = todoByNumber[surah.surahNumber]

                val backgroundColor = when {
                    isSelected -> MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.2f)
                    inTodoSaved?.status == SurahTodoStatus.LEARNED ->
                        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.secondary, 0.26f)
                    inTodoSaved?.status == SurahTodoStatus.LEARNING ->
                        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.secondary, 0.18f)
                    else -> MaterialTheme.colors.surface
                }

                var isItemMenuOpen by remember { mutableStateOf(false) }

                Card(
                    elevation = 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = backgroundColor,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    viewModel.toggleSelection(surah.surahNumber)
                                } else if (uiState.completeQuran.isNotEmpty()) {
                                    viewModel.openSurahDetail(surah.surahNumber)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(surah.surahNumber)
                            }
                        )
                ) {
                    Box {
                        SurahItem(
                            surahNumber = surah.surahNumber,
                            arabicName = uiState.chapterNames[surah.surahNumber]?.arabic
                                ?: arabicNameByNumber[surah.surahNumber]
                                ?: surah.name,
                            transliteration = uiState.chapterNames[surah.surahNumber]?.transliteration
                                ?: transliterationByNumber[surah.surahNumber],
                            translatedName = uiState.chapterNames[surah.surahNumber]?.translated
                                ?: when (language) {
                                    AppLanguage.RU -> surah.name
                                    else -> englishNameByNumber[surah.surahNumber]
                                },
                            ayats = surah.ayats,
                            revelationOrder = surah.revelationOrder,
                            revelationPlace = localizeRevelationPlace(surah.revelationPlace, strings),
                            statusLabel = when (inTodoSaved?.status) {
                                SurahTodoStatus.LEARNED -> strings.learnedLabel
                                SurahTodoStatus.LEARNING -> strings.learningLabel
                                null -> null
                            },
                            statusColor = when (inTodoSaved?.status) {
                                SurahTodoStatus.LEARNED -> MaterialTheme.colors.primary
                                SurahTodoStatus.LEARNING -> MaterialTheme.colors.secondary
                                null -> MaterialTheme.colors.faintText
                            },
                            isSelected = isSelected,
                            strings = strings,
                            onStatusClick = if (inTodoSaved != null) {
                                { isItemMenuOpen = true }
                            } else {
                                null
                            },
                            modifier = Modifier
                        )

                        DropdownMenu(
                            expanded = isItemMenuOpen,
                            onDismissRequest = {
                                isItemMenuOpen = false
                            }
                        ) {
                            when (inTodoSaved?.status) {
                                SurahTodoStatus.LEARNED -> {
                                    DropdownMenuItem(onClick = {
                                        isItemMenuOpen = false
                                        viewModel.setSurahStatus(inTodoSaved.surahNumber, SurahTodoStatus.LEARNING)
                                    }) {
                                        Text(strings.learningLabel, fontWeight = FontWeight.Bold)
                                    }

                                    DropdownMenuItem(onClick = {
                                        isItemMenuOpen = false
                                        viewModel.setSurahStatus(inTodoSaved.surahNumber, null)
                                    }) {
                                        Text(strings.clearLabel, color = MaterialTheme.colors.error)
                                    }
                                }

                                SurahTodoStatus.LEARNING -> {
                                    DropdownMenuItem(onClick = {
                                        isItemMenuOpen = false
                                        viewModel.setSurahStatus(inTodoSaved.surahNumber, SurahTodoStatus.LEARNED)
                                    }) {
                                        Text(strings.learnedLabel, fontWeight = FontWeight.Bold)
                                    }
                                    DropdownMenuItem(onClick = {
                                        isItemMenuOpen = false
                                        viewModel.setSurahStatus(inTodoSaved.surahNumber, null)
                                    }) {
                                        Text(strings.clearLabel, color = MaterialTheme.colors.error)
                                    }
                                }

                                null -> {
                                    DropdownMenuItem(onClick = {
                                        isItemMenuOpen = false
                                    }) {
                                        Text(strings.actionsLabel, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (index == filteredSurahs.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showFocus) {
        FocusSessionDialog(
            title = strings.focusSessionTitle,
            subtitle = strings.focusSessionSubtitle,
            startLabel = strings.focusStartLabel,
            stopLabel = strings.focusStopLabel,
            doneLabel = strings.focusDoneLabel,
            minutes = settings.focusMinutes,
            onDismiss = { showFocus = false },
            onCompleted = { minutes ->
                viewModel.recordFocusSession(minutes)
                showFocus = false
            }
        )
    }

    if (showReviewSession) {
        ReviewSessionDialog(
            title = strings.reviewDueTitle,
            doneLabel = strings.focusDoneLabel,
            emptyLabel = strings.reviewEmptyLabel,
            dueReviews = uiState.dueReviews,
            ayahTextByNumber = ayahTextByNumber,
            onDismiss = { showReviewSession = false },
            onComplete = { ayahNumber, surahNumber, quality ->
                viewModel.completeReview(ayahNumber, surahNumber, quality)
            }
        )
    }

    uiState.selectedSurah?.let { surah ->
        Dialog(
            onDismissRequest = { viewModel.dismissSurahDetail() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SurahScreen(
                surah,
                onDismiss = { viewModel.dismissSurahDetail() }
            )
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    onLearned: () -> Unit,
    onLearning: () -> Unit,
    onClear: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${strings.selectionLabel}: $selectedCount",
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(label = strings.learnedLabel, onClick = onLearned)
            ActionPill(label = strings.learningLabel, onClick = onLearning)
            ActionPill(label = strings.clearLabel, onClick = onClear, isDestructive = true)
        }
    }
}

@Composable
private fun ReminderCard(
    title: String,
    body: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    todayLabel: String,
    todayProgress: Int,
    dailyGoal: Int,
    recommendedNewAyahs: Int,
    recommendedReviewAyahs: Int,
    focusMinutes: Int,
    learnedAyahs: Int,
    targetAyahs: Int,
    targetEpochDay: Int,
) {
    val todayEpochDay = currentLocalDate().toEpochDays()
    val daysLeft = (targetEpochDay - todayEpochDay).coerceAtLeast(1)
    val remainingAyahs = (targetAyahs - learnedAyahs).coerceAtLeast(0)
    val pace = if (remainingAyahs == 0) 0 else ceil(remainingAyahs.toDouble() / daysLeft).toInt()
    val progress = if (dailyGoal > 0) todayProgress.toFloat() / dailyGoal else 0f
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$todayLabel: $todayProgress/$dailyGoal",
                    style = MaterialTheme.typography.body2
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
            ProgressBarLine(progress = progress)
            Text(
                text = "${LocalAppStrings.current.targetPaceLabel}: $pace/${LocalAppStrings.current.perDayShortLabel} • $remainingAyahs ${LocalAppStrings.current.leftLabel} • $daysLeft ${LocalAppStrings.current.daysLabel}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            Text(
                text = "Smart plan: new $recommendedNewAyahs • review $recommendedReviewAyahs • ${focusMinutes}m focus",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
            Text(
                text = "Daily baseline: $dailyGoal",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun ProgressBarLine(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colors.progressTrack,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {}
        Surface(
            color = MaterialTheme.colors.primary,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f))
        ) {}
    }
}

@Composable
private fun DueReviewsCard(
    title: String,
    emptyLabel: String,
    dueReviews: List<AyahReviewEntity>,
    dueTotal: Int,
    openSessionLabel: String,
    onOpenSession: () -> Unit,
    onComplete: (Int, Int, ReviewQuality) -> Unit,
) {
    val strings = LocalAppStrings.current
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            if (dueReviews.isEmpty()) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.mutedText
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Due: $dueTotal",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.mutedText
                    )
                    ReviewChip(label = openSessionLabel, color = MaterialTheme.colors.primary) {
                        onOpenSession()
                    }
                }
                dueReviews.forEach { review ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.surahLabel} ${review.surahNumber} · ${strings.ayahsLabel} ${review.ayahNumber}",
                            style = MaterialTheme.typography.body2
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReviewChip(label = strings.hardLabel, color = MaterialTheme.colors.error) {
                                onComplete(review.ayahNumber, review.surahNumber, ReviewQuality.HARD)
                            }
                            ReviewChip(label = strings.goodLabel, color = MaterialTheme.colors.primary) {
                                onComplete(review.ayahNumber, review.surahNumber, ReviewQuality.GOOD)
                            }
                            ReviewChip(label = strings.easyLabel, color = MaterialTheme.colors.secondary) {
                                onComplete(review.ayahNumber, review.surahNumber, ReviewQuality.EASY)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.tintedSurface(color, 0.16f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OfflinePackageCard(
    isRunning: Boolean,
    done: Int,
    total: Int,
    status: String?,
    onStart: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val statusText = when (status) {
        "DOWNLOADING" -> strings.offlinePackageDownloadingLabel
        "READY" -> strings.offlinePackageReadyLabel
        else -> strings.offlinePackageSubtitle
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 3.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.offlinePackageTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Text(
                text = statusText,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            if (total > 0) {
                Text("$done / $total", style = MaterialTheme.typography.caption)
                ProgressBarLine(progress = done.toFloat() / total.toFloat())
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isRunning) MaterialTheme.colors.softSurfaceStrong else MaterialTheme.colors.primary,
                modifier = Modifier.clickable(enabled = !isRunning) { onStart() }
            ) {
                Text(
                    text = if (isRunning) strings.offlinePackageDownloadingLabel else strings.offlinePackageDownloadLabel,
                    color = if (isRunning) MaterialTheme.colors.faintText else Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun CloudSyncCard(
    status: String?,
    providerLabel: String,
    hasSnapshot: Boolean,
    lastSyncAt: Long?,
    onSync: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 3.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Cloud sync", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Text(
                text = "Provider: $providerLabel",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            Text(
                text = if (hasSnapshot) {
                    "Snapshot ready${lastSyncAt?.let { " (${localDateFromEpoch(it)})" } ?: ""}"
                } else {
                    "No snapshot"
                },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            if (!status.isNullOrBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewChip(label = "Sync now", color = MaterialTheme.colors.primary) { onSync() }
                ReviewChip(label = "Restore", color = MaterialTheme.colors.secondary) { onRestore() }
            }
        }
    }
}

@Composable
private fun ReviewSessionDialog(
    title: String,
    doneLabel: String,
    emptyLabel: String,
    dueReviews: List<AyahReviewEntity>,
    ayahTextByNumber: Map<Int, String>,
    onDismiss: () -> Unit,
    onComplete: (Int, Int, ReviewQuality) -> Unit,
) {
    var queue by remember(dueReviews) { mutableStateOf(dueReviews) }
    val current = queue.firstOrNull()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = 4.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                if (current == null) {
                    Text(
                        text = emptyLabel,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.mutedText
                    )
                    ReviewChip(label = doneLabel, color = MaterialTheme.colors.primary) { onDismiss() }
                } else {
                    Text(
                        text = "Left: ${queue.size}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.mutedText
                    )
                    Text(
                        text = "${LocalAppStrings.current.surahLabel} ${current.surahNumber} · ${LocalAppStrings.current.ayahsLabel} ${current.ayahNumber}",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = ayahTextByNumber[current.ayahNumber] ?: "Ayah text unavailable",
                        style = MaterialTheme.typography.body2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewChip(label = LocalAppStrings.current.hardLabel, color = MaterialTheme.colors.error) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.HARD)
                            queue = queue.drop(1)
                        }
                        ReviewChip(label = LocalAppStrings.current.goodLabel, color = MaterialTheme.colors.primary) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.GOOD)
                            queue = queue.drop(1)
                        }
                        ReviewChip(label = LocalAppStrings.current.easyLabel, color = MaterialTheme.colors.secondary) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.EASY)
                            queue = queue.drop(1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewChip(label = doneLabel, color = MaterialTheme.colors.primary) { onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusSessionDialog(
    title: String,
    subtitle: String,
    startLabel: String,
    stopLabel: String,
    doneLabel: String,
    minutes: Int,
    onDismiss: () -> Unit,
    onCompleted: (Int) -> Unit,
) {
    var isRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(minutes * 60) }

    LaunchedEffect(isRunning, secondsLeft) {
        if (!isRunning) return@LaunchedEffect
        if (secondsLeft <= 0) {
            onCompleted(minutes)
            return@LaunchedEffect
        }
        delay(1000)
        secondsLeft -= 1
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = 4.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colors.primary)
                Text(text = title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
                Text(
                    text = formatTimer(secondsLeft),
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isRunning = !isRunning },
                        enabled = secondsLeft > 0
                    ) {
                        Text(if (isRunning) stopLabel else startLabel)
                    }
                    Button(
                        onClick = { onCompleted(minutes) },
                        enabled = secondsLeft <= 0 || !isRunning
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(doneLabel)
                    }
                }
            }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun StatsRow(learned: Int, learning: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatChip(label = LocalAppStrings.current.learnedLabel, value = learned)
        StatChip(label = LocalAppStrings.current.learningLabel, value = learning)
        StatChip(label = LocalAppStrings.current.totalLabel, value = total)
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colors.softSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.subtitle1)
            Text(text = label, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun FocusCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 3.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = subtitle, style = MaterialTheme.typography.body2)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.softSurfaceStrong,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(enabled = enabled) { onClick() }
            ) {
                Text(
                    text = actionLabel,
                    color = if (enabled) Color.White else MaterialTheme.colors.faintText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    strings: AppStrings,
    learnedCount: Int,
    learningCount: Int,
    weakCount: Int,
    showMistakesOnly: Boolean,
    filter: SurahTodoStatus?,
    onFilterChange: (SurahTodoStatus?) -> Unit,
    onToggleMistakes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            label = strings.filterAll,
            count = null,
            selected = filter == null,
            onClick = { onFilterChange(null) }
        )
        FilterChip(
            label = strings.filterLearned,
            count = learnedCount,
            selected = filter == SurahTodoStatus.LEARNED,
            onClick = { onFilterChange(SurahTodoStatus.LEARNED) }
        )
        FilterChip(
            label = strings.filterLearning,
            count = learningCount,
            selected = filter == SurahTodoStatus.LEARNING,
            onClick = { onFilterChange(SurahTodoStatus.LEARNING) }
        )
        FilterChip(
            label = strings.mistakesFilterLabel,
            count = weakCount,
            selected = showMistakesOnly,
            onClick = onToggleMistakes
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.18f)
    } else {
        MaterialTheme.colors.softSurface
    }
    val textColor = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = textColor, style = MaterialTheme.typography.caption)
            if (count != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = " $count",
                    color = textColor,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val color = if (isDestructive) MaterialTheme.colors.error else MaterialTheme.colors.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colors.tintedSurface(color, 0.16f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.caption
        )
    }
}

@Composable
fun SurahItem(
    modifier: Modifier,
    surahNumber: Int,
    arabicName: String,
    transliteration: String?,
    translatedName: String?,
    ayats: Int,
    revelationPlace: String,
    revelationOrder: Int,
    statusLabel: String?,
    statusColor: Color,
    isSelected: Boolean,
    strings: AppStrings,
    onStatusClick: (() -> Unit)?,
) {
    Column(modifier = Modifier.then(modifier).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${strings.surahLabel} #$surahNumber",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = revelationPlace,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }

        if (statusLabel != null && onStatusClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colors.tintedSurface(statusColor, 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onStatusClick() }
            ) {
                Text(
                    text = "${strings.statusLabel}: $statusLabel",
                    style = MaterialTheme.typography.caption,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else if (isSelected) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.selectionLabel,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = arabicName,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold
        )

        if (!transliteration.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = transliteration,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
        }

        if (!translatedName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = translatedName,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${strings.ayahsLabel}: $ayats",
                style = MaterialTheme.typography.subtitle2
            )
            Text(
                text = "${strings.revelationOrderLabel} #$revelationOrder",
                style = MaterialTheme.typography.subtitle2
            )
        }
    }
}

private data class DailyPlanRecommendation(
    val newAyahs: Int,
    val reviewAyahs: Int,
)

private fun recommendDailyPlan(
    focusMinutes: Int,
    dueReviews: Int,
    paceAyahs: Int,
    dailyGoal: Int,
): DailyPlanRecommendation {
    val totalSeconds = (focusMinutes.coerceAtLeast(5) * 60).coerceAtLeast(300)
    val maxReviewsByTime = (totalSeconds / 25).coerceAtLeast(6)
    val reviewAyahs = dueReviews.coerceAtMost(maxReviewsByTime).coerceAtLeast(0)
    val secondsAfterReview = (totalSeconds - reviewAyahs * 25).coerceAtLeast(0)
    val newByTime = (secondsAfterReview / 180).coerceAtLeast(1)
    val baselineNew = maxOf(1, dailyGoal, paceAyahs)
    val newAyahs = baselineNew.coerceAtMost(newByTime + 2).coerceAtLeast(1)
    return DailyPlanRecommendation(
        newAyahs = newAyahs,
        reviewAyahs = reviewAyahs
    )
}

private fun mergeSettings(
    current: tj.app.quran_todo.common.settings.AppSettings,
    restored: SettingsSnapshot,
): tj.app.quran_todo.common.settings.AppSettings {
    return current.copy(
        dailyGoal = restored.dailyGoal,
        focusMinutes = restored.focusMinutes,
        remindersEnabled = restored.remindersEnabled,
        targetAyahs = restored.targetAyahs,
        targetEpochDay = restored.targetEpochDay,
        examModeEnabled = restored.examModeEnabled
    )
}
