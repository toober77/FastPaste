package com.github.toober77.fastpaste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder {
    TIME, ALPHA
}

sealed interface PasteEvent {
    /** Items were deleted; pass [items] back to [PasteViewModel.restore] to undo. */
    data class Deleted(val items: List<PasteItem>) : PasteEvent
}

class PasteViewModel(
    private val dao: PasteDao,
    private val onDataChanged: () -> Unit = {}
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.TIME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    private val _events = Channel<PasteEvent>(Channel.BUFFERED)
    val events: Flow<PasteEvent> = _events.receiveAsFlow()

    /** Unfiltered items in the current sort order; null until the first DB emission. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allItems: StateFlow<List<PasteItem>?> = _sortOrder
        .flatMapLatest { order ->
            if (order == SortOrder.TIME) dao.getAllItemsSortedByTime()
            else dao.getAllItemsSortedByAlpha()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Items after search filtering; null while loading (so the empty state doesn't flash).
     * Filtering is in-memory on an already-loaded list, so no debounce/extra query is needed.
     */
    val items: StateFlow<List<PasteItem>?> = combine(allItems, _searchQuery) { list, query ->
        list?.let { PasteLogic.filter(it, query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun isDuplicate(content: String): Boolean =
        PasteLogic.isDuplicate(allItems.value.orEmpty(), content)

    fun addItem(content: String, label: String = "") {
        val c = PasteLogic.normalizeContent(content)
        if (c.isEmpty()) return
        val l = PasteLogic.normalizeLabel(label)
        viewModelScope.launch {
            dao.insert(PasteItem(content = c, label = l))
            onDataChanged()
        }
    }

    fun toggleSelection(id: Int) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun select(id: Int) {
        _selectedIds.update { it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Deletes the current selection and emits [PasteEvent.Deleted] so the UI can offer undo. */
    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            val removed = dao.getByIds(ids)
            dao.deleteByIds(ids)
            onDataChanged()
            if (removed.isNotEmpty()) _events.send(PasteEvent.Deleted(removed))
        }
    }

    /** Re-inserts previously deleted items with their original ids and timestamps. */
    fun restore(items: List<PasteItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            dao.insertAll(items)
            onDataChanged()
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                PasteViewModel(AppDatabase.getDatabase(app).pasteDao()) {
                    PasteWidgetUpdater.requestUpdate(app)
                }
            }
        }
    }
}
