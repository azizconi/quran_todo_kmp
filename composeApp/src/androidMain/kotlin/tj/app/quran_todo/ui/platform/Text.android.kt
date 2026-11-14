package tj.app.quran_todo.ui.platform

import java.text.Normalizer

actual fun decomposeCanonically(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
