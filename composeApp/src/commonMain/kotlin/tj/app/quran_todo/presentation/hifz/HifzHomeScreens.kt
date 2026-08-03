package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.presentation.designsystem.HifzMetric
import tj.app.quran_todo.presentation.designsystem.HifzPanel
import tj.app.quran_todo.presentation.designsystem.HifzPrimaryButton
import tj.app.quran_todo.presentation.designsystem.HifzProgressCapsule
import tj.app.quran_todo.presentation.designsystem.HifzScreen
import tj.app.quran_todo.presentation.designsystem.HifzSecondaryButton
import tj.app.quran_todo.presentation.designsystem.HifzSectionHeader
import tj.app.quran_todo.presentation.designsystem.HifzStatusChip
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel

private enum class TodayTaskKind {
    NEW,
    RECENT,
    OLD,
    WEAK,
}

private data class TodayTaskUi(
    val id: String,
    val kind: TodayTaskKind,
    val surahNumber: Int,
    val surahName: String,
    val startAyah: Int,
    val endAyah: Int,
    val ayahCount: Int,
    val ayahNumbers: List<Int>,
)

private enum class QuranFilter {
    ALL,
    REVIEW,
    DIFFICULT,
}

private enum class QuranMemoryState {
    NEW,
    LEARNING,
    MEMORIZED,
    NEEDS_REVIEW,
}

private data class QuranSurahUi(
    val surahNumber: Int,
    val arabicName: String,
    val transliteration: String,
    val translatedName: String,
    val ayahCount: Int,
    val learnedAyahs: Int,
    val dueCount: Int,
    val weakCount: Int,
    val memoryState: QuranMemoryState,
    val canOpen: Boolean,
)

