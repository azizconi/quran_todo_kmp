package tj.app.quran_todo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A calm empty or error state: an illustration (by default the mihrab arch drawing itself in 900ms
 * with a calligraphy word inside), a [title], an optional [message] and an optional action button.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: @Composable () -> Unit = { MihrabCalligraphy(width = 88.dp) },
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.x8, vertical = QhSpacing.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration()
        Spacer(Modifier.height(QhSpacing.x5))
        Text(
            text = title,
            style = QhTheme.type.title1,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Spacer(Modifier.height(QhSpacing.x2))
            Text(
                text = message,
                style = QhTheme.type.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(QhSpacing.x5))
            QhButton(
                text = actionLabel,
                onClick = onAction,
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.M,
            )
        }
    }
}
