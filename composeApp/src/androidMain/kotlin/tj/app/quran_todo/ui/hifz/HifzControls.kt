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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import tj.app.quran_todo.ui.components.hints.Hints
import tj.app.quran_todo.ui.components.hints.InlineHint
import tj.app.quran_todo.ui.components.hints.LocalHints
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.rememberHintsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.state.HifzHintMode
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

    /** The level of the hint of a hidden ayah (task F2a). Read in composition. */
    val hintMode: HifzHintMode

    /** The choice of the sheet of levels; the screen keeps it in the settings of the app. */
    fun setHintMode(mode: HifzHintMode)

    /** The words of the ayah [inSurah] (a number in the surah) as the screens draw them; null for an ayah that is not in the passage. */
    fun words(inSurah: Int): AyahWords?

    fun reveal()

    /** Answers the ayah at session position [position]; ignored when the session has moved on already. */
    fun mark(mark: AyahMark, position: Int)

    /** Answers the ayah at [position] without the reveal first: the progressive hint offers the answers while the ayah is still partly shown. */
    fun markEarly(mark: AyahMark, position: Int)

    /** "Hide more" of the progressive hint: the next quarter of the words of the ayah at [position]. */
    fun hideMore(position: Int)

    /** A tap on the hidden word [word] of the ayah at [position]: it opens and counts as one peek. */
    fun peekWord(position: Int, word: Int)

    /** Holding the whole ayah at [position] to peek: counts as two peeks. */
    fun peekAyah(position: Int)

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
            MarkControls(host = host, session = session)
        } else {
            RevealControls(host = host, session = session, firstAyah = session.markedCount == 0)
        }
    }
}

// -- Before the reveal --------------------------------------------------------------------------------------

/** How "Listen" and the main action share the width: side by side with a named Listen, with a round Listen, or stacked. */
private enum class RevealLayout { Wide, Narrow, Stacked }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevealControls(host: RecallHost, session: HifzSession, firstAyah: Boolean) {
    val copy = host.copy
    val colors = QhTheme.colors
    val listening by host.listening.collectAsState()
    val fontScale = LocalDensity.current.fontScale
    val item = session.current
    val mode = host.hintMode
    val words = item?.let { host.words(it.inSurah) }
    // The progressive hint: "Hide more" takes the place of "Reveal" until the ayah is hidden entirely, and the answers are on offer
    // from the first hidden quarter on.
    val step = item?.hideStep ?: 0
    val hidesMore = mode == HifzHintMode.PROGRESSIVE && words != null && RecallHints.canHideMore(words.wordCount, step)
    var hintOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = copy.reciteFromMemory, style = QhTheme.type.callout, color = colors.inkSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val layout = when {
                fontScale > LargeFontScale -> RevealLayout.Stacked
                maxWidth >= WideRevealMinWidth -> RevealLayout.Wide
                else -> RevealLayout.Narrow
            }
            val action: @Composable (Modifier) -> Unit = { modifier ->
                if (hidesMore) {
                    HideMoreButton(copy, host, session.index, modifier)
                } else {
                    RevealButton(copy, host, modifier)
                }
            }
            when (layout) {
                RevealLayout.Stacked -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    action(Modifier.fillMaxWidth())
                    ListenButton(copy, listening, host, Modifier.fillMaxWidth())
                }
                RevealLayout.Wide -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenButton(copy, listening, host)
                    action(Modifier.weight(1f))
                }
                RevealLayout.Narrow -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListenIconButton(copy, listening, host)
                    action(Modifier.weight(1f))
                }
            }
        }
        if (hidesMore && step > 0) {
            AnswerPills(host = host, item = item, position = session.index, early = true)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            HintChip(label = copy.hintChip(mode), onClick = { hintOpen = true }, modifier = Modifier.align(Alignment.CenterVertically))
            if (mode == HifzHintMode.PROGRESSIVE && step > 0) {
                Text(
                    text = copy.hiddenShare(RecallHints.percentAt(step)),
                    style = QhTheme.type.label,
                    color = colors.inkSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (firstAyah) {
            Text(text = copy.peekHint, style = QhTheme.type.label, color = colors.inkSecondary)
        }
        if (item != null && item.suggestsHard) {
            Text(text = copy.peeked(item.peeks), style = QhTheme.type.label, color = colors.inkSecondary)
        }
    }
    if (hintOpen) {
        HintSheet(copy = copy, current = mode, ayah = words, onPick = host::setHintMode, onDismiss = { hintOpen = false })
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
private fun HideMoreButton(copy: HifzCopy, host: RecallHost, position: Int, modifier: Modifier = Modifier) {
    QhButton(
        text = copy.hideMore,
        onClick = { host.hideMore(position) },
        modifier = modifier,
        size = QhButtonSize.L,
        icon = Icons.Rounded.VisibilityOff,
    )
}

/** "Hint: First word": the level that is in force; a tap opens the sheet of the five levels. */
@Composable
private fun HintChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, highlight = false, onClickLabel = label)
            .clip(shape)
            .background(colors.surfaceMuted)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.gold, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = QhTheme.type.label,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
    }
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
 * of the passage comes at the end. An ayah that was peeked at twice or more ([HifzItem.suggestsHard]) is offered "Hard" as the
 * main (filled) button, with "I remember" next to it as the quiet one, and says how much was peeked: the person still decides.
 */
@Composable
private fun MarkControls(host: RecallHost, session: HifzSession) {
    val copy = host.copy
    val colors = QhTheme.colors
    val item = session.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = copy.markTitle, style = QhTheme.type.callout, color = colors.inkSecondary)
        if (item != null) {
            AnswerPills(host = host, item = item, position = session.index, early = false)
            InlineHint(id = Hints.LessonGrade, text = rememberHintsCopy().lessonGrade)
            if (item.suggestsHard) {
                Text(text = copy.peeked(item.peeks), style = QhTheme.type.label, color = colors.inkSecondary)
            }
        }
    }
}

