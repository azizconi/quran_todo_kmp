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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.theme.AyahCardStyle
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface

@Composable
fun CardStyleOnboardingScreen(
    selected: AyahCardStyle,
    onSelected: (AyahCardStyle) -> Unit,
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
                text = strings.onboardingCardStyleTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.onboardingCardStyleSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )

            CardStylePreview(style = selected)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AyahCardStyle.values().forEach { style ->
                    CardStyleOption(
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
private fun CardStylePreview(style: AyahCardStyle) {
    val colors = MaterialTheme.colors
    val shape = when (style) {
        AyahCardStyle.CLASSIC -> RoundedCornerShape(16.dp)
        AyahCardStyle.COMPACT -> RoundedCornerShape(12.dp)
        AyahCardStyle.FOCUS -> RoundedCornerShape(20.dp)
    }
    val background = when (style) {
        AyahCardStyle.CLASSIC -> colors.surface
        AyahCardStyle.COMPACT -> colors.tintedSurface(colors.primary, 0.1f)
        AyahCardStyle.FOCUS -> colors.tintedSurface(colors.secondary, 0.16f)
    }
    val arabicSize = when (style) {
        AyahCardStyle.CLASSIC -> 24.sp
        AyahCardStyle.COMPACT -> 21.sp
        AyahCardStyle.FOCUS -> 27.sp
    }
    val translationSize = when (style) {
        AyahCardStyle.COMPACT -> MaterialTheme.typography.caption
        AyahCardStyle.CLASSIC, AyahCardStyle.FOCUS -> MaterialTheme.typography.body2
    }
    Card(
        shape = shape,
        elevation = if (style == AyahCardStyle.COMPACT) 1.dp else 4.dp,
        backgroundColor = background,
        border = BorderStroke(1.dp, colors.subtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.caption,
                color = colors.primary
            )
            Text(
                text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                textAlign = TextAlign.End,
                fontSize = arabicSize,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "In the name of Allah, the Most Gracious, the Most Merciful",
                style = translationSize,
                color = colors.mutedText
            )
        }
    }
}

@Composable
private fun CardStyleOption(
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
