package com.aicallscreen.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aicallscreen.R
import com.aicallscreen.rules.AllowlistEntry

class AllowlistAdapter(
    private val onRemove: (AllowlistEntry) -> Unit,
) : ListAdapter<AllowlistEntry, AllowlistAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_allowlist_entry, parent, false)
        return Holder(view, onRemove)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        itemView: View,
        private val onRemove: (AllowlistEntry) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.allowlistLabel)
        private val number: TextView = itemView.findViewById(R.id.allowlistNumber)
        private val remove: ImageButton = itemView.findViewById(R.id.buttonRemoveAllowlist)

        fun bind(entry: AllowlistEntry) {
            label.text = entry.displayLabel
            number.text = entry.normalizedNumber
            remove.setOnClickListener { onRemove(entry) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AllowlistEntry>() {
        override fun areItemsTheSame(oldItem: AllowlistEntry, newItem: AllowlistEntry): Boolean =
            oldItem.normalizedNumber == newItem.normalizedNumber

        override fun areContentsTheSame(oldItem: AllowlistEntry, newItem: AllowlistEntry): Boolean =
            oldItem == newItem
    }
}
