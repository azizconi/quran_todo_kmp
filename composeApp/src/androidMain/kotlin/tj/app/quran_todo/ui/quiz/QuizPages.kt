package tj.app.quran_todo.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.quiz.ContinueQuestion
import tj.app.quran_todo.hifz.quiz.NextQuestion
import tj.app.quran_todo.hifz.quiz.OrderQuestion
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.hifz.quiz.QuizQuestion
import tj.app.quran_todo.hifz.quiz.StartQuestion
import tj.app.quran_todo.hifz.quiz.WhereQuestion
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.i18n.QuizCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.similar.SimilarIcon
import tj.app.quran_todo.ui.similar.rememberPlaceLabel
import tj.app.quran_todo.ui.i18n.rememberSimilarCopy
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.hifz.quiz.TellApartQuestion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

// -- Continue, next, start: "Show", then "Knew it" / "Did not know" ----------------------------------

@Composable
internal fun SelfGradedPage(
    session: QuizSession,
    question: QuizQuestion,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    arabicStyle: TextStyle,
) {
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val type = QhTheme.type
    val colors = QhTheme.colors
    val revealed = session.revealed
    QuestionFrame(
        title = kindTitle(question, copy),
        hint = kindHint(question, copy),
        bottom = {
            if (!revealed) {
                QhButton(text = copy.show, onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                    QhButton(
                        text = copy.didNotKnow,
                        onClick = { viewModel.answerSelf(false) },
                        modifier = Modifier.weight(1f),
                        variant = QhButtonVariant.Tonal,
                    )
                    QhButton(text = copy.knew, onClick = { viewModel.answerSelf(true) }, modifier = Modifier.weight(1f))
                }
            }
        },
    ) {
        when (question) {
            is ContinueQuestion -> {
                val words = viewModel.ayahWords(question.surah, question.ayah)
                if (words != null) {
                    AyahCard {
                        QuizVeiledText(words, question.shownWords, revealed, arabicStyle, copy.hiddenText)
                    }
                    if (revealed) Where(viewModel.surahTitle(question.surah, language, names), question.ayah)
                }
            }

            is NextQuestion -> {
                val shown = viewModel.ayahWords(question.surah, question.ayah)
                val answer = viewModel.ayahWords(question.surah, question.answerAyah)
                if (shown != null) AyahCard { QuizVeiledText(shown, Int.MAX_VALUE, true, arabicStyle, copy.hiddenText) }
                Text(text = copy.whatNext, style = type.title2, color = colors.inkSecondary)
                if (answer != null) {
                    AyahCard { QuizVeiledText(answer, 0, revealed, arabicStyle, copy.hiddenText) }
                    if (revealed) Where(viewModel.surahTitle(question.surah, language, names), question.answerAyah)
                }
            }

            is StartQuestion -> {
                val family = getQuranFontFamily()
                val headerStyle = remember(type, family) { type.quranHeader(family, 38.sp) }
                val arabicName = viewModel.surahArabicName(question.surah, names)
                QhCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = QhCardStyle.Hero,
                    contentPadding = PaddingValues(QhSpacing.cardLarge),
                ) {
                    if (arabicName.isNotEmpty()) {
                        Text(
                            text = arabicName,
                            style = headerStyle,
                            color = colors.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = viewModel.surahTitle(question.surah, language, names),
                        style = type.title1,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = QhSpacing.x2),
                    )
                }
                val answer = viewModel.ayahWords(question.surah, 1)
                if (answer != null) {
                    AyahCard { QuizVeiledText(answer, 0, revealed, arabicStyle, copy.hiddenText) }
                }
            }

            else -> Unit
        }
    }
}

/** "Al-Mulk 12": where the revealed ayah is from. */
@Composable
private fun Where(surahName: String, ayah: Int) {
    Text(
        text = passageRangeLabel(surahName, ayah, ayah),
        style = QhTheme.type.callout,
        color = QhTheme.colors.inkSecondary,
    )
}

@Composable
private fun AyahCard(content: @Composable () -> Unit) {
    QhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.cardLarge)) { content() }
}

// -- Put in order ---------------------------------------------------------------------------------------

private enum class Tone { Neutral, Right, Wrong }

