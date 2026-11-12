package tj.app.quran_todo.ui.reminder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for the notification permission where the system needs it (Android 13 and later) and reports the answer. Shared by the
 * settings switch and the question on the lesson summary. The returned function starts the request; where no request is needed it
 * reports `true` at once. [onResult] gets `true` when the reminder may be turned on.
 */
@Composable
internal fun rememberReminderPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> latest(granted) }
    return {
        if (ReminderPermission.needsRequest(context)) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            latest(true)
        }
    }
}
