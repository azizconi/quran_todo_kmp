package tj.app.quran_todo.ui.similar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The mark of a similar ayah (task F6): two rings that overlap, one a little to the side of the other: "the same, and not quite". Round and
 * quiet; it is no star and has no points. Drawn with a line (tinted by the caller), 24 x 24.
 */
val SimilarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Similar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        ) {
            ring(cx = 9.2f, cy = 12f, r = 5.6f)
            ring(cx = 14.8f, cy = 12f, r = 5.6f)
        }
    }.build()
}

/** A closed circle as two half arcs. */
private fun PathBuilder.ring(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}
