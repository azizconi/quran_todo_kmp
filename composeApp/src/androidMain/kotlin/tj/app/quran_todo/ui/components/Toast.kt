package tj.app.quran_todo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** What a [QhToast] says about: it picks the icon and its color. */
enum class ToastKind { Success, Error, Info }

/** One message in flight. */
@Immutable
data class ToastData(
    val id: Long,
    val message: String,
    val kind: ToastKind,
    val durationMillis: Long,
)

/**
 * Shows one toast at a time; a new message replaces the current one. Get the instance from
 * [LocalToaster] (the app root provides it) and call [show]. Wire it up once, at the root:
 *
 * ```
 * val toaster = rememberToaster()
 * CompositionLocalProvider(LocalToaster provides toaster) {
 *     Box {
 *         AppContent()
 *         ToastHost(Modifier.align(Alignment.TopCenter))
 *     }
 * }
 * ```
 */
@Stable
class Toaster {
    /** The toast currently on screen, or null. */
    var current: ToastData? by mutableStateOf(null)
        private set

    private var nextId = 0L

    fun show(message: String, kind: ToastKind = ToastKind.Info, durationMillis: Long = DefaultDurationMillis) {
        current = ToastData(++nextId, message, kind, durationMillis)
    }

    /** Hides whatever is on screen. */
    fun dismiss() {
        current = null
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }

    companion object {
        const val DefaultDurationMillis = 3_000L
    }
}

@Composable
fun rememberToaster(): Toaster = remember { Toaster() }

/** The app-wide [Toaster]. Without a provider (previews) messages go nowhere. */
val LocalToaster = staticCompositionLocalOf { Toaster() }

/**
 * Renders [Toaster.current] as a floating pill under the status bar. Place it last in the root `Box`
 * with `Modifier.align(Alignment.TopCenter)`. Auto-hides after the toast's duration (3s by default);
 * swipe it up to dismiss. It only occupies the space of the toast itself, so it never blocks touches
 * elsewhere.
 */
@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
    toaster: Toaster = LocalToaster.current,
) {
    val data = toaster.current
    val reduced = LocalReducedMotion.current
    LaunchedEffect(data?.id) {
        if (data != null) {
            delay(data.durationMillis)
            toaster.dismiss(data.id)
        }
    }
    AnimatedContent(
        targetState = data,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = QhSpacing.x2, start = QhSpacing.x4, end = QhSpacing.x4),
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(QhMotion.Short)) togetherWith fadeOut(tween(QhMotion.Short))
            } else {
                (slideInVertically(QhMotion.snappy()) { -it } + fadeIn(QhMotion.snappy())) togetherWith
                    (slideOutVertically(QhMotion.snappy()) { -it } + fadeOut(QhMotion.snappy()))
            }
        },
        contentAlignment = Alignment.TopCenter,
        label = "toast",
    ) { toast ->
        if (toast != null) QhToast(toast, onDismiss = { toaster.dismiss(toast.id) })
    }
}

/** A toast bubble: an icon, one or two lines of text. Normally shown by [ToastHost], not directly. */
@Composable
fun QhToast(
    toast: ToastData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val (icon, tint) = toast.kind.visuals()
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 40.dp.toPx() }
    val returnSpec = QhMotion.snappy<Float>().orSnap()

    Row(
        modifier
            .widthIn(max = 480.dp)
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtMost(0f)) }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffset.value < -dismissDistance || velocity < -900f) {
                        onDismiss()
                    } else {
                        dragOffset.animateTo(0f, returnSpec)
                    }
                },
            )
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(start = QhSpacing.x3, end = QhSpacing.x5, top = QhSpacing.x3, bottom = QhSpacing.x3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconBar))
        Text(
            text = toast.message,
            style = QhTheme.type.callout,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToastKind.visuals(): Pair<ImageVector, Color> {
    val colors = QhTheme.colors
    return when (this) {
        ToastKind.Success -> Icons.Rounded.CheckCircle to colors.learned
        ToastKind.Error -> Icons.Rounded.Error to colors.danger
        ToastKind.Info -> Icons.Rounded.Info to colors.primary
    }
}
