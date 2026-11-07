@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.DotSpinner
import tj.app.quran_todo.ui.components.softShadow
import tj.app.quran_todo.ui.home.MiniPlayerUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Height of the floating pill: 64dp, taller when the system font is large so two lines of text fit. */
internal fun miniPlayerHeight(fontScale: Float): Dp = max(64f, 40f * fontScale + 16f).dp

/** Gap between the pill and the screen edges (and the navigation bar). */
internal val MiniPlayerMargin = 12.dp

/** Room kept below the last row of the list, and above the mini player when it is shown. */
private val ListEndSpace = 24.dp

/**
 * Bottom padding of the list: the navigation bar, plus the pill with its margins while the mini
 * player is visible, plus [ListEndSpace]. The last surah can then scroll fully above the pill.
 */
internal fun listBottomPadding(navigationInset: Dp, playerVisible: Boolean, fontScale: Float): Dp =
    navigationInset +
        (if (playerVisible) miniPlayerHeight(fontScale) + MiniPlayerMargin * 2 else 0.dp) +
        ListEndSpace

/** Distance from the screen bottom to the top edge of the mini player pill. */
internal fun miniPlayerTop(navigationInset: Dp, fontScale: Float): Dp =
    navigationInset + MiniPlayerMargin + miniPlayerHeight(fontScale)

/**
 * The floating mini player: a pill with the play button (ringed by the progress of the ayah), the
 * surah, "Ayah 12 · 1.25×" and a close button. Slides up with a spring when audio starts, down when
 * it stops; drag it down to stop; tap it to open the reader of the playing surah (it grows into the
 * reader's player dock via `SharedKeys.PlayerDock` / `PlayButton`).
 *
 * [progress] is read only inside the play button ring, so the 5 updates a second never recompose
 * anything else. Position the host with [modifier] (bottom center); [bottomInset] is the navigation bar.
 */
@Composable
internal fun MiniPlayerHost(
    player: MiniPlayerUi?,
    progress: State<Float>,
    copy: HomeCopy,
    onOpen: (MiniPlayerUi) -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    // Keep drawing the last player while it slides out.
    val held = remember { arrayOfNulls<MiniPlayerUi>(1) }
    if (player != null) held[0] = player
    val shown = player ?: held[0]
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = player != null,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(tween(QhMotion.Short))
        } else {
            slideInVertically(QhMotion.bounds()) { it } + fadeIn(tween(QhMotion.Short))
        },
        exit = if (reduced) {
            fadeOut(tween(QhMotion.Short))
        } else {
            slideOutVertically(QhMotion.exit(QhMotion.Medium)) { it } + fadeOut(tween(QhMotion.Short))
        },
    ) {
        if (shown != null) {
            // The margins are inside the animated box so the pill starts just below the screen edge.
            Box(
                Modifier
                    .padding(horizontal = MiniPlayerMargin)
                    .padding(bottom = bottomInset + MiniPlayerMargin),
            ) {
                MiniPlayerPill(
                    player = shown,
                    progress = progress,
                    copy = copy,
                    onOpen = { onOpen(shown) },
                    onToggle = onToggle,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerPill(
    player: MiniPlayerUi,
    progress: State<Float>,
    copy: HomeCopy,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val shape = QhTheme.shapes.full
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val dismissDistance = with(density) { DismissDistance.toPx() }
    val returnSpec = QhMotion.snappy<Float>().orSnap()
    val height = miniPlayerHeight(density.fontScale)

    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .height(height)
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (drag.value > dismissDistance || velocity > DismissVelocity) {
                        onStop()
                    } else {
                        drag.animateTo(0f, returnSpec)
                    }
                },
            )
            .sharedBoundsKey(SharedKeys.PlayerDock, SharedResize.Remeasure)
            .softShadow(shape)
            .pressable(onClick = onOpen, shape = shape, onClickLabel = copy.openPlayer)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(start = QhSpacing.x2, end = QhSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayButton(player = player, progress = progress, copy = copy, onToggle = onToggle)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = QhSpacing.x3),
        ) {
            Text(
                text = player.name,
                style = type.title2.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (player.hasError) copy.audioError else copy.playerSub(player.ayah, player.speed),
                style = type.callout,
                color = if (player.hasError) colors.danger else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(QhSpacing.minTouch)
                .pressable(onClick = onStop, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = copy.stop,
                tint = colors.inkSecondary,
                modifier = Modifier.size(QhSpacing.iconInline),
            )
        }
    }
}

private val DismissDistance = 56.dp
private const val DismissVelocity = 1_200f

private enum class PlayGlyph { Preparing, Error, Playing, Paused }

/** Play / pause in a 36dp disc inside a ring that fills as the ayah plays. */
@Composable
private fun PlayButton(
    player: MiniPlayerUi,
    progress: State<Float>,
    copy: HomeCopy,
    onToggle: () -> Unit,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val glyph = when {
        player.hasError -> PlayGlyph.Error
        player.isPreparing -> PlayGlyph.Preparing
        player.isPlaying -> PlayGlyph.Playing
        else -> PlayGlyph.Paused
    }
    val label = if (player.isPlaying) copy.pause else copy.play

    // The ring follows the ayah smoothly between the 200ms position updates and jumps back at once.
    val smooth = remember { Animatable(progress.value) }
    LaunchedEffect(reduced) {
        snapshotFlow { progress.value }.collectLatest { target ->
            if (reduced || target < smooth.value) {
                smooth.snapTo(target)
            } else {
                smooth.animateTo(target, tween(RingMillis, easing = LinearEasing))
            }
        }
    }
    val track = colors.hairline
    val arc = colors.primary

    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .sharedElementKey(SharedKeys.PlayButton)
            .pressable(onClick = onToggle, shape = CircleShape, onClickLabel = label)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(track, 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            val sweep = 360f * smooth.value
            if (sweep > 0f) {
                drawArc(arc, -90f, sweep, false, topLeft, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = glyph,
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (scaleIn(QhMotion.snappy(), initialScale = 0.5f) + fadeIn(QhMotion.snappy())) togetherWith
                            (scaleOut(QhMotion.snappy(), targetScale = 0.5f) + fadeOut(QhMotion.snappy()))
                    }
                },
                label = "mini-player-glyph",
            ) { target ->
                when (target) {
                    PlayGlyph.Preparing -> DotSpinner(colors.onPrimary)
                    PlayGlyph.Error -> GlyphIcon(Icons.Rounded.Refresh)
                    PlayGlyph.Playing -> GlyphIcon(Icons.Rounded.Pause)
                    PlayGlyph.Paused -> GlyphIcon(Icons.Rounded.PlayArrow)
                }
            }
        }
    }
}

@Composable
private fun GlyphIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = QhTheme.colors.onPrimary, modifier = Modifier.size(22.dp))
}

private const val RingMillis = 220