@Composable
internal fun OrderPage(
    session: QuizSession,
    question: OrderQuestion,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    arabicStyle: TextStyle,
) {
    val type = QhTheme.type
    val colors = QhTheme.colors
    val answer = session.currentAnswer
    val last = session.position == session.total - 1
    QuestionFrame(
        title = kindTitle(question, copy),
        hint = kindHint(question, copy),
        bottom = {
            if (answer == null) {
                QhButton(
                    text = copy.check,
                    onClick = viewModel::checkOrder,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = session.picks.size == question.inOrder.size,
                )
                QhButton(
                    text = copy.clearOrder,
                    onClick = viewModel::clearPicks,
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Ghost,
                    size = QhButtonSize.M,
                    enabled = session.picks.isNotEmpty(),
                )
            } else {
                QhButton(
                    text = if (last) copy.seeResult else copy.next,
                    onClick = viewModel::next,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        if (answer != null) Verdict(right = answer.knew, copy = copy)
        val tone = when {
            answer == null -> Tone.Neutral
            answer.knew -> Tone.Right
            else -> Tone.Wrong
        }
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            question.shuffled.forEach { ayah ->
                OrderCard(
                    words = viewModel.ayahWords(question.surah, ayah),
                    ayah = ayah,
                    place = session.picks.indexOf(ayah) + 1,
                    tone = tone,
                    onClick = if (answer == null) ({ viewModel.pick(ayah) }) else null,
                    copy = copy,
                    arabicStyle = arabicStyle,
                )
            }
        }
        if (answer != null && !answer.knew) {
            Text(text = copy.correctOrder, style = type.title2, color = colors.ink, modifier = Modifier.semantics { heading() })
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                question.inOrder.forEachIndexed { index, ayah ->
                    OrderCard(
                        words = viewModel.ayahWords(question.surah, ayah),
                        ayah = ayah,
                        place = index + 1,
                        tone = Tone.Right,
                        onClick = null,
                        copy = copy,
                        arabicStyle = arabicStyle,
                    )
                }
            }
        }
    }
}

/** An ayah of a "put in order" question: tap it to give it the next number, tap again to take the number back. */
@Composable
private fun OrderCard(
    words: AyahWords?,
    ayah: Int,
    place: Int,
    tone: Tone,
    onClick: (() -> Unit)?,
    copy: QuizCopy,
    arabicStyle: TextStyle,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val picked = place > 0
    val border = when (tone) {
        Tone.Right -> colors.learned
        Tone.Wrong -> colors.danger.copy(alpha = 0.6f)
        Tone.Neutral -> if (picked) colors.primary else colors.hairline
    }
    val description = if (picked) "${copy.ayah(ayah)}. ${copy.orderPlace(place)}" else copy.ayah(ayah)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick = onClick, shape = shape, highlight = false, role = Role.Checkbox)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(colors.surface)
            .border(if (picked || tone != Tone.Neutral) 2.dp else 1.dp, border, shape)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(QhSpacing.card),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        PlaceBadge(place = place, tone = tone)
        if (words != null) {
            Text(
                text = words.text,
                style = arabicStyle,
                color = colors.ink,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The number of an ayah in the order being made: an empty ring until the ayah is tapped. */
@Composable
private fun PlaceBadge(place: Int, tone: Tone) {
    val colors = QhTheme.colors
    val fill = when {
        place == 0 -> Color.Transparent
        tone == Tone.Right -> colors.learned
        tone == Tone.Wrong -> colors.danger.copy(alpha = 0.85f)
        else -> colors.primary
    }
    val onFill = when {
        tone == Tone.Right -> colors.onLearned
        tone == Tone.Wrong -> colors.onDanger
        else -> colors.onPrimary
    }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (place == 0) Modifier.border(1.5.dp, colors.inkTertiary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (place > 0) Text(text = place.toString(), style = QhTheme.type.title2, color = onFill)
    }
}

/** "Correct" with a check, "Not quite" with a cross: shape and words, not only color. */
@Composable
private fun Verdict(right: Boolean, copy: QuizCopy) {
    val colors = QhTheme.colors
    val tint = if (right) colors.learned else colors.danger
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        Icon(
            imageVector = if (right) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(QhSpacing.iconBar),
        )
        Text(text = if (right) copy.right else copy.wrong, style = QhTheme.type.title2, color = tint)
    }
}

// -- Where is this ayah -----------------------------------------------------------------------------------

@Composable
internal fun WherePage(
    session: QuizSession,
    question: WhereQuestion,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    arabicStyle: TextStyle,
) {
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val family = getQuranFontFamily()
    val type = QhTheme.type
    val nameStyle = remember(type, family) { type.quranInline(family, 20.sp) }
    val answer = session.currentAnswer
    val last = session.position == session.total - 1
    QuestionFrame(
        title = kindTitle(question, copy),
        hint = kindHint(question, copy),
        bottom = {
            if (answer != null) {
                QhButton(
                    text = if (last) copy.seeResult else copy.next,
                    onClick = viewModel::next,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        val words = viewModel.ayahWords(question.surah, question.ayah)
        if (words != null) AyahCard { QuizVeiledText(words, Int.MAX_VALUE, true, arabicStyle, copy.hiddenText) }
        if (answer != null) Verdict(right = answer.knew, copy = copy)
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            question.options.forEach { surah ->
                OptionRow(
                    name = viewModel.surahTitle(surah, language, names),
                    arabicName = viewModel.surahArabicName(surah, names),
                    nameStyle = nameStyle,
                    state = when {
                        answer == null -> OptionState.Open
                        surah == question.surah -> OptionState.Correct
                        surah == session.chosen -> OptionState.Wrong
                        else -> OptionState.Dim
                    },
                    onClick = { viewModel.choose(surah) },
                )
            }
        }
        if (answer != null && !answer.knew) {
            Text(
                text = copy.itIsSurah(viewModel.surahTitle(question.surah, language, names)),
                style = type.callout,
                color = QhTheme.colors.inkSecondary,
            )
        }
    }
}

private enum class OptionState { Open, Correct, Wrong, Dim }

/** One of the four surahs: after the choice the right one is marked (a check and a green frame), a wrong choice softly red. */
@Composable
private fun OptionRow(name: String, arabicName: String, nameStyle: TextStyle, state: OptionState, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val border = when (state) {
        OptionState.Correct -> colors.learned
        OptionState.Wrong -> colors.danger.copy(alpha = 0.6f)
        else -> colors.hairline
    }
    val background = when (state) {
        OptionState.Correct -> colors.learnedContainer
        OptionState.Wrong -> colors.danger.copy(alpha = 0.1f)
        else -> colors.surface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (state == OptionState.Open) {
                    Modifier.pressable(onClick = onClick, shape = shape, highlight = false)
                } else {
                    Modifier
                },
            )
            .alpha(if (state == OptionState.Dim) 0.6f else 1f)
            .clip(shape)
            .background(background)
            .border(if (state == OptionState.Correct || state == OptionState.Wrong) 2.dp else 1.dp, border, shape)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x3)
            .semantics(mergeDescendants = true) { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Text(text = name, style = QhTheme.type.title2, color = colors.ink, modifier = Modifier.weight(1f))
        if (arabicName.isNotEmpty()) Text(text = arabicName, style = nameStyle, color = colors.inkSecondary)
        when (state) {
            OptionState.Correct -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(QhSpacing.iconBar))
            OptionState.Wrong -> Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.danger, modifier = Modifier.size(QhSpacing.iconBar))
            else -> Unit
        }
    }
}

// -- Tell similar places apart (task F6) ---------------------------------------------------------------------

/**
 * "Tell apart": two similar places side by side, both shown only up to their common start and veiled after it. The person recalls each
 * one, taps "Show", sees both with the words that differ in the accent colour, and says "Told apart" or "Mixed up" (which marks both
 * ayahs difficult; nothing else is written).
 */
@Composable
internal fun TellApartPage(
    session: QuizSession,
    question: TellApartQuestion,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    arabicStyle: TextStyle,
) {
    val similar = rememberSimilarCopy()
    val label = rememberPlaceLabel()
    val revealed = session.revealed
    val diff = remember(question, revealed) { if (revealed) viewModel.tellApartDiff(question) else null }
    val keys = listOf(AyahKey(question.surah, question.ayah), AyahKey(question.otherSurah, question.otherAyah))
    QuestionFrame(
        title = similar.drillTitle,
        hint = similar.drillHint,
        bottom = {
            if (!revealed) {
                QhButton(text = copy.show, onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                    QhButton(
                        text = similar.mixedUp,
                        onClick = { viewModel.answerSelf(false) },
                        modifier = Modifier.weight(1f),
                        variant = QhButtonVariant.Tonal,
                    )
                    QhButton(text = similar.knewApart, onClick = { viewModel.answerSelf(true) }, modifier = Modifier.weight(1f))
                }
            }
        },
    ) {
        keys.forEachIndexed { index, key ->
            val words = viewModel.ayahWords(key.surah, key.ayah)
            QhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(QhSpacing.cardLarge)) {
                Text(
                    text = if (revealed) label(key) else similar.place(index + 1),
                    style = QhTheme.type.callout,
                    color = QhTheme.colors.inkSecondary,
                    modifier = Modifier.padding(bottom = QhSpacing.x2),
                )
                if (words != null) {
                    QuizVeiledText(
                        words = words,
                        visibleWords = question.shownWords,
                        revealed = revealed,
                        style = arabicStyle,
                        hiddenLabel = copy.hiddenText,
                        emphasis = (if (index == 0) diff?.a else diff?.b)?.differingRanges.orEmpty(),
                    )
                }
            }
        }
        if (revealed) Text(text = similar.differencesNote, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
    }
}

// -- The result ------------------------------------------------------------------------------------------

@Composable
internal fun QuizResultPage(
    session: QuizSession,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
    /** The line "Tell apart · 3 pairs" (task F6); null when there is no pair to tell apart or this is the drill itself. */
    onTellApart: (() -> Unit)? = null,
    pairs: Int = 0,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val similarCopy = rememberSimilarCopy()
    val label = rememberPlaceLabel()
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val known = session.known
    val total = session.total
    val missed = session.answers.filter { !it.knew }
    val marked = session.answers.any { it.hardAyahs.isNotEmpty() }

    // The ring fills and its number rolls up, calmly: no burst, nothing falls.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(known) {
        delay(240)
        shown = known
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x4, bottom = QhSpacing.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            ProgressRing(
                progress = if (total == 0) 1f else known.toFloat() / total,
                size = 148.dp,
                strokeWidth = 12.dp,
                color = colors.learned,
                trackColor = colors.surfaceMuted,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedCounter(value = shown, style = type.headline, color = colors.ink)
                    Text(text = " / $total", style = type.title2, color = colors.inkSecondary)
                }
            }
            Text(
                text = copy.result(known, total),
                style = type.headline,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (missed.isEmpty()) {
                Text(text = copy.allRight, style = type.body, color = colors.inkSecondary, textAlign = TextAlign.Center)
            } else {
                Text(
                    text = copy.missedHeader,
                    style = type.title2,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                missed.forEach { answer ->
                    val question = answer.question
                    val (from, to) = missedRange(question)
                    QhCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = if (question is TellApartQuestion) {
                                "${label(AyahKey(question.surah, question.ayah))} · ${label(AyahKey(question.otherSurah, question.otherAyah))}"
                            } else {
                                passageRangeLabel(viewModel.surahTitle(question.surah, language, names), from, to)
                            },
                            style = type.title2,
                            color = colors.ink,
                        )
                        Text(text = kindTitle(question, copy), style = type.callout, color = colors.inkSecondary)
                        QhButton(
                            text = copy.openInReader,
                            onClick = { onOpenReader(Dest.Reader(surah = question.surah, ayah = question.focusAyah, origin = QuizOrigin)) },
                            modifier = Modifier.padding(top = QhSpacing.x2),
                            variant = QhButtonVariant.Ghost,
                            size = QhButtonSize.S,
                        )
                    }
                }
                if (marked) Text(text = copy.markedNote, style = type.callout, color = colors.inkSecondary, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x2, bottom = QhSpacing.x3)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhButton(text = copy.moreQuestions(QuizBuilder.DEFAULT_COUNT), onClick = onAgain, modifier = Modifier.fillMaxWidth())
            if (onTellApart != null && pairs > 0) {
                QhButton(
                    text = similarCopy.entry(pairs),
                    onClick = onTellApart,
                    modifier = Modifier.fillMaxWidth(),
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                    icon = SimilarIcon,
                )
            }
            QhButton(
                text = copy.done,
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.M,
            )
        }
    }
}

/** The ayahs of the answer of a question, for the line of the missed list: "Al-Mulk 12", "Al-Mulk 4-6". */
private fun missedRange(question: QuizQuestion): Pair<Int, Int> = when (question) {
    is ContinueQuestion -> question.ayah to question.ayah
    is NextQuestion -> question.answerAyah to question.answerAyah
    is StartQuestion -> 1 to 1
    is OrderQuestion -> question.inOrder.first() to question.inOrder.last()
    is WhereQuestion -> question.ayah to question.ayah
    is TellApartQuestion -> question.ayah to question.ayah
}
