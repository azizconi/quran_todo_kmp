package tj.app.quran_todo.common.settings

import androidx.compose.runtime.staticCompositionLocalOf

data class AppSettings(
    val dailyGoal: Int,
    val focusMinutes: Int,
    val remindersEnabled: Boolean,
)

val LocalAppSettings = staticCompositionLocalOf { AppSettings(5, 10, true) }
val LocalAppSettingsSetter = staticCompositionLocalOf<(AppSettings) -> Unit> { {} }
