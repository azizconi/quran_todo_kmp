package tj.app.quran_todo.hifz.quiz

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
 * How one question was answered: [knew] is "I knew it" (or the right order, or the right surah) and [hardAyahs] the ayahs of the
 * question to mark difficult (empty when [knew] and for [WhereQuestion]).
 */
data class QuizAnswer(
    val question: QuizQuestion,
    val knew: Boolean,
    val hardAyahs: Set<Int>,
)

/** How the questions are answered (pure; the screen only collects the person's taps). */
object QuizAnswers {
    /** "I knew it" / "I did not know it" of [QuizKind.CONTINUE], [QuizKind.NEXT] and [QuizKind.START]. */
    fun selfGraded(question: QuizQuestion, knew: Boolean): QuizAnswer =
        QuizAnswer(question, knew, if (knew) emptySet() else question.hardOnMiss)

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
    fun hardBySurah(answers: List<QuizAnswer>): Map<Int, Set<Int>> =
        answers.filter { it.hardAyahs.isNotEmpty() }
            .groupBy({ it.question.surah }, { it.hardAyahs })
            .mapValues { (_, sets) -> sets.flatMapTo(LinkedHashSet()) { it } }

    /** How many questions the person knew. */
    fun known(answers: List<QuizAnswer>): Int = answers.count { it.knew }

    /** How many kinds of questions the quiz had (the `types` of `quiz_completed`). */
    fun types(answers: List<QuizAnswer>): Int = answers.map { it.question.kind }.distinct().size
}
