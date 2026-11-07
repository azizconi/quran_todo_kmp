package tj.app.quran_todo.di

import android.content.pm.ApplicationInfo
import android.util.Log
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.getRoomDatabase
import tj.app.quran_todo.data.remote.DataDebug
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.database.androidDatabaseBuilder
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.presentation.stats.StatsViewModel

val androidModule = module {
    single<QuranTodoDatabase> {
        getRoomDatabase(androidDatabaseBuilder(androidContext()))
    }

    // Debug builds log request lines and data timings; a release build (not debuggable) logs nothing.
    single {
        val debuggable = androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        DataDebug(enabled = debuggable, sink = { message -> Log.d("QuranHifzData", message) })
    }

    single { createHttpClient(OkHttp.create(), debug = get<DataDebug>().enabled) }

}


actual val viewModelModule = module {
    singleOf(::HomeViewModel)
    singleOf(::SurahViewModel)
    singleOf(::StatsViewModel)
}
