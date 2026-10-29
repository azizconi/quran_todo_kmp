package tj.app.quran_todo.ui.onboarding

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.LocalThemePaletteSetter
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.ui.components.DotSpinner
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.softShadow
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.rememberOnboardingCopy
import tj.app.quran_todo.ui.i18n.rememberReminderCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTrustCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.rememberThemeSwitcher
import tj.app.quran_todo.ui.reminder.ReminderPermission
import tj.app.quran_todo.ui.reminder.formatReminderTime
import tj.app.quran_todo.ui.settings.MaxReadingFontSize
import tj.app.quran_todo.ui.settings.MinReadingFontSize
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The onboarding v2 (docs/redesign/tasks/PC1-onboarding-v2.md): one screen, one question at a time, in a shared-axis transition.
 * "Back" (the arrow and the system gesture) returns to the previous step, on the first step the system gesture leaves the app. The
 * language, the font, the size, the theme and the palette apply at once through the root's setters; the reminder is turned on at
 * its step; the memorization progress is written only by the button of the last screen (see [OnboardingViewModel.finish]).
 */
@Composable
internal fun OnboardingScreen(onFinished: (OnboardingExit) -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current

    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val fontStyle = LocalReadingFontStyle.current
    val setFontStyle = LocalReadingFontStyleSetter.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current
    val themeMode = LocalThemeMode.current
    val palette = LocalThemePalette.current
    val setPalette = LocalThemePaletteSetter.current
    // Theme and palette changes ripple out from the finger (circular reveal), as in the settings.
    val themeSwitcher = rememberThemeSwitcher()
    val followsSystem by viewModel.themeFollowsSystem.collectAsStateWithLifecycle()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsStateWithLifecycle()

    val copy = rememberOnboardingCopy(language)
    val settingsCopy = rememberSettingsCopy(language)
    val trustCopy = rememberTrustCopy(language)
    val reminderCopy = rememberReminderCopy(language)
    val planCopy = remember(language) { planCopy(language) }
    LaunchedEffect(language) { viewModel.setLanguage(language) }

    val position = ui.position
    val step = position.step
    val memorize = ui.goal != GoalChoice.Read
    val fontSize = settings.readingFontSize.coerceIn(MinReadingFontSize, MaxReadingFontSize)
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }

    BackHandler(enabled = position != FlowPosition.First) { viewModel.back() }

    // "Remind me" asks for the notification permission here, at the step where the reminder is explained.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.applyReminder(allowed = granted && !ReminderPermission.isBlocked(context))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is OnboardingEvent.Finished -> onFinished(event.exit)
                OnboardingEvent.FinishFailed -> toaster.show(copy.finishFailed, ToastKind.Error)
            }
        }
    }

    /** What is saved when a step is left: even a kept default counts as a choice (the old install check reads these). */
    fun leave() {
        when (step) {
            OnboardingStep.Language -> setLanguage(language)
            OnboardingStep.Comfort -> {
                setFontStyle(fontStyle)
                setSettings(settings.copy(readingFontSize = fontSize))
                viewModel.setThemeFollowsSystem(followsSystem)
                setPalette(palette)
            }
            else -> Unit
        }
    }

    fun next() {
        leave()
        viewModel.next()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            OnboardingTopRow(
                showBack = position != FlowPosition.First,
                segmentCount = ui.segmentCount,
                segmentIndex = ui.segmentIndex,
                currentFill = if (step == OnboardingStep.Story) (position.storyPage + 1f) / StoryPageCount else 1f,
                progressLabel = copy.stepLabel(ui.segmentIndex + 1, ui.segmentCount),
                backLabel = settingsCopy.native.back,
                onBack = { viewModel.back() },
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(ReducedStepMillis)) togetherWith fadeOut(tween(ReducedStepMillis))
                    } else {
                        // Shared axis X: the old step leaves toward the direction of travel, the new one arrives from it.
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            fadeIn(tween(260, delayMillis = 60)) +
                                slideInHorizontally(tween(StepMoveMillis, easing = QhMotion.EmphasizedDecelerate)) {
                                    (it * StepSlideFraction).toInt() * direction
                                }
                            ) togetherWith (
                            fadeOut(tween(140)) +
                                slideOutHorizontally(tween(StepMoveMillis, easing = QhMotion.Emphasized)) {
                                    -(it * StepSlideFraction).toInt() * direction
                                }
                            )
                    }
                },
                label = "onboarding-step",
            ) { shown ->
                when (shown) {
                    OnboardingStep.Language -> LanguageStep(copy = copy, language = language, onSelectLanguage = setLanguage)

                    OnboardingStep.Story -> StoryStep(
                        copy = copy,
                        page = position.storyPage,
                        fatiha = ui.fatiha,
                        fatihaName = ui.surahName(1) ?: copy.fatihaName,
                        fontStyle = fontStyle,
                        onPage = viewModel::showStoryPage,
                    )

                    OnboardingStep.Goal -> GoalStep(copy = copy, goal = ui.goal, onChoose = viewModel::chooseGoal)

                    OnboardingStep.Known -> KnownStep(
                        copy = copy,
                        planCopy = planCopy,
                        ui = ui,
                        onToggle = viewModel::toggleKnown,
                        onAdd = viewModel::addKnown,
                    )

                    OnboardingStep.Preset -> PresetStep(
                        copy = copy,
                        planCopy = planCopy,
                        ui = ui,
                        onChoose = viewModel::choosePreset,
                        onAddCustom = viewModel::addCustom,
                        onRemoveCustom = viewModel::removeCustom,
                    )

                    OnboardingStep.Pace -> PaceStep(
                        copy = copy,
                        planCopy = planCopy,
                        ui = ui,
                        onPerDay = viewModel::setPerDay,
                        onMode = viewModel::setMode,
                        onTarget = viewModel::setTarget,
                    )

                    OnboardingStep.Reminder -> ReminderStep(
                        copy = copy,
                        reminderCopy = reminderCopy,
                        lessonRange = ui.firstLesson?.let { range ->
                            passageRangeLabel(ui.surahName(range.surah) ?: range.surah.toString(), range.startAyah, range.endAyah)
                        },
                        lessonMinutes = ui.lessonMinutes,
                        minuteOfDay = ui.reminderMinute,
                        denied = ui.reminder == ReminderChoice.Denied,
                        onMinute = viewModel::setReminderMinute,
                    )

                    OnboardingStep.Comfort -> ComfortStep(
                        copy = copy,
                        settingsCopy = settingsCopy,
                        ayahs = ui.rahman,
                        fontStyle = fontStyle,
                        fontSize = fontSize,
                        onSelectFont = setFontStyle,
                        onFontSizeChange = { size -> setSettings(settings.copy(readingFontSize = size)) },
                        themeIndex = themeSegment(followsSystem, themeMode),
                        onSelectTheme = { index ->
                            when (index) {
                                0 -> themeSwitcher.switch(ThemeMode.LIGHT)
                                1 -> themeSwitcher.switch(ThemeMode.DARK)
                                else -> themeSwitcher.reveal { viewModel.setThemeFollowsSystem(true) }
                            }
                        },
                        palette = palette,
                        onSelectPalette = themeSwitcher::switchPalette,
                    )

                    OnboardingStep.Ready -> ReadyStep(
                        copy = copy,
                        planCopy = planCopy,
                        trustCopy = trustCopy,
                        ui = ui,
                        memorize = memorize,
                        reminderTime = if (ui.reminder == ReminderChoice.On) formatReminderTime(ui.reminderMinute, is24Hour) else null,
                        fontStyle = fontStyle,
                        fontSize = fontSize,
                        analyticsEnabled = analyticsEnabled,
                        onAnalytics = viewModel::setAnalyticsEnabled,
                    )
                }
            }

            // The footer: the main button is always at the bottom, whatever the font size.
            val known = ui.selection.known
            when (step) {
                // "Skip" on a slide leads to the goal; it sits under the main button, where the thumb is and where a long word fits.
                OnboardingStep.Story -> OnboardingFooter(
                    primary = copy.next,
                    onPrimary = ::next,
                    secondary = copy.skip,
                    onSecondary = viewModel::skipStory,
                )

                OnboardingStep.Goal -> OnboardingFooter(copy.next, enabled = ui.goal != null, onPrimary = ::next)

                OnboardingStep.Known -> OnboardingFooter(
                    primary = if (known.isEmpty()) copy.knownNone else copy.next,
                    note = if (known.isEmpty()) null else copy.knownCount(known.size),
                    onPrimary = ::next,
                )

                OnboardingStep.Preset -> OnboardingFooter(
                    primary = copy.next,
                    enabled = ui.selection.preset != null && OnboardingPlanMath.canLearn(ui.selection),
                    onPrimary = ::next,
                )

                OnboardingStep.Reminder -> if (ui.reminder == ReminderChoice.Denied) {
                    OnboardingFooter(copy.next, onPrimary = ::next)
                } else {
                    OnboardingFooter(
                        primary = copy.reminderOn,
                        showArrow = false,
                        onPrimary = {
                            if (ReminderPermission.needsRequest(context)) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.applyReminder(allowed = !ReminderPermission.isBlocked(context))
                            }
                        },
                        secondary = copy.reminderNotNow,
                        onSecondary = viewModel::skipReminder,
                    )
                }

                OnboardingStep.Ready -> OnboardingFooter(
                    primary = if (memorize) copy.readyStart else copy.readyReadOpen,
                    showArrow = false,
                    loading = ui.finishing,
                    onPrimary = { viewModel.finish(if (memorize) OnboardingExit.Lesson else OnboardingExit.Reader) },
                    secondary = copy.later,
                    secondaryEnabled = !ui.finishing,
                    onSecondary = { viewModel.finish(OnboardingExit.Later) },
                )

                else -> OnboardingFooter(copy.next, onPrimary = ::next)
            }
        }
    }
}

