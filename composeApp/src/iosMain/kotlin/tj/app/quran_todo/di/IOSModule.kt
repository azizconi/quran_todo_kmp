package tj.app.quran_todo.di

import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import platform.Foundation.NSLog
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.getRoomDatabase
import tj.app.quran_todo.data.remote.DataDebug
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.database.getDatabaseBuilder
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.stats.StatsViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
val iosModule = module {
    single<QuranTodoDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    // A debug binary logs request lines and data timings; a release binary logs nothing.
    single {
        DataDebug(enabled = Platform.isDebugBinary, sink = { message -> NSLog("QuranHifzData: %@", message) })
    }

    single { createHttpClient(Darwin.create(), debug = get<DataDebug>().enabled) }
}


actual val viewModelModule = module {
    singleOf(::HomeViewModel)
    singleOf(::SurahViewModel)
    singleOf(::StatsViewModel)
}
