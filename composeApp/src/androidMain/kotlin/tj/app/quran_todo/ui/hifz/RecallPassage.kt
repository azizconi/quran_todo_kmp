package tj.app.quran_todo.ui.hifz

import androidx.compose.foundation.background
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
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val reduced by rememberUpdatedState(LocalReducedMotion.current)
    val currentIndex = session.takeUnless { it.finished }?.index
    // The list moves to the ayah being recalled as soon as the one before has folded.
    LaunchedEffect(currentIndex) {
        val position = currentIndex ?: return@LaunchedEffect
        if (position == 0) return@LaunchedEffect
        delay(if (reduced) 0L else GlideDelayMillis)
        if (reduced) listState.scrollToItem(position) else listState.animateScrollToItem(position)
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
            )
        }
        if (bottomContent != null) {
            item(key = "bottom") { bottomContent() }
        }
    }
}

private fun rowStateOf(session: HifzSession, inSurah: Int, hintMode: HifzHintMode, host: RecallHost): HifzRowState {
    val phase = session.phaseOf(inSurah)
    val item = session.itemOf(inSurah)
    return HifzRowState(
        phase = phase,
        revealed = phase == HifzPhase.Current && session.revealed,
        mark = if (phase == HifzPhase.Done) item?.mark else null,
        justMarked = phase == HifzPhase.Done && session.items.getOrNull(session.index - 1)?.inSurah == inSurah,
        firstWordHint = hintMode == HifzHintMode.FIRST_WORD,
        animateIn = false,
        copy = host.copy,
    )
}

@Composable
private fun RecallRow(
    state: HifzRowState,
    ayah: AyahEntity,
    host: RecallHost,
    arabicStyle: TextStyle,
    weak: Boolean,
) {
    val colors = QhTheme.colors
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
