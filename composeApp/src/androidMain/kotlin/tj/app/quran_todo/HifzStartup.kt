package tj.app.quran_todo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.hifz.HifzRepository

/**
 * Runs the one-time import of the old progress into passages in the background, when the app starts. It is safe to run
 * on every start: the repository does nothing once the mark `hifz_meta.legacy_import = done` is set. On an error the mark
 * is not set (the import is one transaction), so it is tried again at the next start; the error goes to the telemetry
 * with a fixed context and no free text.
 */
object HifzStartup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** [repository] is resolved on the background thread too, so that creating it does not slow the start of the app. */
    fun importLegacyInBackground(repository: () -> HifzRepository) {
        scope.launch {
            try {
                repository().importLegacyIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppTelemetry.logError(throwable = error, context = "hifz_legacy_import_failed")
            }
        }
    }
}
