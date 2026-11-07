package tj.app.quran_todo.ui.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.audio.monotonicClock
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Builds the app's [PlaybackController] (a Koin single): the reader session sequences the ayahs, one
 * ExoPlayer ([Media3Engine]) plays them from the audio cache, [AyahAudioSource] chooses the reciter's file
 * and prefetches the next two ayahs, and a [MediaControllerLink] starts the background service when
 * playback is started for the first time.
 */
internal fun createPlaybackController(context: Context, prefs: UiPrefs): PlaybackController {
    val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var controller: PlaybackController

    val output = ExoReaderAudioOutput(
        engine = Media3Engine(appContext),
        scope = scope,
        pausedBySystem = { controller.pauseBySystem() },
    )
    val source = AyahAudioSource(
        files = AudioCacheFiles(AudioCache()),
        reciter = { prefs.reciter.value },
        playlist = { controller.nowPlaying.value?.ayahs.orEmpty() },
        scope = scope,
    )
    val session = ReaderAudioSession(
        scope,
        output,
        { ayah, surah -> source.resolve(ayah, surah) },
        monotonicClock(),
        { surah, ayah -> UserSettingsStorage.saveLastPlaybackPosition(surah, ayah) },
    )
    controller = PlaybackController(
        session = session,
        timerScope = scope,
        reciter = prefs.reciter,
        serviceLink = MediaControllerLink(appContext),
        extras = output,
    )
    return controller
}

/**
 * Binds the app to [PlaybackService] through a Media3 [MediaController]. The controller itself is never
 * used to send commands: connecting is what creates the service and its media session, which then
 * mirrors the [PlaybackController] and puts the service in the foreground while audio plays.
 */
internal class MediaControllerLink(private val context: Context) : PlaybackServiceLink {
    private var connection: ListenableFuture<MediaController>? = null

    override fun ensureConnected() {
        if (connection != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                // The service went away (the app was swiped from the recents): bind again on the next start.
                override fun onDisconnected(controller: MediaController) {
                    connection = null
                }
            })
            .buildAsync()
        connection = future
        future.addListener(
            {
                // A failed connection is retried on the next start.
                if (runCatching { future.get() }.isFailure && connection === future) connection = null
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun release() {
        val current = connection ?: return
        connection = null
        MediaController.releaseFuture(current)
    }
}
