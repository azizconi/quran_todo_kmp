@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tj.app.quran_todo.ui.components.AnimatedCheckmark
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.home.TodayBody
import tj.app.quran_todo.ui.home.TodayCardUi
import tj.app.quran_todo.ui.home.TodayKind
import tj.app.quran_todo.ui.home.TodayUi
import tj.app.quran_todo.ui.home.isGoalReached
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The "Today" block: the counter "3 of 5" (a full ring with a checkmark and "Goal reached · 7" once the
 * goal is met) and, under it, one card per kind of work left (new, review, weak) in a snapping row, or
 * a single calm card when everything is done or when a surah still has to be marked.
 *
 * [consumeGoalCelebration] is asked when the goal is seen reached; it answers true at most once a day,
 * and only then does the small burst play over the ring.
 */
@Composable
internal fun TodaySection(
    today: TodayUi,
    copy: HomeCopy,
    onOpenCard: (TodayCardUi) -> Unit,
    onDismissInvite: () -> Unit,
    consumeGoalCelebration: (done: Int, goal: Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(
            title = copy.today,
            // Above the cards below it: the burst over the ring flies out of the header's bounds.
            modifier = Modifier
                .zIndex(1f)
                .padding(horizontal = QhSpacing.screen),
            trailing = {
                DoneCounter(
                    done = today.done,
                    goal = today.goal,
                    copy = copy,
                    consumeGoalCelebration = consumeGoalCelebration,
                )
            },
        )
        Spacer(Modifier.height(QhSpacing.x3))
        when (val body = today.body) {
            is TodayBody.Cards -> TodayCards(body.cards, copy, onOpenCard)
            TodayBody.AllDone -> AllDoneCard(copy, Modifier.padding(horizontal = QhSpacing.screen))
            TodayBody.Invite -> InviteCard(copy, onDismissInvite, Modifier.padding(horizontal = QhSpacing.screen))
        }
    }
}

/**
 * "3 of 5" with a small ring; the digits roll when a task is finished. Once the goal is met (also when
 * it is passed: "7 of 5" would only read as a mistake) the counter becomes "Goal reached · 7" and the
 * ring is full, with a checkmark that draws itself. The first time that happens on a day, a small burst
 * flies out of the ring.
 */
