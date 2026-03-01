package tj.app.quran_todo.common.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReviewMemoryState(
    val repetitions: Int = 0,
    val intervalDays: Int = 1,
    val easiness: Float = 2.5f,
)

@Serializable
private data class ReviewMemoryStateSnapshot(
    val byAyah: Map<Int, ReviewMemoryState> = emptyMap(),
)

object ReviewStateStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cached: MutableMap<Int, ReviewMemoryState>? = null

    fun get(ayahNumber: Int): ReviewMemoryState? = ensureLoaded()[ayahNumber]

    fun put(ayahNumber: Int, state: ReviewMemoryState) {
        val map = ensureLoaded()
        map[ayahNumber] = state
        persist(map)
    }

    fun remove(ayahNumber: Int) {
        val map = ensureLoaded()
        if (map.remove(ayahNumber) != null) {
            persist(map)
        }
    }

    fun removeAll(ayahNumbers: Collection<Int>) {
        if (ayahNumbers.isEmpty()) return
        val map = ensureLoaded()
        var changed = false
        ayahNumbers.forEach { ayah ->
            if (map.remove(ayah) != null) changed = true
        }
        if (changed) persist(map)
    }

    fun clear() {
        cached = mutableMapOf()
        persist(cached!!)
    }

    private fun ensureLoaded(): MutableMap<Int, ReviewMemoryState> {
        cached?.let { return it }
        val loaded = runCatching {
            val raw = UserSettingsStorage.getReviewStateJson() ?: return@runCatching mutableMapOf()
            json.decodeFromString<ReviewMemoryStateSnapshot>(raw).byAyah.toMutableMap()
        }.getOrElse { mutableMapOf() }
        cached = loaded
        return loaded
    }

    private fun persist(map: Map<Int, ReviewMemoryState>) {
        runCatching {
            val raw = json.encodeToString(ReviewMemoryStateSnapshot(byAyah = map))
            UserSettingsStorage.saveReviewStateJson(raw)
        }
    }
}
