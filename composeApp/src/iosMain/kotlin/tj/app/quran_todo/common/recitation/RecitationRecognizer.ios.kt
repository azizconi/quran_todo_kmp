package tj.app.quran_todo.common.recitation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioTime
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
actual class RecitationRecognizer {
    private var speechRecognizer: SFSpeechRecognizer? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
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
        isRunning = true
        currentLanguageCode = languageCode
        onResultCallback = onResult
        onErrorCallback = onError

        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            if (!granted || !isRunning) {
                reportErrorOnMain(RecitationErrorCode.MICROPHONE_PERMISSION)
                return@requestRecordPermission
            }
            SFSpeechRecognizer.requestAuthorization { status ->
                if (!isRunning) return@requestAuthorization
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    reportErrorOnMain(RecitationErrorCode.SPEECH_PERMISSION)
                    return@requestAuthorization
                }
                dispatch_async(dispatch_get_main_queue()) {
                    startSession()
                }
            }
        }
        return true
    }

    actual fun stop() {
        isRunning = false
        teardownSession()
        onResultCallback = null
        onErrorCallback = null
    }

    private fun startSession() {
        if (!isRunning) return
        teardownSession()

        val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = currentLanguageCode))
        if (!recognizer.available) {
            reportError(RecitationErrorCode.RECOGNIZER_UNAVAILABLE)
            return
        }
        speechRecognizer = recognizer

        val request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        recognitionRequest = request

        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setMode(AVAudioSessionModeMeasurement, error = null)
            session.setActive(true, error = null)
        }.onFailure {
            reportError(RecitationErrorCode.AUDIO_SESSION_FAILED)
            return
        }

        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val format = inputNode.outputFormatForBus(0u)
        inputNode.removeTapOnBus(0u)
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,
            format = format
        ) { buffer: AVAudioPCMBuffer?, _: AVAudioTime? ->
            if (isRunning && buffer != null) {
                request.appendAudioPCMBuffer(buffer)
            }
        }
        audioEngine = engine

        recognitionTask = recognizer.recognitionTaskWithRequest(request) { result, error ->
            val transcript = result?.bestTranscription?.formattedString
            if (!transcript.isNullOrBlank()) {
                dispatch_async(dispatch_get_main_queue()) {
                    if (isRunning) {
                        onResultCallback?.invoke(transcript, result?.isFinal() == true)
                    }
                }
            }

            if (error != null) {
                reportErrorOnMain(mapPlatformError(error.toString()))
                return@recognitionTaskWithRequest
            }

            if (result?.isFinal() == true && isRunning) {
                dispatch_async(dispatch_get_main_queue()) {
                    startSession()
                }
            }
        }

        val started = runCatching {
            engine.prepare()
            engine.startAndReturnError(null)
            engine.isRunning()
        }.getOrDefault(false)

        if (!started) {
            reportError(RecitationErrorCode.CAPTURE_START_FAILED)
        }
    }

    private fun teardownSession() {
        recognitionTask?.cancel()
        recognitionTask = null

        recognitionRequest?.endAudio()
        recognitionRequest = null

        audioEngine?.let { engine ->
            engine.inputNode.removeTapOnBus(0u)
            if (engine.isRunning()) {
                engine.stop()
            }
        }
        audioEngine = null
        speechRecognizer = null
        runCatching { AVAudioSession.sharedInstance().setActive(false, error = null) }
    }

    private fun reportError(message: String) {
        if (!isRunning) return
        onErrorCallback?.invoke(message)
        stop()
    }

    private fun reportErrorOnMain(message: String) {
        dispatch_async(dispatch_get_main_queue()) {
            reportError(message)
        }
    }

    private fun mapPlatformError(raw: String): String {
        val normalized = raw.lowercase()
        return when {
            "network" in normalized -> RecitationErrorCode.NETWORK
            "timeout" in normalized -> RecitationErrorCode.TIMEOUT
            else -> RecitationErrorCode.GENERIC
        }
    }
}
