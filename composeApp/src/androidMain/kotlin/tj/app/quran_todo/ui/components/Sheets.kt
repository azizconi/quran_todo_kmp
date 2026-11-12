package tj.app.quran_todo.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.i18n.rememberA11yCopy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A modal bottom sheet in our style: `xl` top corners, a 32x4 `hairline` handle, `surface`, the scrim
 * token behind it, and content that animates when its size changes. Dismissed by swipe, scrim tap or
 * back; the sheet slides out before [onDismiss] is called when you hide it through [sheetState].
 *
 * Lay the [content] out like a screen section: it already has the 20dp screen inset and bottom room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QhSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    val a11y = rememberA11yCopy(language)
    // Material3 names the scrim "Close sheet" from the phone's locale, not the app language: its dialog window brings its
    // own context, so it cannot be overridden from here (A11Y_FINDINGS.md, item 15).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = QhTheme.shapes.xlTop,
        containerColor = colors.surface,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(QhTheme.shapes.full)
                    .background(colors.hairline)
                    .semantics { contentDescription = a11y.sheetHandle },
            )
        },
    ) {
        Column(
            Modifier
                .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
                .padding(horizontal = QhSpacing.screen)
                .padding(bottom = QhSpacing.x6),
            content = content,
        )
    }
}
