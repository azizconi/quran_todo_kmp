package tj.app.quran_todo.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.motion.StaggerState
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The frame of the screens that open from the settings (privacy, sources): the large collapsing title
 * with a back button over a scrolling column, laid out like the settings screen itself. [content]
 * gets the [StaggerState] for the sections' entrance.
 */
@Composable
internal fun AboutScaffold(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(StaggerState) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val stagger = rememberStaggerState()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // The bar floats over the column: reserve its expanded height (the estimate is exact for a
    // one-line title, so the column does not jump when the first measurement arrives).
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val topPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = topPadding, bottom = navigationBottom + QhSpacing.x8)
                .padding(horizontal = QhSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
        ) {
            content(stagger)
        }
        QhLargeTopBar(
            title = title,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (scrollState.value == 0) expandedBarPx = size.height
            },
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backLabel,
                    onClick = onBack,
                    filled = false,
                )
            },
        )
    }
}
