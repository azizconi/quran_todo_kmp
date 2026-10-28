package tj.app.quran_todo.ui.hifz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.theme.QhTheme

/** The summary comes up this long after the last rating, once the last ayah has folded. */
private const val SummaryDelayMillis = 480L

/**
 * Everything the mode puts on top of the reader that is not a bar: the back gesture (it asks to leave the mode
 * instead of the reader), the "leave memorize mode?" question and, at the end of the session, the summary.
 */
@Composable
fun HifzOverlays(mode: HifzMode) {
    val reduced = LocalReducedMotion.current
    val session = mode.session
    val askingToLeave by mode.viewModel.exitPrompt.collectAsState()

    BackHandler(enabled = session != null) { mode.viewModel.requestExit() }
    // Reciting from memory takes time; the screen must not go dark in the middle of it.
    KeepScreenOnEffect(enabled = session != null)

    if (session != null && askingToLeave && !session.finished) {
        LeaveSheet(mode = mode, session = session)
    }

    val finished = session?.finished == true
    var summaryReady by remember { mutableStateOf(false) }
    LaunchedEffect(finished) {
        if (finished) {
            if (!reduced) delay(SummaryDelayMillis)
            summaryReady = true
        } else {
            summaryReady = false
        }
    }
    if (session != null && finished && summaryReady) {
        SummarySheet(mode = mode, session = session)
    }
}

/** Hides [sheetState] with its slide, then runs [after]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberHideThen(sheetState: SheetState): (after: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, scope) {
        { after -> scope.launch { sheetState.hide() }.invokeOnCompletion { after() } }
    }
}

// -- Leaving ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveSheet(mode: HifzMode, session: HifzSession) {
    val colors = QhTheme.colors
    val copy = mode.copy
    val viewModel = mode.viewModel
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState)
    QhSheet(onDismiss = viewModel::dismissExitPrompt, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = copy.exitTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = copy.exitBody(session.ratedCount, session.size),
                style = QhTheme.type.body,
                color = colors.inkSecondary,
            )
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QhButton(
                    text = copy.keepGoing,
                    onClick = { hideThen(viewModel::dismissExitPrompt) },
                    modifier = Modifier.fillMaxWidth(),
                )
                QhButton(
                    text = copy.leave,
                    onClick = { hideThen(viewModel::exit) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

// -- Summary ------------------------------------------------------------------------------------------------

/** The order of the breakdown, as in the spec: Easy, Good, Hard, Forgot. */
private val SummaryOrder = listOf(ReviewQuality.EASY, ReviewQuality.GOOD, ReviewQuality.HARD, ReviewQuality.FORGOT)

/**
 * The end of a session: a ring with the share of Good and Easy (it fills, its number rolls up), the count of
 * every rating rolling up beside it, a small burst with the success buzz, and "Repeat the hard ones" when there
 * are some, then "Done".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummarySheet(mode: HifzMode, session: HifzSession) {
    val colors = QhTheme.colors
    val copy = mode.copy
    val viewModel = mode.viewModel
    val language = LocalAppLanguage.current
    val ayahCounts = remember(language) { readerCopy(language) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState)

    val percent = session.recallPercent()
    var shownPercent by remember { mutableIntStateOf(0) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(260)
        shownPercent = percent
        delay(260)
        burst++
    }

    QhSheet(onDismiss = viewModel::exit, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = copy.summaryTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${mode.surahName} · ${ayahCounts.ayahCount(session.size)}",
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 12.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = session.recallShare,
                    size = 120.dp,
                    strokeWidth = 10.dp,
                    color = colors.primary,
                    trackColor = colors.surfaceMuted,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(value = shownPercent, style = QhTheme.type.headline, color = colors.ink)
                        Text(
                            text = "%",
                            style = QhTheme.type.title2,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                        )
                    }
                }
                CelebrationBurst(trigger = burst, modifier = Modifier.matchParentSize(), radius = 120.dp)
            }
            Text(
                text = copy.recallCaption,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SummaryOrder.forEachIndexed { position, quality ->
                    CountColumn(
                        quality = quality,
                        count = session.count(quality),
                        copy = copy,
                        delayMillis = 360L + position * 110L,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (session.hardItems.isNotEmpty()) {
                    QhButton(
                        text = copy.retryHard,
                        onClick = { hideThen(viewModel::startRetry) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = QhButtonVariant.Tonal,
                        size = QhButtonSize.M,
                    )
                }
                QhButton(
                    text = copy.done,
                    onClick = { hideThen(viewModel::exit) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One rating of the breakdown: its count rolling up from zero, and its name with a dot of its color. */
@Composable
private fun CountColumn(
    quality: ReviewQuality,
    count: Int,
    copy: HifzCopy,
    delayMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        delay(delayMillis)
        shown = count
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedCounter(value = shown, style = QhTheme.type.title1, color = colors.ink)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ratingColor(quality)),
            )
            Text(
                text = copy.label(quality),
                style = QhTheme.type.label,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
