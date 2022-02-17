package com.android.remindernotes

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.room.Room
import com.android.remindernotes.model.RoomDao
import com.android.remindernotes.model.RoomHelper

class AlarmRegistrationService : Service() {

    private val roomHelper: RoomHelper by lazy {
        Room.databaseBuilder(this, RoomHelper::class.java, "room_reminder")
            .build()
    }

    private val roomDao: RoomDao by lazy { roomHelper.roomDao() }

    override fun onCreate() {
        super.onCreate()
        Log.d("test", "AlarmRegistrationService::onCreate")
    } // onCreate

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("test", "AlarmRegistrationService::onStartCommand")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 오레오 이상

        } else {
            // 오레오 미만

        }

        return super.onStartCommand(intent, flags, startId)
    } // onStartCommand

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    } // onBind

}
