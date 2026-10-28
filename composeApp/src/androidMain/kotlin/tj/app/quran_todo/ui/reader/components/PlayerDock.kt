package tj.app.quran_todo.ui.reader.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.playback.SleepTimer
import tj.app.quran_todo.ui.reader.PlayerUi
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.theme.QhTheme

/** Height of the collapsed dock without the navigation bar inset. */
val DockHeaderHeight = 76.dp

/** The expanded dock takes this share of the screen height ("up to ~70%"). */
private const val ExpandedFraction = 0.72f

/** Share of the distance the dock has to travel before a release lets it settle on the other side. */
private const val SettleThreshold = 0.35f

/**
 * The player at the bottom of the reader. Collapsed it is a 76dp bar (the ayah, a status line and the
 * transport); dragged up, or tapped, it expands over the list to about 72% of the screen with the passage
 * repeat, pause, speed and sleep timer. It is one `AnchoredDraggable` surface, so it follows the finger and
 * settles on the nearest anchor with a spring. Position and duration of the audio are read only inside
 * leaves of this tree (a progress line, a time label), never by the dock itself.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerDock(
    state: PlayerDockState,
    viewModel: ReaderViewModel,
    ui: PlayerUi,
    prefs: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    surah: SurahWithAyahs,
    showTranslation: Boolean,
    copy: ReaderCopy,
    origin: String,
    screenHeight: Dp,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    /** False in the memorize mode: the dock stays a collapsed bar and can neither be dragged nor tapped open. */
    expandable: Boolean = true,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val containerHeight = screenHeight * ExpandedFraction
    var headerPx by remember { mutableIntStateOf(with(density) { DockHeaderHeight.roundToPx() }) }
    val collapsedPx = headerPx + with(density) { navInset.roundToPx() }
    val containerPx = with(density) { containerHeight.roundToPx() }

    SideEffect {
        state.containerHeightPx = containerPx.toFloat()
        state.collapsedHeightPx = collapsedPx.toFloat()
    }
    val anchors = remember(containerPx, collapsedPx) {
        DraggableAnchors {
            DockValue.Collapsed at (containerPx - collapsedPx).coerceAtLeast(0).toFloat()
            DockValue.Expanded at 0f
        }
    }
    SideEffect { state.draggable.updateAnchors(anchors) }

    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state.draggable,
        positionalThreshold = { distance -> distance * SettleThreshold },
        animationSpec = QhMotion.snappy(),
    )
    val connection = remember(state, flingBehavior) { state.nestedScrollConnection(flingBehavior) }

    BackHandler(enabled = state.isExpanding) { scope.launch { state.collapse() } }
    LaunchedEffect(expandable) { if (!expandable && state.isExpanding) state.collapse() }

    // A small tick when the dock lands on the other anchor, so dragging it feels like moving a real thing.
    val haptics = LocalHaptics.current
    LaunchedEffect(state) {
        snapshotFlow { state.draggable.settledValue }
            .drop(1)
            .collect { haptics.perform(HapticKind.Segment) }
    }

    // The expanded content (two wheels, chips) is composed shortly after the reader has settled, so the
    // first drag upwards does not have to build it.
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900)
        warm = true
    }
    val bodyVisible by remember { derivedStateOf { warm || state.progress > 0.001f } }

    Column(
        modifier
            .fillMaxWidth()
            .height(containerHeight)
            .offset {
                // Until the anchors exist the dock rests collapsed (never a frame of the expanded dock).
                val offset = state.draggable.offset
                val px = if (offset.isNaN()) (containerPx - collapsedPx).coerceAtLeast(0).toFloat() else offset
                IntOffset(0, px.roundToInt())
            }
            .then(if (expandable) Modifier.nestedScroll(connection) else Modifier)
            .anchoredDraggable(
                state = state.draggable,
                orientation = Orientation.Vertical,
                enabled = expandable,
                flingBehavior = flingBehavior,
            ),
    ) {
        DockHeader(
            state = state,
            viewModel = viewModel,
            ui = ui,
            prefs = prefs,
            practice = practice,
            totalAyahs = surah.ayahs.size,
            copy = copy,
            origin = origin,
            onPlay = onPlay,
            expandable = expandable,
            onToggleExpanded = {
                scope.launch { if (state.isExpanding) state.collapse() else state.expand() }
            },
            modifier = Modifier.onSizeChanged { headerPx = it.height },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(QhTheme.colors.readerSurface),
        ) {
            if (bodyVisible) {
                DockBody(
                    viewModel = viewModel,
                    ui = ui,
                    prefs = prefs,
                    practice = practice,
                    surah = surah,
                    showTranslation = showTranslation,
                    copy = copy,
                    navInset = navInset,
                    onPlay = onPlay,
                )
            }
        }
    }
}

