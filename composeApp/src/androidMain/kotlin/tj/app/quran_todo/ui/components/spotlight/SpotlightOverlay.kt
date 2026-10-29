package tj.app.quran_todo.ui.components.spotlight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.i18n.SpotlightCopy
import tj.app.quran_todo.ui.i18n.rememberSpotlightCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The target grows by this much on every side to make the cutout. */
internal val SpotlightCutoutPadding = 8.dp

/** The card never gets closer to the left and right edges than this. */
private val CardMargin = 16.dp
private val CardMaxWidth = 340.dp

/** Between the cutout and the card, where the arrow sits. */
private val CardGap = 14.dp
private val ArrowWidth = 18.dp
private val ArrowHeight = 9.dp

/** The arrow keeps this far from the corners of the card. */
private val ArrowInset = 28.dp

/** Pulse of the ring around the cutout. */
private val RingSpread = 7.dp
private val RingWidth = 2.dp
private const val PulseMillis = 1_400

/** How often the bounds of the current target are read while the tour runs. */
private const val TrackMillis = 32L

/** A target that is gone for this long ends its step. A target that can scroll itself into view gets more. */
private const val MissingGraceMillis = 700L
private const val RevealGraceMillis = 1_800L

/** The step on screen: its index in the tour, the live bounds of its target and the shape of the cutout. */
private data class ShownStep(val index: Int, val step: SpotlightStep, val rect: Rect, val shape: SpotlightShape)

/**
 * The tour on screen: the scrim with a cutout around the target, a soft pulsing ring, and the card with the words, the
 * arrow and the buttons. A tap anywhere outside the card goes to the next step (what is under it is not touched); "Skip"
 * and the system Back end the tour.
 */
