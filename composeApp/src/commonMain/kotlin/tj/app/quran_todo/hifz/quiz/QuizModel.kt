package tj.app.quran_todo.hifz.quiz

import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.similar.TellApartPair

/**
 * The five kinds of the questions of the quiz "Check" (task F2b). A quiz is a help, not a lesson: nothing of it goes to the review
 * log, and the only trace is that a missed ayah is marked difficult (`HifzRepository.markHard`).
 */
enum class QuizKind {
    /** The first third of the words of an ayah is shown, the rest is hidden: continue it. */
    CONTINUE,

    /** An ayah is shown: what comes next? */
    NEXT,

    /** The name of a surah is shown: how does it begin? */
    START,

    /** Three or four ayahs that follow each other, shuffled: put them in order. */
    ORDER,

    /** An ayah is shown: which surah is it from? One of four. */
    WHERE,

    /** Two similar places (mutashabihat) begin the same way: recall each, then see what differs (task F6). */
    TELL_APART,
}

/**
 * One question. Numbers of ayahs are numbers **in the surah**. [ayahsUsed] are the ayahs the question shows or gives away: one ayah
 * is in at most one question of a quiz.
 */
sealed interface QuizQuestion {
    val kind: QuizKind
    val surah: Int
    val ayahsUsed: Set<Int>

    /** The ayah the reader opens for this question ("Open in the reader"). */
    val focusAyah: Int

    /**
     * The ayahs that become difficult when the question is missed: the ayahs of the answer. Empty for [WhereQuestion]: a wrong surah is
     * a mistake about the place, not about the text. An [OrderQuestion] marks only the ayahs that were put in a wrong place
     * ([QuizAnswers.order]).
     */
    val hardOnMiss: Set<Int>

    /** Every ayah the question uses, with its surah (a question of [TellApartQuestion] has two places in two surahs). */
    val places: Set<AyahKey> get() = ayahsUsed.mapTo(LinkedHashSet()) { AyahKey(surah, it) }

    /** Difficult ayahs of a miss that are NOT in [surah] (only [TellApartQuestion] has some). */
    val otherHardOnMiss: Set<AyahKey> get() = emptySet()
}

/** The first [shownWords] of [totalWords] words of the ayah are shown (a third of them, rounded up), the rest is hidden. */
data class ContinueQuestion(
    override val surah: Int,
    val ayah: Int,
    val shownWords: Int,
    val totalWords: Int,
) : QuizQuestion {
    override val kind: QuizKind get() = QuizKind.CONTINUE
    override val ayahsUsed: Set<Int> get() = setOf(ayah)
    override val focusAyah: Int get() = ayah
    override val hardOnMiss: Set<Int> get() = setOf(ayah)
}

/** [ayah] is shown, the answer is the next ayah of the same surah (it is covered too). */
data class NextQuestion(override val surah: Int, val ayah: Int) : QuizQuestion {
    val answerAyah: Int get() = ayah + 1
    override val kind: QuizKind get() = QuizKind.NEXT
    override val ayahsUsed: Set<Int> get() = setOf(ayah, answerAyah)
    override val focusAyah: Int get() = answerAyah
    override val hardOnMiss: Set<Int> get() = setOf(answerAyah)
}

/** The name of the surah is shown, the answer is its first ayah (without the Bismillah, as the reader draws it). */
data class StartQuestion(override val surah: Int) : QuizQuestion {
    override val kind: QuizKind get() = QuizKind.START
    override val ayahsUsed: Set<Int> get() = setOf(FIRST_AYAH)
    override val focusAyah: Int get() = FIRST_AYAH
    override val hardOnMiss: Set<Int> get() = setOf(FIRST_AYAH)

    private companion object {
        const val FIRST_AYAH = 1
    }
}

/** [inOrder] are 3 or 4 ayahs that follow each other; [shuffled] is the same set in an order that is never [inOrder]. */
data class OrderQuestion(
    override val surah: Int,
    val inOrder: List<Int>,
    val shuffled: List<Int>,
) : QuizQuestion {
    override val kind: QuizKind get() = QuizKind.ORDER
    override val ayahsUsed: Set<Int> get() = inOrder.toSet()
    override val focusAyah: Int get() = inOrder.first()
    override val hardOnMiss: Set<Int> get() = inOrder.toSet()
}

