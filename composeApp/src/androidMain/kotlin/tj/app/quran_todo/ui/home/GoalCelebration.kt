package tj.app.quran_todo.ui.home

/** The daily goal is met: [done] ayahs worked on today reach (or pass) the [goal]. */
internal fun isGoalReached(done: Int, goal: Int): Boolean = goal > 0 && done >= goal

/**
 * The small burst for reaching the goal plays once per day: the first time the goal is seen reached
 * ([celebratedDay] is the last epoch day it played on). Seeing "7 of 5" again later the same day, or
 * after the app restarted, does not replay it.
 */
internal fun shouldCelebrateGoal(done: Int, goal: Int, celebratedDay: Int, today: Int): Boolean =
    isGoalReached(done, goal) && celebratedDay != today
