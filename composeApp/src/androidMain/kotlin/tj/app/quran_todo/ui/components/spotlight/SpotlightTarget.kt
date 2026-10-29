package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marks an element as something a tour can point at. The element registers itself with the app's [SpotlightRegistry]
 * while it is in the composition (only its layout coordinates are kept: nothing is computed while the screen scrolls),
 * and the host reads its bounds when a tour needs them.
 *
 * - [id] is a `SpotlightTargets` value. Several elements may share an id; the most visible one counts.
 * - [shape] is the cutout around it: a circle or a rounded rectangle. A step may override it.
 * - [reveal]: the element may scroll itself into view when a step wants it and it is outside the visible area (the
 *   expanded player dock does); otherwise a target outside the screen means the step is skipped.
 * - [inset] pulls the bounds in on every side before the cutout is made (a full-width row would otherwise get a
 *   cutout from edge to edge).
 *
 * Put it AFTER the padding and BEFORE the clip/background of the element, so the bounds are the visible element.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.spotlightTarget(
    id: String,
    shape: SpotlightShape = SpotlightShape.Rounded(),
    reveal: Boolean = false,
    inset: Dp = 0.dp,
): Modifier {
    val registry = LocalSpotlight.current.registry
    val requester = remember { BringIntoViewRequester() }
    val insetPx = with(LocalDensity.current) { inset.toPx() }
    return this
        .then(if (reveal) Modifier.bringIntoViewRequester(requester) else Modifier)
        .then(SpotlightTargetElement(registry, id, shape, if (reveal) requester else null, insetPx))
}

/** What the host knows about a target. */
internal class SpotlightTargetInfo(
    val bounds: SpotlightBounds,
    val shape: SpotlightShape,
    val reveal: (suspend () -> Unit)?,
)

/** The targets that are on the screen right now. Lives in the [SpotlightController]. */
class SpotlightRegistry {
    private val nodes = HashMap<String, MutableList<SpotlightTargetNode>>()

    internal fun add(node: SpotlightTargetNode) {
        nodes.getOrPut(node.id) { ArrayList() }.add(node)
    }

    internal fun remove(node: SpotlightTargetNode) {
        val list = nodes[node.id] ?: return
        list.remove(node)
        if (list.isEmpty()) nodes.remove(node.id)
    }

    /** The target [id] as seen from [host], or null when it is not placed. With several elements the most visible one wins. */
    internal fun find(id: String, host: LayoutCoordinates): SpotlightTargetInfo? {
        val list = nodes[id] ?: return null
        if (!host.isAttached) return null
        val viewport = Rect(0f, 0f, host.size.width.toFloat(), host.size.height.toFloat())
        var best: SpotlightTargetInfo? = null
        var bestVisible = false
        for (node in list) {
            val coordinates = node.coordinates ?: continue
            if (!coordinates.isAttached || coordinates.size.width <= 0 || coordinates.size.height <= 0) continue
            val rect = try {
                host.localBoundingBoxOf(coordinates, clipBounds = true)
            } catch (e: RuntimeException) {
                continue
            }
            val inset = node.inset
            val bounds = SpotlightBounds(
                rect = if (inset == 0f) rect else Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                full = Size(
                    (coordinates.size.width - 2 * inset).coerceAtLeast(1f),
                    (coordinates.size.height - 2 * inset).coerceAtLeast(1f),
                ),
                revealable = node.requester != null,
            )
            val visible = SpotlightSteps.isVisible(bounds, viewport)
            val requester = node.requester
            val info = SpotlightTargetInfo(bounds, node.shape, requester?.let { { it.bringIntoView() } })
            val better = best == null || (visible && !bestVisible) || (visible == bestVisible && rect.top < (best.bounds.rect.top))
            if (better) {
                best = info
                bestVisible = visible
            }
        }
        return best
    }

    internal fun boundsOf(id: String, host: LayoutCoordinates): SpotlightBounds? = find(id, host)?.bounds
}

internal class SpotlightTargetNode(
    var registry: SpotlightRegistry,
    var id: String,
    var shape: SpotlightShape,
    var requester: BringIntoViewRequester?,
    var inset: Float,
) : Modifier.Node(), LayoutAwareModifierNode {
    /** The layout coordinates of the element; valid while [LayoutCoordinates.isAttached]. */
    var coordinates: LayoutCoordinates? = null

    override fun onAttach() {
        registry.add(this)
    }

    override fun onDetach() {
        registry.remove(this)
        coordinates = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }
}

private data class SpotlightTargetElement(
    val registry: SpotlightRegistry,
    val id: String,
    val shape: SpotlightShape,
    val requester: BringIntoViewRequester?,
    val inset: Float,
) : ModifierNodeElement<SpotlightTargetNode>() {
    override fun create() = SpotlightTargetNode(registry, id, shape, requester, inset)

    override fun update(node: SpotlightTargetNode) {
        if (node.registry !== registry || node.id != id) {
            if (node.isAttached) node.registry.remove(node)
            node.registry = registry
            node.id = id
            if (node.isAttached) registry.add(node)
        }
        node.shape = shape
        node.requester = requester
        node.inset = inset
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "spotlightTarget"
        properties["id"] = id
    }
}
