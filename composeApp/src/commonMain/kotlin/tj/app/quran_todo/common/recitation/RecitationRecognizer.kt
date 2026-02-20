package tj.app.quran_todo.common.recitation

object RecitationErrorCode {
    const val MICROPHONE_PERMISSION = "MICROPHONE_PERMISSION"
    const val SPEECH_PERMISSION = "SPEECH_PERMISSION"
    const val RECOGNIZER_UNAVAILABLE = "RECOGNIZER_UNAVAILABLE"
    const val AUDIO_SESSION_FAILED = "AUDIO_SESSION_FAILED"
    const val CAPTURE_START_FAILED = "CAPTURE_START_FAILED"
    const val NETWORK = "NETWORK"
    const val TIMEOUT = "TIMEOUT"
    const val SERVER = "SERVER"
    const val GENERIC = "GENERIC"
}

expect class RecitationRecognizer() {
    fun start(
        languageCode: String,
        onResult: (text: String, isFinal: Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean

    fun stop()
}
