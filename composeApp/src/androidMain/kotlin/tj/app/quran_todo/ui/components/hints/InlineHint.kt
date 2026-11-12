package tj.app.quran_todo.ui.components.hints

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.i18n.rememberHintsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The hints of the app. Without the app root above (previews, the catalog) the flags live in memory. */
val LocalHints = staticCompositionLocalOf { HintFlags(InMemoryHintStore()) }

@Composable
fun rememberHintFlags(): HintFlags {
    val context = LocalContext.current.applicationContext
    return remember(context) { HintFlags(SharedPreferencesHintStore(context)) }
}

/**
 * One quiet line that explains a gesture on the screen itself: no overlay, no dimming. It goes away for good after the person
 * does the thing once (the screen calls `LocalHints.current.markUsed(id)`) or taps the "×".
 */
@Composable
fun InlineHint(
    id: String,
    text: String,
    modifier: Modifier = Modifier,
    hints: HintFlags = LocalHints.current,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val copy = rememberHintsCopy()
    AnimatedVisibility(
        visible = !hints.isUsed(id),
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(QhMotion.Short)) + expandVertically(tween(QhMotion.Medium)),
        exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(QhMotion.Short)) + shrinkVertically(tween(QhMotion.Medium)),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = QhSpacing.minTouch), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = copy.dismiss) { hints.markUsed(id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = copy.dismiss, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
