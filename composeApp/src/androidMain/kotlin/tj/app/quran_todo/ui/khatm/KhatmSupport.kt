package tj.app.quran_todo.ui.khatm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.khatm.HijriConverter
import tj.app.quran_todo.khatm.HijriDate

/**
 * The Hijri date by the Umm al-Qura calendar of `android.icu` (API 24+): no table of dates, the library computes it. The epoch day is a calendar
 * day of the phone's time zone, so it is read at noon of that civil date in GMT.
 */
internal object IcuHijriConverter : HijriConverter {
    override fun toHijri(epochDay: Int): HijriDate {
        val calendar = Calendar.getInstance(TimeZone.GMT_ZONE, ULocale("en@calendar=islamic-umalqura"))
        calendar.timeInMillis = epochDay * MILLIS_PER_DAY + MILLIS_PER_DAY / 2
        return HijriDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}

/**
 * "Today" for the reading plan and Ramadan, with a hidden override for the debug build: a debug `BroadcastReceiver` (`src/androidDebug`,
 * `adb shell am broadcast -a tj.app.quran_todo.debug.SET_DATE ...`) stores a day here to see Ramadan on an emulator. A release build never reads
 * the stored value (`FLAG_DEBUGGABLE` is off) and has no receiver, so the override does not exist there.
 */
internal object DebugToday {
    private const val PREFS = "debug_today"
    private const val KEY_EPOCH_DAY = "epoch_day"

    private val _override = MutableStateFlow<Int?>(null)

    /** The day to use instead of the real one; null (always, in release) means the real day. */
    val override: StateFlow<Int?> = _override.asStateFlow()

    private var loaded = false

    fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Reads the stored override once per process (debug builds only). */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        if (!isDebuggable(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _override.value = if (prefs.contains(KEY_EPOCH_DAY)) prefs.getInt(KEY_EPOCH_DAY, 0) else null
    }

    /** Sets or, with null, clears the override. Does nothing in a release build. */
    fun set(context: Context, epochDay: Int?) {
        if (!isDebuggable(context)) return
        load(context)
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (epochDay == null) edit.remove(KEY_EPOCH_DAY) else edit.putInt(KEY_EPOCH_DAY, epochDay)
        edit.apply()
        _override.value = epochDay
    }
}
