package tj.app.quran_todo.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.home.LessonCardBuilder
import tj.app.quran_todo.ui.home.LessonCardUi

/**
 * When the "Lesson of the day" widget is redrawn (task F5b), with no polling at all:
 *
 * - **A change of what it shows.** [start] (called once by the application) subscribes to the lesson and the streak of the day in the
 *   app process, the same flows the home screen reads. The card is built again on every change and the widget is redrawn only when the
 *   card or the streak differ from the last one - a grade of a passage, a new lesson, a pause, a plan, a day that turned.
 * - **Midnight.** One `AlarmManager` alarm just after midnight redraws the widget for the new day and plans the next one. It does not wake
 *   the phone; a phone that sleeps through midnight redraws at the first wake.
 * - **The clock.** [LessonWidgetSystemReceiver] moves the alarm and redraws after `TIME_SET`, `TIMEZONE_CHANGED`, a reboot, an update of
 *   the app and a change of language.
 * - **The language of the app**, when the person changes it ([refresh]).
 *
 * Nothing runs while no widget is on a home screen: [refresh] looks for widgets first, and the alarm is cancelled with the last one.
 */
internal object LessonWidgetUpdater {
    const val ACTION_MIDNIGHT = "tj.app.quran_todo.widget.MIDNIGHT"

    private const val REQUEST_MIDNIGHT = 9120

    /** The redraw comes a few seconds after midnight, so the clock of the day has surely turned. */
    private const val MIDNIGHT_SLACK_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Starts the subscription in this process (once). Koin must be running. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            try {
                val koin = KoinPlatform.getKoin()
                observe(koin.get<HifzRepository>(), koin.get<EpochDayTicker>().day)
                    .collect { refresh(app) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "widget_observe_failed")
            }
        }
    }

    /** The values that the widget shows change: one value per change, none for a repeat. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(hifz: HifzRepository, day: Flow<Int>): Flow<Pair<LessonCardUi, Int>> =
        day.distinctUntilChanged().flatMapLatest { today ->
            combine(hifz.lesson(today), hifz.streak(today)) { lesson, streak ->
                // The names of the surahs do not matter for "did it change": the card is compared as it is.
                LessonCardBuilder.build(lesson) { "$it" } to streak
            }
        }
            .distinctUntilChanged()
            .catch { error ->
                AppTelemetry.logError(throwable = error, context = "widget_flow_failed")
            }

    /** Redraws every widget of the app; does nothing when there is none. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (GlanceAppWidgetManager(app).getGlanceIds(LessonWidget::class.java).isEmpty()) return
        LessonWidget().updateAll(app)
    }

    /** Plans the alarm for the first seconds of the next day, replacing the earlier one. Returns the planned moment. */
    fun scheduleMidnight(context: Context, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val at = now + EpochDays.millisUntilNextMidnight(Instant.fromEpochMilliseconds(now), zone) + MIDNIGHT_SLACK_MS
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return at
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, pending(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return at)
        return at
    }

    fun cancelMidnight(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pending(context, PendingIntent.FLAG_NO_CREATE) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun pending(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_MIDNIGHT,
        Intent(context, LessonWidgetSystemReceiver::class.java).setAction(ACTION_MIDNIGHT),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Whether any widget of the app is on a home screen. */
    suspend fun hasWidgets(context: Context): Boolean =
        GlanceAppWidgetManager(context.applicationContext).getGlanceIds(LessonWidget::class.java).isNotEmpty()
}

/**
 * Redraws the widget and plans the midnight alarm again after what the alarm does not survive or follow on its own (reboot, update of the
 * app, clock, time zone, language) and when the midnight alarm itself arrives. `exported="false"`: the system delivers its protected
 * broadcasts to it, and no other app can address it.
 */
class LessonWidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                if (LessonWidgetUpdater.hasWidgets(app)) {
                    // The next midnight first, so nothing that goes wrong while drawing can lose the following days.
                    LessonWidgetUpdater.scheduleMidnight(app)
                    LessonWidgetUpdater.refresh(app)
                } else {
                    LessonWidgetUpdater.cancelMidnight(app)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "widget_receiver_failed:$action")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
