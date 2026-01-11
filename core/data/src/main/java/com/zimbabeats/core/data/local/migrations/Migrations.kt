package com.zimbabeats.core.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 7 to 8: Added playlist sharing fields
 * Version 8 added: shareCode, sharedAt, isImported, importedFrom, importedAt
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE playlists ADD COLUMN shareCode TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN sharedAt INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN isImported INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE playlists ADD COLUMN importedFrom TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN importedAt INTEGER DEFAULT NULL")
    }
}
