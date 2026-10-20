package com.philj56.gbcc.main

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

    class FileViewHolder(itemView: View, val onClick: (RomEntry) -> Unit, val onShowActions: (RomEntry) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val textView = itemView.findViewById<TextView>(R.id.fileEntry)
        private val imageView = itemView.findViewById<ImageView>(R.id.fileIcon)
        private val moreButton = itemView.findViewById<ImageButton>(R.id.fileMore)
        private var currentEntry: RomEntry? = null

        init {
            itemView.setOnClickListener {
                currentEntry?.let { onClick(it) }
            }
            itemView.setOnLongClickListener {
                currentEntry?.takeUnless { it.isDirectory }?.let { onShowActions(it) }
                return@setOnLongClickListener true
            }
            // The ⋮ button opens the same actions as a long press
            moreButton.setOnClickListener {
                currentEntry?.let { onShowActions(it) }
            }
        }

        fun bind(entry: RomEntry) {
            currentEntry = entry
            imageView.setImageResource(
                when {
                    entry.isDirectory -> R.drawable.ic_folder_34dp
                    entry.extension.equals("gbc", ignoreCase = true) -> R.drawable.ic_file_gbc
                    else -> R.drawable.ic_file_dmg
                }
            )
            // Folders have no actions
            moreButton.visibility = if (entry.isDirectory) View.INVISIBLE else View.VISIBLE
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
        return FileViewHolder(view, onClick, onShowActions)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
