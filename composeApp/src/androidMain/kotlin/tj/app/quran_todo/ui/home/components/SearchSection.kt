@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.home.SmartResultUi
import tj.app.quran_todo.ui.home.SurahFilter
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The search field and the three segments. Lives in a sticky list header: [stuck] is true while it is
 * pinned under the top bar (it then gets a hairline). The segments fold away ([showFilters] = false)
 * while the person searches, because a search looks through everything.
 */
@Composable
internal fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: SurahFilter,
    onFilterChange: (SurahFilter) -> Unit,
    showFilters: Boolean,
    copy: HomeCopy,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    stuck: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val reduced = LocalReducedMotion.current
    val labels = listOf(copy.filterAll, copy.filterLearning, copy.filterLearned)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(colors.bg)
                if (stuck()) {
                    drawLine(
                        color = colors.hairline,
                        start = Offset(0f, size.height - 0.5.dp.toPx()),
                        end = Offset(size.width, size.height - 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x2, bottom = QhSpacing.x3),
    ) {
        QhSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = copy.searchPlaceholder,
            clearContentDescription = copy.clearSearch,
            focusRequester = focusRequester,
            onFocusChange = onFocusChange,
            onSearch = { keyboard?.hide() },
        )
        AnimatedVisibility(
            visible = showFilters,
            enter = if (reduced) {
                EnterTransition.None
            } else {
                expandVertically(QhMotion.gentle<IntSize>()) + fadeIn(QhMotion.snappy())
            },
            exit = if (reduced) {
                ExitTransition.None
            } else {
                shrinkVertically(QhMotion.gentle<IntSize>()) + fadeOut(QhMotion.snappy())
            },
        ) {
            Column {
                Spacer(Modifier.height(QhSpacing.x3))
                QhSegmentedControl(
                    options = labels,
                    selectedIndex = filter.ordinal,
                    onSelect = { onFilterChange(SurahFilter.entries[it]) },
                )
            }
        }
    }
}

/**
 * The first result for `2:255`: "Al-Baqarah · ayah 255 -> Open". Opens the reader scrolled to that
 * ayah. Takes part in the shared element transition only when no row of the same surah does.
 */
@Composable
internal fun SmartResultCard(
    result: SmartResultUi,
    copy: HomeCopy,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val row = result.row
    val origin = Origin.SEARCH
    val title = copy.smartTitle(row.name, result.ayah)
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$title. ${copy.smartOpen}" }
            .then(
                if (result.sharesElements) {
                    Modifier.sharedBoundsKey(SharedKeys.surahContainer(origin, row.number), SharedResize.Remeasure)
                } else {
                    Modifier
                },
            ),
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            SurahNumberBadge(
                number = row.number,
                status = row.status,
                learnedFraction = row.learnedFraction,
                modifier = if (result.sharesElements) {
                    Modifier.sharedElementKey(SharedKeys.surahBadge(origin, row.number))
                } else {
                    Modifier
                },
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = QhTheme.type.title2, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(copy.smartOpen, style = QhTheme.type.callout, color = colors.primary, maxLines = 1)
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(QhSpacing.iconBar),
            )
        }
    }
}

/** What the list says when nothing is in it: no search result, or no surah with the chosen status. */
@Composable
internal fun ListEmptyState(
    isSearching: Boolean,
    filter: SurahFilter,
    copy: HomeCopy,
    modifier: Modifier = Modifier,
) {
    val (title, body) = when {
        isSearching -> copy.noResultsTitle to copy.noResultsBody
        filter == SurahFilter.Learning -> copy.emptyLearningTitle to copy.emptyLearningBody
        else -> copy.emptyLearnedTitle to copy.emptyLearnedBody
    }
    EmptyState(title = title, message = body, modifier = modifier.padding(top = QhSpacing.x8))
}
