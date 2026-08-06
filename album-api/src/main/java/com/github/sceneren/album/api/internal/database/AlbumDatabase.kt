package com.github.sceneren.album.api.internal.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PickedMediaEntity::class],
    version = 1,
    exportSchema = true,
)
/** 负责 `AlbumDatabase` 相关的数据与行为。 */
internal abstract class AlbumDatabase : RoomDatabase() {
    /** 执行 `pickedMediaDao` 方法定义的处理。 */
    abstract fun pickedMediaDao(): PickedMediaDao
}