private data class HifzHomeCopy(
    val today: String,
    val todaySubtitle: String,
    val focusedHifz: String,
    val ready: String,
    val inProgress: String,
    val complete: String,
    val minutesToday: String,
    val createPlan: String,
    val createPlanBody: String,
    val createPlanAction: String,
    val startToday: String,
    val startReview: String,
    val newHifz: String,
    val recentReview: String,
    val oldReview: String,
    val weakSpots: String,
    val emptyNew: String,
    val emptyRecent: String,
    val emptyOld: String,
    val emptyWeak: String,
    val quran: String,
    val quranSubtitle: String,
    val library: String,
    val searchHint: String,
    val reviewFilter: String,
    val difficultFilter: String,
    val noResults: String,
    val newState: String,
    val memorizedState: String,
    val needsReviewState: String,
    val review: String,
    val learn: String,
    val strong: String,
    val open: String,
    val confirmStrongTitle: String,
    val confirmStrongBody: String,
    val confirm: String,
    val cancel: String,
)

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HifzTodayRoute(
    viewModel: HomeViewModel = koinViewModel(),
    onOpenQuran: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val strings = LocalAppStrings.current
    val settings = LocalAppSettings.current
    val state by viewModel.uiState.collectAsState()
    val copy = remember(language) { hifzHomeCopy(language) }
    val colors = MaterialTheme.extendedColors
    var sessionRequest by remember { mutableStateOf<HifzSessionRequest?>(null) }

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
    }
    LaunchedEffect(Unit) {
        viewModel.refreshDueReviewsNow()
    }

    val tasks = remember(state, settings.dailyGoal, settings.focusMinutes) {
        buildTodayTasks(
            state = state,
            dailyGoal = settings.dailyGoal,
            focusMinutes = settings.focusMinutes
        )
    }
    val newTasks = tasks.filter { it.kind == TodayTaskKind.NEW }
    val recentTasks = tasks.filter { it.kind == TodayTaskKind.RECENT }
    val oldTasks = tasks.filter { it.kind == TodayTaskKind.OLD }
    val weakTasks = tasks.filter { it.kind == TodayTaskKind.WEAK }
    val today = LocalDate.fromEpochDays(state.currentEpochDay)
    val todayProgress = state.ayahTodos.count {
        it.status == AyahTodoStatus.LEARNED &&
            it.updatedAt > 0 &&
            localDateFromEpoch(it.updatedAt) == today
    }
    val dailyGoal = settings.dailyGoal.coerceAtLeast(1)
    val reviewCount = recentTasks.sumOf { it.ayahCount } + oldTasks.sumOf { it.ayahCount }
    val newCount = newTasks.sumOf { it.ayahCount }
    val weakCount = weakTasks.sumOf { it.ayahCount }
    val estimatedMinutes = minOf(
        (newCount * 3 + reviewCount + weakCount).coerceAtLeast(1),
        settings.focusMinutes.coerceAtLeast(1)
    )
    val completionLabel = when {
        todayProgress >= dailyGoal -> copy.complete
        todayProgress > 0 -> copy.inProgress
        else -> copy.ready
    }
    val firstTask = newTasks.firstOrNull()
        ?: recentTasks.firstOrNull()
        ?: oldTasks.firstOrNull()
        ?: weakTasks.firstOrNull()

    fun openTask(task: TodayTaskUi) {
        sessionRequest = HifzSessionRequest(
            mode = if (task.kind == TodayTaskKind.NEW) {
                HifzSessionMode.LEARN
            } else {
                HifzSessionMode.REVIEW
            },
            startAyah = task.startAyah,
            endAyah = task.endAyah,
            ayahNumbers = task.ayahNumbers
        )
        viewModel.openSurahDetail(task.surahNumber)
    }

    HifzScreen {
        item {
            HifzSectionHeader(
                title = copy.today,
                subtitle = "${copy.todaySubtitle} · $today"
            )
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item {
                HifzMessagePanel(
                    title = strings.homeLoadErrorTitle,
                    body = state.errorMessage.orEmpty(),
                    tint = colors.danger,
                    action = strings.homeRetryLabel,
                    onAction = { viewModel.refreshQuran(withLocalAction = false) }
                )
            }
        }

        if (state.isLoadingQuran && state.completeQuran.isEmpty()) {
            item { HifzLoadingPanel() }
        } else if (state.todoSurahs.isEmpty()) {
            item {
                HifzPanel {
                    Text(
                        text = copy.createPlan,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = copy.createPlanBody,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    HifzPrimaryButton(
                        label = copy.createPlanAction,
                        onClick = onOpenQuran
                    )
                }
            }
        } else {
            item {
                HifzPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = completionLabel,
                                color = MaterialTheme.colors.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = copy.focusedHifz,
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "$estimatedMinutes ${copy.minutesToday}",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Column(
                            modifier = Modifier.width(120.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$todayProgress/$dailyGoal",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HifzProgressCapsule(
                                progress = todayProgress.toFloat() / dailyGoal.toFloat()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HifzMetric(
                            label = copy.newHifz,
                            value = newCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HifzMetric(
                            label = copy.review,
                            value = reviewCount.toString(),
                            modifier = Modifier.weight(1f),
                            tint = colors.warning
                        )
                        HifzMetric(
                            label = copy.weakSpots,
                            value = weakCount.toString(),
                            modifier = Modifier.weight(1f),
                            tint = colors.danger
                        )
                    }
                    if (firstTask != null) {
                        HifzPrimaryButton(
                            label = if (firstTask.kind == TodayTaskKind.NEW) {
                                copy.startToday
                            } else {
                                copy.startReview
                            },
                            onClick = { openTask(firstTask) }
                        )
                    }
                }
            }

            taskSection(
                title = copy.newHifz,
                emptyText = copy.emptyNew,
                tasks = newTasks,
                copy = copy,
                onOpen = ::openTask
            )
            taskSection(
                title = copy.recentReview,
                emptyText = copy.emptyRecent,
                tasks = recentTasks,
                copy = copy,
                onOpen = ::openTask
            )
            taskSection(
                title = copy.oldReview,
                emptyText = copy.emptyOld,
                tasks = oldTasks,
                copy = copy,
                onOpen = ::openTask
            )
            taskSection(
                title = copy.weakSpots,
                emptyText = copy.emptyWeak,
                tasks = weakTasks,
                copy = copy,
                onOpen = ::openTask
            )
        }
    }

    SelectedSurahDialog(
        state = state,
        request = sessionRequest,
        onDismiss = {
            sessionRequest = null
            viewModel.dismissSurahDetail()
        }
    )
}

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HifzQuranRoute(
    viewModel: HomeViewModel = koinViewModel(),
) {
    val language = LocalAppLanguage.current
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsState()
    val copy = remember(language) { hifzHomeCopy(language) }
    val colors = MaterialTheme.extendedColors
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(QuranFilter.ALL.name) }
    val filter = QuranFilter.valueOf(filterName)
    var confirmStrongSurah by remember { mutableStateOf<QuranSurahUi?>(null) }
    var sessionRequest by remember { mutableStateOf<HifzSessionRequest?>(null) }

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
    }
    LaunchedEffect(Unit) {
        viewModel.refreshDueReviewsNow()
    }

    val allRows = remember(state, language) {
        buildQuranRows(state, language)
    }
    val rows = remember(allRows, query, filter) {
        val normalized = query.trim().lowercase()
        allRows.filter { row ->
            val matchesQuery = normalized.isEmpty() ||
                row.surahNumber.toString() == normalized ||
                row.transliteration.lowercase().contains(normalized) ||
                row.translatedName.lowercase().contains(normalized) ||
                row.arabicName.contains(normalized)
            val matchesFilter = when (filter) {
                QuranFilter.ALL -> true
                QuranFilter.REVIEW -> row.dueCount > 0
                QuranFilter.DIFFICULT -> row.weakCount > 0
            }
            matchesQuery && matchesFilter
        }
    }

    HifzScreen {
        item {
            HifzSectionHeader(
                title = copy.quran,
                subtitle = copy.quranSubtitle
            )
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item {
                HifzMessagePanel(
                    title = strings.homeLoadErrorTitle,
                    body = state.errorMessage.orEmpty(),
                    tint = colors.danger,
                    action = strings.homeRetryLabel,
                    onAction = { viewModel.refreshQuran(withLocalAction = false) }
                )
            }
        }

        item {
            HifzPanel {
                Text(
                    text = copy.library,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${allRows.size} ${strings.statsSurahs.lowercase()}",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(copy.searchHint) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                QuranFilterControl(
                    selected = filter,
                    allLabel = strings.filterAll,
                    reviewLabel = copy.reviewFilter,
                    difficultLabel = copy.difficultFilter,
                    onSelected = { filterName = it.name }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HifzMetric(
                        label = strings.learningLabel,
                        value = state.ayahTodos.count {
                            it.status == AyahTodoStatus.LEARNING
                        }.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    HifzMetric(
                        label = copy.strong,
                        value = state.ayahTodos.count {
                            it.status == AyahTodoStatus.LEARNED
                        }.toString(),
                        modifier = Modifier.weight(1f),
                        tint = colors.success
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HifzMetric(
                        label = copy.reviewFilter,
                        value = state.dueReviews.size.toString(),
                        modifier = Modifier.weight(1f),
                        tint = colors.warning
                    )
                    HifzMetric(
                        label = copy.difficultFilter,
                        value = state.weakAyahKeys.size.toString(),
                        modifier = Modifier.weight(1f),
                        tint = colors.danger
                    )
                }
            }
        }

        if (state.isLoadingQuran && allRows.isEmpty()) {
            item { HifzLoadingPanel() }
        } else if (rows.isEmpty()) {
            item {
                HifzPanel {
                    Text(
                        text = copy.noResults,
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            items(rows, key = { it.surahNumber }) { row ->
                val dueAyahNumbers = state.dueReviews
                    .asSequence()
                    .filter { it.surahNumber == row.surahNumber }
                    .sortedBy { it.nextReviewAt }
                    .map { it.ayahNumber }
                    .take(5)
                    .toList()
                val weakAyahNumbers = state.weakAyahKeys
                    .asSequence()
                    .mapNotNull { key ->
                        val parts = key.split(":")
                        val surahNumber = parts.getOrNull(0)?.toIntOrNull()
                        val ayahNumber = parts.getOrNull(1)?.toIntOrNull()
                        if (surahNumber == row.surahNumber) ayahNumber else null
                    }
                    .sorted()
                    .take(5)
                    .toList()
                val targetedReviewAyahs = when (filter) {
                    QuranFilter.REVIEW -> dueAyahNumbers
                    QuranFilter.DIFFICULT -> weakAyahNumbers
                    QuranFilter.ALL -> (dueAyahNumbers + weakAyahNumbers)
                        .distinct()
                        .take(5)
                }
                val learnedAyahNumbers = state.ayahTodos
                    .asSequence()
                    .filter {
                        it.surahNumber == row.surahNumber &&
                            it.status == AyahTodoStatus.LEARNED
                    }
                    .map { it.ayahNumber }
                    .toSet()
                val nextLearningAyahs = state.completeQuran
                    .firstOrNull { it.surah.number == row.surahNumber }
                    ?.ayahs
                    .orEmpty()
                    .asSequence()
                    .sortedBy { it.numberInSurah }
                    .filterNot { it.number in learnedAyahNumbers }
                    .map { it.number }
                    .take(5)
                    .toList()
                QuranSurahRow(
                    row = row,
                    copy = copy,
                    canLearn = nextLearningAyahs.isNotEmpty(),
                    onReview = {
                        sessionRequest = if (
                            filter == QuranFilter.ALL && targetedReviewAyahs.isEmpty()
                        ) {
                            HifzSessionRequest(
                                mode = HifzSessionMode.REVIEW,
                                startAyah = 1,
                                endAyah = minOf(5, row.ayahCount)
                            )
                        } else {
                            HifzSessionRequest(
                                mode = HifzSessionMode.REVIEW,
                                ayahNumbers = targetedReviewAyahs
                            )
                        }
                        viewModel.openSurahDetail(row.surahNumber)
                    },
                    onLearn = {
                        sessionRequest = HifzSessionRequest(
                            mode = HifzSessionMode.LEARN,
                            ayahNumbers = nextLearningAyahs
                        )
                        viewModel.upsertSurahToTodo(
                            SurahTodoEntity(
                                surahNumber = row.surahNumber,
                                status = SurahTodoStatus.LEARNING
                            )
                        )
                        viewModel.openSurahDetail(row.surahNumber)
                    },
                    onMarkStrong = { confirmStrongSurah = row }
                )
            }
        }
    }

    confirmStrongSurah?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmStrongSurah = null },
            title = { Text(copy.confirmStrongTitle) },
            text = { Text(copy.confirmStrongBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSurahStatus(row.surahNumber, SurahTodoStatus.LEARNED)
                        confirmStrongSurah = null
                    }
                ) {
                    Text(copy.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStrongSurah = null }) {
                    Text(copy.cancel)
                }
            }
        )
    }

    SelectedSurahDialog(
        state = state,
        request = sessionRequest,
        onDismiss = {
            sessionRequest = null
            viewModel.dismissSurahDetail()
        }
    )
}

