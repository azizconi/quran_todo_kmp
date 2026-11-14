package tj.app.quran_todo.ui.widget

/**
 * Tells the home screen widget "Lesson of the day" that what it shows may have changed (a lesson was finished, the plan or the language
 * changed, the day turned).
 *
 * Android: `LessonWidgetUpdater` (Glance `updateAll`, an alarm at midnight). iOS (S2): `WidgetCenter.shared.reloadAllTimelines()` after
 * the app wrote the new snapshot (`LessonWidgetModel`) to the App Group.
 */
interface WidgetRefresher {
    fun refresh()
}

object NoWidgetRefresher : WidgetRefresher {
    override fun refresh() = Unit
}
