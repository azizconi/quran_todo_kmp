package tj.app.quran_todo.ui.hifz

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.hints.Hints
import tj.app.quran_todo.ui.components.hints.InlineHint
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.rememberHintsCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The controls of the lesson's "Recall", pinned to the bottom of the screen above the system bar (task U6), so the answer is always
 * where the thumb is, whatever the list does:
 *
 * - the ayah being recalled is hidden: the main button "Reveal" (or "Hide more" of the progressive hint) and above it one row of the
 *   quieter actions: "Listen", "Hint", "Record reading";
 * - the ayah is revealed: the line "The answer decides when the passage comes back" and two large answers, "I remember" and "Hard";
 * - every ayah is answered (task U5): the result row (the grade, when the passage comes back, "Change") and the button [nextLabel].
 *
 * The same controls under the ayah (for the reader and the drill) are `RecallControlsHost`.
 *
 * [result] is the grade that is about to be written (null while ayahs are left); [returnDays] says when the passage comes back (null: leave
 * it out). [onRecord] opens "Record my reading", [onChange] the grade sheet, [onNext] writes the grade and goes on; [saving] holds the button.
 */
@Composable
fun RecallBottomBar(
    host: RecallHost,
    session: HifzSession,
    result: RecallResult?,
    returnDays: Int?,
    recordLabel: String,
    onRecord: () -> Unit,
    onChange: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
    saving: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .animateContentSize(QhMotion.gentle<IntSize>().orSnap()),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x3, bottom = QhSpacing.x3),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            when {
                result != null -> ResultBar(host, result, returnDays, onChange, nextLabel, onNext, saving)
                session.revealed -> AnswerBar(host, session)
                else -> RevealBar(host, session, recordLabel, onRecord)
            }
        }
    }
}

// -- Every ayah answered ------------------------------------------------------------------------------------

@Composable
private fun ResultBar(
    host: RecallHost,
    result: RecallResult,
    returnDays: Int?,
    onChange: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
    saving: Boolean,
) {
    val copy = host.copy
    val colors = QhTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = copy.result(result.grade), style = QhTheme.type.title2, color = colors.ink)
            if (returnDays != null) {
                Text(text = copy.returns(returnDays), style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        Box(
            Modifier
                .heightIn(min = QhSpacing.minTouch)
                .pressable(onClick = onChange, shape = QhTheme.shapes.full, role = Role.Button, onClickLabel = copy.change)
                .clip(QhTheme.shapes.full)
                .padding(horizontal = QhSpacing.x3),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = copy.change, style = QhTheme.type.label, color = colors.primary)
        }
    }
    QhButton(
        text = nextLabel,
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        loading = saving,
        enabled = !saving,
    )
}

// -- The ayah is revealed -----------------------------------------------------------------------------------

@Composable
private fun AnswerBar(host: RecallHost, session: HifzSession) {
    val copy = host.copy
    val item = session.current ?: return
    InlineHint(id = Hints.LessonGrade, text = rememberHintsCopy().lessonGrade)
    if (item.suggestsHard) {
        Text(text = copy.peeked(item.peeks), style = QhTheme.type.label, color = QhTheme.colors.inkSecondary)
    }
    AnswerPills(host = host, item = item, position = session.index, early = false)
}

// -- The ayah is hidden -------------------------------------------------------------------------------------

@Composable
private fun RevealBar(host: RecallHost, session: HifzSession, recordLabel: String, onRecord: () -> Unit) {
    val copy = host.copy
    val colors = QhTheme.colors
    val listening by host.listening.collectAsState()
    val item = session.current
    val mode = host.hintMode
    val words = item?.let { host.words(it.inSurah) }
    val step = item?.hideStep ?: 0
    // The progressive hint: "Hide more" takes the place of "Reveal" until the ayah is hidden entirely, and the answers are on offer from
    // the first hidden quarter on (as under the ayah in the reader).
    val hidesMore = mode == HifzHintMode.PROGRESSIVE && words != null && RecallHints.canHideMore(words.wordCount, step)
    var hintOpen by remember { mutableStateOf(false) }

    if (session.markedCount == 0) {
        Text(text = copy.peekHint, style = QhTheme.type.label, color = colors.inkSecondary)
    }
    if (mode == HifzHintMode.PROGRESSIVE && step > 0) {
        Text(text = copy.hiddenShare(RecallHints.percentAt(step)), style = QhTheme.type.label, color = colors.inkSecondary)
    }
    if (item != null && item.suggestsHard) {
        Text(text = copy.peeked(item.peeks), style = QhTheme.type.label, color = colors.inkSecondary)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        BarAction(
            icon = if (listening) Icons.Rounded.Pause else Icons.Rounded.Headphones,
            label = if (listening) copy.listenPause else copy.listen,
            onClick = host::toggleListen,
            modifier = Modifier.weight(1f),
        )
        BarAction(
            icon = Icons.Rounded.Lightbulb,
            label = copy.hintTitle,
            onClick = { hintOpen = true },
            modifier = Modifier.weight(1f),
        )
        BarAction(icon = Icons.Rounded.Mic, label = recordLabel, onClick = onRecord, modifier = Modifier.weight(1f))
    }
    if (hidesMore) {
        QhButton(
            text = copy.hideMore,
            onClick = { host.hideMore(session.index) },
            modifier = Modifier.fillMaxWidth(),
            size = QhButtonSize.L,
            icon = Icons.Rounded.VisibilityOff,
        )
        if (step > 0 && item != null) {
            AnswerPills(host = host, item = item, position = session.index, early = true)
        }
    } else {
        QhButton(
            text = copy.reveal,
            onClick = host::reveal,
            modifier = Modifier.fillMaxWidth(),
            size = QhButtonSize.L,
            icon = Icons.Rounded.Visibility,
        )
    }
    if (hintOpen) {
        HintSheet(copy = copy, current = mode, ayah = words, onPick = host::setHintMode, onDismiss = { hintOpen = false })
    }
}

/** A quiet action of the bar: the icon over a short name, at least 48 dp high; the name may take two lines. */
@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    Column(
        modifier
            .heightIn(min = 56.dp)
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.Button, onClickLabel = label)
            .clip(shape)
            .background(colors.surfaceMuted)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = QhTheme.type.label,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
