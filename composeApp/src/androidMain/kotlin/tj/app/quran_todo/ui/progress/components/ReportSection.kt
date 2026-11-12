package tj.app.quran_todo.ui.progress.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import tj.app.quran_todo.ui.components.QhSegmentedControl
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.progress.report.ReportPeriod
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The card at the bottom of the page: "In detail" (the analytics screen: year calendar, by surah and juz, forecast) and the report for
 * the teacher, which opens the choice of the period.
 */
@Composable
internal fun ReportSection(
    copy: ProgressCopy,
    analytics: AnalyticsCopy,
    onOpenDetails: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QhCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        Column {
            QhNavRow(
                title = analytics.detailsTitle,
                description = analytics.detailsBody,
                onClick = onOpenDetails,
                icon = Icons.Rounded.BarChart,
                iconTint = QhTheme.colors.primary,
            )
            QhRowDivider()
            QhNavRow(
                title = copy.reportAction,
                description = copy.reportRowBody,
                onClick = onShare,
                icon = Icons.Rounded.Share,
                iconTint = QhTheme.colors.primary,
            )
        }
    }
}

/** How the report goes out: the card picture (with the text as its caption), a PDF, or the text alone. */
internal enum class ReportFormat { Image, Pdf, Text }

/**
 * What the sheet asks for: the [period] and the [format]. For [ReportPeriod.Custom] the days are [fromDay]..[toDay] (epoch days, both
 * included); for the week and the month they are null and the last days up to today are meant.
 */
internal data class ReportRequest(
    val period: ReportPeriod,
    val format: ReportFormat,
    val fromDay: Int? = null,
    val toDay: Int? = null,
)

/**
 * The choice of the report: the format ("Image" / "PDF" / "Text") and the period: "Past week", "Past month" or "Custom period" (the
 * system date range picker of Material 3, only days up to today). Pressing a period runs [onShare] (which prepares the report and
 * opens the chooser of the system); the pressed button shows a spinner meanwhile, and the sheet slides away when [onShare] reports
 * that the report went out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheet(
    copy: ProgressCopy,
    analytics: AnalyticsCopy,
    today: Int,
    onShare: suspend (ReportRequest) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var formatIndex by rememberSaveable { mutableIntStateOf(ReportFormat.Image.ordinal) }
    var busy by remember { mutableStateOf<ReportPeriod?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }

    fun pick(period: ReportPeriod, from: Int? = null, to: Int? = null) {
        if (busy != null) return
        busy = period
        scope.launch {
            val sent = try {
                onShare(ReportRequest(period, ReportFormat.entries[formatIndex], from, to))
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
        Text(text = analytics.formatTitle, style = QhTheme.type.label, color = colors.inkSecondary)
        Spacer(Modifier.height(QhSpacing.x2))
        QhSegmentedControl(
            options = listOf(analytics.formatImage, analytics.formatPdf, analytics.formatText),
            selectedIndex = formatIndex,
            onSelect = { if (busy == null) formatIndex = it },
        )
        Spacer(Modifier.height(QhSpacing.x4))
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
            QhButton(
                text = analytics.periodCustom,
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.L,
                enabled = busy == null || busy == ReportPeriod.Custom,
                loading = busy == ReportPeriod.Custom,
            )
        }
    }

    if (picking) {
        ReportRangeDialog(
            analytics = analytics,
            today = today,
            onConfirm = { from, to ->
                picking = false
                pick(ReportPeriod.Custom, from, to)
            },
            onDismiss = { picking = false },
        )
    }
}

/** The system date range picker of Material 3 in its dialog: only days up to [today]; the result is epoch days, both ends included. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportRangeDialog(
    analytics: AnalyticsCopy,
    today: Int,
    onConfirm: (fromDay: Int, toDay: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val millisPerDay = 86_400_000L
    val selectable = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(utcTimeMillis, millisPerDay) <= today

            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.fromEpochDays(today).year
        }
    }
    val state = rememberDateRangePickerState(selectableDates = selectable)
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = Math.floorDiv(start!!, millisPerDay).toInt()
                    // One tapped day is a one-day period.
                    val to = Math.floorDiv(end ?: start, millisPerDay).toInt()
                    onConfirm(from, to)
                },
                enabled = start != null,
            ) { Text(analytics.pickRangeConfirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(analytics.pickRangeCancel) } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f, fill = false),
            title = {
                Text(
                    text = analytics.pickRangeTitle,
                    style = QhTheme.type.title2,
                    modifier = Modifier.padding(start = QhSpacing.x6, end = QhSpacing.x4, top = QhSpacing.x4),
                )
            },
        )
    }
}
