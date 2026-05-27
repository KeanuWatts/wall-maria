package com.aicallscreen.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aicallscreen.R

data class ChatLine(
    val id: String,
    val roleLabel: String,
    val body: String,
    val isAi: Boolean,
    val isSystem: Boolean = false,
)

class ChatLineAdapter : ListAdapter<ChatLine, ChatLineAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_line, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val roleView: TextView = itemView.findViewById(R.id.chatRole)
        private val bodyView: TextView = itemView.findViewById(R.id.chatBody)

        fun bind(line: ChatLine) {
            roleView.text = line.roleLabel
            bodyView.text = line.body
            val roleColor = when {
                line.isSystem -> Color.parseColor("#8B949E")
                line.isAi -> Color.parseColor("#58A6FF")
                else -> Color.parseColor("#3FB950")
            }
            roleView.setTextColor(roleColor)
            bodyView.setTextColor(Color.parseColor("#E6EDF3"))
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatLine>() {
        override fun areItemsTheSame(oldItem: ChatLine, newItem: ChatLine): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatLine, newItem: ChatLine): Boolean =
            oldItem == newItem
    }
}
