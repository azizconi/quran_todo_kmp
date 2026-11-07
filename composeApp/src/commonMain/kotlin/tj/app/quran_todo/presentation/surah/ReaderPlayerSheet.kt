package tj.app.quran_todo.presentation.surah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.player_guide_art
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.playerFeatureCopy
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.presentation.designsystem.platformBottomSystemBarsPadding
import kotlin.math.abs

internal enum class ReaderPlayerFeature { Range, Pause }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPlayerSheet(
    feature: ReaderPlayerFeature,
    preferences: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    currentAyah: Int,
    ayahCount: Int,
    onDismiss: () -> Unit,
    onApply: (SurahReaderPreferences, ReaderPracticeSettings, Boolean) -> Unit,
) {
    val copy = playerFeatureCopy(LocalAppLanguage.current)
    val colors = MaterialTheme.extendedColors
    val limit = ayahCount.coerceAtLeast(1)
    val guideIndex = if (feature == ReaderPlayerFeature.Range) 0 else 1
    var guide by rememberSaveable(feature) { mutableStateOf(UserSettingsStorage.isPlayerGuideSeen(feature.name) != true) }
    var first by rememberSaveable { mutableStateOf(if (practice.rangeEnabled) practice.startAyah else currentAyah.coerceIn(1, limit)) }
    var last by rememberSaveable { mutableStateOf(if (practice.rangeEnabled) practice.endAyah else currentAyah.coerceIn(1, limit)) }
    var repeats by rememberSaveable { mutableStateOf(practice.rangeRepeatCount.takeIf { it in listOf(3, 5, -1) } ?: 3) }
    val finishGuide = {
        UserSettingsStorage.savePlayerGuideSeen(true, feature.name)
        guide = false
    }
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colors.background,
    ) {
        Column(
            Modifier.fillMaxWidth().platformBottomSystemBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!guide) {
                    Text(if (feature == ReaderPlayerFeature.Range) copy.chooseAyahs else copy.pause,
                        color = MaterialTheme.colors.onBackground, fontSize = 21.sp, lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { guide = true }) {
                        Icon(Icons.Outlined.HelpOutline, copy.help, tint = colors.textSecondary)
                    }
                } else Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, copy.close, tint = colors.textSecondary) }
            }
            when {
                guide -> {
                    PlayerGuideIllustration(guideIndex)
                    Text(copy.guideTitle(guideIndex), color = MaterialTheme.colors.onBackground, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(copy.guideBody(guideIndex), color = colors.textSecondary, fontSize = 16.sp, lineHeight = 23.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
                    PlayerSheetButton(copy.tryIt, finishGuide)
                }
                feature == ReaderPlayerFeature.Range -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        AyahWheel(copy.from, 1, limit, first, Modifier.weight(1f)) {
                            first = it
                            if (last < first) last = first
                        }
                        key(first) {
                            AyahWheel(copy.to, first, limit, last.coerceAtLeast(first), Modifier.weight(1f)) { last = it }
                        }
                    }
                    Text("${copy.ayahsLabel} $first–$last", color = MaterialTheme.colors.primary,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, -1).forEach { count ->
                            val active = repeats == count
                            Surface(color = if (active) MaterialTheme.colors.primary else colors.surfaceAlt,
                                contentColor = if (active) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).semantics { selected = active }
                                    .clickable(role = Role.RadioButton) { repeats = count }) {
                                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                                    Text(if (count == -1) copy.onLoop else "$count ${if (count == 3) copy.timesFew else copy.timesLabel}", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    PlayerSheetButton(copy.startRepeat) {
                        onApply(preferences.copy(repeatCount = 1), practice.copy(rangeEnabled = true,
                            startAyah = first, endAyah = last, rangeRepeatCount = repeats).normalized(limit), true)
                        onDismiss()
                    }
                    if (practice.rangeEnabled) TextButton(onClick = {
                        onApply(preferences.copy(repeatCount = 1), practice.copy(rangeEnabled = false), false)
                        onDismiss()
                    }) { Text(copy.stopRepeat) }
                }
                else -> {
                    Text(copy.pauseHint, color = colors.textSecondary, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    listOf(0, 3, 5, 10).forEach { seconds ->
                        val active = seconds == practice.pauseSeconds
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(if (active) colors.surfaceAlt else MaterialTheme.colors.background, RoundedCornerShape(14.dp))
                            .semantics { selected = active }.clickable(role = Role.RadioButton) {
                                onApply(preferences, practice.copy(pauseSeconds = seconds), false)
                                onDismiss()
                            }.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (seconds == 0) copy.noPause else "$seconds ${copy.seconds}",
                                color = MaterialTheme.colors.onBackground, modifier = Modifier.weight(1f), fontSize = 17.sp)
                            if (active) Icon(Icons.Default.Check, null, tint = MaterialTheme.colors.primary)
                        }
                    }
                }
            }
        }
    }
}

/** Five visible rows; drag or tap a number, with the selected ayah snapped to the centre. */
@Composable
private fun AyahWheel(label: String, minimum: Int, maximum: Int, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (value - minimum).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val update by rememberUpdatedState(onChange)
    LaunchedEffect(state, minimum) {
        snapshotFlow {
            val layout = state.layoutInfo
            val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
        }.collect { index -> if (index != null) update((minimum + index).coerceIn(minimum, maximum)) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.extendedColors.textSecondary, fontSize = 14.sp)
        Box(Modifier.fillMaxWidth().height(200.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.extendedColors.surfaceAlt, RoundedCornerShape(12.dp)))
            LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state),
                contentPadding = PaddingValues(vertical = 78.dp),
                modifier = Modifier.fillMaxSize().semantics { contentDescription = label; stateDescription = value.toString() }) {
                items(maximum - minimum + 1, key = { minimum + it }) { index ->
                    val number = minimum + index
                    Box(Modifier.fillMaxWidth().height(40.dp).clickable {
                        scope.launch { state.animateScrollToItem(index) }
                    }, contentAlignment = Alignment.Center) {
                        Text(number.toString(), fontSize = if (number == value) 27.sp else 21.sp,
                            fontWeight = if (number == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (number == value) MaterialTheme.colors.primary else MaterialTheme.extendedColors.textSecondary.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerGuideIllustration(step: Int) {
    val atlas = imageResource(Res.drawable.player_guide_art)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val side = minOf(size.width, size.height).toInt()
        drawImage(image = atlas, srcOffset = IntOffset(step * (atlas.width / 3), 0),
            srcSize = IntSize(atlas.width / 3, atlas.height), dstOffset = IntOffset((size.width.toInt() - side) / 2, 0),
            dstSize = IntSize(side, side), filterQuality = FilterQuality.High)
    }
}

@Composable
private fun PlayerSheetButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
