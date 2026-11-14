package tj.app.quran_todo.ui.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val Dispatchers.IO: CoroutineDispatcher get() = Dispatchers.IO
