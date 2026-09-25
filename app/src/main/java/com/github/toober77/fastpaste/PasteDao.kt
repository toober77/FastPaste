package com.github.toober77.fastpaste

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PasteDao {
    @Query("SELECT * FROM paste_items ORDER BY createdAt DESC")
    fun getAllItemsSortedByTime(): Flow<List<PasteItem>>

    // NOCASE so "apple" and "Apple" sort together instead of all upper-case first.
    @Query("SELECT * FROM paste_items ORDER BY content COLLATE NOCASE ASC")
    fun getAllItemsSortedByAlpha(): Flow<List<PasteItem>>

    @Query("SELECT * FROM paste_items ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentItems(limit: Int): Flow<List<PasteItem>>

    @Query("SELECT * FROM paste_items ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentItemsOnce(limit: Int): List<PasteItem>

    @Query("SELECT * FROM paste_items WHERE id = :id")
    suspend fun getById(id: Int): PasteItem?

    @Query("SELECT * FROM paste_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<PasteItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PasteItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PasteItem>)

    @Query("DELETE FROM paste_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
