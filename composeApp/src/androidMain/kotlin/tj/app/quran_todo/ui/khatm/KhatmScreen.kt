package tj.app.quran_todo.ui.khatm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.khatm.KhatmDayStatus
import tj.app.quran_todo.khatm.KhatmMath
import tj.app.quran_todo.khatm.KhatmState
import tj.app.quran_todo.khatm.KhatmToday
import tj.app.quran_todo.ui.about.AboutScaffold
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhDialogButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.KhatmCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.rememberKhatmCopy
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.plan.TargetDateDialog
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The reading plan (`Dest.Khatm`, task F5b). No plan: the presets (7, 15, 30, 60 days) and "By a chosen date", from the first page or from the
 * current one. A plan: how far it is, the portion of today with "Read" and "Mark as read", the calendar of its days (read, missed, today), a
 * new end date, "Mark up to a page" for a manual fix and "Delete the plan". Reading here never touches the lessons.
 */
@Composable
fun KhatmScreen(onBack: () -> Unit, onOpenMushaf: (page: Int) -> Unit, openTargetPicker: Boolean = false) {
    val viewModel = koinViewModel<KhatmViewModel>()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val copy = rememberKhatmCopy(language)
    val toaster = LocalToaster.current
    LaunchedEffect(viewModel, copy) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                KhatmEvent.Marked -> toaster.show(copy.markedToast, ToastKind.Success)
                KhatmEvent.MonthPlanCreated -> toaster.show(copy.monthPlanCreatedToast, ToastKind.Success)
                KhatmEvent.Created -> toaster.show(copy.createdToast, ToastKind.Success)
                KhatmEvent.Deleted -> toaster.show(copy.deletedToast, ToastKind.Info)
                KhatmEvent.Failed -> toaster.show(copy.failed, ToastKind.Error)
                KhatmEvent.ListenFailed -> toaster.show(copy.juzUnavailable, ToastKind.Error)
            }
        }
    }
    var datePickerFor by rememberSaveable { mutableStateOf<String?>(if (openTargetPicker) "change" else null) }
    // The page a new plan starts on: the first one, or the page the person is on now.
    var fromPage by rememberSaveable { mutableIntStateOf(1) }

    AboutScaffold(title = copy.screenTitle, backLabel = copy.back, onBack = onBack) { stagger ->
        val state = ui.state
        if (!ui.loaded) return@AboutScaffold
        if (state == null) {
            NewPlan(
                copy = copy,
                currentPage = ui.readPage,
                fromPage = fromPage,
                onFromPage = { fromPage = it },
                onPreset = { days -> viewModel.create(days, null, fromPage) },
                onPickDate = { datePickerFor = "new" },
                modifier = Modifier.staggeredEnter(0, stagger),
            )
        } else {
            PlanOverview(
                state = state,
                today = ui.today,
                copy = copy,
                onRead = onOpenMushaf,
                onMarkRead = viewModel::markPortionRead,
                onNewTarget = { datePickerFor = "change" },
                modifier = Modifier.staggeredEnter(0, stagger),
            )
            Calendar(state = state, today = ui.today, copy = copy, modifier = Modifier.staggeredEnter(1, stagger))
            ManagePlan(
                state = state,
                copy = copy,
                onChangeTarget = { datePickerFor = "change" },
                onMarkUpTo = viewModel::markUpTo,
                onDelete = viewModel::delete,
                modifier = Modifier.staggeredEnter(2, stagger),
            )
        }
    }

    val picker = datePickerFor
    if (picker != null) {
        val plan = planCopy(language)
        val current = ui.state?.plan
        TargetDateDialog(
            initialDay = (current?.targetEpochDay ?: (ui.today + 30)).coerceAtLeast(ui.today + 1),
            today = ui.today,
            copy = plan,
            onPick = { day ->
                if (picker == "new") viewModel.create(null, day, fromPage) else viewModel.changeTarget(day)
            },
            onDismiss = { datePickerFor = null },
        )
    }
}

@Composable
private fun NewPlan(
    copy: KhatmCopy,
    currentPage: Int?,
    fromPage: Int,
    onFromPage: (Int) -> Unit,
    onPreset: (days: Int) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        QhCard {
            Text(copy.newPlanTitle, style = QhTheme.type.title1, color = colors.ink)
            Spacer(Modifier.height(QhSpacing.x1))
            Text(copy.newPlanBody, style = QhTheme.type.callout, color = colors.inkSecondary)
        }
        if (currentPage != null && currentPage > 1) {
            QhSectionHeader(copy.startTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                QhChip(copy.fromFirstPage, selected = fromPage == 1, onClick = { onFromPage(1) })
                QhChip(copy.fromCurrent(currentPage), selected = fromPage == currentPage, onClick = { onFromPage(currentPage) })
            }
        }
        KhatmMath.PRESET_DAYS.forEach { days ->
            QhButton(
                text = copy.preset(days),
                onClick = { onPreset(days) },
                modifier = Modifier.fillMaxWidth(),
                variant = if (days == 30) QhButtonVariant.Primary else QhButtonVariant.Tonal,
                size = QhButtonSize.M,
            )
        }
        QhButton(
            text = copy.toDate,
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
            variant = QhButtonVariant.Ghost,
            size = QhButtonSize.M,
        )
    }
}

