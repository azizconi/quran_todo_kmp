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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.theme.QhTheme

/** The color a rating stands for: Forgot is due, Hard is learning, Good is the brand, Easy is learned. */
@Composable
internal fun ratingColor(quality: ReviewQuality): Color = with(QhTheme.colors) {
    when (quality) {
        ReviewQuality.FORGOT -> due
        ReviewQuality.HARD -> learning
        ReviewQuality.GOOD -> primary
        ReviewQuality.EASY -> learned
    }
}

/**
 * The rosette of an ayah in the mode. A rated ayah carries a small round mark of its rating on the rosette
 * (a glyph as well as a color) and its name next to it; the mark of the ayah rated last pops in.
 */
@Composable
fun HifzRosette(
    number: Int,
    status: AyahTodoStatus?,
    weak: Boolean,
    state: HifzRowState,
    modifier: Modifier = Modifier,
) {
    val rating = state.rating
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            AyahRosette(number = number, status = status, weak = weak)
            if (rating != null) {
                RatingMark(
                    quality = rating,
                    pop = state.justRated,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 4.dp),
                )
            }
        }
        if (rating != null) {
            Text(
                text = state.copy.label(rating),
                style = QhTheme.type.label,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .semantics { contentDescription = state.copy.rated(rating) },
            )
        }
    }
}

@Composable
private fun RatingMark(quality: ReviewQuality, pop: Boolean, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (pop && !reduced) 0.2f else 1f) }
    LaunchedEffect(Unit) {
        if (scale.value != 1f) scale.animateTo(1f, QhMotion.bouncy())
    }
    val fill = ratingColor(quality)
    val (icon, glyph) = ratingGlyph(quality)
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

/** The glyph of a rating mark and its color against the mark's fill. */
@Composable
private fun ratingGlyph(quality: ReviewQuality): Pair<ImageVector, Color> {
    val colors = QhTheme.colors
    return when (quality) {
        ReviewQuality.FORGOT -> Icons.Rounded.Close to colors.readerBg
        ReviewQuality.HARD -> Icons.Rounded.PriorityHigh to colors.readerBg
        ReviewQuality.GOOD -> Icons.Rounded.Check to colors.onPrimary
        ReviewQuality.EASY -> Icons.Rounded.DoneAll to colors.onLearned
    }
}