@Composable
private fun DoneCounter(
    done: Int,
    goal: Int,
    copy: HomeCopy,
    consumeGoalCelebration: (done: Int, goal: Int) -> Boolean,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val reduced = LocalReducedMotion.current
    val reached = isGoalReached(done, goal)
    var burst by remember { mutableIntStateOf(0) }
    // Asked when the goal is first seen met by this composition; the answer is "yes" once a day.
    LaunchedEffect(reached) {
        if (reached && consumeGoalCelebration(done, goal)) burst += 1
    }
    Row(
        Modifier.clearAndSetSemantics {
            contentDescription = if (reached) copy.goalReachedDescription(done, goal) else copy.todayProgress(done, goal)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = reached,
            transitionSpec = {
                if (reduced) {
                    (EnterTransition.None togetherWith ExitTransition.None)
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                } else {
                    (fadeIn(tween(CounterFadeMillis, delayMillis = CounterFadeMillis)) togetherWith fadeOut(tween(CounterFadeMillis)))
                        .using(SizeTransform(clip = false))
                }
            },
            label = "today-counter",
        ) { isReached ->
            if (isReached) {
                Text(
                    text = copy.goalReached(done),
                    style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.learned,
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedCounter(value = done, style = type.title2, color = colors.ink)
                    Text(
                        text = " ${copy.ofGoal(goal)}",
                        style = type.callout,
                        color = colors.inkSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(QhSpacing.x2))
        ProgressRing(
            progress = if (reached) 1f else if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f,
            size = 22.dp,
            strokeWidth = 3.dp,
            color = if (reached) colors.learned else colors.primary,
        ) {
            if (reached) AnimatedCheckmark(size = 12.dp, color = colors.learned, strokeWidth = 2.dp)
            CelebrationBurst(trigger = burst, modifier = Modifier.matchParentSize(), radius = GoalBurstRadius)
        }
    }
}

private const val CounterFadeMillis = 140

/** Small: the ring is 22dp, the burst only has to reach past the counter text and the header edge. */
private val GoalBurstRadius = 36.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayCards(cards: List<TodayCardUi>, copy: HomeCopy, onOpenCard: (TodayCardUi) -> Unit) {
    val listState = rememberLazyListState()
    // Cards keep one height, whatever their texts need: the tallest card sets it for all of them.
    val cardHeight = rememberCardHeight(cards, copy)
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

/** The texts of one card: the kind heading and the two lines under it. */
private class CardTexts(val kindLabel: String, val title: String, val subtitle: String)

private fun cardTexts(card: TodayCardUi, copy: HomeCopy): CardTexts {
    val kindLabel = when (card.kind) {
        TodayKind.New -> copy.kindNew
        TodayKind.Review -> copy.kindReview
        TodayKind.Weak -> copy.kindWeak
    }
    return when (card.kind) {
        TodayKind.New -> CardTexts(kindLabel, card.surahName, copy.ayahRange(card.startAyah, card.endAyah))
        TodayKind.Review, TodayKind.Weak -> CardTexts(
            kindLabel = kindLabel,
            title = copy.ayahCount(card.totalAyahs),
            subtitle = if (card.surahCount > 1) copy.surahCount(card.surahCount) else card.surahName,
        )
    }
}

/**
 * The height every card gets: what the card with the longest texts needs (a heading of two lines
 * beside the icon, a title of two lines, a subtitle), but never less than the resting height scaled
 * with the font. The headings are the longest in Tajik and Uzbek ("Ҷойҳои заиф", "Zaif joylar").
 */
@Composable
private fun rememberCardHeight(cards: List<TodayCardUi>, copy: HomeCopy): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val type = QhTheme.type
    return remember(cards, copy, type, density, measurer) {
        val minimum = (BaseCardHeight * density.fontScale.coerceIn(1f, 1.5f)).dp
        tallestCardHeight(
            texts = cards.map { cardTexts(it, copy) },
            measurer = measurer,
            density = density,
            labelStyle = type.label,
            titleStyle = type.title2,
            subtitleStyle = type.callout,
            minimum = minimum,
        )
    }
}

private fun tallestCardHeight(
    texts: List<CardTexts>,
    measurer: TextMeasurer,
    density: Density,
    labelStyle: TextStyle,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle,
    minimum: Dp,
): Dp = with(density) {
    val innerWidth = (CardWidth - CardPadding * 2).roundToPx()
    val labelWidth = (CardWidth - CardPadding * 2 - IconBadgeSize - HeaderGap).roundToPx()
    val badge = IconBadgeSize.roundToPx()

    fun heightOf(text: String, style: TextStyle, maxLines: Int, width: Int): Int = measurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = width),
        density = density,
    ).size.height

    val tallest = texts.maxOfOrNull { text ->
        maxOf(badge, heightOf(text.kindLabel, labelStyle, KindLabelMaxLines, labelWidth)) +
            heightOf(text.title, titleStyle, TitleMaxLines, innerWidth) +
            heightOf(text.subtitle, subtitleStyle, 1, innerWidth)
    } ?: 0
    maxOf(minimum, tallest.toDp() + MinBlockGap + CardPadding * 2)
}

private const val BaseCardHeight = 128f
private const val KindLabelMaxLines = 2
private const val TitleMaxLines = 2
private val CardWidth = 156.dp
private val CardPadding = QhSpacing.card
private val IconBadgeSize = 28.dp
private val HeaderGap = QhSpacing.x2
private val MinBlockGap = QhSpacing.x2

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
    val texts = remember(card, copy) { cardTexts(card, copy) }
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
            .padding(CardPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = "${texts.kindLabel}: ${texts.title}, ${texts.subtitle}"
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HeaderGap)) {
            Box(
                Modifier
                    .size(IconBadgeSize)
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(16.dp))
            }
            // Up to two lines: "Weak spots" must not be cut to "Weak sp…" next to the icon.
            Text(
                text = texts.kindLabel,
                style = type.label,
                color = colors.ink,
                maxLines = KindLabelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column {
            Text(
                text = texts.title,
                style = type.title2,
                color = colors.ink,
                maxLines = TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = if (card.kind == TodayKind.New && card.sharesElements) {
                    Modifier.sharedBoundsKey(SharedKeys.surahTitle(origin, card.surah), SharedResize.Scale)
                } else {
                    Modifier
                },
            )
            Text(texts.subtitle, style = type.callout, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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
        TodayKind.New -> CardLook(colors.goldContainer, colors.gold.copy(alpha = 0.35f), 1.dp, colors.gold, Icons.AutoMirrored.Rounded.MenuBook)
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
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
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
