@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.AnimatedCheckmark
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.home.TodayBody
import tj.app.quran_todo.ui.home.TodayCardUi
import tj.app.quran_todo.ui.home.TodayKind
import tj.app.quran_todo.ui.home.TodayUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The "Today" block: the counter "3 of 5" and, under it, one card per kind of work left (new, review,
 * weak) in a snapping row, or a single calm card when everything is done or when a surah still has
 * to be marked.
 */
@Composable
internal fun TodaySection(
    today: TodayUi,
    copy: HomeCopy,
    onOpenCard: (TodayCardUi) -> Unit,
    onDismissInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(
            title = copy.today,
            modifier = Modifier.padding(horizontal = QhSpacing.screen),
            trailing = { DoneCounter(done = today.done, goal = today.goal, copy = copy) },
        )
        Spacer(Modifier.height(QhSpacing.x3))
        when (val body = today.body) {
            is TodayBody.Cards -> TodayCards(body.cards, copy, onOpenCard)
            TodayBody.AllDone -> AllDoneCard(copy, Modifier.padding(horizontal = QhSpacing.screen))
            TodayBody.Invite -> InviteCard(copy, onDismissInvite, Modifier.padding(horizontal = QhSpacing.screen))
        }
    }
}

/** "3 of 5" with a small ring; the digits roll when a task is finished. */
@Composable
private fun DoneCounter(done: Int, goal: Int, copy: HomeCopy) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Row(
        Modifier.clearAndSetSemantics { contentDescription = copy.todayProgress(done, goal) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCounter(value = done, style = type.title2, color = colors.ink)
        Text(
            text = " ${copy.ofGoal(goal)}",
            style = type.callout,
            color = colors.inkSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.width(QhSpacing.x2))
        ProgressRing(
            progress = if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f,
            size = 22.dp,
            strokeWidth = 3.dp,
            color = if (done >= goal) colors.learned else colors.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayCards(cards: List<TodayCardUi>, copy: HomeCopy, onOpenCard: (TodayCardUi) -> Unit) {
    val listState = rememberLazyListState()
    // Cards keep one height at any font size so the row does not look ragged.
    val cardHeight = (BaseCardHeight * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = QhSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        items(cards, key = { it.kind.ordinal }) { card ->
            TodayCard(card = card, copy = copy, onClick = { onOpenCard(card) }, modifier = Modifier.height(cardHeight))
        }
    }
}

private const val BaseCardHeight = 128f

@Composable
private fun TodayCard(
    card: TodayCardUi,
    copy: HomeCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val shape = QhTheme.shapes.lg
    val look = cardLook(card.kind)
    val kindLabel = when (card.kind) {
        TodayKind.New -> copy.kindNew
        TodayKind.Review -> copy.kindReview
        TodayKind.Weak -> copy.kindWeak
    }
    val title: String
    val subtitle: String
    when (card.kind) {
        TodayKind.New -> {
            title = card.surahName
            subtitle = copy.ayahRange(card.startAyah, card.endAyah)
        }
        TodayKind.Review, TodayKind.Weak -> {
            title = copy.ayahCount(card.totalAyahs)
            subtitle = if (card.surahCount > 1) copy.surahCount(card.surahCount) else card.surahName
        }
    }
    val origin = Origin.TODAY
    Column(
        modifier
            .width(CardWidth)
            .then(
                if (card.sharesElements) {
                    Modifier.sharedBoundsKey(SharedKeys.surahContainer(origin, card.surah), SharedResize.Remeasure)
                } else {
                    Modifier
                },
            )
            .pressable(onClick = onClick, shape = shape, onClickLabel = copy.open)
            .clip(shape)
            .background(look.container)
            .border(look.borderWidth, look.border, shape)
            .padding(QhSpacing.card)
            .semantics(mergeDescendants = true) { contentDescription = "$kindLabel: $title, $subtitle" },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(16.dp))
            }
            Text(kindLabel, style = type.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column {
            Text(
                text = title,
                style = type.title2,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (card.kind == TodayKind.New && card.sharesElements) {
                    Modifier.sharedBoundsKey(SharedKeys.surahTitle(origin, card.surah), SharedResize.Scale)
                } else {
                    Modifier
                },
            )
            Text(subtitle, style = type.callout, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val CardWidth = 156.dp

private class CardLook(
    val container: Color,
    val border: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val tint: Color,
    val icon: ImageVector,
)

/** New: gold. Review: terracotta. Weak: terracotta outline. */
@Composable
private fun cardLook(kind: TodayKind): CardLook {
    val colors = QhTheme.colors
    return when (kind) {
        TodayKind.New -> CardLook(colors.goldContainer, colors.gold.copy(alpha = 0.35f), 1.dp, colors.gold, Icons.Rounded.AutoAwesome)
        TodayKind.Review -> CardLook(colors.dueContainer, Color.Transparent, 1.dp, colors.due, Icons.Rounded.Refresh)
        TodayKind.Weak -> CardLook(colors.surface, colors.due, 1.5.dp, colors.due, Icons.Rounded.PriorityHigh)
    }
}

/** "Everything is reviewed for today": a checkmark that draws itself. */
@Composable
private fun AllDoneCard(copy: HomeCopy, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    QhCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.cardLarge)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.learnedContainer),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedCheckmark(size = 28.dp, color = colors.learned, strokeWidth = 3.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(copy.allDoneTitle, style = QhTheme.type.title2, color = colors.ink)
                Text(copy.allDoneBody, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
    }
}

/** Memorizing was chosen as a goal but no surah is marked yet; can be closed for good. */
@Composable
private fun InviteCard(copy: HomeCopy, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    QhCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = QhSpacing.cardLarge, top = QhSpacing.card, bottom = QhSpacing.card, end = QhSpacing.x1)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Box(
                Modifier
                    .padding(top = QhSpacing.x1)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.goldContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(top = QhSpacing.x1)) {
                Text(copy.inviteTitle, style = QhTheme.type.title2, color = colors.ink)
                Spacer(Modifier.height(QhSpacing.x1))
                Text(copy.inviteBody, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            QhIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = copy.dismiss,
                onClick = onDismiss,
                filled = false,
                tint = colors.inkSecondary,
                iconSize = 20.dp,
            )
        }
    }
}
