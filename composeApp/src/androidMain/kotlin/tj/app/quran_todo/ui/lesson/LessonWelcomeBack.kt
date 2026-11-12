package tj.app.quran_todo.ui.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.welcomeBackCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * "Welcome back" (task U17): shown before the first card when days were missed. Calm: no red, no blame. Two large choices; the system
 * back button leaves the lesson without answering ([onBack]), so the screen shows again next time.
 */
@Composable
internal fun WelcomeBackScreen(
    ui: WelcomeBackUi,
    lessonCopy: LessonCopy,
    onCatchUp: () -> Unit,
    onLighter: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = QhTheme.colors
    val language = LocalAppLanguage.current
    val copy = remember(language) { welcomeBackCopy(language) }
    Column(Modifier.fillMaxSize()) {
        LessonTopBar(copy = lessonCopy, subtitle = null, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x4),
        ) {
            Text(
                text = copy.title,
                style = QhTheme.type.title1,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(QhTheme.shapes.lg)
                    .background(colors.surfaceMuted)
                    .padding(QhSpacing.x4),
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                Text(text = copy.missed(ui.missedDays), style = QhTheme.type.body, color = colors.ink)
                Text(
                    text = copy.today(ui.repetitions, ui.minutes, ui.hasNew),
                    style = QhTheme.type.body,
                    color = colors.inkSecondary,
                )
                // Never "0": a best streak of nothing is left out.
                if (ui.bestStreak > 0) {
                    Text(text = copy.best(ui.bestStreak), style = QhTheme.type.callout, color = colors.inkSecondary)
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QhSpacing.screen)
                .padding(bottom = QhSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        ) {
            QhButton(text = copy.catchUp, onClick = onCatchUp, modifier = Modifier.fillMaxWidth())
            QhButton(
                text = copy.lighter,
                onClick = onLighter,
                modifier = Modifier.fillMaxWidth(),
                variant = QhButtonVariant.Tonal,
            )
            Text(
                text = copy.lighterNote,
                style = QhTheme.type.callout,
                color = colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
