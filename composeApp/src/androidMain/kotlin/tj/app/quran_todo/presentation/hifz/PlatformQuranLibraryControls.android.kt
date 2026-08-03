package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformQuranTopBar(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val strings = LocalAppStrings.current
    val titleColor = MaterialTheme.colors.onBackground
    TopAppBar(
        title = {
            Text(
                text = title,
                color = titleColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = strings.settingsTitle,
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colors.background,
            scrolledContainerColor = MaterialTheme.colors.background,
            titleContentColor = titleColor,
            actionIconContentColor = titleColor,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformQuranSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    val libraryColors = MaterialTheme.extendedColors
    val fieldBackgroundColor = libraryColors.libraryControlSurface
    val fieldBorderColor = libraryColors.libraryControlBorder
    val secondaryColor = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = fieldBackgroundColor,
        border = BorderStroke(1.dp, fieldBorderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        SearchBarDefaults.InputField(
            query = value,
            onQueryChange = onValueChange,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = secondaryColor,
                    fontSize = 15.sp,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
            colors = SearchBarDefaults.colors(
                containerColor = fieldBackgroundColor,
                dividerColor = Color.Transparent,
            ).inputFieldColors,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformQuranSegmentedControl(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.extendedColors
    val selectedContainerColor = if (MaterialTheme.colors.isLight) {
        Color(0xFFBDE7D5)
    } else {
        colors.primaryWeak
    }
    val containerColor = if (MaterialTheme.colors.isLight) {
        Color.White
    } else {
        colors.libraryControlSurface
    }
    val containerBorderColor = if (MaterialTheme.colors.isLight) {
        Color(0xFFE1E7E3)
    } else {
        colors.libraryControlBorder
    }
    val tabGap = 4.dp
    val containerInset = 2.dp
    val selectedTabIndex = selectedIndex.coerceIn(0, (labels.lastIndex).coerceAtLeast(0))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val tabWidth = (maxWidth - containerInset * 2 - tabGap * (labels.size - 1)) / labels.size
            val targetOffset = containerInset + (tabWidth + tabGap) * selectedTabIndex
            val indicatorOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 220),
                label = "quran-tab-indicator",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(containerColor, RoundedCornerShape(14.dp))
                    .border(1.dp, containerBorderColor, RoundedCornerShape(14.dp)),
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset, y = containerInset)
                    .width(tabWidth)
                    .height(32.dp)
                    .background(selectedContainerColor, RoundedCornerShape(12.dp)),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(tabGap),
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = selectedTabIndex == index
                    TextButton(
                        onClick = { onSelected(index) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = if (selected) {
                                MaterialTheme.colors.primary
                            } else {
                                colors.textSecondary
                            },
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformQuranSurahStatusButton(
    status: SurahTodoStatus?,
    statusTitle: String,
    notStartedLabel: String,
    learningLabel: String,
    learnedLabel: String,
    cancelLabel: String,
    onStatusSelected: (SurahTodoStatus?) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.extendedColors
    val (label, containerColor, contentColor) = when (status) {
        SurahTodoStatus.LEARNING -> Triple(
            learningLabel,
            colors.surahStatusLearningSurface,
            colors.info,
        )

        SurahTodoStatus.LEARNED -> Triple(
            learnedLabel,
            colors.surahStatusLearnedSurface,
            colors.success,
        )

        null -> Triple(notStartedLabel, colors.surahStatusNotStartedSurface, colors.textSecondary)
    }
    var isStatusSheetVisible by remember { mutableStateOf(false) }
    val statusSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TextButton(
        onClick = { isStatusSheetVisible = true },
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp),
        )
    }

    if (isStatusSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isStatusSheetVisible = false },
            sheetState = statusSheetState,
            containerColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
        ) {
            Text(
                text = statusTitle,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            listOf(
                null to notStartedLabel,
                SurahTodoStatus.LEARNING to learningLabel,
                SurahTodoStatus.LEARNED to learnedLabel,
            ).forEach { (nextStatus, nextLabel) ->
                TextButton(
                    onClick = {
                        isStatusSheetVisible = false
                        onStatusSelected(nextStatus)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colors.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = nextLabel,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            TextButton(
                onClick = { isStatusSheetVisible = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.textSecondary,
                ),
            ) {
                Text(
                    text = cancelLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
