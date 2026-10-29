package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.plan.AddSurahSheet
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The surahs of the grid: Al-Fatihah, then the last juz from the shortest surah to the longest (114 down to 78). */
internal val KnownGrid: List<Int> = listOf(1) + (114 downTo 78).toList()

/**
 * Step 6 (only "Memorize"): which surahs the person already knows by heart. A grid of the short ones, a search for any other
 * ("Other surahs"), the added ones as chips. A touch marks; nothing is written until the last screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KnownStep(
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    ui: OnboardingUi,
    onToggle: (Int) -> Unit,
    onAdd: (Int) -> Unit,
) {
    val known = ui.selection.known
    val extras = known.filter { it !in KnownGrid }
    var adding by rememberSaveable { mutableStateOf(false) }

    StepColumn {
        StepHeader(copy.knownTitle, copy.knownSubtitle)
        // With a large system font two names to a row no longer fit: one to a row then.
        val columns = if (LocalDensity.current.fontScale > LargeFontScale) 1 else 2
        ColumnGrid(KnownGrid, columns) { surah, modifier ->
            SurahTile(
                number = surah,
                name = ui.surahName(surah) ?: surah.toString(),
                selected = surah in known,
                onClick = { onToggle(surah) },
                modifier = modifier,
            )
        }
        QhButton(
            text = copy.knownOther,
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.M,
            icon = Icons.Rounded.Add,
        )
        if (extras.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                extras.forEach { surah ->
                    val name = ui.surahName(surah) ?: surah.toString()
                    RemovableChip(label = "$surah · $name", removeLabel = copy.remove(name), onRemove = { onToggle(surah) })
                }
            }
        }
    }

    if (adding) {
        AddSurahSheet(
            catalog = ui.catalog,
            inQueue = known.toSet(),
            copy = planCopy,
            onAdd = onAdd,
            onDismiss = { adding = false },
        )
    }
}

/** The system font scale from which the grid shows one surah to a row. */
private const val LargeFontScale = 1.15f

/** Lays [items] out [columns] to a row; the tiles of a row are equally high (a longer name wraps to two lines). */
@Composable
private fun <T> ColumnGrid(items: List<T>, columns: Int, tile: @Composable (item: T, modifier: Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        items.chunked(columns).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                row.forEach { item -> tile(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A surah of the grid: its number badge (filled emerald when marked) and its name, one tile of two in a row. */
@Composable
private fun SurahTile(
    number: Int,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "tile-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "tile-outline",
    )
    Row(
        modifier
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, highlight = false, role = Role.Checkbox)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurahNumberBadge(
            number = number,
            status = if (selected) SurahTodoStatus.LEARNED else null,
            learnedFraction = if (selected) 1f else 0f,
            size = 30.dp,
        )
        Text(
            text = name,
            style = QhTheme.type.label.copy(fontWeight = FontWeight.Medium),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A chip with a name and a cross: tapping it takes the surah back out. */
@Composable
internal fun RemovableChip(label: String, removeLabel: String, onRemove: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .pressable(onClick = onRemove, haptic = HapticKind.Segment, shape = shape, highlight = false, role = Role.Button)
            .semantics { contentDescription = removeLabel }
            .clip(shape)
            .background(colors.primaryContainer)
            .padding(start = 14.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = QhTheme.type.label, color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(16.dp))
    }
}
