package tj.app.quran_todo.ui.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhTheme

/** The five levels in the order the sheet lists them: from the hardest to the gentlest, then the one that goes by steps. */
internal val HintLevels = listOf(
    HifzHintMode.NONE,
    HifzHintMode.FIRST_WORD,
    HifzHintMode.FIRST_LETTERS,
    HifzHintMode.EVERY_OTHER,
    HifzHintMode.PROGRESSIVE,
)

/** The ayah the examples of the sheet are drawn on when there is none being recalled (the settings open the sheet too): 112:1. */
private val SampleWords = listOf("قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ")

/**
 * The small sheet of the hint levels (task F2a): the five levels, each with a line that says what it leaves and an example on the
 * ayah being recalled ([ayah], its words; Al-Ikhlas 1 when there is none, as in the settings). [current] is the level in force;
 * [onPick] gets the choice, which works at once (the screens keep it in the settings of the app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HintSheet(
    copy: HifzCopy,
    current: HifzHintMode,
    ayah: AyahWords?,
    onPick: (HifzHintMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val words = remember(ayah) { ayah?.let { shown -> shown.ranges.map { shown.text.substring(it) } } ?: SampleWords }
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.heightIn(max = 640.dp)) {
            Text(
                text = copy.hintTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = copy.hintSubtitle,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HintLevels.forEach { mode ->
                    HintRow(
                        mode = mode,
                        selected = mode == current,
                        copy = copy,
                        example = RecallHints.preview(mode, words),
                        onClick = {
                            onPick(mode)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HintRow(mode: HifzHintMode, selected: Boolean, copy: HifzCopy, example: String, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    val family = getQuranFontFamily()
    val type = QhTheme.type
    val exampleStyle = remember(type, family) { type.quranInline(family, 22.sp) }
    val name = copy.hintName(mode)
    Column(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton, onClickLabel = name)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceMuted)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = QhTheme.type.title2,
                color = if (selected) colors.onPrimaryContainer else colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = copy.hintDescription(mode),
            style = QhTheme.type.callout,
            color = if (selected) colors.onPrimaryContainer else colors.inkSecondary,
        )
        if (example.isNotEmpty()) {
            Text(
                text = example,
                style = exampleStyle,
                color = if (selected) colors.onPrimaryContainer else colors.ink,
                textAlign = TextAlign.Start,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}
