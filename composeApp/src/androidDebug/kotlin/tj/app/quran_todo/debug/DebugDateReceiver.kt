package tj.app.quran_todo.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.ui.khatm.DebugToday

/**
 * Debug builds only (this file lives in `src/androidDebug`, so a release build has neither the class nor its manifest entry): moves "today" of the
 * reading plan and Ramadan, to see Ramadan on an emulator.
 *
 * ```
 * adb shell am broadcast -n tj.app.quran_todo/.debug.DebugDateReceiver -a tj.app.quran_todo.debug.SET_DATE --es date 2027-02-08
 * adb shell am broadcast -n tj.app.quran_todo/.debug.DebugDateReceiver -a tj.app.quran_todo.debug.SET_DATE --es date clear
 * ```
 */
class DebugDateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val text = intent.getStringExtra("date")?.trim().orEmpty()
        val day = if (text.isEmpty() || text.equals("clear", ignoreCase = true)) {
            null
        } else {
            runCatching { LocalDate.parse(text).toEpochDays() }.getOrNull() ?: run {
                Log.w(TAG, "SET_DATE: '$text' is not a date (yyyy-MM-dd or clear)")
                return
            }
        }
        DebugToday.set(context.applicationContext, day)
        Log.i(TAG, "SET_DATE: today of the reading plan is " + (day?.let { LocalDate.fromEpochDays(it).toString() } ?: "the real day"))
    }

    private companion object {
        const val ACTION = "tj.app.quran_todo.debug.SET_DATE"
        const val TAG = "DebugDate"
    }
}
