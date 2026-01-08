package tj.app.quran_todo.presentation

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.presentation.surah.SurahScreen

@OptIn(ExperimentalFoundationApi::class, KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val todoByNumber = uiState.todoSurahs.associateBy { it.surahNumber }
    val selectionMode = uiState.selectedSurahNumbers.isNotEmpty()

    val filteredSurahs = if (uiState.filter == null) {
        uiState.surahList
    } else {
        uiState.surahList.filter { element ->
            todoByNumber[element.surahNumber]?.status == uiState.filter
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (selectionMode) {
                        "Выбрано: ${uiState.selectedSurahNumbers.size}"
                    } else {
                        "Quran Todo"
                    }
                    Text(title)
                },
                actions = {
                    if (selectionMode) {
                        var isShowDropDown by remember { mutableStateOf(false) }

                        Text(
                            text = "Действия",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { isShowDropDown = true },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        DropdownMenu(
                            expanded = isShowDropDown,
                            onDismissRequest = { isShowDropDown = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                isShowDropDown = false
                                viewModel.markSelected(SurahTodoStatus.LEARNED)
                            }) {
                                Text("Выучил", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenuItem(onClick = {
                                isShowDropDown = false
                                viewModel.markSelected(SurahTodoStatus.LEARNING)
                            }) {
                                Text("Заучиваю", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenuItem(
                                onClick = {
                                    isShowDropDown = false
                                    viewModel.clearSelection()
                                }
                            ) {
                                Text("Сбросить выбранные", color = MaterialTheme.colors.error)
                            }
                        }
                    } else {
                        var isShowDropDown by remember { mutableStateOf(false) }

                        Text(
                            text = "Фильтр",
                            modifier = Modifier.padding(end = 16.dp).clickable {
                                isShowDropDown = true
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val learnedCount =
                            uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNED }
                        val learningCount =
                            uiState.todoSurahs.count { it.status == SurahTodoStatus.LEARNING }

                        DropdownMenu(
                            expanded = isShowDropDown,
                            onDismissRequest = { isShowDropDown = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                isShowDropDown = false
                                viewModel.setFilter(null)
                            }) {
                                Text("Все", fontWeight = FontWeight.Bold)
                            }

                            DropdownMenuItem(
                                onClick = {
                                    isShowDropDown = false
                                    viewModel.setFilter(SurahTodoStatus.LEARNED)
                                }
                            ) {
                                Text("Выучил: $learnedCount", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenuItem(
                                onClick = {
                                    isShowDropDown = false
                                    viewModel.setFilter(SurahTodoStatus.LEARNING)
                                }
                            ) {
                                Text("Заучиваю: $learningCount", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp
            )
        }
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
            if (uiState.isLoadingQuran) {
                item {
                    Text(
                        text = "Загрузка полного Корана...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Text(
                        text = uiState.errorMessage ?: "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

            itemsIndexed(
                items = filteredSurahs,
                key = { _, surah -> surah.surahNumber }
            ) { index, surah ->
                val isSelected = uiState.selectedSurahNumbers.contains(surah.surahNumber)
                val inTodoSaved = todoByNumber[surah.surahNumber]
                val selectedColor = MaterialTheme.colors.primary.copy(alpha = 0.14f)
                val learnedColor = MaterialTheme.colors.secondary.copy(alpha = 0.22f)
                val learningColor = MaterialTheme.colors.secondary.copy(alpha = 0.12f)
                val backgroundColor = when {
                    isSelected -> selectedColor
                    inTodoSaved?.status == SurahTodoStatus.LEARNED -> learnedColor
                    inTodoSaved?.status == SurahTodoStatus.LEARNING -> learningColor
                    else -> MaterialTheme.colors.surface
                }

                var isItemMenuOpen by remember { mutableStateOf(false) }

                if (index == 0) Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))

                Box {
                    SurahItem(
                        surahNumber = surah.surahNumber,
                        name = surah.name,
                        ayats = surah.ayats,
                        revelationOrder = surah.revelationOrder,
                        revelationPlace = surah.revelationPlace,
                        statusLabel = when (inTodoSaved?.status) {
                            SurahTodoStatus.LEARNED -> "Выучил"
                            SurahTodoStatus.LEARNING -> "Заучиваю"
                            null -> null
                        },
                        selected = isSelected,
                        onStatusClick = if (inTodoSaved != null) {
                            { isItemMenuOpen = true }
                        } else {
                            null
                        },
                        modifier = Modifier
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
                            .background(backgroundColor)
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
                                    viewModel.setSurahStatus(
                                        inTodoSaved.surahNumber,
                                        SurahTodoStatus.LEARNING
                                    )
                                }) {
                                    Text("Заучиваю", fontWeight = FontWeight.Bold)
                                }

                                DropdownMenuItem(onClick = {
                                    isItemMenuOpen = false
                                    viewModel.setSurahStatus(inTodoSaved.surahNumber, null)
                                }) {
                                    Text(
                                        "Сбросить",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colors.error
                                    )
                                }
                            }

                            SurahTodoStatus.LEARNING -> {
                                DropdownMenuItem(onClick = {
                                    isItemMenuOpen = false
                                    viewModel.setSurahStatus(
                                        inTodoSaved.surahNumber,
                                        SurahTodoStatus.LEARNED
                                    )
                                }) {
                                    Text("Выучил", fontWeight = FontWeight.Bold)
                                }
                                DropdownMenuItem(onClick = {
                                    isItemMenuOpen = false
                                    viewModel.setSurahStatus(inTodoSaved.surahNumber, null)
                                }) {
                                    Text(
                                        "Сбросить",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colors.error
                                    )
                                }
                            }

                            null -> {
                                DropdownMenuItem(onClick = {
                                    isItemMenuOpen = false
                                }) {
                                    Text("Отмена", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (index < filteredSurahs.size - 1) {
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
                }
            }
        }
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
fun SurahItem(
    modifier: Modifier,
    surahNumber: Int,
    name: String,
    ayats: Int,
    revelationPlace: String,
    revelationOrder: Int,
    statusLabel: String?,
    selected: Boolean,
    onStatusClick: (() -> Unit)?,
) {
    Column(modifier = Modifier.then(modifier).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Сура №$surahNumber",
                style = MaterialTheme.typography.body1
            )
            Text(
                text = revelationPlace,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }

        if (statusLabel != null && onStatusClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Статус: $statusLabel",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.clickable { onStatusClick() }
            )
        } else if (selected) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Выбрано",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Аятов: $ayats",
                style = MaterialTheme.typography.subtitle2
            )
            Text(
                text = "Ниспослано #$revelationOrder",
                style = MaterialTheme.typography.subtitle2
            )
        }
    }
}
