package com.github.toober77.fastpaste

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paste_items")
data class PasteItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Column defaults must match Migrations.MIGRATION_1_2 exactly (Room validates them).
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Last content/label edit; equals createdAt until the snippet is edited. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    /** Custom (drag) order within the pinned / unpinned group; lower = higher up. */
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

/** Partial entity used to persist a drag-reordered list in one @Update transaction. */
data class PositionUpdate(val id: Int, val position: Int)
