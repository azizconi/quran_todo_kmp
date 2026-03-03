package tj.app.quran_todo.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.AppStrings
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.theme.faintText
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.progressTrack
import tj.app.quran_todo.common.theme.softSurface
import tj.app.quran_todo.common.theme.softSurfaceStrong
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.daysBetween
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.surah.SurahScreen
import kotlin.math.ceil

private enum class SyncIndicatorState {
    UP_TO_DATE,
    NEEDS_SYNC,
    CONFLICT,
    SYNCING,
}

private data class DailyPlanRecommendation(
    val newAyahs: Int,
    val reviewAyahs: Int,
)

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
    val todoByNumber = remember(uiState.todoSurahs) { uiState.todoSurahs.associateBy { it.surahNumber } }
    val selectionMode = uiState.selectedSurahNumbers.isNotEmpty()

    var showMistakesOnly by remember { mutableStateOf(false) }
    var showReviewSession by remember { mutableStateOf(false) }
    var showFocusSession by remember { mutableStateOf(false) }
    var focusPresetMinutes by remember { mutableStateOf(3) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var syncInProgress by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDueReviewsNow()
    }

    LaunchedEffect(uiState.syncStatusMessage) {
        syncInProgress = false
    }

    LaunchedEffect(uiState.restoredSettings) {
        val restored = uiState.restoredSettings ?: return@LaunchedEffect
        setSettings(mergeSettings(settings, restored))
        viewModel.consumeRestoredSettings()
    }

    val ayahTextByNumber = remember(uiState.completeQuran) {
        uiState.completeQuran
            .flatMap { it.ayahs }
            .associate { it.number to it.text }
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

    val weakAyahMap = remember(uiState.weakAyahKeys) {
        uiState.weakAyahKeys.mapNotNull { key ->
            val parts = key.split(":")
            if (parts.size != 2) return@mapNotNull null
            val surah = parts[0].toIntOrNull() ?: return@mapNotNull null
            val ayah = parts[1].toIntOrNull() ?: return@mapNotNull null
            surah to ayah
        }.groupBy({ it.first }, { it.second })
    }

    val dueBySurah = remember(uiState.dueReviews) {
        uiState.dueReviews.groupBy { it.surahNumber }.mapValues { it.value.size }
    }

    val filteredByStatus = if (uiState.filter == null) {
        uiState.surahList
    } else {
        uiState.surahList.filter { element ->
            todoByNumber[element.surahNumber]?.status == uiState.filter
        }
    }
    val filteredSurahs = if (showMistakesOnly) {
        filteredByStatus.filter { weakAyahMap.containsKey(it.surahNumber) }
    } else {
        filteredByStatus
    }

    val learnedCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNED }
    val learningCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNING }
    val learnedAyahs = uiState.ayahTodos.count { it.status == AyahTodoStatus.LEARNED }

    val today = remember { currentLocalDate() }
    val todayProgress = uiState.ayahTodos.count {
        it.updatedAt > 0 && localDateFromEpoch(it.updatedAt) == today
    }
    val lastActivityDays = uiState.lastActivityAt?.let {
        daysBetween(localDateFromEpoch(it), today)
    }
    val showInactivityNudge = lastActivityDays != null && lastActivityDays >= 2

    val todayEpochDay = remember(today) { today.toEpochDays() }
    val daysLeft = (settings.targetEpochDay - todayEpochDay).coerceAtLeast(1)
    val remainingAyahs = (settings.targetAyahs - learnedAyahs).coerceAtLeast(0)
    val pacePerDay = if (remainingAyahs == 0) 0 else ceil(remainingAyahs.toDouble() / daysLeft).toInt()
    val projectionDays = if (settings.dailyGoal <= 0) Int.MAX_VALUE else ceil(remainingAyahs.toDouble() / settings.dailyGoal).toInt()
    val behindDays = (projectionDays - daysLeft).coerceAtLeast(0)

    val smartPlan = remember(
        settings.focusMinutes,
        settings.dailyGoal,
        uiState.dueReviews.size,
        pacePerDay
    ) {
        recommendDailyPlan(
            focusMinutes = settings.focusMinutes,
            dueReviews = uiState.dueReviews.size,
            paceAyahs = pacePerDay,
            dailyGoal = settings.dailyGoal
        )
    }

    var dueQueue by remember(uiState.dueReviews) { mutableStateOf(uiState.dueReviews) }
    val duePreview = dueQueue.firstOrNull()

    val syncState = remember(syncInProgress, uiState.syncStatusMessage, uiState.hasCloudSnapshot) {
        when {
            syncInProgress -> SyncIndicatorState.SYNCING
            uiState.syncStatusMessage?.contains("conflict", ignoreCase = true) == true -> SyncIndicatorState.CONFLICT
            uiState.syncStatusMessage?.contains("failed", ignoreCase = true) == true -> SyncIndicatorState.CONFLICT
            uiState.hasCloudSnapshot -> SyncIndicatorState.UP_TO_DATE
            else -> SyncIndicatorState.NEEDS_SYNC
        }
    }

    val recentSurahs = remember(uiState.ayahTodos) {
        uiState.ayahTodos
            .sortedByDescending { it.updatedAt }
            .map { it.surahNumber }
            .distinct()
            .take(7)
    }
    val learningSurahs = remember(uiState.todoSurahs) {
        uiState.todoSurahs
            .filter { it.status == SurahTodoStatus.LEARNING }
            .map { it.surahNumber }
            .take(7)
    }
    val weakSurahs = remember(weakAyahMap) {
        weakAyahMap.keys.sorted().take(7)
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetBackgroundColor = MaterialTheme.colors.surface,
        sheetElevation = 4.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContent = {
            QuickJumpSheet(
                strings = strings,
                resumeLast = UserSettingsStorage.getLastPlaybackSurah(),
                learningSurahs = learningSurahs,
                recentSurahs = recentSurahs,
                weakSurahs = weakSurahs,
                onOpenSurah = { surahNumber ->
                    viewModel.openSurahDetail(surahNumber)
                    scope.launch { sheetState.hide() }
                },
                onClose = {
                    scope.launch { sheetState.hide() }
                }
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                item {
                    HomeHeader(
                        strings = strings,
                        syncState = syncState,
                        onQuickJump = {
                            scope.launch { sheetState.show() }
                        }
                    )
                }

                item {
                    SummaryCard(
                        strings = strings,
                        learned = learnedCount,
                        learning = learningCount,
                        total = uiState.surahList.size
                    )
                }

                if (uiState.isLoadingQuran && uiState.completeQuran.isEmpty()) {
                    items(3) {
                        SkeletonCard()
                    }
                } else {
                    if (uiState.errorMessage != null) {
                        item {
                            TopBanner(
                                title = strings.homeLoadErrorTitle,
                                subtitle = uiState.errorMessage ?: strings.homeLoadErrorTitle,
                                kind = BannerKind.ERROR,
                                action = strings.homeRetryLabel,
                                onAction = { viewModel.refreshQuran(withLocalAction = false) }
                            )
                        }
                    }

                    if (uiState.errorMessage?.contains("offline", ignoreCase = true) == true ||
                        uiState.errorMessage?.contains("network", ignoreCase = true) == true
                    ) {
                        item {
                            TopBanner(
                                title = strings.homeOfflineBanner,
                                subtitle = null,
                                kind = BannerKind.WARNING,
                                action = null,
                                onAction = {}
                            )
                        }
                    }

                    item {
                        TodayPlanCard(
                            strings = strings,
                            todayProgress = todayProgress,
                            dailyGoal = settings.dailyGoal,
                            behindDays = behindDays,
                            smartPlan = smartPlan,
                            onStart = {
                                focusPresetMinutes = settings.focusMinutes.coerceIn(3, 60)
                                showFocusSession = true
                            },
                            onAdjust = {
                                scope.launch { sheetState.show() }
                            }
                        )
                    }

                    if (showInactivityNudge) {
                        item {
                            InactivityNudgeCard(
                                strings = strings,
                                onStart = {
                                    focusPresetMinutes = 3
                                    showFocusSession = true
                                }
                            )
                        }
                    }

                    item {
                        DuePreviewCard(
                            strings = strings,
                            dueTotal = uiState.dueReviews.size,
                            review = duePreview,
                            ayahTextByNumber = ayahTextByNumber,
                            onRate = { quality ->
                                val current = dueQueue.firstOrNull() ?: return@DuePreviewCard
                                viewModel.completeReview(current.ayahNumber, current.surahNumber, quality)
                                dueQueue = dueQueue.drop(1)
                            },
                            onOpenQueue = {
                                dueQueue = uiState.dueReviews
                                showReviewSession = true
                            }
                        )
                    }

                    item {
                        FocusTimerCard(
                            strings = strings,
                            selectedPreset = focusPresetMinutes,
                            onSelectPreset = { focusPresetMinutes = it },
                            onStart = { showFocusSession = true }
                        )
                    }

                    item {
                        OfflinePackageCard(
                            strings = strings,
                            isRunning = uiState.offlineDownloadRunning,
                            done = uiState.offlineDownloadDone,
                            total = uiState.offlineDownloadTotal,
                            status = uiState.offlineDownloadStatus,
                            onManage = { viewModel.startOfflinePackage(language) }
                        )
                    }

                    item {
                        CloudSyncCard(
                            strings = strings,
                            status = uiState.syncStatusMessage,
                            providerLabel = uiState.syncProviderLabel,
                            hasSnapshot = uiState.hasCloudSnapshot,
                            lastSyncAt = uiState.lastSyncAt,
                            syncing = syncInProgress,
                            onSync = {
                                syncInProgress = true
                                viewModel.syncProgressToCloud(settings)
                            },
                            onRestore = {
                                showRestoreDialog = true
                            }
                        )
                    }

                    if (selectionMode) {
                        item {
                            SelectionBar(
                                selectedCount = uiState.selectedSurahNumbers.size,
                                onLearned = { viewModel.markSelected(SurahTodoStatus.LEARNED) },
                                onLearning = { viewModel.markSelected(SurahTodoStatus.LEARNING) },
                                onClear = { viewModel.clearSelection() }
                            )
                        }
                    }

                    item {
                        FilterRow(
                            strings = strings,
                            learnedCount = learnedCount,
                            learningCount = learningCount,
                            weakCount = weakAyahMap.keys.size,
                            showMistakesOnly = showMistakesOnly,
                            filter = uiState.filter,
                            onFilterChange = viewModel::setFilter,
                            onToggleMistakes = {
                                showMistakesOnly = !showMistakesOnly
                                AppTelemetry.logEvent(
                                    name = "home_mistakes_filter_toggled",
                                    params = mapOf("enabled" to showMistakesOnly.toString())
                                )
                            }
                        )
                    }

                    items(filteredSurahs, key = { it.surahNumber }) { surah ->
                        val todo = todoByNumber[surah.surahNumber]
                        val isSelected = uiState.selectedSurahNumbers.contains(surah.surahNumber)
                        var menuExpanded by remember(surah.surahNumber) { mutableStateOf(false) }

                        SurahListItem(
                            strings = strings,
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
                            status = todo?.status,
                            dueCount = dueBySurah[surah.surahNumber] ?: 0,
                            weakCount = weakAyahMap[surah.surahNumber]?.size ?: 0,
                            isSelected = isSelected,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.toggleSelection(surah.surahNumber)
                                } else {
                                    viewModel.openSurahDetail(surah.surahNumber)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(surah.surahNumber)
                            },
                            onOpenMenu = { menuExpanded = true },
                            menu = {
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        viewModel.setSurahStatus(surah.surahNumber, SurahTodoStatus.LEARNED)
                                    }) {
                                        Text(strings.learnedLabel)
                                    }
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        viewModel.setSurahStatus(surah.surahNumber, SurahTodoStatus.LEARNING)
                                    }) {
                                        Text(strings.learningLabel)
                                    }
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        viewModel.setSurahStatus(surah.surahNumber, null)
                                    }) {
                                        Text(strings.clearLabel, color = MaterialTheme.extendedColors.danger)
                                    }
                                    DropdownMenuItem(onClick = {
                                        menuExpanded = false
                                        viewModel.startOfflinePackage(language)
                                    }) {
                                        Text(strings.offlinePackageDownloadLabel)
                                    }
                                }
                            }
                        )
                    }
                }
        }
    }

    if (showFocusSession) {
        FocusSessionDialog(
            title = strings.focusSessionTitle,
            subtitle = strings.focusSessionSubtitle,
            startLabel = strings.focusStartLabel,
            stopLabel = strings.focusStopLabel,
            doneLabel = strings.focusDoneLabel,
            minutes = focusPresetMinutes,
            onDismiss = { showFocusSession = false },
            onCompleted = { minutes ->
                viewModel.recordFocusSession(minutes)
                showFocusSession = false
            }
        )
    }

    if (showReviewSession) {
        ReviewSessionDialog(
            strings = strings,
            dueReviews = dueQueue,
            ayahTextByNumber = ayahTextByNumber,
            onDismiss = { showReviewSession = false },
            onComplete = { ayahNumber, surahNumber, quality ->
                viewModel.completeReview(ayahNumber, surahNumber, quality)
                dueQueue = dueQueue.drop(1)
            }
        )
    }

    if (showRestoreDialog) {
        RestoreConfirmDialog(
            strings = strings,
            onDismiss = { showRestoreDialog = false },
            onConfirm = {
                showRestoreDialog = false
                syncInProgress = true
                viewModel.restoreProgressFromCloud()
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
private fun HomeHeader(
    strings: AppStrings,
    syncState: SyncIndicatorState,
    onQuickJump: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.homeSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onQuickJump) {
                Icon(Icons.Default.Search, contentDescription = strings.quickJumpTitle)
            }
            SyncDot(state = syncState)
        }
    }
}

