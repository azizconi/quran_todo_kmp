package tj.app.quran_todo.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.json.surahJson
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.domain.model.SurahModel

@Composable
fun HomeScreen() {

    val surahList = remember { mutableStateOf<List<SurahModel>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Здесь можно выполнить асинхронные операции, например, загрузку данных
        surahList.value = parseSurahList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Quran Todo")
                },
                backgroundColor = Color.White,
                elevation = 2.dp
            )
        }
    ) {

//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 8.dp),
//            elevation = 4.dp,
//            shape = RoundedCornerShape(16.dp)
//        ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
            itemsIndexed(surahList.value) { index, surah ->
                SurahItem(
                    surahNumber = surah.surahNumber,
                    name = surah.name,
                    ayats = surah.ayats,
                    revelationOrder = surah.revelationOrder,
                    revelationPlace = surah.revelationPlace
                )
                if (index < surahList.value.size - 1) {
                    Divider()
                }

            }

        }
//        }


    }

}

@Composable
fun SurahItem(
    surahNumber: Int,
    name: String,
    ayats: Int,
    revelationPlace: String,
    revelationOrder: Int,
) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 8.dp),
//        elevation = 4.dp,
//        shape = RoundedCornerShape(16.dp)
//    ) {
    Column(modifier = Modifier.padding(16.dp)) {
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
//        }
    }
}