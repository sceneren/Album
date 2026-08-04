package com.github.sceneren.album.api.internal.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PickedMediaEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AlbumDatabase : RoomDatabase() {
    abstract fun pickedMediaDao(): PickedMediaDao
}
