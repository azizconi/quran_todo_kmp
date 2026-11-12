package tj.app.quran_todo.ui.store

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.common.analytics.AppTelemetry

/**
 * Google Play in-app updates.
 *
 * - A version published with update priority 4 or 5 (set through the Play Developer API, `inAppUpdatePriority`) is installed
 *   with the full-screen **immediate** flow: the person cannot use the old version until it is installed or they cancel.
 * - Any other new version is offered with the **flexible** flow: Play's own dialog asks, the download runs in the background
 *   while the app is used, and once it is downloaded [readyToInstall] turns true and the app shows the "Restart" banner
 *   ([UpdateReadyBanner]). A declined offer is not repeated for [AskAgainDays] days for the same version.
 *
 * Create one instance per activity as a property (it registers an activity-result launcher, which must happen before the
 * activity is started), call [check] from `onCreate` and [onResume] from `onResume`. Outside a Play install (debug builds from
 * Android Studio, sideloaded APKs) Play answers with an error and nothing is shown.
 */
class AppUpdates(private val activity: ComponentActivity) {
    // Lazy: this object is made as a property of the activity, when the activity has no context yet (it gets one in onCreate).
    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(activity.applicationContext) }
    private val prefs by lazy { activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private var offeredVersion: Int = 0

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK && offeredVersion != 0) {
                // Declined (or the flow failed): do not ask again for this version for a while.
                prefs.edit()
                    .putInt(KEY_DECLINED_VERSION, offeredVersion)
                    .putLong(KEY_DECLINED_AT, System.currentTimeMillis())
                    .apply()
            }
        }

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> markReady()
            InstallStatus.INSTALLED, InstallStatus.FAILED, InstallStatus.CANCELED -> readyFlow.value = false
            else -> Unit
        }
    }

    /** Asks Play once whether a newer version exists and starts the right flow. */
    fun check() {
        manager.registerListener(installListener)
        manager.appUpdateInfo
            .addOnSuccessListener { info -> handle(info) }
            .addOnFailureListener { error -> Log.i(TAG, "No update information from Play", error) }
    }

    /**
     * Back in the app: a download that finished while it was in the background is offered for install again, and an immediate
     * update that was interrupted (the app went away mid-install) is resumed, as Play requires.
     */
    fun onResume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> markReady()
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    start(info, AppUpdateType.IMMEDIATE)
            }
        }
    }

    fun onDestroy() {
        manager.unregisterListener(installListener)
    }

    private fun handle(info: AppUpdateInfo) {
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            markReady()
            return
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return
        val version = info.availableVersionCode()
        when {
            info.updatePriority() >= HighPriority && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                start(info, AppUpdateType.IMMEDIATE)
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && !recentlyDeclined(version) ->
                start(info, AppUpdateType.FLEXIBLE)
        }
    }

    private fun start(info: AppUpdateInfo, type: Int) {
        if (activity.isFinishing || activity.isDestroyed) return
        offeredVersion = info.availableVersionCode()
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
            AppTelemetry.logEvent(
                name = "app_update_offered",
                params = mapOf("type" to if (type == AppUpdateType.IMMEDIATE) "immediate" else "flexible"),
            )
        } catch (error: Exception) {
            Log.w(TAG, "The update flow could not be started", error)
        }
    }

    private fun recentlyDeclined(version: Int): Boolean {
        if (prefs.getInt(KEY_DECLINED_VERSION, 0) != version) return false
        val declinedAt = prefs.getLong(KEY_DECLINED_AT, 0L)
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - declinedAt) < AskAgainDays
    }

    private fun markReady() {
        readyFlow.value = true
        installer = { manager.completeUpdate() }
    }

    companion object {
        private const val TAG = "AppUpdates"
        private const val PREFS_NAME = "store_prompts"
        private const val KEY_DECLINED_VERSION = "update_declined_version"
        private const val KEY_DECLINED_AT = "update_declined_at"
        private const val HighPriority = 4
        private const val AskAgainDays = 3L

        private val readyFlow = MutableStateFlow(false)
        private var installer: (() -> Unit)? = null

        /** True once a flexible update is downloaded and waits for the restart that installs it. */
        val readyToInstall: StateFlow<Boolean> = readyFlow.asStateFlow()

        /** Installs the downloaded update: Play restarts the app into the new version. */
        fun installNow() {
            installer?.invoke()
        }
    }
}

