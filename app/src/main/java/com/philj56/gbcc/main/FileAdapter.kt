package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philj56.gbcc.R

class FileAdapter(
    private val onClick: (RomEntry) -> Unit,
    private val onShowActions: (RomEntry) -> Unit
) :
    ListAdapter<RomEntry, FileAdapter.FileViewHolder>(FileDiffCallback) {

    /** Base names of the games that have a save, to mark them in the list. */
    var gamesWithSaves: Set<String> = emptySet()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (value != field) {
                field = value
                notifyDataSetChanged()
            }
        }

    class FileViewHolder(itemView: View, private val adapter: FileAdapter, val onClick: (RomEntry) -> Unit, val onShowActions: (RomEntry) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val textView = itemView.findViewById<TextView>(R.id.fileEntry)
        private val imageView = itemView.findViewById<ImageView>(R.id.fileIcon)
        private val saveIcon = itemView.findViewById<ImageView>(R.id.fileSaveIcon)
        private val moreButton = itemView.findViewById<ImageButton>(R.id.fileMore)
        private var currentEntry: RomEntry? = null

        init {
            itemView.setOnClickListener {
                currentEntry?.let { onClick(it) }
            }
            itemView.setOnLongClickListener {
                currentEntry?.let { onShowActions(it) }
                return@setOnLongClickListener true
            }
            // The ⋮ button opens the same actions as a long press
            moreButton.setOnClickListener {
                currentEntry?.let { onShowActions(it) }
            }
        }

        fun bind(entry: RomEntry) {
            currentEntry = entry
            imageView.setImageResource(if (entry.isGbc) R.drawable.ic_file_gbc else R.drawable.ic_file_dmg)
            saveIcon.visibility = if (entry.baseName in adapter.gamesWithSaves) View.VISIBLE else View.GONE
            textView.text = entry.baseName
        }
    }

    private object FileDiffCallback : DiffUtil.ItemCallback<RomEntry>() {
        override fun areItemsTheSame(oldItem: RomEntry, newItem: RomEntry): Boolean {
            return oldItem.documentId == newItem.documentId
        }

        override fun areContentsTheSame(oldItem: RomEntry, newItem: RomEntry): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry_file, parent, false)
        return FileViewHolder(view, this, onClick, onShowActions)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
