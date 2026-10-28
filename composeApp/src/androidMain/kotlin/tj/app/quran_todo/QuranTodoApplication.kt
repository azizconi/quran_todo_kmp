package tj.app.quran_todo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.di.androidModule
import tj.app.quran_todo.di.commonModule
import tj.app.quran_todo.di.viewModelModule
import tj.app.quran_todo.ui.di.uiModules

class QuranTodoApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        AndroidContextHolder.context = this
        AppTelemetry.initialize()

        startKoin {
            androidContext(this@QuranTodoApplication)
            modules(listOf(commonModule, androidModule, viewModelModule) + uiModules)
        }

    }
}
