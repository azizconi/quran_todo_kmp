package tj.app.quran_todo.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface

@Composable
fun LanguageOnboardingScreen(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.onboardingLanguageTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.onboardingLanguageSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppLanguage.values().forEach { language ->
                    LanguageOptionCard(
                        language = language,
                        selected = language == selected,
                        onClick = { onSelected(language) }
                    )
                }
            }

            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.onboardingContinueLabel)
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colors
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp,
        backgroundColor = if (selected) {
            colors.tintedSurface(colors.primary, emphasis = 0.2f)
        } else {
            colors.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.primary else colors.subtleBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
