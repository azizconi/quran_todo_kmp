package tj.app.quran_todo.ui.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.Skeleton
import tj.app.quran_todo.ui.i18n.PagesCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.theme.tabular

/** Height of the button row of the top margin, below the status bar. */
internal val MushafBarRowHeight = 56.dp

/** Height of the bottom margin (the page number), above the navigation bar. */
internal val MushafFootHeight = 40.dp

/**
 * The top margin of a page of the mushaf: back, the surah of the first line with "Juz 2 · Hizb 3" under it, and the actions: listen,
 * "Check yourself", and the switch to the text. It is paper (the colour of the page), so the page never shows through; the tap on
 * the page hides it and shows it again.
 */
@Composable
internal fun MushafTopBar(
    surahName: String,
    place: String,
    playing: Boolean,
    checking: Boolean,
    backLabel: String,
    listenLabel: String,
    pauseLabel: String,
    checkLabel: String,
    textLabel: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onCheck: () -> Unit,
    onText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(colors.readerBg)
                drawLine(
                    color = colors.hairline,
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(MushafBarRowHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = backLabel,
                onClick = onBack,
                filled = false,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = QhSpacing.x1),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = surahName,
                    style = QhTheme.type.title2,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = place,
                    style = QhTheme.type.callout.tabular(),
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            QhIconButton(
                icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) pauseLabel else listenLabel,
                onClick = onPlay,
                filled = false,
                enabled = !checking,
            )
            QhIconButton(
                icon = Icons.Rounded.Visibility,
                contentDescription = checkLabel,
                onClick = onCheck,
                filled = false,
                enabled = !checking,
            )
            QhIconButton(
                icon = Icons.Rounded.Article,
                contentDescription = textLabel,
                onClick = onText,
                filled = false,
                enabled = !checking,
            )
        }
    }
}

/** The page number in the bottom margin, between two thin lines (a printed mushaf has it there). */
@Composable
internal fun MushafFoot(page: Int, modifier: Modifier = Modifier) {
    val colors = QhTheme.colors
    Box(modifier.fillMaxWidth().height(MushafFootHeight), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
            Box(Modifier.width(28.dp).height(1.dp).background(colors.gold.copy(alpha = 0.6f)))
            Text(text = page.toString(), style = QhTheme.type.callout.tabular(), color = colors.inkSecondary)
            Box(Modifier.width(28.dp).height(1.dp).background(colors.gold.copy(alpha = 0.6f)))
        }
    }
}

/** What a page shows while its layout and font are on the way: quiet bars where the lines will be. */
@Composable
internal fun PageSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = QhSpacing.screen + 8.dp),
        verticalArrangement = Arrangement.spacedBy(QhSpacing.x6, Alignment.CenterVertically),
    ) {
        repeat(SkeletonLines) { index ->
            Skeleton(
                Modifier
                    .fillMaxWidth(if (index == SkeletonLines - 1) 0.6f else 1f)
                    .height(12.dp),
            )
        }
    }
}

private const val SkeletonLines = 12

/**
 * The page is not on the phone and cannot be fetched: "Page not loaded. The internet is needed once", with "Retry" and "Open as text"
 * (the text of the Quran is always there).
 */
@Composable
internal fun PageMissing(
    copy: PagesCopy,
    onRetry: () -> Unit,
    onOpenText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = QhSpacing.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = copy.notLoaded,
            style = QhTheme.type.title2,
            color = QhTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Column(
            Modifier.padding(top = QhSpacing.x5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhButton(text = copy.retry, onClick = onRetry, size = QhButtonSize.M)
            QhButton(text = copy.openAsText, onClick = onOpenText, variant = QhButtonVariant.Tonal, size = QhButtonSize.M)
        }
    }
}
