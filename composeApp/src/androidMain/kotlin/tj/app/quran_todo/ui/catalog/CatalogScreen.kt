package tj.app.quran_todo.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.AnimatedCheckmark
import tj.app.quran_todo.ui.components.ArabesqueLattice
import tj.app.quran_todo.ui.components.AyahRosette
import tj.app.quran_todo.ui.components.AyahWheel
import tj.app.quran_todo.ui.components.Calligraphy
import tj.app.quran_todo.ui.components.CalligraphyText
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.Medallion
import tj.app.quran_todo.ui.components.MihrabCalligraphy
import tj.app.quran_todo.ui.components.OrnamentDivider
import tj.app.quran_todo.ui.components.ProgressRing
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhCardStyle
import tj.app.quran_todo.ui.components.QhChip
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.components.QhNavRow
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.components.QhSheet
import tj.app.quran_todo.ui.components.QhSlider
import tj.app.quran_todo.ui.components.QhStatus
import tj.app.quran_todo.ui.components.QhSwitchRow
import tj.app.quran_todo.ui.components.QhValueRow
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.components.SkeletonRow
import tj.app.quran_todo.ui.components.StatusPill
import tj.app.quran_todo.ui.components.SurahCartouche
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.components.ToastHost
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.components.rememberToaster
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.motion.rememberStaggerState
import tj.app.quran_todo.ui.motion.staggeredEnter
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

private const val ArabicSample = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

/**
 * Debug-only showcase of every design-system component in every state, used for visual acceptance.
 * It wraps its content in its own [QhTheme] driven by the switches at the top (theme, palette, Reduce
 * Motion), so it can be opened from anywhere. Route: `Dest.Catalog`.
 */
@Composable
fun CatalogScreen(onBack: () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    var mode by rememberSaveable { mutableStateOf(if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT) }
    var palette by rememberSaveable { mutableStateOf(ThemePalette.FOREST) }
    var reduceMotion by rememberSaveable { mutableStateOf(false) }

    QhTheme(mode = mode, palette = palette, reducedMotion = reduceMotion, hapticsEnabled = true) {
        val toaster = rememberToaster()
        CompositionLocalProvider(LocalToaster provides toaster) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(QhTheme.colors.bg),
            ) {
                CatalogList(
                    onBack = onBack,
                    mode = mode,
                    onMode = { mode = it },
                    palette = palette,
                    onPalette = { palette = it },
                    reduceMotion = reduceMotion,
                    onReduceMotion = { reduceMotion = it },
                )
                ToastHost(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun CatalogList(
    onBack: () -> Unit,
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    palette: ThemePalette,
    onPalette: (ThemePalette) -> Unit,
    reduceMotion: Boolean,
    onReduceMotion: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val topBarState = rememberQhTopBarState(listState)
    val stagger = rememberStaggerState()
    val density = LocalDensity.current
    // Reserve the expanded height of the top bar once; it overlays the list.
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barHeight = with(density) { barHeightPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = QhSpacing.screen,
                end = QhSpacing.screen,
                top = barHeight + QhSpacing.x2,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + QhSpacing.x8,
            ),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.section),
        ) {
            item("theme") {
                Section("Theme", Modifier.staggeredEnter(0, stagger)) {
                    QhSegmentedControl(
                        options = listOf("Light", "Dark"),
                        selectedIndex = if (mode == ThemeMode.LIGHT) 0 else 1,
                        onSelect = { onMode(if (it == 0) ThemeMode.LIGHT else ThemeMode.DARK) },
                        icons = listOf(Icons.Rounded.LightMode, Icons.Rounded.DarkMode),
                    )
                    FlowChips {
                        ThemePalette.entries.forEach { entry ->
                            QhChip(
                                label = entry.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = entry == palette,
                                onClick = { onPalette(entry) },
                            )
                        }
                    }
                    QhCard(contentPadding = PaddingValues(0.dp)) {
                        QhSwitchRow(
                            title = "Reduce motion",
                            description = "Springs snap, no particles, no stagger",
                            checked = reduceMotion,
                            onCheckedChange = onReduceMotion,
                            icon = Icons.Rounded.Speed,
                        )
                    }
                }
            }
            item("colors") { Section("Colors") { ColorSwatches() } }
            item("type") { Section("Typography") { TypographySamples() } }
            item("buttons") { Section("Buttons") { ButtonsDemo() } }
            item("cards") { Section("Cards") { CardsDemo() } }
            item("chips") { Section("Chips, segments, search") { ChipsDemo() } }
            item("status") { Section("Status pill") { StatusDemo() } }
            item("badges") { Section("Surah number badge") { BadgesDemo() } }
            item("ornaments") { Section("Ornaments") { OrnamentsDemo() } }
            item("rosettes") { Section("Ayah rosette") { RosettesDemo() } }
            item("progress") { Section("Progress ring and bar") { ProgressDemo() } }
            item("counter") { Section("Animated counter") { CounterDemo() } }
            item("wheel") { Section("Ayah wheel") { WheelDemo() } }
            item("rows") { Section("Settings rows") { RowsDemo() } }
            item("slider") { Section("Slider") { SliderDemo() } }
            item("skeleton") { Section("Skeleton") { SkeletonDemo() } }
            item("empty") { Section("Empty state") { EmptyDemo() } }
            item("toast") { Section("Toast") { ToastDemo() } }
            item("sheet") { Section("Sheet") { SheetDemo() } }
            item("celebration") { Section("Celebration") { CelebrationDemo() } }
        }
        QhLargeTopBar(
            title = "Catalog",
            state = topBarState,
            overline = "Design system",
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    filled = false,
                )
            },
            modifier = Modifier.onSizeChanged { barHeightPx = maxOf(barHeightPx, it.height) },
        )
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        QhSectionHeader(title)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x1),
    ) { content() }
}

