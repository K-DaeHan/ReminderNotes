package com.android.remindernotes.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_reminder")
class RoomReminder(
    @PrimaryKey var no: Long,
    @ColumnInfo var content: String,
    @ColumnInfo var memo: String,
    @ColumnInfo var dDay: String,
    @ColumnInfo var notificationTime: String,
    @ColumnInfo var remainingDays: String,
    @ColumnInfo var targetAlarmTime: Long,
    @ColumnInfo var isAutoDelete: Int
) {
    /*@PrimaryKey(autoGenerate = true)
    var no: Long? = null*/
}
