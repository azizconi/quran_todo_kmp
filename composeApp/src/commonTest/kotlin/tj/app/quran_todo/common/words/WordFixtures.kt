package tj.app.quran_todo.common.words
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


/** Real data (api.quran.com `verses/by_chapter/{n}?words=true`, 2026-10-31): the words of surah 1 and of 2:1-10, and of the three ayahs whose words do not line up. */
object WordFixtures {
    data class W(val meaning: String, val transliteration: String)

    val surah1: Map<Int, List<W>> = mapOf(
        1 to listOf(W("In (the) name", "bis'mi"), W("(of) Allah", "l-lahi"), W("the Most Gracious", "l-raḥmāni"), W("the Most Merciful", "l-raḥīmi")),
        2 to listOf(W("All praises and thanks", "al-ḥamdu"), W("(be) to Allah", "lillahi"), W("the Lord", "rabbi"), W("of the universe", "l-ʿālamīna")),
        3 to listOf(W("The Most Gracious", "al-raḥmāni"), W("the Most Merciful", "l-raḥīmi")),
        4 to listOf(W("(The) Master", "māliki"), W("(of the) Day", "yawmi"), W("(of the) Judgment", "l-dīni")),
        5 to listOf(W("You Alone", "iyyāka"), W("we worship", "naʿbudu"), W("and You Alone", "wa-iyyāka"), W("we ask for help", "nastaʿīnu")),
        6 to listOf(W("Guide us", "ih'dinā"), W("(to) the path", "l-ṣirāṭa"), W("the straight", "l-mus'taqīma")),
        7 to listOf(W("(The) path", "ṣirāṭa"), W("(of) those", "alladhīna"), W("You have bestowed (Your) Favors", "anʿamta"), W("on them", "ʿalayhim"), W("not (of)", "ghayri"), W("those who earned (Your) wrath", "l-maghḍūbi"), W("on themselves", "ʿalayhim"), W("and not", "walā"), W("(of) those who go astray", "l-ḍālīna")),
    )

    val surah2First10: Map<Int, List<W>> = mapOf(
        1 to listOf(W("Alif Laam Meem", "alif-lam-meem")),
        2 to listOf(W("That", "dhālika"), W("(is) the book", "l-kitābu"), W("no", "lā"), W("doubt", "rayba"), W("in it", "fīhi"), W("a Guidance", "hudan"), W("for the God-conscious", "lil'muttaqīna")),
        3 to listOf(W("Those who", "alladhīna"), W("believe", "yu'minūna"), W("in the unseen", "bil-ghaybi"), W("and establish", "wayuqīmūna"), W("the prayer", "l-ṣalata"), W("and out of what", "wamimmā"), W("We have provided them", "razaqnāhum"), W("they spend", "yunfiqūna")),
        4 to listOf(W("And those who", "wa-alladhīna"), W("believe", "yu'minūna"), W("in what", "bimā"), W("(is) sent down", "unzila"), W("to you", "ilayka"), W("and what", "wamā"), W("was sent down", "unzila"), W("from", "min"), W("before you", "qablika"), W("and in the Hereafter", "wabil-ākhirati"), W("they", "hum"), W("firmly believe", "yūqinūna")),
        5 to listOf(W("Those", "ulāika"), W("(are) on", "ʿalā"), W("Guidance", "hudan"), W("from", "min"), W("their Lord", "rabbihim"), W("and those ", "wa-ulāika"), W("they", "humu"), W("(are) the successful ones", "l-muf'liḥūna")),
        6 to listOf(W("Indeed", "inna"), W("those who", "alladhīna"), W("disbelieve[d]", "kafarū"), W("(it) is same", "sawāon"), W("to them", "ʿalayhim"), W("whether you warn them", "a-andhartahum"), W("or", "am"), W("not", "lam"), W("you warn them", "tundhir'hum"), W("not", "lā"), W("they believe", "yu'minūna")),
        7 to listOf(W("Has set a seal", "khatama"), W("Allah", "l-lahu"), W("on", "ʿalā"), W("their hearts", "qulūbihim"), W("and on", "waʿalā"), W("their hearing", "samʿihim"), W("and on", "waʿalā"), W("their vision", "abṣārihim"), W("(is) a veil", "ghishāwatun"), W("And for them", "walahum"), W("(is) a punishment", "ʿadhābun"), W("great", "ʿaẓīmun")),
        8 to listOf(W("And of", "wamina"), W("the people", "l-nāsi"), W("(are some) who", "man"), W("say", "yaqūlu"), W("We believed", "āmannā"), W("in Allah", "bil-lahi"), W("and in the Day", "wabil-yawmi"), W("[the] Last", "l-ākhiri"), W("but not", "wamā"), W("they", "hum"), W("(are) believers (at all)", "bimu'minīna")),
        9 to listOf(W("They seek to deceive", "yukhādiʿūna"), W("Allah", "l-laha"), W("and those who", "wa-alladhīna"), W("believe[d]", "āmanū"), W("and not", "wamā"), W("they deceive", "yakhdaʿūna"), W("except", "illā"), W("themselves", "anfusahum"), W("and not", "wamā"), W("they realize (it)", "yashʿurūna")),
        10 to listOf(W("In", "fī"), W("their hearts", "qulūbihim"), W("(is) a disease", "maraḍun"), W("so has increased them", "fazādahumu"), W("Allah", "l-lahu"), W("(in) disease", "maraḍan"), W("and for them", "walahum"), W("(is) a punishment", "ʿadhābun"), W("painful", "alīmun"), W("because", "bimā"), W("they used to", "kānū"), W("[they] lie", "yakdhibūna")),
    )

