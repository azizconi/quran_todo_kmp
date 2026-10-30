package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.i18n.MarkLine
import tj.app.quran_todo.ui.i18n.MushafCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The quiet line above an ayah where a juz, a quarter of a hizb or a page begins: "Juz 2 · Hizb 3" on the left, a hairline,
 * "p. 22" on the right (either end may be missing; the hairline fills what is left). Plain text and a line: no ornament, so
 * it never competes with the text. A screen reader gets it as one heading ("Juz 2 · Hizb 3 · p. 22").
 */
@Composable
internal fun MushafMarkLine(line: MarkLine, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val style = QhTheme.type.label
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = line.spoken
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (line.start != null) {
            Text(
                text = line.start,
                style = style,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(QhSpacing.x2))
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.hairline),
        )
        if (line.end != null) {
            Spacer(Modifier.width(QhSpacing.x2))
            Text(
                text = line.end,
                style = style,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The sajda sign beside the ayah number: a small arch (a mihrab, drawn as a path: never a star) and "Sajdah · recommended" /
 * "Sajdah · obligatory" on a quiet gold pill. A tap says in a toast what the sign means.
 */
@Composable
internal fun SajdaSign(sajda: Sajda, mushaf: MushafCopy, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val toaster = LocalToaster.current
    val label = remember(sajda, mushaf) { mushaf.sajdaLabel(sajda) }
    val shape = QhTheme.shapes.full
    // The touch target is the whole height of the number row; the pill inside it is shorter.
    Box(
        modifier
            .heightIn(min = QhSpacing.minTouch)
            .pressable(
                onClick = { toaster.show(mushaf.sajdaExplain) },
                shape = shape,
                onClickLabel = mushaf.sajdaWhat,
            )
            .semantics { contentDescription = "$label. ${mushaf.sajdaExplain}" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(colors.goldContainer)
                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SajdaArch(color = colors.gold)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = QhTheme.type.label,
                color = colors.gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A pointed arch over a ground line, with a dot in it: the mihrab, in strokes of [color]. */
@Composable
internal fun SajdaArch(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.5.dp.toPx()
        val left = 0.16f * w
        val right = 0.84f * w
        val shoulder = 0.50f * h
        val top = 0.06f * h
        val ground = 0.94f * h
        val arch = Path().apply {
            moveTo(left, ground)
            lineTo(left, shoulder)
            cubicTo(left, 0.24f * h, 0.30f * w, 0.14f * h, 0.5f * w, top)
            cubicTo(0.70f * w, 0.14f * h, right, 0.24f * h, right, shoulder)
            lineTo(right, ground)
        }
        drawPath(arch, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(0.04f * w, ground), Offset(0.96f * w, ground), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = 0.085f * w, center = Offset(0.5f * w, 0.62f * h))
    }
}
