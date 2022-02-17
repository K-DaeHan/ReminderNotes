package com.android.remindernotes

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

class Reminder(no_: Long, content_: String, memo_: String, dDay_: String, notificationTime_: String,
               remainingDays_: String, targetAlarmTime_: Long, isAutoDelete_: Int) : Parcelable {
    var no: Long = no_                                   // ID
    var content: String = content_                       // 작업 내용
    var memo: String = memo_                             // 간단 메모
    var dDay: String = dDay_                             // 타겟 날짜 -> "yyyy년 mm월 dd일"
    var notificationTime: String = notificationTime_     // 알림 시간 -> "a hh:mm"
    var remainingDays: String = remainingDays_           // 남은 기간 -> "D - n" or "D - Day" or "D + n"
    var targetAlarmTime: Long = targetAlarmTime_         // 목표 알림 시간
    var isAutoDelete: Int = isAutoDelete_                // 자동 삭제 상태
    //-------------------------------------------------------------------------
    constructor(parcel: Parcel) : this(
        parcel.readLong(),parcel.readString()!!, parcel.readString()!!, parcel.readString()!!,
        parcel.readString()!!, parcel.readString()!!, parcel.readLong(), parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(no)
        parcel.writeString(content)
        parcel.writeString(memo)
        parcel.writeString(dDay)
        parcel.writeString(notificationTime)
        parcel.writeString(remainingDays)
        parcel.writeLong(targetAlarmTime)
        parcel.writeInt(isAutoDelete)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<Reminder> {
        override fun createFromParcel(parcel: Parcel): Reminder {
            return Reminder(parcel)
        }

        override fun newArray(size: Int): Array<Reminder?> {
            return arrayOfNulls(size)
        }
    }
    //-------------------------------------------------------------------------
}
