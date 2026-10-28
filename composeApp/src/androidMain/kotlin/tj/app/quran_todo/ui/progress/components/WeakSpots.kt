package tj.app.quran_todo.ui.progress.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhRowDivider
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.progress.WeakSpotUi
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Rows shown before "Show more". */
private const val CollapsedRows = 5

/** Above this font scale the button goes under the texts instead of beside them. */
private const val StackedFontScale = 1.15f

/** What the reader opens for a "Review" tap: memorize mode on the weak ayahs of [spot]. */
internal fun WeakSpotUi.toReaderDestination(): Dest.Reader = Dest.Reader(
    surah = surah,
    ayah = ayahs.first(),
    origin = Origin.MAP,
    hifz = true,
    rangeStart = ayahs.min(),
    rangeEnd = ayahs.max(),
)

/**
 * "Weak spots": the surahs with ayahs marked weak, the most affected first. Each row names the
 * surah, how many ayahs are weak and which, and a "Review" button that opens the reader in memorize
 * mode on that range. Without weak ayahs a calm card says so.
 */
@Composable
internal fun WeakSpotsSection(
    spots: List<WeakSpotUi>,
    copy: ProgressCopy,
    onReview: (Dest.Reader) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.weakTitle)
        Spacer(Modifier.height(QhSpacing.x3))
        if (spots.isEmpty()) {
            NoWeakSpots(copy)
            return@Column
        }
        val visible = if (expanded) spots else spots.take(CollapsedRows)
        QhCard(
            contentPadding = PaddingValues(vertical = QhSpacing.x1),
            modifier = if (reduced) Modifier else Modifier.animateContentSize(QhMotion.gentle()),
        ) {
            visible.forEachIndexed { index, spot ->
                if (index > 0) QhRowDivider(startIndent = QhSpacing.card)
                WeakSpotRow(spot = spot, copy = copy, onReview = { onReview(spot.toReaderDestination()) })
            }
            if (spots.size > CollapsedRows) {
                QhRowDivider(startIndent = QhSpacing.card)
                Box(Modifier.fillMaxWidth().padding(vertical = QhSpacing.x1), contentAlignment = Alignment.Center) {
                    QhButton(
                        text = if (expanded) copy.weakLess else copy.weakMore(spots.size - CollapsedRows),
                        onClick = { expanded = !expanded },
                        variant = QhButtonVariant.Ghost,
                        size = QhButtonSize.S,
                        icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeakSpotRow(
    spot: WeakSpotUi,
    copy: ProgressCopy,
    onReview: () -> Unit,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    val stacked = LocalDensity.current.fontScale > StackedFontScale
    val details = "${copy.weakCount(spot.ayahs.size)} · ${copy.weakAyahs(spot.ayahs)}"
    val action = copy.weakRepeatDescription(spot.name)

    @Composable
    fun Texts(modifier: Modifier) {
        Column(modifier) {
            Text(
                text = spot.name,
                style = type.title2,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                style = type.callout,
                color = colors.inkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    fun ReviewButton(modifier: Modifier = Modifier) {
        QhButton(
            text = copy.weakRepeat,
            onClick = onReview,
            modifier = modifier.clearAndSetSemantics {
                contentDescription = action
                role = Role.Button
                onClick(label = action) { onReview(); true }
            },
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.S,
            icon = Icons.Rounded.Replay,
        )
    }

    if (stacked) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(QhSpacing.card),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.Top) {
                SurahNumberBadge(number = spot.surah, status = null, learnedFraction = 0f)
                Texts(Modifier.weight(1f))
            }
            ReviewButton(Modifier.padding(start = 52.dp))
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(QhSpacing.card),
            horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurahNumberBadge(number = spot.surah, status = null, learnedFraction = 0f)
            Texts(Modifier.weight(1f))
            ReviewButton()
        }
    }
}

/** No weak ayahs: a check and a kind word instead of an empty box. */
@Composable
private fun NoWeakSpots(copy: ProgressCopy) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    QhCard {
        Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.learned.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.learned, modifier = Modifier.size(QhSpacing.iconInline))
            }
            Column(Modifier.weight(1f)) {
                Text(text = copy.weakNoneTitle, style = type.title2, color = colors.ink)
                Text(text = copy.weakNoneBody, style = type.callout, color = colors.inkSecondary)
            }
        }
    }
}
