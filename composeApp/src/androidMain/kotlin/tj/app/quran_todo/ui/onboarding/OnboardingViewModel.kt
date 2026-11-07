package tj.app.quran_todo.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.home.HomeRows
import tj.app.quran_todo.ui.home.QuranIndex
import tj.app.quran_todo.ui.home.RowsInput
import tj.app.quran_todo.ui.home.SurahRowUi
import tj.app.quran_todo.ui.plan.PaceMode
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.state.DEFAULT_REMINDER_MINUTE_OF_DAY
import tj.app.quran_todo.ui.state.OnboardingGoal
import tj.app.quran_todo.ui.state.UiPrefs

/** What the person decided about the reminder on the reminder step. */
internal enum class ReminderChoice {
    Undecided,
    On,
    Off,

    /** "Remind me" was pressed and the system did not allow notifications: the reminder is off, the step is passed. */
    Denied,
}

/** What the last button of the onboarding opens. */
internal enum class OnboardingExit(val analyticsName: String) {
    /** Home with the first lesson over it. */
    Lesson("lesson"),

    /** Home. */
    Later("later"),

    /** Home with Al-Fatihah in the reader over it ("Read and listen" path). */
    Reader("reader"),
}

/** One-time things the screen reacts to. */
internal sealed interface OnboardingEvent {
    /** All is written: leave through [exit]. */
    data class Finished(val exit: OnboardingExit) : OnboardingEvent

    /** The write failed: a toast, the screen stays. */
    data object FinishFailed : OnboardingEvent
}

/** Everything the screen draws. */
internal data class OnboardingUi(
    val position: FlowPosition = FlowPosition.First,
    val goal: GoalChoice? = null,
    val selection: PlanSelection = PlanSelection(),
    val reminder: ReminderChoice = ReminderChoice.Undecided,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE_OF_DAY,
    /** The 114 rows with the names in the language of the app; empty until the surah list is there. */
    val catalog: List<SurahRowUi> = emptyList(),
    /** One card per preset, with the first lesson around what is known. */
    val presets: List<PresetInfo> = emptyList(),
    val firstLesson: AyahRange? = null,
    /** The estimate of the first lesson in minutes; null until counted (or when it can not be). */
    val lessonMinutes: Int? = null,
    val forecast: OnboardingForecast = OnboardingForecast(0, 0, 0, null, null, false),
    val today: Int = 0,
    /** Al-Fatihah 1-3 for the story: the loaded text, or the built-in sample. */
    val fatiha: List<PreviewAyah> = FallbackFatihaAyahs,
    /** Ar-Rahman 1-4 for the reading preview. */
    val rahman: List<PreviewAyah> = FallbackRahmanAyahs,
    val finishing: Boolean = false,
) {
    val segmentCount: Int get() = OnboardingFlow.segmentCount(goal)
    val segmentIndex: Int get() = OnboardingFlow.segmentIndex(position, goal)

    fun surahName(number: Int): String? = catalog.firstOrNull { it.number == number }?.name
}

/**
 * Creates the [OnboardingViewModel] with the [androidx.lifecycle.SavedStateHandle] of the navigation entry. Koin's own
 * `koinViewModel` does not hand a `SavedStateHandle` to a definition, so the handle is made here, from the creation extras of the
 * entry, and passed to the Koin definition (`onboardingUiModule`) as a parameter; everything else comes from Koin through `get()`.
 */
internal val OnboardingViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { KoinPlatform.getKoin().get<OnboardingViewModel> { parametersOf(createSavedStateHandle()) } }
}

