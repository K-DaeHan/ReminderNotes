package com.android.remindernotes.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.remindernotes.R
import com.android.remindernotes.Reminder
import com.android.remindernotes.databinding.ActivityInputBinding
import java.text.SimpleDateFormat
import java.util.*

class InputActivity : AppCompatActivity() {
    private val binding: ActivityInputBinding by lazy { ActivityInputBinding.inflate(layoutInflater) }
    private val imm: InputMethodManager by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }
    private var toast: Toast? = null

    private lateinit var dDay: String
    private var notificationTime: String = ""

    private var year: Int = 0
    private var month: Int = 0
    private var day: Int = 0
    private var hour: Int = 0
    private var minute: Int = 0

    private var modificationMode = false
    private var isAlarmSet = false
    private var toggleStatus = true

    private var itemPosition: Int? = null
    private var no: Long? = null

    private val sharedPref: SharedPreferences by lazy {
        getSharedPreferences("shared_pref", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initHelpButton()
        initClickListener()

        val intent = intent
        val bundle = intent.extras

        modificationMode =
            if (bundle != null) { // recyclerView 아이템을 눌러서 InputActivity 화면으로 넘어옴
                bringInputActivityFromRecyclerView(bundle)
                itemPosition = bundle.getInt("position")
                no = bundle.getLong("no")
                true
            } else { // "+" 버튼을 눌러서 InputActivity 화면으로 넘어옴
                // keyPadUp()
                binding.contentEdit.requestFocus()
                no = getReminderID()
                false
            }
    } // onCreate

    private fun initClickListener() {
        // 날짜 선택 버튼 클릭
        binding.selectDateButton.setOnClickListener {
            dateSelectionProcessing()
        }

        // 시간 선택 버튼 클릭
        binding.selectTimeButton.setOnClickListener {
            timeSelectionProcessing()
        }

        // 라디오 버튼 클릭
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            radioSelectionProcessing(checkedId) // when
        }

        // 토클 버튼 클릭
        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            toggleProcessing(isChecked)
        }

        // 취소 버튼 클릭
        binding.cancelButton.setOnClickListener {
            keyPadDown()
            onBackPressed()
        }

        // 저장 버튼 클릭
        binding.saveButton.setOnClickListener {
            keyPadDown()
            saveProcessing()
        }
    } // initClickListener

    private fun bringInputActivityFromRecyclerView(bundle: Bundle) {
        Log.d("test", "리싸이클러뷰로 들어옴")
        val reminder: Reminder? = bundle.getParcelable("reminder")

        if (reminder != null) {
            with(binding) {
                contentEdit.setText(reminder.content)
                memoEdit.setText(reminder.memo)
                selectDateButton.text = reminder.dDay

                dDay = reminder.dDay
                year = dDay.substring(0, 4).toInt()
                month = dDay.substring(6, 8).toInt() - 1
                day = dDay.substring(10, 12).toInt()

                if (reminder.targetAlarmTime == -1L) {
                    negative.isChecked = true
                } else {
                    if (isDDayExceed()) {
                        negative.isChecked = true
                    } else {
                        positive.isChecked = true
                        positive.performClick()
                        isAlarmSet = true
                        selectTimeButton.text = reminder.notificationTime

                        notificationTime = reminder.notificationTime
                        hour = notificationTime.substring(3, 5).toInt()
                        minute = notificationTime.substring(6).toInt()
                        if (notificationTime.startsWith("오후") && hour != 12) {
                            hour += 12
                        } else if (notificationTime.startsWith("오전") && hour == 12) {
                            hour -= 12
                        }
                    }
                }

                toggleStatus = reminder.isAutoDelete == 1
                if (!toggleStatus) toggleButton.setTextColor(Color.parseColor("#626363"))
                toggleButton.isChecked = toggleStatus
            } // with
        }
    } // bringInputActivityFromRecyclerView

    private fun isDDayExceed(): Boolean { // 설정한 D-Day가 지났을 경우 true 반환, 아니면 false 반환
        extractDateFromButton()

        val targetDate = Calendar.getInstance()
        targetDate.set(year, month, day, 23, 59, 59)
        targetDate.set(Calendar.MILLISECOND, 999)

        val targetTimeMillis = targetDate.timeInMillis

        return System.currentTimeMillis() > targetTimeMillis
    } // isDDayExceed

    @SuppressLint("SimpleDateFormat")
    private fun dateSelectionProcessing() {
        if (binding.selectDateButton.text != "날짜 선택") {
            extractDateFromButton()
        }

        val datePickerDialog = DatePickerDialog(
            this@InputActivity,
            R.style.pickerDialogStyle,
            { _, _year, _month, _dayOfMonth ->
                year = _year
                month = _month
                day = _dayOfMonth

                val calendar = Calendar.getInstance()
                calendar.set(year, month, day) // 데이트 피커에서 선택한 날짜로 set

                val simpleDateFormat = SimpleDateFormat("yyyy년 MM월 dd일")
                dDay = simpleDateFormat.format(calendar.time) // getTime의 반환형은 Date 형

                binding.selectDateButton.text = dDay
            }, year, month, day
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    } // dateSelectionProcessing

    @SuppressLint("SimpleDateFormat")
    private fun timeSelectionProcessing() {
        if (binding.selectDateButton.text.equals("날짜 선택")) {
            sendToastMessage("날짜를 먼저 선택해주세요")
            return
        }

        val timePickerDialog = TimePickerDialog(
            this@InputActivity,
            R.style.pickerDialogStyle,
            { _, hourOfDay, _minute ->
                hour = hourOfDay
                minute = _minute

                val calender = Calendar.getInstance()
                calender.set(year, month, day, hour, minute)

                if (System.currentTimeMillis() >= calender.timeInMillis) {
                    sendToastMessage("D-Day가 오늘입니다. 현재 시간 이후로 설정해 주세요")
                    return@TimePickerDialog
                }

                val simpleDateFormat = SimpleDateFormat("a hh:mm")
                notificationTime = simpleDateFormat.format(calender.time) // getTime의 반환형은 Date 형

                binding.selectTimeButton.text = notificationTime
                Log.d("test", "${hour}시 ${minute}분")
            }, hour, minute, false
        )

        timePickerDialog.show()
    } // timeSelectionProcessing

    private fun radioSelectionProcessing(checkedId: Int) {
        when (checkedId) {
            R.id.negative -> {
                Log.d("test", "알림 없음")
                binding.selectTimeButton.text = "시간 선택"
                binding.selectTimeArea.clearAnimation()
                binding.selectTimeArea.visibility = View.GONE
                isAlarmSet = false
            }
            R.id.positive -> {
                if (modificationMode && isDDayExceed()) {
                    sendToastMessage("D-Day가 지나서 알림을 받을 수 없습니다. D-Day를 다시 설정해주세요.")
                    binding.negative.isChecked = true
                    return
                }

                Log.d("test", "알림 받음")
                val animShow =
                    AnimationUtils.loadAnimation(applicationContext, R.anim.scale_show_btn)
                binding.selectTimeArea.startAnimation(animShow)
                binding.selectTimeArea.visibility = View.VISIBLE
                isAlarmSet = true
                keyPadDown()
            }
        } // when
    } // radioSelectionProcessing

    private fun toggleProcessing(isChecked: Boolean) {
        if (isChecked) {
            Log.d("test", "On") // Off -> On
            if (modificationMode && isDDayExceed()) { // 디데이 초과되었는데 ON 으로 눌렀을 경우
                sendToastMessage("D-Day가 지났습니다.")
                binding.toggleButton.isChecked = false
                return
            }
            binding.toggleButton.setTextColor(Color.parseColor("#6200EE"))
            toggleStatus = true
        } else {
            Log.d("test", "Off") // On -> Off
            // On -> Off
            binding.toggleButton.setTextColor(Color.parseColor("#626363"))
            toggleStatus = false
        }
    } // toggleClickProcessing

    private fun saveProcessing() {
        val intent = Intent(this@InputActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP

        with(binding) {
            if (modificationMode) { // 기존에 만들어있던 아이템을 눌러서 넘어온 경우
                intent.putExtra("position", itemPosition)
            }

            if (emptyCheck(contentEdit.text.toString())) { // 작업 내용을 작성하지 않았을 경우
                sendToastMessage("작업 내용을 입력해주세요")
            } else if (selectDateButton.text == "날짜 선택") { // 날짜를 선택하지 않았을 경우
                sendToastMessage("날짜를 선택해주세요")
            } else if (isAlarmSet && selectTimeButton.text == "시간 선택") { // 알림 받음을 선택했는데 시간을 선택하지 않았을 경우
                sendToastMessage("알림 받을 시간을 선택해주세요")
            } else { // 알림 없음 or (알림 받음 + 알림 시간 선택)
                val content: String = contentEdit.text.toString()
                val memo: String = memoEdit.text.toString()
                val remainingDays: String = getRemainingDays()

                val targetAlarmTime: Long =
                    if (isAlarmSet) {
                        if (System.currentTimeMillis() < getAlarmTime()) {
                            getAlarmTime()
                        } else {
                            if (modificationMode) {
                                -1L
                            } else {
                                sendToastMessage("D-Day가 오늘입니다. 현재 시간 이후로 설정해 주세요")
                                return
                            }
                        }
                    } else {
                        -1L
                    }

                val isAutoDelete: Int = if (toggleStatus) 1 else 0 // 1 -> On, 0 -> Off

                val reminder = Reminder(
                    no!!,
                    content,
                    memo,
                    dDay,
                    notificationTime,
                    remainingDays,
                    targetAlarmTime,
                    isAutoDelete
                )

                intent.putExtra("reminder", reminder)

                setResult(RESULT_OK, intent)
                onBackPressed()
            }
        } // with
    } // saveProcessing

    private fun getReminderID(): Long {
        val id: Long = sharedPref.getLong("reminder_id", 1)

        val editor = sharedPref.edit()
        editor.putLong("reminder_id", id + 1)
        editor.apply()

        return id
    } // getReminderID

    private fun getAlarmTime(): Long { // 알림 설정한 목표 시간(ms)
        val targetTime = Calendar.getInstance()
        targetTime.set(year, month, day, hour, minute, 0)
        targetTime.set(Calendar.MILLISECOND, 0)

        return targetTime.timeInMillis
    } // getRemainingTime

    private fun getRemainingDays(): String { // D-Day 까지 남은 날짜 계산
        val targetDate = Calendar.getInstance()
        targetDate.set(year, month, day, 23, 59, 59)
        targetDate.set(Calendar.MILLISECOND, 999)

        val targetTimeMillis = targetDate.timeInMillis // D-Day의 시간을 1/1000초(ms) 단위로 반환
        val currentTimeMillis = System.currentTimeMillis() // 현재 시간을 1/1000초(ms) 단위로 반환
        val dateDiff: Int

        return if (currentTimeMillis > targetTimeMillis) { // "D + n" : n > 0
            val millisDiff = currentTimeMillis - targetTimeMillis
            dateDiff = (millisDiff / 86_400_000).toInt() // 60 * 60 * 24 * 1000 = 86,400,000ms = 24h
            "D + ${dateDiff + 1}"
        } else { // "D - n" ~ "D - day" : n <= 0
            val millisDiff = targetTimeMillis - currentTimeMillis
            dateDiff = (millisDiff / 86_400_000).toInt() // 60 * 60 * 24 * 1000 = 86,400,000ms = 24h
            if (dateDiff == 0) "D - Day" else "D - $dateDiff"
        }
    } // getRemainingDays

    private fun extractDateFromButton() {
        dDay = binding.selectDateButton.text as String
        year = dDay.substring(0, 4).toInt()
        month = dDay.substring(6, 8).toInt() - 1
        day = dDay.substring(10, 12).toInt()
    } // extractDateFromButton

    private fun emptyCheck(content: String): Boolean {
        var flag = true
        for (i in content.indices) {
            if (content[i] != ' ' && content[i] != '\n') {
                flag = false
                break
            }
        }
        return flag // 문자열이 공백으로 되어있을 경우 true 반환
    } // emptyCheck

    private fun initHelpButton() {
        val helpBtn = arrayOf(
            findViewById(R.id.helpBtn1),
            findViewById(R.id.helpBtn2),
            findViewById<Button>(R.id.helpBtn3)
        )

        for (btn in helpBtn) {
            btn.setOnClickListener(OnHelpButtonClickListener())
        }
    } // initHelpButton

    override fun onBackPressed() {
        toast?.cancel()
        super.onBackPressed()
        overridePendingTransition(R.anim.translate, R.anim.translate_down)
    } // onBackPressed

    private fun sendToastMessage(message: String?) {
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast!!.show()
    } // sendToastMessage

    private fun keyPadUp() {
        imm.showSoftInput(binding.contentEdit, InputMethodManager.SHOW_IMPLICIT)
    } // keyPadUp

    private fun keyPadDown() {
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    } // keyPadDown

    private fun showDialog(title: String?, message: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setNegativeButton("닫기") { dialog, which -> }

        val dialog = builder.create()
        dialog.show()
    } // showDialog

    inner class OnHelpButtonClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            val title = arrayOf("D-Day", "알림 기능", "자동 삭제")
            val message = arrayOf(
                "D-Day 날짜를 선택합니다.",
                "알림을 받을 시간을 선택합니다.\n알림은 D-Day에 한 번만 울립니다.",
                "ON으로 설정 시 D-Day가 지나면 목록에서 자동으로 삭제됩니다."
            )

            when (v.id) {
                R.id.helpBtn1 -> showDialog(title[0], message[0])
                R.id.helpBtn2 -> showDialog(title[1], message[1])
                R.id.helpBtn3 -> showDialog(title[2], message[2])
            } // when
        } // onClick
    } // OnHelpButtonClickListener - Inner Class

}
