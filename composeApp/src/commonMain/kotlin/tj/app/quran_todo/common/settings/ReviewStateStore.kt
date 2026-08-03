package tj.app.quran_todo.common.settings

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val accessMutex = Mutex()
    private val access = ExclusiveAccess()
    private var cached: MutableMap<Int, ReviewMemoryState>? = null

    class ExclusiveAccess internal constructor() {
        fun get(ayahNumber: Int): ReviewMemoryState? =
            ReviewStateStore.ensureLoaded()[ayahNumber]

        fun put(ayahNumber: Int, state: ReviewMemoryState) {
            val map = ReviewStateStore.ensureLoaded()
            map[ayahNumber] = state
            ReviewStateStore.persist(map)
        }

        fun putAll(states: Map<Int, ReviewMemoryState>) {
            if (states.isEmpty()) return
            val map = ReviewStateStore.ensureLoaded()
            map.putAll(states)
            ReviewStateStore.persist(map)
        }

        fun remove(ayahNumber: Int) {
            val map = ReviewStateStore.ensureLoaded()
            if (map.remove(ayahNumber) != null) {
                ReviewStateStore.persist(map)
            }
        }

        fun removeAll(ayahNumbers: Collection<Int>) {
            if (ayahNumbers.isEmpty()) return
            val map = ReviewStateStore.ensureLoaded()
            var changed = false
            ayahNumbers.forEach { ayah ->
                if (map.remove(ayah) != null) changed = true
            }
            if (changed) ReviewStateStore.persist(map)
        }

        fun replaceAll(states: Map<Int, ReviewMemoryState>) {
            val replacement = states.toMutableMap()
            ReviewStateStore.cached = replacement
            ReviewStateStore.persist(replacement)
        }
    }

    suspend fun <T> withExclusiveAccess(
        block: suspend ExclusiveAccess.() -> T,
    ): T = accessMutex.withLock {
        access.block()
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