// -- Colors ----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches() {
    val c = QhTheme.colors
    val swatches = listOf(
        "bg" to c.bg, "surface" to c.surface, "surfaceMuted" to c.surfaceMuted,
        "surfaceStrong" to c.surfaceStrong, "hairline" to c.hairline, "ink" to c.ink,
        "inkSecondary" to c.inkSecondary, "inkTertiary" to c.inkTertiary, "primary" to c.primary,
        "onPrimary" to c.onPrimary, "primaryContainer" to c.primaryContainer,
        "onPrimaryContainer" to c.onPrimaryContainer, "gold" to c.gold, "goldContainer" to c.goldContainer,
        "learned" to c.learned, "onLearned" to c.onLearned, "learnedContainer" to c.learnedContainer,
        "learning" to c.learning, "learningContainer" to c.learningContainer, "due" to c.due,
        "dueContainer" to c.dueContainer, "danger" to c.danger, "onDanger" to c.onDanger,
        "readerBg" to c.readerBg, "readerSurface" to c.readerSurface,
        "readerHighlight" to c.readerHighlight, "scrim" to c.scrim,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
    ) {
        swatches.forEach { (name, color) ->
            Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(QhTheme.shapes.sm)
                        .border(1.dp, c.hairline, QhTheme.shapes.sm)
                        .background(c.bg)
                        .background(color),
                )
                Text(name, style = QhTheme.type.label, color = c.ink, maxLines = 1)
            }
        }
    }
}

// -- Typography ------------------------------------------------------------------------------------

