package tj.app.quran_todo.ui.platform

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@OptIn(BetaInteropApi::class)
actual fun decomposeCanonically(text: String): String = (text as NSString).decomposedStringWithCanonicalMapping
