package tj.app.quran_todo.di

import org.koin.core.module.Module
import org.koin.dsl.module
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.repository.AyahTodoRepositoryImpl
import tj.app.quran_todo.data.repository.QuranRemoteRepositoryImpl
import tj.app.quran_todo.data.repository.QuranTodoRepositoryImpl
import tj.app.quran_todo.domain.repository.AyahTodoRepository
import tj.app.quran_todo.domain.repository.QuranRemoteRepository
import tj.app.quran_todo.domain.repository.QuranTodoRepository
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteByAyahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetAllUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahOnceUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase

val commonModule = module {
    single { get<QuranTodoDatabase>().getSurahTodoDao() }
    single { get<QuranTodoDatabase>().getAyahTodoDao() }
    single { get<QuranTodoDatabase>().getAyahNoteDao() }
    single { get<QuranTodoDatabase>().getAyahReviewDao() }
    single { get<QuranTodoDatabase>().getFocusSessionDao() }
    single { get<QuranTodoDatabase>().getChapterNameDao() }
    single { get<QuranTodoDatabase>().getChapterNameCacheDao() }
    single { get<QuranTodoDatabase>().getQuranDao() }

    // Repositories
    single<QuranTodoRepository> { QuranTodoRepositoryImpl(get()) }
    single<QuranRemoteRepository> { QuranRemoteRepositoryImpl(get(), get()) }
    single<AyahTodoRepository> { AyahTodoRepositoryImpl(get()) }


    // Use Cases
    single<TodoDeleteSurahUseCase> { TodoDeleteSurahUseCase(get()) }
    single<TodoDeleteSurahByNumberUseCase> { TodoDeleteSurahByNumberUseCase(get()) }
    single<TodoUpsertSurahUseCase> { TodoUpsertSurahUseCase(get()) }
    single<TodoGetSurahListUseCase> { TodoGetSurahListUseCase(get()) }
    single<GetCompleteQuranUseCase> { GetCompleteQuranUseCase(get()) }
    single<AyahTodoGetBySurahUseCase> { AyahTodoGetBySurahUseCase(get()) }
    single<AyahTodoGetBySurahOnceUseCase> { AyahTodoGetBySurahOnceUseCase(get()) }
    single<AyahTodoGetAllUseCase> { AyahTodoGetAllUseCase(get()) }
    single<AyahTodoUpsertUseCase> { AyahTodoUpsertUseCase(get()) }
    single<AyahTodoDeleteBySurahUseCase> { AyahTodoDeleteBySurahUseCase(get()) }
    single<AyahTodoDeleteByAyahUseCase> { AyahTodoDeleteByAyahUseCase(get()) }
}


expect val viewModelModule: Module
