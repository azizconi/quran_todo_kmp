package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** Step 5: one question, two big cards. Choosing "Memorize" opens the plan steps, "Read and listen" goes to the reading step. */
@Composable
internal fun GoalStep(
    copy: OnboardingCopy,
    goal: GoalChoice?,
    onChoose: (GoalChoice) -> Unit,
) {
    StepColumn {
        StepHeader(copy.goalTitle, copy.goalSubtitle)
        GoalCard(
            selected = goal == GoalChoice.Memorize,
            icon = Icons.Rounded.Psychology,
            title = copy.goalMemorizeTitle,
            body = copy.goalMemorizeBody,
            onClick = { onChoose(GoalChoice.Memorize) },
        )
        GoalCard(
            selected = goal == GoalChoice.Read,
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            secondIcon = Icons.Rounded.Headphones,
            title = copy.goalReadTitle,
            body = copy.goalReadBody,
            onClick = { onChoose(GoalChoice.Read) },
        )
    }
}

@Composable
private fun GoalCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    secondIcon: ImageVector? = null,
) {
    val colors = QhTheme.colors
    val reduced = LocalReducedMotion.current
    ChoiceCard(selected = selected, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2)) {
                GoalIcon(icon, selected)
                if (secondIcon != null) GoalIcon(secondIcon, selected)
            }
            AnimatedVisibility(
                visible = selected,
                enter = if (reduced) fadeIn(snap()) else fadeIn(QhMotion.snappy()) + scaleIn(QhMotion.bouncy(), initialScale = 0.5f),
                exit = if (reduced) fadeOut(snap()) else fadeOut(QhMotion.snappy()) + scaleOut(QhMotion.snappy(), targetScale = 0.5f),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(QhTheme.shapes.full)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x1)) {
            Box(Modifier.size(QhSpacing.x2))
            Text(text = title, style = QhTheme.type.title1, color = colors.ink)
            Text(text = body, style = QhTheme.type.body, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun GoalIcon(icon: ImageVector, selected: Boolean) {
    val colors = QhTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(QhTheme.shapes.md)
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
    }
}
