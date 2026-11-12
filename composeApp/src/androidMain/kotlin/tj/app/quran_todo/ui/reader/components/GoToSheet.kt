package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.home.SearchItem
import tj.app.quran_todo.ui.home.SurahSearch
import tj.app.quran_todo.ui.i18n.GoToCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.reader.GoToRules
import tj.app.quran_todo.ui.reader.GoToTarget
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** One surah of the list of the sheet "Go to": its display name, other names people type, and the Arabic name. */
class GoToSurah(
    val number: Int,
    val name: String,
    val alternatives: List<String> = emptyList(),
    val arabic: String = "",
)

/**
 * The sheet "Go to" (U21): the surah (the current one chosen; a tap opens a searchable list of the 114) and the ayah (a number limited to the
 * ayah count of that surah, 1 at first; "2:255" is read as a surah and an ayah). The button reports the place through [onGoTo] and closes the
 * sheet; what to do with it (scroll, open another surah, turn the page) is the caller's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoToSheet(
    surahs: List<GoToSurah>,
    currentSurah: Int,
    copy: GoToCopy,
    onGoTo: (GoToTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(sheetState, onDismiss)
    var surah by rememberSaveable { mutableIntStateOf(currentSurah) }
    var ayahText by rememberSaveable { mutableStateOf("1") }
    var picking by rememberSaveable { mutableStateOf(false) }

    val target = GoToRules.resolve(surah, ayahText)
    // A typed "2:255" names its own surah: the field above shows it.
    val shownSurah = target?.surah ?: surah
    val byNumber = remember(surahs) { surahs.associateBy { it.number } }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.imePadding()) {
            if (picking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QhIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = copy.title,
                        onClick = { picking = false },
                        filled = false,
                        modifier = Modifier.padding(start = 0.dp),
                    )
                    SheetTitle(copy.surah)
                }
                SurahPicker(
                    surahs = surahs,
                    selected = shownSurah,
                    copy = copy,
                    onPick = { number ->
                        surah = number
                        // A reference typed before no longer matters: the surah was chosen by hand.
                        ayahText = GoToRules.onSurahChanged(number, ayahText.substringAfter(':', ayahText))
                        picking = false
                    },
                )
            } else {
                SheetTitle(copy.title)
                Spacer(Modifier.height(QhSpacing.x3))
                Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.Bottom) {
                    FieldColumn(label = copy.surah, modifier = Modifier.weight(1.7f)) {
                        SurahField(
                            name = byNumber[shownSurah]?.name ?: "№ $shownSurah",
                            description = copy.chooseSurah,
                            onClick = { picking = true },
                        )
                    }
                    FieldColumn(label = copy.ayah, modifier = Modifier.weight(1f)) {
                        AyahField(
                            text = ayahText,
                            onChange = { ayahText = GoToRules.sanitize(surah, it) },
                            onDone = { target?.let { onGoTo(it); close() } },
                        )
                    }
                }
                Text(
                    text = copy.range(GoToRules.limit(shownSurah)),
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                    modifier = Modifier.padding(top = QhSpacing.x2),
                )
                Spacer(Modifier.height(QhSpacing.x4))
                QhButton(
                    text = copy.go,
                    onClick = { target?.let { onGoTo(it); close() } },
                    enabled = target != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FieldColumn(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
        Text(label, style = QhTheme.type.label, color = QhTheme.colors.inkSecondary)
        content()
    }
}

/** The chosen surah as a field with a small arrow: a tap opens the list. */
@Composable
private fun SurahField(name: String, description: String, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    Row(
        Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .pressable(onClick = onClick, shape = shape, onClickLabel = description)
            .clip(shape)
            .background(colors.surfaceMuted)
            .border(1.dp, colors.hairline, shape)
            .padding(start = QhSpacing.x4, end = QhSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = QhTheme.type.body,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AyahField(text: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    BasicTextField(
        value = text,
        onValueChange = onChange,
        singleLine = true,
        textStyle = QhTheme.type.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .clip(shape)
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.hairline, shape)
                    .padding(horizontal = QhSpacing.x4),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

/** The searchable list of the 114 surahs: the fuzzy search of Home (name in any script, or the number). */
@Composable
private fun SurahPicker(surahs: List<GoToSurah>, selected: Int, copy: GoToCopy, onPick: (Int) -> Unit) {
    val colors = QhTheme.colors
    var query by remember { mutableStateOf("") }
    val index = remember(surahs) {
        SurahSearch.Index.build(
            surahs.map { SearchItem(number = it.number, names = listOf(it.name) + it.alternatives, arabic = listOfNotNull(it.arabic.takeIf(String::isNotBlank))) },
        )
    }
    val byNumber = remember(surahs) { surahs.associateBy { it.number } }
    val found = remember(index, query) { index.search(query) }
    Spacer(Modifier.height(QhSpacing.x2))
    QhSearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = copy.searchHint,
        clearContentDescription = copy.clearSearch,
    )
    Spacer(Modifier.height(QhSpacing.x2))
    if (found.isEmpty()) {
        Text(copy.nothingFound, style = QhTheme.type.body, color = colors.inkSecondary, modifier = Modifier.padding(vertical = QhSpacing.x4))
        return
    }
    // The list has its own scroll: a drag at its end must not pass on to the sheet and dismiss it.
    LazyColumn(Modifier.heightIn(max = 360.dp).nestedScroll(ConsumeLeftoverScroll)) {
        items(found, key = { it }) { number ->
            val item = byNumber[number] ?: return@items
            val chosen = number == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = QhSpacing.minTouch)
                    .pressable(onClick = { onPick(number) }, shape = QhTheme.shapes.md, role = Role.RadioButton)
                    .semantics { this.selected = chosen }
                    .padding(horizontal = QhSpacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = number.toString(),
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    text = item.name,
                    style = QhTheme.type.body,
                    color = if (chosen) colors.primary else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val FieldHeight = 52.dp
