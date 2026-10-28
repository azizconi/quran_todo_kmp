package tj.app.quran_todo.ui.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import tj.app.quran_todo.ui.components.QhSegmentedControl
import tj.app.quran_todo.ui.i18n.SettingsCopy
import tj.app.quran_todo.ui.theme.QhTheme

// Layout constants of QhSegmentedControl that decide how much room a label has (dp).
private const val TrackInset = 4f
private const val SegmentPadding = 8f
private const val SegmentIcon = 18f
private const val SegmentIconGap = 6f

/** A little air so a label that fits to the pixel is not ellipsized by rounding (dp). */
private const val FitMargin = 2f

/**
 * Whether every label still fits its segment with the icon next to it. All segments have the same
 * width: the control's width minus the track insets, divided by the number of segments.
 */
internal fun segmentsFitWithIcons(labelWidthsPx: List<Float>, controlWidthPx: Float, density: Float): Boolean {
    if (labelWidthsPx.isEmpty()) return true
    val segmentPx = (controlWidthPx - 2f * TrackInset * density) / labelWidthsPx.size
    val chromePx = (2f * SegmentPadding + SegmentIcon + SegmentIconGap + FitMargin) * density
    return labelWidthsPx.all { it + chromePx <= segmentPx }
}

/**
 * The "Light / Dark / System" control of the settings and the onboarding. It measures its labels:
 * when they do not all fit next to their icons at the current width and font size, the icons are
 * dropped for all three segments, so no label is ever ellipsized.
 */
@Composable
internal fun ThemeSegments(
    copy: SettingsCopy,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(copy.themeLight, copy.themeDark, copy.themeSystem)
    val style = QhTheme.type.label
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val withIcons = remember(options, style, widthPx, density) {
            val labelWidths = options.map { label ->
                measurer.measure(text = label, style = style, softWrap = false, maxLines = 1).size.width.toFloat()
            }
            segmentsFitWithIcons(labelWidths, widthPx, density.density)
        }
        QhSegmentedControl(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            icons = if (withIcons) ThemeIcons else null,
        )
    }
}

private val ThemeIcons = listOf(Icons.Rounded.LightMode, Icons.Rounded.DarkMode, Icons.Rounded.SettingsBrightness)