private const val StepMoveMillis = 380
private const val StepSlideFraction = 0.12f
private const val ReducedStepMillis = 150

// -- Top row -----------------------------------------------------------------------------------------

/**
 * The row above the steps: a back arrow once there is something to go back to, and the thin segmented progress bar (one segment
 * per step; the story's three slides fill one segment in thirds).
 */
@Composable
private fun OnboardingTopRow(
    showBack: Boolean,
    segmentCount: Int,
    segmentIndex: Int,
    currentFill: Float,
    progressLabel: String,
    backLabel: String,
    onBack: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val enter = if (reduced) fadeIn(snap()) else fadeIn(QhMotion.snappy()) + scaleIn(QhMotion.snappy(), initialScale = 0.6f)
    val exit = if (reduced) fadeOut(snap()) else fadeOut(QhMotion.snappy()) + scaleOut(QhMotion.snappy(), targetScale = 0.6f)
    // Side slots of one width keep the bar centered.
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = QhSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SideSlot), contentAlignment = Alignment.CenterStart) {
            TopRowSlot(visible = showBack, enter = enter, exit = exit) {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backLabel,
                    onClick = onBack,
                    filled = false,
                )
            }
        }
        SegmentBar(
            count = segmentCount,
            index = segmentIndex,
            currentFill = currentFill,
            label = progressLabel,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(SideSlot))
    }
}