@Composable
private fun PlanOverview(
    state: KhatmState,
    today: Int,
    copy: KhatmCopy,
    onRead: (Int) -> Unit,
    onMarkRead: () -> Unit,
    onNewTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val plan = state.plan
    val status = KhatmMath.status(state, today)
    val delta = KhatmMath.deltaPages(state, today)
    val daysToEnd = KhatmMath.daysToEnd(plan, today)
    QhCard(modifier = modifier.fillMaxWidth()) {
        Text(
            copy.summary(plan.totalPages, copy.dateLabel(plan.targetEpochDay, today)),
            style = QhTheme.type.title1,
            color = colors.ink,
        )
        Spacer(Modifier.height(QhSpacing.x3))
        QhLinearProgress(progress = KhatmMath.fraction(state))
        Spacer(Modifier.height(QhSpacing.x2))
        Text(
            copy.progress(KhatmMath.pagesRead(state), plan.totalPages),
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
        )
        if (status == KhatmToday.Complete) {
            Spacer(Modifier.height(QhSpacing.x3))
            Text(copy.completeBanner, style = QhTheme.type.title2, color = colors.learned)
            Text(copy.cardCompleteBody, style = QhTheme.type.callout, color = colors.inkSecondary)
            return@QhCard
        }
        if (status is KhatmToday.Overdue) {
            Spacer(Modifier.height(QhSpacing.x3))
            Text(copy.overdueTitle, style = QhTheme.type.title2, color = colors.due)
            Spacer(Modifier.height(QhSpacing.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), modifier = Modifier.fillMaxWidth()) {
                QhButton(
                    text = copy.readButton,
                    onClick = { onRead(status.nextPage) },
                    modifier = Modifier.weight(1f),
                    size = QhButtonSize.M,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                )
                QhButton(
                    text = copy.newTarget,
                    onClick = onNewTarget,
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Edit,
                )
            }
            return@QhCard
        }
        Spacer(Modifier.height(QhSpacing.x3))
        val pace = buildList {
            add(copy.daily(KhatmMath.dailyPages(state, today)))
            add(
                when {
                    daysToEnd == 0 -> copy.lastDay
                    else -> copy.daysLeft(daysToEnd + 1)
                },
            )
            when {
                delta < 0 -> add(copy.behind(-delta))
                delta > 0 -> add(copy.ahead(delta))
            }
        }
        Text(pace.joinToString(" · "), style = QhTheme.type.callout, color = colors.inkSecondary)
        if (status is KhatmToday.Pending) {
            Spacer(Modifier.height(QhSpacing.x3))
            Text(
                copy.cardPending(status.portion.firstPage, status.portion.lastPage, status.minutes),
                style = QhTheme.type.title2,
                color = colors.ink,
            )
            Spacer(Modifier.height(QhSpacing.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), modifier = Modifier.fillMaxWidth()) {
                QhButton(
                    text = copy.readButton,
                    onClick = { onRead(status.portion.firstPage) },
                    modifier = Modifier.weight(1f),
                    size = QhButtonSize.M,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                )
                QhButton(
                    text = copy.markReadButton,
                    onClick = onMarkRead,
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Check,
                )
            }
        } else if (status is KhatmToday.DoneToday) {
            status.next?.let { next ->
                Spacer(Modifier.height(QhSpacing.x3))
                Text(copy.cardDone(next.firstPage, next.lastPage), style = QhTheme.type.title2, color = colors.ink)
            }
        }
    }
}

/** The days of the plan, seven in a row: read (filled with a check), missed (warm outline), today (outline of the brand), later (calm). */
@Composable
private fun Calendar(state: KhatmState, today: Int, copy: KhatmCopy, modifier: Modifier = Modifier) {
    val days = remember(state, today) { KhatmMath.calendar(state, today) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        QhSectionHeader(copy.calendarTitle)
        QhCard {
            days.chunked(7).forEachIndexed { rowIndex, week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEachIndexed { column, day ->
                        val number = rowIndex * 7 + column + 1
                        val label = when (day.status) {
                            KhatmDayStatus.Read -> copy.legendRead
                            KhatmDayStatus.Missed -> copy.legendMissed
                            KhatmDayStatus.Today -> copy.legendToday
                            KhatmDayStatus.Future -> ""
                        }
                        DayCell(
                            number = number,
                            status = day.status,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "$number ${label}".trim() },
                        )
                    }
                    // A short last week keeps the cells of the full ones their width.
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(QhSpacing.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
                Legend(copy.legendRead, KhatmDayStatus.Read)
                Legend(copy.legendMissed, KhatmDayStatus.Missed)
                Legend(copy.legendToday, KhatmDayStatus.Today)
            }
        }
    }
}

