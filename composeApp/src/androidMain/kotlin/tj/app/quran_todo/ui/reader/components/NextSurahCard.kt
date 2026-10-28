package tj.app.quran_todo.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.AnimatedEightPointStar
import tj.app.quran_todo.ui.components.OrnamentDivider
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.i18n.ReaderCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** "Next surah: Al Imran" at the end of the list; opens the next reader. */
@Composable
fun NextSurahCard(
    title: String,
    meaning: String,
    arabicName: String,
    arabicStyle: TextStyle,
    copy: ReaderCopy,
    enter: ReaderEnterState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .readerEnter(enter)
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x6),
    ) {
        OrnamentDivider(Modifier.padding(horizontal = QhSpacing.x8, vertical = QhSpacing.x2))
        Spacer(Modifier.size(QhSpacing.x3))
        QhCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = QhSpacing.cardLarge,
                vertical = QhSpacing.card,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = copy.nextSurah.uppercase(),
                        style = QhTheme.type.overline,
                        color = colors.inkSecondary,
                    )
                    Text(
                        text = title,
                        style = QhTheme.type.title1,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meaning.isNotBlank()) {
                        Text(
                            text = meaning,
                            style = QhTheme.type.callout,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = arabicName,
                    style = arabicStyle,
                    color = colors.primary,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
            }
        }
    }
}

/** The end of the last surah. */
@Composable
fun QuranFinishedCard(
    copy: ReaderCopy,
    enter: ReaderEnterState,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .readerEnter(enter)
            .padding(horizontal = QhSpacing.screen)
            .padding(top = QhSpacing.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrnamentDivider(Modifier.padding(horizontal = QhSpacing.x8))
        Spacer(Modifier.size(QhSpacing.x5))
        AnimatedEightPointStar(size = 72.dp, drawMillis = 1_100, breathing = true)
        Spacer(Modifier.size(QhSpacing.x4))
        Text(
            text = copy.quranFinished,
            style = QhTheme.type.title1,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(QhSpacing.x4))
        QhButton(
            text = copy.startOver,
            onClick = onStartOver,
            variant = QhButtonVariant.Tonal,
            size = QhButtonSize.M,
        )
    }
}
