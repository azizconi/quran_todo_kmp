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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.surah.SurahScreen

@OptIn(ExperimentalFoundationApi::class, KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
) {

    val surahList = remember { mutableStateOf<List<SurahModel>>(emptyList()) }

    val selectedSurahList = remember { mutableStateListOf<SurahModel>() }

    val todoSurahList = viewModel.surahList.collectAsState(emptyList())

    val filterParam = remember {
        mutableStateOf<SurahTodoStatus?>(null)
    }

    fun save(status: SurahTodoStatus) {
        selectedSurahList.forEach {
            viewModel.upsertSurahToTodo(it.toEntity(status))
        }
        selectedSurahList.clear()
    }

    LaunchedEffect(Unit) {
        viewModel.getCompleteQuran()
        surahList.value = parseSurahList()
    }

    val selectedSurah = remember { mutableStateOf<SurahWithAyahs?>(null) }
    val completeQuran = remember { mutableStateOf<List<SurahWithAyahs>>(emptyList()) }

    LaunchedEffect(viewModel.completeQuranResult.value) {
        when (val result = viewModel.completeQuranResult.value) {
            is Resource.Success -> {
                completeQuran.value = result.data
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Quran Todo")
                },
                actions = {
                    if (selectedSurahList.isNotEmpty()) {
                        val isShowDropDown = remember { mutableStateOf(false) }

                        Text(
                            text = "Выбрано: ${selectedSurahList.size}",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable {
                                    isShowDropDown.value = true
                                },
                            fontSize = 14.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )

                        if (isShowDropDown.value) {
                            DropdownMenu(
                                expanded = isShowDropDown.value,
                                onDismissRequest = {
                                    isShowDropDown.value = false
                                }
                            ) {
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    save(SurahTodoStatus.LEARNED)
                                }) {
                                    Text("Выучил", fontWeight = FontWeight.Bold)
                                }
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    save(SurahTodoStatus.LEARNING)
                                }) {
                                    Text("Заучиваю", fontWeight = FontWeight.Bold)
                                }
                                DropdownMenuItem(
                                    onClick = {
                                        isShowDropDown.value = false
                                        selectedSurahList.clear()
                                    }
                                ) {
                                    Text("Сбросить выбранные", color = Color.Red)
                                }
                            }
                        }
                    } else {
                        val isShowDropDown = remember { mutableStateOf(false) }

                        Text(
                            text = "Фильтр",
                            modifier = Modifier.padding(end = 16.dp).clickable {
                                isShowDropDown.value = true
                            },
                            fontSize = 14.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )

                        if (isShowDropDown.value) {
                            DropdownMenu(
                                expanded = isShowDropDown.value,
                                onDismissRequest = {
                                    isShowDropDown.value = false
                                }
                            ) {
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    filterParam.value = null
                                }) {
                                    Text(
                                        "Все",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    filterParam.value = SurahTodoStatus.LEARNED
                                }) {
                                    Text(
                                        "Выучил: ${todoSurahList.value.filter { it.status == SurahTodoStatus.LEARNED }.size}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    filterParam.value = SurahTodoStatus.LEARNING
                                }) {
                                    Text(
                                        "Заучиваю: ${todoSurahList.value.filter { it.status == SurahTodoStatus.LEARNING }.size}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                backgroundColor = Color.White,
                elevation = 0.dp,
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) {

        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {

            itemsIndexed(
                items = if (filterParam.value == null) surahList.value else
                    surahList.value.filter { element ->
                        todoSurahList.value.find {
                            it.surahNumber == element.surahNumber && it.status == filterParam.value
                        } != null
                    },
            ) { index, surah ->
                val isSelected =
                    selectedSurahList.find { it.surahNumber == surah.surahNumber } != null

                val inTodoSaved =
                    todoSurahList.value.find { it.surahNumber == surah.surahNumber }

                val selectedColor = when {
                    inTodoSaved?.status == SurahTodoStatus.LEARNED -> Color.Red
                    inTodoSaved?.status == SurahTodoStatus.LEARNING -> Color.Red.copy(.3f)
                    isSelected -> Color.Red.copy(.6f)
                    else -> Color.White
                }

                val isShowDropDown = remember { mutableStateOf(false) }

                if (index == 0) Divider(color = selectedColor)

                Box {

                    SurahItem(
                        surahNumber = surah.surahNumber,
                        name = surah.name,
                        ayats = surah.ayats,
                        revelationOrder = surah.revelationOrder,
                        revelationPlace = surah.revelationPlace,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (selectedSurahList.isNotEmpty() && inTodoSaved == null) {
                                        if (isSelected) selectedSurahList.remove(surah)
                                        else selectedSurahList.add(surah)
                                    } else if (completeQuran.value.isNotEmpty()) {
                                        selectedSurah.value = completeQuran.value.find {
                                            it.surah.number == surah.surahNumber
                                        }
                                        println("selected surah = ${selectedSurah.value}")
                                    }
                                    println("completeQuran.value = ${completeQuran.value}")
                                },
                                onLongClick = {
                                    if (inTodoSaved != null) {
                                        isShowDropDown.value = true
                                    }
                                },
                                onDoubleClick = {
                                    if (inTodoSaved == null && !isSelected) {
                                        selectedSurahList.add(surah)
                                    }
                                }
                            )
                            .background(selectedColor)
                    )
                    DropdownMenu(
                        expanded = isShowDropDown.value,
                        onDismissRequest = {
                            isShowDropDown.value = false
                        }
                    ) {
                        when (inTodoSaved?.status) {
                            SurahTodoStatus.LEARNED -> {
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    viewModel.upsertSurahToTodo(inTodoSaved.copy(status = SurahTodoStatus.LEARNING))
                                }) {
                                    Text("Заучиваю", fontWeight = FontWeight.Bold)
                                }

                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    viewModel.deleteSurahFromTodo(inTodoSaved)
                                }) {
                                    Text(
                                        "Сбросить",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                }
                            }

                            SurahTodoStatus.LEARNING -> {
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    viewModel.upsertSurahToTodo(inTodoSaved.copy(status = SurahTodoStatus.LEARNED))
                                }) {
                                    Text("Выучил", fontWeight = FontWeight.Bold)
                                }
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                    viewModel.deleteSurahFromTodo(inTodoSaved)
                                }) {
                                    Text(
                                        "Сбросить",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red
                                    )
                                }
                            }

                            null -> {
                                DropdownMenuItem(onClick = {
                                    isShowDropDown.value = false
                                }) {
                                    Text("Отмена", fontWeight = FontWeight.Bold)
                                }
                            }
                        }


                    }
                }

                if (index < surahList.value.size - 1) Divider(color = selectedColor)
            }

        }
    }


    selectedSurah.value?.let { surah ->
        Dialog(
            onDismissRequest = { selectedSurah.value = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SurahScreen(
                surah,
                onDismiss = { selectedSurah.value = null }
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
) {
    Column(modifier = Modifier.then(modifier).padding(16.dp)) {
        // Верхняя строка: Номер суры и место ниспослания
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

        Spacer(modifier = Modifier.height(8.dp))

        // Название суры
        Text(
            text = name,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Количество аятов и порядок ниспослания
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