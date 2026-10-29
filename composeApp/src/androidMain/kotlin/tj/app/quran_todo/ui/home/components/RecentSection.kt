@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.home.RecentUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Chips of the surahs opened last, the pinned ones first with a pin. Long press opens the surah sheet. */
@Composable
internal fun RecentSection(
    recent: List<RecentUi>,
    copy: HomeCopy,
    onOpen: (surah: Int) -> Unit,
    onLongPress: (surah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(copy.recent, Modifier.padding(horizontal = QhSpacing.screen))
        Spacer(Modifier.height(QhSpacing.x1))
        LazyRow(
            contentPadding = PaddingValues(horizontal = QhSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            items(recent, key = { it.surah }) { chip ->
                RecentChip(
                    chip = chip,
                    pinnedLabel = copy.pinned,
                    openLabel = copy.open,
                    onOpen = onOpen,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

@Composable
private fun RecentChip(
    chip: RecentUi,
    pinnedLabel: String,
    openLabel: String,
    onOpen: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val origin = Origin.RECENT
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .sharedBoundsKey(SharedKeys.surahContainer(origin, chip.surah), SharedResize.Remeasure)
            .pressable(
                onClick = { onOpen(chip.surah) },
                onLongClick = { onLongPress(chip.surah) },
                shape = shape,
                onClickLabel = openLabel,
            )
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .heightIn(min = 38.dp)
            .padding(start = if (chip.pinned) 12.dp else 16.dp, end = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = chip.name
                // Spoken after the name, like the status of a switch: "Al-Baqarah, Pinned".
                if (chip.pinned) stateDescription = pinnedLabel
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.pinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = chip.name,
            style = QhTheme.type.label,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsKey(SharedKeys.surahTitle(origin, chip.surah), SharedResize.Scale),
        )
    }
}