    /** The words of quran.com for 10:1, 13:1 and 50:34 (their number is not the number of words of our text). */
    val special: Map<Pair<Int, Int>, List<W>> = mapOf(
        10 to 1 to listOf(W("Alif Lam Ra", "alif-lam-ra"), W("These", "til'ka"), W("(are the) verses", "āyātu"), W("(of) the Book", "l-kitābi"), W("the wise", "l-ḥakīmi")),
        13 to 1 to listOf(W("Alif Laam Mim Ra", "alif-lam-meem-ra"), W("These", "til'ka"), W("(are) the Verses", "āyātu"), W("(of) the Book", "l-kitābi"), W("And that which", "wa-alladhī"), W("has been revealed", "unzila"), W("to you", "ilayka"), W("from", "min"), W("your Lord", "rabbika"), W("(is) the truth", "l-ḥaqu"), W("but", "walākinna"), W("most", "akthara"), W("(of) the mankind", "l-nāsi"), W("(do) not", "lā"), W("believe", "yu'minūna")),
        50 to 34 to listOf(W("Enter it", "ud'khulūhā"), W("in peace", "bisalāmin"), W("That", "dhālika"), W("(is) a Day", "yawmu"), W("(of) Eternity", "l-khulūdi")),
    )

    /** Builds the answer of `verses/by_chapter` the way quran.com writes it: every word, and the sign that closes the ayah. */
    fun answer(surah: Int, ayahs: Map<Int, List<W>>, next: Int? = null, total: Int? = null): String = buildJsonObject {
        put("verses", buildJsonArray {
            for ((ayah, words) in ayahs) add(buildJsonObject {
                put("id", ayah)
                put("verse_key", "$surah:$ayah")
                put("words", buildJsonArray {
                    words.forEachIndexed { index, w ->
                        add(word(index + 1, "word", w.meaning, w.transliteration))
                    }
                    add(word(words.size + 1, "end", "($ayah)", null))
                })
            })
        })
        put("pagination", buildJsonObject {
            put("per_page", 50)
            put("next_page", next?.let { JsonPrimitive(it) } ?: JsonNull)
            put("total_records", total ?: ayahs.size)
        })
    }.toString()

    private fun word(position: Int, type: String, meaning: String, transliteration: String?) = buildJsonObject {
        put("id", position)
        put("position", position)
        put("char_type_name", type)
        put("translation", buildJsonObject { put("text", meaning); put("language_name", "english") })
        put("transliteration", buildJsonObject { put("text", transliteration?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull); put("language_name", "english") })
    }
}
