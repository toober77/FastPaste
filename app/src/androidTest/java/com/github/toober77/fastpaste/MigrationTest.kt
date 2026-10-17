package com.github.toober77.fastpaste

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side migration check with Room's validator (run with connectedAndroidTest). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsData() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO paste_items (id, content, label, createdAt) VALUES (1, 'hello', 'work', 111)")
        }
        // Validates the migrated schema against 2.json.
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*Migrations.ALL).build()
        try {
            val item = runBlocking { db.pasteDao().getById(1) }!!
            assertEquals(
                PasteItem(1, "hello", "work", createdAt = 111, pinned = false, favorite = false, updatedAt = 111),
                item
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate2To3_initialisesPositionsNewestFirst() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO paste_items (id, content, label, createdAt, pinned, favorite, updatedAt) VALUES (1, 'old', '', 100, 0, 0, 100)")
            db.execSQL("INSERT INTO paste_items (id, content, label, createdAt, pinned, favorite, updatedAt) VALUES (2, 'new', '', 200, 1, 0, 200)")
        }
        // Validates the migrated schema against 3.json.
        helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT id, position FROM paste_items ORDER BY id").use { c ->
                val rows = buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) }
                assertEquals(listOf(1 to 1, 2 to 0), rows)
            }
        }
    }
}
