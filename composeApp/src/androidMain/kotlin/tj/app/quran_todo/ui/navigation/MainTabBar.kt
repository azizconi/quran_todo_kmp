package tj.app.quran_todo.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.TabsCopy
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The height of the bar without the navigation-bar inset; every item is at least [QhSpacing.minTouch] tall inside it. */
val MainTabBarHeight = 64.dp

/** The words of the tabs: "Lesson" is its own word, "Quran" and "Progress" are the titles of those screens. */
internal data class MainTabLabels(val lesson: String, val quran: String, val progress: String) {
    fun of(tab: MainTab): String = when (tab) {
        MainTab.Lesson -> lesson
        MainTab.Quran -> quran
        MainTab.Progress -> progress
    }

    companion object {
        fun of(copy: TabsCopy) = MainTabLabels(
            lesson = copy.tabLesson,
            quran = homeCopy(copy.language).title,
            progress = progressCopy(copy.language).title,
        )
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Lesson -> Icons.Rounded.School
    MainTab.Quran -> Icons.AutoMirrored.Rounded.MenuBook
    MainTab.Progress -> Icons.Rounded.BarChart
}

/**
 * The bottom bar of the three tabs (UX task U14): an icon over a word, the chosen tab on a soft pill of the primary colour. The bar
 * is painted under the navigation bar (the inset is padded inside), items are as tall as the bar and never narrower than a third.
 */
@Composable
internal fun MainTabBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    copy: TabsCopy,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val labels = remember(copy) { MainTabLabels.of(copy) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                drawLine(colors.hairline, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .semantics { contentDescription = copy.barDescription },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MainTabBarHeight)
                .padding(horizontal = QhSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    label = labels.of(tab),
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.inkSecondary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "tab-tint",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else Color.Transparent,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "tab-pill",
    )
    Column(
        modifier
            .heightIn(min = MainTabBarHeight)
            .pressable(
                onClick = onClick,
                haptic = if (selected) null else HapticKind.Tick,
                shape = RoundedCornerShape(QhSpacing.x4),
                role = Role.Tab,
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
            .padding(vertical = QhSpacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = QhTheme.type.label,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
