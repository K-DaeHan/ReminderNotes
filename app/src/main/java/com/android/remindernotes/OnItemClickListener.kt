package com.android.remindernotes

import android.view.View
import com.android.remindernotes.adapter.ViewHolder

interface OnItemClickListener {
    fun onItemClick(holder: ViewHolder?, view: View?, position: Int)
}
