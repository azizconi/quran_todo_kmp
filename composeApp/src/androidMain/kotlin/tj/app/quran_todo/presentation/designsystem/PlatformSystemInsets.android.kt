package tj.app.quran_todo.presentation.designsystem

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.platformBottomSystemBarsPadding(): Modifier =
    navigationBarsPadding()
