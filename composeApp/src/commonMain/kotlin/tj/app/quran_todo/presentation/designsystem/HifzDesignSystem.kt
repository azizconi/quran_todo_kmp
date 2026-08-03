package tj.app.quran_todo.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.theme.extendedColors

private val PanelShape = RoundedCornerShape(8.dp)
private val ActionShape = RoundedCornerShape(18.dp)

@Composable
fun HifzScreen(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun HifzPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.border),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HifzSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colors.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MaterialTheme.extendedColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun HifzProgressCapsule(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colors.primary,
) {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.extendedColors.surfaceAlt),
    ) {
        if (normalizedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxOf(8.dp, maxWidth * normalizedProgress))
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

@Composable
fun HifzPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val extendedColors = MaterialTheme.extendedColors
    val buttonModifier = modifier
        .fillMaxWidth()
        .then(
            if (enabled) {
                Modifier.shadow(
                    elevation = 12.dp,
                    shape = ActionShape,
                    clip = false,
                    ambientColor = extendedColors.shadow,
                    spotColor = extendedColors.shadow,
                )
            } else {
                Modifier
            },
        )

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled,
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary,
            contentColor = MaterialTheme.colors.onPrimary,
            disabledBackgroundColor = extendedColors.surfaceAlt,
            disabledContentColor = extendedColors.textSecondary,
        ),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun HifzSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val extendedColors = MaterialTheme.extendedColors
    val contentColor = if (enabled) {
        MaterialTheme.colors.onSurface
    } else {
        extendedColors.textSecondary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ActionShape)
            .adaptiveClickable(
                enabled = enabled,
                shape = ActionShape,
                onClick = onClick,
            ),
        shape = ActionShape,
        color = extendedColors.utilitySurface.copy(alpha = if (enabled) 1f else 0.8f),
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = extendedColors.selectionBorder.copy(alpha = if (enabled) 1f else 0.55f),
        ),
        elevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun HifzMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colors.primary,
) {
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = MaterialTheme.extendedColors.surfaceAlt.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                color = MaterialTheme.colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
fun HifzStatusChip(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        elevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun HifzStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
) {
    val safeStep = step.coerceAtLeast(1)
    val (decreaseLabel, increaseLabel) = stepperActionLabels(LocalAppLanguage.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        HifzStepButton(
            contentDescription = "$decreaseLabel: $label",
            enabled = value > range.first,
            onClick = {
                onValueChange((value - safeStep).coerceAtLeast(range.first))
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 32.dp),
            color = MaterialTheme.colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        HifzStepButton(
            contentDescription = "$increaseLabel: $label",
            enabled = value < range.last,
            onClick = {
                onValueChange((value + safeStep).coerceAtMost(range.last))
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
fun HifzToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extendedColors = MaterialTheme.extendedColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colors.primary,
                uncheckedThumbColor = extendedColors.textSecondary,
                uncheckedTrackColor = extendedColors.utilitySurface,
                uncheckedBorderColor = extendedColors.utilityBorder,
            ),
        )
    }
}

@Composable
private fun HifzStepButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val iconColor = if (enabled) {
        MaterialTheme.colors.onSurface
    } else {
        MaterialTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .adaptiveClickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                shape = CircleShape,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.extendedColors.utilitySurface),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material.LocalContentColor provides iconColor,
                content = content,
            )
        }
    }
}

private fun stepperActionLabels(language: AppLanguage): Pair<String, String> = when (language) {
    AppLanguage.RU -> "Уменьшить" to "Увеличить"
    AppLanguage.TG -> "Кам кардан" to "Зиёд кардан"
    AppLanguage.UZ -> "Kamaytirish" to "Ko‘paytirish"
    AppLanguage.TR -> "Azalt" to "Artır"
    AppLanguage.EN -> "Decrease" to "Increase"
}
