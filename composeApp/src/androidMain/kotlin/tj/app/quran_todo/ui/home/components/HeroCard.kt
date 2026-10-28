@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.home.ContinueUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** What the "Listen" button of the hero is doing. */
internal enum class HeroListenState {
    /** Nothing of this surah is playing: "Listen". */
    Idle,

    /** This surah is playing: "Pause" with an equalizer. */
    Playing,
}

/**
 * The "Continue" hero: the surah the person stopped at, how far, and the two ways on: read (opens the
 * reader) or listen (starts the mini player, no navigation). Grows into the reader header
 * (`Origin.HERO` shared keys on the container, the name and the Arabic name).
 */
@Composable
internal fun HeroCard(
    item: ContinueUi,
    copy: HomeCopy,
    arabicStyle: TextStyle,
    listenState: HeroListenState,
    onRead: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val origin = Origin.HERO
    val stackButtons = LocalDensity.current.fontScale > StackButtonsAtFontScale

    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .sharedBoundsKey(SharedKeys.surahContainer(origin, item.surah), SharedResize.Remeasure),
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(QhSpacing.cardLarge),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = (if (item.isFresh) copy.startWithFatiha else copy.continueLabel).uppercase(),
                    style = type.overline,
                    color = colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(QhSpacing.x2))
                Text(
                    text = item.name,
                    style = type.headline,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedBoundsKey(SharedKeys.surahTitle(origin, item.surah), SharedResize.Scale),
                )
            }
            if (item.arabicName.isNotBlank()) {
                Spacer(Modifier.width(QhSpacing.x3))
                Text(
                    text = item.arabicName,
                    style = arabicStyle,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier.sharedBoundsKey(SharedKeys.surahArabic(origin, item.surah), SharedResize.Scale),
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x2))
        val position = if (item.isFresh) {
            listOf(item.translatedName, copy.ayahCount(item.ayahCount)).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            copy.ayahOf(item.ayah, item.ayahCount)
        }
        Text(text = position, style = type.callout, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!item.isFresh) {
            Spacer(Modifier.height(QhSpacing.x3))
            QhLinearProgress(
                progress = item.progress,
                trackColor = colors.surface.copy(alpha = 0.7f),
                modifier = Modifier.semantics { contentDescription = position },
            )
        }
        Spacer(Modifier.height(QhSpacing.x5))
        val read: @Composable (Modifier) -> Unit = { buttonModifier ->
            HeroButton(
                label = copy.read,
                onClick = onRead,
                primary = true,
                active = false,
                modifier = buttonModifier,
            ) { tint ->
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconInline))
            }
        }
        val listen: @Composable (Modifier) -> Unit = { buttonModifier ->
            val playing = listenState == HeroListenState.Playing
            HeroButton(
                label = if (playing) copy.pause else copy.listen,
                onClick = onListen,
                primary = false,
                active = playing,
                modifier = buttonModifier.semantics { if (playing) stateDescription = copy.pause },
            ) { tint ->
                if (playing) {
                    Equalizer(color = tint, size = QhSpacing.iconInline)
                } else {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconInline))
                }
            }
        }
        if (stackButtons) {
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                read(Modifier.fillMaxWidth())
                listen(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                read(Modifier.weight(1f))
                listen(Modifier.weight(1f))
            }
        }
    }
}

/** Beyond this font scale two buttons side by side no longer fit their labels. */
private const val StackButtonsAtFontScale = 1.15f

/**
 * A 52dp pill for the hero. The primary one is solid, the secondary one sits on `surface`; while it
 * [active] (audio is playing) the secondary one gets a `primary` outline. Local: the design system
 * buttons have no slot for an equalizer.
 */
@Composable
private fun HeroButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable (tint: Color) -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val container by animateColorAsState(
        targetValue = if (primary) colors.primary else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "hero-button-container",
    )
    val border by animateColorAsState(
        targetValue = when {
            primary -> Color.Transparent
            active -> colors.primary
            else -> colors.hairline
        },
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "hero-button-border",
    )
    val tint = if (primary) colors.onPrimary else colors.primary
    Row(
        modifier
            .heightIn(min = 52.dp)
            .pressable(onClick = onClick, shape = shape, highlight = !primary)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .padding(horizontal = QhSpacing.x4),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading(tint)
        Text(
            text = label,
            style = QhTheme.type.title2.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
