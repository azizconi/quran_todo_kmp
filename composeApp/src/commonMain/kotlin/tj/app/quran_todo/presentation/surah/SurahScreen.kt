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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.localizeRevelationPlace
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
    val totalAyahs = surahWithAyahs.ayahs.size
    val surahNumber = surahWithAyahs.surah.number

    LaunchedEffect(language, surahWithAyahs.surah.number) {
        viewModel.loadTranslations(surahNumber, language)
        viewModel.loadChapterNames(language)
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
            modifier = Modifier.fillMaxSize()
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
            items(surahWithAyahs.ayahs, key = { it.number }) { ayah ->
                val status = todoByAyah[ayah.number]?.status
                val translation = translations[ayah.number]
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AyahCard(
    ayah: AyahEntity,
    status: AyahTodoStatus?,
    statusLabel: String?,
    clearLabel: String,
    translation: String?,
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
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                }
            }
        }
    )
}