@Composable
private fun TypographySamples() {
    val type = QhTheme.type
    val colors = QhTheme.colors
    val quran = getQuranFontFamily(ReadingFontStyle.MADINAH_MUSHAF)
    val samples: List<Pair<String, TextStyle>> = listOf(
        "display 34/40 - Quran" to type.display,
        "headline 26/32 - Al-Baqarah" to type.headline,
        "title1 21/28 - Al-Fatiha" to type.title1,
        "title2 17/24 - Surah row title" to type.title2,
        "body 16/24 - Translation of the ayah" to type.body,
        "callout 14/20 - Caption and metadata" to type.callout,
        "label 13/16 - Chips and buttons" to type.label,
    )
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        samples.forEach { (text, style) -> Text(text, style = style, color = colors.ink) }
        Text("overline 11/14 - section".uppercase(), style = type.overline, color = colors.inkSecondary)
        Text(
            "Glyph check: Ғғ Ӣӣ Ққ Ӯӯ Ҳҳ Ҷҷ O‘ ʻ İ ş ğ ç ö ü 0123456789",
            style = type.body,
            color = colors.ink,
        )
        Text("Tabular: 111 / 888 / 1234567890", style = type.body.copy(fontFeatureSettings = "tnum"), color = colors.ink)
        Text("quranHeader 32", style = type.callout, color = colors.inkSecondary)
        Text("البقرة", style = type.quranHeader(quran), color = colors.ink, modifier = Modifier.fillMaxWidth())
        Text("quranBody 28", style = type.callout, color = colors.inkSecondary)
        Text(ArabicSample, style = type.quranBody(quran), color = colors.ink, modifier = Modifier.fillMaxWidth())
        Text("quranInline 19", style = type.callout, color = colors.inkSecondary)
        Text("الفاتحة", style = type.quranInline(quran), color = colors.primary, modifier = Modifier.fillMaxWidth())
    }
}

// -- Buttons ---------------------------------------------------------------------------------------

@Composable
private fun ButtonsDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        QhButtonVariant.entries.forEach { variant ->
            Text(variant.name, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QhButtonSize.entries.forEach { size ->
                    QhButton(
                        text = size.name,
                        onClick = {},
                        variant = variant,
                        size = size,
                        icon = if (size == QhButtonSize.L) Icons.Rounded.PlayArrow else null,
                    )
                }
            }
        }
        Text("Disabled / loading", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhButton("Disabled", {}, enabled = false, size = QhButtonSize.M)
            QhButton("Ghost off", {}, variant = QhButtonVariant.Ghost, enabled = false, size = QhButtonSize.M)
            LoadingButton()
        }
        QhButton("Full width primary", {}, Modifier.fillMaxWidth(), icon = Icons.Rounded.Headphones)
        Text("Icon buttons", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            QhIconButton(Icons.Rounded.Settings, "Settings", {})
            QhIconButton(Icons.Rounded.Settings, "Settings", {}, filled = false)
            QhIconButton(Icons.Rounded.Settings, "Settings", {}, enabled = false)
        }
    }
}

@Composable
private fun LoadingButton() {
    var loading by remember { mutableStateOf(true) }
    QhButton(
        text = if (loading) "Loading" else "Tap to load",
        onClick = { loading = true },
        size = QhButtonSize.M,
        loading = loading,
    )
    // Stop the spinner after a moment so the demo can be repeated.
    LaunchedEffect(loading) {
        if (loading) {
            delay(2_500)
            loading = false
        }
    }
}

// -- Cards -----------------------------------------------------------------------------------------

@Composable
private fun CardsDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
        QhCard(Modifier.fillMaxWidth(), onClick = {}) {
            Text("Standard card, pressable", style = QhTheme.type.title2, color = QhTheme.colors.ink)
            Text("Hairline outline, no shadow", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        }
        QhCard(
            Modifier.fillMaxWidth(),
            onClick = {},
            style = QhCardStyle.Hero,
            contentPadding = PaddingValues(QhSpacing.cardLarge),
        ) {
            Text("CONTINUE", style = QhTheme.type.overline, color = QhTheme.colors.inkSecondary)
            Spacer(Modifier.height(QhSpacing.x1))
            Text("Al-Baqarah", style = QhTheme.type.title1, color = QhTheme.colors.ink)
            Spacer(Modifier.height(QhSpacing.x1))
            Text("Ayah 255 of 286", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
            Spacer(Modifier.height(QhSpacing.x3))
            QhLinearProgress(255f / 286f)
        }
    }
}

