package com.github.toober77.fastpaste

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PasteDao {
    @Query("SELECT * FROM paste_items ORDER BY createdAt DESC")
    fun getAllItemsSortedByTime(): Flow<List<PasteItem>>

    @Query("SELECT * FROM paste_items ORDER BY content ASC")
    fun getAllItemsSortedByAlpha(): Flow<List<PasteItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PasteItem)

    @Delete
    suspend fun deleteItems(items: List<PasteItem>)

    @Query("DELETE FROM paste_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
