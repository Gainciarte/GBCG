package com.philj56.gbcc.main

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.io.File

private const val PREF_ROM_FOLDER = "rom_folder_uri"
private const val REFRESH_DELAY_MS = 300L
// Limits for scanning subfolders, so a huge folder (e.g. the whole storage) can't stall the app
private const val MAX_DEPTH = 4
private const val MAX_FOLDERS = 64

// Extensions are matched ignoring case, so that e.g. "Tetris.GB" is listed too
val ROM_REGEX = Regex(".*\\.gbc?", RegexOption.IGNORE_CASE)

/** A ROM or a subfolder inside the ROM folder. */
data class RomEntry(val name: String, val documentId: String, val isDirectory: Boolean) {
    /** The name without extension, which is also the name of the game's saves and config. */
    val baseName get() = if (isDirectory) name else File(name).nameWithoutExtension
    val extension get() = if (isDirectory) "" else File(name).extension
    val isGbc get() = extension.equals("gbc", ignoreCase = true)
}

/*
 * The folder the user keeps their ROMs in, chosen once with the system folder picker
 * (Storage Access Framework). The ROMs are read from there and never copied into the app.
 */
object RomFolder {
    /** True if a folder was chosen at some point, even if its permission has been lost. */
    fun wasChosen(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).contains(PREF_ROM_FOLDER)

    /** The chosen folder, or null if none has been chosen or its permission was lost. */
    fun treeUri(context: Context): Uri? {
        val uri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_ROM_FOLDER, null)?.toUri() ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (granted) uri else null
    }

    /** Remembers a folder returned by the picker, keeping access to it across restarts. */
    fun choose(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Write access is only needed to rename ROMs
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Let go of the previous folder, so the app doesn't keep access it no longer uses
        resolver.persistedUriPermissions
            .filter { it.uri != uri }
            .forEach {
                var flags = 0
                if (it.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (it.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                resolver.releasePersistableUriPermission(it.uri, flags)
            }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_ROM_FOLDER, uri.toString())
        }
    }

    fun documentUri(tree: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    fun queryChildren(context: Context, tree: Uri, documentId: String): Cursor? = try {
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId),
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE),
            null, null, null
        )
    } catch (e: Exception) {
        // The folder was deleted, or the permission revoked
        null
    }

    /** The subfolders and ROMs in a cursor from queryChildren. */
    fun entries(cursor: Cursor): List<RomEntry> {
        val list = ArrayList<RomEntry>()
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val name = cursor.getString(0) ?: continue
            val isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR
            if ((isDirectory && !name.startsWith(".")) || name.matches(ROM_REGEX)) {
                list.add(RomEntry(name, cursor.getString(1), isDirectory))
            }
        }
        return list
    }
}

/*
 * Scans the ROM folder and its subfolders, and calls onChange (debounced) with every ROM
 * found whenever files are added, removed or renamed in any of them; null means the folder
 * can't be read. The documents provider only watches a folder while a cursor on it is open,
 * so the cursors stay open until stop().
 */
class RomFolderWatcher(private val context: Context, private val onChange: (List<RomEntry>?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var cursors = emptyList<Cursor>()
    private var tree: Uri? = null
    private val refresh = Runnable { reload() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refresh)
            handler.postDelayed(refresh, REFRESH_DELAY_MS)
        }
    }

    fun watch(tree: Uri) {
        this.tree = tree
        reload()
    }

    fun reload() {
        val tree = tree ?: return
        val newCursors = ArrayList<Cursor>()
        val roms = ArrayList<RomEntry>()
        val readable = scan(tree, DocumentsContract.getTreeDocumentId(tree), 0, newCursors, roms)
        closeCursors()
        cursors = newCursors
        newCursors.forEach { it.registerContentObserver(observer) }
        onChange(if (readable) roms.sortedBy { it.name.lowercase() } else null)
    }

    fun stop() {
        handler.removeCallbacks(refresh)
        closeCursors()
    }

    private fun scan(tree: Uri, documentId: String, depth: Int, cursors: MutableList<Cursor>, roms: MutableList<RomEntry>): Boolean {
        val cursor = RomFolder.queryChildren(context, tree, documentId) ?: return false
        cursors.add(cursor)
        RomFolder.entries(cursor).forEach { entry ->
            if (!entry.isDirectory) {
                roms.add(entry)
            } else if (depth < MAX_DEPTH && cursors.size < MAX_FOLDERS) {
                scan(tree, entry.documentId, depth + 1, cursors, roms)
            }
        }
        return true
    }

    private fun closeCursors() {
        cursors.forEach {
            it.unregisterContentObserver(observer)
            it.close()
        }
        cursors = emptyList()
    }
}
