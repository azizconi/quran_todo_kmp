package tj.app.quran_todo.bridge

import kotlinx.coroutines.flow.StateFlow
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.A11yCopy
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.i18n.AudioCopy
import tj.app.quran_todo.ui.i18n.AyahActionsCopy
import tj.app.quran_todo.ui.i18n.DownloadsCopy
import tj.app.quran_todo.ui.i18n.DrillCopy
import tj.app.quran_todo.ui.i18n.GoToCopy
import tj.app.quran_todo.ui.i18n.HifzCopy
import tj.app.quran_todo.ui.i18n.HintsCopy
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.KhatmCopy
import tj.app.quran_todo.ui.i18n.LessonCardCopy
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.LessonListenCopy
import tj.app.quran_todo.ui.i18n.ListenCopy
import tj.app.quran_todo.ui.i18n.MushafCopy
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PagesCopy
import tj.app.quran_todo.ui.i18n.PauseCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.i18n.PlayerRepeatCopy
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.QuizCopy
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.i18n.ReminderCopy
import tj.app.quran_todo.ui.i18n.ReportCopy
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.i18n.SimilarCopy
import tj.app.quran_todo.ui.i18n.SummaryExtrasCopy
import tj.app.quran_todo.ui.i18n.TabsCopy
import tj.app.quran_todo.ui.i18n.TafsirCopy
import tj.app.quran_todo.ui.i18n.TajweedCopy
import tj.app.quran_todo.ui.i18n.TransferCopy
import tj.app.quran_todo.ui.i18n.TranslationCopy
import tj.app.quran_todo.ui.i18n.TrustCopy
import tj.app.quran_todo.ui.i18n.VoiceCopy
import tj.app.quran_todo.ui.i18n.WelcomeBackCopy
import tj.app.quran_todo.ui.i18n.WidgetCopy
import tj.app.quran_todo.ui.i18n.a11yCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy
import tj.app.quran_todo.ui.i18n.audioCopy
import tj.app.quran_todo.ui.i18n.ayahActionsCopy
import tj.app.quran_todo.ui.i18n.downloadsCopy
import tj.app.quran_todo.ui.i18n.drillCopy
import tj.app.quran_todo.ui.i18n.goToCopy
import tj.app.quran_todo.ui.i18n.hifzCopy
import tj.app.quran_todo.ui.i18n.hintsCopy
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.khatmCopy
import tj.app.quran_todo.ui.i18n.lessonCardCopy
import tj.app.quran_todo.ui.i18n.lessonCopy
import tj.app.quran_todo.ui.i18n.lessonListenCopy
import tj.app.quran_todo.ui.i18n.listenCopy
import tj.app.quran_todo.ui.i18n.mushafCopy
import tj.app.quran_todo.ui.i18n.onboardingCopy
import tj.app.quran_todo.ui.i18n.pagesCopy
import tj.app.quran_todo.ui.i18n.pauseCopy
import tj.app.quran_todo.ui.i18n.planCopy
import tj.app.quran_todo.ui.i18n.playerRepeatCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.i18n.quizCopy
import tj.app.quran_todo.ui.i18n.readerCopy
import tj.app.quran_todo.ui.i18n.reminderCopy
import tj.app.quran_todo.ui.i18n.reportCopy
import tj.app.quran_todo.ui.i18n.settingsCopy
import tj.app.quran_todo.ui.i18n.similarCopy
import tj.app.quran_todo.ui.i18n.summaryExtrasCopy
import tj.app.quran_todo.ui.i18n.tabsCopy
import tj.app.quran_todo.ui.i18n.tafsirCopy
import tj.app.quran_todo.ui.i18n.tajweedCopy
import tj.app.quran_todo.ui.i18n.transferCopy
import tj.app.quran_todo.ui.i18n.translationCopy
import tj.app.quran_todo.ui.i18n.trustCopy
import tj.app.quran_todo.ui.i18n.voiceCopy
import tj.app.quran_todo.ui.i18n.welcomeBackCopy
import tj.app.quran_todo.ui.i18n.widgetCopy
import tj.app.quran_todo.ui.whatsnew.WhatsNewCopy
import tj.app.quran_todo.ui.whatsnew.whatsNewCopy

