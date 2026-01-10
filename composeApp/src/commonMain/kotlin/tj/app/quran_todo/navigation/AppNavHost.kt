package tj.app.quran_todo.navigation

import androidx.compose.runtime.Composable
import tj.app.quran_todo.AppTab

@Composable
expect fun AppNavHost(current: AppTab)
