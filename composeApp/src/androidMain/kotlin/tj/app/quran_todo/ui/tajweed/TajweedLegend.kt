package tj.app.quran_todo.ui.tajweed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.tajweed.TajweedGroup
import tj.app.quran_todo.common.tajweed.TajweedRule
import tj.app.quran_todo.common.tajweed.TajweedSpan
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.i18n.TajweedCopy
import tj.app.quran_todo.ui.i18n.rememberTajweedCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "What the colours mean" (task F3b): the six groups in the order a person learns them, and in each group its rules, every rule with the
 * colour it has now (light or dark theme), its name in the language's spelling of the Arabic term, one line on how it is read and a word
 * from the Quran with the rule coloured in it. It needs no markup file: the examples are written down ([TajweedExamples]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TajweedLegendSheet(onDismiss: () -> Unit) {
    val copy = rememberTajweedCopy()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(top = QhSpacing.x1)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Text(
                text = copy.legendTitle,
                style = QhTheme.type.title1,
                color = QhTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.legendIntro, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
            TajweedGroup.entries.forEach { group -> LegendGroup(group, copy) }
        }
    }
}

@Composable
private fun LegendGroup(group: TajweedGroup, copy: TajweedCopy) {
    val dark = QhTheme.colors.isDark
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            ColorDot(TajweedPalette.groupColor(group, dark), size = 12)
            Text(
                text = copy.group(group),
                style = QhTheme.type.title2,
                color = QhTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        val rules = TajweedRule.entries.filter { it.group == group }
        QhCard(contentPadding = PaddingValues(0.dp)) {
            rules.forEachIndexed { index, rule ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                LegendRule(rule, copy)
            }
        }
    }
}

@Composable
private fun LegendRule(rule: TajweedRule, copy: TajweedCopy) {
    val colors = QhTheme.colors
    val dark = colors.isDark
    val text = copy.rule(rule)
    val example = TajweedExamples.byRule.getValue(rule)
    val family = getQuranFontFamily()
    val type = QhTheme.type
    val exampleStyle = remember(type, family) { type.quranInline(family, 26.sp) }
    val styled = remember(example, rule, dark) {
        tajweedAnnotated(example.text, example.spans.map { TajweedSpan(rule, it.first..it.last) }, dark)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = QhSpacing.minTouch)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = 2.dp), contentAlignment = Alignment.Center) {
            ColorDot(TajweedPalette.color(rule, dark), size = 14)
        }
        Column(Modifier.weight(1f)) {
            Text(text = text.name, style = type.title2, color = colors.ink)
            Text(text = text.how, style = type.callout, color = colors.inkSecondary)
        }
        Text(
            text = styled,
            style = exampleStyle,
            color = colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 150.dp),
        )
    }
}

/** A round swatch of a colour (a circle, never a star). */
@Composable
private fun ColorDot(color: androidx.compose.ui.graphics.Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}
