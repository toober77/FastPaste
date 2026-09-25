package com.github.toober77.fastpaste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class SortOrder {
    TIME, ALPHA
}

enum class BackupError { READ, WRITE, INVALID, UNSUPPORTED_VERSION, TOO_LARGE }

sealed interface PasteEvent {
    /** Items were deleted; pass [items] back to [PasteViewModel.restore] to undo. */
    data class Deleted(val items: List<PasteItem>) : PasteEvent
    data class Exported(val count: Int) : PasteEvent
    data class Imported(val added: Int, val skipped: Int) : PasteEvent
    data class BackupFailed(val error: BackupError) : PasteEvent
}

/** Pinned / favorite state of the current selection, used for the bulk actions. */
data class SelectionFlags(val allPinned: Boolean = false, val allFavorite: Boolean = false)

class PasteViewModel(
    private val dao: PasteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onDataChanged: () -> Unit = {}
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SortOrder.TIME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    private val _events = Channel<PasteEvent>(Channel.BUFFERED)
    val events: Flow<PasteEvent> = _events.receiveAsFlow()

    /** Unfiltered items in the current sort order (pinned first); null until the first DB emission. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allItems: StateFlow<List<PasteItem>?> = _sortOrder
        .flatMapLatest { order ->
            if (order == SortOrder.TIME) dao.getAllItemsSortedByTime()
            else dao.getAllItemsSortedByAlpha()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Items after favorites + search filtering; null while loading (so the empty state doesn't
     * flash). Filtering is in-memory on an already-loaded list, so no debounce/extra query.
     */
    val items: StateFlow<List<PasteItem>?> =
        combine(allItems, _searchQuery, _favoritesOnly) { list, query, favOnly ->
            list?.let { PasteLogic.filter(it, query, favOnly) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectionFlags: StateFlow<SelectionFlags> =
        combine(allItems, _selectedIds) { list, ids ->
            val selected = list.orEmpty().filter { it.id in ids }
            SelectionFlags(
                allPinned = selected.isNotEmpty() && selected.all { it.pinned },
                allFavorite = selected.isNotEmpty() && selected.all { it.favorite }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SelectionFlags())

    fun findItem(id: Int): PasteItem? = allItems.value?.firstOrNull { it.id == id }

    /** Duplicate hint for the editor; [excludeId] is the snippet being edited. */
    fun isDuplicate(content: String, excludeId: Int? = null): Boolean =
        PasteLogic.isDuplicate(allItems.value.orEmpty().filter { it.id != excludeId }, content)

    fun addItem(content: String, label: String = "") {
        val c = PasteLogic.normalizeContent(content)
        if (c.isEmpty()) return
        val l = PasteLogic.normalizeLabel(label)
        viewModelScope.launch {
            val now = clock()
            dao.insert(PasteItem(content = c, label = l, createdAt = now, updatedAt = now))
            onDataChanged()
        }
    }

    /** Edits content/label, keeping id, createdAt, pinned and favorite. */
    fun updateItem(id: Int, content: String, label: String) {
        val c = PasteLogic.normalizeContent(content)
        if (c.isEmpty()) return
        val l = PasteLogic.normalizeLabel(label)
        viewModelScope.launch {
            val current = dao.getById(id) ?: return@launch
            if (current.content == c && current.label == l) return@launch
            dao.update(current.copy(content = c, label = l, updatedAt = clock()))
            onDataChanged()
        }
    }

    fun togglePinned(item: PasteItem) = setPinned(listOf(item.id), !item.pinned)

    fun toggleFavorite(item: PasteItem) = setFavorite(listOf(item.id), !item.favorite)

    /** Bulk pin: pins all selected unless they are all pinned already, then unpins. */
    fun togglePinnedForSelection() {
        val ids = _selectedIds.value.toList()
        if (ids.isNotEmpty()) setPinned(ids, !selectionFlags.value.allPinned)
    }

    fun toggleFavoriteForSelection() {
        val ids = _selectedIds.value.toList()
        if (ids.isNotEmpty()) setFavorite(ids, !selectionFlags.value.allFavorite)
    }

    private fun setPinned(ids: List<Int>, pinned: Boolean) {
        viewModelScope.launch {
            dao.setPinned(ids, pinned)
            onDataChanged()
        }
    }

    private fun setFavorite(ids: List<Int>, favorite: Boolean) {
        viewModelScope.launch {
            dao.setFavorite(ids, favorite)
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
        delete(ids)
    }

    fun deleteItem(id: Int) {
        _selectedIds.update { it - id }
        delete(listOf(id))
    }

    private fun delete(ids: List<Int>) {
        viewModelScope.launch {
            val removed = dao.getByIds(ids)
            dao.deleteByIds(ids)
            onDataChanged()
            if (removed.isNotEmpty()) _events.send(PasteEvent.Deleted(removed))
        }
    }

    /** Re-inserts previously deleted items with their original ids, flags and timestamps. */
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

    fun setFavoritesOnly(enabled: Boolean) {
        _favoritesOnly.value = enabled
    }

    /** Writes every snippet as JSON to the stream from [openOutput] (e.g. a SAF document). */
    fun exportBackup(openOutput: () -> OutputStream?) {
        viewModelScope.launch {
            val event = try {
                val items = dao.getAllOnce()
                val text = Backup.encode(items, clock())
                withContext(ioDispatcher) {
                    val out = openOutput() ?: throw IOException("No output stream")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                PasteEvent.Exported(items.size)
            } catch (e: IOException) {
                PasteEvent.BackupFailed(BackupError.WRITE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SecurityException, provider bugs, etc.: report instead of crashing.
                PasteEvent.BackupFailed(BackupError.WRITE)
            }
            _events.send(event)
        }
    }

    /** Reads a JSON backup and merges it, skipping exact (content, label) duplicates. */
    fun importBackup(openInput: () -> InputStream?) {
        viewModelScope.launch {
            val event = try {
                val file = withContext(ioDispatcher) {
                    val input = openInput() ?: throw IOException("No input stream")
                    Backup.decode(input.use { readCapped(it, Backup.MAX_BYTES) })
                }
                val merge = Backup.merge(dao.getAllOnce(), file.items, clock())
                if (merge.toInsert.isNotEmpty()) {
                    dao.insertAll(merge.toInsert)
                    onDataChanged()
                }
                PasteEvent.Imported(merge.toInsert.size, merge.skipped)
            } catch (e: BackupFormatException) {
                PasteEvent.BackupFailed(
                    when (e.reason) {
                        BackupFormatException.Reason.INVALID -> BackupError.INVALID
                        BackupFormatException.Reason.UNSUPPORTED_VERSION -> BackupError.UNSUPPORTED_VERSION
                        BackupFormatException.Reason.TOO_LARGE -> BackupError.TOO_LARGE
                    }
                )
            } catch (e: IOException) {
                PasteEvent.BackupFailed(BackupError.READ)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PasteEvent.BackupFailed(BackupError.READ)
            }
            _events.send(event)
        }
    }

    private fun readCapped(input: InputStream, maxBytes: Int): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > maxBytes) {
                throw BackupFormatException(BackupFormatException.Reason.TOO_LARGE)
            }
            buffer.write(chunk, 0, n)
        }
        return buffer.toString(Charsets.UTF_8.name())
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
