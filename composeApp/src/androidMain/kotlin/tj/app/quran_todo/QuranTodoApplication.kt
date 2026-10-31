package tj.app.quran_todo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.di.androidModule
import tj.app.quran_todo.di.commonModule
import tj.app.quran_todo.di.viewModelModule
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.di.uiModules
import tj.app.quran_todo.ui.widget.LessonWidgetUpdater

class QuranTodoApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        AndroidContextHolder.context = this
        AppTelemetry.initialize()

        startKoin {
            androidContext(this@QuranTodoApplication)
            modules(listOf(commonModule, androidModule, viewModelModule) + uiModules)
        }

        // Turns the progress of the old model (surah/ayah statuses, SM-2 reviews, weak ayahs) into passages, once.
        // The widget "Lesson of the day" is redrawn when what it shows changes (task F5b); no polling.
        LessonWidgetUpdater.start(this)

        HifzStartup.importLegacyInBackground { KoinPlatform.getKoin().get<HifzRepository>() }
    }
}
