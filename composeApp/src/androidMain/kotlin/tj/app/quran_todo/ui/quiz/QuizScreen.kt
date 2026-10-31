package tj.app.quran_todo.ui.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.hifz.quiz.ContinueQuestion
import tj.app.quran_todo.hifz.quiz.NextQuestion
import tj.app.quran_todo.hifz.quiz.OrderQuestion
import tj.app.quran_todo.hifz.quiz.QuizQuestion
import tj.app.quran_todo.hifz.quiz.StartQuestion
import tj.app.quran_todo.hifz.quiz.TellApartQuestion
import tj.app.quran_todo.hifz.quiz.WhereQuestion
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.i18n.QuizCopy
import tj.app.quran_todo.ui.i18n.rememberQuizCopy
import tj.app.quran_todo.ui.i18n.rememberSimilarCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The origin of a reader opened from the result of a quiz: it matches no shared element, so the reader simply slides in. */
internal const val QuizOrigin = "quiz"

/**
 * The quiz "Check" (task F2b, docs/redesign/API.md, "Проверка"): five questions on what is memorized, one after another with the
 * same horizontal slide as the cards of the lesson, then the result. "Open in the reader" of a missed ayah goes to [onOpenReader].
 * A quiz is a help: it writes nothing to the review log; a missed ayah becomes difficult.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
    /** The line "Tell apart" of the result opens the drill of every pair (task F6); null: no such line. */
    onTellApart: (() -> Unit)? = null,
    /** The usual quiz, or only "Tell apart" (all pairs, or the group of one ayah, task F6). */
    mode: QuizMode = QuizMode.Mixed,
) {
    val viewModel = koinViewModel<QuizViewModel>()
    val language = LocalAppLanguage.current
    val copy = rememberQuizCopy(language)
    val similarCopy = rememberSimilarCopy(language)
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(language, viewModel) { viewModel.onLanguage(language) }
    LaunchedEffect(viewModel) { viewModel.begin(mode) }

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            val playing = ui as? QuizUi.Playing
            val session = playing?.session
            QuizTopBar(
                title = if (mode is QuizMode.TellApart) similarCopy.drillTitle else copy.title,
                copy = copy,
                progress = session?.takeIf { !it.finished }?.let { copy.progress(it.position + 1, it.total) },
                onBack = onBack,
            )
            if (session != null) {
                QhLinearProgress(
                    progress = (if (session.finished) session.total else session.answers.size).toFloat() / session.total.coerceAtLeast(1),
                    modifier = Modifier.padding(horizontal = QhSpacing.screen),
                    height = 4.dp,
                    color = QhTheme.colors.learned,
                )
            }
            when (val state = ui) {
                QuizUi.Loading -> QuizLoading(copy)
                QuizUi.TooLittle -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(title = copy.emptyTitle, message = copy.emptyBody, actionLabel = copy.back, onAction = onBack)
                }
                QuizUi.NoPairs -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(title = similarCopy.emptyTitle, message = similarCopy.emptyBody, actionLabel = copy.back, onAction = onBack)
                }
                is QuizUi.Playing -> QuizPages(
                    state = state,
                    viewModel = viewModel,
                    copy = copy,
                    onBack = onBack,
                    onOpenReader = onOpenReader,
                    onTellApart = onTellApart,
                )
            }
        }
    }
}

