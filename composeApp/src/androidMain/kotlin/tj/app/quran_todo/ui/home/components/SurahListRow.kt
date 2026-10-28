@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.home.SurahRowUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.ayahCountLabel
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The few strings a row needs, so the rows do not depend on the whole [HomeCopy]. */
@Immutable
internal class RowTexts(
    val language: AppLanguage,
    val open: String,
    val notStarted: String,
    val learning: String,
    val learned: String,
    val pinned: String,
) {
    companion object {
        fun of(copy: HomeCopy) = RowTexts(
            language = copy.language,
            open = copy.open,
            notStarted = copy.statusNotStarted,
            learning = copy.statusLearning,
            learned = copy.statusLearned,
            pinned = copy.pinned,
        )
    }
}

/**
 * A surah row: the numbered star (its shape and fill carry the status, its arc the progress), the
 * name, "meaning · N ayahs" and the Arabic name. No status pill: long press opens the surah sheet.
 * [origin] is `Origin.LIST` or `Origin.SEARCH` and feeds the shared element keys, so the reader's
 * header can grow out of this row. [celebration] != 0 plays a burst over the badge.
 */
@Composable
internal fun SurahListRow(
    row: SurahRowUi,
    origin: String,
    texts: RowTexts,
    arabicStyle: TextStyle,
    celebration: Int,
    onOpen: (surah: Int) -> Unit,
    onLongPress: (surah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val subtitle = remember(row.translatedName, row.ayahCount, texts.language) {
        listOf(row.translatedName, ayahCountLabel(row.ayahCount, texts.language))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    val statusLabel = when (row.status) {
        SurahTodoStatus.LEARNED -> texts.learned
        SurahTodoStatus.LEARNING -> texts.learning
        null -> texts.notStarted
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .sharedBoundsKey(SharedKeys.surahContainer(origin, row.number), SharedResize.Remeasure)
                .pressable(
                    onClick = { onOpen(row.number) },
                    onLongClick = { onLongPress(row.number) },
                    scale = RowPressScale,
                    enabled = row.canOpen,
                    onClickLabel = texts.open,
                )
                .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x3)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(row.name)
                        if (subtitle.isNotEmpty()) append(", ").append(subtitle)
                        if (row.isPinned) append(", ").append(texts.pinned)
                    }
                    stateDescription = statusLabel
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(BadgeSize), contentAlignment = Alignment.Center) {
                SurahNumberBadge(
                    number = row.number,
                    status = row.status,
                    learnedFraction = row.learnedFraction,
                    size = BadgeSize,
                    modifier = Modifier.sharedElementKey(SharedKeys.surahBadge(origin, row.number)),
                )
                if (celebration != 0) Celebration(token = celebration)
            }
            Spacer(Modifier.width(BadgeGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = row.name,
                        style = type.title2,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .sharedBoundsKey(SharedKeys.surahTitle(origin, row.number), SharedResize.Scale),
                    )
                    if (row.isPinned) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = colors.gold, modifier = Modifier.size(14.dp))
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = type.callout,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.arabicName.isNotBlank()) {
                Spacer(Modifier.width(QhSpacing.x3))
                Text(
                    text = row.arabicName,
                    style = arabicStyle,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier.sharedBoundsKey(SharedKeys.surahArabic(origin, row.number), SharedResize.Scale),
                )
            }
        }
        // The hairline starts under the name, not under the badge.
        Box(
            Modifier
                .padding(start = QhSpacing.screen + BadgeSize + BadgeGap)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}

/**
 * The burst over the badge of the row that was just memorized. Composed only for that row, and
 * started one frame after it appears because the burst fires on a change of its trigger.
 */
@Composable
private fun Celebration(token: Int) {
    var trigger by remember(token) { mutableIntStateOf(0) }
    LaunchedEffect(token) { trigger = token }
    CelebrationBurst(
        trigger = trigger,
        modifier = Modifier.fillMaxSize(),
        radius = 44.dp,
        // The screen plays the success haptic itself so it also works when the row is off screen.
        haptic = null,
    )
}

private val BadgeSize = 40.dp
private val BadgeGap = 14.dp
private const val RowPressScale = 0.985f
