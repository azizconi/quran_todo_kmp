package tj.app.quran_todo.data.translation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.settings.UserSettingsStorage

/** Which edition is shown under the ayah when the interface is in [language]. */
fun interface TranslationSelection {
    fun editionFor(language: AppLanguage): TranslationEdition

    companion object {
        /** The editions the app has always used: one per language. */
        val Defaults = TranslationSelection { language -> TranslationCatalog.defaultFor(language) }
    }
}

/**
 * The choice of the person, kept per language of the interface: Russian has its own edition, Turkish its own; a change of the
 * language of the interface takes the choice of that language. The edition may be in another language than the interface ("Other
 * languages"): the interface does not change.
 *
 * Stored as one JSON object `{"ru":"ru.abuadel","tr":"qc:210"}`; a language that is not there (or has an edition this app does
 * not know) uses its default edition. [revision] changes with every choice, so the screens that show the translation load it again.
 */
class TranslationChoiceStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : TranslationSelection {
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _revision = MutableStateFlow(0)

    /** Changes every time a choice is made or replaced. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private var cached: Map<String, String>? = null

    private fun stored(): Map<String, String> = cached ?: runCatching {
        json.decodeFromString(serializer, read() ?: "{}")
    }.getOrDefault(emptyMap()).also { cached = it }

    override fun editionFor(language: AppLanguage): TranslationEdition =
        TranslationCatalog.byKey(stored()[language.code]) ?: TranslationCatalog.defaultFor(language)

    /** The person chose [edition] for the interface in [language]. */
    fun select(language: AppLanguage, edition: TranslationEdition) {
        save(stored() + (language.code to edition.key))
    }

    /** What is chosen, by code of the language of the interface: only real choices, not the defaults that nobody picked. */
    fun choices(): Map<String, String> = stored().filter { (code, key) ->
        AppLanguage.entries.any { it.code == code } && TranslationCatalog.byKey(key) != null
    }

    /** Takes the choices of a backup; a language that the file does not mention keeps its choice. */
    fun restore(choices: Map<String, String>) {
        val valid = choices.filter { (code, key) -> AppLanguage.entries.any { it.code == code } && TranslationCatalog.byKey(key) != null }
        if (valid.isNotEmpty()) save(stored() + valid)
    }

    private fun save(next: Map<String, String>) {
        cached = next
        write(json.encodeToString(serializer, next))
        _revision.value += 1
    }
}

/** The choice of this phone. The repository, the reader, the downloads and the search all ask it. */
object TranslationChoice {
    val store = TranslationChoiceStore(
        read = { UserSettingsStorage.getTranslationChoiceJson() },
        write = { UserSettingsStorage.saveTranslationChoiceJson(it) },
    )

    val selection: TranslationSelection get() = store
}

/**
 * The version of every edition that is stored whole (QuranEnc numbers its versions): written when a download ends, read to say
 * "update available" when the app knows a newer one ([TranslationEdition.version]). Stored as `{"qe:tajik_arifi":"1.0.2"}`.
 */
class TranslationVersions(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private fun all(): Map<String, String> = runCatching { json.decodeFromString(serializer, read() ?: "{}") }.getOrDefault(emptyMap())

    fun installed(key: String): String? = all()[key]

    fun record(edition: TranslationEdition) {
        val version = latest(edition) ?: return
        write(json.encodeToString(serializer, all() + (edition.key to version)))
    }

    fun forget(key: String) {
        write(json.encodeToString(serializer, all() - key))
    }

    /**
     * The newest version of [edition]: the one the last check of the list of QuranEnc saw, else the one of the catalog (the fallback).
     * Kept in the same JSON as `remote:<key>`.
     */
    fun latest(edition: TranslationEdition): String? = all()["$REMOTE${edition.key}"] ?: edition.version

    /** A stored edition older than the newest known version (an edition stored before versions were kept has none: it is not flagged). */
    fun updateAvailable(edition: TranslationEdition): Boolean {
        val known = latest(edition) ?: return false
        val have = installed(edition.key) ?: return false
        return have != known
    }

    /** True when the list of versions was not asked for during the last [intervalMs] (7 days). */
    fun checkIsDue(now: Long, intervalMs: Long = CHECK_INTERVAL_MS): Boolean {
        val at = all()[CHECKED_AT]?.toLongOrNull() ?: return true
        return now - at !in 0 until intervalMs
    }

    /** Keeps the versions that the list of QuranEnc named (by edition key) and the time of the check. */
    fun recordRemote(versionsByEditionKey: Map<String, String>, now: Long) {
        val next = all().filterKeys { !it.startsWith(REMOTE) } +
            versionsByEditionKey.mapKeys { "$REMOTE${it.key}" } + (CHECKED_AT to now.toString())
        write(json.encodeToString(serializer, next))
    }

    companion object {
        private const val REMOTE = "remote:"
        private const val CHECKED_AT = "checkedAt"
        const val CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** `GET /api/v1/translations/list`: the edition keys and versions of QuranEnc, as `qe:<key>` to version. */
        fun parseList(body: String): Map<String, String> {
            val parser = Json { ignoreUnknownKeys = true }
            val list = parser.parseToJsonElement(body).jsonObject["translations"]?.jsonArray ?: return emptyMap()
            return buildMap {
                list.forEach { item ->
                    val obj = item.jsonObject
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull
                    val version = obj["version"]?.jsonPrimitive?.contentOrNull
                    if (key != null && version != null) put("qe:$key", version)
                }
            }
        }

        /** The versions of this phone. */
        val shared = TranslationVersions(
            read = { UserSettingsStorage.getTranslationVersionsJson() },
            write = { UserSettingsStorage.saveTranslationVersionsJson(it) },
        )
    }
}
