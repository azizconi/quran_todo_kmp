package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.progress.JuzUi
import tj.app.quran_todo.ui.progress.MapCellUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The fourth block of the page: "Quran map", collapsed until the person opens it. Inside are the 114 cells of the surahs and the
 * thirty juz. Nothing of the two is composed while the block is closed.
 */
@Composable
internal fun QuranMapBlock(
    cells: List<MapCellUi>,
    juz: List<JuzUi>,
    copy: ProgressCopy,
    scrollState: ScrollState,
    onOpenSurah: (surah: Int) -> Unit,
    onOpenJuz: (JuzUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val colors = QhTheme.colors
    val action = if (expanded) copy.mapCollapse else copy.mapExpand
    Column(
        modifier
            .fillMaxWidth()
            .then(if (reduced) Modifier else Modifier.animateContentSize(QhMotion.gentle())),
    ) {
        QhCard(onClick = { expanded = !expanded }) {
            Row(
                Modifier.semantics {
                    role = Role.Button
                    contentDescription = "${copy.mapTitle}. $action"
                },
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = copy.mapTitle, style = QhTheme.type.title2, color = colors.ink, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(QhSpacing.x3))
            QuranMapSection(cells = cells, copy = copy, scrollState = scrollState, onOpen = onOpenSurah)
            Spacer(Modifier.height(QhSpacing.section))
            JuzSection(juz = juz, copy = copy, onOpen = onOpenJuz)
        }
    }
}
