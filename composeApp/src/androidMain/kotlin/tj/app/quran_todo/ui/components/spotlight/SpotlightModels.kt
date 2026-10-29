package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The shape of the cutout around a target. */
@Immutable
sealed interface SpotlightShape {
    /** A circle; around a wide target a pill (the corner radius is half of the shorter side). */
    data object Circle : SpotlightShape

    /** A rounded rectangle. */
    @Immutable
    data class Rounded(val radius: Dp = 16.dp) : SpotlightShape
}

/** Where the tip card sits relative to the cutout. */
enum class SpotlightPosition {
    /** On the side of the cutout that has more room. */
    Auto,
    Above,
    Below,
}

/** The screen a tour belongs to: a tour starts only while this screen is the one on top. */
enum class SpotlightScreen(val route: String) {
    Home("home"),
    Reader("reader"),
    Lesson("lesson"),
}

/** The text of one step; the words live in `SpotlightCopy`. */
enum class SpotlightTip {
    HomeLesson,
    HomeContinue,
    HomeSurah,
    HomeProgress,
    ReaderTap,
    ReaderHold,
    ReaderPlayer,
    ReaderText,
    ReaderCheck,
    LessonSteps,
    LessonPeek,
    LessonGrade,
    PlayerRepeat,
    PlayerPause,
}

/**
 * One step of a tour: the target it points at (registered with `Modifier.spotlightTarget`), the words, and
 * optionally a [shape] that overrides the one the target registered and a fixed [position] of the card.
 */
@Immutable
data class SpotlightStep(
    val targetId: String,
    val tip: SpotlightTip,
    val shape: SpotlightShape? = null,
    val position: SpotlightPosition = SpotlightPosition.Auto,
)

/**
 * A list of steps shown once. [id] is the key of the "seen" flag and the `tour` value of the analytics events,
 * [screen] the screen that asks for it.
 */
@Immutable
data class SpotlightTour(
    val id: String,
    val screen: SpotlightScreen,
    val steps: List<SpotlightStep>,
)

/** Ids of the targets the app registers. */
object SpotlightTargets {
    const val HomeLesson = "home.lesson"
    const val HomeContinue = "home.continue"
    const val HomeSurahRow = "home.surah"
    const val HomeProgress = "home.progress"

    const val ReaderAyah = "reader.ayah"
    const val ReaderDock = "reader.dock"
    const val ReaderText = "reader.text"
    const val ReaderCheck = "reader.check"

    const val LessonSteps = "lesson.steps"
    const val LessonHiddenAyah = "lesson.hidden"
    const val LessonGrade = "lesson.grade"

    const val PlayerRepeat = "player.repeat"
    const val PlayerPause = "player.pause"
}

/** The tours of the app (docs/redesign/PRODUCT_PLAN.md, 5.2). */
object SpotlightTours {
    val Home = SpotlightTour(
        id = "home",
        screen = SpotlightScreen.Home,
        steps = listOf(
            SpotlightStep(SpotlightTargets.HomeLesson, SpotlightTip.HomeLesson),
            SpotlightStep(SpotlightTargets.HomeContinue, SpotlightTip.HomeContinue),
            SpotlightStep(SpotlightTargets.HomeSurahRow, SpotlightTip.HomeSurah),
            SpotlightStep(SpotlightTargets.HomeProgress, SpotlightTip.HomeProgress),
        ),
    )

    /** Both ayah steps point at the same ayah row: the reader registers one row, the first one fully on screen. */
    val Reader = SpotlightTour(
        id = "reader",
        screen = SpotlightScreen.Reader,
        steps = listOf(
            SpotlightStep(SpotlightTargets.ReaderAyah, SpotlightTip.ReaderTap),
            SpotlightStep(SpotlightTargets.ReaderAyah, SpotlightTip.ReaderHold),
            SpotlightStep(SpotlightTargets.ReaderDock, SpotlightTip.ReaderPlayer),
            SpotlightStep(SpotlightTargets.ReaderText, SpotlightTip.ReaderText),
            SpotlightStep(SpotlightTargets.ReaderCheck, SpotlightTip.ReaderCheck),
        ),
    )

    /** The header of the lesson: the three steps. The recall steps are separate tours (their targets come later). */
    val LessonHeader = SpotlightTour(
        id = "lesson",
        screen = SpotlightScreen.Lesson,
        steps = listOf(SpotlightStep(SpotlightTargets.LessonSteps, SpotlightTip.LessonSteps)),
    )

    /** The first hidden ayah of "Recall". */
    val LessonPeek = SpotlightTour(
        id = "lesson_peek",
        screen = SpotlightScreen.Lesson,
        steps = listOf(SpotlightStep(SpotlightTargets.LessonHiddenAyah, SpotlightTip.LessonPeek)),
    )

    /** "I remember" / "Hard", once they appear. */
    val LessonGrade = SpotlightTour(
        id = "lesson_grade",
        screen = SpotlightScreen.Lesson,
        steps = listOf(SpotlightStep(SpotlightTargets.LessonGrade, SpotlightTip.LessonGrade)),
    )

    /** The first time the player dock is pulled up. */
    val Player = SpotlightTour(
        id = "player",
        screen = SpotlightScreen.Reader,
        steps = listOf(
            SpotlightStep(SpotlightTargets.PlayerRepeat, SpotlightTip.PlayerRepeat),
            SpotlightStep(SpotlightTargets.PlayerPause, SpotlightTip.PlayerPause),
        ),
    )

    val all: List<SpotlightTour> = listOf(Home, Reader, LessonHeader, LessonPeek, LessonGrade, Player)
}
