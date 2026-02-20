package tj.app.quran_todo.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.progressTrack
import tj.app.quran_todo.common.theme.softSurface
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface

@Composable
fun FeatureGuideOnboardingScreen(
    onContinue: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val items = remember(strings) { featureGuideItems(strings) }
    var currentIndex by remember { mutableStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val item = items.getOrNull(safeIndex)

    if (item == null) {
        onContinue()
        return
    }

    val isLast = safeIndex == items.lastIndex

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
                text = strings.onboardingFeaturesTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.onboardingFeaturesSubtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.mutedText
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
                backgroundColor = MaterialTheme.colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeaturePreview(
                        kind = item.previewKind,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.mutedText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val active = index == safeIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (active) 18.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                color = if (active) {
                                    MaterialTheme.colors.primary
                                } else {
                                    MaterialTheme.colors.subtleBorder
                                },
                                shape = RoundedCornerShape(100)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (safeIndex > 0) {
                    IconButton(
                        onClick = {
                            currentIndex = (safeIndex - 1).coerceAtLeast(0)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Button(
                    onClick = {
                        if (isLast) {
                            onContinue()
                        } else {
                            currentIndex = (safeIndex + 1).coerceAtMost(items.lastIndex)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLast) strings.onboardingStartLabel else strings.onboardingContinueLabel)
                }
            }
        }
    }
}

@Composable
private fun FeaturePreview(
    kind: FeatureGuidePreviewKind,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
        backgroundColor = MaterialTheme.colors.softSurface,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.18f),
                            MaterialTheme.colors.tintedSurface(MaterialTheme.colors.secondary, 0.12f),
                            MaterialTheme.colors.surface
                        )
                    )
                )
                .padding(12.dp)
        ) {
            when (kind) {
                FeatureGuidePreviewKind.RECITATION -> RecitationPreview()
                FeatureGuidePreviewKind.EXAM_MODE -> ExamModePreview()
                FeatureGuidePreviewKind.WEAK_BANK -> WeakBankPreview()
                FeatureGuidePreviewKind.VOICE_CHECK -> VoiceCheckPreview()
                FeatureGuidePreviewKind.AB_COMPARE -> AbComparePreview()
                FeatureGuidePreviewKind.OFFLINE_PACKAGE -> OfflinePackagePreview()
                FeatureGuidePreviewKind.PROJECTION -> ProjectionPreview()
                FeatureGuidePreviewKind.RECITATION_REPORT -> RecitationReportPreview()
            }
        }
    }
}

@Composable
private fun RecitationPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("Mic LIVE", MaterialTheme.colors.error)
            PreviewChip("Ayah 4", MaterialTheme.colors.primary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "ar-rahman allamal quran",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Issue: missing word",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error
            )
        }
        PreviewPanel {
            LinearProgressIndicator(
                progress = 0.56f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.progressTrack
            )
        }
    }
}

@Composable
private fun ExamModePreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("Exam ON", MaterialTheme.colors.secondary)
            PreviewChip("Reveal", MaterialTheme.colors.primary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = ".......................",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Text opens after voice match",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
        PreviewPanel {
            Text(
                text = "Tap reveal or read by microphone",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun WeakBankPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("Weak Ayahs", MaterialTheme.colors.error)
            PreviewChip("Quick Filter", MaterialTheme.colors.primary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            WeakRow("2:255")
            Spacer(modifier = Modifier.height(6.dp))
            WeakRow("18:10")
            Spacer(modifier = Modifier.height(6.dp))
            WeakRow("67:14")
        }
    }
}

@Composable
private fun VoiceCheckPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "Voice check rating",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewChip("Hard", MaterialTheme.colors.error)
                PreviewChip("Good", MaterialTheme.colors.primary)
                PreviewChip("Easy", MaterialTheme.colors.secondary)
            }
        }
        PreviewPanel {
            Text(
                text = "Updates spaced repetition",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun AbComparePreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("A 0.8x", MaterialTheme.colors.primary)
            PreviewChip("B 1.2x", MaterialTheme.colors.secondary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "Compare pronunciation tempo",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(10.dp, 18.dp, 12.dp, 20.dp, 14.dp, 16.dp, 9.dp, 15.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h)
                            .background(
                                color = MaterialTheme.colors.tintedSurface(
                                    MaterialTheme.colors.primary,
                                    0.26f
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflinePackagePreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("Offline package", MaterialTheme.colors.primary)
            PreviewChip("67 / 120", MaterialTheme.colors.secondary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "Downloading audio + translations",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = 0.56f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.progressTrack
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ready for low internet mode",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun ProjectionPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("On track", MaterialTheme.colors.primary)
            PreviewChip("14 days", MaterialTheme.colors.secondary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "Remaining: 120 ayahs",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(10.dp, 14.dp, 16.dp, 22.dp, 18.dp, 24.dp, 26.dp).forEachIndexed { index, h ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h)
                            .background(
                                color = if (index >= 4) {
                                    MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.24f)
                                } else {
                                    MaterialTheme.colors.tintedSurface(MaterialTheme.colors.secondary, 0.22f)
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RecitationReportPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip("Score 84", MaterialTheme.colors.primary)
            PreviewChip("Best streak 9", MaterialTheme.colors.secondary)
        }
        PreviewPanel(Modifier.weight(1f)) {
            Text(
                text = "Accuracy 88%  Fluency 82%",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Issue rate 0.18",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Top: Al-Fatiha, Al-Mulk, Yaseen",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun WeakRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ayah $label",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colors.error, CircleShape)
        )
    }
}

@Composable
private fun PreviewPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colors.subtleBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PreviewChip(
    label: String,
    tint: Color,
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colors.tintedSurface(tint, 0.24f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
