package com.android.remindernotes.model

import androidx.room.*

@Dao
interface RoomDao {
    @Query("select * from room_reminder")
    suspend fun getAll(): List<RoomReminder>

    @Query("delete from room_reminder")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(reminder: RoomReminder)

    @Delete
    suspend fun delete(reminder: RoomReminder)

    @Update
    suspend fun update(reminder: RoomReminder)
}
