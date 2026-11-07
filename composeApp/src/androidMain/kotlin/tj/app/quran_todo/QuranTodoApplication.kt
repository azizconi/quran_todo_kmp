package tj.app.quran_todo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import io.ktor.client.HttpClient
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.di.androidModule
import tj.app.quran_todo.di.commonModule
import tj.app.quran_todo.di.viewModelModule
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.components.spotlight.SharedPreferencesSpotlightStore
import tj.app.quran_todo.ui.di.uiModules
import tj.app.quran_todo.ui.downloads.DownloadsManager
import tj.app.quran_todo.ui.onboarding.OnboardingStartup
import tj.app.quran_todo.ui.tafsir.TafsirStore
import tj.app.quran_todo.ui.tajweed.TajweedStore
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.widget.LessonWidgetUpdater

class QuranTodoApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.context = this
        warmUpPreferences()
        // The onboarding decision (flags in the SharedPreferences) is made here, not on the main thread: the splash waits for it.
        Thread(OnboardingStartup::resolveInBackground, "onboarding-startup").start()
        AppTelemetry.initialize()

        startKoin {
            androidContext(this@QuranTodoApplication)
            modules(listOf(commonModule, androidModule, viewModelModule) + uiModules)
        }

        // Turns the progress of the old model (surah/ayah statuses, SM-2 reviews, weak ayahs) into passages, once.
        // The widget "Lesson of the day" is redrawn when what it shows changes (task F5b); no polling.
        LessonWidgetUpdater.start(this)
        warmUpStores()

        HifzStartup.importLegacyInBackground { KoinPlatform.getKoin().get<HifzRepository>() }
    }

    /**
     * Opens the SharedPreferences files of the app on a worker thread. The first getSharedPreferences() of a file starts reading it, and
     * the first read waits for it: done on the main thread, that is the disk work StrictMode shows at a cold start (hundreds of ms, seconds
     * on a busy phone). The instances are cached by the context, so the main thread later finds the file already read.
     */
    private fun warmUpPreferences() {
        Thread({
            listOf(
                "quran_todo_prefs", // language, theme, reading settings (UserSettingsStorage, LanguageStorage, ThemeStorage)
                UiPrefs.PREFS_NAME,
                SharedPreferencesSpotlightStore.FileName,
                "downloads_prefs",
                "quran_todo_cloud_sync",
                "debug_today",
            ).forEach { name -> getSharedPreferences(name, MODE_PRIVATE) }
        }, "prefs-warmup").start()
    }

    /**
     * Creates the file-based singletons on a worker thread, so that the main thread finds them ready when the first screens ask for them.
     * Each of them touches the disk when it is made (the folders of the downloads and the audio cache, the saved queue, the folders of
     * the commentaries and the tajweed file). They are lazy and thread-safe: a screen that asks before this is done just waits for the
     * same object, nothing is made twice. A failure here is not fatal: the screen that needs the object makes it again and meets it.
     */
    private fun warmUpStores() {
        val koin = KoinPlatform.getKoin()
        Thread({
            listOf<() -> Unit>(
                { DownloadStorage.shared },
                { AudioCacheStorage.shared },
                { TafsirStore.shared },
                { TajweedStore.shared },
                { koin.get<UiPrefs>() },
                { koin.get<DownloadsManager>() },
                { koin.get<HttpClient>() },
            ).forEach { warm -> runCatching(warm) }
        }, "stores-warmup").start()
    }
}
