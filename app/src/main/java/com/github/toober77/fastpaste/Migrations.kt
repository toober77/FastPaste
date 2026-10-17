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

    /**
     * v2 -> v3: add `position` for the custom drag order. Initialised to the current newest-first
     * order (rank by createdAt DESC, id DESC as tie-break) so switching to 「自訂」 looks familiar.
     */
    val MIGRATION_2_3_SQL = listOf(
        "ALTER TABLE `paste_items` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0",
        "UPDATE `paste_items` SET `position` = (SELECT COUNT(*) FROM `paste_items` AS p " +
            "WHERE p.`createdAt` > `paste_items`.`createdAt` OR " +
            "(p.`createdAt` = `paste_items`.`createdAt` AND p.`id` > `paste_items`.`id`))"
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