// -- Header -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DockHeader(
    state: PlayerDockState,
    viewModel: ReaderViewModel,
    ui: PlayerUi,
    prefs: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    totalAyahs: Int,
    copy: ReaderCopy,
    origin: String,
    onPlay: () -> Unit,
    expandable: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xlTop
    val audioProgress = viewModel.audioProgress.collectAsState()
    val pauseRemaining = viewModel.pauseRemainingSeconds.collectAsState()
    val showControls by remember { derivedStateOf { state.progress < 0.5f } }
    val shared = origin == Origin.PLAYER
    val number = ui.index + 1

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = DockHeaderHeight)
            .then(if (shared) Modifier.sharedBoundsKey(SharedKeys.PlayerDock, SharedResize.Remeasure) else Modifier)
            .clip(shape)
            .background(colors.readerSurface)
            .drawWithContent {
                drawContent()
                // The progress of the ayah runs along the top edge, under the rounded corners.
                val fraction = audioProgress.value
                if (fraction > 0f) {
                    drawRect(
                        color = colors.primary,
                        topLeft = Offset.Zero,
                        size = Size(size.width * fraction, 2.dp.toPx()),
                    )
                }
                // Hairline around the top: up the left side, over the corners, down the right side.
                val radius = 28.dp.toPx()
                val outline = Path().apply {
                    moveTo(0.5f, size.height)
                    lineTo(0.5f, radius)
                    arcTo(Rect(0.5f, 0.5f, radius * 2f, radius * 2f), 180f, 90f, false)
                    lineTo(size.width - radius, 0.5f)
                    arcTo(Rect(size.width - radius * 2f, 0.5f, size.width - 0.5f, radius * 2f), 270f, 90f, false)
                    lineTo(size.width - 0.5f, size.height)
                }
                drawPath(outline, colors.hairline, style = Stroke(1.dp.toPx()))
            },
    ) {
        if (expandable) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.hairline),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .pressable(
                        onClick = onToggleExpanded,
                        enabled = expandable,
                        scale = 0.99f,
                        shape = QhTheme.shapes.md,
                        onClickLabel = if (state.isExpanding) copy.collapsePlayer else copy.expandPlayer,
                    )
                    .semantics {
                        stateDescription = "${copy.ayah} $number ${copy.ofWord} $totalAyahs"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AyahRosette(number = number, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val titleStyle = QhTheme.type.title2
                        Text("${copy.ayah} ", style = titleStyle, color = colors.ink)
                        AnimatedCounter(value = number, style = titleStyle, color = colors.ink)
                        Text(" ${copy.ofWord} $totalAyahs", style = titleStyle, color = colors.ink, maxLines = 1)
                    }
                    DockStatusLine(
                        ui = ui,
                        prefs = prefs,
                        practice = practice,
                        pauseRemaining = pauseRemaining,
                        viewModel = viewModel,
                        copy = copy,
                    )
                }
            }
            Box(Modifier.width(IntrinsicControlsWidth), contentAlignment = Alignment.CenterEnd) {
                if (showControls) {
                    Row(
                        Modifier.graphicsLayer { alpha = (1f - state.progress * 2f).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QhIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = copy.previous,
                            onClick = viewModel::previousAyah,
                            filled = false,
                        )
                        DockPlayButton(
                            ui = ui,
                            pauseRemaining = pauseRemaining,
                            pauseTotal = practice.pauseSeconds,
                            size = 48.dp,
                            copy = copy,
                            shared = shared,
                            onClick = onPlay,
                        )
                        QhIconButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = copy.next,
                            onClick = viewModel::nextAyah,
                            filled = false,
                        )
                    }
                } else {
                    QhIconButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = copy.collapsePlayer,
                        onClick = onToggleExpanded,
                        modifier = Modifier.graphicsLayer { alpha = ((state.progress - 0.5f) * 2f).coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

/** Previous (48) + play with its countdown ring (48 + 2 x 5) + next (48). */
private val IntrinsicControlsWidth = 154.dp

/**
 * The line under "Ayah 12 of 286": the reciter, a non-default speed, the repeated passage and the sleep
 * timer; "Your turn · 3 s" during the pause between ayahs; "No connection · Retry" after an audio error.
 * It is a leaf: only it recomposes when the pause countdown ticks.
 */
@Composable
private fun DockStatusLine(
    ui: PlayerUi,
    prefs: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    pauseRemaining: State<Int>,
    viewModel: ReaderViewModel,
    copy: ReaderCopy,
) {
    val colors = QhTheme.colors
    val style = QhTheme.type.callout
    val waiting = pauseRemaining.value
    when {
        ui.hasError -> Row(
            Modifier
                .padding(top = 2.dp)
                .pressable(
                    onClick = viewModel::retry,
                    haptic = HapticKind.Reject,
                    shape = QhTheme.shapes.full,
                    scale = 0.96f,
                )
                .clip(QhTheme.shapes.full)
                .background(colors.dueContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.due, modifier = Modifier.size(14.dp))
            Text(copy.noConnectionRetry, style = style, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ui.active && waiting > 0 -> Text(
            text = "${copy.yourTurn} · $waiting ${copy.secondsShort}",
            style = style,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusSummary(prefs, practice, copy),
                style = style,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            SleepBadge(viewModel)
        }
    }
}

/** A small moon and the minutes left, shown in the status line while a sleep timer runs. */
@Composable
private fun SleepBadge(viewModel: ReaderViewModel) {
    val timer by viewModel.sleepTimer.collectAsState()
    val colors = QhTheme.colors
    if (timer == SleepTimer.Off) return
    val remaining = viewModel.sleepRemainingSeconds.collectAsState()
    val minutes by remember { derivedStateOf { (remaining.value + 59) / 60 } }
    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = colors.gold, modifier = Modifier.size(14.dp))
        if (timer is SleepTimer.Minutes) {
            Text(
                text = " $minutes",
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 1,
            )
        }
    }
}

private fun statusSummary(prefs: SurahReaderPreferences, practice: ReaderPracticeSettings, copy: ReaderCopy): String =
    buildString {
        append(copy.reciter)
        if (prefs.playbackSpeed != 1f) append(" · ").append(speedLabel(prefs.playbackSpeed))
        if (practice.rangeEnabled) {
            append(" · ").append(copy.repeatLabel("${practice.startAyah}–${practice.endAyah}", repeatLabel(practice.rangeRepeatCount)))
        } else if (prefs.repeatCount != 1) {
            append(" · ↻ ").append(repeatLabel(prefs.repeatCount))
        }
    }

internal fun speedLabel(speed: Float): String = "${speed.toString().removeSuffix(".0")}×"

internal fun repeatLabel(count: Int): String = if (count == -1) "∞" else "$count×"

// -- Play button ----------------------------------------------------------------------------------------------

/**
 * The round play/pause button. While the pause between ayahs runs a ring around it counts the time down;
 * while audio is being prepared the icon gives way to three dots. The icon morphs with scale and rotation.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun DockPlayButton(
    ui: PlayerUi,
    pauseRemaining: State<Int>,
    pauseTotal: Int,
    size: Dp,
    copy: ReaderCopy,
    shared: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val playing = ui.isPlaying && ui.active
    val ringPadding = 5.dp

    Box(
        modifier
            .size(size + ringPadding * 2)
            .then(if (shared) Modifier.sharedElementKey(SharedKeys.PlayButton) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PauseRing(pauseRemaining = pauseRemaining, total = pauseTotal, active = ui.active && playing)
        Box(
            Modifier
                .size(size)
                .pressable(
                    onClick = onClick,
                    shape = CircleShape,
                    highlight = false,
                    onClickLabel = if (playing) copy.pause else copy.play,
                )
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (ui.isPreparing && ui.active) {
                tj.app.quran_todo.ui.components.DotSpinner(colors.onPrimary)
            } else {
                AnimatedContent(
                    targetState = playing,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(QhMotion.Short)) + scaleIn(QhMotion.snappy(), initialScale = 0.5f)) togetherWith
                                (fadeOut(tween(QhMotion.Instant)) + scaleOut(tween(QhMotion.Instant), targetScale = 0.5f))
                        }
                    },
                    label = "play-icon",
                ) { isPlaying ->
                    val rotation by transition.animateFloat(
                        transitionSpec = { tween(QhMotion.Medium, easing = QhMotion.Emphasized) },
                        label = "play-rotation",
                    ) { enterExit ->
                        if (reduced) {
                            0f
                        } else {
                            when (enterExit) {
                                EnterExitState.PreEnter -> if (isPlaying) -60f else 60f
                                EnterExitState.Visible -> 0f
                                EnterExitState.PostExit -> if (isPlaying) 60f else -60f
                            }
                        }
                    }
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) copy.pause else copy.play,
                        tint = colors.onPrimary,
                        modifier = Modifier
                            .size(size * 0.55f)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            }
        }
    }
}

/** The countdown ring of the pause between ayahs: full when the pause starts, empty when it ends. */
@Composable
private fun PauseRing(pauseRemaining: State<Int>, total: Int, active: Boolean) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val remaining = pauseRemaining.value
    val sweep = remember { Animatable(0f) }
    val visible = active && total > 0 && remaining > 0
    LaunchedEffect(remaining, total, visible) {
        if (!visible) {
            sweep.snapTo(0f)
            return@LaunchedEffect
        }
        if (remaining >= total) sweep.snapTo(1f)
        val target = ((remaining - 1).coerceAtLeast(0)).toFloat() / total
        if (reduced) sweep.snapTo(target) else sweep.animateTo(target, tween(1_000, easing = LinearEasing))
    }
    if (!visible && sweep.value <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val diameter = size.minDimension - stroke
        val topLeft = Offset(inset + (size.width - size.minDimension) / 2f, inset + (size.height - size.minDimension) / 2f)
        drawArc(
            color = colors.primary.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(stroke),
        )
        drawArc(
            color = colors.primary,
            startAngle = -90f,
            sweepAngle = 360f * sweep.value,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
