package tj.app.quran_todo.presentation.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

data class ChartSegment(
    val label: String,
    val value: Int,
    val color: Color,
)

@Composable
fun StatsScreen(viewModel: StatsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Статистика",
            style = MaterialTheme.typography.h5
        )

        StatsCard(
            title = "Суры",
            total = uiState.totalSurahs,
            segments = listOf(
                ChartSegment("Выучено", uiState.learnedSurahs, MaterialTheme.colors.primary),
                ChartSegment("Заучиваю", uiState.learningSurahs, MaterialTheme.colors.secondary),
                ChartSegment("Без статуса", uiState.idleSurahs, MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
            )
        )

        StatsCard(
            title = "Аяты",
            total = uiState.totalAyahs,
            segments = listOf(
                ChartSegment("Выучено", uiState.learnedAyahs, MaterialTheme.colors.primary),
                ChartSegment("Заучиваю", uiState.learningAyahs, MaterialTheme.colors.secondary),
                ChartSegment("Без статуса", uiState.idleAyahs, MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
            )
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    total: Int,
    segments: List<ChartSegment>,
) {
    Card(elevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.subtitle1)
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
                text = "Всего: $total",
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
fun DonutChart(
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
fun LegendRow(segment: ChartSegment) {
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
