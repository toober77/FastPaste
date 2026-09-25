package com.github.toober77.fastpaste

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /**
     * v1 -> v2: add pinned, favorite and updatedAt. Pure ADD COLUMN with defaults, so every
     * existing row survives; updatedAt is backfilled from createdAt.
     * Kept as plain SQL so it can be unit-tested on the JVM (see MigrationSqlTest).
     */
    val MIGRATION_1_2_SQL = listOf(
        "ALTER TABLE `paste_items` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `paste_items` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `paste_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "UPDATE `paste_items` SET `updatedAt` = `createdAt`"
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
