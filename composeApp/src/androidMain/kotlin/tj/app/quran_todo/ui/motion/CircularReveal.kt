package tj.app.quran_todo.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.theme.LocalThemeModeSetter
import tj.app.quran_todo.common.theme.LocalThemePaletteSetter
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette

/**
 * The circular theme reveal (docs/redesign/ANDROID_REDESIGN.md, 6.6).
 *
 * Right before the theme changes, [ThemeReveal] snapshots the whole UI (`GraphicsLayer.toImageBitmap`), lets the
 * new theme compose underneath and draws the old frame on top with an expanding circle cut out of it, centered
 * on the touch point: 520 ms with the `Emphasized` curve. With reduced motion the old frame just fades out in
 * 150 ms. The root of the app owns one instance ([ThemeRevealHost]); screens use [rememberThemeSwitcher].
 */
@Stable
class ThemeReveal internal constructor() {
    /** Where a finger last went down, in screen pixels; [Offset.Unspecified] until a touch was seen. */
    var lastTouch: Offset = Offset.Unspecified

    internal var layer: GraphicsLayer? = null
    internal var root: LayoutCoordinates? = null
    internal var scope: CoroutineScope? = null
    internal var reduced: Boolean = false

    /** Whether the theme on screen right now is dark; kept up to date by the root. */
    internal var currentDark: Boolean = false

    internal var frame: ImageBitmap? by mutableStateOf(null)
    internal var center: Offset by mutableStateOf(Offset.Zero)
    internal val progress = Animatable(0f)
    private var oldDark = false
    private var busy = false

    /**
     * Runs [change] (the theme state change) under a circular reveal that starts at [origin] (screen px; the last
     * touch by default). Without a host, with a running reveal, or if the snapshot fails, [change] just runs.
     */
    fun reveal(origin: Offset = lastTouch, change: () -> Unit) {
        val layer = layer
        val scope = scope
        if (layer == null || scope == null || busy) {
            change()
            return
        }
        busy = true
        scope.launch {
            var applied = false
            try {
                val bitmap = try {
                    layer.toImageBitmap()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                }
                if (bitmap == null) return@launch
                val size = root?.size ?: IntSize(bitmap.width, bitmap.height)
                center = toRootLocal(origin, size)
                oldDark = currentDark
                progress.snapTo(0f)
                // Same frame: the old picture goes on top and the new theme starts composing under it.
                frame = bitmap
                applied = true
                change()
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = if (reduced) {
                        tween(ReducedMillis, easing = LinearEasing)
                    } else {
                        tween(QhMotion.Hero, easing = QhMotion.Emphasized)
                    },
                )
            } finally {
                if (!applied) change()
                frame = null
                busy = false
            }
        }
    }

    /**
     * Whether a system bar draws light icons (dark theme) while the reveal runs: the bar keeps the old style until
     * the circle has reached it, then takes the new one, so its icons are never lost on the wrong background.
     */
    internal fun barIsDark(newDark: Boolean, top: Boolean): Boolean {
        if (frame == null || reduced) return newDark
        val size = root?.size ?: return newDark
        val point = Offset(size.width / 2f, if (top) 0f else size.height.toFloat())
        val covered = (point - center).getDistance() <= maxRadius(center, size) * progress.value
        return if (covered) newDark else oldDark
    }

    private fun toRootLocal(origin: Offset, size: IntSize): Offset {
        val root = root
        val local = if (origin.isUnspecified || root == null || !root.isAttached) {
            Offset(size.width / 2f, size.height / 2f)
        } else {
            origin - root.positionOnScreen()
        }
        return Offset(local.x.coerceIn(0f, size.width.toFloat()), local.y.coerceIn(0f, size.height.toFloat()))
    }

    private companion object {
        const val ReducedMillis = 150
    }
}

/** The app's [ThemeReveal]; a host-less instance (previews, catalog) only runs the change. */
val LocalThemeRevealOrigin = staticCompositionLocalOf { ThemeReveal() }

@Composable
fun rememberThemeReveal(): ThemeReveal = remember { ThemeReveal() }

