package com.android.remindernotes.activity

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.android.remindernotes.AlarmReceiver
import com.android.remindernotes.R
import com.android.remindernotes.Reminder
import com.android.remindernotes.SwipeHelperCallback
import com.android.remindernotes.adapter.ReminderAdapter
import com.android.remindernotes.databinding.ActivityMainBinding
import com.android.remindernotes.model.RoomDao
import com.android.remindernotes.model.RoomHelper
import com.android.remindernotes.model.RoomReminder
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val reminderAdapter: ReminderAdapter by lazy { ReminderAdapter(this) }

    private val roomHelper: RoomHelper by lazy {
        Room.databaseBuilder(this, RoomHelper::class.java, "room_reminder")
            .build()
    }

    private val roomDao: RoomDao by lazy { roomHelper.roomDao() }

    private val sharedPref: SharedPreferences by lazy {
        getSharedPreferences("shared_pref", MODE_PRIVATE)
    }

    private var exitFlag = false
    private var deleteMode = false
    private lateinit var removeIndexList: BooleanArray

    private lateinit var animShowBtn: Animation
    private lateinit var animHideBtn: Animation
    private lateinit var toast: Toast
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var inputActivityResultListener: ActivityResultLauncher<Intent>
    private lateinit var exactAlarmResultListener: ActivityResultLauncher<Intent>
    private lateinit var menu: Menu

    var isSwipeDelete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initRecyclerView()
        initAnimation()
        initNavigationView()
        getAllRecords()
        itemSort()
        showWhichButton()
        registerListener()

        if (sharedPref.getLong("saved_time", -1) > 0) {
            Log.d("test", "??? ?????? ?????? ??????")
            if (reminderAdapter.itemCount > 0 && isDayPass()) {
                Log.d("test", "?????? ??????")
                countRemainingDays()
            }
        } else {
            // ?????? ???????????? ????????? ???????????? ???
            Log.d("test", "??? ?????? ??????")
            saveCurrentTimeMillis()
        }

        // onActivityResult??? ??????
        inputActivityResultListener =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { inputActivityResult ->
                if (inputActivityResult.resultCode == RESULT_OK) {
                    val intent = inputActivityResult?.data
                    val bundle: Bundle? = intent?.extras
                    val reminder: Reminder? = bundle?.getParcelable("reminder")

                    if (reminder == null) {
                        Toast.makeText(this, "??????. ?????? ??????????????????", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }

                    val updatePosition = intent.getIntExtra("position", Int.MAX_VALUE)
                    if (updatePosition != Int.MAX_VALUE) {
                        Log.d("test", "?????? ???")
                        updateItems(updatePosition, reminder)
                        return@registerForActivityResult
                    }

                    reminderAdapter.addItem(reminder)
                    binding.recyclerView.smoothScrollToPosition(reminderAdapter.itemCount)
                    showWhichButton()

                    if (reminder.targetAlarmTime != -1L) {
                        setAlarmManager(reminder)
                    }

                    val roomReminder = RoomReminder(
                        reminder.no, reminder.content, reminder.memo,
                        reminder.dDay, reminder.notificationTime,
                        reminder.remainingDays, reminder.targetAlarmTime, reminder.isAutoDelete
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        roomDao.insert(roomReminder) // DB??? ??????
                    } // coroutineScope
                } // RESULT_OK
            }

        // onActivityResult??? ??????
        exactAlarmResultListener =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
                when (activityResult.resultCode) {
                    RESULT_OK -> {
                        Log.d("test", "RESULT_OK")
                    }
                    RESULT_CANCELED -> {
                        Log.d("test", "RESULT_CANCELED")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // android 12(api level 31) ??????
                            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                            if (!alarmManager.canScheduleExactAlarms()) {
                                finish()
                            }
                        }
                    }
                    else -> {
                        Log.d("test", "Result ?")
                    }
                }
            }

        permissionProcessing()
    } // onCreate

    private fun permissionProcessing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // android 12(api level 31) ??????
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d("test", "????????? ?????? ?????? ?????? ??????")
            } else {
                Log.d("test", "????????? ?????? ?????? ????????? ??????")
                val intent = Intent()
                intent.action = ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                exactAlarmResultListener.launch(intent) // startActivityForResult ??? ??????
            }
        }
    } // permissionProcessing

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun setAlarmManager(reminder: Reminder) {
        val pendingIntentFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT

        val alarmId: Int = reminder.no.toInt()
        Log.d("test", "$alarmId: ??????????????? ??????")

        val intent = Intent(this, AlarmReceiver::class.java)
        intent.action = "android.intent.action.NOTIFY"
        intent.putExtra("id", alarmId)
        intent.putExtra("content", reminder.content)

        val pendingIntent =
            PendingIntent.getBroadcast(this, alarmId, intent, pendingIntentFlag)

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        /*alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, reminder.targetAlarmTime, pendingIntent
        )*/

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(reminder.targetAlarmTime, pendingIntent), pendingIntent
        )
    } // setAlarmManager

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun cancelAlarmManager(reminder: Reminder) {
        val pendingIntentFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_CANCEL_CURRENT

        val alarmId: Int = reminder.no.toInt()
        Log.d("test", "$alarmId: ??????????????? ??????")

        val intent = Intent(this, AlarmReceiver::class.java)
        intent.action = "android.intent.action.NOTIFY"
        intent.putExtra("id", alarmId)
        intent.putExtra("content", reminder.content)

        val pendingIntent =
            PendingIntent.getBroadcast(this, alarmId, intent, pendingIntentFlag)

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(pendingIntent)
    } // cancelAlarmManager

    private fun countRemainingDays() {
        initRemoveIndexList()

        for (index in 0 until reminderAdapter.itemCount) {
            Log.d("test", "${index}??? position ??????")
            val reminder: Reminder = reminderAdapter.getItem(index)
            val dDay: String = reminder.dDay

            val year = dDay.substring(0, 4).toInt()
            val month: Int = dDay.substring(6, 8).toInt() - 1
            val day: Int = dDay.substring(10, 12).toInt()

            val targetDate = Calendar.getInstance()
            targetDate.set(year, month, day, 23, 59, 59)
            targetDate[Calendar.MILLISECOND] = 999

            val currentTimeMillis = System.currentTimeMillis()
            val targetTimeMillis = targetDate.timeInMillis
            var renewalRemainingDays: Int

            if (currentTimeMillis > targetTimeMillis) { // "D + n"
                val millisDiff = currentTimeMillis - targetTimeMillis
                renewalRemainingDays = (millisDiff / 86_400_000).toInt()
                reminder.remainingDays = "D + ${renewalRemainingDays + 1}"

                if (reminder.isAutoDelete == 1) { // ?????? ??????
                    removeIndexList[index] = true
                } else { // ???????????? ?????? D-Day ????????? ?????? ??????
                    reminder.targetAlarmTime = -1
                    updateItems(index, reminder)
                }
            } else { // "D - n" ~ "D - Day"
                val millisDiff = targetTimeMillis - currentTimeMillis
                renewalRemainingDays = (millisDiff / 86_400_000).toInt()

                if (renewalRemainingDays == 0) reminder.remainingDays = "D - Day"
                else reminder.remainingDays = "D - $renewalRemainingDays"
                updateItems(index, reminder)
            }
        } // for

        Handler(Looper.getMainLooper()).postDelayed({
            deleteItems()
            if (reminderAdapter.itemCount == 0) {
                binding.guideButton.visibility = View.VISIBLE
                binding.guideButton.startAnimation(animShowBtn)
                binding.addOrDeleteButton.clearAnimation()
                binding.addOrDeleteButton.visibility = View.GONE
            }
        }, 400L)
        saveCurrentTimeMillis()
    } // countRemainingDays

    @SuppressLint("ClickableViewAccessibility")
    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true // https://blog.naver.com/kdhan16/222124456792 ??????

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = reminderAdapter

        val swipeHelperCallback = SwipeHelperCallback(this)
        itemTouchHelper = ItemTouchHelper(swipeHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        /*binding.recyclerView.setOnTouchListener { _, _ ->
            swipeHelperCallback.removePreviousClamp(binding.recyclerView)
            false
        }*/
    } // initRecyclerView

    private fun initAnimation() {
        animHideBtn =
            AnimationUtils.loadAnimation(this, R.anim.scale_hide_btn)
        animShowBtn =
            AnimationUtils.loadAnimation(this, R.anim.scale_show_btn)
    } // initAnimation

    private fun initNavigationView() {
        with(binding) {
            setSupportActionBar(toolbar)

            val toggle = ActionBarDrawerToggle(
                this@MainActivity,
                drawerLayout,
                toolbar,
                com.android.remindernotes.R.string.navigation_drawer_open,
                com.android.remindernotes.R.string.navigation_drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()

            navView.menu.getItem(0).isChecked = true // ?????? ???????????? ????????? ??????
            navView.itemIconTintList = null // ?????? ?????? ??????
            navView.setNavigationItemSelectedListener { item ->
                val selectId = item.itemId
                drawerLayout.closeDrawer(GravityCompat.START) // NavigationView ??????

                when (selectId) {
                    com.android.remindernotes.R.id.send -> {
                        sendComments()
                    }
                    com.android.remindernotes.R.id.setting -> {
                        showSettingScreen()
                    }
                } // when
                return@setNavigationItemSelectedListener false
                /* return ????????? true ?????? ??? ?????? ??? ???????????? highlighting ?????? ??????.
                   false ?????? ??? ?????? ?????? ??????. */
            } // onNavigationItemSelected
        }
    } // initNavigationView

    private fun getAllRecords() {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val roomList = roomDao.getAll()
            Log.d("test", "${roomList.size} ???")

            for (item in roomList) {
                Log.d("test", "${item.no}, ${item.content}, ${item.dDay}")
                val reminder = Reminder(
                    item.no,
                    item.content,
                    item.memo,
                    item.dDay,
                    item.notificationTime,
                    item.remainingDays,
                    item.targetAlarmTime,
                    item.isAutoDelete
                )
                reminderAdapter.addItem(reminder)
            } // for
        } // coroutineScope

        runBlocking {
            job.join()
            Log.d("test", "runBlocking...")
        }
    } // getAll

    private fun showWhichButton() {
        if (reminderAdapter.itemCount > 0) {
            with(binding) {
                if (guideButton.animation == animShowBtn) {
                    Log.d("test", "???????")
                    guideButton.clearAnimation()
                }
                guideButton.visibility = View.GONE
                addOrDeleteButton.visibility = View.VISIBLE
            }
            return
        }

        with(binding) {
            guideButton.visibility = View.VISIBLE
            addOrDeleteButton.visibility = View.GONE
        }
    } // showWhichButton

    private fun registerListener() {
        // ?????? ????????? ??????
        binding.guideButton.setOnClickListener {
            binding.addOrDeleteButton.performClick()
        }

        // ??????/?????? ????????? ??????
        binding.addOrDeleteButton.setOnClickListener {
            if (deleteMode) { // "??????" ????????? ?????? - ????????? ????????? ??????
                removeIndexList.forEachIndexed { index, b -> Log.d("test", "position[$index]: $b") }

                if (removeIndexList.any { it }) {
                    showDeleteDialog() // ?????? ??????????????? ?????? ?????????????????? ?????????.
                    return@setOnClickListener
                }
                Toast.makeText(applicationContext, "????????? ????????? ??????????????????.", Toast.LENGTH_SHORT).show()
            } else { // "+" ????????? ?????? - InputActivity??? ??????
                val intent = Intent(this@MainActivity, InputActivity::class.java)
                inputActivityResultListener.launch(intent) // startActivityForResult ??? ??????
            }
        }

        // recyclerView ???????????? ??????
        reminderAdapter.setOnItemClickListener { holder, view, position ->
            Log.d("test", "$position ???")
            val reminder: Reminder = reminderAdapter.getItem(position)

            val intent = Intent(applicationContext, InputActivity::class.java)
            intent.putExtra("reminder", reminder)
            intent.putExtra("position", position)
            intent.putExtra("no", reminder.no)

            inputActivityResultListener.launch(intent) // startActivityForResult ??? ??????
            // overridePendingTransition(R.anim.translate_up, R.anim.translate)
        }

        // ????????? ??? ??? ????????? ???????????? ????????? ?????? ??????
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var preY = -1

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!recyclerView.canScrollVertically(-1)) {
                    // Log.d("test", "Top of list")
                } else if (!recyclerView.canScrollVertically(1)) {
                    // Log.d("test", "End of list")
                } else {
                    // Log.d("test", "idle")
                }
            } // onScrollStateChanged

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // dx, dy??? ????????? ????????? ????????? ???????????? ????????? ????????? ??? ?????? ?????????.
                // ????????? ????????? ??????, ?????? ????????? ????????? ??????
                if (deleteMode.not()) { // ?????? ????????? ?????? ???????????? ?????? ???????????? ??????
                    if (dy > 0 && preY <= 0) {
                        binding.addOrDeleteButton.startAnimation(animHideBtn) // ?????? ?????????
                    } else if (dy < 0 && preY > 0) {
                        binding.addOrDeleteButton.startAnimation(animShowBtn) // ?????? ?????????
                    }
                    preY = dy
                }
            } // onScrolled
        })
    } // registerListener

    private fun changeToAddBtn() {
        binding.addOrDeleteButton.text = "???"
        binding.addOrDeleteButton.textSize = 26F
        binding.addOrDeleteButton.setTextColor(Color.WHITE)
        binding.addOrDeleteButton.startAnimation(animShowBtn)
    } // changeToAddBtn

    private fun changeToDeleteBtn() {
        binding.addOrDeleteButton.text = "??????"
        binding.addOrDeleteButton.textSize = 21F
        binding.addOrDeleteButton.setTextColor(Color.parseColor("#ABF200"))
        binding.addOrDeleteButton.startAnimation(animShowBtn)
    } // changeToDeleteBtn

    private fun enterAddMode() {
        if (binding.addOrDeleteButton.text == "??????") {
            itemTouchHelper.attachToRecyclerView(binding.recyclerView)
            deleteMode = false

            binding.addOrDeleteButton.startAnimation(animHideBtn)
            Handler(mainLooper).postDelayed({ changeToAddBtn() }, 400L)
        }
    } // enterAdditionMode

    fun enterDeleteMode() {
        itemTouchHelper.attachToRecyclerView(null)
        deleteMode = true
        initRemoveIndexList()

        if (binding.addOrDeleteButton.animation === animHideBtn) { // ????????? ?????? ????????? ??????,
            changeToDeleteBtn()
        } else {
            binding.addOrDeleteButton.startAnimation(animHideBtn)
            Handler(mainLooper).postDelayed({ changeToDeleteBtn() }, 400L)
        }
    } // enterDeletionMode

    fun initRemoveIndexList() {
        removeIndexList = BooleanArray(reminderAdapter.itemCount)
    } // initRemoveIndexList

    fun setRemoveIndexList(index: Int, state: Boolean) {
        removeIndexList[index] = state
    } // setRemoveIndexList

    fun isDeleteMode(): Boolean {
        return deleteMode
    } // isDeleteMode

    fun showDeleteDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("??????")
        builder.setMessage("?????? ?????????????????????????")
        builder.setIcon(android.R.drawable.ic_dialog_alert)

        builder.setPositiveButton("??? ???????????????") { dialog, which ->
            reminderAdapter.allReturnToNormalState()
            deleteItems()

            if (reminderAdapter.itemCount > 0) {
                enterAddMode()
            } else { // ?????? ??????
                // ?????? ?????? ?????? ???????????? ?????? ?????? ????????? ?????? ????????? ??????
                deleteMode = false

                binding.guideButton.visibility = View.VISIBLE
                binding.guideButton.startAnimation(animShowBtn)
                changeToAddBtn()
                binding.addOrDeleteButton.clearAnimation()
                binding.addOrDeleteButton.visibility = View.GONE
            }

            if (isSwipeDelete) {
                isSwipeDelete = !isSwipeDelete
            }
        } // positive

        builder.setNeutralButton("??????") { dialog, which ->
            cancelDelete()
        } // neutral

        builder.setOnDismissListener {
            cancelDelete()
        } // dismiss

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(com.android.remindernotes.R.drawable.dialog_round_corner)
        dialog.show()
    } //showDeleteDialog

    private fun cancelDelete() {
        if (isSwipeDelete) {
            removeIndexList.forEachIndexed { index, b -> // true??? ????????? ?????????
                if (b) {
                    reminderAdapter.notifyItemChanged(index)
                    isSwipeDelete = !isSwipeDelete
                    return@forEachIndexed
                }
            }
        }
    } // cancelDelete

    private fun isDayPass(): Boolean {
        val nextDay = Calendar.getInstance()
        nextDay.timeInMillis = sharedPref.getLong("saved_time", -1)
        nextDay.add(Calendar.DAY_OF_MONTH, 1)
        nextDay.set(Calendar.HOUR_OF_DAY, 0)
        nextDay.set(Calendar.MINUTE, 0)
        nextDay.set(Calendar.SECOND, 0)
        nextDay.set(Calendar.MILLISECOND, 0)

        return System.currentTimeMillis() >= nextDay.timeInMillis
    } // isDayPass

    private fun saveCurrentTimeMillis() {
        val editor = sharedPref.edit()
        editor.putLong("saved_time", System.currentTimeMillis())
        editor.apply()

        Log.d("test", "save currentTimeMillis: ${System.currentTimeMillis()}")
    } // saveCurrentTimeMillis

    private fun deleteItems() {
        val deleteNoList: MutableList<Long> = mutableListOf()

        var num = 0
        removeIndexList.forEachIndexed { index, b ->
            if (b) {
                val deleteObj = reminderAdapter.getItem(index - num)

                if (deleteObj.targetAlarmTime >= System.currentTimeMillis()) {
                    cancelAlarmManager(deleteObj)
                }
                deleteNoList.add(deleteObj.no)
                reminderAdapter.removeItem(index - num) // ???????????? ??????(Adapter)
                num++
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val roomList = roomDao.getAll()
            val roomDeleteList: List<RoomReminder> =
                roomList.filter { deleteNoList.contains(it.no) }
            roomDeleteList.forEach {
                roomDao.delete(it)
            }
        } // CoroutineScope

    } // deleteItems

    private fun updateItems(position: Int, reminder: Reminder) {
        val before = reminderAdapter.getItem(position).targetAlarmTime
        val after = reminder.targetAlarmTime
        resetAlarm(before, after, position, reminder)

        reminderAdapter.setItem(position, reminder) // ???????????? ????????????(Adapter)

        CoroutineScope(Dispatchers.IO).launch {
            val roomList = roomDao.getAll()
            val updateObj = roomList.single { it.no == reminder.no }

            updateObj.apply {
                /*no = reminder.no*/
                content = reminder.content
                memo = reminder.memo
                dDay = reminder.dDay
                remainingDays = reminder.remainingDays
                notificationTime = reminder.notificationTime
                targetAlarmTime = reminder.targetAlarmTime
                isAutoDelete = reminder.isAutoDelete
            }

            roomDao.update(updateObj) // DB?????? ????????????(Room)
        } // CoroutineScope
    } // updateItems

    private fun resetAlarm(before: Long, after: Long, position: Int, reminder: Reminder) {
        if (before == after) return

        val isAlarmCancel = before > System.currentTimeMillis()
        val isAlarmSet = after > System.currentTimeMillis()

        if (isAlarmCancel) {
            Log.d("test", "?????? ??????")
            cancelAlarmManager(reminderAdapter.getItem(position)) // ?????? ?????? ??????
        }

        if (isAlarmSet) {
            Log.d("test", "?????? ??? ??????")
            setAlarmManager(reminder)
        }
    } // resetAlarm

    private fun sendComments() {
        val email = Intent(Intent.ACTION_SEND)
        email.type = "plain/text"
        email.setPackage("com.google.android.gm")

        val recipientEmail = arrayOf("kdhan16@gmail.com")
        email.putExtra(Intent.EXTRA_EMAIL, recipientEmail)
        // email.putExtra(Intent.EXTRA_SUBJECT, "??????"); // ??????
        // email.putExtra(Intent.EXTRA_TEXT, "??????"); // ??????
        startActivity(email)
    } // sendComments

    private fun showSettingScreen() {
        /*val intent = Intent(applicationContext, SettingPreferenceActivity::class.java)
        startActivityForResult(intent, MainActivity.REQUEST_CODE_SETTING)*/
    } // ShowSettingScreen

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        if (deleteMode) { // ?????? ????????? ??????
            enterAddMode()
            // ?????? ?????? ????????? ??????????????? ?????? ?????????(??? ??????) ???????????? ??????
            reminderAdapter.allReturnToNormalState()
            return
        }

        if (exitFlag) {
            toast.cancel()
            super.onBackPressed()
        } else {
            exitFlag = true
            toast =
                Toast.makeText(applicationContext, "??? ??? ??? ????????? ?????? ???????????????.", Toast.LENGTH_SHORT)
            toast.show()

            Timer().schedule(object : TimerTask() {
                override fun run() {
                    exitFlag = false
                }
            }, 2000)
        }
    } // onBackPressed

    private fun itemSort() {
        Log.d("test", "itemSort")
        val sortType = sharedPref.getInt("type_sort", 0)
        reminderAdapter.sort(sortType)
    } // itemSort

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("test", "onCreateOptionsMenu")
        val menuInflater: MenuInflater = menuInflater
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu

        when (sharedPref.getInt("type_sort", 0)) {
            0 -> menu.findItem(R.id.last_add_sorting).title = "????????? ?????? ?????? ??????  ???"
            1 -> menu.findItem(R.id.asc_sorting).title = "????????? ??????(????????????)  ???"
            -1 -> menu.findItem(R.id.desc_sorting).title = "????????? ??????(????????????)  ???"
        }

        return true // false ?????? ??? ????????? ???????????? ??????
    } // onCreateOptionsMenu

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val idSort: MenuItem = menu.findItem(R.id.last_add_sorting)
        val ascSort: MenuItem = menu.findItem(R.id.asc_sorting)
        val descSort: MenuItem = menu.findItem(R.id.desc_sorting)

        when (item.itemId) {
            R.id.last_add_sorting -> {
                reminderAdapter.sort(ID_SORT)
                idSort.title = "????????? ?????? ?????? ??????  ???"
                ascSort.title = "????????? ??????(????????????)"
                descSort.title = "????????? ??????(????????????)"
            }
            R.id.asc_sorting -> {
                reminderAdapter.sort(ASC_SORT)
                idSort.title = "????????? ?????? ?????? ??????"
                ascSort.title = "????????? ??????(????????????)  ???"
                descSort.title = "????????? ??????(????????????)"
            }
            com.android.remindernotes.R.id.desc_sorting -> {
                reminderAdapter.sort(DESC_SORT)
                idSort.title = "????????? ?????? ?????? ??????"
                ascSort.title = "????????? ??????(????????????)"
                descSort.title = "????????? ??????(????????????)  ???"
            }
        }
        return super.onOptionsItemSelected(item)
    } // onOptionsItemSelected

    companion object {
        const val ID_SORT = 0
        const val ASC_SORT = 1
        const val DESC_SORT = -1
    }

    private fun showUpdateDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("????????? ????????? ???????????????")
        builder.setMessage("??? ???????????? ???????????? ???????????????????")
        builder.setIcon(android.R.drawable.ic_dialog_alert)

        builder.setPositiveButton("???????????? ?????? ??????") { dialog, which ->

        } // positive

        builder.setNeutralButton("??????") { dialog, which ->

        } // neutral

        builder.setOnDismissListener {

        } // dismiss

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_round_corner)
        dialog.show()
    } // showUpdateDialog

}
