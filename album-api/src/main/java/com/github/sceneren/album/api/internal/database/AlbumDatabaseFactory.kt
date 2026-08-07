package com.github.sceneren.album.api.internal.database

import android.content.Context
import androidx.room.Room

/** 负责创建 `AlbumDatabaseFactory` 管理的实例。 */
internal object AlbumDatabaseFactory {
    @Volatile
    /** 表示 `instance` 对应的数据。 */
    private var instance: AlbumDatabase? = null

    /** 获取 `get` 所需的数据。 */
    fun get(context: Context): AlbumDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            AlbumDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(AlbumDatabase.MIGRATION_1_2)
            .build()
            .also { database -> instance = database }
    }

    /** 表示 `DATABASE_NAME` 对应的数据。 */
    private const val DATABASE_NAME = "album_api.db"
}
