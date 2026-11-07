package tj.app.quran_todo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * A 52dp search pill. The magnifier warms up to `primary` and a 1.5dp `primary` outline fades in on
 * focus; the clear button scales in once there is text. [clearContentDescription] is the
 * accessibility label of that button (pass it in the app language). Pass a [focusRequester] to focus
 * the field from outside; [onFocusChange] reports focus, e.g. to scroll the list to the field.
 */
@Composable
fun QhSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    onSearch: (String) -> Unit = {},
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val keyboard = LocalSoftwareKeyboardController.current
    val reduced = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(
        targetValue = if (focused) colors.primary else Color.Transparent,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "search-outline",
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused) colors.primary else colors.inkSecondary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "search-icon",
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(colors.surfaceMuted)
            .border(1.5.dp, outline, shape)
            .pointerInput(Unit) {
                // Tapping anywhere on the pill (icon, padding) focuses the field.
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(QhSpacing.iconInline),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = QhTheme.type.body, color = colors.inkTertiary, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = QhTheme.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                    },
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = if (reduced) EnterTransition.None else scaleIn(QhMotion.snappy(), initialScale = 0.5f) + fadeIn(QhMotion.snappy()),
            exit = if (reduced) ExitTransition.None else scaleOut(QhMotion.snappy(), targetScale = 0.5f) + fadeOut(QhMotion.snappy()),
        ) {
            Box(
                Modifier
                    .size(QhSpacing.minTouch)
                    .pressable(
                        onClick = { onValueChange("") },
                        haptic = HapticKind.Tick,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = clearContentDescription,
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
