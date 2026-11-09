package tj.app.quran_todo.ui.store

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import java.util.concurrent.TimeUnit
import tj.app.quran_todo.common.analytics.AppTelemetry

/**
 * The Google Play in-app review card (the stars and the review text in a sheet over the app).
 *
 * It is asked for at a happy moment: on the summary of a finished lesson. Only after the person has finished lessons on
 * [MinLessonDays] different days, at least [MinDaysSinceInstall] days after the install, and not again for [AskAgainDays] days.
 * Play has its own quota on top of this and may show nothing at all, so the app never tells the person a card is coming and
 * never waits for it. The "Rate the app" row of the settings keeps opening the store listing, which always works.
 */
object InAppReview {
    private const val TAG = "InAppReview"
    private const val PREFS_NAME = "store_prompts"
    private const val KEY_LESSON_DAYS = "review_lesson_days"
    private const val KEY_LAST_LESSON_DAY = "review_last_lesson_day"
    private const val KEY_LAST_ASKED_AT = "review_last_asked_at"

    private const val MinLessonDays = 3
    private const val MinDaysSinceInstall = 3L
    private const val AskAgainDays = 90L

    /**
     * Counts the finished lesson (once per calendar day) and, when the conditions above hold, asks Play for the review card.
     * Safe to call from a coroutine of the lesson screen: every failure is swallowed.
     */
    suspend fun onLessonFinished(context: Context) {
        val activity = context.findActivity() ?: return
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = TimeUnit.MILLISECONDS.toDays(now)

        var lessonDays = prefs.getInt(KEY_LESSON_DAYS, 0)
        if (prefs.getLong(KEY_LAST_LESSON_DAY, -1L) != today) {
            lessonDays += 1
            prefs.edit().putInt(KEY_LESSON_DAYS, lessonDays).putLong(KEY_LAST_LESSON_DAY, today).apply()
        }
        if (lessonDays < MinLessonDays) return
        if (daysSince(installTime(activity), now) < MinDaysSinceInstall) return
        val lastAsked = prefs.getLong(KEY_LAST_ASKED_AT, 0L)
        if (lastAsked > 0L && daysSince(lastAsked, now) < AskAgainDays) return

        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            if (activity.isFinishing || activity.isDestroyed) return
            manager.launchReview(activity, info)
            prefs.edit().putLong(KEY_LAST_ASKED_AT, now).apply()
            AppTelemetry.logEvent(name = "review_prompt_requested", params = mapOf("lesson_days" to lessonDays.toString()))
        } catch (error: Exception) {
            // No Play Store, no Play services, an app not installed from Play: the card simply does not appear.
            Log.w(TAG, "The review card could not be requested", error)
        }
    }

    private fun installTime(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (_: Exception) {
        0L
    }

    private fun daysSince(timeMillis: Long, now: Long): Long = TimeUnit.MILLISECONDS.toDays(now - timeMillis)
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