// -- Chips, segments, search -----------------------------------------------------------------------

@Composable
private fun ChipsDemo() {
    var selected by remember { mutableStateOf(setOf("Meccan")) }
    var segment by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        FlowChips {
            listOf("Meccan", "Medinan", "Short", "Long").forEach { label ->
                QhChip(
                    label = label,
                    selected = label in selected,
                    onClick = { selected = if (label in selected) selected - label else selected + label },
                )
            }
            QhChip(label = "Disabled", selected = false, onClick = {}, enabled = false)
            QhChip(label = "With icon", selected = false, onClick = {}, leadingIcon = Icons.Rounded.Language)
        }
        QhSegmentedControl(
            options = listOf("All", "Memorizing", "Memorized"),
            selectedIndex = segment,
            onSelect = { segment = it },
        )
        QhSegmentedControl(
            options = listOf("Light", "Dark", "System"),
            selectedIndex = segment.coerceAtMost(2),
            onSelect = { segment = it },
            icons = listOf(Icons.Rounded.LightMode, Icons.Rounded.DarkMode, Icons.Rounded.Settings),
        )
        QhSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Surah, number or 2:255",
            clearContentDescription = "Clear",
        )
    }
}

// -- Status ----------------------------------------------------------------------------------------

@Composable
private fun StatusDemo() {
    var status by remember { mutableStateOf(QhStatus.NotStarted) }
    val labels = mapOf(
        QhStatus.NotStarted to "Not started",
        QhStatus.Learning to "Memorizing",
        QhStatus.Learned to "Memorized",
        QhStatus.Due to "Review today",
    )
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
        FlowChips {
            QhStatus.entries.forEach { StatusPill(it, labels.getValue(it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(status, labels.getValue(status), onClick = {
                status = QhStatus.entries[(status.ordinal + 1) % QhStatus.entries.size]
            })
            Text("tap to cycle (morphs)", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        }
    }
}

// -- Badges ----------------------------------------------------------------------------------------

@Composable
private fun BadgesDemo() {
    var learned by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
            SurahNumberBadge(1, null, 0f)
            SurahNumberBadge(36, SurahTodoStatus.LEARNING, 0f)
            SurahNumberBadge(36, SurahTodoStatus.LEARNING, 0.4f)
            SurahNumberBadge(36, SurahTodoStatus.LEARNING, 1f)
            SurahNumberBadge(114, SurahTodoStatus.LEARNED, 1f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
            SurahNumberBadge(2, null, 0f, size = 64.dp)
            SurahNumberBadge(67, SurahTodoStatus.LEARNING, 0.4f, size = 64.dp)
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                SurahNumberBadge(
                    number = 9,
                    status = if (learned) SurahTodoStatus.LEARNED else SurahTodoStatus.LEARNING,
                    learnedFraction = 0.7f,
                    size = 64.dp,
                )
                CelebrationBurst(trigger = burst)
            }
            QhButton(
                text = if (learned) "Reset" else "Learned",
                onClick = {
                    learned = !learned
                    if (learned) burst++
                },
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.S,
            )
        }
    }
}

// -- Ornaments -------------------------------------------------------------------------------------

@Composable
private fun OrnamentsDemo() {
    val quran = getQuranFontFamily(ReadingFontStyle.MADINAH_MUSHAF)
    var replay by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
            Medallion(size = 24.dp)
            Medallion(size = 48.dp)
            Medallion(size = 72.dp, strokeWidth = 2.dp, fill = QhTheme.colors.goldContainer)
            key(replay) { AnimatedCheckmark(size = 48.dp) }
            QhButton("Replay", { replay++ }, variant = QhButtonVariant.Tonal, size = QhButtonSize.S)
        }
        key(replay) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                CalligraphyText(Calligraphy.Bismillah, size = 44.dp)
                CalligraphyText(Calligraphy.Iqra, size = 40.dp, revealMillis = 900)
                CalligraphyText(Calligraphy.Alhamdulillah, size = 36.dp, revealMillis = 1_200)
                MihrabCalligraphy()
            }
        }
        OrnamentDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .clip(QhTheme.shapes.md)
                .background(QhTheme.colors.readerBg)
                .padding(vertical = QhSpacing.x5),
            contentAlignment = Alignment.Center,
        ) {
            SurahCartouche(Modifier.fillMaxWidth(0.85f)) {
                Text(
                    "البقرة",
                    style = QhTheme.type.quranHeader(quran),
                    color = QhTheme.colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(QhTheme.shapes.lg)
                .background(QhTheme.colors.primaryContainer),
        ) {
            ArabesqueLattice(Modifier.fillMaxSize(), alpha = 0.16f)
            Text(
                "ArabesqueLattice (alpha 16% here, 7% by default)",
                style = QhTheme.type.callout,
                color = QhTheme.colors.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(QhSpacing.x4),
            )
        }
    }
}

