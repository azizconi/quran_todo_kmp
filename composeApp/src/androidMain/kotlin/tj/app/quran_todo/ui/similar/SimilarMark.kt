package tj.app.quran_todo.ui.similar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.rememberSimilarCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The mark by the number of an ayah that has similar ayahs (task F6): two overlapping rings, quiet, in the secondary ink. A tap opens the
 * sheet "Similar verses". Nothing is drawn while the setting "Similar verse marks" is off, while the index is being read, or when the ayah
 * has no like.
 */
@Composable
fun SimilarMark(surah: Int, ayah: Int, modifier: Modifier = Modifier) {
    val marksOn by koinInject<UiPrefs>().similarMarks.collectAsState()
    if (!marksOn) return
    val store = koinInject<SimilarStore>()
    val index by store.index.collectAsState()
    val key = remember(surah, ayah) { AyahKey(surah, ayah) }
    if (index?.has(key) != true) return
    val copy = rememberSimilarCopy()
    val description = copy.markDescription
    Box(
        modifier
            .size(QhSpacing.minTouch)
            .pressable(onClick = { store.openSheet(key) }, shape = QhTheme.shapes.full, role = Role.Button, onClickLabel = description)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = SimilarIcon, contentDescription = null, tint = QhTheme.colors.inkSecondary, modifier = Modifier.size(22.dp))
    }
}

/**
 * The line under a new ayah of the lesson, "Similar to Al-A'raf 19 - compare" (task F6): only when a like of the ayah is already memorized
 * ([passage] are the ayahs of the passage being learned, which do not count as memorized). A tap opens the sheet of the new ayah.
 */
@Composable
fun SimilarLessonHint(surah: Int, ayah: Int, passage: Set<AyahKey>, modifier: Modifier = Modifier) {
    val marksOn by koinInject<UiPrefs>().similarMarks.collectAsState()
    if (!marksOn) return
    val store = koinInject<SimilarStore>()
    val index by store.index.collectAsState()
    val covered by store.covered.collectAsState()
    val key = remember(surah, ayah) { AyahKey(surah, ayah) }
    val like = remember(index, covered, key, passage) { store.memorizedLike(key, passage) } ?: return
    val copy = rememberSimilarCopy()
    val label = rememberPlaceLabel()
    val text = copy.lessonHint(label(like))
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = QhSpacing.minTouch)
            .pressable(onClick = { store.openSheet(key) }, shape = QhTheme.shapes.md, role = Role.Button),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Icon(imageVector = SimilarIcon, contentDescription = null, tint = QhTheme.colors.primary, modifier = Modifier.size(18.dp))
        Text(text = text, style = QhTheme.type.callout, color = QhTheme.colors.primary, modifier = Modifier.padding(vertical = QhSpacing.x1))
    }
}

/** "Similar verse marks" in the "Aa" sheet (task F6): on by default. */
@Composable
fun SimilarMarksSwitchRow() {
    val prefs = koinInject<UiPrefs>()
    val copy = rememberSimilarCopy()
    val enabled by prefs.similarMarks.collectAsState()
    QhSwitchRow(
        title = copy.switchTitle,
        checked = enabled,
        onCheckedChange = prefs::setSimilarMarks,
        description = copy.switchHint,
    )
}
