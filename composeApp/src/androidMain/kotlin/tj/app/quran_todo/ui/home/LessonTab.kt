package tj.app.quran_todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.home.components.LessonCard
import tj.app.quran_todo.ui.home.components.PlanRow
import tj.app.quran_todo.ui.home.components.QuizRow
import tj.app.quran_todo.ui.home.components.SettingsAction
import tj.app.quran_todo.ui.home.components.listBottomPadding
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.pauseCopy
import tj.app.quran_todo.ui.khatm.KhatmHomeCards
import tj.app.quran_todo.ui.listen.ListenOldSheet
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The "Lesson" tab (UX task U14): "Lesson of the day" with the greeting and the settings gear, the lesson card, the plan row under it
 * (U15), the row "Check", the reading-plan and Ramadan cards, and, when there is no reading plan, the quiet row "Reading plan" at the
 * bottom. Nothing of the library: that is the "Quran" tab.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun LessonTab(
    onOpenSettings: () -> Unit,
    onOpenLesson: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenKhatm: () -> Unit,
    onOpenKhatmPicker: () -> Unit,
    onOpenMushaf: (page: Int) -> Unit,
) {
    val viewModel = koinViewModel<HomeUiViewModel>()
    val language = LocalAppLanguage.current
    val copy = remember(language) { homeCopy(language) }
    val toaster = LocalToaster.current
    val colors = QhTheme.colors
    val density = LocalDensity.current

    LaunchedEffect(language) { viewModel.setLanguage(language) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(viewModel, copy) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.StatusUpdateFailed -> toaster.show(copy.statusUpdateFailed, ToastKind.Error)
                HomeEvent.ResumeFailed -> toaster.show(pauseCopy(copy.language).failed, ToastKind.Error)
            }
        }
    }

    // Frozen while another screen is on top or a transition is running, so the card does not change under the shared element
    // animation; fresh as soon as the screen is resumed again.
    val ui by viewModel.ui.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val player by viewModel.player.collectAsStateWithLifecycle()
    // "Listen to the old" while its queue plays: the card says where it is.
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    // The sheet "Listen to the old" (task F1b); the queue itself lives in the playback controller, not in this sheet.
    var listenOpen by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val topBarState = rememberQhTopBarState(scrollState)
    val stagger = rememberStaggerState(ready = ui.phase == HomePhase.Content && ui.lesson != null)

    // The bar floats over the content: reserve its expanded height (the estimate is exact for a one-line title).
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val topPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    // The tab bar owns the navigation-bar inset; the floating mini player (of the scaffold) needs room under the last row.
    val bottomPadding = listBottomPadding(0.dp, playerVisible = player != null, fontScale = density.fontScale)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = topPadding, bottom = bottomPadding),
        ) {
            val card = ui.lesson
            when {
                ui.phase == HomePhase.Error -> EmptyState(
                    title = copy.loadErrorTitle,
                    message = copy.loadErrorBody,
                    actionLabel = copy.retry,
                    onAction = viewModel::retry,
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )

                card == null -> Skeleton(
                    Modifier
                        .padding(horizontal = QhSpacing.screen)
                        .fillMaxWidth()
                        .height(196.dp),
                    QhTheme.shapes.lg,
                )

                else -> {
                    LessonCard(
                        card = card,
                        copy = copy,
                        onOpenLesson = onOpenLesson,
                        onOpenPlan = onOpenPlan,
                        modifier = Modifier
                            .padding(horizontal = QhSpacing.screen)
                            .staggeredEnter(0, stagger),
                        onListenOld = { listenOpen = true },
                        listening = listening,
                        onResumePlan = viewModel::resumePlan,
                    )
                    // "Juz Amma · covered 10 of 564 · finish ~2 March · Change": the visible way to "My plan" (U15).
                    ui.planRow?.let { row ->
                        PlanRow(
                            row = row,
                            onClick = onOpenPlan,
                            modifier = Modifier
                                .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x2)
                                .staggeredEnter(1, stagger),
                        )
                    }
                    // "Check · 5 questions on what you know": a row of its own under the lesson, also while the plan is paused.
                    if (ui.quizReady) {
                        QuizRow(
                            onClick = onOpenQuiz,
                            modifier = Modifier
                                .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x2)
                                .staggeredEnter(2, stagger),
                        )
                    }
                    // The reading plan (khatm) and Ramadan (task F5b): each card shows only when there is something to say; with no
                    // reading plan the last thing on the tab is the quiet row "Reading plan".
                    KhatmHomeCards(
                        onOpenKhatm = onOpenKhatm,
                        onOpenKhatmPicker = onOpenKhatmPicker,
                        onOpenMushaf = onOpenMushaf,
                        modifier = Modifier
                            .padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x2)
                            .staggeredEnter(3, stagger),
                        showReadingPlanEntry = true,
                    )
                }
            }
        }

        QhLargeTopBar(
            title = copy.lessonTitle,
            overline = copy.greeting,
            state = topBarState,
            actions = {
                SettingsAction(label = copy.settings, onClick = onOpenSettings)
            },
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (scrollState.value == 0) expandedBarPx = size.height
            },
        )
    }

    if (listenOpen) ListenOldSheet(onDismiss = { listenOpen = false })
}
