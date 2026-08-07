package com.github.sceneren.album.api.internal.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PickedMediaEntity::class],
    version = 2,
    exportSchema = true,
)
/** 负责 `AlbumDatabase` 相关的数据与行为。 */
internal abstract class AlbumDatabase : RoomDatabase() {
    /** 执行 `pickedMediaDao` 方法定义的处理。 */
    abstract fun pickedMediaDao(): PickedMediaDao

    companion object {
        /** Preserves existing selections while adding special-format metadata. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE picked_media " +
                        "ADD COLUMN specialFormat TEXT NOT NULL DEFAULT 'NONE'",
                )
            }
        }
    }
}