/** [ayah] is shown whole (it is not the first ayah of its surah); [options] are four numbers of surahs, the right one among them. */
data class WhereQuestion(
    override val surah: Int,
    val ayah: Int,
    val options: List<Int>,
) : QuizQuestion {
    override val kind: QuizKind get() = QuizKind.WHERE
    override val ayahsUsed: Set<Int> get() = setOf(ayah)
    override val focusAyah: Int get() = ayah
    override val hardOnMiss: Set<Int> get() = emptySet()
}

/**
 * Two similar places (task F6): the ayah [ayah] of [surah] and the ayah [otherAyah] of [otherSurah] begin with [shownWords] common words
 * (that much is shown, the rest is hidden); the person recalls each and then sees what differs. A miss ("I mixed them up") marks both ayahs
 * difficult. Self-graded like [ContinueQuestion].
 */
data class TellApartQuestion(
    override val surah: Int,
    val ayah: Int,
    val otherSurah: Int,
    val otherAyah: Int,
    val shownWords: Int,
) : QuizQuestion {
    override val kind: QuizKind get() = QuizKind.TELL_APART
    override val ayahsUsed: Set<Int> get() = setOf(ayah)
    override val focusAyah: Int get() = ayah
    override val hardOnMiss: Set<Int> get() = setOf(ayah)
    override val places: Set<AyahKey> get() = linkedSetOf(AyahKey(surah, ayah), AyahKey(otherSurah, otherAyah))
    override val otherHardOnMiss: Set<AyahKey> get() = linkedSetOf(AyahKey(otherSurah, otherAyah))
}

/** The question made of a [pair] chosen by `TellApartPairs`. */
fun TellApartPair.toQuestion(): TellApartQuestion =
    TellApartQuestion(first.surah, first.ayah, second.surah, second.ayah, shownWords)

/**
 * How one question was answered: [knew] is "I knew it" (or the right order, or the right surah) and [hardAyahs] the ayahs of the
 * question to mark difficult (empty when [knew] and for [WhereQuestion]).
 */
data class QuizAnswer(
    val question: QuizQuestion,
    val knew: Boolean,
    val hardAyahs: Set<Int>,
    /** Difficult ayahs outside the surah of the question (the second place of [TellApartQuestion]); empty for every other kind. */
    val otherHard: Set<AyahKey> = emptySet(),
)

/** How the questions are answered (pure; the screen only collects the person's taps). */
object QuizAnswers {
    /** "I knew it" / "I did not know it" of [QuizKind.CONTINUE], [QuizKind.NEXT] and [QuizKind.START]. */
    fun selfGraded(question: QuizQuestion, knew: Boolean): QuizAnswer =
        QuizAnswer(question, knew, if (knew) emptySet() else question.hardOnMiss, if (knew) emptySet() else question.otherHardOnMiss)

    /** The order the person made: right when it is [OrderQuestion.inOrder]; otherwise the ayahs put in a wrong place are difficult. */
    fun order(question: OrderQuestion, answer: List<Int>): QuizAnswer {
        val right = answer == question.inOrder
        val misplaced = if (right) emptySet() else question.inOrder.filterIndexedTo(LinkedHashSet()) { index, ayah -> answer.getOrNull(index) != ayah }
        return QuizAnswer(question, right, misplaced)
    }

    /** The surah the person chose: right when it is the surah of the ayah; a wrong choice marks nothing. */
    fun where(question: WhereQuestion, chosenSurah: Int): QuizAnswer =
        QuizAnswer(question, chosenSurah == question.surah, emptySet())

    /** The difficult ayahs of all the answers by surah (what `markHard` is called with, once per surah); surahs without any are left out. */
    fun hardBySurah(answers: List<QuizAnswer>): Map<Int, Set<Int>> {
        val result = LinkedHashMap<Int, MutableSet<Int>>()
        for (answer in answers) {
            if (answer.hardAyahs.isNotEmpty()) result.getOrPut(answer.question.surah) { LinkedHashSet() } += answer.hardAyahs
            for (other in answer.otherHard) result.getOrPut(other.surah) { LinkedHashSet() } += other.ayah
        }
        return result
    }

    /** How many questions the person knew. */
    fun known(answers: List<QuizAnswer>): Int = answers.count { it.knew }

    /** How many kinds of questions the quiz had (the `types` of `quiz_completed`). */
    fun types(answers: List<QuizAnswer>): Int = answers.map { it.question.kind }.distinct().size
}
