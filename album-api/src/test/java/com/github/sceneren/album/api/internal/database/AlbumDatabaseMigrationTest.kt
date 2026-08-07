package com.github.sceneren.album.api.internal.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies that the version 1 database upgrades without losing persisted selections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumDatabaseMigrationTest {
    @Test
    fun migration1To2AddsDefaultSpecialFormatAndKeepsRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(CREATE_VERSION_1_TABLE)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        try {
            val database = helper.writableDatabase
            database.execSQL(INSERT_VERSION_1_ROW)

            AlbumDatabase.MIGRATION_1_2.migrate(database)

            database.query(
                "SELECT uri, specialFormat FROM picked_media",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("content://picked/1", cursor.getString(0))
                assertEquals("NONE", cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private companion object {
        const val DATABASE_NAME = "album-migration-test.db"
        const val CREATE_VERSION_1_TABLE =
            "CREATE TABLE picked_media (" +
                "uri TEXT NOT NULL PRIMARY KEY, " +
                "mediaType TEXT NOT NULL, " +
                "displayName TEXT, mimeType TEXT, sizeBytes INTEGER, " +
                "width INTEGER, height INTEGER, durationMillis INTEGER, " +
                "selectedAtEpochMillis INTEGER NOT NULL, " +
                "sortOrder INTEGER NOT NULL, ownsPersistableGrant INTEGER NOT NULL)"
        const val INSERT_VERSION_1_ROW =
            "INSERT INTO picked_media " +
                "(uri, mediaType, selectedAtEpochMillis, sortOrder, ownsPersistableGrant) " +
                "VALUES ('content://picked/1', 'IMAGE', 1, 1, 0)"
    }
}
