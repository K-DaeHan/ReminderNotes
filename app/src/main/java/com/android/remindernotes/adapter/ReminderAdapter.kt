package com.android.remindernotes.adapter

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.android.remindernotes.OnItemClickListener
import com.android.remindernotes.R
import com.android.remindernotes.Reminder
import com.android.remindernotes.activity.MainActivity
import com.android.remindernotes.databinding.ReminderItemBinding
import java.util.*
import kotlin.Comparator

class ReminderAdapter(private val mainActivity: MainActivity) : RecyclerView.Adapter<ViewHolder>(),
    OnItemClickListener {

    private var items: ArrayList<Reminder> = ArrayList<Reminder>()
    private var viewHolders: ArrayList<ViewHolder> = ArrayList<ViewHolder>()

    private lateinit var itemClickListener: (ViewHolder, View, Int) -> Unit

    private val sharedPref: SharedPreferences by lazy {
        mainActivity.getSharedPreferences("shared_pref", AppCompatActivity.MODE_PRIVATE)
    }

    //----------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        /*val itemView = layoutInflater.inflate(R.layout.reminder_item, parent, false)
        return ViewHolder(itemView)*/

        val binding = ReminderItemBinding.inflate(layoutInflater, parent, false)
        val vh = ViewHolder(binding, mainActivity, this)
        viewHolders.add(vh)

        return vh
    } // onCreateViewHolder

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item: Reminder = items[position]
        holder.setItem(item)
    } // onBindViewHolder

    override fun getItemCount(): Int {
        return items.size
    } // getItemCount
    //----------------------------------------------------------------------------------------------

    override fun onItemClick(holder: ViewHolder?, view: View?, position: Int) {
        itemClickListener(holder!!, view!!, position)
    }

    fun setOnItemClickListener(listener: (ViewHolder, View, Int) -> Unit) { // 외부에서 리스너를 설정할 수 있도록
        itemClickListener = listener
    } // setOnItemClickListener

    fun addItem(item: Reminder) {
        items.add(item)
        notifyItemInserted(itemCount)
    } // addItem

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    } // removeItem

    fun getItem(position: Int): Reminder {
        return items[position]
    } // getItem

    fun setItem(position: Int, item: Reminder) {
        items[position] = item
        notifyItemChanged(position)
    } // setItem

    fun allReturnToNormalState() {
        for (vh in viewHolders) {
            vh.returnToNormalState()
        }
    } // allReturnToNormalState

    fun sort(num: Int) {
        if (itemCount <= 1) {
            return
        }

        Collections.sort(items, object : Comparator<Reminder> {
            override fun compare(o1: Reminder, o2: Reminder): Int {
                if (num != 0) {
                    val n1 = stringToInt(o1.remainingDays)
                    val n2 = stringToInt(o2.remainingDays)

                    return when {
                        n1 > n2 -> 1 * num
                        n1 == n2 -> 0
                        else -> -1 * num
                    }
                } else {
                    val n1 = o1.no
                    val n2 = o2.no

                    return when {
                        n1 > n2 -> 1
                        n1 == n2 -> 0
                        else -> -1
                    }
                }
            } // compare

            fun stringToInt(remainingDays: String): Int {
                val str = remainingDays.substring(4)

                return if (str != "Day") {
                    str.toInt() * (if (remainingDays[2] == '-') -1 else 1)
                } else {
                    0
                }
            } // stringToInt
        })

        for (index in 0 until items.size) {
            notifyItemChanged(index)
        }

        val editor = sharedPref.edit()
        editor.putInt("type_sort", num)
        editor.apply()
    } // sort

} // ReminderAdapter

class ViewHolder(
    private val binding: ReminderItemBinding,
    private val mainActivity: MainActivity,
    private val listener: OnItemClickListener
) : RecyclerView.ViewHolder(binding.root) {
    init {
        with(binding) {
            // short touch
            itemView.setOnClickListener {
                val aBoolean: Boolean
                val resId: Int
                val contentViewColor: Int

                if (mainActivity.isDeleteMode()) { // 삭제 모드
                    if (contentTextView.currentTextColor == Color.BLACK) { // 삭제 대기 상태로 전환 (블랙 -> 블루)
                        aBoolean = true
                        resId = R.drawable.ripple_item_delete_state
                        contentViewColor = Color.BLUE
                    } else { // 삭제 대기 상태 해제 (블루 -> 블랙)
                        aBoolean = false
                        resId = R.drawable.ripple_item_normal_state
                        contentViewColor = Color.BLACK
                    }

                    mainActivity.setRemoveIndexList(adapterPosition, aBoolean)

                    Handler(Looper.getMainLooper()).postDelayed({
                        itemView.setBackgroundResource(resId)
                        contentTextView.setTextColor(contentViewColor)
                    }, 150)
                } else { // 기본 모드
                    val position: Int = adapterPosition
                    listener.onItemClick(this@ViewHolder, it, position)
                }
            }

            // long touch
            itemView.setOnLongClickListener {
                if (!mainActivity.isDeleteMode()) { // 삭제 모드가 아닌 경우
                    mainActivity.enterDeleteMode()
                    mainActivity.setRemoveIndexList(adapterPosition, true)

                    itemView.setBackgroundResource(R.drawable.ripple_item_delete_state)
                    contentTextView.setTextColor(Color.BLUE)
                }
                return@setOnLongClickListener true
                /* 뷰 하나에 onClick, onLongClick 둘 다 적용되어 있으면서
                onLongClick 리턴 값이 false 인 경우에는 손가락을 떼는 순간 onClick 이 작동한다. */
            }
        } // with
    } // init

    fun setItem(reminder: Reminder) {
        with(binding) {
            contentTextView.text = reminder.content
            memoTextView.text = reminder.memo
            dDayTextView.text = reminder.dDay
            remainingDaysTextView.text = reminder.remainingDays

            when {
                remainingDaysTextView.text.toString().contains("Day") -> {
                    remainingDaysTextView.setTextColor(Color.parseColor("#C90000"))
                    postPositionTextView.text = "까지"
                }
                remainingDaysTextView.text.toString().contains("-") -> {
                    remainingDaysTextView.setTextColor(Color.parseColor("#6200EE"))
                    postPositionTextView.text = "까지"
                }
                else -> {
                    remainingDaysTextView.setTextColor(Color.parseColor("#EDA900"))
                    postPositionTextView.text = "부터"
                }
            } // when

            if (reminder.targetAlarmTime != -1L) {
                notiImageView.visibility = View.VISIBLE
            } else {
                notiImageView.visibility = View.GONE
            }

            if (reminder.isAutoDelete == 1) {
                autoDeleteImageView.visibility = View.VISIBLE
            } else {
                autoDeleteImageView.visibility = View.GONE
            }
        } // with
    } // setItem

    fun returnToNormalState() {
        if (binding.contentTextView.currentTextColor == Color.BLUE) {
            binding.itemView.setBackgroundResource(R.drawable.ripple_item_normal_state)
            binding.contentTextView.setTextColor(Color.BLACK)
        }
    } // def

} // ViewHolder
