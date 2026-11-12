package tj.app.quran_todo.ui.lesson

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.RecallBottomBar
import tj.app.quran_todo.ui.hifz.RecallGradeSheet
import tj.app.quran_todo.ui.hifz.RecallGrading
import tj.app.quran_todo.ui.hifz.RecallHost
import tj.app.quran_todo.ui.hifz.RecallPassage
import tj.app.quran_todo.ui.hifz.RecallResult
import tj.app.quran_todo.ui.hifz.VoiceProtoHook
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.rememberVoiceCopy
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.reader.displayWords
import tj.app.quran_todo.ui.recording.PassageKey
import tj.app.quran_todo.ui.recording.VoiceSheetHost
import tj.app.quran_todo.ui.recording.rememberRecordingCount
import tj.app.quran_todo.ui.recording.rememberVoiceSheetState
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.theme.QhSpacing

// -- Recall -------------------------------------------------------------------------------------------------

/** What the controls of the lesson's "Recall" talk to. */
@Stable
internal class LessonRecallHost(
    private val viewModel: LessonViewModel,
    private val key: String,
    private val work: State<LessonUi.Working>,
    private val hint: State<HifzHintMode>,
    override val copy: HifzCopy,
) : RecallHost {
    override val session get() = work.value.progress.recall

    override val listening get() = viewModel.listeningCurrent

    override fun reveal() = viewModel.reveal(key)

    override fun mark(mark: AyahMark, position: Int) = viewModel.mark(key, mark, position)

    override fun toggleListen() {
        val current = session?.current ?: return
        viewModel.toggleListenAyah(work.value, current.inSurah)
    }

    override val hintMode: HifzHintMode get() = hint.value

    override fun setHintMode(mode: HifzHintMode) = viewModel.setHintMode(mode)

    private val wordsCache = HashMap<Int, AyahWords?>()

    override fun words(inSurah: Int): AyahWords? = wordsCache.getOrPut(inSurah) {
        work.value.ayahs.firstOrNull { it.numberInSurah == inSurah }?.let { displayWords(it.surahNumber, it.numberInSurah, it.text) }
    }

    override fun markEarly(mark: AyahMark, position: Int) = viewModel.markEarly(key, mark, position)

    override fun hideMore(position: Int) {
        val inSurah = session?.items?.getOrNull(position)?.inSurah ?: return
        viewModel.hideMore(key, position, words(inSurah)?.wordCount ?: return)
    }

    override fun peekWord(position: Int, word: Int) = viewModel.peekWord(key, position, word)

    override fun peekAyah(position: Int) = viewModel.peekAyah(key, position)
}

/**
 * "Recall" of a card, new or repeated: the hidden passage, ayah by ayah, with its controls in a bar pinned to the bottom (tasks U5, U6).
 * There is one grading: after the last ayah the grade of the passage is the one the answers give ([RecallGrading.resultOf]) and the
 * bar shows it with "Change" (the grade sheet, for a manual choice) and "Next", which writes it. Nothing opens by itself.
 */
@Composable
internal fun RecallStage(viewModel: LessonViewModel, copy: LessonCopy, work: LessonUi.Working) {
    val hifzCopy = rememberHifzCopy()
    val workState = rememberUpdatedState(work)
    val hintState = viewModel.hintMode.collectAsState()
    val hint by hintState
    val host = remember(viewModel, work.card.key, hifzCopy) { LessonRecallHost(viewModel, work.card.key, workState, hintState, hifzCopy) }
    val saving by viewModel.isSaving.collectAsState()
    val session = work.progress.recall
    if (session == null) {
        PassageLoading()
        return
    }
    var gradeOpen by remember(work.card.key) { mutableStateOf(false) }
    // The grade the person chose on the sheet instead of the one the answers give (it survives a rotation and a process death).
    var overrideText by rememberSaveable(work.card.key) { mutableStateOf("") }
    val result = RecallGrading.resultOf(session, RecallResult.decode(overrideText))
    val stage = (work.card as? LessonCard.Review)?.item?.stage
    val returnDays = result?.let { RecallGrading.returnDays(stage, it.grade) }

    val voice = rememberVoiceSheetState()
    val voiceCopy = rememberVoiceCopy()
    val voiceRange = work.card.range
    val voiceKey = remember(voiceRange) { PassageKey(voiceRange.surah, voiceRange.startAyah, voiceRange.endAyah) }
    val recordings = rememberRecordingCount(voiceKey)
    val language = LocalAppLanguage.current
    val names by viewModel.chapterNames.collectAsState()
    val passageTitle = "${viewModel.surahTitle(voiceRange.surah, language, names)} · ${copy.range(voiceRange.startAyah, voiceRange.endAyah)}"

    val hard = (work.card as? LessonCard.Review)?.item?.hardAyahs.orEmpty()
    Column(Modifier.fillMaxSize()) {
        // Debug builds only (F7 prototype): "Check by voice"; null, so nothing, in a release build.
        VoiceProtoHook.passageEntry?.invoke(work.ayahs)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            RecallPassage(
                session = session,
                ayahs = work.ayahs,
                host = host,
                hintMode = hint,
                arabicStyle = rememberLessonArabicStyle(),
                hard = hard,
                contentPadding = PaddingValues(bottom = QhSpacing.x2),
                compact = true,
            )
        }
        RecallBottomBar(
            host = host,
            session = session,
            result = result,
            returnDays = returnDays,
            // "Record reading" / "My reading · 3": the person's own voice for this passage (it stays on the phone).
            recordLabel = if (recordings > 0) voiceCopy.myReadingCount(recordings) else hifzCopy.recordShort,
            onRecord = { if (recordings > 0) voice.openList() else voice.openRecording() },
            onChange = { gradeOpen = true },
            nextLabel = copy.next,
            onNext = { result?.let { viewModel.submit(work.card, it.grade, it.hard) } },
            saving = saving,
        )
    }
    VoiceSheetHost(
        state = voice,
        key = voiceKey,
        title = passageTitle,
        onListenReciter = { viewModel.toggleListenOnce(work) },
    )
    if (gradeOpen && session.finished) {
        RecallGradeSheet(
            session = session,
            title = passageTitle,
            copy = hifzCopy,
            busy = saving,
            initial = result,
            onConfirm = { grade, hardAyahs ->
                gradeOpen = false
                overrideText = RecallResult(grade, hardAyahs).encode()
            },
            onDismiss = { gradeOpen = false },
        )
    }
}
