package tj.app.quran_todo.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.presentation.onboarding.FontOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.LanguageOnboardingScreen

private const val StepLanguage = 0
private const val StepFont = 1

/**
 * Placeholder of the onboarding: phase 1C replaces the body. Until then it runs the old language and
 * font screens one after the other. Choices are applied through the root setters, so they are saved
 * as soon as they are made.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val language = LocalAppLanguage.current
    val setLanguage = LocalAppLanguageSetter.current
    val fontStyle = LocalReadingFontStyle.current
    val setFontStyle = LocalReadingFontStyleSetter.current
    val settings = LocalAppSettings.current
    val setSettings = LocalAppSettingsSetter.current

    // A returning user who already chose a language only sees the remaining step.
    val firstStep = remember { if (LanguageStorage.getSavedLanguage() == null) StepLanguage else StepFont }
    var step by rememberSaveable { mutableIntStateOf(firstStep) }

    BackHandler(enabled = step > firstStep) { step = StepLanguage }

    when (step) {
        StepLanguage -> LanguageOnboardingScreen(
            selected = language,
            onSelected = setLanguage,
            onContinue = {
                LanguageStorage.saveLanguage(language)
                AppTelemetry.logEvent(
                    name = "onboarding_completed",
                    params = mapOf("step" to "language", "language" to language.name.lowercase()),
                )
                step = StepFont
            },
        )

        else -> FontOnboardingScreen(
            selected = fontStyle,
            fontSize = settings.readingFontSize,
            onSelected = setFontStyle,
            onFontSizeChange = { size ->
                setSettings(settings.copy(readingFontSize = size.coerceIn(24, 34)))
            },
            onContinue = {
                ThemeStorage.saveReadingFontStyle(fontStyle)
                UserSettingsStorage.saveReadingFontSize(settings.readingFontSize)
                AppTelemetry.logEvent(
                    name = "onboarding_completed",
                    params = mapOf("step" to "font", "font" to fontStyle.name.lowercase()),
                )
                onFinished()
            },
        )
    }
}
