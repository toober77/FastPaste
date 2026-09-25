package com.github.toober77.fastpaste

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PasteItem::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pasteDao(): PasteDao

    companion object {
        const val NAME = "paste_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Re-check inside the lock so two threads can't build two instances.
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                    // Explicit migrations only: no destructive fallback, user data must survive.
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
