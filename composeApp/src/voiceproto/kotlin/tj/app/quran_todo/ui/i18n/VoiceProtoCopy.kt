package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage

/**
 * Texts of the voice check prototype (task F7). Debug builds only (the debug source set), Russian and English only: the prototype is
 * for the owner and the testers, not for the store.
 */
class VoiceProtoCopy(private val ru: Boolean) {
    private fun t(ru: String, en: String) = if (this.ru) ru else en

    val check = t("Проверить голосом (прототип)", "Check by voice (prototype)")
    val lab = t("Лаборатория", "Lab")
    val secondsUnit = t("с", "s")
    val stop = t("Стоп", "Stop")
    val reading = t("Читайте отрывок по памяти…", "Recite the passage from memory…")
    val analyzing = t("Разбор…", "Analyzing…")
    val noModel = t(
        "Нет модели. Положите ggml-quran-q5_0.bin в files/voice/ (adb, см. API.md «Голос: прототип»).",
        "No model. Put ggml-quran-q5_0.bin into files/voice/ (adb, see API.md, \"Voice: prototype\").",
    )
    val noMic = t("Нет доступа к микрофону.", "No microphone access.")
    val micFailed = t("Микрофон не запустился.", "The microphone did not start.")
    val noSpeech = t("Ничего не распознано.", "Nothing was recognized.")
    fun model(name: String) = t("модель $name", "model $name")
    val legendOk = t("верно", "right")
    val legendMissed = t("пропущено", "missed")
    val legendReplaced = t("заменено", "replaced")
    val extraTitle = t("Лишние слова", "Extra words")
    val replacedTitle = t("Заменено", "Replaced")
    val heardTitle = t("Распознано", "Heard")
    val noErrors = t("Ошибок слов нет.", "No word errors.")
    fun summary(missed: Int, replaced: Int, extra: Int) = t(
        "пропущено $missed, заменено $replaced, лишних $extra",
        "missed $missed, replaced $replaced, extra $extra",
    )
    fun timing(analysisMs: Long, audioMs: Long) = t(
        "разбор %.1f с, запись %.1f с (x%.2f)",
        "analysis %.1f s, recording %.1f s (x%.2f)",
    ).format(analysisMs / 1000.0, audioMs / 1000.0, if (audioMs == 0L) 0.0 else analysisMs.toDouble() / audioMs)

    // Lab
    val labTitle = t("Голос: лаборатория", "Voice: lab")
    val passage = t("Отрывок", "Passage")
    val surah = t("Сура", "Surah")
    val from = t("С аята", "From")
    val to = t("По аят", "To")
    val models = t("Модели", "Models")
    val samples = t("Образцы", "Samples")
    val noSamples = t("Нет образцов в files/voice/samples/", "No samples in files/voice/samples/")
    val run = t("Прогнать", "Run")
    val delete = t("Удалить", "Delete")
    val record = t("Записать образец", "Record a sample")
    val saveSample = t("Стоп и сохранить", "Stop and save")
    val shareAll = t("Поделиться всеми", "Share all")
    val bench = t("Замер: все образцы x все модели", "Bench: all samples x all models")
    val matching = t("Допуск слов", "Word tolerance")
    val strict = t("строгий", "strict")
    val fuzzy = t("мягкий (+1 буква)", "fuzzy (+1 letter)")
    val splitOn = t("резать по паузам", "split at pauses")
    val splitOff = t("целиком", "whole")
    val saved = t("Сохранено", "Saved")
    val benchDone = t("Замер готов (files/voice/bench.json, logcat VoiceBench)", "Bench done (files/voice/bench.json, logcat VoiceBench)")

    // Live (phonemes)
    val liveTitle = t("Живая проверка (фонемы)", "Live (phonemes)")
    val liveHint = t(
        "Читайте отрывок по памяти: слова появляются по мере чтения. После стопа слова получают оценку.",
        "Recite the passage from memory: the words appear as you say them. After stop the words get their verdicts.",
    )
    val downloadModel = t("Скачать модель (23 МБ)", "Download model (23 MB)")
    fun downloading(percent: Int) = t("Загрузка модели… $percent%", "Downloading the model… $percent%")
    fun downloadFailed(message: String) = t("Не удалось скачать: $message", "Download failed: $message")
    val modelReady = t("Модель фонем загружена.", "The phoneme model is ready.")
    val startLive = t("Начать (фонемы)", "Start live")
    val stopLive = t("Стоп", "Stop")
    val runPhonemes = t("Фонемы", "Phonemes")
    val loadingModel = t("Загрузка модели в память…", "Loading the model…")
    fun unfit(ref: String) = t(
        "В отрывке есть аят без фонетической записи ($ref): эта проверка его не умеет.",
        "The passage has an ayah without a phonetic script ($ref): this engine cannot check it.",
    )
    val noPassage = t("Нет такого отрывка.", "No such passage.")
    fun wordsShown(shown: Int, total: Int) = t("слов: $shown из $total", "words: $shown of $total")
    fun liveTimings(ticks: Int, mel: Long, model: Long, follow: Long, max: Long, audioSeconds: Double) = t(
        "шаг $ticks: мел $mel мс, модель $model мс, сопоставление $follow мс; самый долгий шаг $max мс; запись %.1f с",
        "tick $ticks: mel $mel ms, model $model ms, follow $follow ms; slowest tick $max ms; audio %.1f s",
    ).format(audioSeconds)
    fun finalTimings(audioMs: Long, mel: Long, model: Long, judge: Long) = t(
        "запись %.1f с; мел $mel мс, модель $model мс, оценка $judge мс",
        "audio %.1f s; mel $mel ms, model $model ms, judge $judge ms",
    ).format(audioMs / 1000.0)

    companion object {
        /** Russian when the app is in Russian, English otherwise. */
        fun forApp(): VoiceProtoCopy =
            VoiceProtoCopy((LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()) == AppLanguage.RU)
    }
}
