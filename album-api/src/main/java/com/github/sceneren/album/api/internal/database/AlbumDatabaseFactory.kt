package com.github.sceneren.album.api.internal.database

import android.content.Context
import androidx.room.Room

internal object AlbumDatabaseFactory {
    @Volatile
    private var instance: AlbumDatabase? = null

    fun get(context: Context): AlbumDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            AlbumDatabase::class.java,
            DATABASE_NAME,
        ).build().also { database -> instance = database }
    }

    private const val DATABASE_NAME = "album_api.db"
}
