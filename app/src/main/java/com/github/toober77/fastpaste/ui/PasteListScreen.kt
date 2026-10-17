package com.github.toober77.fastpaste.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.toober77.fastpaste.Backup
import com.github.toober77.fastpaste.BackupError
import com.github.toober77.fastpaste.PasteEvent
import com.github.toober77.fastpaste.PasteViewModel
import com.github.toober77.fastpaste.R
import com.github.toober77.fastpaste.SortOrder
import com.github.toober77.fastpaste.PasteItem
import com.github.toober77.fastpaste.PasteLogic
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteListScreen(viewModel: PasteViewModel = viewModel(factory = PasteViewModel.Factory)) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val selectionFlags by viewModel.selectionFlags.collectAsStateWithLifecycle()

    // Editor: closed when false; editingId == NO_ID means "add new".
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableIntStateOf(NO_ID) }
    var showTopMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Fixed-height bar: the old LargeTopAppBar + exitUntilCollapsed only collapsed when the list
    // could scroll, so its empty expanded area ("space above the title") came and went depending
    // on list length, filters and selection mode. Pinned behaviour only tints the bar on scroll.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val selectionMode = selectedIds.isNotEmpty()

    val copiedMessage = stringResource(R.string.copied)
    val undoLabel = stringResource(R.string.undo)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup { context.applicationContext.contentResolver.openOutputStream(uri, "wt") }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup { context.applicationContext.contentResolver.openInputStream(uri) }
    }

    // Back in selection mode clears the selection instead of leaving the app.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PasteEvent.Deleted -> {
                    // Each snackbar owns its own batch, so undo always restores the right items.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.deleted_count, event.items.size),
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restore(event.items)
                        }
                    }
                }
                is PasteEvent.Exported -> launch {
                    showMessage(snackbarHostState, context.getString(R.string.export_done, event.count))
                }
                is PasteEvent.Imported -> launch {
                    showMessage(
                        snackbarHostState,
                        context.getString(R.string.import_done, event.added, event.skipped)
                    )
                }
                is PasteEvent.BackupFailed -> launch {
                    val res = when (event.error) {
                        BackupError.READ -> R.string.backup_error_read
                        BackupError.WRITE -> R.string.backup_error_write
                        BackupError.INVALID -> R.string.backup_error_invalid
                        BackupError.UNSUPPORTED_VERSION -> R.string.backup_error_version
                        BackupError.TOO_LARGE -> R.string.backup_error_too_large
                    }
                    showMessage(snackbarHostState, context.getString(res))
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel_selection)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.togglePinnedForSelection() }) {
                            Icon(
                                if (selectionFlags.allPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (selectionFlags.allPinned) R.string.unpin_selected else R.string.pin_selected
                                )
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavoriteForSelection() }) {
                            Icon(
                                if (selectionFlags.allFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(
                                    if (selectionFlags.allFavorite) R.string.unfavorite_selected
                                    else R.string.favorite_selected
                                )
                            )
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_selected)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_backup)) },
                                    leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        exportLauncher.launch(Backup.defaultFileName())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_backup)) },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        // Some file managers label .json as text or octet-stream.
                                        importLauncher.launch(
                                            arrayOf("application/json", "text/*", "application/octet-stream")
                                        )
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = {
                        editingId = NO_ID
                        editorOpen = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Docked Material search field (non-expanding so the list stays visible)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // In custom mode the automatic sorts are shown greyed out (still tappable).
                val autoSortColors = if (sortOrder == SortOrder.CUSTOM) {
                    FilterChipDefaults.filterChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                }
                FilterChip(
                    selected = sortOrder == SortOrder.TIME,
                    onClick = { viewModel.setSortOrder(SortOrder.TIME) },
                    label = { Text(stringResource(R.string.sort_by_time)) },
                    colors = autoSortColors
                )
                FilterChip(
                    selected = sortOrder == SortOrder.ALPHA,
                    onClick = { viewModel.setSortOrder(SortOrder.ALPHA) },
                    label = { Text(stringResource(R.string.sort_by_alpha)) },
                    colors = autoSortColors
                )
                FilterChip(
                    selected = sortOrder == SortOrder.CUSTOM,
                    onClick = { viewModel.setSortOrder(SortOrder.CUSTOM) },
                    label = { Text(stringResource(R.string.sort_custom)) }
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { viewModel.setFavoritesOnly(!favoritesOnly) },
                    label = { Text(stringResource(R.string.filter_favorites)) },
                    leadingIcon = {
                        Icon(
                            if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
            }

            val list = items
            when {
                list == null -> Unit // Still loading; avoid flashing the empty state.
                list.isEmpty() && searchQuery.isNotBlank() -> {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Outlined.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        title = stringResource(R.string.no_results_title),
                        subtitle = stringResource(R.string.no_results_subtitle)
                    )
                }
                list.isEmpty() && favoritesOnly -> {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Outlined.StarOutline,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        title = stringResource(R.string.no_favorites_title),
                        subtitle = stringResource(R.string.no_favorites_subtitle)
                    )
                }
                list.isEmpty() -> {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Outlined.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        title = stringResource(R.string.empty_title),
                        subtitle = stringResource(R.string.empty_subtitle)
                    )
                }
                else -> {
                    // Drag to reorder only on the full list: with a search/favorites filter the
                    // position of hidden items would be ambiguous.
                    val reorderEnabled = !selectionMode && searchQuery.isBlank() && !favoritesOnly
                    val latestList by rememberUpdatedState(list)
                    // Order shown while dragging; cleared once the DB emits the saved order.
                    var dragOrder by remember { mutableStateOf<List<PasteItem>?>(null) }
                    var isDragging by remember { mutableStateOf(false) }
                    LaunchedEffect(list) { if (!isDragging) dragOrder = null }
                    val shown = dragOrder ?: list
                    val lazyListState = rememberLazyListState()
                    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        val current = dragOrder ?: latestList
                        PasteLogic.move(current, from.key as Int, to.key as Int)?.let {
                            dragOrder = it
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                        }
                    }
                    val dragLabel = stringResource(R.string.drag_to_reorder)
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 88.dp,
                            top = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(shown, key = { it.id }) { item ->
                            ReorderableItem(reorderState, key = item.id) { dragging ->
                            PasteCard(
                                item = item,
                                isSelected = item.id in selectedIds,
                                selectionMode = selectionMode,
                                dragging = dragging,
                                dragHandle = if (!reorderEnabled) null else {
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 40.dp, height = 48.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        isDragging = true
                                                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                                                    },
                                                    onDragStopped = {
                                                        isDragging = false
                                                        val ordered = dragOrder
                                                        if (ordered != null &&
                                                            ordered.map { it.id } != latestList.map { it.id }
                                                        ) {
                                                            viewModel.applyCustomOrder(ordered.map { it.id })
                                                        } else {
                                                            dragOrder = null
                                                        }
                                                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                                    }
                                                )
                                                .semantics { contentDescription = dragLabel },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.DragIndicator,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (selectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(item.label.ifEmpty { "Paste" }, item.content)
                                        )
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        scope.launch {
                                            // Replace instead of queueing one snackbar per tap.
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(copiedMessage)
                                        }
                                    }
                                },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    viewModel.select(item.id)
                                },
                                onEdit = {
                                    editingId = item.id
                                    editorOpen = true
                                },
                                onTogglePin = { viewModel.togglePinned(item) },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onDelete = { viewModel.deleteItem(item.id) }
                            )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        val editing = if (editingId == NO_ID) null else viewModel.findItem(editingId)
        if (editingId != NO_ID && editing == null) {
            // Item vanished (e.g. deleted); wait for the list to load after process death.
            LaunchedEffect(items) { if (items != null) editorOpen = false }
        } else {
            // key: a different target gets a fresh sheet with fresh prefilled state.
            key(editingId) {
                PasteEditorSheet(
                    initial = editing,
                    onDismiss = { editorOpen = false },
                    onSave = { content, label ->
                        if (editing == null) viewModel.addItem(content, label)
                        else viewModel.updateItem(editing.id, content, label)
                        editorOpen = false
                    },
                    isDuplicate = { viewModel.isDuplicate(it, editing?.id) }
                )
            }
        }
    }
}

private const val NO_ID = -1

private suspend fun showMessage(host: SnackbarHostState, message: String) {
    host.currentSnackbarData?.dismiss()
    host.showSnackbar(message, withDismissAction = true)
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
