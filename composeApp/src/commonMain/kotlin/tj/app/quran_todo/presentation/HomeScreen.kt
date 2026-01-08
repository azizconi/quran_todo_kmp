package tj.app.quran_todo.presentation

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
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.daysBetween
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.presentation.surah.SurahScreen

@OptIn(ExperimentalFoundationApi::class, KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val settings = LocalAppSettings.current

    val uiState by viewModel.uiState.collectAsState()
    val todoByNumber = uiState.todoSurahs.associateBy { it.surahNumber }
    val selectionMode = uiState.selectedSurahNumbers.isNotEmpty()

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
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

    val filteredSurahs = if (uiState.filter == null) {
        uiState.surahList
    } else {
        uiState.surahList.filter { element ->
            todoByNumber[element.surahNumber]?.status == uiState.filter
        }
    }

    val learnedCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNED }
    val learningCount = uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNING }

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
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
                    dailyGoal = settings.dailyGoal
                )
            }

            item {
                DueReviewsCard(
                    title = strings.reviewDueTitle,
                    emptyLabel = strings.reviewEmptyLabel,
                    actionLabel = strings.reviewNowLabel,
                    dueReviews = uiState.dueReviews.take(3),
                    onComplete = { ayahNumber, surahNumber ->
                        viewModel.completeReview(ayahNumber, surahNumber)
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
                FilterRow(
                    strings = strings,
                    learnedCount = learnedCount,
                    learningCount = learningCount,
                    filter = uiState.filter,
                    onFilterChange = viewModel::setFilter
                )
            }

            itemsIndexed(
                items = filteredSurahs,
                key = { _, surah -> surah.surahNumber }
            ) { index, surah ->
                val isSelected = uiState.selectedSurahNumbers.contains(surah.surahNumber)
                val inTodoSaved = todoByNumber[surah.surahNumber]

                val backgroundColor = when {
                    isSelected -> MaterialTheme.colors.primary//.copy(alpha = 0.12f)
                    inTodoSaved?.status == SurahTodoStatus.LEARNED -> MaterialTheme.colors.secondary//.copy(alpha = 0.18f)
                    inTodoSaved?.status == SurahTodoStatus.LEARNING -> MaterialTheme.colors.secondary//.copy(alpha = 0.12f)
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
                                    tj.app.quran_todo.common.i18n.AppLanguage.RU -> surah.name
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
                                null -> MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
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
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
) {
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
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
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
    actionLabel: String,
    dueReviews: List<AyahReviewEntity>,
    onComplete: (Int, Int) -> Unit,
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            } else {
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
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.clickable {
                                onComplete(review.ayahNumber, review.surahNumber)
                            }
                        )
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
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
                color = if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(enabled = enabled) { onClick() }
            ) {
                Text(
                    text = actionLabel,
                    color = if (enabled) Color.White else MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    strings: tj.app.quran_todo.common.i18n.AppStrings,
    learnedCount: Int,
    learningCount: Int,
    filter: SurahTodoStatus?,
    onFilterChange: (SurahTodoStatus?) -> Unit,
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
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.15f) else MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
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
        color = color.copy(alpha = 0.12f),
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
    strings: tj.app.quran_todo.common.i18n.AppStrings,
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
                color = statusColor.copy(alpha = 0.12f),
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
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (!translatedName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = translatedName,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
