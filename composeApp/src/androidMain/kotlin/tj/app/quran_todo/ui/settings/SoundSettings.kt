package tj.app.quran_todo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Sd
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.DotSpinner
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.i18n.AudioCopy
import tj.app.quran_todo.ui.i18n.rememberAudioCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.playback.PreviewState
import tj.app.quran_todo.ui.playback.rememberReciterPreview
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The reciter and audio-storage rows of the "Sound" section. They keep their own state (the reciter
 * sheet, the size of the cache, the confirmation) so the settings screen only places them.
 *
 * @param reciterTitle the title of the reciter row ("Reciter") in the language of the screen.
 */
@Composable
internal fun SoundVoiceRows(language: AppLanguage, reciterTitle: String) {
    val prefs = koinInject<UiPrefs>()
    val reciter by prefs.reciter.collectAsState()
    val copy = rememberAudioCopy(language)
    var sheetOpen by remember { mutableStateOf(false) }

    QhValueRow(
        title = reciterTitle,
        value = reciter.shortName(language),
        onClick = { sheetOpen = true },
        icon = Icons.Rounded.RecordVoiceOver,
        iconTint = QhTheme.colors.gold,
    )
    QhRowDivider()
    AudioStorageRow(copy)

    if (sheetOpen) {
        ReciterSheet(
            title = reciterTitle,
            copy = copy,
            language = language,
            selected = reciter,
            onSelect = prefs::setReciter,
            onDismiss = { sheetOpen = false },
        )
    }
}

/** "Audio on this device · 230 MB" with a clear button that asks first. */
@Composable
private fun AudioStorageRow(copy: AudioCopy) {
    val colors = QhTheme.colors
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<Long?>(null) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bytes = withContext(Dispatchers.IO) { AudioCacheStorage.shared.totalBytes() }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = QhSpacing.card, vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconTile(Icons.Rounded.Sd, tint = colors.learned)
        Column(Modifier.weight(1f)) {
            Text(text = copy.audioOnDevice, style = QhTheme.type.title2, color = colors.ink)
            Text(
                text = bytes?.let(copy::sizeLabel) ?: "…",
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 1,
            )
        }
        QhButton(
            text = copy.audioClear,
            onClick = { confirming = true },
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.S,
            enabled = (bytes ?: 0L) > 0L,
        )
    }

    if (confirming) {
        ClearAudioDialog(
            copy = copy,
            onConfirm = {
                confirming = false
                scope.launch {
                    withContext(Dispatchers.IO) { AudioCacheStorage.shared.clear() }
                    bytes = 0L
                    toaster.show(copy.audioCleared, ToastKind.Success)
                }
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Confirms deleting the cached audio: the danger action on top, a quiet cancel under it. */
@Composable
private fun ClearAudioDialog(copy: AudioCopy, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.xl
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape)
                .padding(QhSpacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            QhIconTile(Icons.Rounded.DeleteSweep, tint = colors.danger)
            Text(
                text = copy.audioClearTitle,
                style = QhTheme.type.title1,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = copy.audioClearMessage,
                style = QhTheme.type.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(QhSpacing.x1))
            QhButton(
                text = copy.audioClearConfirm,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Danger,
                size = QhButtonSize.M,
            )
            QhButton(
                text = copy.cancel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Ghost,
                size = QhButtonSize.M,
            )
        }
    }
}

/**
 * The four reciters as cards: name, a short line about the style and tempo, a sample button (Surah 112,
 * ayah 1) and a check on the chosen one. The choice applies at once; the sheet stays open so the voices
 * can be compared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReciterSheet(
    title: String,
    copy: AudioCopy,
    language: AppLanguage,
    selected: Reciter,
    onSelect: (Reciter) -> Unit,
    onDismiss: () -> Unit,
) {
    val toaster = LocalToaster.current
    val preview = rememberReciterPreview()
    val previewState by preview.state.collectAsState()
    LaunchedEffect(preview) {
        preview.failures.collect { toaster.show(copy.sampleFailed, ToastKind.Error) }
    }
    QhSheet(onDismiss = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            Text(text = title, style = QhTheme.type.title1, color = QhTheme.colors.ink)
            Text(text = copy.reciterSheetHint, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
            Spacer(Modifier.height(QhSpacing.x2))
            Reciters.all.forEach { reciter ->
                ReciterCard(
                    reciter = reciter,
                    language = language,
                    copy = copy,
                    selected = reciter == selected,
                    sample = previewState.glyphFor(reciter),
                    onSelect = { onSelect(reciter) },
                    onSample = { preview.toggle(reciter) },
                )
            }
        }
    }
}

private enum class SampleGlyph { Play, Loading, Stop }

private fun PreviewState.glyphFor(reciter: Reciter): SampleGlyph = when (this) {
    is PreviewState.Loading -> if (reciterId == reciter.id) SampleGlyph.Loading else SampleGlyph.Play
    is PreviewState.Playing -> if (reciterId == reciter.id) SampleGlyph.Stop else SampleGlyph.Play
    PreviewState.Idle -> SampleGlyph.Play
}

@Composable
private fun ReciterCard(
    reciter: Reciter,
    language: AppLanguage,
    copy: AudioCopy,
    selected: Boolean,
    sample: SampleGlyph,
    onSelect: () -> Unit,
    onSample: () -> Unit,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "reciter-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "reciter-outline",
    )
    val badgeIn = QhMotion.bouncy<Float>().orSnap()
    val badgeOut = QhMotion.snappy<Float>().orSnap()

    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onSelect, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .heightIn(min = 72.dp)
            .padding(horizontal = QhSpacing.x3, vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SampleButton(glyph = sample, copy = copy, onClick = onSample)
        Column(Modifier.weight(1f)) {
            Text(
                text = reciter.name(language),
                style = QhTheme.type.title2,
                color = if (selected) colors.onPrimaryContainer else colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = copy.styleOf(reciter),
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(badgeIn, initialScale = 0.2f) + fadeIn(badgeOut),
            exit = scaleOut(badgeOut, targetScale = 0.2f) + fadeOut(badgeOut),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(QhSpacing.iconBar + 2.dp),
            )
        }
    }
}

/** The round "play sample" button of a reciter: play, three dots while it loads, stop while it plays. */
@Composable
private fun SampleButton(glyph: SampleGlyph, copy: AudioCopy, onClick: () -> Unit) {
    val colors = QhTheme.colors
    val label = if (glyph == SampleGlyph.Play) copy.playSample else copy.stopSample
    Box(
        Modifier
            .size(QhSpacing.minTouch)
            .pressable(onClick = onClick, haptic = HapticKind.Tick, shape = CircleShape, onClickLabel = label)
            .clip(CircleShape)
            .background(colors.primary)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (glyph) {
            SampleGlyph.Loading -> DotSpinner(colors.onPrimary)
            SampleGlyph.Stop -> Icon(Icons.Rounded.Stop, null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
            SampleGlyph.Play -> Icon(Icons.Rounded.PlayArrow, null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
        }
    }
}
