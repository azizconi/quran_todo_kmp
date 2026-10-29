package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.components.ArabesqueLattice
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.settings.LanguageCard
import tj.app.quran_todo.ui.settings.LanguageOrder
import tj.app.quran_todo.ui.settings.bleed
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Step 1: the calligraphy bismillah written from right to left on a faint lattice, the promise of the app, and the five languages.
 * A language applies at once (the root's setter), so the text of this very screen changes under the finger.
 */
@Composable
internal fun LanguageStep(
    copy: OnboardingCopy,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    val colors = QhTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QhSpacing.screen)
            .padding(bottom = QhSpacing.x4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The calligraphy sits on a faint ogee lattice that fades out toward every edge.
        Box(
            Modifier
                .fillMaxWidth()
                .bleed(QhSpacing.screen),
            contentAlignment = Alignment.Center,
        ) {
            ArabesqueLattice(Modifier.matchParentSize(), alpha = 0.09f)
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.bg,
                            0.35f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to colors.bg,
                        ),
                    ),
            )
            Column(
                Modifier
                    .padding(horizontal = QhSpacing.screen)
                    .padding(vertical = QhSpacing.x6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
            ) {
                WelcomeCalligraphy()
                Spacer(Modifier.height(QhSpacing.x2))
                Text(
                    text = "Quran Hifz",
                    style = QhTheme.type.display,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                CrossfadeText(
                    text = copy.welcomeSubtitle,
                    style = QhTheme.type.body,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x4))
        Crossfade(
            targetState = copy.chooseLanguage,
            modifier = Modifier.fillMaxWidth(),
            animationSpec = if (LocalReducedMotion.current) snap() else tween(QhMotion.Medium),
            label = "choose-language",
        ) { title -> QhSectionHeader(title) }
        Spacer(Modifier.height(QhSpacing.x3))
        Column(verticalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
            LanguageOrder.forEach { option ->
                LanguageCard(
                    language = option,
                    selected = option == language,
                    onClick = { onSelectLanguage(option) },
                )
            }
        }
        Spacer(Modifier.height(QhSpacing.x3))
        CrossfadeText(
            text = copy.chooseLanguageHint,
            style = QhTheme.type.callout,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
