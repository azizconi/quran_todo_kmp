package tj.app.quran_todo.common.utils

fun ayahStableId(surahNumber: Int, numberInSurah: Int): Long {
    return surahNumber.toLong() * 1000L + numberInSurah.toLong()
}
