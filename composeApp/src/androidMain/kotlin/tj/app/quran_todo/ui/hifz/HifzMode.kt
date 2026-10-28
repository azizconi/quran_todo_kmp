package tj.app.quran_todo.ui.hifz

import android.os.SystemClock
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.hifzCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.state.HifzHintMode

/**
 * What one row of the reader needs to draw an ayah in the memorize mode; null means the mode is off and the
 * row is drawn as always. Cheap to compare.
 */
@Immutable
data class HifzRowState(
    val phase: HifzPhase,
    /** The ayah being recalled has been revealed (its ratings are on offer). */
    val revealed: Boolean,
    /** The rating of a rated ayah. */
    val rating: ReviewQuality?,
    /** This is the ayah rated last: its mark pops in. */
    val justRated: Boolean,
    /** The first word of a hidden ayah stays readable. */
    val firstWordHint: Boolean,
    /** The mode has only just started: the rows on screen melt into their veil instead of appearing veiled. */
    val animateIn: Boolean,
    val copy: HifzCopy,
)

/**
 * The memorize mode as the reader sees it (docs/redesign/ANDROID_REDESIGN.md, 6.5). Everything about the mode
 * lives behind this object; the reader asks it for the row state of an ayah, the controls under the current
 * ayah, the eye button and the overlays, and otherwise behaves as it always does. Get it from
 * [rememberHifzMode].
 */
@Stable
class HifzMode internal constructor(
    internal val viewModel: HifzViewModel,
    internal val surah: SurahWithAyahs,
    internal val surahName: String,
    internal val copy: HifzCopy,
    private val sessionState: State<HifzSession?>,
    private val hintState: State<HifzHintMode>,
    private val startedAt: LongArray,
    private val startIndex: State<() -> Int>,
    private val rangeEndIndex: State<() -> Int?>,
) {
    /** The running session, or null. Reading it recomposes the reader of it whenever an ayah is rated. */
    val session: HifzSession? get() = sessionState.value

    val active: Boolean get() = session != null

    /** Index in the surah's ayah list of the ayah being recalled; null when the mode is off or the session over. */
    val currentIndex: Int?
        get() = session?.takeUnless { it.finished }?.current?.let { it.inSurah - 1 }

    /** The eye button: starts a session at the ayah the reader is on. */
    fun start() {
        viewModel.startFromAyah(surah, startIndex.value(), rangeEndIndex.value())
    }

    fun rowState(index: Int): HifzRowState? {
        val session = session ?: return null
        val inSurah = index + 1
        val phase = session.phaseOf(inSurah)
        val item = session.itemOf(inSurah)
        return HifzRowState(
            phase = phase,
            revealed = phase == HifzPhase.Current && session.revealed,
            rating = if (phase == HifzPhase.Done) item?.rating else null,
            justRated = phase == HifzPhase.Done && session.items.getOrNull(session.index - 1)?.inSurah == inSurah,
            firstWordHint = hintState.value == HifzHintMode.FIRST_WORD,
            animateIn = SystemClock.uptimeMillis() - startedAt[0] < EnterWindowMillis,
            copy = copy,
        )
    }

    /** The slot under an ayah of the session: nothing for the ayahs outside it, the controls host for the rest. */
    fun controlsFor(index: Int): (@Composable () -> Unit)? {
        val session = session ?: return null
        if (session.phaseOf(index + 1) == HifzPhase.Outside) return null
        return { HifzControlsHost(mode = this, inSurah = index + 1) }
    }

    private companion object {
        /** Rows composed this soon after the start of the mode melt into the veil. */
        const val EnterWindowMillis = 700L
    }
}

/**
 * Creates the mode for the reader of [surah]: the view model, the copy, the saved-text copy of the session
 * (kept across the process being killed) and the scrolling that follows the ayah being recalled.
 *
 * - [startIndex] / [rangeEndIndex] are asked when the eye button is pressed: the ayah the reader is on, and the
 *   end of the repeat passage when one is on.
 * - [bringToFocus] glides the list so the ayah with that index sits where the reader holds the playing ayah.
 * - [headerItems] is the number of list items before the first ayah.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun rememberHifzMode(
    route: Dest.Reader,
    surah: SurahWithAyahs,
    surahName: String,
    listState: LazyListState,
    headerItems: Int,
    bringToFocus: suspend (ayahIndex: Int) -> Unit,
    startIndex: () -> Int,
    rangeEndIndex: () -> Int?,
): HifzMode {
    val viewModel = koinViewModel<HifzViewModel>(parameters = { parametersOf(route) })
    val language = LocalAppLanguage.current
    val copy = remember(language) { hifzCopy(language) }
    val sessionState = viewModel.session.collectAsState()
    val hintState = viewModel.hintMode.collectAsState()
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current

    // The mode starting in this composition (as opposed to being there already after a rotation) lets the rows
    // on screen melt into their veil.
    val active = sessionState.value != null
    val wasActive = remember { booleanArrayOf(active) }
    val startedAt = remember { longArrayOf(0L) }
    if (active && !wasActive[0]) startedAt[0] = SystemClock.uptimeMillis()
    SideEffect { wasActive[0] = active }

    // A text copy of the session survives the process being killed; "off" says the mode was left.
    var saved by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.enter(surah, saved)
        viewModel.session.collect { saved = it?.encode() ?: HifzViewModel.SavedOff }
    }

    // The ayah being recalled: the rated one folds, the list glides to the next, then the controls are made
    // sure to be above the dock.
    val session = sessionState.value
    val focusKey = session?.takeUnless { it.finished }?.let { it.items[it.index].inSurah to it.revealed }
    val bring by rememberUpdatedState(bringToFocus)
    LaunchedEffect(focusKey) {
        val (inSurah, revealed) = focusKey ?: return@LaunchedEffect
        val ayahIndex = inSurah - 1
        if (!revealed) {
            delay(if (reduced) 0L else GlideDelayMillis)
            bring(ayahIndex)
            delay(if (reduced) 0L else ControlsEnterMillis)
        } else {
            delay(if (reduced) 0L else ControlsEnterMillis)
        }
        listState.keepRowBottomVisible(ayahIndex + headerItems, margin = with(density) { 12.dp.toPx() }, animate = !reduced)
    }

    val start = rememberUpdatedState(startIndex)
    val rangeEnd = rememberUpdatedState(rangeEndIndex)
    return remember(viewModel, surah, surahName, copy) {
        HifzMode(viewModel, surah, surahName, copy, sessionState, hintState, startedAt, start, rangeEnd)
    }
}

/** The rated ayah folds before the list moves on. */
private const val GlideDelayMillis = 160L

/** About the time the controls of the new current ayah take to arrive. */
private const val ControlsEnterMillis = 380L

/**
 * Scrolls just far enough that the bottom of the list item [itemIndex] is [margin] px above the end of the
 * visible list, but never so far that its top goes under the top bar. The buttons under the ayah are the point.
 */
private suspend fun LazyListState.keepRowBottomVisible(itemIndex: Int, margin: Float, animate: Boolean) {
    val info = layoutInfo
    val viewport = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
    if (viewport <= 0) return
    val item = info.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return
    val overflow = item.offset + item.size - (viewport - margin)
    if (overflow < 1f) return
    val room = item.offset.toFloat()
    val delta = overflow.coerceAtMost(room).coerceAtLeast(0f)
    if (delta < 1f) return
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}

/** Percent of recall for display: 0..100. */
internal fun HifzSession.recallPercent(): Int = (recallShare * 100f).roundToInt()
