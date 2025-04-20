package tj.app.quran_todo.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.repository.QuranRemoteRepositoryImpl
import tj.app.quran_todo.data.repository.QuranTodoRepositoryImpl
import tj.app.quran_todo.domain.repository.QuranRemoteRepository
import tj.app.quran_todo.domain.repository.QuranTodoRepository
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase

val commonModule = module {
    single { get<QuranTodoDatabase>().getSurahTodoDao() }
    single { get<QuranTodoDatabase>().getQuranDao() }

    // Repositories
    single<QuranTodoRepository> { QuranTodoRepositoryImpl(get()) }
    single<QuranRemoteRepository> { QuranRemoteRepositoryImpl(get(), get()) }


    // Use Cases
    single<TodoDeleteSurahUseCase> { TodoDeleteSurahUseCase(get()) }
    single<TodoUpsertSurahUseCase> { TodoUpsertSurahUseCase(get()) }
    single<TodoGetSurahListUseCase> { TodoGetSurahListUseCase(get()) }
    single<GetCompleteQuranUseCase> { GetCompleteQuranUseCase(get()) }
}


expect val viewModelModule: Module