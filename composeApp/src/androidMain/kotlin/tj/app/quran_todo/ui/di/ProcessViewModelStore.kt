package tj.app.quran_todo.ui.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * A [ViewModelStoreOwner] that lives as long as the process.
 *
 * HomeViewModel, SurahViewModel and StatsViewModel are Koin singletons shared with iOS, but
 * `koinViewModel()` also puts them into the store of the current owner. Inside a NavHost that is the
 * back stack entry, and popping the entry clears the store: `onCleared()` runs and `viewModelScope`
 * is cancelled for a singleton that other screens keep using (for example, surah status changes on
 * the library stop working after visiting a reader). Legacy screens that call
 * `koinViewModel<HomeViewModel>()` therefore run inside [ProvideProcessViewModelStore].
 *
 * New screens should not need it: their own ViewModels get the shared ones through Koin constructor
 * injection, which never involves a store.
 */
object ProcessViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

@Composable
fun ProvideProcessViewModelStore(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides ProcessViewModelStoreOwner,
        content = content,
    )
}
