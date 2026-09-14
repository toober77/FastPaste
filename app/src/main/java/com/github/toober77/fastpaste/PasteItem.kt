package com.github.toober77.fastpaste

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paste_items")
data class PasteItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
