package tj.app.quran_todo.ui.platform

/**
 * Canonical decomposition (Unicode NFD) of [text]: accented letters become a base letter plus combining marks, which the surah
 * search then drops.
 *
 * iOS implementation: `NSString.decomposedStringWithCanonicalMapping`.
 */
expect fun decomposeCanonically(text: String): String