/**
 * State of the onboarding v2. The step, the goal and every choice live here and in the [SavedStateHandle], so a killed process
 * continues from the same step; **nothing of the progress is written until the last screen** ([finish], through
 * [OnboardingFinisher] and `HifzRepository` only). The language, the font and the theme are applied at once through the root's
 * setters by the screen, the reminder through [ReminderController], the goal is written to `UiPrefs` when the goal step is left.
 *
 * The shared `HomeViewModel` (the Quran text and the surah names) and `HifzRepository` arrive through the constructor, never
 * `koinViewModel`: they are singletons (docs/redesign/API.md).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class OnboardingViewModel(
    private val handle: SavedStateHandle,
    private val home: HomeViewModel,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val uiPrefs: UiPrefs,
    private val reminder: ReminderController,
) : ViewModel() {

    private class Choices(
        val position: FlowPosition,
        val goal: GoalChoice?,
        val selection: PlanSelection,
        val reminder: ReminderChoice,
        val reminderMinute: Int,
    )

    private val choices = MutableStateFlow(restore())
    private val finishing = MutableStateFlow(false)
    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val events = Channel<OnboardingEvent>(Channel.BUFFERED)
    private val finisher = OnboardingFinisher(hifz) { uiPrefs.setOnboardingCompleted() }

    /** One-shot events. */
    val eventFlow: Flow<OnboardingEvent> = events.receiveAsFlow()

    @Volatile
    private var cachedIndex: Pair<List<SurahWithAyahs>, QuranIndex>? = null

    private fun indexFor(complete: List<SurahWithAyahs>): QuranIndex {
        cachedIndex?.takeIf { it.first === complete }?.let { return it.second }
        return QuranIndex.from(complete).also { cachedIndex = complete to it }
    }

    private val quran = home.uiState
        .map { Triple(it.surahList, it.chapterNames, it.completeQuran) }
        .distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second && a.third === b.third }

    /** The 114 rows with the names in the language of the app (the same rule as on the home screen). */
    private val catalog: Flow<List<SurahRowUi>> = combine(quran, language) { (surahList, chapterNames, complete), language ->
        HomeRows.build(
            RowsInput(
                language = language,
                surahList = surahList,
                index = indexFor(complete),
                chapterNames = chapterNames,
                statuses = emptyMap(),
                coveredAyahs = emptyMap(),
                currentPlanSurah = null,
                pinned = emptySet(),
            ),
        )
    }.flowOn(Dispatchers.Default)

    private val samples: Flow<Pair<List<PreviewAyah>, List<PreviewAyah>>> = home.uiState
        .map { it.completeQuran }
        .distinctUntilChanged { a, b -> a === b }
        .map { quranText -> (fatihaPreview(quranText) ?: FallbackFatihaAyahs) to (rahmanPreview(quranText) ?: FallbackRahmanAyahs) }
        .flowOn(Dispatchers.Default)

    /** The estimate of the first lesson, counted by the repository's own formulas (a read; nothing is written). */
    private val lessonMinutes: Flow<Int?> = combine(choices, ticker.day) { c, today -> OnboardingPlanMath.firstLesson(c.selection, today) }
        .distinctUntilChanged()
        .debounce(EstimateDebounceMillis)
        .mapLatest { range -> range?.let { estimate(it) } }

    private data class Core(val choices: Choices, val finishing: Boolean, val today: Int)

    private val core: Flow<Core> = combine(choices, finishing, ticker.day, ::Core)

    val ui: StateFlow<OnboardingUi> = combine(core, catalog, samples, lessonMinutes) { core, catalog, (fatiha, rahman), minutes ->
        val c = core.choices
        val selection = c.selection
        OnboardingUi(
            position = c.position,
            goal = c.goal,
            selection = selection,
            reminder = c.reminder,
            reminderMinute = c.reminderMinute,
            catalog = catalog,
            presets = PresetChoice.entries.map {
                OnboardingPlanMath.presetInfo(it, selection.customQueue, selection.known, OnboardingPlanMath.effectivePerDay(selection, core.today))
            },
            firstLesson = OnboardingPlanMath.firstLesson(selection, core.today),
            lessonMinutes = minutes,
            forecast = OnboardingPlanMath.forecast(selection, core.today),
            today = core.today,
            fatiha = fatiha,
            rahman = rahman,
            finishing = core.finishing,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), OnboardingUi(position = choices.value.position))

    init {
        // From now on a half-passed onboarding is not mistaken for an old install that only updated.
        uiPrefs.markOnboardingStarted()
        logStep(choices.value.position)
        // The surah grid shows the names of the app language, as Home does; Home is not open yet to load them.
        home.loadChapterNames(language.value)
    }

    fun setLanguage(next: AppLanguage) {
        if (language.value == next) return
        language.value = next
        home.loadChapterNames(next)
    }

    /** "System" in the theme control; light and dark go through the root's mode setter. */
    val themeFollowsSystem: StateFlow<Boolean> = uiPrefs.themeFollowsSystem

    fun setThemeFollowsSystem(value: Boolean) = uiPrefs.setThemeFollowsSystem(value)

    /** The "anonymous statistics" switch of the last screen (the same switch as in the settings). */
    val analyticsEnabled: StateFlow<Boolean> = uiPrefs.analyticsEnabled

    fun setAnalyticsEnabled(value: Boolean) = uiPrefs.setAnalyticsEnabled(value)

    // -- Moving -------------------------------------------------------------------------------

    private fun update(change: (Choices) -> Choices) {
        val next = change(choices.value)
        choices.value = next
        save(next)
    }

    private fun moveTo(position: FlowPosition) {
        update { Choices(position, it.goal, it.selection, it.reminder, it.reminderMinute) }
        logStep(position)
    }

    /** "Next". Leaving the goal step writes the goal to `UiPrefs`. */
    fun next() {
        val c = choices.value
        if (c.position.step == OnboardingStep.Goal) saveGoal(c.goal)
        OnboardingFlow.next(c.position, c.goal)?.let(::moveTo)
    }

    /** "Back" (the arrow and the system gesture). False on the first step: back then leaves the app. */
    fun back(): Boolean {
        val c = choices.value
        val previous = OnboardingFlow.back(c.position, c.goal) ?: return false
        moveTo(previous)
        return true
    }

    fun skipStory() {
        val c = choices.value
        if (c.position.step != OnboardingStep.Story) return
        AppTelemetry.logEvent("onboarding_story_skipped")
        moveTo(OnboardingFlow.skipStory(c.position))
    }

    /** The pager of the story was swiped to [page]. */
    fun showStoryPage(page: Int) {
        val c = choices.value
        if (c.position.step != OnboardingStep.Story || c.position.storyPage == page) return
        moveTo(FlowPosition(OnboardingStep.Story, page.coerceIn(0, StoryPageCount - 1)))
    }

    private fun saveGoal(goal: GoalChoice?) {
        when (goal) {
            GoalChoice.Memorize -> uiPrefs.setOnboardingGoals(setOf(OnboardingGoal.MEMORIZE))
            GoalChoice.Read -> uiPrefs.setOnboardingGoals(setOf(OnboardingGoal.READ, OnboardingGoal.LISTEN))
            null -> Unit
        }
    }

    // -- Choices ------------------------------------------------------------------------------

    fun chooseGoal(goal: GoalChoice) = update { Choices(it.position, goal, it.selection, it.reminder, it.reminderMinute) }

    private fun editSelection(change: (PlanSelection) -> PlanSelection) = update {
        Choices(it.position, it.goal, change(it.selection).settled(), it.reminder, it.reminderMinute)
    }

    /** A preset that has nothing left to learn can not stay chosen. */
    private fun PlanSelection.settled(): PlanSelection {
        val chosen = preset ?: return this
        val everythingKnown = OnboardingPlanMath.queueOf(chosen, customQueue).let { it.isNotEmpty() && it.all { surah -> surah in known } }
        return if (everythingKnown && chosen != PresetChoice.Custom) copy(preset = null) else this
    }

    fun toggleKnown(surah: Int) = editSelection { selection ->
        val known = if (surah in selection.known) selection.known - surah else (selection.known + surah).sorted()
        selection.copy(known = known)
    }

    fun addKnown(surah: Int) = editSelection { selection ->
        if (surah in selection.known || surah !in 1..114) selection else selection.copy(known = (selection.known + surah).sorted())
    }

    fun choosePreset(preset: PresetChoice) = editSelection { it.copy(preset = preset) }

    fun addCustom(surah: Int) = editSelection { selection ->
        if (surah in selection.customQueue || surah !in 1..114) selection else selection.copy(customQueue = selection.customQueue + surah)
    }

    fun removeCustom(surah: Int) = editSelection { it.copy(customQueue = it.customQueue - surah) }

    fun setPerDay(value: Int) = editSelection { it.copy(perDay = value.coerceIn(MinPerDay, MaxPerDay)) }

    fun setMode(mode: PaceMode) = editSelection { selection ->
        when (mode) {
            PaceMode.PerDay -> selection.copy(mode = mode)
            PaceMode.ByDate -> selection.copy(
                mode = mode,
                targetDay = selection.targetDay ?: OnboardingPlanMath.suggestedTarget(selection, ticker.day.value),
            )
        }
    }

    fun setTarget(epochDay: Int) = editSelection { it.copy(mode = PaceMode.ByDate, targetDay = epochDay) }

    // -- Reminder -----------------------------------------------------------------------------

    fun setReminderMinute(minuteOfDay: Int) = update { Choices(it.position, it.goal, it.selection, it.reminder, minuteOfDay.coerceIn(0, 1439)) }

    /**
     * The result of "Remind me": [allowed] is true when notifications can come (the permission was given, or is not needed). Allowed:
     * the time is stored, the alarm is planned and the step is passed; otherwise the reminder stays off and the step shows why.
     */
    fun applyReminder(allowed: Boolean) {
        val c = choices.value
        if (!allowed) {
            reminder.setEnabled(false, track = false)
            update { Choices(it.position, it.goal, it.selection, ReminderChoice.Denied, it.reminderMinute) }
            return
        }
        reminder.setMinuteOfDay(c.reminderMinute, track = false)
        reminder.setEnabled(true)
        update { Choices(it.position, it.goal, it.selection, ReminderChoice.On, it.reminderMinute) }
        next()
    }

    /** "Not now": no permission is asked and the reminder is off. */
    fun skipReminder() {
        reminder.setEnabled(false, track = false)
        update { Choices(it.position, it.goal, it.selection, ReminderChoice.Off, it.reminderMinute) }
        next()
    }

    // -- The end ------------------------------------------------------------------------------

    /**
     * The last button. In one coroutine: the known surahs, the plan, the "passed" flag; then [OnboardingEvent.Finished]. A failure is
     * reported and the screen stays; a repeated press is turned away while writing and after success.
     */
    fun finish(exit: OnboardingExit) {
        val c = choices.value
        val memorize = c.goal != GoalChoice.Read
        val today = ticker.day.value
        val commit = OnboardingCommit(
            known = if (memorize) c.selection.known else emptyList(),
            plan = if (memorize) OnboardingPlanMath.planToSave(c.selection, today) else null,
        )
        viewModelScope.launch {
            finishing.value = true
            val result = finisher.finish(commit, today)
            finishing.value = false
            when (result) {
                FinishResult.Done -> {
                    logCompleted(c, exit, memorize)
                    events.send(OnboardingEvent.Finished(exit))
                }
                is FinishResult.Failed -> {
                    AppTelemetry.logError(throwable = result.error, context = "onboarding_finish_failed")
                    events.send(OnboardingEvent.FinishFailed)
                }
                FinishResult.Busy -> Unit
            }
        }
    }

    private fun logCompleted(c: Choices, exit: OnboardingExit, memorize: Boolean) {
        val goal = if (memorize) GoalChoice.Memorize else GoalChoice.Read
        AppTelemetry.logEvent(
            name = "onboarding_completed",
            params = mapOf(
                "goal" to goal.analyticsName,
                "preset" to if (memorize) (c.selection.preset?.analyticsName ?: "none") else "none",
                "pace" to if (memorize) OnboardingPlanMath.effectivePerDay(c.selection, ticker.day.value).toString() else "0",
                "known" to OnboardingPlanMath.knownBucket(if (memorize) c.selection.known.size else 0),
                "reminder" to if (uiPrefs.reminderEnabled.value) "on" else "off",
                "action" to exit.analyticsName,
            ),
        )
    }

    private fun logStep(position: FlowPosition) {
        AppTelemetry.logEvent("onboarding_step", mapOf("step" to position.analyticsName))
    }

    private suspend fun estimate(range: AyahRange): Int? = try {
        hifz.estimateMinutes(range.surah, range.startAyah, range.endAyah, isNew = true)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AppTelemetry.logError(throwable = error, context = "onboarding_estimate_failed")
        null
    }

    // -- Saved state --------------------------------------------------------------------------

    private fun save(c: Choices) {
        handle[KEY_STEP] = c.position.step.name
        handle[KEY_PAGE] = c.position.storyPage
        handle[KEY_GOAL] = c.goal?.name.orEmpty()
        handle[KEY_KNOWN] = c.selection.known.toIntArray()
        handle[KEY_PRESET] = c.selection.preset?.name.orEmpty()
        handle[KEY_CUSTOM] = c.selection.customQueue.toIntArray()
        handle[KEY_MODE] = c.selection.mode.name
        handle[KEY_PER_DAY] = c.selection.perDay
        handle[KEY_TARGET] = c.selection.targetDay ?: NO_TARGET
        handle[KEY_REMINDER] = c.reminder.name
        handle[KEY_REMINDER_MINUTE] = c.reminderMinute
    }

    private fun restore(): Choices {
        val default = PlanSelection()
        val step = handle.get<String>(KEY_STEP)?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
        val goal = handle.get<String>(KEY_GOAL)?.let { name -> GoalChoice.entries.firstOrNull { it.name == name } }
        val selection = PlanSelection(
            known = handle.get<IntArray>(KEY_KNOWN)?.toList() ?: default.known,
            preset = if (handle.contains(KEY_PRESET)) {
                handle.get<String>(KEY_PRESET)?.let { name -> PresetChoice.entries.firstOrNull { it.name == name } }
            } else {
                default.preset
            },
            customQueue = handle.get<IntArray>(KEY_CUSTOM)?.toList() ?: default.customQueue,
            mode = handle.get<String>(KEY_MODE)?.let { name -> PaceMode.entries.firstOrNull { it.name == name } } ?: default.mode,
            perDay = handle.get<Int>(KEY_PER_DAY) ?: default.perDay,
            targetDay = handle.get<Int>(KEY_TARGET)?.takeIf { it != NO_TARGET },
        )
        return Choices(
            position = FlowPosition(step ?: OnboardingStep.Language, handle.get<Int>(KEY_PAGE) ?: 0),
            goal = goal,
            selection = selection,
            reminder = handle.get<String>(KEY_REMINDER)?.let { name -> ReminderChoice.entries.firstOrNull { it.name == name } } ?: ReminderChoice.Undecided,
            reminderMinute = handle.get<Int>(KEY_REMINDER_MINUTE) ?: DEFAULT_REMINDER_MINUTE_OF_DAY,
        )
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
        const val EstimateDebounceMillis = 150L
        const val MinPerDay = 1
        const val MaxPerDay = 20
        const val NO_TARGET = Int.MIN_VALUE

        const val KEY_STEP = "step"
        const val KEY_PAGE = "story_page"
        const val KEY_GOAL = "goal"
        const val KEY_KNOWN = "known"
        const val KEY_PRESET = "preset"
        const val KEY_CUSTOM = "custom_queue"
        const val KEY_MODE = "pace_mode"
        const val KEY_PER_DAY = "per_day"
        const val KEY_TARGET = "target_day"
        const val KEY_REMINDER = "reminder"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