@Composable
internal fun SpotlightOverlay(
    controller: SpotlightController,
    run: SpotlightRun,
    host: SpotlightHostRef,
) {
    val copy = rememberSpotlightCopy()
    val reduced = LocalReducedMotion.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val colors = QhTheme.colors

    // Back ends the tour (and counts it as seen). Registered only while the overlay is composed, after the NavHost, so it comes first.
    BackHandler(enabled = controller.run != null) { controller.skip() }

    // -- The step on screen ---------------------------------------------------------------------------------
    var shown by remember { mutableStateOf<ShownStep?>(null) }
    val latestRun by rememberUpdatedState(run)
    LaunchedEffect(run.tour.id, run.index) {
        val step = latestRun.step
        val index = latestRun.index
        var missingMillis = 0L
        var announced = false
        var revealing = false
        while (true) {
            val viewport = host.viewport()
            val coordinates = host.coordinates
            val info = if (viewport != null && coordinates != null) controller.registry.find(step.targetId, coordinates) else null
            if (info != null && viewport != null && SpotlightSteps.isVisible(info.bounds, viewport)) {
                missingMillis = 0L
                val next = ShownStep(index, step, info.bounds.rect, step.shape ?: info.shape)
                if (shown != next) shown = next
                if (!announced) {
                    announced = true
                    controller.stepShown(index)
                    haptics.perform(HapticKind.Tick)
                }
            } else {
                // While the next target is being brought into view (the list scrolls under the scrim) the cutout keeps
                // following the target of the step before, so it does not stay behind in empty space.
                val previous = shown
                if (previous != null && previous.index != index && viewport != null && coordinates != null) {
                    val old = controller.registry.find(previous.step.targetId, coordinates)
                    if (old != null && SpotlightSteps.isVisible(old.bounds, viewport)) {
                        val moved = previous.copy(rect = old.bounds.rect)
                        if (moved != previous) shown = moved
                    }
                }
                val reveal = info?.reveal
                if (reveal != null && !revealing) {
                    revealing = true
                    launch {
                        try {
                            reveal()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // The target could not scroll into view: the step is dropped below.
                        }
                    }
                }
                missingMillis += TrackMillis
                if (missingMillis > (if (info?.reveal != null) RevealGraceMillis else MissingGraceMillis)) {
                    controller.dropStep()
                    return@LaunchedEffect
                }
            }
            delay(TrackMillis)
        }
    }

    val current = shown
    // A tap before the step has settled is ignored, so a double tap cannot jump over a step.
    val onNext = { if (current != null && current.index == run.index) controller.advance() }
    val onSkip = { controller.skip() }
    val latestOnNext by rememberUpdatedState(onNext)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { isTraversalGroup = true }
            // Everything under the scrim is out of reach: a tap goes to the next step.
            .pointerInput(Unit) { detectTapGestures(onTap = { latestOnNext() }) },
    ) {
        if (current == null) return@BoxWithConstraints
        val viewportPx = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // -- The cutout flows from step to step; with Reduce Motion it jumps and fades --------------------
        val (cutout, radius) = remember(current.rect, current.shape, density) {
            val circle = current.shape is SpotlightShape.Circle
            val corner = (current.shape as? SpotlightShape.Rounded)?.radius ?: 0.dp
            with(density) { SpotlightLayout.cutout(current.rect, SpotlightCutoutPadding.toPx(), circle, corner.toPx()) }
        }
        val animatedCutout = animateRectAsState(cutout, if (reduced) snap() else QhMotion.bounds(), label = "spotlight-cutout")
        val animatedRadius = animateFloatAsState(radius, if (reduced) snap() else QhMotion.bounds(), label = "spotlight-radius")
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) { scrimAlpha.animateTo(1f, if (reduced) snap() else tween(QhMotion.Medium)) }
        // Reduce Motion: on a new step the cutout opens again by fading in instead of travelling.
        val stepFade = remember { Animatable(1f) }
        LaunchedEffect(current.index, reduced) {
            if (reduced && current.index > 0) {
                stepFade.snapTo(0f)
                stepFade.animateTo(1f, tween(QhMotion.Short))
            } else {
                stepFade.snapTo(1f)
            }
        }
        val pulse = if (reduced) null else rememberInfiniteTransition(label = "spotlight-pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PulseMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "spotlight-pulse-value",
        )

        val scrimColor = remember(colors) { scrimColorOf(colors.ink, colors.bg, colors.isDark) }
        val ringColor = colors.gold
        val spreadPx = with(density) { RingSpread.toPx() }
        val ringWidthPx = with(density) { RingWidth.toPx() }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = scrimAlpha.value
                },
        ) {
            drawRect(scrimColor)
            val rect = animatedCutout.value
            val corner = animatedRadius.value
            drawRoundRect(
                color = Color.Black.copy(alpha = stepFade.value),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(corner),
                blendMode = BlendMode.DstOut,
            )
            // The ring breathes just outside the cutout: 0.35 -> 0.85 opaque, up to RingSpread wide.
            val t = pulse?.value ?: 0.5f
            val spread = spreadPx * t
            val ringAlpha = (0.35f + 0.5f * t) * stepFade.value
            drawRoundRect(
                color = ringColor.copy(alpha = ringAlpha),
                topLeft = Offset(rect.left - spread - ringWidthPx / 2f, rect.top - spread - ringWidthPx / 2f),
                size = Size(rect.width + 2 * spread + ringWidthPx, rect.height + 2 * spread + ringWidthPx),
                cornerRadius = CornerRadius(corner + spread + ringWidthPx / 2f),
                style = Stroke(width = ringWidthPx),
            )
        }

        // -- The card --------------------------------------------------------------------------------------
        val systemBars = WindowInsets.systemBars
        val safeTop = systemBars.getTop(density) + with(density) { 8.dp.toPx() }
        val safeBottom = viewportPx.height - systemBars.getBottom(density) - with(density) { 8.dp.toPx() }
        val cardWidth = minOf(maxWidth - CardMargin * 2, CardMaxWidth)
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        val placement = remember(cutout, cardSize, viewportPx, safeTop, safeBottom, current.step.position, density) {
            with(density) {
                SpotlightLayout.place(
                    cutout = cutout,
                    card = Size(cardSize.width.toFloat(), cardSize.height.toFloat()),
                    viewport = viewportPx,
                    safeTop = safeTop,
                    safeBottom = safeBottom,
                    margin = CardMargin.toPx(),
                    gap = CardGap.toPx(),
                    arrowInset = ArrowInset.toPx(),
                    position = current.step.position,
                )
            }
        }
        val cardOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var positioned by remember { mutableStateOf(false) }
        LaunchedEffect(placement.topLeft, cardSize) {
            if (cardSize == IntSize.Zero) return@LaunchedEffect
            if (!positioned || reduced) {
                cardOffset.snapTo(placement.topLeft)
                positioned = true
            } else {
                cardOffset.animateTo(placement.topLeft, QhMotion.bounds())
            }
        }

        val tip = copy.tip(current.step.tip)
        TipCard(
            copy = copy,
            title = tip.title,
            body = tip.body,
            nextLabel = if (current.index == run.total - 1) copy.gotIt else copy.nextLabel(current.index + 1, run.total),
            below = placement.below,
            arrowX = placement.arrowCenterX?.let { it - placement.topLeft.x },
            onNext = onNext,
            onSkip = onSkip,
            modifier = Modifier
                .width(cardWidth)
                .onSizeChanged { cardSize = it }
                .offset { IntOffset(cardOffset.value.x.roundToInt(), cardOffset.value.y.roundToInt()) }
                .graphicsLayer { alpha = if (positioned) scrimAlpha.value * stepFade.value else 0f },
        )
    }
}

