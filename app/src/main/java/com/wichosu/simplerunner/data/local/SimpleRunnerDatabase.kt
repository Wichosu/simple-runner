package com.wichosu.simplerunner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RunEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SimpleRunnerDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        private const val NAME = "simple-runner.db"

        @Volatile
        private var instance: SimpleRunnerDatabase? = null

        fun get(context: Context): SimpleRunnerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SimpleRunnerDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
