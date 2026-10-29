package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where the tip card goes (px, a 1080 x 2400 screen: status bar 80, navigation bar 120). */
class SpotlightPlacementTest {
    private val viewport = Size(1080f, 2400f)
    private val safeTop = 96f
    private val safeBottom = 2400f - 136f
    private val margin = 48f
    private val gap = 42f
    private val arrowInset = 84f
    private val card = Size(960f, 480f)

    private fun place(cutout: Rect, card: Size = this.card, viewport: Size = this.viewport, position: SpotlightPosition = SpotlightPosition.Auto) =
        SpotlightLayout.place(cutout, card, viewport, safeTop, safeBottom, margin, gap, arrowInset, position)

    @Test
    fun aTargetAtTheTopGetsTheCardBelowIt() {
        val cutout = Rect(840f, 120f, 960f, 240f)
        val placement = place(cutout)
        assertTrue(placement.below)
        assertEquals(cutout.bottom + gap, placement.topLeft.y)
    }

    @Test
    fun aTargetAtTheBottomGetsTheCardAboveIt() {
        val cutout = Rect(60f, 2080f, 1020f, 2230f)
        val placement = place(cutout)
        assertFalse(placement.below)
        assertEquals(cutout.top - gap - card.height, placement.topLeft.y)
    }

    @Test
    fun withRoomOnBothSidesTheBiggerSideWins() {
        // A little above the middle: more room below.
        val above = place(Rect(300f, 900f, 700f, 1000f))
        assertTrue(above.below)
        // A little below the middle: more room above.
        val below = place(Rect(300f, 1400f, 700f, 1500f))
        assertFalse(below.below)
    }

    @Test
    fun theCardIsCenteredOnTheTargetWhenThereIsRoom() {
        val narrow = Size(600f, 400f)
        val cutout = Rect(440f, 300f, 640f, 400f)
        val placement = place(cutout, card = narrow)
        assertEquals(cutout.center.x - 300f, placement.topLeft.x, 0.01f)
        assertEquals(cutout.center.x, placement.arrowCenterX)
    }

    @Test
    fun theCardStaysAMarginAwayFromTheEdges() {
        val narrow = Size(600f, 400f)
        val atLeft = place(Rect(0f, 300f, 120f, 420f), card = narrow)
        assertEquals(margin, atLeft.topLeft.x)
        val atRight = place(Rect(960f, 300f, 1080f, 420f), card = narrow)
        assertEquals(viewport.width - margin - narrow.width, atRight.topLeft.x)
    }

    @Test
    fun theArrowFollowsTheTargetButNotIntoTheCorners() {
        val narrow = Size(600f, 400f)
        val atLeft = place(Rect(0f, 300f, 60f, 420f), card = narrow)
        // The card is pinned to the left margin, the target is left of its corner: the arrow stops at the inset.
        assertEquals(margin + arrowInset, atLeft.arrowCenterX)
        val atRight = place(Rect(1020f, 300f, 1080f, 420f), card = narrow)
        assertEquals(atRight.topLeft.x + narrow.width - arrowInset, atRight.arrowCenterX)
    }

    @Test
    fun aNarrowScreenShrinksTheCardToTheMargins() {
        val phone = Size(480f, 1000f)
        val placement = SpotlightLayout.place(
            cutout = Rect(200f, 200f, 280f, 280f),
            card = Size(960f, 300f),
            viewport = phone,
            safeTop = 40f,
            safeBottom = 960f,
            margin = margin,
            gap = gap,
            arrowInset = arrowInset,
        )
        assertEquals(margin, placement.topLeft.x)
        assertNotNull(placement.arrowCenterX)
    }

    @Test
    fun aTargetPartlyOffTheScreenStillGetsACardInsideTheScreen() {
        val placement = place(Rect(-40f, 1200f, 160f, 1320f))
        assertTrue(placement.topLeft.x >= margin)
        assertTrue(placement.topLeft.x + card.width <= viewport.width - margin)
    }

    @Test
    fun theCardNeverLeavesTheFreeArea() {
        // A target right under the status bar with a forced "above": the card is pushed down into the free area.
        val forced = place(Rect(100f, 100f, 300f, 200f), position = SpotlightPosition.Above)
        assertTrue(forced.topLeft.y >= safeTop)
        assertTrue(forced.topLeft.y + card.height <= safeBottom)
    }

    @Test
    fun aFixedPositionIsHonouredWhenThereIsRoomForIt() {
        val cutout = Rect(300f, 1400f, 700f, 1500f)
        assertTrue(place(cutout, position = SpotlightPosition.Below).below)
        assertFalse(place(Rect(300f, 900f, 700f, 1000f), position = SpotlightPosition.Above).below)
    }

    @Test
    fun aTargetTallerThanTheScreenGetsACardOverItWithoutAnArrow() {
        val huge = Rect(40f, 160f, 1040f, 2200f)
        val placement = place(huge)
        assertNull(placement.arrowCenterX)
        assertTrue(placement.topLeft.y >= safeTop)
        assertTrue(placement.topLeft.y + card.height <= safeBottom)
    }

    @Test
    fun aCardTallerThanTheFreeAreaStartsAtItsTop() {
        val tall = Size(960f, 3000f)
        val placement = place(Rect(300f, 1000f, 700f, 1100f), card = tall)
        assertEquals(safeTop, placement.topLeft.y)
    }

    @Test
    fun theCutoutIsTheTargetGrownByThePadding() {
        val (rect, corner) = SpotlightLayout.cutout(Rect(100f, 200f, 200f, 300f), padding = 24f, circle = false, radius = 30f)
        assertEquals(Rect(76f, 176f, 224f, 324f), rect)
        assertEquals(54f, corner)
    }

    @Test
    fun aCircleCutoutRoundsEverythingAndAWideOneBecomesAPill() {
        val (square, squareCorner) = SpotlightLayout.cutout(Rect(0f, 0f, 100f, 100f), padding = 20f, circle = true, radius = 0f)
        assertEquals(70f, squareCorner)
        assertEquals(140f, square.width)
        val (wide, wideCorner) = SpotlightLayout.cutout(Rect(0f, 0f, 400f, 100f), padding = 20f, circle = true, radius = 0f)
        assertEquals(70f, wideCorner)
        assertEquals(440f, wide.width)
    }

    @Test
    fun aRoundedCutoutNeverRoundsMoreThanAPill() {
        val (_, corner) = SpotlightLayout.cutout(Rect(0f, 0f, 100f, 40f), padding = 10f, circle = false, radius = 90f)
        assertEquals(30f, corner)
    }
}
