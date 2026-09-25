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
}