@Composable
private fun SyncDot(state: SyncIndicatorState) {
    val color = when (state) {
        SyncIndicatorState.UP_TO_DATE -> MaterialTheme.extendedColors.success
        SyncIndicatorState.NEEDS_SYNC -> MaterialTheme.extendedColors.warning
        SyncIndicatorState.CONFLICT -> MaterialTheme.extendedColors.danger
        SyncIndicatorState.SYNCING -> MaterialTheme.extendedColors.info
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color,
        modifier = Modifier
            .padding(start = 4.dp)
            .width(12.dp)
            .height(12.dp)
    ) {}
}

private enum class BannerKind {
    ERROR,
    WARNING,
}

@Composable
private fun TopBanner(
    title: String,
    subtitle: String?,
    kind: BannerKind,
    action: String?,
    onAction: () -> Unit,
) {
    val tint = when (kind) {
        BannerKind.ERROR -> MaterialTheme.extendedColors.danger
        BannerKind.WARNING -> MaterialTheme.extendedColors.warning
    }
    val icon = when (kind) {
        BannerKind.ERROR -> Icons.Default.ErrorOutline
        BannerKind.WARNING -> Icons.Default.Warning
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        backgroundColor = MaterialTheme.colors.softSurface,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.body2, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.mutedText)
                }
            }
            if (!action.isNullOrBlank()) {
                TextButton(onClick = onAction) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 2) 0.58f else 1f)
                        .height(if (it == 0) 18.dp else 12.dp)
                        .background(MaterialTheme.colors.softSurface, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    strings: AppStrings,
    learned: Int,
    learning: Int,
    total: Int,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.homeSummaryTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$learned ${strings.learnedLabel.lowercase()} • $learning ${strings.learningLabel.lowercase()} • $total ${strings.totalLabel.lowercase()}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun InactivityNudgeCard(
    strings: AppStrings,
    onStart: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.homeWelcomeBackTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(strings.homeWelcomeBackSubtitle, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.mutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(strings.homeStart3MinFocus)
                }
                OutlinedPillButton(label = strings.homeDismissLabel, onClick = {})
            }
        }
    }
}

