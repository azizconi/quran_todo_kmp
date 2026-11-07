package tj.app.quran_todo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.NativeSettingsCopy
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The Bismillah that every font card shows in its own typeface. */
private const val SampleText = "بِسْمِ ٱللَّهِ"

internal fun ReadingFontStyle.displayName(): String = when (this) {
    ReadingFontStyle.MADINAH_MUSHAF -> "Madinah Mushaf"
    ReadingFontStyle.AMIRI_QURAN -> "Amiri Quran"
    ReadingFontStyle.SCHEHERAZADE_NEW -> "Scheherazade New"
    ReadingFontStyle.NOTO_NASKH_ARABIC -> "Noto Naskh Arabic"
    ReadingFontStyle.LATEEF -> "Lateef"
    ReadingFontStyle.NOTO_NASTALIQ_URDU -> "Noto Nastaliq Urdu"
    ReadingFontStyle.AREF_RUQAA_INK -> "Aref Ruqaa Ink"
}

/** A short name for a value column: the family without "Quran" / "Arabic" / "Urdu" qualifiers. */
internal fun ReadingFontStyle.shortName(): String = when (this) {
    ReadingFontStyle.MADINAH_MUSHAF -> "Madinah"
    ReadingFontStyle.AMIRI_QURAN -> "Amiri"
    ReadingFontStyle.SCHEHERAZADE_NEW -> "Scheherazade"
    ReadingFontStyle.NOTO_NASKH_ARABIC -> "Naskh"
    ReadingFontStyle.LATEEF -> "Lateef"
    ReadingFontStyle.NOTO_NASTALIQ_URDU -> "Nastaliq"
    ReadingFontStyle.AREF_RUQAA_INK -> "Ruqaa"
}

internal fun ReadingFontStyle.description(copy: NativeSettingsCopy): String = when (this) {
    ReadingFontStyle.MADINAH_MUSHAF -> copy.fontMadinahDescription
    ReadingFontStyle.AMIRI_QURAN -> copy.fontAmiriDescription
    ReadingFontStyle.SCHEHERAZADE_NEW -> copy.fontScheherazadeDescription
    ReadingFontStyle.NOTO_NASKH_ARABIC -> copy.fontNotoNaskhDescription
    ReadingFontStyle.LATEEF -> copy.fontLateefDescription
    ReadingFontStyle.NOTO_NASTALIQ_URDU -> copy.fontNastaliqDescription
    ReadingFontStyle.AREF_RUQAA_INK -> copy.fontRuqaaDescription
}

/**
 * A snapping horizontal carousel of the reading fonts. Every card shows "بِسْمِ ٱللَّهِ" in its own
 * typeface with its name and description; the selected card is full size with a primary outline and a
 * check that pops in, the others sit a little smaller. Tapping selects (segment haptic) and slides a
 * partly hidden card into view.
 *
 * [contentPadding] is the inset before the first and after the last card, so the carousel can run
 * edge to edge of the screen while its first card lines up with the content above.
 */
@Composable
internal fun FontCarousel(
    selected: ReadingFontStyle,
    onSelect: (ReadingFontStyle) -> Unit,
    descriptions: NativeSettingsCopy,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = QhSpacing.screen),
) {
    val fonts = ReadingFontStyle.entries
    val selectedIndex = fonts.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val cardHeight = rememberCardHeight(descriptions)

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
    ) {
        itemsIndexed(fonts, key = { _, font -> font.storageValue }) { index, font ->
            FontCard(
                style = font,
                selected = font == selected,
                descriptions = descriptions,
                height = cardHeight,
                onClick = {
                    onSelect(font)
                    scope.launch { listState.bringIntoView(index, animate = !reduced) }
                },
            )
        }
    }
}

/** Height of the Arabic sample area of a card. */
private val SampleHeight = 64.dp

/** Lines the description may take before it is ellipsized. */
private const val DescriptionLines = 3

/**
 * One height for every card: the tallest one wins. Each card holds the sample, the name and a
 * description of up to [DescriptionLines] lines; the description is measured for every font in the
 * current language at the current font size, so nothing is cut at 1.0x and only a very long text
 * at a large font scale loses its last line to an ellipsis.
 */
@Composable
private fun rememberCardHeight(descriptions: NativeSettingsCopy): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = QhTheme.type.label
    val descriptionStyle = QhTheme.type.callout
    return remember(descriptions, density, measurer, nameStyle, descriptionStyle) {
        val textWidth = with(density) { (CardWidth - CardPadding * 2).roundToPx() }
        val constraints = Constraints(maxWidth = textWidth)
        val fonts = ReadingFontStyle.entries
        val nameHeight = fonts.maxOf { font ->
            measurer.measure(font.displayName(), nameStyle, TextOverflow.Ellipsis, false, 1, constraints = constraints).size.height
        }
        val descriptionHeight = fonts.maxOf { font ->
            measurer.measure(
                font.description(descriptions),
                descriptionStyle,
                TextOverflow.Ellipsis,
                true,
                DescriptionLines,
                constraints = constraints,
            ).size.height
        }
        with(density) {
            (CardPadding * 2 + SampleHeight + QhSpacing.x1).toPx() + nameHeight + descriptionHeight
        }.let { totalPx -> with(density) { totalPx.toDp() } + 1.dp }
    }
}

/** Scrolls so the card at [index] is fully on screen; does nothing when it already is. */
private suspend fun LazyListState.bringIntoView(index: Int, animate: Boolean) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val fullyVisible = info != null &&
        info.offset >= layoutInfo.viewportStartOffset &&
        info.offset + info.size <= layoutInfo.viewportEndOffset
    if (fullyVisible) return
    if (animate) animateScrollToItem(index) else scrollToItem(index)
}

@Composable
private fun FontCard(
    style: ReadingFontStyle,
    selected: Boolean,
    descriptions: NativeSettingsCopy,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = QhMotion.snappy<Float>().orSnap(),
        label = "font-card-scale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "font-card-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "font-card-outline",
    )
    val badgeIn = QhMotion.bouncy<Float>().orSnap()
    val badgeOut = QhMotion.snappy<Float>().orSnap()

    Box(
        Modifier
            .width(CardWidth)
            .height(height)
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, shape),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SampleHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = SampleText,
                    style = TextStyle(
                        fontFamily = getQuranFontFamily(style),
                        fontSize = 26.sp,
                        lineHeight = 40.sp,
                    ),
                    color = colors.ink,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(QhSpacing.x1))
            Text(
                text = style.displayName(),
                style = QhTheme.type.label,
                color = if (selected) colors.onPrimaryContainer else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = style.description(descriptions),
                style = QhTheme.type.callout,
                color = if (selected) colors.onPrimaryContainer else colors.inkSecondary,
                maxLines = DescriptionLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(QhSpacing.x2),
            enter = scaleIn(badgeIn, initialScale = 0.3f) + fadeIn(badgeOut),
            exit = scaleOut(badgeOut, targetScale = 0.3f) + fadeOut(badgeOut),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val CardWidth = 168.dp
private val CardPadding = QhSpacing.x3
