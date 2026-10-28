package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.hifz.HifzAyahContent
import tj.app.quran_todo.ui.hifz.HifzRosette
import tj.app.quran_todo.ui.hifz.HifzRowState
import tj.app.quran_todo.ui.hifz.hifzRow
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Where an ayah sits inside the passage that is being repeated (it draws a gold bracket on the right). */
enum class RangePosition { None, Single, Start, Middle, End }

fun rangePositionOf(index: Int, range: IntRange?): RangePosition = when {
    range == null || index !in range -> RangePosition.None
    range.first == range.last -> RangePosition.Single
    index == range.first -> RangePosition.Start
    index == range.last -> RangePosition.End
    else -> RangePosition.Middle
}

/** Everything one ayah row needs besides the callbacks. All of it is cheap to compare. */
@Immutable
data class AyahRowModel(
    val ayah: AyahEntity,
    val index: Int,
    /** Arabic text without the Bismillah that the header shows on its own line. */
    val arabic: String,
    val translation: String?,
    val status: AyahTodoStatus?,
    val weak: Boolean,
    val hasNote: Boolean,
    /** The playing or picked ayah: tinted background, gold bar, "more" button. */
    val current: Boolean,
    /** Briefly marked when the reader opened on this ayah. */
    val flash: Boolean,
    val range: RangePosition,
)

/**
 * One ayah: the rosette with its status ring, the Arabic text right-aligned in the reading size with a
 * generous line height, the translation and a hairline. The current ayah gets a [readerHighlight] wash that
 * fades in and a gold bar on the left that grows from the middle; a repeated passage is marked with a
 * thin gold bracket on the right.
 */
@Composable
fun AyahRow(
    model: AyahRowModel,
    arabicStyle: TextStyle,
    translationStyle: TextStyle,
    copy: ReaderCopy,
    showDivider: Boolean,
    enter: ReaderEnterState,
    onSelect: (Int) -> Unit,
    onMore: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Memorize mode: how this ayah is drawn in it. Null outside the mode, and the row is then as it always was. */
    hifz: HifzRowState? = null,
    /** Memorize mode: what sits under the text of the ayah (listen, reveal, ratings). */
    hifzControls: (@Composable () -> Unit)? = null,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val marked = model.current || model.flash
    val highlight by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(QhMotion.Short + 40),
        label = "ayah-highlight",
    )
    val index = model.index
    val number = model.ayah.numberInSurah

    Column(
        modifier
            .fillMaxWidth()
            .readerEnter(enter)
            .hifzRow(hifz)
            .then(
                if (hifz == null) {
                    Modifier.pressable(
                        onClick = { onSelect(index) },
                        onLongClick = { onMore(index) },
                        scale = 0.995f,
                        onClickLabel = copy.selectAyah(number),
                    )
                } else {
                    // In the memorize mode only the controls under the current ayah and its text (hold to peek) react.
                    Modifier
                },
            )
            .semantics {
                selected = model.current
                if (hifz == null) customActions = listOf(CustomAccessibilityAction(copy.ayahActions) { onMore(index); true })
            }
            .drawBehind { drawAyahDecoration(highlight, model.range, colors.readerHighlight, colors.gold) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x1, bottom = QhSpacing.x4),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(QhSpacing.minTouch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hifz == null) {
                    AyahRosette(number = number, status = model.status, weak = model.weak)
                } else {
                    HifzRosette(number = number, status = model.status, weak = model.weak, state = hifz)
                }
                if (model.hasNote) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                        contentDescription = copy.hasNote,
                        tint = colors.gold,
                        modifier = Modifier
                            .padding(start = QhSpacing.x2)
                            .size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = model.current && hifz == null,
                    enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(QhMotion.Short)) + scaleIn(QhMotion.snappy(), initialScale = 0.6f),
                    exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(QhMotion.Instant)) + scaleOut(tween(QhMotion.Instant), targetScale = 0.6f),
                ) {
                    QhIconButton(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = copy.ayahActions,
                        onClick = { onMore(index) },
                        filled = false,
                    )
                }
            }
            if (hifz == null) {
                Text(
                    text = model.arabic,
                    style = arabicStyle,
                    color = colors.ink,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!model.translation.isNullOrBlank()) {
                    Text(
                        text = model.translation,
                        style = translationStyle,
                        color = colors.inkSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = QhSpacing.x3),
                    )
                }
            } else {
                HifzAyahContent(
                    state = hifz,
                    arabic = model.arabic,
                    arabicStyle = arabicStyle,
                    translation = model.translation,
                    translationStyle = translationStyle,
                )
                hifzControls?.invoke()
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QhSpacing.screen)
                    .height(1.dp)
                    .drawBehind { drawRect(colors.hairline) },
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAyahDecoration(
    highlight: Float,
    range: RangePosition,
    wash: Color,
    gold: Color,
) {
    if (highlight > 0f) {
        drawRect(wash.copy(alpha = wash.alpha * highlight))
        // The bar grows from the middle of the row.
        val barWidth = 3.dp.toPx()
        val barHeight = size.height * highlight
        drawRoundRect(
            color = gold,
            topLeft = Offset(0f, (size.height - barHeight) / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
    if (range != RangePosition.None) {
        val line = 1.5.dp.toPx()
        val x = size.width - 8.dp.toPx()
        val tick = 6.dp.toPx()
        val inset = 6.dp.toPx()
        val top = if (range == RangePosition.Start || range == RangePosition.Single) inset else 0f
        val bottom = if (range == RangePosition.End || range == RangePosition.Single) size.height - inset else size.height
        val color = gold.copy(alpha = 0.85f)
        drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = line)
        if (range == RangePosition.Start || range == RangePosition.Single) {
            drawLine(color, Offset(x, top), Offset(x - tick, top), strokeWidth = line)
        }
        if (range == RangePosition.End || range == RangePosition.Single) {
            drawLine(color, Offset(x, bottom), Offset(x - tick, bottom), strokeWidth = line)
        }
    }
}
