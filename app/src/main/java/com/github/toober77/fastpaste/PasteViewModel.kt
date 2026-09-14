package com.github.toober77.fastpaste

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    TIME, ALPHA
}

class PasteViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).pasteDao()

    private val _sortOrder = MutableStateFlow(SortOrder.TIME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val items: StateFlow<List<PasteItem>> = combine(
        _sortOrder.flatMapLatest { order ->
            if (order == SortOrder.TIME) dao.getAllItemsSortedByTime()
            else dao.getAllItemsSortedByAlpha()
        },
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) items
        else items.filter { it.content.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addItem(content: String, label: String = "") {
        viewModelScope.launch {
            dao.insert(PasteItem(content = content, label = label))
        }
    }

    fun deleteItems(ids: List<Int>) {
        viewModelScope.launch {
            dao.deleteByIds(ids)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