@Composable
private fun TodayPlanCard(
    strings: AppStrings,
    todayProgress: Int,
    dailyGoal: Int,
    behindDays: Int,
    smartPlan: DailyPlanRecommendation,
    onStart: () -> Unit,
    onAdjust: () -> Unit,
) {
    val progress = if (dailyGoal <= 0) 0f else (todayProgress.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.homeTodayTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$todayProgress/$dailyGoal ${strings.ayahsLabel.lowercase()} • ${strings.dailyGoalLabel} $dailyGoal",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.progressTrack
            )
            Text(
                text = if (behindDays == 0) {
                    strings.homeOnTrackLabel
                } else {
                    "${strings.homeBehindByLabel} $behindDays ${strings.homeDaysLabel}"
                },
                style = MaterialTheme.typography.caption,
                color = if (behindDays == 0) {
                    MaterialTheme.extendedColors.success
                } else {
                    MaterialTheme.extendedColors.warning
                }
            )
            Text(
                text = "${strings.todayProgressLabel}: ${smartPlan.newAyahs} • " +
                    "${strings.reviewDueTitle}: ${smartPlan.reviewAyahs} • " +
                    "${strings.focusMinutesLabel}: ${smartPlan.newAyahs * 5}m",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(strings.homeStartSession)
                }
                OutlinedPillButton(label = strings.homeAdjustLabel, onClick = onAdjust)
            }
        }
    }
}

