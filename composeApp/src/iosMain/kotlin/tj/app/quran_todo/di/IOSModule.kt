package tj.app.quran_todo.di

import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.database.getDatabaseBuilder
import tj.app.quran_todo.presentation.HomeViewModel

val iosModule = module {
    single<QuranTodoDatabase> {
        getDatabaseBuilder().build()
    }

    single { createHttpClient(Darwin.create()) }
}


actual val viewModelModule = module {
    singleOf(::HomeViewModel)
}