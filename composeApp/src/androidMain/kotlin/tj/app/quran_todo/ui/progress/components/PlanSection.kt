package tj.app.quran_todo.ui.progress.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.components.QhCard
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.progress.PlanLineUi
import tj.app.quran_todo.ui.progress.PlanStatus
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * The plan and the streak in one card, from `HifzRepository.forecast`: "Juz Amma: you will finish ~16 February" and under it whether the
 * person is behind or ahead of the date they set. A tap opens "My plan". Without a plan the card invites to make one.
 */
@Composable
internal fun PlanSection(
    line: PlanLineUi,
    streakDays: Int,
    today: Int,
    copy: ProgressCopy,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val type = QhTheme.type
    Column(modifier.fillMaxWidth()) {
        QhSectionHeader(title = copy.planTitle)
        Spacer(Modifier.height(QhSpacing.x3))
        if (line.status == PlanStatus.NoPlan) {
            QhCard {
                Row(horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3), verticalAlignment = Alignment.CenterVertically) {
                    PlanIcon(colors.gold)
                    Column(Modifier.weight(1f)) {
                        Text(text = copy.planNoneTitle, style = type.title2, color = colors.ink)
                        Text(text = copy.planNoneBody, style = type.callout, color = colors.inkSecondary)
                    }
                }
                Spacer(Modifier.height(QhSpacing.x3))
                QhButton(
                    text = copy.planCreate,
                    onClick = onOpenPlan,
                    variant = QhButtonVariant.Tonal,
                    size = QhButtonSize.M,
                )
                StreakLine(streakDays, copy)
            }
            return@Column
        }
        val headline = copy.planHeadline(line, today)
        val status = copy.planStatusLine(line)
        val statusColor = when (line.status) {
            PlanStatus.Behind, PlanStatus.NeedPace -> colors.due
            PlanStatus.Ahead, PlanStatus.OnTrack -> colors.learned
            else -> colors.inkSecondary
        }
        QhCard(onClick = onOpenPlan) {
            Row(
                Modifier.semantics { role = Role.Button },
                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlanIcon(if (line.status == PlanStatus.Finished) colors.learned else colors.gold)
                Column(Modifier.weight(1f)) {
                    if (headline != null) Text(text = headline, style = type.title2, color = colors.ink)
                    if (status != null) Text(text = status, style = type.callout, color = statusColor)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.inkTertiary,
                    modifier = Modifier.size(QhSpacing.iconBar),
                )
            }
            StreakLine(streakDays, copy)
        }
    }
}

/** "5 days in a row" under the plan; nothing while there is no streak. */
@Composable
private fun StreakLine(days: Int, copy: ProgressCopy) {
    if (days <= 0) return
    val colors = QhTheme.colors
    Spacer(Modifier.height(QhSpacing.x3))
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$days, ${copy.tileStreak}" },
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.gold, modifier = Modifier.size(QhSpacing.iconInline))
        Text(text = "$days", style = QhTheme.type.title2, color = colors.ink)
        Text(text = copy.tileStreak, style = QhTheme.type.callout, color = colors.inkSecondary)
    }
}

@Composable
private fun PlanIcon(tint: Color) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(QhSpacing.iconInline))
    }
}
