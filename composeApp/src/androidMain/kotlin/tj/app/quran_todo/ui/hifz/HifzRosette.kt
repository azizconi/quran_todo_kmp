package tj.app.quran_todo.ui.hifz

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.theme.QhTheme

/** The color an answer stands for: "I remember" is the brand, "hard" is due. */
@Composable
internal fun markColor(mark: AyahMark): Color = with(QhTheme.colors) {
    when (mark) {
        AyahMark.REMEMBERED -> primary
        AyahMark.HARD -> due
    }
}

/**
 * The rosette of an ayah in the mode. An answered ayah carries a small round mark of its answer on the rosette
 * (a glyph as well as a color) and its name next to it; the mark of the ayah answered last pops in.
 */
@Composable
fun HifzRosette(
    number: Int,
    status: AyahTodoStatus?,
    weak: Boolean,
    state: HifzRowState,
    modifier: Modifier = Modifier,
) {
    val mark = state.mark
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            AyahRosette(number = number, status = status, weak = weak)
            if (mark != null) {
                AnswerMark(
                    mark = mark,
                    pop = state.justMarked,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 4.dp),
                )
            }
        }
        if (mark != null) {
            Text(
                text = state.copy.label(mark),
                style = QhTheme.type.label,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .semantics { contentDescription = state.copy.answered(mark) },
            )
        }
    }
}

@Composable
private fun AnswerMark(mark: AyahMark, pop: Boolean, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (pop && !reduced) 0.2f else 1f) }
    LaunchedEffect(Unit) {
        if (scale.value != 1f) scale.animateTo(1f, QhMotion.bouncy())
    }
    val fill = markColor(mark)
    val (icon, glyph) = when (mark) {
        AyahMark.REMEMBERED -> Icons.Rounded.Check to colors.onPrimary
        AyahMark.HARD -> Icons.Rounded.PriorityHigh to colors.readerBg
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .size(16.dp)
            .border(1.5.dp, colors.readerBg, CircleShape)
            .clip(CircleShape)
            .background(fill)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = glyph, modifier = Modifier.size(10.dp))
    }
}
