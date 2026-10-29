package tj.app.quran_todo.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.analytics.AppTelemetry
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
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.softShadow
import tj.app.quran_todo.ui.i18n.rememberOnboardingCopy
import tj.app.quran_todo.ui.i18n.rememberSettingsCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.motion.rememberThemeSwitcher
import tj.app.quran_todo.ui.settings.MaxReadingFontSize
import tj.app.quran_todo.ui.settings.MinReadingFontSize
import tj.app.quran_todo.ui.state.OnboardingGoal
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private const val StepCount = 4
private const val LastStep = StepCount - 1

/**
 * First-launch onboarding (docs/redesign/ANDROID_REDESIGN.md, 6.2): one screen, four steps in a
 * shared-axis transition. Every choice goes through the root's setters (so the app behind already
 * wears it) and is persisted as the person moves on; system back returns to the previous step, and
 * on the first step leaves the app.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val viewModel = koinViewModel<OnboardingViewModel>()
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
    val reduced = LocalReducedMotion.current
    val colors = QhTheme.colors

    val followsSystem by viewModel.themeFollowsSystem.collectAsState()
    val loadedAyahs by viewModel.rahmanAyahs.collectAsState()
    val copy = rememberOnboardingCopy(language)
    val settingsCopy = rememberSettingsCopy(language)

    var step by rememberSaveable { mutableIntStateOf(0) }
    var goals by rememberSaveable { mutableIntStateOf(0) }
    var dailyGoal by rememberSaveable { mutableIntStateOf(DailyGoalPresets.nearestTo(settings.dailyGoal)) }

    BackHandler(enabled = step > 0) { step -= 1 }

    fun logStep(name: String, extra: Map<String, String> = emptyMap()) {
        AppTelemetry.logEvent(name = "onboarding_completed", params = mapOf("step" to name) + extra)
    }

    fun finish(saveGoals: Boolean) {
        if (saveGoals) {
            val chosen = buildSet {
                if (goals and GoalRead != 0) add(OnboardingGoal.READ)
                if (goals and GoalListen != 0) add(OnboardingGoal.LISTEN)
                if (goals and GoalMemorize != 0) add(OnboardingGoal.MEMORIZE)
            }
            viewModel.saveGoals(chosen)
            if (goals and GoalMemorize != 0) setSettings(settings.copy(dailyGoal = dailyGoal))
            logStep(
                "goals",
                mapOf("goals" to chosen.sorted().joinToString(","), "daily_goal" to dailyGoal.toString()),
            )
        } else {
            logStep("goals_skipped")
        }
        onFinished()
    }

    fun advance() {
        when (step) {
            0 -> {
                // Saved even if the person kept the suggested language.
                setLanguage(language)
                logStep("language", mapOf("language" to language.name.lowercase()))
            }
            1 -> {
                setFontStyle(fontStyle)
                setSettings(settings.copy(readingFontSize = settings.readingFontSize.coerceIn(MinReadingFontSize, MaxReadingFontSize)))
                logStep("font", mapOf("font" to fontStyle.name.lowercase(), "size" to settings.readingFontSize.toString()))
            }
            2 -> {
                viewModel.setThemeFollowsSystem(followsSystem)
                setPalette(palette)
                logStep(
                    "theme",
                    mapOf(
                        "mode" to if (followsSystem) "system" else themeMode.name.lowercase(),
                        "palette" to palette.name.lowercase(),
                    ),
                )
            }
        }
        if (step < LastStep) step += 1 else finish(saveGoals = true)
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
                step = step,
                stepLabel = copy.stepLabel(step + 1, StepCount),
                backLabel = settingsCopy.native.back,
                skipLabel = copy.skip,
                onBack = { if (step > 0) step -= 1 },
                onSkip = { finish(saveGoals = false) },
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
                        val direction = if (targetState > initialState) 1 else -1
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
            ) { shownStep ->
                when (shownStep) {
                    0 -> WelcomeStep(
                        copy = copy,
                        language = language,
                        onSelectLanguage = setLanguage,
                    )

                    1 -> ReadingStep(
                        copy = copy,
                        settingsCopy = settingsCopy,
                        ayahs = loadedAyahs ?: FallbackRahmanAyahs,
                        fontStyle = fontStyle,
                        fontSize = settings.readingFontSize.coerceIn(MinReadingFontSize, MaxReadingFontSize),
                        onSelectFont = setFontStyle,
                        onFontSizeChange = { size -> setSettings(settings.copy(readingFontSize = size)) },
                    )

                    2 -> LookStep(
                        copy = copy,
                        settingsCopy = settingsCopy,
                        fontStyle = fontStyle,
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

                    else -> GoalsStep(
                        copy = copy,
                        settingsCopy = settingsCopy,
                        goals = goals,
                        onToggleGoal = { bit -> goals = goals xor bit },
                        dailyGoal = dailyGoal,
                        onDailyGoalChange = { dailyGoal = it },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QhSpacing.screen)
                    .padding(top = QhSpacing.x3, bottom = QhSpacing.x4),
            ) {
                OnboardingCta(
                    text = if (step == LastStep) copy.start else copy.next,
                    showArrow = step != LastStep,
                    onClick = ::advance,
                )
            }
        }
    }
}

private const val StepMoveMillis = 380
private const val StepSlideFraction = 0.12f
private const val ReducedStepMillis = 150

/**
 * The row above the steps: a back arrow once there is something to go back to, the step indicator in
 * the middle, "Skip" on the last step.
 */
