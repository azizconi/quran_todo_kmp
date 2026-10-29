package tj.app.quran_todo.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.QhIconTile
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.home.SurahRowUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The surah sheet (long press on a row or a chip): three big status cards (circle, half circle, check),
 * pin / unpin and "Listen to surah". Choosing a status lights up the card ([onPicked], at the tap),
 * slides the sheet away and then applies it ([onSetStatus]). [row] is the live row, so the selected
 * card follows the status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SurahSheet(
    row: SurahRowUi,
    copy: HomeCopy,
    arabicStyle: TextStyle,
    onPicked: (SurahTodoStatus?) -> Unit,
    onSetStatus: (SurahTodoStatus?) -> Unit,
    onTogglePin: () -> Unit,
    onListen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = QhTheme.colors
    val type = QhTheme.type
    // The status the person tapped: its card lights up at once, before the change is applied.
    var picked by remember { mutableStateOf<Picked?>(null) }
    var closing by remember { mutableStateOf(false) }
    val shownStatus = picked?.let { it.status } ?: row.status

    /**
     * Slides the sheet away, then runs [action]: the row behind it changes (the badge pops, the burst
     * plays) once the person can see it, not under the scrim. Ignores a second tap while closing.
     */
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            action()
            onDismiss()
        }
    }

    /** A tap on a status card: lights it up, reports the pick, and applies it after the sheet closed. */
    fun choose(status: SurahTodoStatus?) {
        if (closing || shownStatus == status) return
        picked = Picked(status)
        onPicked(status)
        closeThen { onSetStatus(status) }
    }

    QhSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            SurahNumberBadge(
                number = row.number,
                status = row.status,
                learnedFraction = row.learnedFraction,
                size = 48.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(row.name, style = type.title1, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = listOf(row.translatedName, copy.ayahCount(row.ayahCount)).filter { it.isNotBlank() }.joinToString(" · ")
                Text(subtitle, style = type.callout, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (row.arabicName.isNotBlank()) {
                Text(row.arabicName, style = arabicStyle, color = colors.primary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(QhSpacing.x6))
        QhSectionHeader(copy.sheetStatus)
        Spacer(Modifier.height(QhSpacing.x3))
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            StatusCard(
                label = copy.statusNotStarted,
                icon = Icons.Rounded.RadioButtonUnchecked,
                selected = shownStatus == null,
                accent = colors.inkSecondary,
                container = colors.surfaceMuted,
                onClick = { choose(null) },
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                label = copy.statusLearning,
                icon = Icons.Rounded.Contrast,
                selected = shownStatus == SurahTodoStatus.LEARNING,
                accent = colors.learning,
                container = colors.learningContainer,
                onClick = { choose(SurahTodoStatus.LEARNING) },
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                label = copy.statusLearned,
                icon = Icons.Rounded.CheckCircle,
                selected = shownStatus == SurahTodoStatus.LEARNED,
                accent = colors.learned,
                container = colors.learnedContainer,
                onClick = { choose(SurahTodoStatus.LEARNED) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(QhSpacing.x4))
        SheetAction(
            icon = Icons.Rounded.PushPin,
            title = if (row.isPinned) copy.unpin else copy.pin,
            tint = colors.gold,
            onClick = { closeThen(onTogglePin) },
        )
        SheetAction(
            icon = Icons.Rounded.Headphones,
            title = copy.listenSurah,
            tint = colors.primary,
            enabled = row.canOpen,
            onClick = { closeThen(onListen) },
        )
    }
}

/** A status choice (null = "not started") waiting for the sheet to close. */
private class Picked(val status: SurahTodoStatus?)

/**
 * One of the three status choices. The selected one is tinted with the status color, gets a thicker
 * outline (not only color: the shape of the icon differs too) and its icon pops with `bouncy()`.
 */
@Composable
private fun StatusCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.md
    val background by animateColorAsState(
        targetValue = if (selected) container else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "status-card-background",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) accent else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "status-card-outline",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) accent else colors.inkSecondary,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "status-card-tint",
    )
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = QhMotion.bouncy<Float>().orSnap(),
        label = "status-card-pop",
    )
    Column(
        modifier
            .heightIn(min = 96.dp)
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .padding(horizontal = QhSpacing.x2, vertical = QhSpacing.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.CenterVertically),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        )
        Text(
            text = label,
            style = QhTheme.type.label,
            color = if (selected) colors.ink else colors.inkSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = QhSpacing.x12 + QhSpacing.x2)
            .pressable(onClick = onClick, enabled = enabled, scale = 0.99f)
            .padding(vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QhIconTile(icon, tint = if (enabled) tint else QhTheme.colors.inkTertiary)
        Text(
            text = title,
            style = QhTheme.type.title2,
            color = if (enabled) QhTheme.colors.ink else QhTheme.colors.inkTertiary,
            modifier = Modifier.weight(1f),
        )
    }
}