/** The two answers; [early] is before the reveal (the progressive hint, from the first hidden quarter on). */
@Composable
private fun AnswerPills(host: RecallHost, item: HifzItem, position: Int, early: Boolean, modifier: Modifier = Modifier) {
    val copy = host.copy
    val fontScale = LocalDensity.current.fontScale
    val hints = LocalHints.current
    val onMark: (AyahMark) -> Unit = { mark ->
        hints.markUsed(Hints.LessonGrade)
        if (early) host.markEarly(mark, position) else host.mark(mark, position)
    }
    val hardFirst = item.suggestsHard
    if (fontScale > LargeFontScale) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RememberedPill(copy, hardFirst, onMark, Modifier.fillMaxWidth())
            HardPill(copy, hardFirst, onMark, Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RememberedPill(copy, hardFirst, onMark, Modifier.weight(1f))
            HardPill(copy, hardFirst, onMark, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RememberedPill(copy: HifzCopy, quiet: Boolean, onMark: (AyahMark) -> Unit, modifier: Modifier) {
    val colors = QhTheme.colors
    MarkPill(
        label = copy.remembered,
        container = if (quiet) colors.primaryContainer else colors.primary,
        content = if (quiet) colors.onPrimaryContainer else colors.onPrimary,
        dot = null,
        haptic = HapticKind.Tick,
        onClick = { onMark(AyahMark.REMEMBERED) },
        modifier = modifier,
    )
}

@Composable
private fun HardPill(copy: HifzCopy, main: Boolean, onMark: (AyahMark) -> Unit, modifier: Modifier) {
    val colors = QhTheme.colors
    MarkPill(
        label = copy.hardMark,
        container = if (main) colors.due else colors.dueContainer,
        content = if (main) colors.onDanger else colors.ink,
        dot = if (main) null else colors.due,
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
