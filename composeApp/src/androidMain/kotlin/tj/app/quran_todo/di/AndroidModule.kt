package tj.app.quran_todo.di

import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.database.androidDatabaseBuilder
import tj.app.quran_todo.presentation.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.presentation.stats.StatsViewModel

val androidModule = module {
    single<QuranTodoDatabase> {
        androidDatabaseBuilder(androidContext()).build()
    }

    single { createHttpClient(OkHttp.create()) }

}


actual val viewModelModule = module {
    singleOf(::HomeViewModel)
    singleOf(::SurahViewModel)
    singleOf(::StatsViewModel)
}
