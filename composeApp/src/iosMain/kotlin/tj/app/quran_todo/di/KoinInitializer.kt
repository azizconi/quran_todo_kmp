package tj.app.quran_todo.di

import org.koin.core.context.startKoin

fun InitKoinIos() {
    startKoin {
        modules(commonModule, iosModule, viewModelModule)
    }
}