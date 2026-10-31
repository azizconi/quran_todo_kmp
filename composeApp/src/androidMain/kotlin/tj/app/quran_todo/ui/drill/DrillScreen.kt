package tj.app.quran_todo.ui.drill

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzAyahContent
import tj.app.quran_todo.ui.hifz.HifzSession
import tj.app.quran_todo.ui.hifz.RecallControlsHost
import tj.app.quran_todo.ui.hifz.RecallHost
import tj.app.quran_todo.ui.hifz.hifzRow
import tj.app.quran_todo.ui.hifz.rowStateOf
import tj.app.quran_todo.ui.i18n.DrillCopy
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.hifzCopy
import tj.app.quran_todo.ui.i18n.rememberDrillCopy
import tj.app.quran_todo.ui.lesson.rememberLessonArabicStyle
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.reader.components.KeepScreenOnEffect
import tj.app.quran_todo.ui.reader.displayWords
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The drill of the difficult ayahs (`Dest.HardDrill`, task F2a): the ayahs the person marked as hard, in the order of the mushaf, at most
 * 20 at a time. Each card names the surah and the ayah, shows the ayah before it in full as the start of the thought, and hides the
 * hard ayah behind the same "Recall" component as the lesson (hint levels, peeking, "Show", "I remember" / "Hard"). The end tells how many
 * were remembered and how many are still hard. [onBack] leaves the drill (what was answered is saved first).
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun DrillScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<DrillViewModel>()
    val language = LocalAppLanguage.current
    val copy = rememberDrillCopy()
    val recallCopy = remember(language) { hifzCopy(language) }
    val toaster = LocalToaster.current
    val ui by viewModel.ui.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val hintState = viewModel.hintMode.collectAsState()
    val latestCopy by rememberUpdatedState(copy)

    LaunchedEffect(language, viewModel) { viewModel.onLanguage(language) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                DrillEvent.SaveFailed -> toaster.show(latestCopy.saveFailed, ToastKind.Error)
            }
        }
    }
    val card = ui as? DrillUi.Card
    KeepScreenOnEffect(enabled = card != null)

    val leave = {
        viewModel.exit()
        onBack()
    }
    BackHandler { leave() }

    val cardState = rememberUpdatedState(card)
    val host = remember(viewModel, recallCopy) { DrillHost(viewModel, cardState, hintState, recallCopy) }

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            DrillTopBar(copy = copy, ui = ui, onClose = leave)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val state = ui) {
                    DrillUi.Loading -> DrillLoading()
                    DrillUi.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(title = copy.emptyTitle, message = copy.emptyBody, actionLabel = copy.done, onAction = leave)
                    }
                    is DrillUi.Card, is DrillUi.Done -> DrillContent(
                        viewModel = viewModel,
                        host = host,
                        copy = copy,
                        state = state,
                        saving = saving,
                        onDone = leave,
                    )
                }
            }
        }
    }
}

/** What the controls under the hidden ayah of the drill talk to. */
@Stable
private class DrillHost(
    private val viewModel: DrillViewModel,
    private val card: State<DrillUi.Card?>,
    private val hint: State<HifzHintMode>,
    override val copy: HifzCopy,
) : RecallHost {
    override val session: HifzSession? get() = card.value?.session

    override val listening: StateFlow<Boolean> get() = viewModel.listening

    override val hintMode: HifzHintMode get() = hint.value

    override fun setHintMode(mode: HifzHintMode) = viewModel.setHintMode(mode)

    private var cached: Pair<Int, AyahWords>? = null

    override fun words(inSurah: Int): AyahWords? {
        val ayah = card.value?.ayah?.takeIf { it.numberInSurah == inSurah } ?: return null
        cached?.takeIf { (key, _) -> key == ayah.number }?.let { return it.second }
        return displayWords(ayah.surahNumber, ayah.numberInSurah, ayah.text).also { cached = ayah.number to it }
    }

    override fun reveal() = viewModel.reveal()

    override fun mark(mark: AyahMark, position: Int) = viewModel.mark(mark, position, early = false)

    override fun markEarly(mark: AyahMark, position: Int) = viewModel.mark(mark, position, early = true)

    override fun hideMore(position: Int) {
        val inSurah = session?.items?.getOrNull(position)?.inSurah ?: return
        viewModel.hideMore(position, words(inSurah)?.wordCount ?: return)
    }

    override fun peekWord(position: Int, word: Int) = viewModel.peekWord(position, word)

    override fun peekAyah(position: Int) = viewModel.peekAyah(position)

    override fun toggleListen() {
        val current = card.value ?: return
        viewModel.toggleListen(current.surah, current.ayah.numberInSurah)
    }
}