private fun LazyListScope.taskSection(
    title: String,
    emptyText: String,
    tasks: List<TodayTaskUi>,
    copy: HifzHomeCopy,
    onOpen: (TodayTaskUi) -> Unit,
) {
    item {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground
        )
    }
    if (tasks.isEmpty()) {
        item {
            Text(
                text = emptyText,
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.extendedColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        items(tasks, key = { it.id }) { task ->
            TodayTaskRow(
                task = task,
                copy = copy,
                onOpen = { onOpen(task) }
            )
        }
    }
}

@Composable
private fun TodayTaskRow(
    task: TodayTaskUi,
    copy: HifzHomeCopy,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val (icon, tint) = taskVisual(task.kind)
    HifzPanel(padding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = tint.copy(alpha = 0.14f),
                elevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.surahName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.startAyah}–${task.endAyah} · ${task.ayahCount}",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            HifzStatusChip(
                label = taskKindLabel(task.kind, copy),
                tint = tint
            )
        }
        HifzSecondaryButton(
            label = copy.open,
            onClick = onOpen
        )
    }
}

@Composable
private fun QuranFilterControl(
    selected: QuranFilter,
    allLabel: String,
    reviewLabel: String,
    difficultLabel: String,
    onSelected: (QuranFilter) -> Unit,
) {
    val entries = listOf(
        QuranFilter.ALL to allLabel,
        QuranFilter.REVIEW to reviewLabel,
        QuranFilter.DIFFICULT to difficultLabel
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.extendedColors.surfaceAlt,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            entries.forEach { (filter, label) ->
                val isSelected = selected == filter
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .adaptiveClickable(
                            shape = RoundedCornerShape(6.dp),
                            onClick = { onSelected(filter) },
                        ),
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        Color.Transparent
                    },
                    elevation = 0.dp
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colors.onPrimary
                        } else {
                            MaterialTheme.colors.onSurface
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranSurahRow(
    row: QuranSurahUi,
    copy: HifzHomeCopy,
    canLearn: Boolean,
    onReview: () -> Unit,
    onLearn: () -> Unit,
    onMarkStrong: () -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val stateTint = when (row.memoryState) {
        QuranMemoryState.NEW -> colors.textSecondary
        QuranMemoryState.LEARNING -> MaterialTheme.colors.primary
        QuranMemoryState.MEMORIZED -> colors.success
        QuranMemoryState.NEEDS_REVIEW -> colors.warning
    }
    val stateLabel = when (row.memoryState) {
        QuranMemoryState.NEW -> copy.newState
        QuranMemoryState.LEARNING -> LocalAppStrings.current.learningLabel
        QuranMemoryState.MEMORIZED -> copy.memorizedState
        QuranMemoryState.NEEDS_REVIEW -> copy.needsReviewState
    }

    HifzPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.extendedColors.primaryWeak,
                elevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = row.surahNumber.toString(),
                        color = MaterialTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HifzStatusChip(label = stateLabel, tint = stateTint)
                    if (row.dueCount > 0) {
                        HifzStatusChip(
                            label = "${row.dueCount} ${copy.reviewFilter.lowercase()}",
                            tint = colors.warning
                        )
                    }
                }
                Text(
                    text = row.transliteration,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.translatedName,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.ayahCount} · ${row.learnedAyahs} · " +
                        "${row.dueCount} · ${row.weakCount}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = row.arabicName,
                modifier = Modifier.width(88.dp),
                color = MaterialTheme.colors.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuranRowAction(
                label = copy.review,
                onClick = onReview,
                modifier = Modifier.weight(1f),
                enabled = row.canOpen
            )
            QuranRowAction(
                label = copy.learn,
                onClick = onLearn,
                modifier = Modifier.weight(1f),
                enabled = row.canOpen && canLearn
            )
            QuranRowAction(
                label = copy.strong,
                onClick = onMarkStrong,
                modifier = Modifier.weight(1f),
                enabled = row.canOpen && row.memoryState != QuranMemoryState.MEMORIZED,
                prominent = true
            )
        }
    }
}

