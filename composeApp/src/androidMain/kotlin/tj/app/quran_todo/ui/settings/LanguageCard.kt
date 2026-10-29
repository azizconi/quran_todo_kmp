package tj.app.quran_todo.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.haptics.HapticKind
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.motion.orSnap
import tj.app.quran_todo.ui.motion.pressable
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** The order languages are offered in (the spec's: Ру, En, Тҷ, Uz, Tr). */
internal val LanguageOrder = listOf(
    AppLanguage.RU,
    AppLanguage.EN,
    AppLanguage.TG,
    AppLanguage.UZ,
    AppLanguage.TR,
)

/** The language's own name for itself. */
internal fun AppLanguage.nativeName(): String = when (this) {
    AppLanguage.RU -> "Русский"
    AppLanguage.EN -> "English"
    AppLanguage.TG -> "Тоҷикӣ"
    AppLanguage.UZ -> "O‘zbek"
    AppLanguage.TR -> "Türkçe"
}

/** Two letters in a circle: the language's monogram. */
internal fun AppLanguage.monogram(): String = when (this) {
    AppLanguage.RU -> "Ру"
    AppLanguage.EN -> "En"
    AppLanguage.TG -> "Тҷ"
    AppLanguage.UZ -> "Uz"
    AppLanguage.TR -> "Tr"
}

/**
 * A language choice: a monogram circle, the native name and a check. Selected cards tint to
 * `primaryContainer` with a primary outline. Used by the onboarding and the settings language sheet.
 */
@Composable
internal fun LanguageCard(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QhTheme.colors
    val shape = QhTheme.shapes.lg
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surface,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "language-container",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.hairline,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "language-outline",
    )
    val monogramFill by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceMuted,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "language-monogram",
    )
    val monogramInk by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.ink,
        animationSpec = QhMotion.snappy<Color>().orSnap(),
        label = "language-monogram-ink",
    )
    val badgeIn = QhMotion.bouncy<Float>().orSnap()
    val badgeOut = QhMotion.snappy<Float>().orSnap()

    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, haptic = HapticKind.Segment, shape = shape, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .heightIn(min = 64.dp)
            .padding(horizontal = QhSpacing.x4, vertical = QhSpacing.x2),
        horizontalArrangement = Arrangement.spacedBy(QhSpacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(monogramFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = language.monogram(),
                style = QhTheme.type.label,
                fontWeight = FontWeight.Bold,
                color = monogramInk,
                maxLines = 1,
            )
        }
        Text(
            text = language.nativeName(),
            style = QhTheme.type.title2,
            color = if (selected) colors.onPrimaryContainer else colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(badgeIn, initialScale = 0.2f) + fadeIn(badgeOut),
            exit = scaleOut(badgeOut, targetScale = 0.2f) + fadeOut(badgeOut),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(QhSpacing.iconBar + 2.dp),
            )
        }
    }
}