@Composable
private fun DayCell(number: Int, status: KhatmDayStatus, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val fill: Color = when (status) {
        KhatmDayStatus.Read -> colors.learned
        KhatmDayStatus.Missed -> colors.dueContainer
        KhatmDayStatus.Today -> Color.Transparent
        KhatmDayStatus.Future -> colors.surfaceMuted
    }
    val outline: Color = when (status) {
        KhatmDayStatus.Read -> colors.learned
        KhatmDayStatus.Missed -> colors.due
        KhatmDayStatus.Today -> colors.primary
        KhatmDayStatus.Future -> Color.Transparent
    }
    val content = when (status) {
        KhatmDayStatus.Read -> colors.onLearned
        KhatmDayStatus.Missed -> colors.due
        KhatmDayStatus.Today -> colors.primary
        KhatmDayStatus.Future -> colors.inkSecondary
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fill)
                .border(if (status == KhatmDayStatus.Future) 0.dp else 1.5.dp, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = QhTheme.type.label, color = content, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Legend(label: String, status: KhatmDayStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
        Box(Modifier.size(14.dp)) { DayDot(status) }
        Text(label, style = QhTheme.type.label, color = QhTheme.colors.inkSecondary)
    }
}

@Composable
private fun DayDot(status: KhatmDayStatus) {
    val colors = QhTheme.colors
    val (fill, outline) = when (status) {
        KhatmDayStatus.Read -> colors.learned to colors.learned
        KhatmDayStatus.Missed -> colors.dueContainer to colors.due
        KhatmDayStatus.Today -> Color.Transparent to colors.primary
        KhatmDayStatus.Future -> colors.surfaceMuted to Color.Transparent
    }
    Box(Modifier.size(14.dp).clip(CircleShape).background(fill).border(1.5.dp, outline, CircleShape))
}

@Composable
private fun ManagePlan(
    state: KhatmState,
    copy: KhatmCopy,
    onChangeTarget: () -> Unit,
    onMarkUpTo: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    var pageText by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val plan = state.plan
    val page = pageText.toIntOrNull()
    val valid = page != null && page in (plan.startPage - 1)..plan.endPage
    Column(modifier, verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        if (!KhatmMath.isComplete(state)) {
            QhButton(
                text = copy.changeTarget,
                onClick = onChangeTarget,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.M,
                icon = Icons.Rounded.Edit,
            )
        }
        QhCard {
            Text(copy.markUpToTitle, style = QhTheme.type.title2, color = colors.ink)
            Text(copy.markUpToHint, style = QhTheme.type.callout, color = colors.inkSecondary)
            Spacer(Modifier.height(QhSpacing.x3))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { text -> pageText = text.filter { it.isDigit() }.take(3) },
                    modifier = Modifier.weight(1f),
                    label = { Text("${copy.pageLabel} ${plan.startPage}–${plan.endPage}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = QhTheme.shapes.md,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.hairline,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.inkSecondary,
                        cursorColor = colors.primary,
                    ),
                )
                QhButton(
                    text = copy.markUpToAction,
                    onClick = {
                        page?.let(onMarkUpTo)
                        pageText = ""
                    },
                    enabled = valid,
                    size = QhButtonSize.M,
                )
            }
        }
        QhButton(
            text = if (KhatmMath.isComplete(state)) copy.startNew else copy.deletePlan,
            onClick = { if (KhatmMath.isComplete(state)) onDelete() else confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
            variant = if (KhatmMath.isComplete(state)) QhButtonVariant.Primary else QhButtonVariant.Ghost,
            size = QhButtonSize.M,
            icon = if (KhatmMath.isComplete(state)) null else Icons.Rounded.DeleteOutline,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text(copy.deleteTitle, style = QhTheme.type.title1, color = colors.ink) },
            text = { Text(copy.deleteBody, style = QhTheme.type.body, color = colors.inkSecondary) },
            confirmButton = {
                QhDialogButton(text = copy.delete, color = colors.danger, onClick = {
                    confirmDelete = false
                    onDelete()
                })
            },
            dismissButton = {
                QhDialogButton(text = copy.cancel, color = colors.inkSecondary, onClick = { confirmDelete = false })
            },
        )
    }
}