// -- Rosettes --------------------------------------------------------------------------------------

@Composable
private fun RosettesDemo() {
    Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
        AyahRosette(1)
        AyahRosette(12, status = AyahTodoStatus.LEARNING)
        AyahRosette(55, status = AyahTodoStatus.LEARNED)
        AyahRosette(7, weak = true)
        AyahRosette(286, status = AyahTodoStatus.LEARNED, weak = true)
        AyahRosette(255, size = 56.dp, status = AyahTodoStatus.LEARNING)
    }
}

// -- Progress --------------------------------------------------------------------------------------

@Composable
private fun ProgressDemo() {
    var progress by remember { mutableFloatStateOf(0.4f) }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress)
            ProgressRing(progress, size = 96.dp, strokeWidth = 8.dp) {
                AnimatedCounter((progress * 100).toInt(), style = QhTheme.type.title1)
            }
            QhButton(
                text = "Animate",
                onClick = { progress = if (progress >= 0.95f) 0f else (progress + 0.3f).coerceAtMost(1f) },
                variant = QhButtonVariant.Tonal,
                size = QhButtonSize.S,
            )
        }
        QhLinearProgress(progress)
        QhLinearProgress(progress, height = 2.dp)
    }
}

// -- Counter ---------------------------------------------------------------------------------------

@Composable
private fun CounterDemo() {
    var count by remember { mutableIntStateOf(8) }
    Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4), verticalAlignment = Alignment.CenterVertically) {
        QhIconButton(Icons.Rounded.Remove, "Decrease", { count = (count - 1).coerceAtLeast(0) })
        AnimatedCounter(count, style = QhTheme.type.display)
        QhIconButton(Icons.Rounded.Add, "Increase", { count++ })
        QhButton("+10", { count += 10 }, variant = QhButtonVariant.Tonal, size = QhButtonSize.S)
        QhButton("-10", { count = (count - 10).coerceAtLeast(0) }, variant = QhButtonVariant.Tonal, size = QhButtonSize.S)
    }
}

// -- Wheel -----------------------------------------------------------------------------------------

@Composable
private fun WheelDemo() {
    var from by remember { mutableIntStateOf(5) }
    var to by remember { mutableIntStateOf(12) }
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x4)) {
            AyahWheel(
                label = "From",
                minimum = 1,
                maximum = 286,
                value = from,
                onValueChange = {
                    from = it
                    if (to < it) to = it
                },
                modifier = Modifier.weight(1f),
            )
            AyahWheel(
                label = "To",
                minimum = from,
                maximum = 286,
                value = to,
                onValueChange = { to = it },
                modifier = Modifier.weight(1f),
            )
        }
        Text("Repeat $from-$to", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
    }
}

// -- Settings rows ---------------------------------------------------------------------------------

