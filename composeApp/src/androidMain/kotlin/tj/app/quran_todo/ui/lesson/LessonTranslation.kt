package tj.app.quran_todo.ui.lesson

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.data.translation.TranslationChoice
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.i18n.LessonListenCopy
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The translation under the ayahs of "Listen" and "Read" while the chip is on: the same source, edition and loading states as the
 * reader (the shared `SurahViewModel`), so a failed load shows the reader's "Translation not loaded · Retry".
 */
@Immutable
class PassageTranslation(
    /** Translations by global ayah number. */
    val byAyah: Map<Int, String>,
    /** The load failed and nothing is there: the first ayah shows "Translation not loaded · Retry". */
    val missing: Boolean,
    val copy: ReaderCopy,
    val onRetry: () -> Unit,
)

/** The state of the translation for [work], or null while the chip is off. Starts the load when it is on. */
@Composable
internal fun rememberPassageTranslation(viewModel: LessonViewModel, work: LessonUi.Working): PassageTranslation? {
    val on by viewModel.translationOn.collectAsState()
    val map by viewModel.translations.collectAsState()
    val failed by viewModel.translationFailedSurah.collectAsState()
    val language = LocalAppLanguage.current
    val revision by TranslationChoice.store.revision.collectAsState()
    val surah = work.card.range.surah
    val count = work.surah?.ayahs?.size
    // The edition was changed in "Aa" (revision) or the language was: loaded again, as the reader does.
    LaunchedEffect(on, surah, count, language, revision) {
        if (on) viewModel.ensureTranslations(work, language)
    }
    val copy = remember(language) { readerCopy(language) }
    if (!on) return null
    return PassageTranslation(
        byAyah = map,
        missing = failed == surah && map.isEmpty(),
        copy = copy,
        onRetry = { viewModel.ensureTranslations(work, language, force = true) },
    )
}

/**
 * The top line of "Listen" and "Read": the [text] (a hint, or where the listening is, with a thin bar under it when [progress] is
 * given, 0..1) and the "Translation" chip at the end.
 */
@Composable
internal fun PhaseTopLine(
    text: String,
    copy: LessonListenCopy,
    translationOn: Boolean,
    onTranslation: (Boolean) -> Unit,
    progress: Float? = null,
) {
    val colors = QhTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            Text(text = text, style = QhTheme.type.callout, color = colors.inkSecondary)
            if (progress != null) ListenBar(progress)
        }
        QhChip(label = copy.translationChip, selected = translationOn, onClick = { onTranslation(!translationOn) })
    }
}

/** A thin linear bar over the whole listening sequence. */
@Composable
private fun ListenBar(fraction: Float) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduced) tween(0) else tween(250),
        label = "listen-progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f) }
            .drawBehind {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(color = colors.surfaceMuted, size = size, cornerRadius = corner)
                if (shown > 0f) {
                    drawRoundRect(color = colors.primary, size = Size(size.width * shown, size.height), cornerRadius = corner)
                }
            },
    )
}
