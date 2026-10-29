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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
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

/**
 * What the controls under an ayah of a recall session need from their screen. The reader's "Check yourself" and the
 * lesson implement it; the controls and the rows are the same in both. [session] is read in composition, so a screen
 * implements it with a snapshot state.
 */
@Stable
interface RecallHost {
    val session: HifzSession?
    val copy: HifzCopy

    /** The audio of the ayah being recalled plays (or is being prepared). */
    val listening: StateFlow<Boolean>

    fun reveal()

    /** Answers the ayah at session position [position]; ignored when the session has moved on already. */
    fun mark(mark: AyahMark, position: Int)

    /** The headphones button: plays the ayah being recalled, pauses it while it plays. */
    fun toggleListen()
}

/** The font scale above which the buttons stack instead of sitting side by side. */
private const val LargeFontScale = 1.15f

/**
 * What sits under an ayah of the session. Only the ayah being recalled shows anything: first "Listen" and
 * "Reveal", after the reveal the two answers "I remember" / "Hard". It comes in when the ayah becomes the current
 * one and folds away when it is answered (the row follows with `animateContentSize`).
 */
@Composable
fun RecallControlsHost(host: RecallHost, inSurah: Int) {
    val reduced = LocalReducedMotion.current
    val session = host.session
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
        held[0]?.let { RecallControls(host = host, session = it) }
    }
}

@Composable
private fun RecallControls(host: RecallHost, session: HifzSession) {
    val reduced = LocalReducedMotion.current
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
        label = "recall-controls",
    ) { revealed ->
        if (revealed) {
            MarkControls(copy = host.copy, onMark = { host.mark(it, session.index) })
        } else {
            RevealControls(host = host, firstAyah = session.markedCount == 0)
        }
    }
}

// -- Before the reveal --------------------------------------------------------------------------------------

/** How "Listen" and "Reveal" share the width: side by side with a named Listen, with a round Listen, or stacked. */
private enum class RevealLayout { Wide, Narrow, Stacked }

@Composable
private fun RevealControls(host: RecallHost, firstAyah: Boolean) {
    val copy = host.copy
    val colors = QhTheme.colors
    val listening by host.listening.collectAsState()
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
                    RevealButton(copy, host, Modifier.fillMaxWidth())
                    ListenButton(copy, listening, host, Modifier.fillMaxWidth())
                }
                RevealLayout.Wide -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenButton(copy, listening, host)
                    RevealButton(copy, host, Modifier.weight(1f))
                }
                RevealLayout.Narrow -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenIconButton(copy, listening, host)
                    RevealButton(copy, host, Modifier.weight(1f))
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
private fun RevealButton(copy: HifzCopy, host: RecallHost, modifier: Modifier = Modifier) {
    QhButton(
        text = copy.reveal,
        onClick = host::reveal,
        modifier = modifier,
        size = QhButtonSize.L,
        icon = Icons.Rounded.Visibility,
    )
}

@Composable
private fun ListenButton(copy: HifzCopy, listening: Boolean, host: RecallHost, modifier: Modifier = Modifier) {
    QhButton(
        text = if (listening) copy.listenPause else copy.listen,
        onClick = host::toggleListen,
        modifier = modifier,
        variant = QhButtonVariant.Tonal,
        size = QhButtonSize.M,
        icon = if (listening) Icons.Rounded.Pause else Icons.Rounded.Headphones,
    )
}

/** "Listen" as a round button, for the screens too narrow for its name; the name is its label. */
@Composable
private fun ListenIconButton(copy: HifzCopy, listening: Boolean, host: RecallHost, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val label = if (listening) copy.listenPause else copy.listen
    Box(
        modifier
            .size(QhSpacing.minTouch)
            .pressable(
                onClick = host::toggleListen,
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

/**
 * "I remember" and "Hard" side by side (stacked at large text sizes). The answer is about this ayah only; the grade
 * of the passage comes at the end.
 */
@Composable
private fun MarkControls(copy: HifzCopy, onMark: (AyahMark) -> Unit) {
    val colors = QhTheme.colors
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = copy.markTitle, style = QhTheme.type.callout, color = colors.inkSecondary)
        if (fontScale > LargeFontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RememberedPill(copy, onMark, Modifier.fillMaxWidth())
                HardPill(copy, onMark, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RememberedPill(copy, onMark, Modifier.weight(1f))
                HardPill(copy, onMark, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RememberedPill(copy: HifzCopy, onMark: (AyahMark) -> Unit, modifier: Modifier) {
    val colors = QhTheme.colors
    MarkPill(
        label = copy.remembered,
        container = colors.primary,
        content = colors.onPrimary,
        dot = null,
        haptic = HapticKind.Tick,
        onClick = { onMark(AyahMark.REMEMBERED) },
        modifier = modifier,
    )
}

@Composable
private fun HardPill(copy: HifzCopy, onMark: (AyahMark) -> Unit, modifier: Modifier) {
    val colors = QhTheme.colors
    MarkPill(
        label = copy.hardMark,
        container = colors.dueContainer,
        content = colors.ink,
        dot = colors.due,
        haptic = HapticKind.Segment,
        onClick = { onMark(AyahMark.HARD) },
        modifier = modifier,
    )
}

/** An answer button: a pill in the color of its meaning, with a dot where the fill alone would not tell. */
@Composable
private fun MarkPill(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    dot: androidx.compose.ui.graphics.Color?,
    haptic: HapticKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = QhTheme.shapes.full
    Row(
        modifier
            .heightIn(min = 52.dp)
            .pressable(onClick = onClick, haptic = haptic, shape = shape, scale = 0.95f, onClickLabel = label)
            .clip(shape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
        Text(
            text = label,
            style = QhTheme.type.title2,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
