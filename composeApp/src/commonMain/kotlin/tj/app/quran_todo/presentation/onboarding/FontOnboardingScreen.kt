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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.quran_font
import qurantodo.composeapp.generated.resources.quran_font_2
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface

@Composable
fun FontOnboardingScreen(
    selected: ReadingFontStyle,
    onSelected: (ReadingFontStyle) -> Unit,
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
                text = strings.onboardingFontTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.onboardingFontSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
                backgroundColor = MaterialTheme.colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "الرَّحْمَٰنُ عَلَّمَ الْقُرْآنَ",
                    style = MaterialTheme.typography.body1.copy(
                        fontSize = 28.sp,
                        fontFamily = previewFontFamily(selected)
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReadingFontStyle.values().forEach { style ->
                    FontOptionCard(
                        label = style.label(strings),
                        selected = style == selected,
                        onClick = { onSelected(style) }
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
private fun FontOptionCard(
    label: String,
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
            text = label,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun previewFontFamily(style: ReadingFontStyle): FontFamily {
    return when (style) {
        ReadingFontStyle.MUSHAF_MODERN -> FontFamily(
            Font(Res.font.quran_font_2, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.MUSHAF_CLASSIC -> FontFamily(
            Font(Res.font.quran_font, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.SANS -> FontFamily.SansSerif
        ReadingFontStyle.SERIF -> FontFamily.Serif
        ReadingFontStyle.MONO -> FontFamily.Monospace
    }
}
