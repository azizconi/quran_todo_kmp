package tj.app.quran_todo.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.theme.QhTheme

/** Today's lesson (docs/redesign/PRODUCT_PLAN.md, 3.3). Placeholder until task B2 builds it. */
@Composable
fun LessonScreen(onBack: () -> Unit, onOpenReader: (Dest.Reader) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(QhTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(title = "…", actionLabel = "←", onAction = onBack)
    }
}
