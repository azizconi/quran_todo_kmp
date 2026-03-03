package tj.app.quran_todo.common.recitation

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import tj.app.quran_todo.common.platform.AndroidContextHolder

actual class RecitationRecognizer {
    private var recognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var currentLanguageCode: String = "ar-SA"
    private var onResultCallback: ((String, Boolean) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    actual fun start(
        languageCode: String,
        onResult: (text: String, isFinal: Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        stop()
        val context = AndroidContextHolder.context
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            AndroidContextHolder.activity?.let { activity ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
            }
            onError(RecitationErrorCode.MICROPHONE_PERMISSION)
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError(RecitationErrorCode.RECOGNIZER_UNAVAILABLE)
            return false
        }

        currentLanguageCode = languageCode
        onResultCallback = onResult
        onErrorCallback = onError
        isRunning = true

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val text = firstResult(results)
                    if (text != null) {
                        onResultCallback?.invoke(text, true)
                    }
                    if (isRunning) {
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    firstResult(partialResults)?.let { partial ->
                        onResultCallback?.invoke(partial, false)
                    }
                }

                override fun onError(error: Int) {
                    if (!isRunning) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartListening()
                        else -> {
                            onErrorCallback?.invoke(errorMessage(error))
                            stop()
                        }
                    }
                }
            })
        }

        restartListening()
        return true
    }

    actual fun stop() {
        isRunning = false
        recognizer?.apply {
            runCatching { stopListening() }
            runCatching { cancel() }
            destroy()
        }
        recognizer = null
        onResultCallback = null
        onErrorCallback = null
    }

    private fun restartListening() {
        if (!isRunning) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer better quality over strict offline mode for Quran recitation.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun firstResult(bundle: Bundle?): String? {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return results?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> RecitationErrorCode.GENERIC
        SpeechRecognizer.ERROR_CLIENT -> RecitationErrorCode.GENERIC
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecitationErrorCode.MICROPHONE_PERMISSION
        SpeechRecognizer.ERROR_NETWORK -> RecitationErrorCode.NETWORK
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> RecitationErrorCode.TIMEOUT
        SpeechRecognizer.ERROR_SERVER -> RecitationErrorCode.SERVER
        else -> RecitationErrorCode.GENERIC
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 4301
    }
}
