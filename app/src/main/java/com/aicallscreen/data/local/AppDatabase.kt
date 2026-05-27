package com.aicallscreen.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CallLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callLogDao(): CallLogDao

    companion object {
        const val DATABASE_NAME = "ai_call_screening.db"
    }
}