/**
 * Wraps the whole UI: records it into a graphics layer for the snapshot, draws the reveal over it, provides
 * [LocalThemeRevealOrigin] and tracks where fingers go down in this window (see [themeRevealOriginTracker]
 * for other windows such as sheets).
 */
@Composable
fun ThemeRevealHost(
    reveal: ThemeReveal,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    SideEffect {
        reveal.layer = layer
        reveal.scope = scope
        reveal.reduced = reduced
    }
    CompositionLocalProvider(LocalThemeRevealOrigin provides reveal) {
        Box(
            modifier
                .onGloballyPositioned { reveal.root = it }
                .themeRevealOriginTracker(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    // Inside the recorded content: the snapshot must include the window colour too.
                    .background(background),
                content = content,
            )
            RevealOverlay(reveal)
        }
    }
}

@Composable
private fun RevealOverlay(reveal: ThemeReveal) {
    val bitmap = reveal.frame ?: return
    Canvas(
        Modifier
            .fillMaxSize()
            // The hole is punched with BlendMode.Clear, which needs its own offscreen buffer.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val p = reveal.progress.value
        if (reveal.reduced) {
            drawImage(bitmap, alpha = 1f - p)
        } else {
            drawImage(bitmap)
            drawCircle(
                color = Color.Black,
                radius = maxRadius(reveal.center, size.toIntSize()) * p,
                center = reveal.center,
                blendMode = BlendMode.Clear,
            )
        }
    }
}

private fun androidx.compose.ui.geometry.Size.toIntSize(): IntSize = IntSize(width.toInt(), height.toInt())

/** Distance from [center] to the farthest corner: the radius at which the circle covers everything. */
private fun maxRadius(center: Offset, size: IntSize): Float {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    return maxOf(
        hypot(center.x, center.y),
        hypot(w - center.x, center.y),
        hypot(center.x, h - center.y),
        hypot(w - center.x, h - center.y),
    )
}

/**
 * Remembers where a finger goes down inside this element (screen pixels) into [LocalThemeRevealOrigin],
 * without consuming anything. The root has it already; put it on the content of a sheet or dialog, which are
 * separate windows that the root never sees.
 */
fun Modifier.themeRevealOriginTracker(): Modifier = composed {
    val reveal = LocalThemeRevealOrigin.current
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(reveal) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
                    val coordinates = holder[0]
                    if (coordinates != null && coordinates.isAttached) {
                        reveal.lastTouch = coordinates.positionOnScreen() + down.position
                    }
                }
            }
        }
}

/**
 * What screens call to change the theme with the circular reveal:
 *
 * ```
 * val switcher = rememberThemeSwitcher()
 * switcher.switch(ThemeMode.DARK)               // from the last touch
 * switcher.switch(ThemeMode.DARK, origin)       // from an explicit point (screen px)
 * ```
 */
@Stable
class ThemeSwitcher internal constructor(
    private val reveal: ThemeReveal,
    private val setMode: (ThemeMode) -> Unit,
    private val setPalette: (ThemePalette) -> Unit,
) {
    fun switch(mode: ThemeMode, origin: Offset = reveal.lastTouch) = reveal.reveal(origin) { setMode(mode) }

    fun switchPalette(palette: ThemePalette, origin: Offset = reveal.lastTouch) =
        reveal.reveal(origin) { setPalette(palette) }

    /** Any other change that repaints the whole UI (e.g. "follow the system theme"). */
    fun reveal(origin: Offset = reveal.lastTouch, change: () -> Unit) = reveal.reveal(origin, change)
}

@Composable
fun rememberThemeSwitcher(): ThemeSwitcher {
    val reveal = LocalThemeRevealOrigin.current
    val setMode = LocalThemeModeSetter.current
    val setPalette = LocalThemePaletteSetter.current
    // The setters are new lambdas on every root recomposition: always call the latest ones.
    val modeSetter = rememberUpdatedState(setMode)
    val paletteSetter = rememberUpdatedState(setPalette)
    return remember(reveal) {
        ThemeSwitcher(reveal, { modeSetter.value(it) }, { paletteSetter.value(it) })
    }
}
