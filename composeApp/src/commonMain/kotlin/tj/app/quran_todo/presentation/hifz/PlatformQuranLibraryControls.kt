package tj.app.quran_todo.presentation.hifz

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

@Composable
internal expect fun PlatformQuranTopBar(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun PlatformQuranSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun PlatformQuranSegmentedControl(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun PlatformQuranSurahStatusButton(
    status: SurahTodoStatus?,
    statusTitle: String,
    notStartedLabel: String,
    learningLabel: String,
    learnedLabel: String,
    cancelLabel: String,
    onStatusSelected: (SurahTodoStatus?) -> Unit,
    modifier: Modifier = Modifier,
)
