package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The label of the repeat ring in the "listen" slide: ayahs 1-5, three times each. The same in every language. */
private const val RepeatLabel = "1–5 · 3×"

/**
 * Steps 2-4: three slides that tell what the app is — read, listen, memorize — as one step with a pager. They are swiped with a
 * finger or moved with "Next" (the screen sets [page]); [onPage] reports a swipe. The Quran text comes from the loaded Quran, and
 * until it is there from a built-in sample: the slides never wait for the data.
 */
@Composable
internal fun StoryStep(
    copy: OnboardingCopy,
    page: Int,
    fatiha: List<PreviewAyah>,
    fatihaName: String,
    fontStyle: ReadingFontStyle,
    onPage: (Int) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val pagerState = rememberPagerState(initialPage = page.coerceIn(0, StoryPageCount - 1)) { StoryPageCount }

    // A swipe settles on a page: the screen state follows it.
    val latestOnPage by rememberUpdatedState(onPage)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled -> latestOnPage(settled) }
    }
    // "Next" and "Back" set the page of the screen state: the pager follows it.
    LaunchedEffect(page) {
        val target = page.coerceIn(0, StoryPageCount - 1)
        if (pagerState.currentPage != target) {
            if (reduced) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { index ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QhSpacing.screen)
                    .padding(top = QhSpacing.x2, bottom = QhSpacing.x2),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x5),
            ) {
                when (index) {
                    0 -> {
                        ReadArt(
                            caption = passageRangeLabel(fatihaName, 1, 3),
                            ayahs = fatiha,
                            fontStyle = fontStyle,
                        )
                        StoryText(copy.storyReadTitle, copy.storyReadBody)
                    }

                    1 -> {
                        ListenArt(repeatLabel = RepeatLabel)
                        StoryText(copy.storyListenTitle, copy.storyListenBody)
                    }

                    else -> {
                        MemorizeArt(ayah = fatiha.getOrElse(1) { fatiha.first() }, fontStyle = fontStyle)
                        StoryText(copy.storyMemorizeTitle, copy.storyMemorizeBody)
                    }
                }
            }
        }
        PagerDots(
            current = pagerState.currentPage,
            label = copy.slideLabel(pagerState.currentPage + 1, StoryPageCount),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = QhSpacing.x3),
        )
    }
}

@Composable
private fun StoryText(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        CrossfadeText(
            text = title,
            style = QhTheme.type.headline,
            color = QhTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        CrossfadeText(text = body, style = QhTheme.type.body, color = QhTheme.colors.inkSecondary)
    }
}

/** Three dots; the current one stretches into a pill on a spring. Announced as "Slide n of 3". */
@Composable
private fun PagerDots(current: Int, label: String, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(StoryPageCount) { index ->
            val width: Dp by animateDpAsState(
                targetValue = if (index == current) 20.dp else 7.dp,
                animationSpec = QhMotion.snappy<Dp>().orSnap(),
                label = "dot-width",
            )
            val color: Color by animateColorAsState(
                targetValue = if (index == current) colors.primary else colors.surfaceStrong,
                animationSpec = QhMotion.snappy<Color>().orSnap(),
                label = "dot-color",
            )
            Box(
                Modifier
                    .size(width = width, height = 7.dp)
                    .clip(QhTheme.shapes.full)
                    .background(color),
            )
        }
    }
}
