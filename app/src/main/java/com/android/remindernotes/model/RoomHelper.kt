package com.android.remindernotes.model

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RoomReminder::class], version = 1, exportSchema = false)
abstract class RoomHelper : RoomDatabase() {
    abstract fun roomDao(): RoomDao
}
