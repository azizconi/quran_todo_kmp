package tj.app.quran_todo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.di.androidModule
import tj.app.quran_todo.di.commonModule
import tj.app.quran_todo.di.viewModelModule

class QuranTodoApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        AndroidContextHolder.context = this

        startKoin {
            androidContext(this@QuranTodoApplication)
            modules(commonModule, androidModule, viewModelModule)
        }

    }
}
