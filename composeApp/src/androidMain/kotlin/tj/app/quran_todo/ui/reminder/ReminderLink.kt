package tj.app.quran_todo.ui.reminder

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.MainActivity

/**
 * The link from the notification into the app. The notification opens `MainActivity` with an explicit action and extras
 * (`open=lesson`, or `open=reader` with the surah and the ayah); the activity turns them into a [PendingOpen] for the
 * navigation and removes the extras from its intent, so a recreated activity or a rotation does not open the screen again.
 */
internal object ReminderLink {
    const val ACTION_OPEN = "tj.app.quran_todo.action.OPEN_FROM_REMINDER"
    const val EXTRA_OPEN = "tj.app.quran_todo.extra.REMINDER_OPEN"
    const val EXTRA_SURAH = "tj.app.quran_todo.extra.REMINDER_SURAH"
    const val EXTRA_AYAH = "tj.app.quran_todo.extra.REMINDER_AYAH"

    /** Who made the link: the home screen widget says so (task F5b), the notification leaves it out. */
    const val EXTRA_SOURCE = "tj.app.quran_todo.extra.REMINDER_SOURCE"
    const val SOURCE_WIDGET = "widget"

    const val OPEN_LESSON = "lesson"
    const val OPEN_READER = "reader"

    /** The intent that a tap on the notification (or, with [source] [SOURCE_WIDGET], on the widget) starts. */
    fun intentFor(context: Context, target: ReminderTarget, source: String? = null): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                source?.let { putExtra(EXTRA_SOURCE, it) }
                when (target) {
                    ReminderTarget.Lesson -> putExtra(EXTRA_OPEN, OPEN_LESSON)
                    is ReminderTarget.Reader -> {
                        putExtra(EXTRA_OPEN, OPEN_READER)
                        putExtra(EXTRA_SURAH, target.surah)
                        target.ayah?.let { putExtra(EXTRA_AYAH, it) }
                    }
                }
            }

    /** The target in the extras of [intent], or null when it did not come from a reminder. */
    fun targetFrom(intent: Intent?): ReminderTarget? {
        if (intent == null) return null
        return parse(
            open = intent.getStringExtra(EXTRA_OPEN),
            surah = intent.getIntExtra(EXTRA_SURAH, 0),
            ayah = intent.getIntExtra(EXTRA_AYAH, 0),
        )
    }

    /** Who made the link in [intent] ([SOURCE_WIDGET]), or null for the notification. */
    fun sourceFrom(intent: Intent?): String? = intent?.getStringExtra(EXTRA_SOURCE)

    /** Takes the link out of [intent] so that it is handled once. */
    fun consume(intent: Intent) {
        intent.removeExtra(EXTRA_SOURCE)
        intent.removeExtra(EXTRA_OPEN)
        intent.removeExtra(EXTRA_SURAH)
        intent.removeExtra(EXTRA_AYAH)
    }

    /** The extras as values; a reader link with a surah outside 1..114 is not a link. [ayah] 0 means "not given". */
    fun parse(open: String?, surah: Int, ayah: Int): ReminderTarget? = when (open) {
        OPEN_LESSON -> ReminderTarget.Lesson
        OPEN_READER -> if (surah in 1..114) ReminderTarget.Reader(surah, ayah.takeIf { it > 0 }) else null
        else -> null
    }
}

/** A link waiting for the navigation. [token] differs for every tap, so the same target twice still opens twice. */
internal data class PendingOpen(val target: ReminderTarget, val token: Long)

/** Hands a tap on the notification from `MainActivity` to the Compose navigation, which takes it and clears it. */
internal object ReminderOpenRequests {
    private val _pending = MutableStateFlow<PendingOpen?>(null)
    val pending: StateFlow<PendingOpen?> = _pending.asStateFlow()

    private var nextToken = 0L

    fun post(target: ReminderTarget) {
        _pending.value = PendingOpen(target, ++nextToken)
    }

    /** The navigation handled [request] (or dropped it): later requests stay. */
    fun consume(request: PendingOpen) {
        if (_pending.value == request) _pending.value = null
    }
}
