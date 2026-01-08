package tj.app.quran_todo.presentation.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.LocalAppSettings

data class ChartSegment(
    val label: String,
    val value: Int,
    val color: Color,
)

@Composable
fun StatsScreen(viewModel: StatsViewModel = koinViewModel()) {
    val strings = LocalAppStrings.current
    val settings = LocalAppSettings.current

    val uiState by viewModel.uiState.collectAsState()

    val progress = if (uiState.totalAyahs > 0) {
        uiState.learnedAyahs.toFloat() / uiState.totalAyahs
    } else {
        0f
    }
    val dailyGoal = settings.dailyGoal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = strings.statsTitle,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        StatsCard(
            title = strings.statsSurahs,
            total = uiState.totalSurahs,
            segments = listOf(
                ChartSegment(strings.learnedLabel, uiState.learnedSurahs, MaterialTheme.colors.primary),
                ChartSegment(strings.learningLabel, uiState.learningSurahs, MaterialTheme.colors.secondary),
                ChartSegment(strings.noStateLabel, uiState.idleSurahs, MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
            )
        )

        StatsCard(
            title = strings.statsAyahs,
            total = uiState.totalAyahs,
            segments = listOf(
                ChartSegment(strings.learnedLabel, uiState.learnedAyahs, MaterialTheme.colors.primary),
                ChartSegment(strings.learningLabel, uiState.learningAyahs, MaterialTheme.colors.secondary),
                ChartSegment(strings.noStateLabel, uiState.idleAyahs, MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
            )
        )

        InsightsCard(
            progress = progress,
            dailyGoal = dailyGoal,
            totalAyahs = uiState.totalAyahs,
            strings = strings
        )

        HighlightsCard(
            streakDays = uiState.streakDays,
            bestDayCount = uiState.bestDayCount,
            avgPerDay = uiState.avgPerDay,
            focusMinutes = uiState.focusMinutes,
            strings = strings
        )
    }
}

@Composable
private fun StatsCard(
    title: String,
    total: Int,
    segments: List<ChartSegment>,
) {
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DonutChart(
                    total = total,
                    segments = segments
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    segments.forEach { segment ->
                        LegendRow(segment)
                    }
                }
            }
            Text(
                text = "${LocalAppStrings.current.totalLabel}: $total",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
private fun InsightsCard(
    progress: Float,
    dailyGoal: Int,
    totalAyahs: Int,
    strings: tj.app.quran_todo.common.i18n.AppStrings,
) {
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = strings.insightsTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = strings.progressTitle, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
                Text(
                    text = strings.progressSubtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                ProgressBar(progress = progress)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.caption
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = strings.goalTitle, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
                Text(
                    text = strings.goalSubtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = if (totalAyahs == 0) "-" else "$dailyGoal",
                    style = MaterialTheme.typography.subtitle1
                )
            }
        }
    }
}

@Composable
private fun HighlightsCard(
    streakDays: Int,
    bestDayCount: Int,
    avgPerDay: Int,
    focusMinutes: Int,
    strings: tj.app.quran_todo.common.i18n.AppStrings,
) {
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = strings.insightsTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HighlightItem(label = strings.streakLabel, value = streakDays.toString())
                HighlightItem(label = strings.bestDayLabel, value = bestDayCount.toString())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HighlightItem(label = strings.avgPerDayLabel, value = avgPerDay.toString())
                HighlightItem(label = strings.focusMinutesStatsLabel, value = focusMinutes.toString())
            }
        }
    }
}

@Composable
private fun HighlightItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = value, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .padding(vertical = 2.dp)
    ) {
        Surface(
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {}
        Surface(
            color = MaterialTheme.colors.primary,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(animated)
        ) {}
    }
}

@Composable
private fun DonutChart(
    total: Int,
    segments: List<ChartSegment>,
) {
    var startAnimation by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "chartProgress"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Canvas(modifier = Modifier.size(120.dp)) {
        val strokeWidth = 16.dp.toPx()
        var startAngle = -90f
        val totalSafe = if (total <= 0) 1 else total
        segments.forEach { segment ->
            val sweep = (segment.value.toFloat() / totalSafe) * 360f * progress
            if (sweep > 0f) {
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(
                        size.width - strokeWidth,
                        size.height - strokeWidth
                    )
                )
            }
            startAngle += (segment.value.toFloat() / totalSafe) * 360f * progress
        }
    }
}

@Composable
private fun LegendRow(segment: ChartSegment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRect(segment.color)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "${segment.label}: ${segment.value}",
            style = MaterialTheme.typography.caption
        )
    }
}
