package tj.app.quran_todo.ui.recording

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.VoiceCopy
import tj.app.quran_todo.ui.i18n.rememberVoiceCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhTheme

/** How long "Recording deleted · Undo" waits before the file is really removed. */
private const val UndoMillis = 5_000L

/**
 * Whether the sheet of "My reading" is open and whether it was opened by the record button (then it starts recording at once, asking
 * for the microphone first). The entry buttons call [openRecording] / [openList]; [VoiceSheetHost] draws the sheet.
 */
@Stable
class VoiceSheetState {
    var open by mutableStateOf(false)
        private set
    var startRecording by mutableStateOf(false)
        private set

    /** The record button: opens the sheet and starts recording (after the permission). */
    fun openRecording() {
        startRecording = true
        open = true
    }

    /** "My reading": opens the list of the recordings. */
    fun openList() {
        startRecording = false
        open = true
    }

    fun close() {
        open = false
        startRecording = false
    }
}

@Composable
fun rememberVoiceSheetState(): VoiceSheetState = remember { VoiceSheetState() }

/** Why the microphone cannot be used: refused once (ask again), or refused for good (only the settings of the phone can change it). */
private enum class MicDenial { Once, ForGood }

/**
 * The sheet "My reading" for the passage [key] ([title] is its name): the recordings with ⏯ (swipe a row to delete it, the snackbar
 * undoes it), "Record another" and "Listen to the reciter" ([onListenReciter] plays the same passage, once). While it records it
 * shows the time, the loudness and a big "Stop". The microphone is asked for at the first press of a record button; a refusal says
 * "Without the microphone there is nothing to record" and offers the settings when it is final.
 *
 * Closing the sheet (or leaving the screen) stops what runs: the recording is kept, the playing of a recording stops.
 */
@Composable
fun VoiceSheetHost(state: VoiceSheetState, key: PassageKey, title: String, onListenReciter: () -> Unit) {
    if (state.open) {
        VoiceSheet(key = key, title = title, startRecording = state.startRecording, onListenReciter = onListenReciter, onDismiss = state::close)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VoiceSheet(
    key: PassageKey,
    title: String,
    startRecording: Boolean,
    onListenReciter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QhTheme.colors
    val copy = rememberVoiceCopy()
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val controller = koinInject<VoiceController>()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recorder by controller.recorder.collectAsState()
    val player by controller.player.collectAsState()
    val notes by remember(controller, key) { controller.notes(key) }.collectAsState(initial = emptyList())
    val latestCopy by rememberUpdatedState(copy)
    var denial by remember { mutableStateOf<MicDenial?>(null) }

    // The rows the person has deleted: hidden at once, removed for good when the undo time is over (or the sheet closes).
    var hiddenFiles by remember { mutableStateOf(emptySet<java.io.File>()) }
    var undoable by remember { mutableStateOf<VoiceNote?>(null) }
    val commit: (VoiceNote) -> Unit = { note -> scope.launch { controller.delete(note.recording) } }
    LaunchedEffect(undoable) {
        val note = undoable ?: return@LaunchedEffect
        delay(UndoMillis)
        commit(note)
        undoable = null
    }
    val latestUndoable by rememberUpdatedState(undoable)
    DisposableEffect(controller) {
        onDispose {
            // The sheet is gone: a recording that runs is kept, the playing stops, a deleted row goes for good.
            controller.release()
            latestUndoable?.let { note -> controller.deleteNow(note.recording) }
        }
    }
    // The app goes to the background: the microphone is not available there, so the recording is kept and ends.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.release() }

    fun begin() {
        when (controller.start(key)) {
            StartResult.Started -> denial = null
            StartResult.AlreadyRecording -> Unit
            StartResult.Failed -> toaster.show(latestCopy.startFailed, ToastKind.Error)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            begin()
        } else {
            // Asked once, refused: the system will not show the question again after the next refusal.
            val activity = context.findActivity()
            val canAskAgain = activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            denial = if (canAskAgain) MicDenial.Once else MicDenial.ForGood
        }
    }
    val askAndRecord: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // The sheet was opened by the record button: the first press asks for the microphone and goes on to record.
    LaunchedEffect(Unit) { if (startRecording) askAndRecord() }

    val recordingThis = (recorder as? RecorderState.Recording)?.takeIf { it.key == key }
    val visibleNotes = notes.filter { it.recording.file !in hiddenFiles }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = copy.myReading,
                    style = QhTheme.type.title1,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = title, style = QhTheme.type.callout, color = colors.inkSecondary)
            }
            if (recordingThis != null) {
                RecordingPanel(copy = copy, state = recordingThis, onStop = {
                    val saved = controller.stop()
                    if (saved == null) toaster.show(copy.tooShort, ToastKind.Info)
                })
            } else {
                denial?.let { reason ->
                    DeniedCard(
                        copy = copy,
                        reason = reason,
                        onOpenSettings = { openAppSettings(context) },
                        onTryAgain = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visibleNotes.isEmpty()) {
                        Text(
                            text = copy.emptyList,
                            style = QhTheme.type.body,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    visibleNotes.forEach { note ->
                        // Keyed by the file: a row that goes does not hand its swipe state to the next one.
                        key(note.recording.file.path) {
                            NoteRow(
                                note = note,
                                copy = copy,
                                language = language,
                                player = player,
                                onToggle = { controller.toggle(note.recording.file) },
                                onDelete = {
                                    // A deleted recording does not go on playing.
                                    if (player.file == note.recording.file) controller.stopPlayback()
                                    // A delete before the undo time is over makes the previous one final.
                                    undoable?.let(commit)
                                    hiddenFiles = hiddenFiles + note.recording.file
                                    undoable = note
                                },
                            )
                        }
                    }
                }
                // One under the other: the names are long in some languages and the two do not fit side by side.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QhButton(
                        text = if (visibleNotes.isEmpty()) copy.record else copy.recordMore,
                        onClick = askAndRecord,
                        modifier = Modifier.fillMaxWidth(),
                        size = QhButtonSize.M,
                        icon = Icons.Rounded.Mic,
                    )
                    QhButton(
                        text = copy.listenReciter,
                        onClick = {
                            controller.stopPlayback()
                            onListenReciter()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = QhButtonVariant.Tonal,
                        size = QhButtonSize.M,
                        icon = Icons.Rounded.Headphones,
                    )
                }
                undoable?.let { _ ->
                    UndoBar(
                        copy = copy,
                        onUndo = {
                            val note = undoable
                            if (note != null) hiddenFiles = hiddenFiles - note.recording.file
                            undoable = null
                        },
                    )
                }
                Text(text = copy.sheetHint, style = QhTheme.type.label, color = colors.inkSecondary)
            }
        }
    }
}

