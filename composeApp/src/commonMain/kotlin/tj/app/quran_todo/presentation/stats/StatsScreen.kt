package tj.app.quran_todo.presentation.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.theme.progressTrack
import tj.app.quran_todo.common.theme.subtleBorder
import tj.app.quran_todo.common.theme.tintedSurface
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    var teacherReportWindow by remember { mutableStateOf(30) }

    LaunchedEffect(teacherReportWindow) {
        AppTelemetry.logEvent(
            name = "stats_report_window_changed",
            params = mapOf("window_days" to teacherReportWindow.toString())
        )
    }

    val progress = if (uiState.totalAyahs > 0) {
        uiState.learnedAyahs.toFloat() / uiState.totalAyahs
    } else {
        0f
    }
    val dailyGoal = settings.dailyGoal
    val todayEpochDay = currentLocalDate().toEpochDays()
    val remainingAyahs = (settings.targetAyahs - uiState.learnedAyahs).coerceAtLeast(0)
    val avgForProjection = uiState.avgPerDay.coerceAtLeast(1)
    val projectedDays = if (remainingAyahs == 0) 0 else ceil(remainingAyahs.toDouble() / avgForProjection).toInt()
    val targetDaysLeft = (settings.targetEpochDay - todayEpochDay).coerceAtLeast(0)
    val onTrack = projectedDays <= targetDaysLeft

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.statsTitle,
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            StatsCard(
                title = strings.statsSurahs,
                total = uiState.totalSurahs,
                segments = listOf(
                    ChartSegment(strings.learnedLabel, uiState.learnedSurahs, MaterialTheme.colors.primary),
                    ChartSegment(strings.learningLabel, uiState.learningSurahs, MaterialTheme.colors.secondary),
                    ChartSegment(strings.noStateLabel, uiState.idleSurahs, MaterialTheme.colors.subtleBorder),
                )
            )
        }

        item {
            StatsCard(
                title = strings.statsAyahs,
                total = uiState.totalAyahs,
                segments = listOf(
                    ChartSegment(strings.learnedLabel, uiState.learnedAyahs, MaterialTheme.colors.primary),
                    ChartSegment(strings.learningLabel, uiState.learningAyahs, MaterialTheme.colors.secondary),
                    ChartSegment(strings.noStateLabel, uiState.idleAyahs, MaterialTheme.colors.subtleBorder),
                )
            )
        }

        item {
            InsightsCard(
                progress = progress,
                dailyGoal = dailyGoal,
                totalAyahs = uiState.totalAyahs,
                strings = strings
            )
        }

        item {
            HighlightsCard(
                streakDays = uiState.streakDays,
                bestDayCount = uiState.bestDayCount,
                avgPerDay = uiState.avgPerDay,
                focusMinutes = uiState.focusMinutes,
                strings = strings
            )
        }

        item {
            RecitationReportCard(
                sessions = uiState.recitationSessions,
                attempts = uiState.recitationAttempts,
                matches = uiState.recitationMatches,
                accuracy = uiState.recitationAccuracy,
                confidence = uiState.recitationConfidence,
                issueRate = uiState.recitationIssueRate,
                bestStreak = uiState.recitationBestStreak,
                score = uiState.recitationScore,
                topSurahs = uiState.recitationTopSurahs,
                hasData = uiState.hasRecitationData
            )
        }

        item {
            TeacherReportCard(
                selectedWindow = teacherReportWindow,
                onWindowSelected = { teacherReportWindow = it },
                ayahUpdates7 = uiState.ayahUpdates7,
                ayahUpdates30 = uiState.ayahUpdates30,
                ayahUpdates90 = uiState.ayahUpdates90,
                focusMinutes7 = uiState.focusMinutes7,
                focusMinutes30 = uiState.focusMinutes30,
                focusMinutes90 = uiState.focusMinutes90,
                surahPeriodActivity = uiState.surahPeriodActivity,
                weakSurahReport = uiState.weakSurahReport
            )
        }

        item { WeakAyahCard(weakAyahCount = uiState.weakAyahCount) }

        item { ActivityHeatCard(counts = uiState.last14DailyCounts) }

        item {
            ProjectionCard(
                remainingAyahs = remainingAyahs,
                projectedDays = projectedDays,
                targetDaysLeft = targetDaysLeft,
                onTrack = onTrack
            )
        }
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
                    color = MaterialTheme.colors.mutedText
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
                    color = MaterialTheme.colors.mutedText
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
private fun WeakAyahCard(weakAyahCount: Int) {
    val strings = LocalAppStrings.current
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.weakAyahBankTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                weakAyahCount.toString(),
                style = MaterialTheme.typography.h6,
                color = if (weakAyahCount > 0) MaterialTheme.colors.error else MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
private fun ActivityHeatCard(counts: List<Int>) {
    val strings = LocalAppStrings.current
    val safeCounts = if (counts.isEmpty()) List(14) { 0 } else counts
    val maxValue = safeCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.last14DaysTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                safeCounts.forEach { value ->
                    val ratio = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                    val barColor = if (value == 0) {
                        MaterialTheme.colors.progressTrack
                    } else {
                        MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.24f + ratio * 0.26f)
                    }
                    Surface(
                        color = barColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height((10 + ratio * 44).dp)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ProjectionCard(
    remainingAyahs: Int,
    projectedDays: Int,
    targetDaysLeft: Int,
    onTrack: Boolean,
) {
    val strings = LocalAppStrings.current
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.projectionTitle, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$remainingAyahs ${strings.ayahsLeftLabel}",
                style = MaterialTheme.typography.body2
            )
            Text(
                text = "${strings.finishEstimateLabel}: $projectedDays ${strings.daysLabel}",
                style = MaterialTheme.typography.body2
            )
            Text(
                text = "${strings.targetWindowLabel}: $targetDaysLeft ${strings.daysLabel}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            Text(
                text = if (onTrack) strings.onTrackLabel else strings.behindPaceLabel,
                style = MaterialTheme.typography.caption,
                color = if (onTrack) MaterialTheme.colors.primary else MaterialTheme.colors.error
            )
        }
    }
}