@Composable
private fun DuePreviewCard(
    strings: AppStrings,
    dueTotal: Int,
    review: AyahReviewEntity?,
    ayahTextByNumber: Map<Int, String>,
    onRate: (ReviewQuality) -> Unit,
    onOpenQueue: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${strings.homeDueNowTitle}: $dueTotal", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            if (review == null) {
                Text(strings.reviewEmptyLabel, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.mutedText)
            } else {
                Text(
                    text = "${strings.surahLabel} ${review.surahNumber} • ${strings.ayahsLabel} ${review.ayahNumber}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
                Text(
                    text = ayahTextByNumber[review.ayahNumber] ?: strings.reviewEmptyLabel,
                    style = MaterialTheme.typography.body1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewChip(strings.hardLabel, MaterialTheme.extendedColors.danger) { onRate(ReviewQuality.HARD) }
                    ReviewChip(strings.goodLabel, MaterialTheme.colors.primary) { onRate(ReviewQuality.GOOD) }
                    ReviewChip(strings.easyLabel, MaterialTheme.extendedColors.success) { onRate(ReviewQuality.EASY) }
                }
            }
            OutlinedPillButton(label = strings.homeOpenQueueLabel, onClick = onOpenQueue)
        }
    }
}

@Composable
private fun FocusTimerCard(
    strings: AppStrings,
    selectedPreset: Int,
    onSelectPreset: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.focusTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { preset ->
                    SegmentedChip(
                        label = "${preset}m",
                        selected = selectedPreset == preset,
                        onClick = { onSelectPreset(preset) }
                    )
                }
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(strings.focusStartLabel)
            }
        }
    }
}

