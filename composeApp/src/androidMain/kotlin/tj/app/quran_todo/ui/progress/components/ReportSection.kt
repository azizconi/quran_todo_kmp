package tj.app.quran_todo.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The card at the bottom of the screen that explains the report for the teacher and opens the choice of the period. */
@Composable
internal fun ReportSection(
    copy: ProgressCopy,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    QhCard(modifier.fillMaxWidth()) {
        Text(
            text = copy.reportAction,
            style = QhTheme.type.title1,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.reportCardBody, style = QhTheme.type.callout, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x4))
        QhButton(
            text = copy.reportShare,
            onClick = onShare,
            size = QhButtonSize.M,
            icon = Icons.Rounded.Share,
        )
    }
}

/**
 * The choice of the period: "Past week" / "Past month", and a switch for the picture (on: the card and the text go in one
 * message; off: only the text). Pressing a period runs [onShare] (which prepares the report and opens the chooser of the system);
 * the pressed button shows a spinner meanwhile, and the sheet slides away when [onShare] reports that the report went out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheet(
    copy: ProgressCopy,
    onShare: suspend (period: ReportPeriod, withImage: Boolean) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var withImage by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf<ReportPeriod?>(null) }

    fun pick(period: ReportPeriod) {
        if (busy != null) return
        busy = period
        scope.launch {
            val sent = try {
                onShare(period, withImage)
            } finally {
                busy = null
            }
            if (sent) {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        val colors = QhTheme.colors
        Text(
            text = copy.reportAction,
            style = QhTheme.type.title1,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(QhSpacing.x1))
        Text(text = copy.sheetBody, style = QhTheme.type.body, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x3))
        QhSwitchRow(
            title = copy.withImage,
            description = copy.withImageHint,
            checked = withImage,
            onCheckedChange = { withImage = it },
            icon = Icons.Rounded.Image,
            enabled = busy == null,
        )
        Spacer(Modifier.height(QhSpacing.x3))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            QhButton(
                text = copy.periodWeek,
                onClick = { pick(ReportPeriod.Week) },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Primary,
                size = QhButtonSize.L,
                enabled = busy == null || busy == ReportPeriod.Week,
                loading = busy == ReportPeriod.Week,
            )
            QhButton(
                text = copy.periodMonth,
                onClick = { pick(ReportPeriod.Month) },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.L,
                enabled = busy == null || busy == ReportPeriod.Month,
                loading = busy == ReportPeriod.Month,
            )
        }
    }
}
