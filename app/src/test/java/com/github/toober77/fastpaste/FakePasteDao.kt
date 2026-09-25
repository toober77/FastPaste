package com.github.toober77.fastpaste

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory PasteDao mirroring the real queries' ordering, for fast JVM tests. */
class FakePasteDao(initial: List<PasteItem> = emptyList()) : PasteDao {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    var timeQueries = 0
    var alphaQueries = 0

    override fun getAllItemsSortedByTime(): Flow<List<PasteItem>> {
        timeQueries++
        return state.map { list -> list.sortedByDescending { it.createdAt } }
    }

    override fun getAllItemsSortedByAlpha(): Flow<List<PasteItem>> {
        alphaQueries++
        return state.map { list -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.content }) }
    }

    override fun getRecentItems(limit: Int): Flow<List<PasteItem>> =
        state.map { list -> list.sortedByDescending { it.createdAt }.take(limit) }

    override suspend fun getRecentItemsOnce(limit: Int): List<PasteItem> =
        state.value.sortedByDescending { it.createdAt }.take(limit)

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
