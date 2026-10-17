package com.github.toober77.fastpaste

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PasteDao {
    // Pinned items always come first; the chosen sort applies within each group.
    @Query("SELECT * FROM paste_items ORDER BY pinned DESC, createdAt DESC")
    fun getAllItemsSortedByTime(): Flow<List<PasteItem>>

    // NOCASE so "apple" and "Apple" sort together instead of all upper-case first.
    @Query("SELECT * FROM paste_items ORDER BY pinned DESC, content COLLATE NOCASE ASC")
    fun getAllItemsSortedByAlpha(): Flow<List<PasteItem>>

    @Query("SELECT * FROM paste_items ORDER BY pinned DESC, position ASC, createdAt DESC")
    fun getAllItemsSortedByPosition(): Flow<List<PasteItem>>

    @Query("SELECT COALESCE(MIN(position), 0) FROM paste_items")
    suspend fun minPosition(): Int

    @Update(entity = PasteItem::class)
    suspend fun updatePositions(positions: List<PositionUpdate>)

    @Query("SELECT * FROM paste_items ORDER BY pinned DESC, createdAt DESC LIMIT :limit")
    fun getRecentItems(limit: Int): Flow<List<PasteItem>>

    @Query("SELECT * FROM paste_items ORDER BY pinned DESC, createdAt DESC LIMIT :limit")
    suspend fun getRecentItemsOnce(limit: Int): List<PasteItem>

    @Query("SELECT * FROM paste_items ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PasteItem>

    @Query("SELECT * FROM paste_items WHERE id = :id")
    suspend fun getById(id: Int): PasteItem?

    @Query("SELECT * FROM paste_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<PasteItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PasteItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PasteItem>)

    @Update
    suspend fun update(item: PasteItem)

    @Query("UPDATE paste_items SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Int>, pinned: Boolean)

    @Query("UPDATE paste_items SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Int>, favorite: Boolean)

    @Query("DELETE FROM paste_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
