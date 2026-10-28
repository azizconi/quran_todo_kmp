package tj.app.quran_todo.ui.hifz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLinearProgress
import tj.app.quran_todo.ui.motion.AnimatedCounter
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.reader.components.ReaderTopBarRowHeight
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The eye button of the reader's top bar: starts the memorize mode at the ayah the reader is on. */
@Composable
fun HifzEyeButton(mode: HifzMode, modifier: Modifier = Modifier) {
    QhIconButton(
        icon = Icons.Rounded.Visibility,
        contentDescription = mode.copy.eyeAction,
        onClick = mode::start,
        filled = false,
        modifier = modifier,
    )
}

/**
 * The top bar of the mode. It takes the place of the reader's bar (same height, status bar included) with a
 * `goldContainer` background: a close button, "Memorize mode · 4 / 10" with the number rolling, the surah and
 * ayah underneath, and the progress of the session along the bottom edge. It slides in over the reader's bar
 * and out again when the mode ends.
 */
@Composable
fun HifzTopBar(mode: HifzMode, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val session = mode.session
    // Kept while the bar leaves, after the session is gone.
    val held = remember { arrayOfNulls<HifzSession>(1) }
    if (session != null) held[0] = session
    AnimatedVisibility(
        visible = session != null,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(tween(0))
        } else {
            fadeIn(tween(QhMotion.Medium)) + slideInVertically(QhMotion.enter(QhMotion.Medium)) { -it / 3 }
        },
        exit = if (reduced) {
            fadeOut(tween(0))
        } else {
            fadeOut(tween(QhMotion.Short)) + slideOutVertically(QhMotion.exit(QhMotion.Short)) { -it / 3 }
        },
    ) {
        held[0]?.let { HifzTopBarContent(mode, it) }
    }
}

@Composable
private fun HifzTopBarContent(mode: HifzMode, session: HifzSession) {
    val colors = QhTheme.colors
    val copy = mode.copy
    val title = QhTheme.type.title2
    val position = (session.index + 1).coerceAtMost(session.size)
    val ayahNumber = (session.current ?: session.items.last()).inSurah
    Column(
        Modifier
            .fillMaxWidth()
            // The bar sits over the list: taps on its empty part must not reach the ayahs behind it.
            .pointerInput(Unit) { }
            .background(colors.goldContainer)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ReaderTopBarRowHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QhIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = copy.exit,
                onClick = mode.viewModel::requestExit,
                filled = false,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = QhSpacing.x1, end = QhSpacing.x3)
                    .semantics(mergeDescendants = true) { heading() },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${copy.modeName} · ",
                        style = title,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AnimatedCounter(value = position, style = title, color = colors.ink)
                    Text(text = " / ${session.size}", style = title, color = colors.ink, maxLines = 1)
                }
                Text(
                    text = "${mode.surahName} · ${copy.ayah} $ayahNumber",
                    style = QhTheme.type.callout,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        QhLinearProgress(
            progress = position.toFloat() / session.size.coerceAtLeast(1),
            height = 2.dp,
            color = colors.gold,
            trackColor = colors.gold.copy(alpha = 0.25f),
        )
    }
}
