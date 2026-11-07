package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Where a target is, as the host sees it. [rect] is in the coordinates of the host and already cut by the scrolling
 * containers it sits in, [full] is the size the target has when nothing cuts it, [revealable] says that the target can
 * scroll itself into view (`spotlightTarget(reveal = true)`).
 */
@Immutable
internal data class SpotlightBounds(
    val rect: Rect,
    val full: Size,
    val revealable: Boolean = false,
)

/** Which steps of a tour can be shown right now. Pure, tested in `SpotlightStepsTest`. */
internal object SpotlightSteps {
    /** A target counts as being on screen when at least this share of it is inside the viewport. */
    const val MinVisibleFraction = 0.75f

    fun isVisible(bounds: SpotlightBounds?, viewport: Rect): Boolean {
        if (bounds == null) return false
        val fullArea = bounds.full.width * bounds.full.height
        if (fullArea <= 0f) return false
        val width = min(bounds.rect.right, viewport.right) - max(bounds.rect.left, viewport.left)
        val height = min(bounds.rect.bottom, viewport.bottom) - max(bounds.rect.top, viewport.top)
        if (width <= 0f || height <= 0f) return false
        return width * height / fullArea >= MinVisibleFraction
    }

    /**
     * The steps worth showing: a step whose target does not exist, or is outside the visible area and cannot
     * scroll into it, is left out. An empty result means the tour is not shown (and not marked as shown).
     */
    fun select(steps: List<SpotlightStep>, viewport: Rect, boundsOf: (targetId: String) -> SpotlightBounds?): List<SpotlightStep> =
        steps.filter { step ->
            val bounds = boundsOf(step.targetId)
            isVisible(bounds, viewport) || bounds?.revealable == true
        }
}

/** What the gate looks at. */
internal data class SpotlightGateState(
    /** The route of the screen on top (`SpotlightScreen.route`, "onboarding", ...), null when unknown. */
    val route: String?,
    /** The screen on top is fully shown: no navigation transition is running. */
    val navigationIdle: Boolean,
    /** `QhSheet`s on screen. */
    val sheetsOpen: Int,
    /** The window of the app has the focus: no dialog, menu or other popup window is above it. */
    val windowFocused: Boolean,
    /** Another tour is running. */
    val tourRunning: Boolean,
)

/** "May this tour start now?" Pure, tested in `SpotlightGateTest`. */
internal object SpotlightGate {
    const val OnboardingRoute = "onboarding"

    fun canStart(tour: SpotlightTour, state: SpotlightGateState): Boolean = when {
        state.tourRunning -> false
        state.route == null || state.route == OnboardingRoute -> false
        state.route != tour.screen.route -> false
        !state.navigationIdle -> false
        state.sheetsOpen > 0 -> false
        !state.windowFocused -> false
        else -> true
    }
}

/** Where the card goes. */
@Immutable
internal data class SpotlightPlacement(
    /** The top left corner of the card, in the coordinates of the host. */
    val topLeft: Offset,
    /** The card is under the cutout (its arrow is on the top edge); false: above it (the arrow is on the bottom edge). */
    val below: Boolean,
    /** Where the arrow sits (x, host coordinates); null when the card covers the cutout and has no arrow. */
    val arrowCenterX: Float?,
)

/** The position of the tip card. Pure, tested in `SpotlightPlacementTest`. All values are in px. */
internal object SpotlightLayout {
    /**
     * @param cutout the cutout (the target with its padding).
     * @param card the size of the card; its width is cut down to what fits between the margins.
     * @param viewport the size of the host.
     * @param safeTop the top edge of the free area (below the status bar), [safeBottom] its bottom edge (above the navigation bar).
     * @param margin the least distance of the card from the left and right edges.
     * @param gap the distance between the cutout and the card (the arrow lives in it).
     * @param arrowInset the least distance of the arrow from the corners of the card.
     */
    fun place(
        cutout: Rect,
        card: Size,
        viewport: Size,
        safeTop: Float,
        safeBottom: Float,
        margin: Float,
        gap: Float,
        arrowInset: Float,
        position: SpotlightPosition = SpotlightPosition.Auto,
    ): SpotlightPlacement {
        val cardWidth = min(card.width, max(viewport.width - 2 * margin, 0f))
        val maxX = viewport.width - margin - cardWidth
        val x = if (maxX <= margin) margin else (cutout.center.x - cardWidth / 2f).coerceIn(margin, maxX)

        val need = card.height + gap
        val spaceBelow = safeBottom - cutout.bottom
        val spaceAbove = cutout.top - safeTop
        val below = when (position) {
            SpotlightPosition.Below -> true
            SpotlightPosition.Above -> false
            SpotlightPosition.Auto -> {
                val fitsBelow = spaceBelow >= need
                val fitsAbove = spaceAbove >= need
                when {
                    fitsBelow && fitsAbove -> spaceBelow >= spaceAbove
                    fitsBelow -> true
                    fitsAbove -> false
                    else -> spaceBelow >= spaceAbove
                }
            }
        }

        val wantedY = if (below) cutout.bottom + gap else cutout.top - gap - card.height
        val maxY = safeBottom - card.height
        val y = if (maxY <= safeTop) safeTop else wantedY.coerceIn(safeTop, maxY)

        val covers = y < cutout.bottom && y + card.height > cutout.top
        val arrowX = if (covers) {
            null
        } else if (cardWidth <= 2 * arrowInset) {
            x + cardWidth / 2f
        } else {
            cutout.center.x.coerceIn(x + arrowInset, x + cardWidth - arrowInset)
        }
        return SpotlightPlacement(topLeft = Offset(x, y), below = below, arrowCenterX = arrowX)
    }

    /**
     * The cutout around a target: the target grown by [padding] on every side. [circle] gives the corner radius of half the
     * shorter side (a circle around a square target, a pill around a wide one); otherwise it is [radius] grown by the padding.
     */
    fun cutout(target: Rect, padding: Float, circle: Boolean, radius: Float): Pair<Rect, Float> {
        val rect = Rect(target.left - padding, target.top - padding, target.right + padding, target.bottom + padding)
        val corner = if (circle) min(rect.width, rect.height) / 2f else min(radius + padding, min(rect.width, rect.height) / 2f)
        return rect to corner
    }
}
