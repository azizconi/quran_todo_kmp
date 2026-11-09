package tj.app.quran_todo.ui.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.spotlight.SpotlightShape
import tj.app.quran_todo.ui.components.spotlight.SpotlightTargets
import tj.app.quran_todo.ui.components.spotlight.SpotlightTourEffect
import tj.app.quran_todo.ui.components.spotlight.SpotlightTours
import tj.app.quran_todo.ui.components.spotlight.spotlightTarget
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.hifzCopy
import tj.app.quran_todo.ui.i18n.lessonCopy
import tj.app.quran_todo.ui.i18n.rememberPauseCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.store.InAppReview
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Today's lesson (docs/redesign/PRODUCT_PLAN.md, 3.3): the header with the three steps, then the card the person
 * works on (the new passage in three phases, then the repetitions one by one) and the summary.
 *
 * - [onBack] leaves the lesson; with ayahs opened in "Recall" it asks first ("What you did is saved").
 * - [onOpenPlan] is the action of the empty state ("Make a plan"), [onHome] the button of the summary, [onOpenQuiz] its button
 *   "Check yourself" (the quiz, task F2b).
 * - On a day inside a pause of the plan the screen says "Plan paused until ..." instead of "All done today".
 *
 * The root carries the shared bounds [SharedKeys.LessonCard] of the "Today's lesson" card of Home.
 */
@OptIn(KoinExperimentalAPI::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LessonScreen(
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenQuiz: () -> Unit,
    onHome: () -> Unit,
    onOpenDrill: () -> Unit,
) {
    val viewModel = koinViewModel<LessonViewModel>()
    val ticker = koinInject<EpochDayTicker>()
    val hifz = koinInject<HifzRepository>()
    val pauseCopy = rememberPauseCopy(LocalAppLanguage.current)
    // A pause of today: the lesson is empty then, and the screen says why (the view model only knows "nothing today").
    val pause by remember(hifz, ticker) { hifz.pause(ticker.day) }.collectAsState(initial = null)
    val today by ticker.day.collectAsState()
    val pausedNow = pause?.takeIf { today in it }
    val language = LocalAppLanguage.current
    val copy = remember(language) { lessonCopy(language) }
    val toaster = LocalToaster.current
    val ui by viewModel.ui.collectAsState()

    // The position inside the card survives a process death as text.
    var saved by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.restore(saved)
        viewModel.progressText.collect { saved = it }
    }
    LaunchedEffect(language, viewModel) { viewModel.onLanguage(language) }
    // The timer that switches the day at midnight may have been late while the device slept.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ticker.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                LessonEvent.SaveFailed -> toaster.show(copy.saveFailed, ToastKind.Error)
            }
        }
    }

    val working = ui as? LessonUi.Working
    KeepScreenOnEffect(enabled = working != null)

    // A finished lesson is the happy moment for the Google Play review card (InAppReview decides whether it is due). The short wait
    // lets the summary and its celebration play first.
    val context = LocalContext.current
    val finished = ui is LessonUi.Summary
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        delay(ReviewPromptDelayMillis)
        InAppReview.onLessonFinished(context)
    }

    var showExit by rememberSaveable { mutableStateOf(false) }
    val opened = working?.progress?.let { progress ->
        progress.phase == CardPhase.Recall && progress.recall?.let { it.index > 0 || it.revealed } == true
    } == true
    BackHandler(enabled = opened) { showExit = true }
    val onBackPressed = { if (opened) showExit = true else onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .sharedBoundsKey(SharedKeys.LessonCard)
            .background(QhTheme.colors.bg),
    ) {
        val emptyAndPaused = pausedNow != null && (ui is LessonUi.NoPlan || ui is LessonUi.NothingToday)
        if (emptyAndPaused && pausedNow != null) {
            LessonEmpty(
                copy = copy,
                onBack = onBack,
                title = pauseCopy.cardTitle(formatDay(pausedNow.untilEpochDay, language)),
                message = pauseCopy.lessonBody(formatDay(pausedNow.resumeEpochDay, language)),
                actionLabel = copy.toHome,
                onAction = onHome,
            )
        } else when (val state = ui) {
            LessonUi.Loading -> LessonLoading(copy = copy, onBack = onBack)
            LessonUi.NoPlan -> LessonEmpty(
                copy = copy,
                onBack = onBack,
                title = copy.noPlanTitle,
                actionLabel = copy.noPlanAction,
                onAction = onOpenPlan,
            )
            is LessonUi.NothingToday -> LessonEmpty(
                copy = copy,
                onBack = onBack,
                title = if (state.plan.planFinished) copy.planDoneTitle else copy.nothingTitle,
                message = if (state.plan.planFinished) copy.planDoneBody else copy.nothingBody,
                actionLabel = copy.toHome,
                onAction = onHome,
            )
            is LessonUi.Working -> LessonBody(
                viewModel = viewModel,
                copy = copy,
                ui = state,
                onBack = onBackPressed,
            )
            is LessonUi.Summary -> LessonBody(
                viewModel = viewModel,
                copy = copy,
                ui = state,
                onBack = onBackPressed,
                onHome = onHome,
                onOpenDrill = onOpenDrill,
                onOpenQuiz = onOpenQuiz,
            )
        }

        if (showExit) {
            ExitSheet(copy = copy, onStay = { showExit = false }, onLeave = {
                showExit = false
                onBack()
            })
        }
    }
}

