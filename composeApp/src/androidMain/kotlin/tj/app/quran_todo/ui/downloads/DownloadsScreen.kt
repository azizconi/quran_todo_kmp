package tj.app.quran_todo.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.utils.getQuranFontFamily
import tj.app.quran_todo.ui.components.EmptyState
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.QhIconButton
import tj.app.quran_todo.ui.components.QhLargeTopBar
import tj.app.quran_todo.ui.components.QhSearchField
import tj.app.quran_todo.ui.components.QhSectionHeader
import tj.app.quran_todo.ui.components.ToastKind
import tj.app.quran_todo.ui.components.rememberQhTopBarState
import tj.app.quran_todo.ui.i18n.DownloadsCopy
import tj.app.quran_todo.ui.i18n.rememberDownloadsCopy
import tj.app.quran_todo.ui.i18n.TranslationCopy
import tj.app.quran_todo.ui.i18n.rememberPagesCopy
import tj.app.quran_todo.ui.i18n.rememberTranslationCopy
import tj.app.quran_todo.ui.theme.QhSpacing
import tj.app.quran_todo.ui.theme.QhTheme

/** What a confirmation dialog of the screen is about. */
private sealed interface Ask {
    data class DeleteRow(val row: RowUi) : Ask

    data object DeleteAll : Ask

    data class DeleteTranslation(val row: TranslationRowUi) : Ask

    data object ClearCache : Ask

    data object DeleteRecordings : Ask

    data object CancelAll : Ask
}