/**
 * The dim layer: the ink of the theme at 70 %. By day that is the ink color; by night the ink color is light, so the veil
 * takes the night paper (the `bg` token) pulled towards black, which keeps it a darkening in both themes.
 */
internal fun scrimColorOf(ink: Color, bg: Color, dark: Boolean): Color =
    (if (dark) lerp(bg, Color.Black, 0.65f) else ink).copy(alpha = ScrimAlpha)

internal const val ScrimAlpha = 0.7f

@Composable
private fun TipCard(
    copy: SpotlightCopy,
    title: String,
    body: String,
    nextLabel: String,
    below: Boolean,
    arrowX: Float?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val arrowWidth = with(LocalDensity.current) { ArrowWidth.toPx() }
    val arrowHeight = with(LocalDensity.current) { ArrowHeight.toPx() }
    val stroke = with(LocalDensity.current) { 1.dp.toPx() }
    Column(
        modifier
            // The arrow is drawn over the border of the card on the side that faces the target.
            .drawWithContent {
                drawContent()
                val x = arrowX ?: return@drawWithContent
                val baseY = if (below) stroke else size.height - stroke
                val tipY = if (below) -arrowHeight else size.height + arrowHeight
                val fill = Path().apply {
                    moveTo(x - arrowWidth / 2f, baseY)
                    lineTo(x, tipY)
                    lineTo(x + arrowWidth / 2f, baseY)
                    close()
                }
                drawPath(fill, colors.surface)
                val edge = Path().apply {
                    moveTo(x - arrowWidth / 2f, baseY)
                    lineTo(x, tipY)
                    lineTo(x + arrowWidth / 2f, baseY)
                }
                drawPath(edge, colors.hairline, style = Stroke(width = stroke))
            }
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            // A tap on the card itself does nothing (a tap outside goes to the next step).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = QhSpacing.cardLarge, end = QhSpacing.cardLarge, top = QhSpacing.cardLarge, bottom = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        // One group for the screen reader: the tip is announced when the step changes, the buttons stay separate.
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            Text(text = title, style = QhTheme.type.title1, color = colors.ink)
            Text(text = body, style = QhTheme.type.body, color = colors.inkSecondary)
        }
        Spacer(Modifier.height(QhSpacing.x1))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhButton(
                text = copy.skip,
                onClick = onSkip,
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.S,
            )
            QhButton(
                text = nextLabel,
                onClick = onNext,
                size = QhButtonSize.M,
            )
        }
    }
}
