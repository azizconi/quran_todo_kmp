package tj.app.quran_todo.ui.hifz

import androidx.compose.runtime.Composable
import tj.app.quran_todo.data.database.entity.quran.AyahEntity

/**
 * The one place where the main code knows about the voice check prototype (task F7). It holds nothing: in a release build [passageEntry]
 * stays null and the "Recall" step draws nothing extra. A debug build fills it at start (`debug/voice/VoiceProtoInit` of the debug source
 * set), so no class of the `:voiceproto` module is referenced from here.
 */
object VoiceProtoHook {
    /** What the "Recall" step of a lesson draws under "Record my reading": the voice check of the passage [ayahs]. */
    var passageEntry: (@Composable (ayahs: List<AyahEntity>) -> Unit)? = null
}
