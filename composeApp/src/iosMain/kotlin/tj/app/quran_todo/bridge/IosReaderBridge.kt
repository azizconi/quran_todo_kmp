package tj.app.quran_todo.bridge

import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.audio.timing.WordSpan
import tj.app.quran_todo.common.audio.timing.usableSpans
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.ui.recording.VoiceService
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.words.WordTimingsLoader

/**
 * Small doors of the SwiftUI reader into the shared layer that have no view model of their own (task IOS-S5).
 */
object IosReaderBridge {
    private val koin get() = KoinPlatform.getKoin()

    /** The recorder and player of "My reading" (the iOS `VoiceService`). */
    val voice: VoiceService get() = koin.get()

    /**
     * The usable word spans (start/end in ms inside the ayah's recording) of ayah [ayah] of [surah] for the reciter of the settings, or
     * an empty list: the setting "Highlight words" is off, the timings are not on the device and not reachable, or they do not fit the
     * [wordCount] words that are drawn. Reads or fetches the surah's timings once (memory, disk, network); never throws.
     */
    suspend fun wordSpans(surah: Int, ayah: Int, wordCount: Int): List<WordSpan> {
        val prefs: UiPrefs = koin.get()
        if (!prefs.highlightWords.value) return emptyList()
        val loader: WordTimingsLoader = koin.get()
        val timings = try {
            loader.load(prefs.reciter.value.recitationId, surah)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return usableSpans(timings.spans(ayah), wordCount)
    }

    /** The keys of the translation editions that are stored whole on the device (the sheet of editions marks them). Empty on a failure. */
    suspend fun downloadedTranslations(): Set<String> = try {
        koin.get<QuranReferenceRepository>().downloadedTranslations()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        emptySet()
    }
}
