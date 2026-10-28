package tj.app.quran_todo.ui.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.ReaderAudioState

/** What the media session needs to name an ayah: the surah and the reciter in the language of the app. */
internal interface PlaybackMetadata {
    fun surahTitle(surahNumber: Int): String
    fun reciterName(): String

    /** Encoded image shown as the cover on the lock screen and in the notification, or null. */
    fun artwork(): ByteArray?
}

/** The playback controller's state in the terms of a media player. Pure, see [sessionSnapshotOf]. */
internal data class SessionSnapshot(
    val hasMedia: Boolean,
    val surahNumber: Int,
    val ayahCount: Int,
    val index: Int,
    /** "Play when ready": the ayah sequence is running (also during the silent pause between ayahs). */
    val playWhenReady: Boolean,
    /** The next ayah is being fetched. */
    val buffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** The position is moving right now, so the system may extrapolate it between updates. */
    val advancing: Boolean,
    val hasError: Boolean,
) {
    /** The part that makes the media session send an update; the position alone does not. */
    fun withoutPosition() = copy(positionMs = 0)
}

internal fun sessionSnapshotOf(now: NowPlaying?, state: ReaderAudioState): SessionSnapshot {
    if (now == null || now.ayahs.isEmpty()) {
        return SessionSnapshot(
            hasMedia = false, surahNumber = 0, ayahCount = 0, index = 0, playWhenReady = false,
            buffering = false, positionMs = 0, durationMs = 0, advancing = false, hasError = false,
        )
    }
    return SessionSnapshot(
        hasMedia = true,
        surahNumber = now.surahNumber,
        ayahCount = now.ayahs.size,
        index = state.index.coerceIn(now.ayahs.indices),
        playWhenReady = state.isPlaying,
        buffering = state.isPlaying && state.isPreparing,
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        advancing = state.isPlaying && !state.isPreparing && state.pauseRemainingSeconds == 0 &&
            state.durationMs > 0 && state.positionMs < state.durationMs,
        hasError = state.hasError,
    )
}

/**
 * The player the media session (notification, lock screen, headset, Bluetooth) talks to. It plays nothing:
 * it mirrors [PlaybackController] (the ayahs of the surah are its playlist, the current ayah its current
 * item) and turns the commands it receives into calls to the controller.
 *
 * Without audio it is idle with an empty playlist, which removes the notification. A stopped or failed
 * sequence is shown as paused, so the play button of the notification retries.
 */
internal class PlaybackSessionPlayer(
    private val controller: PlaybackController,
    private val metadata: PlaybackMetadata,
    private val speed: () -> Float,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    private var items: List<MediaItemData> = emptyList()
    private var itemsFor: ItemsKey? = null

    private class ItemsKey(val now: NowPlaying, val title: String, val reciter: String)

    /**
     * Keeps the session up to date: the player is told to refresh whenever the playlist, the current ayah,
     * the play state or the duration change. [reciter] changes the artist line. The 5-times-a-second position
     * ticks do not refresh anything: the position is extrapolated, and while it is moving the extrapolation
     * is corrected every [PositionResyncMillis] with the real position (it stands still while the system
     * holds the audio back, for example during a phone call).
     */
    fun attach(scope: CoroutineScope, reciter: Flow<Any>) {
        scope.launch {
            combine(
                controller.nowPlaying,
                controller.state.map { sessionSnapshotOf(controller.nowPlaying.value, it).withoutPosition() }
                    .distinctUntilChanged(),
                reciter,
            ) { _, _, _ -> }.collect { invalidateState() }
        }
        scope.launch {
            controller.state
                .map { sessionSnapshotOf(controller.nowPlaying.value, it).advancing }
                .distinctUntilChanged()
                .collectLatest { advancing ->
                    while (advancing) {
                        delay(PositionResyncMillis)
                        invalidateState()
                    }
                }
        }
    }

    override fun getState(): State {
        val now = controller.nowPlaying.value
        val snapshot = sessionSnapshotOf(now, controller.state.value)
        if (now == null || !snapshot.hasMedia) {
            return State.Builder().setAvailableCommands(IdleCommands).setPlaybackState(Player.STATE_IDLE).build()
        }
        val playlist = itemsFor(now).toMutableList()
        playlist[snapshot.index] = playlist[snapshot.index].buildUpon()
            .setDurationUs(if (snapshot.durationMs > 0) snapshot.durationMs * 1_000L else androidx.media3.common.C.TIME_UNSET)
            .build()
        val playbackSpeed = speed()
        return State.Builder()
            .setAvailableCommands(ActiveCommands)
            .setPlayWhenReady(snapshot.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (snapshot.buffering) Player.STATE_BUFFERING else Player.STATE_READY)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(snapshot.index)
            .setPlaybackParameters(PlaybackParameters(playbackSpeed))
            .setContentPositionMs(
                if (snapshot.advancing) {
                    PositionSupplier.getExtrapolating(snapshot.positionMs, playbackSpeed)
                } else {
                    PositionSupplier.getConstant(snapshot.positionMs)
                },
            )
            .build()
    }

    private fun itemsFor(now: NowPlaying): List<MediaItemData> {
        val title = metadata.surahTitle(now.surahNumber)
        val reciter = metadata.reciterName()
        val cached = itemsFor
        if (cached != null && cached.now === now && cached.title == title && cached.reciter == reciter) return items
        val artwork = metadata.artwork()
        items = now.ayahs.map { ayah ->
            val id = "${now.surahNumber}:${ayah.numberInSurah}"
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle("$title · ${ayah.numberInSurah}")
                .setArtist(reciter)
                .setAlbumTitle(title)
                .setTrackNumber(ayah.numberInSurah)
                .setTotalTrackCount(now.ayahs.size)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .apply { if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                .build()
            MediaItemData.Builder(id)
                .setMediaItem(MediaItem.Builder().setMediaId(id).setMediaMetadata(mediaMetadata).build())
                .setIsSeekable(false)
                .build()
        }
        itemsFor = ItemsKey(now, title, reciter)
        return items
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val state = controller.state.value
        if (playWhenReady && !state.isPlaying) {
            val last = controller.nowPlaying.value?.ayahs?.lastIndex
            val finishedSurah = !state.canResume && last != null && state.index == last &&
                state.durationMs > 0 && state.positionMs >= state.durationMs
            if (finishedSurah) controller.select(0)
            controller.toggle()
        } else if (!playWhenReady && state.isPlaying) {
            controller.toggle()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val current = controller.state.value.index
        when (mediaItemIndex) {
            current + 1 -> controller.next()
            current - 1 -> controller.previous()
            else -> {
                // The same ayah ("previous" after its first seconds) or a jump: play that ayah from its start.
                controller.select(mediaItemIndex)
                controller.toggle()
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val PositionResyncMillis = 2_000L

        val IdleCommands: Player.Commands = Player.Commands.Builder()
            .addAll(Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA)
            .build()

        val ActiveCommands: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
            )
            .build()
    }
}
