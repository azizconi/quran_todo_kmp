package tj.app.quran_todo.common.recitation

/**
 * Recitation recognition is not available on Android: the platform speech recognizer cannot judge
 * Quran recitation (no vowel marks, no tajweed), so the microphone permission was removed from the
 * manifest together with the old implementation. The `expect` class stays because the shared (iOS)
 * code uses it; this actual only reports that the feature is unavailable.
 */
actual class RecitationRecognizer {
    actual fun start(
        languageCode: String,
        onResult: (text: String, isFinal: Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        onError(RecitationErrorCode.RECOGNIZER_UNAVAILABLE)
        return false
    }

    actual fun stop() = Unit
}
