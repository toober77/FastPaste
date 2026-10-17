package com.github.toober77.fastpaste

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory PasteDao mirroring the real queries' ordering, for fast JVM tests. */
private val PINNED_FIRST = compareByDescending<PasteItem> { it.pinned }

class FakePasteDao(initial: List<PasteItem> = emptyList()) : PasteDao {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    var timeQueries = 0
    var alphaQueries = 0

    override fun getAllItemsSortedByTime(): Flow<List<PasteItem>> {
        timeQueries++
        return state.map { list -> list.sortedWith(PINNED_FIRST.thenByDescending { it.createdAt }) }
    }

    override fun getAllItemsSortedByAlpha(): Flow<List<PasteItem>> {
        alphaQueries++
        return state.map { list -> list.sortedWith(PINNED_FIRST.thenBy(String.CASE_INSENSITIVE_ORDER) { it.content }) }
    }

    var positionQueries = 0

    override fun getAllItemsSortedByPosition(): Flow<List<PasteItem>> {
        positionQueries++
        return state.map { list -> PasteLogic.sort(list, SortOrder.CUSTOM) }
    }

    override suspend fun minPosition(): Int = state.value.minOfOrNull { it.position } ?: 0

    override suspend fun updatePositions(positions: List<PositionUpdate>) {
        val byId = positions.associate { it.id to it.position }
        state.value = state.value.map { item -> byId[item.id]?.let { item.copy(position = it) } ?: item }
    }

    override fun getRecentItems(limit: Int): Flow<List<PasteItem>> =
        state.map { list -> list.sortedWith(PINNED_FIRST.thenByDescending { it.createdAt }).take(limit) }

    override suspend fun getRecentItemsOnce(limit: Int): List<PasteItem> =
        state.value.sortedWith(PINNED_FIRST.thenByDescending { it.createdAt }).take(limit)

    override suspend fun getAllOnce(): List<PasteItem> = state.value.sortedBy { it.createdAt }

    override suspend fun update(item: PasteItem) {
        state.value = state.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun setPinned(ids: List<Int>, pinned: Boolean) {
        state.value = state.value.map { if (it.id in ids) it.copy(pinned = pinned) else it }
    }

    override suspend fun setFavorite(ids: List<Int>, favorite: Boolean) {
        state.value = state.value.map { if (it.id in ids) it.copy(favorite = favorite) else it }
    }

    override suspend fun getById(id: Int): PasteItem? = state.value.firstOrNull { it.id == id }

    override suspend fun getByIds(ids: List<Int>): List<PasteItem> = state.value.filter { it.id in ids }

    override suspend fun insert(item: PasteItem) = insertAll(listOf(item))

    override suspend fun insertAll(items: List<PasteItem>) {
        val withIds = items.map { if (it.id == 0) it.copy(id = nextId++) else it }
        val ids = withIds.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + withIds
    }

    override suspend fun deleteByIds(ids: List<Int>) {
        state.value = state.value.filterNot { it.id in ids }
    }
}