private val SideSlot = 52.dp

/** Shows [content] with [enter] / [exit]; a plain function so it is not resolved against the enclosing Row. */
@Composable
private fun TopRowSlot(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) { content() }
}

/** A thin bar cut into [count] segments; the ones passed are full, the current one fills to [currentFill]. Announced as "Step n of m". */
@Composable
private fun SegmentBar(count: Int, index: Int, currentFill: Float, label: String, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    Row(
        modifier
            .padding(horizontal = QhSpacing.x2)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { segment ->
            val target = when {
                segment < index -> 1f
                segment == index -> currentFill
                else -> 0f
            }
            val fill by animateFloatAsState(
                targetValue = target,
                animationSpec = QhMotion.gentle<Float>().orSnap(),
                label = "segment-fill",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(QhTheme.shapes.full)
                    .background(colors.surfaceStrong),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .clip(QhTheme.shapes.full)
                        .background(colors.primary),
                )
            }
        }
    }
}

// -- Footer ------------------------------------------------------------------------------------------

/** The bottom of every step: an optional line of text, the main button, and an optional quiet second one. */
@Composable
private fun OnboardingFooter(
    primary: String,
    onPrimary: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    showArrow: Boolean = true,
    note: String? = null,
    secondary: String? = null,
    secondaryEnabled: Boolean = true,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x3, bottom = QhSpacing.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        if (note != null) {
            Text(
                text = note,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OnboardingCta(text = primary, showArrow = showArrow, enabled = enabled, loading = loading, onClick = onPrimary)
        if (secondary != null) {
            QhButton(
                text = secondary,
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
                enabled = secondaryEnabled,
            )
        }
    }
}

/**
 * The main action of every step: a full-width pill whose label crossfades between steps and whose arrow gives a small push to the
 * right when pressed. Greyed out while [enabled] is false; [loading] swaps the label for three dots and ignores presses.
 */
@Composable
private fun OnboardingCta(
    text: String,
    showArrow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) }
    val active = enabled && !loading
    val container by animateColorAsState(
        targetValue = if (enabled) colors.primary else colors.surfaceStrong,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "cta-container",
    )
    val content by animateColorAsState(
        targetValue = if (enabled) colors.onPrimary else colors.inkTertiary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "cta-content",
    )

    Box(
        modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.softShadow(shape) else Modifier)
            .heightIn(min = 56.dp)
            .pressable(
                onClick = {
                    if (!reduced) {
                        scope.launch {
                            nudge.animateTo(ArrowNudge, QhMotion.press())
                            nudge.animateTo(0f, QhMotion.bouncy())
                        }
                    }
                    onClick()
                },
                enabled = active,
                shape = shape,
                highlight = false,
            )
            .clip(shape)
            .background(container)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            DotSpinner(color = content)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(
                    targetState = text,
                    animationSpec = if (reduced) snap() else tween(QhMotion.Medium),
                    label = "cta-label",
                ) { label ->
                    Text(
                        text = label,
                        style = QhTheme.type.title2,
                        color = content,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(
                    visible = showArrow,
                    enter = if (reduced) fadeIn(snap()) else fadeIn(QhMotion.snappy()),
                    exit = if (reduced) fadeOut(snap()) else fadeOut(QhMotion.snappy()),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier
                            .size(QhSpacing.iconInline)
                            .offset { IntOffset(nudge.value.dp.roundToPx(), 0) },
                    )
                }
            }
        }
    }
}

private const val ArrowNudge = 8f