/**
 * "Downloads and storage" (docs/redesign/PRODUCT_PLAN.md, 2.1): the options (the reciter of the sound settings, "Wi-Fi only", "With
 * translation"), the queue of what is being downloaded, the storage (downloads, listening cache, free space), then "My plan", the 30
 * juz and the 114 surahs with a search. Every row says its size before downloading and what is on the phone afterwards.
 *
 * The downloading itself is done by a WorkManager work ([DownloadWorker]) that goes on in the background; this screen only asks and
 * shows the state.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<DownloadsViewModel>()
    val language = LocalAppLanguage.current
    val copy = rememberDownloadsCopy(language)
    val latestCopy by rememberUpdatedState(copy)
    val toaster = LocalToaster.current
    val context = LocalContext.current

    LaunchedEffect(language) { viewModel.setLanguage(language) }
    // The listening cache grows while the person listens: read its size again each time the screen opens.
    LaunchedEffect(viewModel) { viewModel.refreshCache() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = latestCopy
            when (event) {
                DownloadsEvent.NotEnoughSpace -> toaster.show(text.notEnoughSpace, ToastKind.Error)
                DownloadsEvent.Deleted -> toaster.show(text.deletedToast, ToastKind.Success)
                DownloadsEvent.CacheCleared -> toaster.show(text.cacheClearedToast, ToastKind.Success)
                DownloadsEvent.RecordingsDeleted -> toaster.show(text.recordingsDeletedToast, ToastKind.Success)
            }
        }
    }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf<Ask?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    // The notification of the download is the person's way to see and cancel it: the permission is asked once, at the first download.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val actions = remember(viewModel) {
        RowActions(
            download = { row ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    viewModel.takeNotificationPermissionTurn()
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.download(row.id)
            },
            pause = viewModel::pauseAll,
            resume = viewModel::resume,
            cancel = { row -> viewModel.cancel(row.id) },
            delete = { row -> ask = Ask.DeleteRow(row) },
        )
    }

    val translationText = rememberTranslationCopy(language)
    val translationActions = remember(viewModel) {
        TranslationActions(
            download = { row ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    viewModel.takeNotificationPermissionTurn()
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.downloadTranslation(row.key)
            },
            pause = viewModel::pauseAll,
            resume = viewModel::resume,
            cancel = { row -> viewModel.cancel(DownloadRequest.translationId(row.key)) },
            delete = { row -> ask = Ask.DeleteTranslation(row) },
        )
    }

    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val topBarState = rememberQhTopBarState(listState)
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val estimatedBarPx = statusBarTop + with(density) {
        (56.dp + QhTheme.type.display.lineHeight.toDp() + QhSpacing.x2).roundToPx()
    }
    var expandedBarPx by remember { mutableIntStateOf(estimatedBarPx) }
    val listTopPadding = with(density) { expandedBarPx.toDp() } + QhSpacing.x2
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(QhTheme.colors.bg),
    ) {
        DownloadsList(
            ui = ui,
            copy = copy,
            query = query,
            onQueryChange = { query = it },
            actions = actions,
            translationActions = translationActions,
            translationText = translationText,
            viewModel = viewModel,
            onAsk = { ask = it },
            listState = listState,
            contentPadding = PaddingValues(top = listTopPadding, bottom = navigationBottom + QhSpacing.x8),
        )
        QhLargeTopBar(
            title = copy.title,
            state = topBarState,
            modifier = Modifier.onSizeChanged { size ->
                // Only an unscrolled bar is fully expanded.
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) expandedBarPx = size.height
            },
            navigation = {
                QhIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = copy.back,
                    onClick = onBack,
                    filled = false,
                )
            },
        )
    }

    when (val current = ask) {
        null -> Unit
        is Ask.DeleteRow -> ConfirmDialog(
            title = copy.deleteTitle,
            message = copy.deleteBodyLabel(rowTitle(current.row, copy), (current.row.state.bytes())),
            confirm = copy.deleteConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.delete(current.row.id)
            },
            onDismiss = { ask = null },
        )
        is Ask.DeleteTranslation -> ConfirmDialog(
            title = copy.deleteTitle,
            message = translationText.deleteBody(current.row.name),
            confirm = copy.deleteConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.deleteTranslation(current.row.key)
            },
            onDismiss = { ask = null },
        )
        Ask.DeleteAll -> ConfirmDialog(
            title = copy.deleteAllTitle,
            message = copy.deleteAllBodyLabel(ui.storage.downloadsBytes),
            confirm = copy.deleteAllConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.deleteAll()
            },
            onDismiss = { ask = null },
        )
        Ask.DeleteRecordings -> ConfirmDialog(
            title = copy.recordingsDeleteTitle,
            message = copy.recordingsDeleteBodyLabel(ui.storage.recordingsBytes),
            confirm = copy.recordingsDeleteConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.deleteRecordings()
            },
            onDismiss = { ask = null },
        )
        Ask.ClearCache -> ConfirmDialog(
            title = copy.cacheClearTitle,
            message = copy.cacheClearBody,
            confirm = copy.cacheClearConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.clearCache()
            },
            onDismiss = { ask = null },
        )
        Ask.CancelAll -> ConfirmDialog(
            title = copy.cancelAllTitle,
            message = copy.cancelAllBody,
            confirm = copy.cancelAllConfirm,
            cancel = copy.cancel,
            onConfirm = {
                ask = null
                viewModel.cancelAll()
            },
            onDismiss = { ask = null },
        )
    }
}

private fun RowState.bytes(): Long = when (this) {
    is RowState.Complete -> bytes
    is RowState.Partial -> bytes
    RowState.NotDownloaded -> 0L
}

@Composable
private fun DownloadsList(
    ui: DownloadsUi,
    copy: DownloadsCopy,
    query: String,
    onQueryChange: (String) -> Unit,
    actions: RowActions,
    translationActions: TranslationActions,
    translationText: TranslationCopy,
    viewModel: DownloadsViewModel,
    onAsk: (Ask) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    val language = LocalAppLanguage.current
    val pagesText = rememberPagesCopy(language)
    val type = QhTheme.type
    val arabicStyle = type.quranInline(getQuranFontFamily())
    val shownSurahs = remember(ui.surahs, ui.searchIndex, query) {
        val index = ui.searchIndex
        if (query.isBlank() || index == null) {
            ui.surahs
        } else {
            val byNumber = ui.surahs.associateBy { it.number }
            index.search(query).mapNotNull { byNumber[it] }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "options") {
            OptionsCard(
                ui = ui,
                copy = copy,
                language = language,
                onWifiOnly = viewModel::setWifiOnly,
                onWithTranslation = viewModel::setWithTranslation,
                modifier = Modifier.padding(horizontal = QhSpacing.screen),
            )
        }
        ui.queue?.let { queue ->
            item(key = "queue") {
                QueueCard(
                    queue = queue,
                    wifiOnly = ui.wifiOnly,
                    copy = copy,
                    onPause = viewModel::pauseAll,
                    onResume = viewModel::resume,
                    onCancelAll = { onAsk(Ask.CancelAll) },
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x4),
                )
            }
        }
        item(key = "storage") {
            StorageCard(
                storage = ui.storage,
                copy = copy,
                onDeleteAll = { onAsk(Ask.DeleteAll) },
                onClearCache = { onAsk(Ask.ClearCache) },
                onDeleteRecordings = { onAsk(Ask.DeleteRecordings) },
                modifier = Modifier.padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.x4),
            )
        }

        // "Translations" (task F4a): the chosen edition, whole in one request; the others that are on the phone can be deleted here.
        item(key = "translations-header") { SectionHeader(translationText.downloadsSection) }
        item(key = "translations-hint") {
            Text(
                text = translationText.downloadsHint,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = QhSpacing.screen, end = QhSpacing.screen, bottom = QhSpacing.x2),
            )
        }
        items(ui.translations, key = { "translation-" + it.key }) { row ->
            TranslationDownloadRow(row, copy, translationText, translationActions)
        }

        if (!ui.loaded) {
            item(key = "loading") { Spacer(Modifier.height(QhSpacing.x8)) }
            return@LazyColumn
        }

        ui.planAll?.let { planAll ->
            item(key = "plan-header") { SectionHeader(copy.sectionPlan) }
            item(key = planAll.id) { DownloadRow(planAll, copy, language, arabicStyle, actions) }
            items(ui.planRows, key = { "plan-" + it.id }) { row ->
                DownloadRow(row, copy, language, arabicStyle, actions)
            }
        }

        item(key = "juz-header") { SectionHeader(copy.sectionJuz) }
        items(ui.juz, key = { it.id }) { row -> DownloadRow(row, copy, language, arabicStyle, actions) }

        // "Mushaf pages" (task F3a): the layout and the fonts of the mode "Pages", all of them or the pages of one juz.
        item(key = "pages-header") { SectionHeader(pagesText.downloadsSection) }
        item(key = "pages-hint") {
            Text(
                text = pagesText.downloadsHint,
                style = QhTheme.type.callout,
                color = QhTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = QhSpacing.screen, end = QhSpacing.screen, bottom = QhSpacing.x2),
            )
        }
        ui.pagesAll?.let { row -> item(key = row.id) { DownloadRow(row, copy, language, arabicStyle, actions) } }
        items(ui.pagesJuz, key = { it.id }) { row -> DownloadRow(row, copy, language, arabicStyle, actions) }

        item(key = "surahs-header") { SectionHeader(copy.sectionSurahs) }
        item(key = "search") {
            QhSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = copy.searchPlaceholder,
                clearContentDescription = copy.searchClear,
                modifier = Modifier.padding(horizontal = QhSpacing.screen, vertical = QhSpacing.x2),
            )
        }
        if (shownSurahs.isEmpty()) {
            item(key = "search-empty") { EmptyState(title = copy.searchEmpty) }
        } else {
            items(shownSurahs, key = { it.id }) { row -> DownloadRow(row, copy, language, arabicStyle, actions) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    QhSectionHeader(
        title = title,
        modifier = Modifier.padding(start = QhSpacing.screen, end = QhSpacing.screen, top = QhSpacing.section, bottom = QhSpacing.x2),
    )
}
