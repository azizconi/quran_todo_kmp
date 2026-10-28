package tj.app.quran_todo.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.EightPointStar
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** One ayah of a live preview: its number inside the surah and its Arabic text. */
internal data class PreviewAyah(val number: Int, val text: String)

/** Al-Fatiha 1:2, the preview of the typography sheet. */
internal val FatihaSecondAyah = PreviewAyah(number = 2, text = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ")

/**
 * A card on the reader paper (`readerBg`) that shows [ayahs] the way the reader will: the chosen
 * [fontStyle] at [fontSize], each ayah closed by its rosette. A new size springs to its value and a
 * new font crossfades, so the person watches the text answer to the controls under it.
 */
@Composable
internal fun ReaderPreviewCard(
    caption: String,
    ayahs: List<PreviewAyah>,
    fontStyle: ReadingFontStyle,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    val reduced = LocalReducedMotion.current
    val animatedSize by animateFloatAsState(
        targetValue = fontSize.toFloat(),
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "preview-size",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.readerBg)
            .border(1.dp, colors.hairline, shape)
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap())
            .padding(QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EightPointStar(size = 14.dp, strokeWidth = 1.dp)
            Text(
                text = caption,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Crossfade(
            targetState = fontStyle,
            animationSpec = if (reduced) snap() else tween(QhMotion.Medium),
            label = "preview-font",
        ) { style ->
            PreviewText(ayahs = ayahs, fontStyle = style, size = animatedSize)
        }
    }
}

@Composable
private fun PreviewText(ayahs: List<PreviewAyah>, fontStyle: ReadingFontStyle, size: Float) {
    val colors = QhTheme.colors
    val density = LocalDensity.current
    val family = getQuranFontFamily(fontStyle)
    val rosetteSp = (size * 0.85f).sp
    val rosetteDp = with(density) { rosetteSp.toDp() }

    val text = remember(ayahs) {
        buildAnnotatedString {
            ayahs.forEachIndexed { index, ayah ->
                append(ayah.text)
                append(' ')
                appendInlineContent(rosetteId(ayah.number), ayah.number.toString())
                if (index != ayahs.lastIndex) append(' ')
            }
        }
    }
    val inlineContent = remember(ayahs, rosetteSp) {
        ayahs.associate { ayah ->
            rosetteId(ayah.number) to InlineTextContent(
                Placeholder(
                    width = rosetteSp,
                    height = rosetteSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) { AyahRosette(number = ayah.number, size = rosetteDp) }
        }
    }
    Text(
        text = text,
        inlineContent = inlineContent,
        style = QhTheme.type.quranBody(family, size.sp),
        color = colors.ink,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun rosetteId(number: Int) = "rosette-$number"
