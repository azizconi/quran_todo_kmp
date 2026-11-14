package tj.app.quran_todo.di

import org.koin.core.context.startKoin
import tj.app.quran_todo.ui.di.sharedUiModule

fun InitKoinIos() {
    startKoin {
        modules(commonModule, iosModule, viewModelModule, iosUiPlatformModule, sharedUiModule)
    }
}
