package tj.app.quran_todo.ui.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import tj.app.quran_todo.MainActivity
import tj.app.quran_todo.R
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Background playback (docs/redesign/API.md, "Audio"): a Media3 media session that shows the
 * [PlaybackController]'s audio to the system. Media3 keeps the service in the foreground and posts the
 * notification (previous, play/pause, next, the app's status-bar icon, a tap opens the app) while audio plays; the
 * lock screen, the headset and Bluetooth buttons go through the same session.
 *
 * The service is created when the app binds to it for the first time ([MediaControllerLink]).
 * When the app is swiped away from the recents the audio stops and the service stops itself.
 */
class PlaybackService : MediaSessionService() {
    private val controller: PlaybackController by inject()
    private val home: HomeViewModel by inject()
    private val prefs: UiPrefs by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        // Media3 keeps a paused session in the foreground for up to 10 minutes, which makes its notification
        // impossible to swipe away. A paused recitation leaves the foreground at once: the notification stays
        // (the lock screen and the play button still work) but can be dismissed.
        setForegroundServiceTimeoutMs(0)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_quran) },
        )

        val player = PlaybackSessionPlayer(
            controller = controller,
            metadata = AppPlaybackMetadata(this, ::surahTitle) { prefs.reciter.value.name(currentLanguage()) },
            speed = { SurahReaderPreferences.load().playbackSpeed },
        )
        player.attach(scope, prefs.reciter)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()

        // The gap between two ayahs is a coroutine delay: the CPU must not sleep through it.
        scope.launch {
            controller.state.map { it.isPlaying }.distinctUntilChanged().collect(::holdWakeLock)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession.takeIf { controllerInfo.packageName == packageName || controllerInfo.isTrusted }

    /** Swiped away from the recents: the audio stops, nothing is left running. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.stop()
        controller.disconnectService()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        holdWakeLock(false)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private fun holdWakeLock(hold: Boolean) {
        if (hold) {
            val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "quran_todo:playback")
                .apply { setReferenceCounted(false) }
                .also { wakeLock = it }
            if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun openAppIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun currentLanguage(): AppLanguage = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()

    /** The surah title by the one naming rule of the Android UI ([SurahNames]), as on the home screen. */
    private fun surahTitle(surahNumber: Int): String {
        val state = home.uiState.value
        return SurahNames.displayName(
            language = currentLanguage(),
            russianName = state.surahList.firstOrNull { it.surahNumber == surahNumber }?.name,
            transliteration = state.chapterNames[surahNumber]?.transliteration,
            fallback = state.completeQuran.firstOrNull { it.surah.number == surahNumber }?.surah?.englishName
                ?.takeIf { it.isNotBlank() } ?: "№ $surahNumber",
        )
    }

    private companion object {
        const val CHANNEL_ID = "quran_playback"
        const val WAKE_LOCK_TIMEOUT_MS = 3L * 60 * 60 * 1000
    }
}

/** Names come from the app's own naming rules; the cover is the launcher icon, encoded once. */
internal class AppPlaybackMetadata(
    private val context: Context,
    private val surahName: (Int) -> String,
    private val reciter: () -> String,
) : PlaybackMetadata {
    private val artwork: ByteArray? by lazy { encodeLauncherIcon(context) }

    override fun surahTitle(surahNumber: Int): String = surahName(surahNumber)

    override fun reciterName(): String = reciter()

    override fun artwork(): ByteArray? = artwork

    private fun encodeLauncherIcon(context: Context): ByteArray? = runCatching {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return@runCatching null
        val bitmap = icon.toBitmap(ARTWORK_SIZE, ARTWORK_SIZE)
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }.getOrNull()

    private companion object {
        const val ARTWORK_SIZE = 256
    }
}
