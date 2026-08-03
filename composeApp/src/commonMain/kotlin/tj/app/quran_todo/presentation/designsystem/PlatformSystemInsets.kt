package tj.app.quran_todo.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adds lower system-bar clearance only on hosts whose Compose content is
 * edge-to-edge. iOS SwiftUI already constrains the embedded library view to
 * its container safe area, so applying it there would double the inset.
 */
@Composable
internal expect fun Modifier.platformBottomSystemBarsPadding(): Modifier
