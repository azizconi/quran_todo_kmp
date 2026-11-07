package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.progress.HardSpotUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Rows shown before "Show more". */
private const val CollapsedRows = 5

/** Ayah chips of one surah before "+N": a long list would push everything else off the screen. */
private const val MaxChips = 12

/** What the reader opens for a tap on a chip: the surah at that ayah. */
internal fun HardSpotUi.readerAt(ayah: Int): Dest.Reader = Dest.Reader(surah = surah, ayah = ayah, origin = Origin.MAP)

/**
 * "Hard spots": the surahs with difficult ayahs (the marks inside the passages, from `HifzRepository.hardAyahs()`), the most
 * affected first. Each row names the surah and has a chip for every hard ayah; a chip opens the reader at that ayah. Without
 * hard ayahs a calm card says so.
 */
@Composable
internal fun HardSpotsSection(
    spots: List<HardSpotUi>,
    copy: ProgressCopy,
    onOpenAyah: (Dest.Reader) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.hardTitle)
        Spacer(Modifier.height(QhSpacing.x3))
        if (spots.isEmpty()) {
            NoHardSpots(copy)
            return@Column
        }
        val visible = if (expanded) spots else spots.take(CollapsedRows)
        QhCard(
            contentPadding = PaddingValues(vertical = QhSpacing.x1),
            modifier = if (reduced) Modifier else Modifier.animateContentSize(QhMotion.gentle()),
        ) {
            visible.forEachIndexed { index, spot ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                HardSpotRow(spot = spot, copy = copy, onOpenAyah = onOpenAyah)
            }
            if (spots.size > CollapsedRows) {
                QhRowDivider(startIndent = QhSpacing.card)
                Box(Modifier.fillMaxWidth().padding(vertical = QhSpacing.x1), contentAlignment = Alignment.Center) {
                    QhButton(
                        text = if (expanded) copy.hardLess else copy.hardMore(spots.size - CollapsedRows),
                        onClick = { expanded = !expanded },
                        variant = QhButtonVariant.Ghost,
                        size = QhButtonSize.S,
                        icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HardSpotRow(
    spot: HardSpotUi,
    copy: ProgressCopy,
    onOpenAyah: (Dest.Reader) -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .padding(QhSpacing.card),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
            SurahNumberBadge(number = spot.surah, status = null, learnedFraction = 0f)
            Column(Modifier.weight(1f)) {
                Text(
                    text = spot.name,
                    style = type.title2,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = copy.hardCount(spot.ayahs.size), style = type.callout, color = colors.due)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
        ) {
            spot.ayahs.take(MaxChips).forEach { ayah ->
                val description = copy.hardAyahDescription(spot.name, ayah)
                val open = { onOpenAyah(spot.readerAt(ayah)) }
                QhChip(
                    label = "$ayah",
                    selected = false,
                    onClick = open,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick(label = description) { open(); true }
                    },
                )
            }
            val rest = spot.ayahs.size - MaxChips
            if (rest > 0) {
                Text(
                    text = "+$rest",
                    style = type.label,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = QhSpacing.x2, vertical = QhSpacing.x2),
                )
            }
        }
    }
}

/** No hard ayahs: a check and a kind word instead of an empty box. */
@Composable
private fun NoHardSpots(copy: ProgressCopy) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    QhCard {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.learned.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(QhSpacing.iconInline))
            }
            Column(Modifier.weight(1f)) {
                Text(text = copy.hardNoneTitle, style = type.title2, color = colors.ink)
                Text(text = copy.hardNoneBody, style = type.callout, color = colors.inkSecondary)
            }
        }
    }
}
