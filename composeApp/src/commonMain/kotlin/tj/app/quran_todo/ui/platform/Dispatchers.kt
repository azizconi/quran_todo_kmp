package tj.app.quran_todo.ui.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** `Dispatchers.IO` for common code (the coroutines library only declares it on the JVM and Native). */
expect val Dispatchers.IO: CoroutineDispatcher
