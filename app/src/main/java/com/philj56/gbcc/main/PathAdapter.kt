package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philj56.gbcc.R

class PathAdapter(private val onClick: (PathEntry) -> Unit) :
    ListAdapter<PathEntry, PathAdapter.PathViewHolder>(PathDiffCallback) {

    class PathViewHolder(itemView: View, private val adapter: PathAdapter, val onClick: (PathEntry) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val button = itemView.findViewById<Button>(R.id.pathButton)
        private var currentEntry: PathEntry? = null

        init {
            button.setOnClickListener {
                currentEntry?.let {
                    onClick(it)
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(entry: PathEntry, position: Int) {
            currentEntry = entry
            button.isActivated = (position == adapter.itemCount - 1)
            // The first entry is the ROM folder itself
            button.text = if (position == 0) entry.name else "/  ${entry.name}"
        }
    }

    private object PathDiffCallback : DiffUtil.ItemCallback<PathEntry>() {
        override fun areItemsTheSame(oldItem: PathEntry, newItem: PathEntry): Boolean {
            return oldItem.documentId == newItem.documentId
        }

        override fun areContentsTheSame(oldItem: PathEntry, newItem: PathEntry): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry_path, parent, false)
        return PathViewHolder(view, this, onClick)
    }

    override fun onBindViewHolder(holder: PathViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