@Composable
private fun DrillTopBar(copy: DrillCopy, ui: DrillUi, onClose: () -> Unit) {
    val colors = QhTheme.colors
    val card = ui as? DrillUi.Card
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 4.dp, end = QhSpacing.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconButton(icon = Icons.Rounded.Close, contentDescription = copy.exit, onClick = onClose, filled = false)
            Text(
                text = copy.title,
                style = QhTheme.type.title1,
                color = colors.ink,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = QhSpacing.x1)
                    .semantics { heading() },
            )
            if (card != null) {
                Text(text = copy.progress(card.index + 1, card.total), style = QhTheme.type.callout, color = colors.inkSecondary)
            }
        }
        if (card != null) {
            QhLinearProgress(
                progress = card.index.toFloat() / card.total.coerceAtLeast(1),
                height = 3.dp,
                color = colors.gold,
                trackColor = colors.gold.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = QhSpacing.screen),
            )
        }
    }
}

@Composable
private fun DrillLoading() {
    Column(
        Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
    ) {
        Skeleton(Modifier.fillMaxWidth(0.5f).height(24.dp))
        Skeleton(Modifier.fillMaxWidth().height(120.dp), QhTheme.shapes.lg)
        Skeleton(Modifier.fillMaxWidth().height(120.dp), QhTheme.shapes.lg)
    }
}

/** The card (or the end) sliding to the next one: horizontally like the cards of the lesson, a fade under reduced motion. */
@Composable
private fun DrillContent(
    viewModel: DrillViewModel,
    host: DrillHost,
    copy: DrillCopy,
    state: DrillUi,
    saving: Boolean,
    onDone: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize(),
        contentKey = { current -> if (current is DrillUi.Card) current.index else -1 },
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(QhMotion.Short)) togetherWith fadeOut(tween(QhMotion.Instant))
            } else {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(QhMotion.Long, easing = QhMotion.EmphasizedDecelerate)) { it / 3 } +
                        fadeIn(tween(QhMotion.Medium, delayMillis = 60)),
                    initialContentExit = slideOutHorizontally(tween(QhMotion.Medium, easing = QhMotion.EmphasizedAccelerate)) { -it / 3 } +
                        fadeOut(tween(QhMotion.Short)),
                )
            }
        },
        label = "drill-card",
    ) { current ->
        when (current) {
            is DrillUi.Card -> DrillCard(viewModel = viewModel, host = host, copy = copy, card = current)
            is DrillUi.Done -> DrillDone(copy = copy, done = current, saving = saving, onDone = onDone)
            else -> Unit
        }
    }
}

/** One difficult ayah: its surah and number, the ayah before it in full, then the ayah itself, hidden, with the recall controls. */
@Composable
private fun DrillCard(viewModel: DrillViewModel, host: DrillHost, copy: DrillCopy, card: DrillUi.Card) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val hintMode = host.hintMode
    val arabicStyle = rememberLessonArabicStyle()
    val ayah = card.ayah
    val state = remember(card.session, hintMode, host.copy) { rowStateOf(card.session, ayah.numberInSurah, hintMode, host) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x3, bottom = QhSpacing.x8)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = viewModel.surahTitle(card.item.surah, language, names),
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = copy.ayah(card.item.ayah), style = QhTheme.type.callout, color = colors.due)
        }
        card.previous?.let { previous ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(QhTheme.shapes.md)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = QhSpacing.x4, vertical = QhSpacing.x3),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
            ) {
                Text(text = copy.previousAyah, style = QhTheme.type.label, color = colors.inkSecondary)
                Text(
                    text = splitBismillah(previous.surahNumber, previous.numberInSurah, previous.text),
                    style = arabicStyle,
                    color = colors.ink,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(text = copy.question, style = QhTheme.type.callout, color = colors.inkSecondary)
        Column(Modifier.fillMaxWidth().hifzRow(state)) {
            HifzAyahContent(
                state = state,
                arabic = splitBismillah(ayah.surahNumber, ayah.numberInSurah, ayah.text),
                arabicStyle = arabicStyle,
                translation = null,
                translationStyle = QhTheme.type.body,
            )
            RecallControlsHost(host = host, inSurah = ayah.numberInSurah)
        }
    }
}

/** "Practice finished", "I remember: 7 · Still hard: 3", "4 more next time" and "Done" (it waits for the last write). */
@Composable
private fun DrillDone(copy: DrillCopy, done: DrillUi.Done, saving: Boolean, onDone: () -> Unit) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x6)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3, Alignment.CenterVertically),
        ) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.learned, modifier = Modifier.size(64.dp))
            Text(
                text = copy.doneTitle,
                style = QhTheme.type.headline,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = copy.summary(done.remembered, done.stillHard),
                style = QhTheme.type.title2,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            if (done.more > 0) {
                Text(text = copy.more(done.more), style = QhTheme.type.callout, color = colors.inkSecondary, textAlign = TextAlign.Center)
            }
        }
        QhButton(text = copy.done, onClick = onDone, modifier = Modifier.fillMaxWidth(), loading = saving, enabled = !saving)
    }
}
