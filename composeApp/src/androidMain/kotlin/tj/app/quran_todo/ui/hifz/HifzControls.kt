package tj.app.quran_todo.ui.hifz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The font scale above which the buttons stack instead of sitting side by side. */
private const val LargeFontScale = 1.15f

/**
 * What sits under an ayah of the session. Only the ayah being recalled shows anything: first "Listen" and
 * "Reveal", after the reveal the four ratings. It comes in when the ayah becomes the current one and folds
 * away when it is rated (the row follows with `animateContentSize`).
 */
@Composable
internal fun HifzControlsHost(mode: HifzMode, inSurah: Int) {
    val reduced = LocalReducedMotion.current
    val session = mode.session
    val show = session != null && !session.finished && session.current?.inSurah == inSurah
    // The controls stay up while they leave, after the session has moved on.
    val held = remember { arrayOfNulls<HifzSession>(1) }
    if (show) held[0] = session

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = show
    AnimatedVisibility(
        visibleState = visibleState,
        enter = if (reduced) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(QhMotion.Medium, delayMillis = QhMotion.Short, easing = QhMotion.EmphasizedDecelerate)) +
                expandVertically(tween(QhMotion.Medium, easing = QhMotion.Emphasized))
        },
        exit = if (reduced) {
            fadeOut(tween(0))
        } else {
            fadeOut(tween(QhMotion.Short)) + shrinkVertically(tween(QhMotion.Medium, easing = QhMotion.Emphasized))
        },
    ) {
        held[0]?.let { HifzControls(mode = mode, session = it) }
    }
}

@Composable
private fun HifzControls(mode: HifzMode, session: HifzSession) {
    val reduced = LocalReducedMotion.current
    val copy = mode.copy
    AnimatedContent(
        targetState = session.revealed,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (
                    fadeIn(tween(QhMotion.Medium, delayMillis = 60, easing = QhMotion.EmphasizedDecelerate)) +
                        slideInVertically(tween(QhMotion.Medium, easing = QhMotion.EmphasizedDecelerate)) { it / 3 }
                    ) togetherWith fadeOut(tween(QhMotion.Instant)) using
                    SizeTransform(clip = false) { _, _ -> tween(QhMotion.Medium, easing = QhMotion.Emphasized) }
            }
        },
        label = "hifz-controls",
    ) { revealed ->
        if (revealed) {
            RatingControls(copy = copy, onRate = { mode.viewModel.rate(it, session.index) })
        } else {
            RevealControls(
                mode = mode,
                firstAyah = session.ratedCount == 0,
            )
        }
    }
}

// -- Before the reveal --------------------------------------------------------------------------------------

/** How "Listen" and "Reveal" share the width: side by side with a named Listen, with a round Listen, or stacked. */
private enum class RevealLayout { Wide, Narrow, Stacked }

@Composable
private fun RevealControls(mode: HifzMode, firstAyah: Boolean) {
    val copy = mode.copy
    val colors = QhTheme.colors
    val listening by mode.viewModel.listening.collectAsState()
    val fontScale = LocalDensity.current.fontScale

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = copy.reciteFromMemory, style = QhTheme.type.callout, color = colors.inkSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val layout = when {
                fontScale > LargeFontScale -> RevealLayout.Stacked
                maxWidth >= WideRevealMinWidth -> RevealLayout.Wide
                else -> RevealLayout.Narrow
            }
            when (layout) {
                RevealLayout.Stacked -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RevealButton(copy, mode, Modifier.fillMaxWidth())
                    ListenButton(copy, listening, mode, Modifier.fillMaxWidth())
                }
                RevealLayout.Wide -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenButton(copy, listening, mode)
                    RevealButton(copy, mode, Modifier.weight(1f))
                }
                RevealLayout.Narrow -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenIconButton(copy, listening, mode)
                    RevealButton(copy, mode, Modifier.weight(1f))
                }
            }
        }
        if (firstAyah) {
            Text(text = copy.peekHint, style = QhTheme.type.label, color = colors.inkSecondary)
        }
    }
}

/** The width of the controls from which "Listen" keeps its name next to "Reveal". */
private val WideRevealMinWidth = 360.dp