/** The header (top bar, steps, "catching up") and the card or summary under it, sliding from one card to the next. */
@Composable
private fun LessonBody(
    viewModel: LessonViewModel,
    copy: LessonCopy,
    ui: LessonUi,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onOpenDrill: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
) {
    val plan = when (ui) {
        is LessonUi.Working -> ui.plan
        is LessonUi.Summary -> ui.plan
        else -> return
    }
    val steps = remember(plan) { LessonCards.steps(plan) }
    val current = (ui as? LessonUi.Working)?.card?.block
    val reduced = LocalReducedMotion.current
    // The tip "Lesson steps" (C2): the first time a lesson is worked on.
    SpotlightTourEffect(SpotlightTours.LessonHeader, ready = ui is LessonUi.Working && steps.isNotEmpty())
    Column(Modifier.fillMaxSize()) {
        LessonTopBar(
            copy = copy,
            subtitle = if (ui is LessonUi.Working && plan.remainingMinutes > 0) copy.minutesLeft(plan.remainingMinutes) else null,
            onBack = onBack,
        )
        LessonSteps(steps = steps, current = current, copy = copy)
        if (plan.catchingUp) CatchingUpNote(copy)
        AnimatedContent(
            targetState = ui,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentKey = { state ->
                when (state) {
                    is LessonUi.Working -> state.card.key
                    else -> "summary"
                }
            },
            transitionSpec = {
                val forward = true
                if (reduced) {
                    fadeIn(tween(QhMotion.Short)) togetherWith fadeOut(tween(QhMotion.Instant))
                } else {
                    val width = { full: Int -> if (forward) full / 3 else -full / 3 }
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(tween(QhMotion.Long, easing = QhMotion.EmphasizedDecelerate)) { width(it) } +
                            fadeIn(tween(QhMotion.Medium, delayMillis = 60)),
                        initialContentExit = slideOutHorizontally(tween(QhMotion.Medium, easing = QhMotion.EmphasizedAccelerate)) { -width(it) } +
                            fadeOut(tween(QhMotion.Short)),
                    )
                }
            },
            label = "lesson-card",
        ) { state ->
            when (state) {
                is LessonUi.Working -> when (state.card) {
                    is LessonCard.New -> NewPassageCard(viewModel = viewModel, copy = copy, work = state)
                    is LessonCard.Review -> ReviewCard(viewModel = viewModel, copy = copy, work = state)
                }
                is LessonUi.Summary -> LessonSummary(
                    viewModel = viewModel,
                    copy = copy,
                    summary = state,
                    onHome = onHome,
                    onOpenDrill = onOpenDrill,
                    onOpenQuiz = onOpenQuiz,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun LessonTopBar(copy: LessonCopy, subtitle: String?, onBack: () -> Unit) {
    val colors = QhTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = QhSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = copy.back,
            onClick = onBack,
            filled = false,
        )
        Column(Modifier.weight(1f).padding(start = QhSpacing.x1)) {
            Text(
                text = copy.title,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (subtitle != null) {
            Text(text = subtitle, style = QhTheme.type.callout, color = colors.inkSecondary)
        }
    }
}

/** Three steps with their progress: a check once a step is done, "2 of 5" before. */
@Composable
private fun LessonSteps(steps: List<LessonStep>, current: LessonBlock?, copy: LessonCopy) {
    if (steps.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x1, bottom = QhSpacing.x3)
            .spotlightTarget(SpotlightTargets.LessonSteps, SpotlightShape.Rounded(14.dp), inset = (-4).dp),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        steps.forEach { step ->
            StepProgress(step = step, selected = step.block == current, copy = copy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepProgress(step: LessonStep, selected: Boolean, copy: LessonCopy, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    val name = when (step.block) {
        LessonBlock.New -> copy.stepNew
        LessonBlock.Recent -> copy.stepRecent
        LessonBlock.Old -> copy.stepOld
    }
    val description = copy.stepDescription(name, step.done, step.total)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // The name and, under it, the count (a check once done): three columns stay readable at large text sizes.
        Text(
            text = name,
            style = QhTheme.type.label,
            color = if (selected || step.complete) colors.ink else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (step.complete) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.learned,
                modifier = Modifier.height(16.dp),
            )
        } else {
            Text(
                text = copy.stepProgress(step.done, step.total),
                style = QhTheme.type.label,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        QhLinearProgress(
            progress = if (step.total == 0) 0f else step.done.toFloat() / step.total,
            modifier = Modifier.padding(top = 4.dp),
            height = 4.dp,
            color = if (step.complete) colors.learned else if (selected) colors.primary else colors.inkTertiary,
            trackColor = colors.surfaceMuted,
        )
    }
}

/** The quiet note under the steps while repetitions are moved to later days. */
@Composable
private fun CatchingUpNote(copy: LessonCopy) {
    val colors = QhTheme.colors
    Text(
        text = copy.catchingUp,
        style = QhTheme.type.callout,
        color = colors.inkSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(bottom = QhSpacing.x2)
            .clip(QhTheme.shapes.sm)
            .background(colors.surfaceMuted)
            .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2),
    )
}

@Composable
private fun LessonLoading(copy: LessonCopy, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LessonTopBar(copy = copy, subtitle = null, onBack = onBack)
        Column(
            Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Skeleton(Modifier.fillMaxWidth().height(24.dp))
            Skeleton(Modifier.fillMaxWidth().height(160.dp), QhTheme.shapes.lg)
            Skeleton(Modifier.fillMaxWidth(0.6f).height(20.dp))
        }
    }
}

@Composable
private fun LessonEmpty(
    copy: LessonCopy,
    onBack: () -> Unit,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    message: String? = null,
) {
    Column(Modifier.fillMaxSize()) {
        LessonTopBar(copy = copy, subtitle = null, onBack = onBack)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            EmptyState(title = title, message = message, actionLabel = actionLabel, onAction = onAction)
        }
    }
}

/** "Leave the lesson? What you did is saved." */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExitSheet(copy: LessonCopy, onStay: () -> Unit, onLeave: () -> Unit) {
    val colors = QhTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(after: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { after() }
    }
    QhSheet(onDismiss = onStay, sheetState = sheetState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = copy.exitTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.exitBody, style = QhTheme.type.body, color = colors.inkSecondary)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QhButton(text = copy.stay, onClick = { hideThen(onStay) }, modifier = Modifier.fillMaxWidth())
                QhButton(
                    text = copy.leave,
                    onClick = { hideThen(onLeave) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
            }
        }
    }
}

/** The recall copy is shared with the reader; the lesson only picks it by language. */
@Composable
internal fun rememberHifzCopy(): HifzCopy {
    val language = LocalAppLanguage.current
    return remember(language) { hifzCopy(language) }
}

private const val ReviewPromptDelayMillis = 2_500L