/**
 * The copy (every string of the UI layer) in the current language of the app, for SwiftUI: `IosCopy.shared.homeCopy().lessonTitle`.
 * It is the same Kotlin source as on Android. After [IosUi.setLanguage] a screen reads its copy again (observe [IosUi.language]).
 * The functions that take a language (`homeCopy(language)`) are also reachable from Swift as top-level functions.
 */
object IosCopy {
    private val language: StateFlow<AppLanguage> get() = IosUi.language

    fun a11yCopy(): A11yCopy = a11yCopy(language.value)
    fun analyticsCopy(): AnalyticsCopy = analyticsCopy(language.value)
    fun audioCopy(): AudioCopy = audioCopy(language.value)
    fun ayahActionsCopy(): AyahActionsCopy = ayahActionsCopy(language.value)
    fun downloadsCopy(): DownloadsCopy = downloadsCopy(language.value)
    fun drillCopy(): DrillCopy = drillCopy(language.value)
    fun goToCopy(): GoToCopy = goToCopy(language.value)
    fun hifzCopy(): HifzCopy = hifzCopy(language.value)
    fun hintsCopy(): HintsCopy = hintsCopy(language.value)
    fun homeCopy(): HomeCopy = homeCopy(language.value)
    fun khatmCopy(): KhatmCopy = khatmCopy(language.value)
    fun lessonCardCopy(): LessonCardCopy = lessonCardCopy(language.value)
    fun lessonCopy(): LessonCopy = lessonCopy(language.value)
    fun lessonListenCopy(): LessonListenCopy = lessonListenCopy(language.value)
    fun listenCopy(): ListenCopy = listenCopy(language.value)
    fun mushafCopy(): MushafCopy = mushafCopy(language.value)
    fun onboardingCopy(): OnboardingCopy = onboardingCopy(language.value)
    fun pagesCopy(): PagesCopy = pagesCopy(language.value)
    fun pauseCopy(): PauseCopy = pauseCopy(language.value)
    fun planCopy(): PlanCopy = planCopy(language.value)
    fun playerRepeatCopy(): PlayerRepeatCopy = playerRepeatCopy(language.value)
    fun progressCopy(): ProgressCopy = progressCopy(language.value)
    fun quizCopy(): QuizCopy = quizCopy(language.value)
    fun readerCopy(): ReaderCopy = readerCopy(language.value)
    fun reminderCopy(): ReminderCopy = reminderCopy(language.value)
    fun reportCopy(): ReportCopy = reportCopy(language.value)
    fun settingsCopy(): SettingsCopy = settingsCopy(language.value)
    fun similarCopy(): SimilarCopy = similarCopy(language.value)
    fun summaryExtrasCopy(): SummaryExtrasCopy = summaryExtrasCopy(language.value)
    fun tabsCopy(): TabsCopy = tabsCopy(language.value)
    fun tafsirCopy(): TafsirCopy = tafsirCopy(language.value)
    fun tajweedCopy(): TajweedCopy = tajweedCopy(language.value)
    fun transferCopy(): TransferCopy = transferCopy(language.value)
    fun translationCopy(): TranslationCopy = translationCopy(language.value)
    fun trustCopy(): TrustCopy = trustCopy(language.value)
    fun voiceCopy(): VoiceCopy = voiceCopy(language.value)
    fun welcomeBackCopy(): WelcomeBackCopy = welcomeBackCopy(language.value)
    fun whatsNewCopy(): WhatsNewCopy = whatsNewCopy(language.value)
    fun widgetCopy(): WidgetCopy = widgetCopy(language.value)
}
