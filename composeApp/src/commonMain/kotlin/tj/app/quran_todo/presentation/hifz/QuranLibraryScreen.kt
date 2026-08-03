package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.presentation.designsystem.platformBottomSystemBarsPadding

private enum class QuranLibraryFilter {
    ALL,
    LEARNING,
    LEARNED,
}

private data class QuranLibraryRow(
    val surahNumber: Int,
    val displayName: String,
    val translatedName: String,
    val arabicName: String,
    val ayahCount: Int,
    val status: SurahTodoStatus?,
    val canOpen: Boolean,
)

private data class ContinueReadingUi(
    val row: QuranLibraryRow,
    val ayahNumberInSurah: Int,
)

private data class QuranLibraryCopy(
    val quran: String,
    val searchHint: String,
    val continueReading: String,
    val all: String,
    val learning: String,
    val learned: String,
    val ayah: String,
    val ayahs: String,
    val noResults: String,
    val notStarted: String,
    val status: String,
    val cancel: String,
    val surahs: String,
)

@OptIn(KoinExperimentalAPI::class)
@Composable
internal fun QuranLibraryRoute(
    viewModel: HomeViewModel = koinViewModel(),
    onOpenSurah: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val state by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current
    val copy = remember(language) { quranLibraryCopy(language) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(QuranLibraryFilter.ALL.name) }
    val filter = QuranLibraryFilter.entries.firstOrNull { it.name == filterName }
        ?: QuranLibraryFilter.ALL

    LaunchedEffect(language) {
        viewModel.loadChapterNames(language)
    }
    LaunchedEffect(Unit) {
        viewModel.refreshDueReviewsNow()
    }

    val allRows = remember(
        state.surahList,
        state.completeQuran,
        state.chapterNames,
        state.todoSurahs,
        state.pendingSurahStatuses,
        language,
    ) {
        buildLibraryRows(state, language)
    }
    val visibleRows = remember(allRows, query, filter) {
        val normalizedQuery = query.trim().lowercase()
        allRows.filter { row ->
            val matchesSearch = normalizedQuery.isEmpty() ||
                row.surahNumber.toString() == normalizedQuery ||
                row.displayName.lowercase().contains(normalizedQuery) ||
                row.translatedName.lowercase().contains(normalizedQuery) ||
                row.arabicName.contains(normalizedQuery)
            val matchesFilter = when (filter) {
                QuranLibraryFilter.ALL -> true
                QuranLibraryFilter.LEARNING -> row.status == SurahTodoStatus.LEARNING
                QuranLibraryFilter.LEARNED -> row.status == SurahTodoStatus.LEARNED
            }
            matchesSearch && matchesFilter
        }
    }
    val continueReading = buildContinueReading(allRows, state.completeQuran)

    QuranLibraryContent(
        copy = copy,
        query = query,
        onQueryChange = { query = it },
        filter = filter,
        onFilterSelected = { filterName = it.name },
        continueReading = continueReading,
        visibleRows = visibleRows,
        isInitialLoading = state.quranLoadState is QuranLoadState.Loading && state.completeQuran.isEmpty(),
        initialLoadError = (state.quranLoadState as? QuranLoadState.Error)
            ?.takeIf { state.completeQuran.isEmpty() }
            ?.message,
        loadErrorTitle = strings.homeLoadErrorTitle,
        retryLabel = strings.homeRetryLabel,
        onRetry = { viewModel.refreshQuran(withLocalAction = false) },
        onContinueReading = {
            continueReading?.row
                ?.takeIf { it.canOpen }
                ?.let { onOpenSurah(it.surahNumber) }
        },
        onSurahSelected = { row ->
            if (row.canOpen) onOpenSurah(row.surahNumber)
        },
        onStatusSelected = { row, status ->
            viewModel.setSurahStatus(row.surahNumber, status)
        },
        statusUpdateError = state.statusUpdateError,
        onDismissStatusUpdateError = viewModel::clearStatusUpdateError,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun QuranLibraryContent(
    copy: QuranLibraryCopy,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: QuranLibraryFilter,
    onFilterSelected: (QuranLibraryFilter) -> Unit,
    continueReading: ContinueReadingUi?,
    visibleRows: List<QuranLibraryRow>,
    isInitialLoading: Boolean,
    initialLoadError: String?,
    loadErrorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onContinueReading: () -> Unit,
    onSurahSelected: (QuranLibraryRow) -> Unit,
    onStatusSelected: (QuranLibraryRow, SurahTodoStatus?) -> Unit,
    statusUpdateError: String?,
    onDismissStatusUpdateError: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val showContinueReading =
        continueReading != null && query.isBlank() && filter == QuranLibraryFilter.ALL
    val listState = rememberLazyListState()
    LaunchedEffect(statusUpdateError) {
        if (statusUpdateError != null) {
            delay(4_000)
            onDismissStatusUpdateError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlatformQuranTopBar(
                title = copy.quran,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )

            QuranSearchField(
                value = query,
                placeholder = copy.searchHint,
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            statusUpdateError?.let { error ->
                QuranStatusUpdateError(
                    message = error,
                    onDismiss = onDismissStatusUpdateError,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .platformBottomSystemBarsPadding(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = 16.dp,
                ),
            ) {
                if (isInitialLoading) {
                    item(key = "library-skeleton") {
                        QuranLibrarySkeleton(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                } else if (initialLoadError != null) {
                    item(key = "library-load-error") {
                        QuranLibraryLoadError(
                            title = loadErrorTitle,
                            message = initialLoadError,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 64.dp),
                        )
                    }
                } else {
                    if (showContinueReading) {
                        item(key = "continue-reading") {
                            ContinueReadingCard(
                                item = continueReading,
                                copy = copy,
                                onClick = onContinueReading,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                            )
                        }
                    }

                    stickyHeader(key = "filters") {
                        // This is a physical pinned shelf, not just a background painted behind
                        // the tabs. Native UIKit controls in a row are separate views, so the
                        // shelf must be opaque and explicitly above the scrolling content.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1f),
                            color = MaterialTheme.colors.background,
                            elevation = 0.dp,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (showContinueReading) 6.dp else 4.dp),
                            ) {
                                QuranSegmentedControl(
                                    selected = filter,
                                    copy = copy,
                                    onSelected = onFilterSelected,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(1f),
                                )
                            }
                        }
                    }

                    item(key = "surah-count") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = copy.surahs.uppercase(),
                                color = MaterialTheme.extendedColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = visibleRows.size.toString(),
                                color = MaterialTheme.extendedColors.textTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    when {
                        visibleRows.isEmpty() -> item {
                            Text(
                                text = copy.noResults,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 56.dp),
                                color = MaterialTheme.extendedColors.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                        }

                        else -> itemsIndexed(
                            items = visibleRows,
                            key = { _, row -> row.surahNumber },
                        ) { index, row ->
                            QuranLibrarySurahRow(
                                row = row,
                                copy = copy,
                                isFirst = index == 0,
                                isLast = index == visibleRows.lastIndex,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onClick = { onSurahSelected(row) },
                                onStatusSelected = { status -> onStatusSelected(row, status) },
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun QuranStatusUpdateError(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.danger.copy(alpha = 0.12f),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.danger,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "×",
                color = colors.danger,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .adaptiveClickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun QuranLibrarySkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "quran-library-shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quran-library-shimmer-phase",
    )
    val colors = MaterialTheme.extendedColors
    val shimmer = Brush.linearGradient(
        colors = listOf(colors.surfaceAlt, MaterialTheme.colors.surface, colors.surfaceAlt),
        start = androidx.compose.ui.geometry.Offset(phase * 600f, 0f),
        end = androidx.compose.ui.geometry.Offset((phase + 1f) * 600f, 280f),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(shimmer),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .heightIn(min = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmer),
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .heightIn(min = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmer),
            )
        }
        repeat(7) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(shimmer),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.58f else 0.7f)
                            .heightIn(min = 14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(shimmer),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.34f)
                            .heightIn(min = 11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmer),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .heightIn(min = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmer),
                )
            }
        }
    }
}

@Composable
private fun QuranLibraryLoadError(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = message,
            color = MaterialTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
        Text(
            text = retryLabel,
            color = MaterialTheme.colors.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .adaptiveClickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun QuranSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformQuranSearchField(
        value = value,
        placeholder = placeholder,
        onValueChange = onValueChange,
        modifier = modifier,
    )
}

@Composable
private fun ContinueReadingCard(
    item: ContinueReadingUi,
    copy: QuranLibraryCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extendedColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .adaptiveClickable(
                enabled = item.row.canOpen,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = colors.primaryWeak,
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colors.surface.copy(alpha = 0.86f),
                elevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = copy.continueReading.uppercase(),
                    color = MaterialTheme.colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    text = item.row.displayName,
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${copy.ayah} ${item.ayahNumberInSurah}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = MaterialTheme.colors.surface.copy(alpha = 0.72f),
                elevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranSegmentedControl(
    selected: QuranLibraryFilter,
    copy: QuranLibraryCopy,
    onSelected: (QuranLibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        QuranLibraryFilter.ALL to copy.all,
        QuranLibraryFilter.LEARNING to copy.learning,
        QuranLibraryFilter.LEARNED to copy.learned,
    )
    PlatformQuranSegmentedControl(
        selectedIndex = entries.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        labels = entries.map { it.second },
        onSelected = { index ->
            entries.getOrNull(index)?.first?.let(onSelected)
        },
        modifier = modifier,
    )
}

@Composable
private fun QuranLibrarySurahRow(
    row: QuranLibraryRow,
    copy: QuranLibraryCopy,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onStatusSelected: (SurahTodoStatus?) -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val quranFont = getQuranFontFamily()
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 18.dp else 0.dp,
        topEnd = if (isFirst) 18.dp else 0.dp,
        bottomStart = if (isLast) 18.dp else 0.dp,
        bottomEnd = if (isLast) 18.dp else 0.dp,
    )
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colors.surface,
        elevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 82.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .adaptiveClickable(
                            enabled = row.canOpen,
                            shape = shape,
                            onClick = onClick,
                        )
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = colors.surfaceAlt,
                        elevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = row.surahNumber.toString(),
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = row.displayName,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.arabicName,
                                color = MaterialTheme.colors.primary,
                                fontFamily = quranFont,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${row.ayahCount} ${copy.ayahs}",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
                PlatformQuranSurahStatusButton(
                    status = row.status,
                    statusTitle = copy.status,
                    notStartedLabel = copy.notStarted,
                    learningLabel = copy.learning,
                    learnedLabel = copy.learned,
                    cancelLabel = copy.cancel,
                    onStatusSelected = onStatusSelected,
                    modifier = Modifier.padding(start = 6.dp, end = 10.dp),
                )
            }
            if (!isLast) {
                Divider(
                    modifier = Modifier.padding(start = 57.dp),
                    color = colors.border,
                    thickness = 1.dp,
                )
            }
        }
    }
}

private fun buildLibraryRows(
    state: HomeUiState,
    language: AppLanguage,
): List<QuranLibraryRow> {
    val statusBySurah = state.todoSurahs.associate { it.surahNumber to it.status }
    val pendingStatusBySurah = state.pendingSurahStatuses
    val fallbackNames = state.surahList.associateBy { it.surahNumber }

    fun displayedStatus(surahNumber: Int): SurahTodoStatus? = if (
        pendingStatusBySurah.containsKey(surahNumber)
    ) {
        pendingStatusBySurah.getValue(surahNumber).status
    } else {
        statusBySurah[surahNumber]
    }

    if (state.completeQuran.isNotEmpty()) {
        return state.completeQuran
            .sortedBy { it.surah.number }
            .map { complete ->
                val number = complete.surah.number
                val fallbackName = fallbackNames[number]
                    ?.name
                    ?.replaceFirstChar { it.uppercase() }
                    ?: complete.surah.englishName
                val chapter = state.chapterNames[number]
                QuranLibraryRow(
                    surahNumber = number,
                    displayName = if (language == AppLanguage.RU) {
                        fallbackName
                    } else {
                        chapter?.transliteration ?: complete.surah.englishName
                    },
                    translatedName = chapter?.translated
                        ?: complete.surah.englishNameTranslation
                        ?: fallbackName,
                    arabicName = chapter?.arabic ?: complete.surah.name,
                    ayahCount = complete.ayahs.size,
                    status = displayedStatus(number),
                    canOpen = true,
                )
            }
    }

    return state.surahList.map { surah ->
        val chapter = state.chapterNames[surah.surahNumber]
        val fallbackName = surah.name.replaceFirstChar { it.uppercase() }
        QuranLibraryRow(
            surahNumber = surah.surahNumber,
            displayName = if (language == AppLanguage.RU) {
                fallbackName
            } else {
                chapter?.transliteration ?: fallbackName
            },
            translatedName = chapter?.translated ?: fallbackName,
            arabicName = chapter?.arabic.orEmpty(),
            ayahCount = surah.ayats,
            status = displayedStatus(surah.surahNumber),
            canOpen = false,
        )
    }
}

private fun buildContinueReading(
    rows: List<QuranLibraryRow>,
    completeQuran: List<tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs>,
): ContinueReadingUi? {
    val lastPlaybackSurah = UserSettingsStorage.getLastPlaybackSurah()
    val lastPlaybackAyah = UserSettingsStorage.getLastPlaybackAyahNumber()
    val preferredSurah = lastPlaybackSurah
        ?: rows.firstOrNull { it.status == SurahTodoStatus.LEARNING }?.surahNumber
        ?: rows.firstOrNull { it.canOpen }?.surahNumber
        ?: return null
    val row = rows.firstOrNull { it.surahNumber == preferredSurah && it.canOpen }
        ?: return null
    val ayahNumberInSurah = lastPlaybackAyah
        ?.takeIf { lastPlaybackSurah == preferredSurah }
        ?.let { ayahGlobalNumber ->
            completeQuran
                .firstOrNull { it.surah.number == preferredSurah }
                ?.ayahs
                ?.firstOrNull { it.number == ayahGlobalNumber }
                ?.numberInSurah
        }
        ?: 1
    return ContinueReadingUi(row = row, ayahNumberInSurah = ayahNumberInSurah)
}

private fun quranLibraryCopy(language: AppLanguage): QuranLibraryCopy = when (language) {
    AppLanguage.RU -> QuranLibraryCopy(
        quran = "Коран",
        searchHint = "Поиск по суре или аяту",
        continueReading = "Продолжить чтение",
        all = "Все",
        learning = "Заучиваю",
        learned = "Выучено",
        ayah = "Аят",
        ayahs = "аятов",
        noResults = "Суры не найдены",
        notStarted = "Не начал",
        status = "Статус суры",
        cancel = "Отмена",
        surahs = "Суры",
    )

    AppLanguage.TG -> QuranLibraryCopy(
        quran = "Қуръон",
        searchHint = "Ҷустуҷӯи сура ё оят",
        continueReading = "Идомаи қироат",
        all = "Ҳама",
        learning = "Аз бар мекунам",
        learned = "Аз бар шуд",
        ayah = "Оят",
        ayahs = "оят",
        noResults = "Сура ёфт нашуд",
        notStarted = "Оғоз нашудааст",
        status = "Ҳолати сура",
        cancel = "Бекор",
        surahs = "Сураҳо",
    )

    AppLanguage.UZ -> QuranLibraryCopy(
        quran = "Qur'on",
        searchHint = "Sura yoki oyat qidirish",
        continueReading = "O'qishni davom ettirish",
        all = "Barchasi",
        learning = "Yodlayapman",
        learned = "Yodlangan",
        ayah = "Oyat",
        ayahs = "oyat",
        noResults = "Sura topilmadi",
        notStarted = "Boshlanmagan",
        status = "Sura holati",
        cancel = "Bekor qilish",
        surahs = "Suralar",
    )

    AppLanguage.TR -> QuranLibraryCopy(
        quran = "Kur'an",
        searchHint = "Sure veya ayet ara",
        continueReading = "Okumaya devam et",
        all = "Tümü",
        learning = "Ezberliyorum",
        learned = "Ezberlendi",
        ayah = "Ayet",
        ayahs = "ayet",
        noResults = "Sure bulunamadı",
        notStarted = "Başlanmadı",
        status = "Sure durumu",
        cancel = "İptal",
        surahs = "Sureler",
    )

    AppLanguage.EN -> QuranLibraryCopy(
        quran = "Quran",
        searchHint = "Search surah or ayah",
        continueReading = "Continue reading",
        all = "All",
        learning = "Learning",
        learned = "Learned",
        ayah = "Ayah",
        ayahs = "ayahs",
        noResults = "No surahs found",
        notStarted = "Not started",
        status = "Surah status",
        cancel = "Cancel",
        surahs = "Surahs",
    )
}
