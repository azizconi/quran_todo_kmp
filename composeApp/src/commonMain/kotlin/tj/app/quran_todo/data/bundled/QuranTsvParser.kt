package tj.app.quran_todo.data.bundled

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.quran.SurahEntity

/** The whole bundled Quran text, as database rows. */
class BundledQuran(
    val edition: EditionEntity,
    val surahs: List<SurahEntity>,
    val ayahs: List<AyahEntity>,
)

/** The bundled file is damaged or is not what the app expects. */
class QuranDataException(message: String) : IllegalStateException(message)

/**
 * Parser of `files/quran/quran-uthmani.tsv`. The format is described in `scripts/build_quran_asset.py`:
 * rows `E` (edition), `S` (surah) and `A` (ayah), fields separated by TAB, `#` starts a comment line.
 *
 * Besides parsing it checks the data (114 surahs, 6236 ayahs numbered 1..6236, the ayah counts of every
 * surah, no byte order mark), so a damaged file fails loudly instead of leaving a half-filled database.
 */
object QuranTsvParser {
    private val BOM: Char = 0xFEFF.toChar() // byte order mark
    private const val AYAH_COLUMNS = 13

    fun parse(text: String): BundledQuran {
        var edition: EditionEntity? = null
        val surahs = ArrayList<SurahEntity>(QuranFacts.SURAH_COUNT)
        val ayahs = ArrayList<AyahEntity>(QuranFacts.AYAH_COUNT)
        var editionId = ""

        var lineStart = 0
        var lineNumber = 0
        while (lineStart < text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            lineNumber++
            // Only a CR that belongs to a CRLF line break is dropped; the text itself is never trimmed.
            val contentEnd = if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
            if (contentEnd > lineStart && text[lineStart] != '#') {
                val columns = text.substring(lineStart, contentEnd).split('\t')
                try {
                    when (columns[0]) {
                        "E" -> {
                            val parsed = parseEdition(columns)
                            edition = parsed
                            editionId = parsed.identifier
                        }
                        "S" -> surahs += parseSurah(columns, editionId)
                        "A" -> ayahs += parseAyah(columns)
                        else -> throw QuranDataException("unknown row type '${columns[0]}'")
                    }
                } catch (error: QuranDataException) {
                    throw QuranDataException("line $lineNumber: ${error.message}")
                } catch (error: RuntimeException) {
                    throw QuranDataException("line $lineNumber: ${error.message ?: error::class.simpleName}")
                }
            }
            lineStart = lineEnd + 1
        }

        return validate(BundledQuran(edition ?: throw QuranDataException("no edition row"), surahs, ayahs))
    }

    private fun parseEdition(columns: List<String>): EditionEntity {
        if (columns.size != 7) throw QuranDataException("edition row has ${columns.size} columns")
        return EditionEntity(
            identifier = columns[1],
            englishName = columns[2],
            format = columns[3],
            language = columns[4],
            name = columns[5],
            type = columns[6],
        )
    }

    private fun parseSurah(columns: List<String>, editionId: String): SurahEntity {
        if (columns.size != 6) throw QuranDataException("surah row has ${columns.size} columns")
        if (editionId.isEmpty()) throw QuranDataException("surah row before the edition row")
        return SurahEntity(
            number = columns[1].toInt(),
            name = columns[2],
            englishName = columns[3],
            englishNameTranslation = columns[4],
            revelationType = columns[5],
            editionId = editionId,
        )
    }

    private fun parseAyah(columns: List<String>): AyahEntity {
        if (columns.size != AYAH_COLUMNS) throw QuranDataException("ayah row has ${columns.size} columns")
        val sajda = if (columns[9].isEmpty()) {
            null
        } else {
            Sajda(
                id = columns[9].toInt(),
                recommended = columns[10] == "1",
                obligatory = columns[11] == "1",
            )
        }
        return AyahEntity(
            number = columns[1].toInt(),
            numberInSurah = columns[3].toInt(),
            juz = columns[4].toInt(),
            hizbQuarter = columns[5].toInt(),
            manzil = columns[6].toInt(),
            page = columns[7].toInt(),
            ruku = columns[8].toInt(),
            sajda = sajda,
            text = columns[12],
            surahNumber = columns[2].toInt(),
        )
    }

    private fun validate(quran: BundledQuran): BundledQuran {
        if (quran.surahs.size != QuranFacts.SURAH_COUNT) {
            throw QuranDataException("expected ${QuranFacts.SURAH_COUNT} surahs, found ${quran.surahs.size}")
        }
        if (quran.ayahs.size != QuranFacts.AYAH_COUNT) {
            throw QuranDataException("expected ${QuranFacts.AYAH_COUNT} ayahs, found ${quran.ayahs.size}")
        }
        quran.surahs.forEachIndexed { index, surah ->
            if (surah.number != index + 1) throw QuranDataException("surah #${index + 1} is numbered ${surah.number}")
        }
        val perSurah = IntArray(QuranFacts.SURAH_COUNT + 1)
        quran.ayahs.forEachIndexed { index, ayah ->
            if (ayah.number != index + 1) throw QuranDataException("ayah #${index + 1} is numbered ${ayah.number}")
            if (ayah.surahNumber !in 1..QuranFacts.SURAH_COUNT) {
                throw QuranDataException("ayah ${ayah.number} belongs to surah ${ayah.surahNumber}")
            }
            perSurah[ayah.surahNumber]++
            if (ayah.numberInSurah != perSurah[ayah.surahNumber]) {
                throw QuranDataException("ayah ${ayah.number} is ${ayah.surahNumber}:${ayah.numberInSurah}, out of order")
            }
            if (ayah.text.isBlank()) throw QuranDataException("ayah ${ayah.number} has no text")
            if (ayah.text.indexOf(BOM) >= 0) throw QuranDataException("ayah ${ayah.number} contains U+FEFF")
        }
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            if (perSurah[surah] != QuranFacts.ayahCount(surah)) {
                throw QuranDataException("surah $surah has ${perSurah[surah]} ayahs, expected ${QuranFacts.ayahCount(surah)}")
            }
        }
        return quran
    }
}
