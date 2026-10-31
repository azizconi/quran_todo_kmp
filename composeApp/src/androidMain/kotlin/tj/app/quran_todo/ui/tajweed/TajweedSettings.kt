package tj.app.quran_todo.ui.tajweed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.koin.compose.koinInject
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.i18n.TajweedCopy
import tj.app.quran_todo.ui.i18n.rememberTajweedCopy
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The tajweed rows of the "Aa" sheet (task F3b), for the card that holds the switches: the "Tajweed" switch, under it the state of the
 * markup file while the setting is on ("Downloading tajweed markup · 2.5 MB" with a bar, or "An internet connection is needed once" with
 * "Retry"), and the row "What the colours mean" that opens [TajweedLegendSheet].
 *
 * Turning the switch on fetches the file the first time. The switch stays on without network: the text shows the colours as soon as the
 * file is there (the reader asks again when it opens, "Retry" asks at once).
 */
@Composable
fun TajweedSettingsRows() {
    val prefs = koinInject<UiPrefs>()
    val copy = rememberTajweedCopy()
    val store = TajweedStore.shared
    val enabled by prefs.tajweed.collectAsState()
    val availability by store.availability.collectAsState()
    var legendOpen by remember { mutableStateOf(false) }

    QhSwitchRow(
        title = copy.switchTitle,
        checked = enabled,
        onCheckedChange = { on ->
            prefs.setTajweed(on)
            if (on) store.ensureDownloaded(force = true)
        },
        description = copy.switchHint,
    )
    AnimatedVisibility(
        visible = enabled && availability !is TajweedAvailability.Ready,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        TajweedFileStatus(
            availability = availability,
            copy = copy,
            onRetry = { store.ensureDownloaded(force = true) },
        )
    }
    QhRowDivider(startIndent = QhSpacing.card)
    QhNavRow(title = copy.legendRow, onClick = { legendOpen = true })
    if (legendOpen) TajweedLegendSheet(onDismiss = { legendOpen = false })
}

/** What the person sees while the markup is not on the device: a progress bar with its line, or the "needs internet" line with "Retry". */
@Composable
private fun TajweedFileStatus(availability: TajweedAvailability, copy: TajweedCopy, onRetry: () -> Unit) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.card)
            .padding(bottom = QhSpacing.x3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
    ) {
        when (availability) {
            is TajweedAvailability.NeedsInternet -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = copy.needInternet,
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .heightIn(min = QhSpacing.minTouch)
                        .pressable(onClick = onRetry, shape = QhTheme.shapes.sm, onClickLabel = copy.retry)
                        .padding(horizontal = QhSpacing.x2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = copy.retry, style = QhTheme.type.callout, color = colors.primary)
                }
            }
            is TajweedAvailability.Downloading -> {
                Text(text = copy.downloading, style = QhTheme.type.callout, color = colors.inkSecondary)
                QhLinearProgress(progress = availability.fraction)
            }
            // Missing: the request has just been made (the next frame says "Downloading"); Ready: this row is not shown.
            else -> {
                Text(text = copy.downloading, style = QhTheme.type.callout, color = colors.inkSecondary)
                QhLinearProgress(progress = 0f)
            }
        }
    }
}
