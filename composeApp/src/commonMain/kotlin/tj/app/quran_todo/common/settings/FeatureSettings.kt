package tj.app.quran_todo.common.settings

data class AyahKey(
    val surahNumber: Int,
    val ayahNumber: Int,
)

fun ayahKeyString(surahNumber: Int, ayahNumber: Int): String =
    "$surahNumber:$ayahNumber"

fun parseAyahKey(value: String): AyahKey? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val surah = parts[0].toIntOrNull() ?: return null
    val ayah = parts[1].toIntOrNull() ?: return null
    return AyahKey(surah, ayah)
}

fun weakAyahKeySet(): MutableSet<String> =
    UserSettingsStorage.getWeakAyahKeys()?.toMutableSet() ?: mutableSetOf()

fun addWeakAyah(surahNumber: Int, ayahNumber: Int) {
    val set = weakAyahKeySet()
    set += ayahKeyString(surahNumber, ayahNumber)
    UserSettingsStorage.saveWeakAyahKeys(set)
}

fun removeWeakAyah(surahNumber: Int, ayahNumber: Int) {
    val set = weakAyahKeySet()
    set -= ayahKeyString(surahNumber, ayahNumber)
    UserSettingsStorage.saveWeakAyahKeys(set)
}

fun isWeakAyah(surahNumber: Int, ayahNumber: Int): Boolean =
    weakAyahKeySet().contains(ayahKeyString(surahNumber, ayahNumber))
