package com.android.remindernotes

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.android.remindernotes.activity.MainActivity
import com.android.remindernotes.model.RoomDao
import com.android.remindernotes.model.RoomHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    private lateinit var roomHelper: RoomHelper
    private lateinit var roomDao: RoomDao

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("test", "AlarmReceiver::onReceive")

        when (intent.action) {
            "android.intent.action.NOTIFY" -> {
                Log.d("test", "android.intent.action.NOTIFY")
                val id = intent.getIntExtra("id", 9999)
                val content = intent.getStringExtra("content").toString()
                showNotification(context, id, content)
            }
            "android.intent.action.BOOT_COMPLETED" -> {
                Log.d("test", "android.intent.action.BOOT_COMPLETED")
                reRegistration(context)
            }
        } // when
    } // onReceive

    private fun reRegistration(context: Context) {
        roomHelper = Room.databaseBuilder(context, RoomHelper::class.java, "room_reminder").build()
        roomDao = roomHelper.roomDao()

        CoroutineScope(Dispatchers.IO).launch {
            val roomList = roomDao.getAll()
            roomList.forEach {
                if (it.targetAlarmTime > System.currentTimeMillis()) {
                    val reminder = Reminder(
                        it.no, it.content, it.memo,
                        it.dDay, it.notificationTime,
                        it.remainingDays, it.targetAlarmTime, it.isAutoDelete
                    )
                    setAlarmManager(context, reminder)
                }
            }
        } // CoroutineScope
    } // reRegistration

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun setAlarmManager(context: Context, reminder: Reminder) {
        val alarmId: Int = reminder.no.toInt()
        Log.d("test", "$alarmId: 알람매니저 설정")

        val intent = Intent(context, AlarmReceiver::class.java)
        intent.action = "android.intent.action.NOTIFY"
        intent.putExtra("id", alarmId)
        intent.putExtra("content", reminder.content)

        val pendingIntent =
            PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_CANCEL_CURRENT)

        val alarmManager = context.getSystemService(AppCompatActivity.ALARM_SERVICE) as AlarmManager

        /*alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, reminder.targetAlarmTime, pendingIntent
        )*/

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.targetAlarmTime, pendingIntent), pendingIntent
        )
    } // setAlarmManager

    private fun showNotification(context: Context, notifyID: Int, content: String) {
        Log.d("test", "AlarmReceiver::showNotification")

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder: NotificationCompat.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // api level 26
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
                NotificationCompat.Builder(context, CHANNEL_ID)
            } else { // 마시멜로우(M) ~ 누가(N) 사용자
                NotificationCompat.Builder(context)
            }

        builder.setContentTitle("알림 타이틀")
        builder.setContentText(content)
        builder.setSmallIcon(R.drawable.image_noti_small_icon)
        builder.setLargeIcon(
            BitmapFactory.decodeResource(context.resources, R.drawable.img_symbol)
        )
        builder.setAutoCancel(true)

        val pendingIntentFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 9999, intent, pendingIntentFlag)
        builder.setContentIntent(pendingIntent)

        val notification: Notification = builder.build()
        notificationManager.notify(notifyID, notification)
    } // showNotification

    companion object {
        const val CHANNEL_ID = "notification"
        const val CHANNEL_NAME = "알림"
    }

}
