package tj.app.quran_todo.ui.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The list scrolls to the ayah being recalled this long after the previous one was answered (it folds first). */
private const val GlideDelayMillis = 160L

/**
 * The passage of a lesson card in the "Recall" phase: the same rows as the reader's "Check yourself" (the hidden
 * ayah, its rosette with the answer, the controls under the ayah being recalled), one under the other. The list
 * follows the ayah being recalled.
 *
 * - [session] and [host] are the state and the commands; the host is what the controls talk to.
 * - [hard] are ayahs of the passage that are marked difficult already: their rosettes carry the mark.
 * - [bottomContent] comes after the last ayah (the "grade" button once everything is answered).
 * - [compact] is the lesson (task U6): the rows are short, the number sits on the line of the text, and the controls are not under the
 *   ayah but in a bar the screen pins to its bottom; the list keeps the ayah being recalled (and the one before it) in view above it.
 */
@Composable
fun RecallPassage(
    session: HifzSession,
    ayahs: List<AyahEntity>,
    host: RecallHost,
    hintMode: HifzHintMode,
    arabicStyle: TextStyle,
    hard: Set<Int>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    compact: Boolean = false,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val reduced by rememberUpdatedState(LocalReducedMotion.current)
    val currentIndex = session.takeUnless { it.finished }?.index
    // The list moves to the ayah being recalled as soon as the one before has folded.
    LaunchedEffect(currentIndex) {
        val position = currentIndex ?: return@LaunchedEffect
        if (position == 0) return@LaunchedEffect
        delay(if (reduced) 0L else GlideDelayMillis)
        // The compact list keeps the ayah answered last above the current one, as the way back to what was just said.
        val target = if (compact) position - 1 else position
        if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        items(ayahs, key = { it.number }) { ayah ->
            val inSurah = ayah.numberInSurah
            val state = remember(session, inSurah, hintMode, host.copy) {
                rowStateOf(session, inSurah, hintMode, host)
            }
            RecallRow(
                state = state,
                ayah = ayah,
                host = host,
                arabicStyle = arabicStyle,
                weak = inSurah in hard,
                compact = compact,
            )
        }
        if (bottomContent != null) {
            item(key = "bottom") { bottomContent() }
        }
    }
}

/** The row state of the ayah [inSurah] of [session]; the drill of the difficult ayahs draws its one hidden ayah with it too. */
internal fun rowStateOf(session: HifzSession, inSurah: Int, hintMode: HifzHintMode, host: RecallHost): HifzRowState {
    val phase = session.phaseOf(inSurah)
    val item = session.itemOf(inSurah)
    return HifzRowState(
        phase = phase,
        revealed = phase == HifzPhase.Current && session.revealed,
        mark = if (phase == HifzPhase.Done) item?.mark else null,
        justMarked = phase == HifzPhase.Done && session.items.getOrNull(session.index - 1)?.inSurah == inSurah,
        hintMode = hintMode,
        hideStep = item?.hideStep ?: 0,
        peeked = if (phase == HifzPhase.Current) item?.peeked.orEmpty() else emptySet(),
        animateIn = false,
        copy = host.copy,
        host = host,
        position = session.positionOf(inSurah) ?: -1,
    )
}

@Composable
private fun RecallRow(
    state: HifzRowState,
    ayah: AyahEntity,
    host: RecallHost,
    arabicStyle: TextStyle,
    weak: Boolean,
    compact: Boolean,
) {
    val colors = QhTheme.colors
    if (compact) {
        CompactRecallRow(state = state, ayah = ayah, arabicStyle = arabicStyle, weak = weak)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .hifzRow(state),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x1, bottom = QhSpacing.x4),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(QhSpacing.minTouch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HifzRosette(number = ayah.numberInSurah, status = null, weak = weak, state = state)
            }
            HifzAyahContent(
                state = state,
                arabic = splitBismillah(ayah.surahNumber, ayah.numberInSurah, ayah.text),
                arabicStyle = arabicStyle,
                translation = null,
                translationStyle = QhTheme.type.body,
            )
            RecallControlsHost(host = host, inSurah = ayah.numberInSurah)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}

/** A row of the lesson: the rosette at the start of the line of the text, little air above and below, no controls. */
@Composable
private fun CompactRecallRow(
    state: HifzRowState,
    ayah: AyahEntity,
    arabicStyle: TextStyle,
    weak: Boolean,
) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .hifzRow(state),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(vertical = QhSpacing.x2),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            HifzRosette(number = ayah.numberInSurah, status = null, weak = weak, state = state, showLabel = false)
            HifzAyahContent(
                state = state,
                arabic = splitBismillah(ayah.surahNumber, ayah.numberInSurah, ayah.text),
                arabicStyle = arabicStyle,
                translation = null,
                translationStyle = QhTheme.type.body,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}
