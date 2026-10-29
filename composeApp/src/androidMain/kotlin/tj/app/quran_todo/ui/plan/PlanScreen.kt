package tj.app.quran_todo.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.theme.QhTheme

/** "My plan" (docs/redesign/PRODUCT_PLAN.md, 3.4 and 6, task B3). Placeholder until task B3 builds it. */
@Composable
fun PlanScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(QhTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(title = "…", actionLabel = "←", onAction = onBack)
    }
}