@Composable
private fun TeacherReportCard(
    selectedWindow: Int,
    onWindowSelected: (Int) -> Unit,
    ayahUpdates7: Int,
    ayahUpdates30: Int,
    ayahUpdates90: Int,
    focusMinutes7: Int,
    focusMinutes30: Int,
    focusMinutes90: Int,
    surahPeriodActivity: List<SurahPeriodActivityUi>,
    weakSurahReport: List<WeakSurahReportUi>,
) {
    val updates = when (selectedWindow) {
        7 -> ayahUpdates7
        90 -> ayahUpdates90
        else -> ayahUpdates30
    }
    val focusMinutes = when (selectedWindow) {
        7 -> focusMinutes7
        90 -> focusMinutes90
        else -> focusMinutes30
    }
    val activeSurahs = surahPeriodActivity.mapNotNull { item ->
        val count = when (selectedWindow) {
            7 -> item.count7
            90 -> item.count90
            else -> item.count30
        }
        if (count <= 0) null else item to count
    }.sortedByDescending { it.second }

    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Teacher / Exam report",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportWindowChip(label = "7d", selected = selectedWindow == 7) { onWindowSelected(7) }
                ReportWindowChip(label = "30d", selected = selectedWindow == 30) { onWindowSelected(30) }
                ReportWindowChip(label = "90d", selected = selectedWindow == 90) { onWindowSelected(90) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HighlightItem(label = "Ayah checks", value = updates.toString())
                HighlightItem(label = "Focus min", value = focusMinutes.toString())
                HighlightItem(label = "Weak ayahs", value = weakSurahReport.sumOf { it.weakCount }.toString())
            }
            Text(
                text = "Most active surahs",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            if (activeSurahs.isEmpty()) {
                Text(
                    text = "No activity in selected period.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
            } else {
                activeSurahs.take(5).forEach { (item, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.surahLabel} #${item.surahNumber}",
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
            }
            Text(
                text = "Weak bank by surah",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            if (weakSurahReport.isEmpty()) {
                Text(
                    text = "No weak ayahs.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.mutedText
                )
            } else {
                weakSurahReport.take(6).forEach { weak ->
                    val sample = weak.sampleAyahNumbers.joinToString(", ")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${weak.surahLabel} #${weak.surahNumber}",
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = "${weak.weakCount} (${sample})",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportWindowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colors.tintedSurface(MaterialTheme.colors.primary, 0.18f)
        } else {
            MaterialTheme.colors.progressTrack
        },
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RecitationReportCard(
    sessions: Int,
    attempts: Int,
    matches: Int,
    accuracy: Float,
    confidence: Float,
    issueRate: Float,
    bestStreak: Int,
    score: Int,
    topSurahs: List<RecitationTopSurahUi>,
    hasData: Boolean,
) {
    val strings = LocalAppStrings.current
    Card(elevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.recitationReportTitle,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold
            )
            if (!hasData) {
                Text(
                    text = strings.recitationNoDataLabel,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.mutedText
                )
                return@Column
            }

            Text(
                text = "${strings.recitationScoreLabel}: $score",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary
            )
            Text(
                text = "${strings.recitationSessionsLabel}: $sessions • ${strings.recitationAttemptsLabel}: $attempts • ${strings.recitationMatchedLabel}: $matches",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HighlightItem(strings.recitationAccuracyLabel, formatPercent(accuracy))
                HighlightItem(strings.recitationFluencyLabel, formatPercent(confidence))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HighlightItem(strings.recitationIssueRateLabel, formatDecimal(issueRate))
                HighlightItem(strings.recitationBestStreakLabel, bestStreak.toString())
            }

            Text(
                text = strings.recitationTopSurahsTitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.mutedText
            )
            topSurahs.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.surahLabel} #${item.surahNumber}",
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "${item.attempts} • ${formatPercent(item.accuracy)}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary
                    )
                }
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
            color = MaterialTheme.colors.mutedText
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
            color = MaterialTheme.colors.progressTrack,
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

private fun formatPercent(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private fun formatDecimal(value: Float): String {
    val scaled = (value.coerceAtLeast(0f) * 100f).roundToInt() / 100f
    return scaled.toString()
}
