package tj.app.quran_todo.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** Duration of one rolling digit. */
private const val DigitMillis = 220

/**
 * A number whose digits roll up (when it grows) or down (when it shrinks), one digit at a time.
 * Digits are aligned from the right, so 9 -> 10 only animates the units place. Renders as plain text
 * under reduced motion. Announced to accessibility services as a single number.
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = QhTheme.type.title1,
    color: Color = QhTheme.colors.ink,
) {
    val textStyle = style.tabular()
    val text = value.toString()
    if (LocalReducedMotion.current) {
        Text(text, modifier = modifier, style = textStyle, color = color)
        return
    }

    // A plain holder, not state: writing it must not trigger another recomposition.
    val previous = remember { intArrayOf(value) }
    val increasing = value >= previous[0]
    SideEffect { previous[0] = value }

    Row(
        modifier = modifier
            .animateContentSize(QhMotion.gentle())
            .clearAndSetSemantics { contentDescription = text },
    ) {
        text.forEachIndexed { index, digit ->
            // Keyed by place value (units, tens, ...) so a digit keeps its state when the length changes.
            key(text.length - index) {
                AnimatedContent(
                    targetState = digit,
                    transitionSpec = {
                        val spec = tween<IntOffset>(DigitMillis, easing = QhMotion.Emphasized)
                        val fade = tween<Float>(DigitMillis)
                        if (increasing) {
                            (slideInVertically(spec) { it } + fadeIn(fade)) togetherWith
                                (slideOutVertically(spec) { -it } + fadeOut(fade))
                        } else {
                            (slideInVertically(spec) { -it } + fadeIn(fade)) togetherWith
                                (slideOutVertically(spec) { it } + fadeOut(fade))
                        }
                    },
                    label = "counter-digit",
                ) { target ->
                    Text(target.toString(), style = textStyle, color = color)
                }
            }
        }
    }
}
