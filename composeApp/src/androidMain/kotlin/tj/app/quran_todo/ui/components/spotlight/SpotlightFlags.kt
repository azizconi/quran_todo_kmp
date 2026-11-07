package tj.app.quran_todo.ui.components.spotlight

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where the "tour seen" marks are kept. */
interface SpotlightStore {
    /** The ids of the tours that were shown. */
    fun read(): Set<String>

    fun add(tourId: String)

    fun clear()
}

/** [SpotlightStore] in its own SharedPreferences file (`quran_hifz_spotlights`): not [tj.app.quran_todo.ui.state.UiPrefs], and not part of the B5 backup file. */
class SharedPreferencesSpotlightStore(context: Context) : SpotlightStore {
    private val prefs = context.applicationContext.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    override fun read(): Set<String> =
        prefs.all.entries.filter { it.key.startsWith(KeyPrefix) && it.value == true }.map { it.key.removePrefix(KeyPrefix) }.toSet()

    override fun add(tourId: String) {
        prefs.edit().putBoolean(KeyPrefix + tourId, true).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FileName = "quran_hifz_spotlights"
        private const val KeyPrefix = "seen_"
    }
}

/**
 * Which tours were shown. A tour is marked when it ends (finished or skipped), never when it starts: if the process dies in the
 * middle of a tour, it shows again. [resetAll] ("Show tips again" in the settings) forgets all of them.
 */
class SpotlightFlags(private val store: SpotlightStore) {
    private var seen: Set<String> by mutableStateOf(store.read())

    /** Read inside composition or a snapshot flow, it follows [markSeen] and [resetAll]. */
    fun isSeen(tour: SpotlightTour): Boolean = tour.id in seen

    fun markSeen(tour: SpotlightTour) {
        if (tour.id in seen) return
        store.add(tour.id)
        seen = seen + tour.id
    }

    fun resetAll() {
        store.clear()
        seen = emptySet()
    }
}
