package com.ishireader.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PositionEntity::class], version = 1, exportSchema = false)
abstract class IshiReaderDatabase : RoomDatabase() {

    abstract fun positionDao(): PositionDao

    companion object {
        @Volatile
        private var instance: IshiReaderDatabase? = null

        fun getInstance(context: Context): IshiReaderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    IshiReaderDatabase::class.java,
                    "ishi-reader.db"
                ).build().also { instance = it }
            }
    }
}
