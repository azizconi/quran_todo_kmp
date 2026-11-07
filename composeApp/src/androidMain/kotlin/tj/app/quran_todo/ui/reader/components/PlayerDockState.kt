package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

enum class DockValue { Collapsed, Expanded }

/**
 * The two resting places of the player dock and everything that follows from where it is right now.
 * [progress] is read inside layout, draw and graphics-layer blocks (the scrim, the fading chips and the
 * header controls), never during composition, so dragging the dock does not recompose the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class PlayerDockState internal constructor() {
    internal val draggable = AnchoredDraggableState(initialValue = DockValue.Collapsed)

    /** Height of the part of the dock that shows when it is collapsed (header + navigation bar inset). */
    var collapsedHeightPx by mutableFloatStateOf(0f)
        internal set

    /** Height of the whole dock when expanded. */
    var containerHeightPx by mutableFloatStateOf(0f)
        internal set

    /** Offset of the dock from its expanded position when collapsed. */
    val collapsedOffsetPx: Float get() = (containerHeightPx - collapsedHeightPx).coerceAtLeast(0f)

    /** Current offset in px: 0 expanded, [collapsedOffsetPx] collapsed. Safe before the anchors exist. */
    val offsetPx: Float
        get() {
            val offset = draggable.offset
            return if (offset.isNaN()) collapsedOffsetPx else offset
        }

    /** 0 = collapsed, 1 = expanded; follows the finger and the settling spring. */
    val progress: Float
        get() {
            val distance = collapsedOffsetPx
            if (distance <= 0f) return 0f
            return (1f - offsetPx / distance).coerceIn(0f, 1f)
        }

    /** The dock is, or is about to be, expanded. */
    val isExpanding: Boolean get() = draggable.targetValue == DockValue.Expanded

    suspend fun expand() = draggable.animateTo(DockValue.Expanded)

    suspend fun collapse() = draggable.animateTo(DockValue.Collapsed)

    /**
     * Lets the content of the expanded dock hand its scrolling to the dock: swiping up first pulls the dock
     * up, and when the content is at its top a swipe down brings the dock down again. A fling that the content
     * cannot use settles the dock through [flingBehavior], the same one the dock's own drag uses.
     */
    internal fun nestedScrollConnection(flingBehavior: FlingBehavior): NestedScrollConnection =
        object : NestedScrollConnection {
            private val dockScrollScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = draggable.dispatchRawDelta(pixels)
            }

            private suspend fun fling(velocity: Float): Velocity {
                val remaining = with(flingBehavior) { dockScrollScope.performFling(velocity) }
                return Velocity(0f, velocity - remaining)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta < 0f && source == NestedScrollSource.UserInput) {
                    Offset(0f, draggable.dispatchRawDelta(delta))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return if (source == NestedScrollSource.UserInput) {
                    Offset(0f, draggable.dispatchRawDelta(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val offset = draggable.offset
                return if (available.y < 0f && !offset.isNaN() && offset > 0f) fling(available.y) else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = fling(available.y)
        }
}

@Composable
fun rememberPlayerDockState(): PlayerDockState = remember { PlayerDockState() }

/** Swallows what a nested scrollable could not use, so it does not bubble up and drag the dock (wheels). */
internal object ConsumeLeftoverScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
