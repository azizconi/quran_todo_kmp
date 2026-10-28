package tj.app.quran_todo.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.SkeletonRow
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The hero and the search field as shimmering blocks of their final size, while the Quran loads. */
@Composable
internal fun HomeSkeletonTop(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = QhSpacing.screen)) {
        Skeleton(Modifier.fillMaxWidth().height(HeroSkeletonHeight), QhTheme.shapes.lg)
        Spacer(Modifier.height(QhSpacing.section))
        Skeleton(Modifier.fillMaxWidth().height(52.dp), QhTheme.shapes.full)
        Spacer(Modifier.height(QhSpacing.x3))
    }
}

/** One placeholder row of the surah list. */
@Composable
internal fun HomeSkeletonRow(modifier: Modifier = Modifier) {
    SkeletonRow(modifier.padding(horizontal = QhSpacing.screen))
}

private val HeroSkeletonHeight = 196.dp