/** The questions and then the result, sliding from one page to the next like the cards of the lesson. */
@Composable
private fun ColumnScope.QuizPages(
    state: QuizUi.Playing,
    viewModel: QuizViewModel,
    copy: QuizCopy,
    onBack: () -> Unit,
    onOpenReader: (Dest.Reader) -> Unit,
    onTellApart: (() -> Unit)?,
) {
    val reduced = LocalReducedMotion.current
    val arabicStyle = rememberQuizArabicStyle()
    AnimatedContent(
        targetState = state,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        // A page keeps its slide while the person works on it: only another question (or the result) is a new page.
        contentKey = { page -> if (page.session.finished) "${page.round}-result" else "${page.round}-${page.session.position}" },
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(QhMotion.Short)) togetherWith fadeOut(tween(QhMotion.Instant))
            } else {
                val width = { full: Int -> full / 3 }
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(QhMotion.Long, easing = QhMotion.EmphasizedDecelerate)) { width(it) } +
                        fadeIn(tween(QhMotion.Medium, delayMillis = 60)),
                    initialContentExit = slideOutHorizontally(tween(QhMotion.Medium, easing = QhMotion.EmphasizedAccelerate)) { -width(it) } +
                        fadeOut(tween(QhMotion.Short)),
                )
            }
        },
        label = "quiz-page",
    ) { page ->
        val session = page.session
        val question = session.current
        when {
            question == null -> QuizResultPage(
                session = session,
                viewModel = viewModel,
                copy = copy,
                onAgain = viewModel::again,
                onDone = onBack,
                onOpenReader = onOpenReader,
                onTellApart = onTellApart.takeIf { page.pairs > 0 },
                pairs = page.pairs,
            )
            question is TellApartQuestion -> TellApartPage(session, question, viewModel, copy, arabicStyle)
            question is OrderQuestion -> OrderPage(session, question, viewModel, copy, arabicStyle)
            question is WhereQuestion -> WherePage(session, question, viewModel, copy, arabicStyle)
            else -> SelfGradedPage(session, question, viewModel, copy, arabicStyle)
        }
    }
}

@Composable
private fun QuizTopBar(title: String, copy: QuizCopy, progress: String?, onBack: () -> Unit) {
    val colors = QhTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = QhSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = copy.back,
            onClick = onBack,
            filled = false,
        )
        Text(
            text = title,
            style = QhTheme.type.title1,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .padding(start = QhSpacing.x1)
                .semantics { heading() },
        )
        if (progress != null) {
            Text(text = progress, style = QhTheme.type.callout, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun ColumnScope.QuizLoading(copy: QuizCopy) {
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
    ) {
        Text(text = copy.loadingMessage, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        Skeleton(Modifier.fillMaxWidth().height(160.dp), QhTheme.shapes.lg)
        Skeleton(Modifier.fillMaxWidth(0.6f).height(20.dp))
    }
}

/** The Arabic text as the reader draws it: the same font and the size of the settings "Aa". */
@Composable
internal fun rememberQuizArabicStyle(): TextStyle {
    val family = getQuranFontFamily()
    val fontSize = LocalAppSettings.current.readingFontSize
    val type = QhTheme.type
    return remember(type, family, fontSize) {
        type.quranBody(family, fontSize.sp).copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
}

/** A question page: the kind and what to do on top, the body that scrolls, and the buttons pinned under it. */
@Composable
internal fun QuestionFrame(
    title: String,
    hint: String,
    bottom: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = QhTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x4, bottom = QhSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
                Text(text = title, style = QhTheme.type.headline, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(text = hint, style = QhTheme.type.body, color = colors.inkSecondary)
            }
            body()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(top = QhSpacing.x2, bottom = QhSpacing.x3)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            content = bottom,
        )
    }
}

/** The title of a kind of question. */
internal fun kindTitle(question: QuizQuestion, copy: QuizCopy): String = when (question) {
    is ContinueQuestion -> copy.continueTitle
    is NextQuestion -> copy.nextTitle
    is StartQuestion -> copy.startTitle
    is OrderQuestion -> copy.orderTitle
    is WhereQuestion -> copy.whereTitle
    is TellApartQuestion -> copy.tellApartTitle
}

internal fun kindHint(question: QuizQuestion, copy: QuizCopy): String = when (question) {
    is ContinueQuestion -> copy.continueHint
    is NextQuestion -> copy.nextHint
    is StartQuestion -> copy.startHint
    is OrderQuestion -> copy.orderHint
    is WhereQuestion -> copy.whereHint
    is TellApartQuestion -> copy.tellApartHint
}