@Composable
private fun OnboardingTopRow(
    step: Int,
    stepLabel: String,
    backLabel: String,
    skipLabel: String,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val enter = if (reduced) fadeIn(snap()) else fadeIn(QhMotion.snappy()) + scaleIn(QhMotion.snappy(), initialScale = 0.6f)
    val exit = if (reduced) fadeOut(snap()) else fadeOut(QhMotion.snappy()) + scaleOut(QhMotion.snappy(), targetScale = 0.6f)
    // Equal side slots keep the indicator exactly centered, whatever the width of "Skip".
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = QhSpacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TopRowSlot(visible = step > 0, enter = enter, exit = exit) {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backLabel,
                    onClick = onBack,
                    filled = false,
                )
            }
        }
        StepIndicator(step = step, count = StepCount, label = stepLabel)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TopRowSlot(visible = step == LastStep, enter = enter, exit = exit) {
                QhButton(
                    text = skipLabel,
                    onClick = onSkip,
                    variant = QhButtonVariant.Ghost,
                    size = QhButtonSize.S,
                )
            }
        }
    }
}

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

/** Dots; the current one stretches into a pill on a spring. Announced as "Step n of m". */
@Composable
private fun StepIndicator(step: Int, count: Int, label: String, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val width: Dp by animateDpAsState(
                targetValue = if (index == step) 24.dp else 8.dp,
                animationSpec = QhMotion.snappy<Dp>().orSnap(),
                label = "dot-width",
            )
            val color: Color by animateColorAsState(
                targetValue = when {
                    index == step -> colors.primary
                    index < step -> colors.primary.copy(alpha = 0.4f)
                    else -> colors.surfaceStrong
                },
                animationSpec = QhMotion.snappy<Color>().orSnap(),
                label = "dot-color",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * The primary action of every step: a full-width pill whose label crossfades between "Next" and
 * "Get started" and whose arrow gives a small push to the right when pressed.
 */
@Composable
private fun OnboardingCta(
    text: String,
    showArrow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) }

    Box(
        modifier
            .fillMaxWidth()
            .softShadow(shape)
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
                shape = shape,
                highlight = false,
            )
            .clip(shape)
            .background(colors.primary)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
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
                    color = colors.onPrimary,
                    maxLines = 1,
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
                    tint = colors.onPrimary,
                    modifier = Modifier
                        .size(QhSpacing.iconInline)
                        .offset { IntOffset(nudge.value.dp.roundToPx(), 0) },
                )
            }
        }
    }
}

private const val ArrowNudge = 8f
