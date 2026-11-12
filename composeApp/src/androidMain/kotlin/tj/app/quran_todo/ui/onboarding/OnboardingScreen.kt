package tj.app.quran_todo.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import tj.app.quran_todo.ui.components.DotSpinner
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.softShadow
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.rememberOnboardingCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.i18n.rememberTrustCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The onboarding (docs/redesign/UX_PLAN.md, U1): four screens - welcome and language, what to learn, what is known, the plan - one
 * question at a time, in a shared-axis transition. "Back" (the arrow and the system gesture) returns to the previous step, on the
 * first step the system gesture leaves the app. The language applies at once through the root's setter; the memorization progress
 * is written only by the button of the last screen or by the "just read and listen" link (see [OnboardingViewModel.finish]).
 * The font, the size and the theme keep their defaults (they live in the reader's text sheet and the settings); the reminder is
 * not asked here.
 */
@Composable
internal fun OnboardingScreen(onFinished: (OnboardingExit) -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current

    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsStateWithLifecycle()

    val copy = rememberOnboardingCopy(language)
    val settingsCopy = rememberSettingsCopy(language)
    val trustCopy = rememberTrustCopy(language)
    val planCopy = remember(language) { planCopy(language) }
    LaunchedEffect(language) { viewModel.setLanguage(language) }

    val position = ui.position
    val step = position.step

    BackHandler(enabled = position != FlowPosition.First) { viewModel.back() }

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
                currentFill = 1f,
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

                    OnboardingStep.Preset -> PresetStep(
                        copy = copy,
                        planCopy = planCopy,
                        ui = ui,
                        onChoose = viewModel::choosePreset,
                        onAddCustom = viewModel::addCustom,
                        onRemoveCustom = viewModel::removeCustom,
                        // "Just read and listen for now": the onboarding ends here, in the reader.
                        onReadOnly = { viewModel.finish(OnboardingExit.Reader) },
                        readOnlyEnabled = !ui.finishing,
                    )

                    OnboardingStep.Known -> KnownStep(
                        copy = copy,
                        planCopy = planCopy,
                        ui = ui,
                        onToggle = viewModel::toggleKnown,
                        onAdd = viewModel::addKnown,
                    )

                    OnboardingStep.Ready -> ReadyStep(
                        copy = copy,
                        planCopy = planCopy,
                        trustCopy = trustCopy,
                        ui = ui,
                        analyticsEnabled = analyticsEnabled,
                        onAnalytics = viewModel::setAnalyticsEnabled,
                        onPerDay = viewModel::setPerDay,
                    )
                }
            }

            // The footer: the main button is always at the bottom, whatever the font size. One call for every step, so
            // the label crossfades, the colours animate and the second button / the note grow and shrink instead of popping.
            val known = ui.selection.known
            val footer = when (step) {
                OnboardingStep.Language -> FooterSpec(copy.start, onPrimary = ::next)

                OnboardingStep.Preset -> FooterSpec(
                    primary = copy.next,
                    // A chosen list with something in it; "Choose surahs" stays off until a surah is added.
                    enabled = ui.selection.preset != null && ui.selection.queue.isNotEmpty() && !ui.finishing,
                    onPrimary = ::next,
                )

                // Everything of the chosen list marked as known: say so and keep the button off.
                OnboardingStep.Known -> if (OnboardingPlanMath.nothingToLearn(ui.selection)) {
                    FooterSpec(
                        primary = copy.next,
                        enabled = false,
                        note = when (ui.selection.preset) {
                            PresetChoice.JuzAmma -> copy.allKnownJuzAmma
                            PresetChoice.JuzTabarak -> copy.allKnownJuzTabarak
                            PresetChoice.FromStart -> copy.allKnownFromStart
                            PresetChoice.Custom, null -> copy.allKnownCustom
                        },
                        onPrimary = ::next,
                    )
                } else {
                    FooterSpec(
                        primary = if (known.isEmpty()) copy.knownNone else copy.next,
                        note = if (known.isEmpty()) null else copy.knownCount(known.size),
                        onPrimary = ::next,
                    )
                }

                OnboardingStep.Ready -> FooterSpec(
                    primary = copy.readyStart,
                    showArrow = false,
                    loading = ui.finishing,
                    onPrimary = { viewModel.finish(OnboardingExit.Lesson) },
                    secondary = copy.later,
                    secondaryEnabled = !ui.finishing,
                    onSecondary = { viewModel.finish(OnboardingExit.Later) },
                )
            }
            OnboardingFooter(footer)
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

/** What the footer shows for one step. */
private class FooterSpec(
    val primary: String,
    val onPrimary: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val showArrow: Boolean = true,
    val note: String? = null,
    val secondary: String? = null,
    val secondaryEnabled: Boolean = true,
    val onSecondary: () -> Unit = {},
)

/**
 * The bottom of every step: an optional line of text, the main button, and an optional quiet second one. The line and the second
 * button open and close with a spring (a step that has one slides the main button up smoothly instead of jumping), and keep their
 * last text while they leave.
 */
@Composable
private fun OnboardingFooter(spec: FooterSpec) {
    val reduced = LocalReducedMotion.current
    val expand = if (reduced) {
        fadeIn(snap()) + expandVertically(snap())
    } else {
        fadeIn(QhMotion.gentle()) + expandVertically(QhMotion.gentle())
    }
    val shrink = if (reduced) {
        fadeOut(snap()) + shrinkVertically(snap())
    } else {
        fadeOut(tween(QhMotion.Short)) + shrinkVertically(QhMotion.gentle())
    }
    // The text and the handler of a slot that is leaving stay as they were.
    var lastNote by remember { mutableStateOf("") }
    spec.note?.let { lastNote = it }
    var lastSecondary by remember { mutableStateOf("") }
    spec.secondary?.let { lastSecondary = it }
    val latestOnSecondary by rememberUpdatedState(spec.onSecondary)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x3, bottom = QhSpacing.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = spec.note != null, enter = expand, exit = shrink) {
            Text(
                text = lastNote,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = QhSpacing.x2),
            )
        }
        OnboardingCta(
            text = spec.primary,
            showArrow = spec.showArrow,
            enabled = spec.enabled,
            loading = spec.loading,
            onClick = spec.onPrimary,
        )
        AnimatedVisibility(visible = spec.secondary != null, enter = expand, exit = shrink) {
            QhButton(
                text = lastSecondary,
                onClick = { latestOnSecondary() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = QhSpacing.x2),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
                enabled = spec.secondaryEnabled,
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
