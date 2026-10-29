package tj.app.quran_todo.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Corner radii of section 2.3: buttons are pills, cards are [lg], the player dock and sheets are [xl] at the top. */
@Immutable
class QhShapes(
    val xs: CornerBasedShape = RoundedCornerShape(8.dp),
    val sm: CornerBasedShape = RoundedCornerShape(12.dp),
    val md: CornerBasedShape = RoundedCornerShape(16.dp),
    val lg: CornerBasedShape = RoundedCornerShape(22.dp),
    val xl: CornerBasedShape = RoundedCornerShape(28.dp),
    /** Pills: buttons, chips, fields. */
    val full: CornerBasedShape = RoundedCornerShape(percent = 50),
    /** Top of sheets and the player dock. */
    val xlTop: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

internal fun QhShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = xs,
    small = sm,
    medium = md,
    large = lg,
    extraLarge = xl,
)

internal val LocalQhShapes = staticCompositionLocalOf { QhShapes() }
