package tj.app.quran_todo.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The soft colored shadow of section 2.3 (`primary` @ 18%, blur ~24dp, offset y 8dp). Reserved for
 * the hero card, the primary large button and the floating mini player; everything else is flat.
 * Drawn only on Android 9+, where blur mask filters are hardware accelerated; older versions get none.
 */
@Composable
fun Modifier.softShadow(
    shape: Shape,
    color: Color = QhTheme.colors.primary.copy(alpha = 0.18f),
    blur: Dp = 24.dp,
    offsetY: Dp = 8.dp,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
    return drawWithCache {
        val paint = Paint().apply {
            this.color = color
            asFrameworkPaint().maskFilter = BlurMaskFilter(blur.toPx() * 0.8f, BlurMaskFilter.Blur.NORMAL)
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        val dy = offsetY.toPx()
        onDrawBehind {
            translate(top = dy) {
                drawIntoCanvas { canvas -> canvas.drawOutline(outline, paint) }
            }
        }
    }
}

/** Card looks. */
enum class QhCardStyle {
    /** `surface` with a hairline outline; flat. */
    Standard,

    /** Gradient `primaryContainer -> surface`, an ogee lattice, a gold hairline and the soft shadow. */
    Hero,
}

/**
 * The card surface. With [onClick] it becomes pressable (spring squeeze and haptic); with
 * [onLongClick] too. [content] is laid out in a column inside [contentPadding].
 */
@Composable
fun QhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    style: QhCardStyle = QhCardStyle.Standard,
    contentPadding: PaddingValues = PaddingValues(QhSpacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val hero = style == QhCardStyle.Hero
    val interaction = if (onClick != null) {
        Modifier.pressable(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = shape,
            highlight = !hero,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(if (hero) Modifier.softShadow(shape) else Modifier)
            .then(interaction)
            .clip(shape)
            .then(
                if (hero) {
                    Modifier
                        .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surface)))
                        .border(1.dp, colors.gold.copy(alpha = 0.3f), shape)
                } else {
                    Modifier
                        .background(colors.surface)
                        .border(1.dp, colors.hairline, shape)
                },
            ),
    ) {
        if (hero) ArabesqueLattice(Modifier.matchParentSize())
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * A section heading: an uppercase `overline` with an optional [trailing] slot (a count, a link).
 * Announced as a heading by accessibility services.
 */
@Composable
fun QhSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title.uppercase(),
            style = QhTheme.type.overline,
            color = QhTheme.colors.inkSecondary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke(this)
    }
}
