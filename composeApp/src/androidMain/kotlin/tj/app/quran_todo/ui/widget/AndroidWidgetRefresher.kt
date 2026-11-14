package tj.app.quran_todo.ui.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [WidgetRefresher] of the Glance widget: redraws every placed "Lesson of the day" widget. */
class AndroidWidgetRefresher(private val context: Context, private val scope: CoroutineScope) : WidgetRefresher {
    override fun refresh() {
        scope.launch { runCatching { LessonWidgetUpdater.refresh(context) } }
    }
}