@Composable
private fun OfflinePackageCard(
    strings: AppStrings,
    isRunning: Boolean,
    done: Int,
    total: Int,
    status: String?,
    onManage: () -> Unit,
) {
    val subtitle = when (status) {
        "DOWNLOADING" -> strings.offlinePackageDownloadingLabel
        "READY" -> strings.offlinePackageReadyLabel
        else -> strings.offlinePackageSubtitle
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.offlinePackageTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.mutedText)
            if (total > 0) {
                Text("$done/$total", style = MaterialTheme.typography.caption)
                LinearProgressIndicator(
                    progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colors.primary,
                    backgroundColor = MaterialTheme.colors.progressTrack
                )
            }
            OutlinedPillButton(
                label = if (isRunning) strings.offlinePackageDownloadingLabel else strings.manageLabel,
                onClick = onManage,
                enabled = !isRunning
            )
        }
    }
}

@Composable
private fun CloudSyncCard(
    strings: AppStrings,
    status: String?,
    providerLabel: String,
    hasSnapshot: Boolean,
    lastSyncAt: Long?,
    syncing: Boolean,
    onSync: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colors.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.syncCloudTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            }
            Text(providerLabel, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.mutedText)
            Text(
                text = if (hasSnapshot) {
                    lastSyncAt?.let { "${strings.syncUpToDateLabel} • ${localDateFromEpoch(it)}" } ?: strings.syncUpToDateLabel
                } else {
                    strings.syncNeedsSyncLabel
                },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            if (!status.isNullOrBlank()) {
                Text(status, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSync, enabled = !syncing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.syncNowLabel)
                }
                OutlinedPillButton(
                    label = strings.syncRestoreLabel,
                    onClick = onRestore,
                    enabled = !syncing
                )
            }
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
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$selectedCount ${strings.selectionLabel.lowercase()}", style = MaterialTheme.typography.body2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SegmentedChip(strings.learnedLabel, selected = false, onClick = onLearned)
                SegmentedChip(strings.learningLabel, selected = false, onClick = onLearning)
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = strings.clearLabel)
                }
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
        SegmentedChip(
            label = strings.filterAll,
            selected = filter == null,
            onClick = { onFilterChange(null) }
        )
        SegmentedChip(
            label = "${strings.filterLearned} $learnedCount",
            selected = filter == SurahTodoStatus.LEARNED,
            onClick = { onFilterChange(SurahTodoStatus.LEARNED) }
        )
        SegmentedChip(
            label = "${strings.filterLearning} $learningCount",
            selected = filter == SurahTodoStatus.LEARNING,
            onClick = { onFilterChange(SurahTodoStatus.LEARNING) }
        )
        SegmentedChip(
            label = "${strings.mistakesFilterLabel} $weakCount",
            selected = showMistakesOnly,
            onClick = onToggleMistakes
        )
    }
}