@Composable
private fun QuranRowAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    val colors = MaterialTheme.extendedColors
    Surface(
        modifier = modifier
            .height(42.dp)
            .adaptiveClickable(
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = when {
            !enabled -> colors.surfaceAlt
            prominent -> MaterialTheme.colors.primary
            else -> colors.primaryWeak.copy(alpha = 0.58f)
        },
        border = if (prominent) {
            null
        } else {
            BorderStroke(1.dp, colors.selectionBorder.copy(alpha = 0.55f))
        },
        elevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 4.dp),
                color = when {
                    !enabled -> colors.textSecondary
                    prominent -> MaterialTheme.colors.onPrimary
                    else -> MaterialTheme.colors.primary
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HifzMessagePanel(
    title: String,
    body: String,
    tint: Color,
    action: String,
    onAction: () -> Unit,
) {
    HifzPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = tint
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    color = MaterialTheme.extendedColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
        HifzSecondaryButton(label = action, onClick = onAction)
    }
}

@Composable
private fun HifzLoadingPanel() {
    HifzPanel {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            contentAlignment = Alignment.Center
        ) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colors.primary,
            )
        }
    }
}

@Composable
internal fun SelectedSurahDialog(
    state: HomeUiState,
    request: HifzSessionRequest?,
    onDismiss: () -> Unit,
) {
    val surah = state.selectedSurah
    if (surah != null && request != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                HifzSessionScreen(
                    surahWithAyahs = surah,
                    request = request,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private fun buildTodayTasks(
    state: HomeUiState,
    dailyGoal: Int,
    focusMinutes: Int,
): List<TodayTaskUi> {
    val completeBySurah = state.completeQuran.associateBy { it.surah.number }
    val fallbackNames = state.surahList.associate { it.surahNumber to it.name }
    val dueKeys = state.dueReviews.map { "${it.surahNumber}:${it.ayahNumber}" }.toSet()
    val weakKeys = state.weakAyahKeys

    fun nameFor(surahNumber: Int): String =
        state.chapterNames[surahNumber]?.transliteration
            ?: completeBySurah[surahNumber]?.surah?.englishName
            ?: fallbackNames[surahNumber]
            ?: "Surah $surahNumber"

    fun localAyah(surahNumber: Int, globalAyah: Int): Int =
        completeBySurah[surahNumber]?.ayahs
            ?.firstOrNull { it.number == globalAyah }
            ?.numberInSurah
            ?: globalAyah

    val result = mutableListOf<TodayTaskUi>()
    val newAyahLimit = minOf(
        dailyGoal.coerceIn(1, 50),
        (focusMinutes / 3).coerceAtLeast(1)
    )
    val selectedLearningKeys = state.ayahTodos
        .filter {
            val key = "${it.surahNumber}:${it.ayahNumber}"
            it.status == AyahTodoStatus.LEARNING &&
                key !in dueKeys &&
                key !in weakKeys
        }
        .sortedWith(
            compareBy(
                { it.updatedAt },
                { it.surahNumber },
                { localAyah(it.surahNumber, it.ayahNumber) }
            )
        )
        .take(newAyahLimit)
        .map { it.surahNumber to it.ayahNumber }
    val alreadySelected = selectedLearningKeys
        .mapTo(mutableSetOf()) { (surah, ayah) -> "$surah:$ayah" }
    val todoByAyah = state.ayahTodos.associateBy { it.ayahNumber }
    val nextPlannedKeys = state.todoSurahs
        .filter { it.status == SurahTodoStatus.LEARNING }
        .sortedBy { it.surahNumber }
        .flatMap { todoSurah ->
            completeBySurah[todoSurah.surahNumber]
                ?.ayahs
                .orEmpty()
                .sortedBy { it.numberInSurah }
                .map { todoSurah.surahNumber to it.number }
        }
        .filter { (surah, ayah) ->
            val key = "$surah:$ayah"
            key !in dueKeys &&
                key !in weakKeys &&
                key !in alreadySelected &&
                todoByAyah[ayah]?.status != AyahTodoStatus.LEARNED
        }
        .take((newAyahLimit - selectedLearningKeys.size).coerceAtLeast(0))
    val plannedLearningKeys = selectedLearningKeys + nextPlannedKeys

    plannedLearningKeys
        .groupBy({ it.first }, { it.second })
        .forEach { (surahNumber, ayahNumbers) ->
            val selected = ayahNumbers.sortedBy { localAyah(surahNumber, it) }
            if (selected.isNotEmpty()) {
                result += TodayTaskUi(
                    id = "new:$surahNumber:${selected.first()}",
                    kind = TodayTaskKind.NEW,
                    surahNumber = surahNumber,
                    surahName = nameFor(surahNumber),
                    startAyah = localAyah(surahNumber, selected.first()),
                    endAyah = localAyah(surahNumber, selected.last()),
                    ayahCount = selected.size,
                    ayahNumbers = selected
                )
            }
        }

    fun addReviews(kind: TodayTaskKind, reviews: List<AyahReviewEntity>) {
        reviews.groupBy { it.surahNumber }.forEach { (surahNumber, values) ->
            val selected = values.sortedBy { localAyah(surahNumber, it.ayahNumber) }.take(5)
            if (selected.isNotEmpty()) {
                result += TodayTaskUi(
                    id = "${kind.name.lowercase()}:$surahNumber:${selected.first().ayahNumber}",
                    kind = kind,
                    surahNumber = surahNumber,
                    surahName = nameFor(surahNumber),
                    startAyah = localAyah(surahNumber, selected.first().ayahNumber),
                    endAyah = localAyah(surahNumber, selected.last().ayahNumber),
                    ayahCount = selected.size,
                    ayahNumbers = selected.map { it.ayahNumber }
                )
            }
        }
    }
    addReviews(
        TodayTaskKind.RECENT,
        state.dueReviews.filter { it.intervalIndex <= 1 }
    )
    addReviews(
        TodayTaskKind.OLD,
        state.dueReviews.filter { it.intervalIndex >= 2 }
    )

    state.weakAyahKeys
        .mapNotNull { key ->
            val parts = key.split(":")
            val surah = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val ayah = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (key in dueKeys) return@mapNotNull null
            surah to ayah
        }
        .groupBy({ it.first }, { it.second })
        .forEach { (surahNumber, ayahs) ->
            val selected = ayahs.sortedBy { localAyah(surahNumber, it) }.take(5)
            if (selected.isNotEmpty()) {
                result += TodayTaskUi(
                    id = "weak:$surahNumber:${selected.first()}",
                    kind = TodayTaskKind.WEAK,
                    surahNumber = surahNumber,
                    surahName = nameFor(surahNumber),
                    startAyah = localAyah(surahNumber, selected.first()),
                    endAyah = localAyah(surahNumber, selected.last()),
                    ayahCount = selected.size,
                    ayahNumbers = selected
                )
            }
        }
    return result
}

private fun buildQuranRows(
    state: HomeUiState,
    language: AppLanguage,
): List<QuranSurahUi> {
    val completeBySurah = state.completeQuran.associateBy { it.surah.number }
    val todoBySurah = state.todoSurahs.associateBy { it.surahNumber }
    val ayahTodosBySurah = state.ayahTodos.groupBy { it.surahNumber }
    val dueBySurah = state.dueReviews.groupingBy { it.surahNumber }.eachCount()
    val weakBySurah = state.weakAyahKeys
        .mapNotNull { it.substringBefore(":").toIntOrNull() }
        .groupingBy { it }
        .eachCount()

    return state.surahList.map { surah ->
        val complete = completeBySurah[surah.surahNumber]
        val chapter = state.chapterNames[surah.surahNumber]
        val status = todoBySurah[surah.surahNumber]?.status
        val learned = ayahTodosBySurah[surah.surahNumber]
            .orEmpty()
            .count { it.status == AyahTodoStatus.LEARNED }
        val learning = ayahTodosBySurah[surah.surahNumber]
            .orEmpty()
            .any { it.status == AyahTodoStatus.LEARNING }
        val due = dueBySurah[surah.surahNumber] ?: 0
        val weak = weakBySurah[surah.surahNumber] ?: 0
        QuranSurahUi(
            surahNumber = surah.surahNumber,
            arabicName = chapter?.arabic
                ?: complete?.surah?.name
                ?: surah.name,
            transliteration = chapter?.transliteration
                ?: complete?.surah?.englishName
                ?: surah.name,
            translatedName = chapter?.translated
                ?: if (language == AppLanguage.RU) {
                    surah.name
                } else {
                    complete?.surah?.englishNameTranslation ?: surah.name
                },
            ayahCount = complete?.ayahs?.size ?: surah.ayats,
            learnedAyahs = learned,
            dueCount = due,
            weakCount = weak,
            memoryState = when {
                due > 0 -> QuranMemoryState.NEEDS_REVIEW
                status == SurahTodoStatus.LEARNED -> QuranMemoryState.MEMORIZED
                status == SurahTodoStatus.LEARNING || learning -> QuranMemoryState.LEARNING
                else -> QuranMemoryState.NEW
            },
            canOpen = complete != null
        )
    }
}

@Composable
private fun taskVisual(kind: TodayTaskKind): Pair<ImageVector, Color> {
    val colors = MaterialTheme.extendedColors
    return when (kind) {
        TodayTaskKind.NEW -> Icons.Filled.School to MaterialTheme.colors.primary
        TodayTaskKind.RECENT -> Icons.Filled.History to colors.info
        TodayTaskKind.OLD -> Icons.Filled.AutoStories to colors.warning
        TodayTaskKind.WEAK -> Icons.Filled.Warning to colors.danger
    }
}

private fun taskKindLabel(kind: TodayTaskKind, copy: HifzHomeCopy): String = when (kind) {
    TodayTaskKind.NEW -> copy.newHifz
    TodayTaskKind.RECENT -> copy.recentReview
    TodayTaskKind.OLD -> copy.oldReview
    TodayTaskKind.WEAK -> copy.weakSpots
}

private fun hifzHomeCopy(language: AppLanguage): HifzHomeCopy = when (language) {
    AppLanguage.RU -> HifzHomeCopy(
            today = "Сегодня",
            todaySubtitle = "Новый хифз, повторение и сложные места",
            focusedHifz = "Спокойный хифз",
            ready = "Готово к началу",
            inProgress = "В процессе",
            complete = "План выполнен",
            minutesToday = "минут сегодня",
            createPlan = "Создайте первый план хифза",
            createPlanBody = "Выберите суру и удобный ежедневный темп.",
            createPlanAction = "Выбрать в Коране",
            startToday = "Начать сегодня",
            startReview = "Начать повторение",
            newHifz = "Новый хифз",
            recentReview = "Недавнее повторение",
            oldReview = "Давнее повторение",
            weakSpots = "Сложные места",
            emptyNew = "Новых аятов на сегодня нет.",
            emptyRecent = "Недавних повторений нет.",
            emptyOld = "Давних повторений нет.",
            emptyWeak = "Сложных аятов пока нет.",
            quran = "Коран",
            quranSubtitle = "Выберите суру для хифза или повторения.",
            library = "Библиотека хифза",
            searchHint = "Найти суру или номер",
            reviewFilter = "Повторить",
            difficultFilter = "Сложные",
            noResults = "Ничего не найдено.",
            newState = "Новая",
            memorizedState = "Выучена",
            needsReviewState = "Нужно повторить",
            review = "Повторить",
            learn = "Учить",
            strong = "Уверенно",
            open = "Открыть",
            confirmStrongTitle = "Отметить суру выученной?",
            confirmStrongBody = "Все аяты этой суры будут отмечены как выученные.",
            confirm = "Отметить",
            cancel = "Отмена"
        )
    AppLanguage.TG -> HifzHomeCopy(
        today = "Имрӯз",
        todaySubtitle = "Ҳифзи нав, такрор ва ҷойҳои душвор",
        focusedHifz = "Ҳифзи ором",
        ready = "Омодаи оғоз",
        inProgress = "Дар ҷараён",
        complete = "Нақша иҷро шуд",
        minutesToday = "дақиқа имрӯз",
        createPlan = "Нақшаи аввалини ҳифзро созед",
        createPlanBody = "Сура ва суръати ҳаррӯзаи мувофиқро интихоб кунед.",
        createPlanAction = "Дар Қуръон интихоб кунед",
        startToday = "Имрӯз оғоз кунед",
        startReview = "Такрорро оғоз кунед",
        newHifz = "Ҳифзи нав",
        recentReview = "Такрори наздик",
        oldReview = "Такрори пешина",
        weakSpots = "Ҷойҳои душвор",
        emptyNew = "Барои имрӯз ояти нав нест.",
        emptyRecent = "Такрори наздик нест.",
        emptyOld = "Такрори пешина нест.",
        emptyWeak = "Ҳоло ояти душвор нест.",
        quran = "Қуръон",
        quranSubtitle = "Сураро барои ҳифз ё такрор интихоб кунед.",
        library = "Китобхонаи ҳифз",
        searchHint = "Сура ё рақамро ёбед",
        reviewFilter = "Такрор",
        difficultFilter = "Душвор",
        noResults = "Ҳеҷ чиз ёфт нашуд.",
        newState = "Нав",
        memorizedState = "Азёдшуда",
        needsReviewState = "Такрор лозим",
        review = "Такрор",
        learn = "Омӯзиш",
        strong = "Мустаҳкам",
        open = "Кушодан",
        confirmStrongTitle = "Сура азёдшуда ҳисоб шавад?",
        confirmStrongBody = "Ҳамаи оятҳои ин сура азёдшуда қайд мешаванд.",
        confirm = "Қайд кардан",
        cancel = "Бекор"
    )
    AppLanguage.UZ -> HifzHomeCopy(
        today = "Bugun",
        todaySubtitle = "Yangi hifz, takror va qiyin joylar",
        focusedHifz = "Sokin hifz",
        ready = "Boshlashga tayyor",
        inProgress = "Jarayonda",
        complete = "Reja bajarildi",
        minutesToday = "daqiqa bugun",
        createPlan = "Birinchi hifz rejangizni tuzing",
        createPlanBody = "Sura va qulay kunlik sur'atni tanlang.",
        createPlanAction = "Qur'ondan tanlash",
        startToday = "Bugun boshlash",
        startReview = "Takrorni boshlash",
        newHifz = "Yangi hifz",
        recentReview = "Yaqin takror",
        oldReview = "Eski takror",
        weakSpots = "Qiyin joylar",
        emptyNew = "Bugun uchun yangi oyat yo'q.",
        emptyRecent = "Yaqin takrorlar yo'q.",
        emptyOld = "Eski takrorlar yo'q.",
        emptyWeak = "Hozircha qiyin oyat yo'q.",
        quran = "Qur'on",
        quranSubtitle = "Hifz yoki takror uchun surani tanlang.",
        library = "Hifz kutubxonasi",
        searchHint = "Sura yoki raqamni qidiring",
        reviewFilter = "Takror",
        difficultFilter = "Qiyin",
        noResults = "Hech narsa topilmadi.",
        newState = "Yangi",
        memorizedState = "Yodlangan",
        needsReviewState = "Takror kerak",
        review = "Takror",
        learn = "O'rganish",
        strong = "Mustahkam",
        open = "Ochish",
        confirmStrongTitle = "Sura yodlangan deb belgilansinmi?",
        confirmStrongBody = "Bu suraning barcha oyatlari yodlangan deb belgilanadi.",
        confirm = "Belgilash",
        cancel = "Bekor qilish"
    )
    AppLanguage.TR -> HifzHomeCopy(
        today = "Bugün",
        todaySubtitle = "Yeni hıfz, tekrar ve zor yerler",
        focusedHifz = "Sakin hıfz",
        ready = "Başlamaya hazır",
        inProgress = "Devam ediyor",
        complete = "Plan tamamlandı",
        minutesToday = "dakika bugün",
        createPlan = "İlk hıfz planını oluştur",
        createPlanBody = "Bir sure ve uygun günlük tempo seç.",
        createPlanAction = "Kur'an'dan seç",
        startToday = "Bugün başla",
        startReview = "Tekrarı başlat",
        newHifz = "Yeni hıfz",
        recentReview = "Yakın tekrar",
        oldReview = "Eski tekrar",
        weakSpots = "Zor yerler",
        emptyNew = "Bugün için yeni ayet yok.",
        emptyRecent = "Yakın tekrar yok.",
        emptyOld = "Eski tekrar yok.",
        emptyWeak = "Henüz zor ayet yok.",
        quran = "Kur'an",
        quranSubtitle = "Hıfz veya tekrar için bir sure seç.",
        library = "Hıfz kütüphanesi",
        searchHint = "Sure veya numara ara",
        reviewFilter = "Tekrar",
        difficultFilter = "Zor",
        noResults = "Sonuç bulunamadı.",
        newState = "Yeni",
        memorizedState = "Ezberlendi",
        needsReviewState = "Tekrar gerekli",
        review = "Tekrar",
        learn = "Öğren",
        strong = "Sağlam",
        open = "Aç",
        confirmStrongTitle = "Sure ezberlendi olarak işaretlensin mi?",
        confirmStrongBody = "Bu surenin tüm ayetleri öğrenildi olarak işaretlenecek.",
        confirm = "İşaretle",
        cancel = "İptal"
    )
    AppLanguage.EN -> HifzHomeCopy(
        today = "Today",
        todaySubtitle = "New hifz, review, and weak spots",
        focusedHifz = "Focused hifz",
        ready = "Ready to begin",
        inProgress = "In progress",
        complete = "Plan complete",
        minutesToday = "minutes today",
        createPlan = "Create your first Hifz plan",
        createPlanBody = "Choose a surah and a calm daily pace.",
        createPlanAction = "Choose in Quran",
        startToday = "Start today",
        startReview = "Start review",
        newHifz = "New hifz",
        recentReview = "Recent review",
        oldReview = "Old review",
        weakSpots = "Weak spots",
        emptyNew = "No new ayahs planned.",
        emptyRecent = "No recent reviews due.",
        emptyOld = "No old reviews due.",
        emptyWeak = "No weak ayahs marked yet.",
        quran = "Quran",
        quranSubtitle = "Choose a surah for hifz or review.",
        library = "Memorization library",
        searchHint = "Search surah or number",
        reviewFilter = "Review",
        difficultFilter = "Difficult",
        noResults = "Nothing found.",
        newState = "New",
        memorizedState = "Memorized",
        needsReviewState = "Needs review",
        review = "Review",
        learn = "Learn",
        strong = "Strong",
        open = "Open",
        confirmStrongTitle = "Mark this surah memorized?",
        confirmStrongBody = "Every ayah in this surah will be marked as learned.",
        confirm = "Mark learned",
        cancel = "Cancel"
    )
}
