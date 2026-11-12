package tj.app.quran_todo.ui.components.hints

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where the "hint used" marks are kept. */
interface HintStore {
    /** The ids of the hints that were used or dismissed. */
    fun read(): Set<String>

    fun add(hintId: String)

    fun clear()
}

/**
 * [HintStore] in the SharedPreferences file the marks of the old tours lived in (`quran_hifz_spotlights`): not
 * [tj.app.quran_todo.ui.state.UiPrefs], and not part of the backup file. [clear] empties the whole file.
 */
class SharedPreferencesHintStore(context: Context) : HintStore {
    private val prefs = context.applicationContext.getSharedPreferences(FileName, Context.MODE_PRIVATE)

    override fun read(): Set<String> =
        prefs.all.entries.filter { it.key.startsWith(KeyPrefix) && it.value == true }.map { it.key.removePrefix(KeyPrefix) }.toSet()

    override fun add(hintId: String) {
        prefs.edit().putBoolean(KeyPrefix + hintId, true).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FileName = "quran_hifz_spotlights"
        private const val KeyPrefix = "hint_"
    }
}

/** [HintStore] that forgets everything on exit: for tests and for screens shown without the app root (previews, the catalog). */
class InMemoryHintStore(initial: Set<String> = emptySet()) : HintStore {
    private val ids = initial.toMutableSet()

    override fun read(): Set<String> = ids.toSet()

    override fun add(hintId: String) {
        ids += hintId
    }

    override fun clear() {
        ids.clear()
    }
}

/**
 * Which inline hints are done with: the person did the thing once ([markUsed]) or dismissed the hint. A hint that is used never comes
 * back, until [resetAll] ("Show tips again" in the settings).
 */
class HintFlags(private val store: HintStore) {
    private var used: Set<String> by mutableStateOf(store.read())

    /** Read inside composition, it follows [markUsed] and [resetAll]. */
    fun isUsed(id: String): Boolean = id in used

    fun markUsed(id: String) {
        if (id in used) return
        store.add(id)
        used = used + id
    }

    fun resetAll() {
        store.clear()
        used = emptySet()
    }
}

/** The ids of the inline hints. */
object Hints {
    const val ReaderHold = "reader_hold"
    const val LessonGrade = "lesson_grade"
}