@Composable
private fun RevealButton(copy: HifzCopy, mode: HifzMode, modifier: Modifier = Modifier) {
    QhButton(
        text = copy.reveal,
        onClick = mode.viewModel::reveal,
        modifier = modifier,
        size = QhButtonSize.L,
        icon = Icons.Rounded.Visibility,
    )
}

@Composable
private fun ListenButton(copy: HifzCopy, listening: Boolean, mode: HifzMode, modifier: Modifier = Modifier) {
    QhButton(
        text = if (listening) copy.listenPause else copy.listen,
        onClick = { mode.viewModel.toggleListen(mode.surah) },
        modifier = modifier,
        variant = QhButtonVariant.Tonal,
        size = QhButtonSize.M,
        icon = if (listening) Icons.Rounded.Pause else Icons.Rounded.Headphones,
    )
}

/** "Listen" as a round button, for the screens too narrow for its name; the name is its label. */
@Composable
private fun ListenIconButton(copy: HifzCopy, listening: Boolean, mode: HifzMode, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val label = if (listening) copy.listenPause else copy.listen
    Box(
        modifier
            .size(QhSpacing.minTouch)
            .pressable(
                onClick = { mode.viewModel.toggleListen(mode.surah) },
                shape = CircleShape,
                onClickLabel = label,
            )
            .clip(CircleShape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (listening) Icons.Rounded.Pause else Icons.Rounded.Headphones,
            contentDescription = label,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(QhSpacing.iconBar),
        )
    }
}

// -- After the reveal ---------------------------------------------------------------------------------------

private val Ratings = listOf(ReviewQuality.FORGOT, ReviewQuality.HARD, ReviewQuality.GOOD, ReviewQuality.EASY)

/** How a rating button looks: the fill, the text, and an accent dot where the fill alone would not tell. */
private class RatingLook(val container: Color, val content: Color, val accent: Color?)

@Composable
private fun ratingLook(quality: ReviewQuality): RatingLook {
    val colors = QhTheme.colors
    return when (quality) {
        ReviewQuality.FORGOT -> RatingLook(colors.dueContainer, colors.ink, colors.due)
        ReviewQuality.HARD -> RatingLook(colors.learningContainer, colors.ink, colors.learning)
        ReviewQuality.GOOD -> RatingLook(colors.primary, colors.onPrimary, null)
        ReviewQuality.EASY -> RatingLook(colors.learnedContainer, colors.ink, colors.learned)
    }
}

/**
 * Forgot, Hard, Good, Easy in one row when the screen is wide enough for the longest names (Tajik: "Фаромӯш")
 * to fit uncut; otherwise two by two, which also suits large text and gives the buttons more room for a dot of
 * their color.
 */
@Composable
private fun RatingControls(copy: HifzCopy, onRate: (ReviewQuality) -> Unit) {
    val colors = QhTheme.colors
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = copy.rateTitle, style = QhTheme.type.callout, color = colors.inkSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val oneRow = fontScale <= LargeFontScale && maxWidth >= OneRowMinWidth
            if (oneRow) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ratings.forEach { RatingPill(it, copy, onRate, showDot = false, modifier = Modifier.weight(1f)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ratings.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { RatingPill(it, copy, onRate, showDot = true, modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** The width of the controls from which the four ratings share one row. */
private val OneRowMinWidth = 350.dp

/** A rating button: a pill in the color of its meaning. "Forgot" is answered with the reject buzz, the rest tick. */
@Composable
private fun RatingPill(
    quality: ReviewQuality,
    copy: HifzCopy,
    onRate: (ReviewQuality) -> Unit,
    showDot: Boolean,
    modifier: Modifier = Modifier,
) {
    val look = ratingLook(quality)
    val shape = QhTheme.shapes.full
    Row(
        modifier
            .heightIn(min = 48.dp)
            .pressable(
                onClick = { onRate(quality) },
                haptic = if (quality == ReviewQuality.FORGOT) HapticKind.Reject else HapticKind.Tick,
                shape = shape,
                scale = 0.95f,
                onClickLabel = copy.label(quality),
            )
            .clip(shape)
            .background(look.container)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        look.accent?.takeIf { showDot }?.let { accent ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Text(
            text = copy.label(quality),
            style = QhTheme.type.label,
            color = look.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
