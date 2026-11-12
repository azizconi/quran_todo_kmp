@file:OptIn(ExperimentalSharedTransitionApi::class)

package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.ui.components.Calligraphy
import tj.app.quran_todo.ui.components.CalligraphyText
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.rememberSharedSettled
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.progress.HeroUi
import tj.app.quran_todo.ui.progress.rememberRollingValue
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

internal val BigRingSize = 160.dp
private val BigRingStroke = 14.dp

/**
 * The hero of the screen: a big ring with the covered share of the PLAN counting up in its middle (the whole Quran without a
 * plan), "Juz Amma: 10 of 564 · 2 %" and the whole Quran as one line under it, and the surahs known / being learned. The ring is the shared element that the small ring in the home
 * top bar flies into (`SharedKeys.ProgressRing`).
 */
@Composable
internal fun RingHero(
    hero: HeroUi,
    copy: ProgressCopy,
    modifier: Modifier = Modifier,
) {
    QhCard(
        modifier = modifier.fillMaxWidth(),
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(horizontal = QhSpacing.cardLarge, vertical = QhSpacing.x7),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BigRing(hero = hero, copy = copy)
            Spacer(Modifier.height(QhSpacing.x5))
            val scope = hero.planScope
            if (scope != null) {
                // By the plan: "Juz Amma: 10 of 564 · 2 %", and the whole Quran as one line under it.
                Text(
                    text = copy.planRingLine(scope, hero.ringCovered, hero.ringTotal, hero.percent),
                    style = QhTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = QhTheme.colors.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(QhSpacing.x1))
                Text(
                    text = copy.wholeQuranLine(hero.coveredAyahs, hero.totalAyahs, hero.learnedAyahs),
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = copy.heroAyahs(hero.coveredAyahs, hero.learnedAyahs),
                    style = QhTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = QhTheme.colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
            // "I know by heart: 3 surahs · Learning: 5 surahs"; a part that is zero is left out.
            copy.surahsLine(hero.learnedSurahs, hero.learningSurahs)?.let { line ->
                Spacer(Modifier.height(QhSpacing.x1))
                Text(
                    text = line,
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The ring with the word "hifz" in calligraphy over the percentage; the ring itself fills from 0 on first show. */
@Composable
internal fun BigRing(
    hero: HeroUi,
    copy: ProgressCopy,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val description = copy.ringDescription(hero.percent, hero.ringCovered, hero.ringTotal, hero.ringLearned, hero.planScope?.let(copy::scopeName))
    val settledAtFirstFrame = rememberSharedSettled()
    val percent = rememberRollingValue(target = hero.percent, startDelayMillis = CounterStartMillis)
    // Arriving on the flight of the small ring: the big one starts at its value instead of filling from 0 under the full one.
    val startFull = remember { !settledAtFirstFrame }
    ProgressRing(
        progress = hero.fraction,
        startFull = startFull,
        size = BigRingSize,
        strokeWidth = BigRingStroke,
        trackColor = colors.primary.copy(alpha = 0.12f),
        modifier = modifier
            .sharedBoundsKey(SharedKeys.ProgressRing, SharedResize.Scale, zIndexInOverlay = 1f)
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(hero.fraction, 0f..1f)
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CalligraphyText(Calligraphy.Hifz, size = 20.dp, revealMillis = 700, shimmer = false)
            Spacer(Modifier.height(QhSpacing.x1))
            Percentage(value = percent, signFirst = copy.percentFirst)
        }
    }
}

/** "42%" ("%42" in Turkish) with the sign as a small superscript. */
@Composable
private fun Percentage(value: Int, signFirst: Boolean) {
    val colors = QhTheme.colors
    val number = QhTheme.type.display.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp)
    val sign = QhTheme.type.title1.copy(fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.Top) {
        if (signFirst) Text("%", style = sign, color = colors.inkSecondary, modifier = Modifier.padding(top = QhSpacing.x1, end = 2.dp))
        AnimatedCounter(value = value, style = number, color = colors.ink)
        if (!signFirst) Text("%", style = sign, color = colors.inkSecondary, modifier = Modifier.padding(top = QhSpacing.x1, start = 2.dp))
    }
}

/** The percentage starts counting when the section has settled in. */
private const val CounterStartMillis = 250L