// -- Recording ----------------------------------------------------------------------------------------------

/** While it records: the red dot with "Recording", the time, the loudness and a big "Stop". No pulse under reduced motion. */
@Composable
private fun RecordingPanel(copy: VoiceCopy, state: RecorderState.Recording, onStop: () -> Unit) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "record-dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "record-dot-alpha",
    )
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(if (reduced) 1f else pulse)
                    .clip(CircleShape)
                    .background(colors.danger),
            )
            Text(text = copy.recording, style = QhTheme.type.callout, color = colors.inkSecondary)
        }
        Text(
            text = VoiceFormat.clock(state.elapsedMs),
            style = QhTheme.type.display,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        QhLinearProgress(
            progress = VoiceFormat.barShare(state.level),
            height = 8.dp,
            color = colors.primary,
            trackColor = colors.surfaceMuted,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = copy.loudness },
        )
        // The button and its name are one target: a tap on the word stops the recording too.
        Column(
            Modifier
                .pressable(onClick = onStop, haptic = HapticKind.Success, shape = QhTheme.shapes.lg, onClickLabel = copy.stop)
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.danger),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = copy.stop, tint = colors.onDanger, modifier = Modifier.size(36.dp))
            }
            Text(text = copy.stop, style = QhTheme.type.title2, color = colors.ink)
        }
        Text(
            text = copy.recordingHint,
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeniedCard(copy: VoiceCopy, reason: MicDenial, onOpenSettings: () -> Unit, onTryAgain: () -> Unit) {
    val colors = QhTheme.colors
    QhCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = copy.deniedTitle, style = QhTheme.type.title2, color = colors.ink)
            Text(text = copy.deniedBody, style = QhTheme.type.callout, color = colors.inkSecondary)
            if (reason == MicDenial.ForGood) {
                QhButton(text = copy.openSettings, onClick = onOpenSettings, size = QhButtonSize.M, modifier = Modifier.fillMaxWidth())
            } else {
                QhButton(
                    text = copy.tryAgain,
                    onClick = onTryAgain,
                    size = QhButtonSize.M,
                    variant = QhButtonVariant.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -- The list -----------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteRow(
    note: VoiceNote,
    copy: VoiceCopy,
    language: tj.app.quran_todo.common.i18n.AppLanguage,
    player: PlayerState,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    val file = note.recording.file
    val loaded = player.file == file
    val playing = loaded && player.playing
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.clip(shape),
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.dueContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.due, modifier = Modifier.size(24.dp))
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted)
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .pressable(
                        onClick = onToggle,
                        haptic = HapticKind.Tick,
                        shape = CircleShape,
                        onClickLabel = if (playing) copy.pause else copy.play,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) copy.pause else copy.play,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = VoiceFormat.moment(note.recording.epochMillis, language),
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    maxLines = 1,
                )
                val duration = if (loaded && player.durationMs > 0) player.durationMs.toLong() else note.durationMs
                Text(
                    text = if (loaded && player.durationMs > 0) {
                        VoiceFormat.progress(player.positionMs.toLong(), duration)
                    } else {
                        VoiceFormat.clock(duration)
                    },
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                )
                if (loaded && player.durationMs > 0) {
                    QhLinearProgress(
                        progress = player.positionMs.toFloat() / player.durationMs,
                        height = 4.dp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box(
                Modifier
                    .size(48.dp)
                    .pressable(onClick = onDelete, haptic = HapticKind.Tick, shape = CircleShape, onClickLabel = copy.delete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = copy.delete,
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** "Recording deleted · Undo": a row of the sheet that stays for [UndoMillis]. */
@Composable
private fun UndoBar(copy: VoiceCopy, onUndo: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.full
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceStrong)
            .padding(start = 20.dp, end = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.deleted,
            style = QhTheme.type.callout,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        QhButton(
            text = copy.undo,
            onClick = onUndo,
            variant = QhButtonVariant.Ghost,
            size = QhButtonSize.S,
        )
    }
}

// -- Helpers ------------------------------------------------------------------------------------------------

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The page of this app in the settings of the phone, where the microphone can be allowed again. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
