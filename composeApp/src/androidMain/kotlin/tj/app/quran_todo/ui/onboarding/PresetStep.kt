package tj.app.quran_todo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tj.app.quran_todo.ui.components.QhButton
import tj.app.quran_todo.ui.components.QhButtonSize
import tj.app.quran_todo.ui.components.QhButtonVariant
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.PlanCopy
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.QhMotion
import tj.app.quran_todo.ui.plan.AddSurahSheet
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/**
 * Step 7 (only "Memorize"): what to learn. Four cards, each with the first lesson it would give with the known surahs left out
 * ("We start with Al-Falaq · 5 ayahs"). A preset that is known entirely says so and can not be chosen. "Choose surahs" opens the
 * search and keeps the order they were chosen in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetStep(
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    ui: OnboardingUi,
    onChoose: (PresetChoice) -> Unit,
    onAddCustom: (Int) -> Unit,
    onRemoveCustom: (Int) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val selected = ui.selection.preset
    var adding by rememberSaveable { mutableStateOf(false) }

    StepColumn {
        StepHeader(copy.presetTitle, copy.presetSubtitle)
        PresetChoice.entries.forEach { preset ->
            val info = ui.presets.first { it.preset == preset }
            PresetCard(
                title = when (preset) {
                    PresetChoice.JuzAmma -> copy.presetJuzAmma
                    PresetChoice.JuzTabarak -> copy.presetJuzTabarak
                    PresetChoice.FromStart -> copy.presetFromStart
                    PresetChoice.Custom -> copy.presetChoose
                },
                info = info,
                selected = selected == preset,
                copy = copy,
                planCopy = planCopy,
                surahName = ui::surahName,
                onClick = {
                    onChoose(preset)
                    // An empty own list has nothing to show yet: the search opens at once.
                    if (preset == PresetChoice.Custom && ui.selection.customQueue.isEmpty()) adding = true
                },
            )
            if (preset == PresetChoice.Custom) {
                AnimatedVisibility(
                    visible = selected == PresetChoice.Custom,
                    enter = if (reduced) fadeIn(snap()) else expandVertically(QhMotion.gentle()) + fadeIn(QhMotion.gentle()),
                    exit = if (reduced) fadeOut(snap()) else shrinkVertically(QhMotion.gentle()) + fadeOut(QhMotion.gentle()),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QhSpacing.x3)) {
                        if (ui.selection.customQueue.isNotEmpty()) {
                            Text(copy.customOrderHint, style = QhTheme.type.callout, color = QhTheme.colors.inkSecondary)
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                                verticalArrangement = Arrangement.spacedBy(QhSpacing.x2),
                            ) {
                                ui.selection.customQueue.forEachIndexed { index, surah ->
                                    val name = ui.surahName(surah) ?: surah.toString()
                                    RemovableChip(label = "${index + 1}. $name", removeLabel = copy.remove(name), onRemove = { onRemoveCustom(surah) })
                                }
                            }
                        }
                        QhButton(
                            text = planCopy.addSurah,
                            onClick = { adding = true },
                            modifier = Modifier.fillMaxWidth(),
                            variant = QhButtonVariant.Tonal,
                            size = QhButtonSize.M,
                            icon = Icons.Rounded.Add,
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddSurahSheet(
            catalog = ui.catalog,
            inQueue = ui.selection.customQueue.toSet(),
            copy = planCopy,
            onAdd = onAddCustom,
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun PresetCard(
    title: String,
    info: PresetInfo,
    selected: Boolean,
    copy: OnboardingCopy,
    planCopy: PlanCopy,
    surahName: (Int) -> String?,
    onClick: () -> Unit,
) {
    val colors = QhTheme.colors
    val isCustom = info.preset == PresetChoice.Custom
    val lesson = info.firstLesson
    // What the card says under the title: the size of the list, then where the first lesson will be.
    val sizeLine = when {
        isCustom && info.surahCount == 0 -> copy.customEmpty
        else -> planCopy.scopeInfo(info.surahCount, info.ayahCount)
    }
    val lessonLine = when {
        info.allKnown -> when (info.preset) {
            PresetChoice.JuzAmma -> copy.allKnownJuzAmma
            PresetChoice.JuzTabarak -> copy.allKnownJuzTabarak
            PresetChoice.FromStart -> copy.allKnownFromStart
            PresetChoice.Custom -> copy.allKnownCustom
        }
        lesson != null -> copy.presetStart(surahName(lesson.surah) ?: lesson.surah.toString(), lesson.ayahCount)
        else -> null
    }
    ChoiceCard(selected = selected, onClick = onClick, enabled = info.selectable) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = QhTheme.type.title1,
                color = if (info.selectable) colors.ink else colors.inkTertiary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }
        }
        Text(text = sizeLine, style = QhTheme.type.callout, color = colors.inkSecondary)
        if (lessonLine != null) {
            Text(
                text = lessonLine,
                style = QhTheme.type.title2,
                color = if (info.allKnown) colors.inkSecondary else colors.primary,
            )
        }
    }
}