@Composable
private fun SegmentedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.16f)
    } else {
        MaterialTheme.colors.softSurface
    }
    val textColor = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SurahListItem(
    strings: AppStrings,
    surahNumber: Int,
    arabicName: String,
    transliteration: String?,
    translatedName: String?,
    status: SurahTodoStatus?,
    dueCount: Int,
    weakCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenMenu: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        backgroundColor = if (isSelected) {
            MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.18f)
        } else {
            MaterialTheme.colors.surface
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.subtleBorder)
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(strings = strings, status = status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dueCount > 0) {
                        Badge(
                            text = "${strings.reviewDueTitle.lowercase()} $dueCount",
                            tint = MaterialTheme.extendedColors.warning
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (weakCount > 0) {
                        Badge(
                            text = "${strings.weakLabel.lowercase()} $weakCount",
                            tint = MaterialTheme.extendedColors.danger
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Default.MoreVert, contentDescription = strings.actionsLabel)
                    }
                }
            }

            Text(
                text = arabicName,
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!transliteration.isNullOrBlank()) {
                Text(
                    text = transliteration,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!translatedName.isNullOrBlank()) {
                Text(
                    text = translatedName,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.faintText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${strings.surahLabel} $surahNumber",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
        menu()
    }
}

@Composable
private fun StatusPill(strings: AppStrings, status: SurahTodoStatus?) {
    val label = when (status) {
        SurahTodoStatus.LEARNED -> strings.learnedLabel
        SurahTodoStatus.LEARNING -> strings.learningLabel
        null -> strings.noStateLabel
    }
    val tint = when (status) {
        SurahTodoStatus.LEARNED -> MaterialTheme.extendedColors.success
        SurahTodoStatus.LEARNING -> MaterialTheme.colors.primary
        null -> MaterialTheme.colors.mutedText
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colors.tintedSurface(tint, 0.14f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun Badge(text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colors.tintedSurface(tint, 0.16f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OutlinedPillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
        color = MaterialTheme.colors.surface,
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = if (enabled) MaterialTheme.colors.onSurface else MaterialTheme.extendedColors.disabled,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ReviewChip(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colors.tintedSurface(color, 0.14f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun QuickJumpSheet(
    strings: AppStrings,
    resumeLast: Int?,
    learningSurahs: List<Int>,
    recentSurahs: List<Int>,
    weakSurahs: List<Int>,
    onOpenSurah: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.quickJumpTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }

        QuickJumpSection(strings.quickJumpResumeLast, listOfNotNull(resumeLast), onOpenSurah)
        QuickJumpSection(strings.quickJumpLearningSurahs, learningSurahs.take(7), onOpenSurah)
        QuickJumpSection(strings.quickJumpRecent, recentSurahs.take(7), onOpenSurah)
        QuickJumpSection(strings.quickJumpWeakBank, weakSurahs.take(7), onOpenSurah)
    }
}

@Composable
private fun QuickJumpSection(
    title: String,
    surahNumbers: List<Int>,
    onOpenSurah: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.mutedText)
        if (surahNumbers.isEmpty()) {
            Text("-", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.faintText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                surahNumbers.forEach { number ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colors.softSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSurah(number) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${LocalAppStrings.current.surahLabel} $number", style = MaterialTheme.typography.body2)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colors.mutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSessionDialog(
    strings: AppStrings,
    dueReviews: List<AyahReviewEntity>,
    ayahTextByNumber: Map<Int, String>,
    onDismiss: () -> Unit,
    onComplete: (Int, Int, ReviewQuality) -> Unit,
) {
    val total = dueReviews.size
    val current = dueReviews.firstOrNull()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = 4.dp,
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.reviewSessionTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                if (current == null) {
                    Text(strings.reviewEmptyLabel, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.mutedText)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.reviewDoneLabel)
                    }
                } else {
                    val index = (total - dueReviews.size + 1).coerceAtLeast(1)
                    Text(
                        text = "$index/$total",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.mutedText
                    )
                    Text(
                        text = "${strings.surahLabel} ${current.surahNumber} • ${strings.ayahsLabel} ${current.ayahNumber}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.mutedText
                    )
                    Text(
                        text = ayahTextByNumber[current.ayahNumber] ?: strings.reviewEmptyLabel,
                        style = MaterialTheme.typography.body1,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewChip(strings.hardLabel, MaterialTheme.extendedColors.danger) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.HARD)
                        }
                        ReviewChip(strings.goodLabel, MaterialTheme.colors.primary) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.GOOD)
                        }
                        ReviewChip(strings.easyLabel, MaterialTheme.extendedColors.success) {
                            onComplete(current.ayahNumber, current.surahNumber, ReviewQuality.EASY)
                        }
                    }
                    OutlinedPillButton(label = strings.reviewDoneLabel, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = 4.dp,
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.syncRestoreLabel, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(strings.syncRestoreWarning, style = MaterialTheme.typography.body2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(strings.syncConfirmLabel, style = MaterialTheme.typography.body2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedPillButton(label = strings.homeDismissLabel, onClick = onDismiss)
                    Button(onClick = onConfirm, enabled = confirmed) {
                        Text(strings.syncRestoreConfirmLabel)
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
                    color = MaterialTheme.colors.mutedText,
                    textAlign = TextAlign.Center
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
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
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
        examModeEnabled = restored.examModeEnabled,
        readingFontSize = restored.readingFontSize
    )
}
