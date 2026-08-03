package tj.app.quran_todo.presentation.surah

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.MaterialTheme as Material2Theme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformSurahReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val readerColors = Material2Theme.extendedColors
    val titleColor = Material2Theme.colors.onBackground
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = readerColors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = readerColors.readingBackground,
            scrolledContainerColor = readerColors.readingBackground,
            navigationIconContentColor = titleColor,
            titleContentColor = titleColor,
        ),
    )
}
