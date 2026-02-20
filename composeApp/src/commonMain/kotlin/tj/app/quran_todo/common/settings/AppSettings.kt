package tj.app.quran_todo.common.settings

import androidx.compose.runtime.staticCompositionLocalOf

data class AppSettings(
    val dailyGoal: Int,
    val focusMinutes: Int,
    val remindersEnabled: Boolean,
    val targetAyahs: Int,
    val targetEpochDay: Int,
    val examModeEnabled: Boolean,
)

val LocalAppSettings = staticCompositionLocalOf {
    AppSettings(
        dailyGoal = 5,
        focusMinutes = 10,
        remindersEnabled = true,
        targetAyahs = 300,
        targetEpochDay = 0,
        examModeEnabled = false
    )
}
val LocalAppSettingsSetter = staticCompositionLocalOf<(AppSettings) -> Unit> { {} }