@Composable
private fun RowsDemo() {
    var haptics by remember { mutableStateOf(true) }
    var autoplay by remember { mutableStateOf(false) }
    val colors = QhTheme.colors
    QhCard(contentPadding = PaddingValues(0.dp)) {
        QhSwitchRow(
            title = "Haptic feedback",
            description = "A light tick on every tap",
            checked = haptics,
            onCheckedChange = { haptics = it },
            icon = Icons.Rounded.Vibration,
            iconTint = colors.primary,
        )
        QhRowDivider()
        QhSwitchRow(
            title = "Autoplay",
            checked = autoplay,
            onCheckedChange = { autoplay = it },
            icon = Icons.Rounded.PlayArrow,
            iconTint = colors.gold,
        )
        QhRowDivider()
        QhNavRow(
            title = "Quran font",
            description = "Madinah Mushaf",
            onClick = {},
            icon = Icons.Rounded.Palette,
            iconTint = colors.due,
        )
        QhRowDivider()
        QhValueRow(
            title = "Language",
            value = "English",
            onClick = {},
            icon = Icons.Rounded.Language,
            iconTint = colors.primary,
        )
        QhRowDivider()
        QhValueRow(
            title = "Reminders",
            value = "Off",
            icon = Icons.Rounded.Notifications,
            iconTint = colors.gold,
        )
        QhRowDivider()
        QhNavRow(
            title = "Download for offline",
            onClick = {},
            icon = Icons.Rounded.Download,
            enabled = false,
        )
    }
}

// -- Slider ----------------------------------------------------------------------------------------

@Composable
private fun SliderDemo() {
    var size by remember { mutableFloatStateOf(28f) }
    var continuous by remember { mutableFloatStateOf(0.5f) }
    val quran = getQuranFontFamily(ReadingFontStyle.MADINAH_MUSHAF)
    Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        Text(
            ArabicSample,
            style = QhTheme.type.quranBody(quran, size.sp),
            color = QhTheme.colors.ink,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Font size ${size.toInt()} (steps)", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        QhSlider(size, { size = it }, valueRange = 22f..40f, steps = 17)
        Text("Continuous", style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
        QhSlider(continuous, { continuous = it })
        QhSlider(0.3f, {}, enabled = false)
    }
}

// -- Skeleton, empty state ------------------------------------------------------------------------

@Composable
private fun SkeletonDemo() {
    Column {
        SkeletonRow()
        SkeletonRow()
        SkeletonRow()
        Skeleton(Modifier.fillMaxWidth().height(96.dp), QhTheme.shapes.lg)
    }
}

@Composable
private fun EmptyDemo() {
    QhCard(Modifier.fillMaxWidth()) {
        EmptyState(
            title = "Nothing found",
            message = "Try another name or a number such as 2:255.",
            actionLabel = "Clear search",
            onAction = {},
        )
    }
}

// -- Toast, sheet, celebration ---------------------------------------------------------------------

@Composable
private fun ToastDemo() {
    val toaster = LocalToaster.current
    Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
        QhButton("Success", { toaster.show("Al-Fatiha - memorized", ToastKind.Success) }, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
        QhButton("Error", { toaster.show("No connection. Try again.", ToastKind.Error) }, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
        QhButton("Info", { toaster.show("Playing Al-Mulk instead", ToastKind.Info) }, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDemo() {
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    QhButton("Open sheet", { show = true }, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
    if (show) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var expanded by remember { mutableStateOf(false) }
        QhSheet(onDismiss = { show = false }, sheetState = sheetState) {
            Text("Surah status", style = QhTheme.type.title1, color = QhTheme.colors.ink)
            Spacer(Modifier.height(QhSpacing.x3))
            StatusPill(QhStatus.Learning, "Memorizing", onClick = { expanded = !expanded })
            if (expanded) {
                Spacer(Modifier.height(QhSpacing.x3))
                Text(
                    "The sheet animates its own size when its content changes.",
                    style = QhTheme.type.body,
                    color = QhTheme.colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(QhSpacing.x5))
            QhButton(
                text = "Done",
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { show = false } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CelebrationDemo() {
    var burst by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        QhButton("Celebrate", { burst++ })
        CelebrationBurst(trigger = burst)
    }
}
