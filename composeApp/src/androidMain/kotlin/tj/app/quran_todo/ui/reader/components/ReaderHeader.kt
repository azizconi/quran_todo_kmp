package tj.app.quran_todo.ui.reader.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.components.OrnamentDivider
import tj.app.quran_todo.ui.components.StatusPill
import tj.app.quran_todo.ui.components.SurahCartouche
import tj.app.quran_todo.ui.components.SurahNumberBadge
import tj.app.quran_todo.ui.components.toQhStatus
import tj.app.quran_todo.ui.motion.CelebrationBurst
import tj.app.quran_todo.ui.motion.SharedKeys
import tj.app.quran_todo.ui.motion.SharedResize
import tj.app.quran_todo.ui.motion.sharedBoundsKey
import tj.app.quran_todo.ui.motion.sharedElementKey
import tj.app.quran_todo.ui.reader.BismillahText
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** What the header shows about the surah. */
class ReaderHeaderData(
    val surahNumber: Int,
    val origin: String,
    val arabicName: String,
    val title: String,
    val meaning: String,
    val meta: String,
    val showBismillah: Boolean,
)

/**
 * First item of the list: a small number badge, the cartouche with the Arabic name, the transliterated
 * name and its meaning, the meta line with the surah status, an ornament and (for most surahs) the
 * Bismillah on its own line. The badge, the cartouche and the names are shared elements of the transition
 * from the surah row/card; everything below them enters with the rest of the content.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderHeader(
    data: ReaderHeaderData,
    status: SurahTodoStatus?,
    statusLabel: String,
    learnedFraction: Float,
    celebration: Int,
    headerStyle: TextStyle,
    bismillahStyle: TextStyle,
    enter: ReaderEnterState,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x2, bottom = QhSpacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurahNumberBadge(
            number = data.surahNumber,
            status = status,
            learnedFraction = learnedFraction,
            size = 44.dp,
            modifier = Modifier.sharedElementKey(SharedKeys.surahBadge(data.origin, data.surahNumber)),
        )
        Spacer(Modifier.height(QhSpacing.x3))
        SurahCartouche(
            modifier = Modifier
                .fillMaxWidth()
                .sharedBoundsKey(SharedKeys.surahContainer(data.origin, data.surahNumber), SharedResize.Remeasure),
        ) {
            Text(
                text = data.arabicName,
                style = headerStyle,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBoundsKey(SharedKeys.surahArabic(data.origin, data.surahNumber), SharedResize.Scale),
            )
        }
        Spacer(Modifier.height(QhSpacing.x4))
        Text(
            text = data.title,
            style = type.title1,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .sharedBoundsKey(SharedKeys.surahTitle(data.origin, data.surahNumber), SharedResize.Scale)
                .semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .readerEnter(enter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (data.meaning.isNotBlank()) {
                Text(
                    text = data.meaning,
                    style = type.body,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(QhSpacing.x1))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.meta,
                    style = type.callout,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
                Box {
                    StatusPill(
                        status = status.toQhStatus(),
                        label = statusLabel,
                        onClick = onStatusClick,
                    )
                    CelebrationBurst(trigger = celebration, modifier = Modifier.matchParentSize(), radius = 90.dp)
                }
            }
            OrnamentDivider(Modifier.padding(horizontal = QhSpacing.x8, vertical = QhSpacing.x5))
            if (data.showBismillah) {
                Text(
                    text = BismillahText,
                    style = bismillahStyle,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = QhSpacing.x2),
                )
            }
        }
    }
}
