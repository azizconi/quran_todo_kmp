package tj.app.quran_todo.ui.khatm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.Font
import org.koin.compose.viewmodel.koinViewModel
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.aref_ruqaa
import tj.app.quran_todo.khatm.KhatmMath
import tj.app.quran_todo.khatm.KhatmState
import tj.app.quran_todo.khatm.KhatmToday
import tj.app.quran_todo.khatm.RamadanCalendar
import tj.app.quran_todo.khatm.RamadanStatus
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.i18n.KhatmCopy
import tj.app.quran_todo.ui.i18n.rememberKhatmCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The cards of the reading plan and of Ramadan under the row "Check" of the home screen (task F5b): the khatm card when there is a plan, the
 * Ramadan card within a week of Ramadan and during it. [onOpenKhatm] opens the plan screen; [onOpenMushaf] opens the pages of the mushaf at
 * a page (a khatm is read in pages, whatever mode the reader is in).
 */
@Composable
internal fun KhatmHomeCards(
    onOpenKhatm: () -> Unit,
    onOpenKhatmPicker: () -> Unit,
    onOpenMushaf: (page: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<KhatmViewModel>()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val copy = rememberKhatmCopy()
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
    if (!ui.loaded) return
    val state = ui.state
    if (state != null) {
        KhatmCard(
            state = state,
            today = ui.today,
            readPage = ui.readPage,
            copy = copy,
            onOpen = onOpenKhatm,
            onNewTarget = onOpenKhatmPicker,
            onRead = onOpenMushaf,
            onMarkRead = viewModel::markPortionRead,
            modifier = modifier,
        )
    }
    if (ui.ramadan != RamadanStatus.Outside) {
        RamadanCard(
            status = ui.ramadan,
            today = ui.today,
            hasPlan = state != null,
            copy = copy,
            onCreatePlan = viewModel::createMonthPlan,
            onListen = viewModel::listenJuz,
            onRead = onOpenMushaf,
            modifier = modifier,
        )
    }
}

/** "Khatm: today pages 121–140 · ~25 min", the bar of the whole plan, "Read" and "Mark as read". */
@Composable
private fun KhatmCard(
    state: KhatmState,
    today: Int,
    readPage: Int?,
    copy: KhatmCopy,
    onOpen: () -> Unit,
    onNewTarget: () -> Unit,
    onRead: (Int) -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val status = KhatmMath.status(state, today)
    val headline = when (status) {
        is KhatmToday.Pending -> copy.cardPending(status.portion.firstPage, status.portion.lastPage, status.minutes)
        is KhatmToday.Overdue -> copy.overdueTitle
        is KhatmToday.DoneToday -> status.next?.let { copy.cardDone(it.firstPage, it.lastPage) } ?: copy.cardCompleteTitle
        KhatmToday.Complete -> copy.cardCompleteTitle
    }
    val overdue = status is KhatmToday.Overdue
    // Overdue: no "behind" count, the plan is simply past its end day.
    val delta = if (overdue) 0 else KhatmMath.deltaPages(state, today)
    val progress = buildString {
        append(
            if (status == KhatmToday.Complete) copy.cardCompleteBody
            else copy.progress(KhatmMath.pagesRead(state), state.plan.totalPages),
        )
        if (status != KhatmToday.Complete) {
            when {
                delta < 0 -> append(" · ").append(copy.behind(-delta))
                delta > 0 -> append(" · ").append(copy.ahead(delta))
            }
        }
    }
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { contentDescription = "$headline. $progress" },
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = QhSpacing.card, vertical = QhSpacing.x3),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            QhIconTile(Icons.AutoMirrored.Rounded.MenuBook, tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(headline, style = QhTheme.type.title2, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(progress, style = QhTheme.type.callout, color = colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(QhSpacing.x3))
        QhLinearProgress(progress = KhatmMath.fraction(state))
        if (status is KhatmToday.Overdue) {
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
        }
        if (status is KhatmToday.Pending) {
            // The person read on to the end of today's portion in the pages: ask softly, never decide.
            if (KhatmMath.portionReachedBy(state, today, readPage)) {
                Spacer(Modifier.height(QhSpacing.x3))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                    Text(copy.reachedPrompt, style = QhTheme.type.callout, color = colors.ink, modifier = Modifier.weight(1f))
                    QhButton(copy.reachedConfirm, onClick = onMarkRead, size = QhButtonSize.S, icon = Icons.Rounded.Check)
                }
            }
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
        }
    }
}

/**
 * Ramadan (task F5b): the calligraphy "رمضان كريم", the night, "A khatm plan for the month" (when there is no plan yet), and during the month
 * the juz of the night with "Listen to the juz" and "Read". Calm geometry only, no stars.
 */
@Composable
private fun RamadanCard(
    status: RamadanStatus,
    today: Int,
    hasPlan: Boolean,
    copy: KhatmCopy,
    onCreatePlan: () -> Unit,
    onListen: (Int) -> Unit,
    onRead: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val calligraphy = Font(Res.font.aref_ruqaa, weight = FontWeight.Normal, style = FontStyle.Normal)
    val calligraphyStyle = remember(calligraphy, colors.gold) {
        TextStyle(fontFamily = FontFamily(calligraphy), fontSize = 34.sp, lineHeight = 52.sp, color = colors.gold)
    }
    val line = when (status) {
        is RamadanStatus.Upcoming -> copy.ramadanUpcoming(status.daysUntil)
        is RamadanStatus.Active -> copy.ramadanNight(status.dayOfMonth, status.daysLeft(today))
        RamadanStatus.Outside -> return
    }
    QhCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { contentDescription = "${copy.ramadanTitle}. $line" },
        style = QhCardStyle.Hero,
        contentPadding = PaddingValues(QhSpacing.cardLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(copy.ramadanTitle.uppercase(), style = QhTheme.type.overline, color = colors.primary)
                Spacer(Modifier.height(QhSpacing.x1))
                Text(line, style = QhTheme.type.title2, color = colors.ink)
            }
            // The Arabic is always right to left, whatever the language of the app is.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = "رمضان كريم",
                    style = calligraphyStyle,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.padding(start = QhSpacing.x3).semantics { contentDescription = "" },
                )
            }
        }
        if (status is RamadanStatus.Active) {
            Spacer(Modifier.height(QhSpacing.x3))
            Text(copy.juzTitle(status.juz), style = QhTheme.type.title1, color = colors.ink)
            Spacer(Modifier.height(QhSpacing.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2), modifier = Modifier.fillMaxWidth()) {
                QhButton(
                    text = copy.listenJuz,
                    onClick = { onListen(status.juz) },
                    modifier = Modifier.weight(1f),
                    size = QhButtonSize.M,
                    icon = Icons.Rounded.Headphones,
                )
                QhButton(
                    text = copy.readJuz,
                    onClick = { onRead(RamadanCalendar.firstPageOfJuz(status.juz)) },
                    modifier = Modifier.weight(1f),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                )
            }
        }
        if (!hasPlan) {
            Spacer(Modifier.height(QhSpacing.x3))
            QhButton(
                text = copy.monthPlan,
                onClick = onCreatePlan,
                modifier = Modifier.fillMaxWidth(),
                variant = if (status is RamadanStatus.Active) QhButtonVariant.Ghost else QhButtonVariant.Primary,
                size = QhButtonSize.M,
            )
            Text(
                copy.monthPlanHint,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = QhSpacing.x1, start = QhSpacing.x1),
            )
        }
    }
}
