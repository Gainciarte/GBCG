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

// Extensions are matched ignoring case, so that e.g. "Tetris.GB" is listed too
val ROM_REGEX = Regex(".*\\.gbc?", RegexOption.IGNORE_CASE)

/** A ROM or a subfolder inside the ROM folder. */
data class RomEntry(val name: String, val documentId: String, val isDirectory: Boolean) {
    /** The name without extension, which is also the name of the game's saves and config. */
    val baseName get() = if (isDirectory) name else File(name).nameWithoutExtension
    val extension get() = if (isDirectory) "" else File(name).extension
}

/** A step in the path from the ROM folder to the folder being shown. */
data class PathEntry(val name: String, val documentId: String)

/*
 * The folder the user keeps their ROMs in, chosen once with the system folder picker
 * (Storage Access Framework). The ROMs are read from there and never copied into the app.
 */
object RomFolder {
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

    fun root(context: Context, tree: Uri): PathEntry {
        val documentId = DocumentsContract.getTreeDocumentId(tree)
        val name = try {
            context.contentResolver.query(
                DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            // The folder was deleted: listing it will fail too, and ask for a new one
            null
        }
        return PathEntry(name ?: documentId.substringAfterLast(':'), documentId)
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

    /** The subfolders and ROMs in a cursor from queryChildren, folders first. */
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
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}

/*
 * Keeps a folder's listing open and calls onChange (debounced) whenever files are added,
 * removed or renamed in it. The documents provider only watches a folder while a cursor
 * on it is open, so the cursor stays open until stop().
 */
class RomFolderWatcher(private val context: Context, private val onChange: (List<RomEntry>?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var cursor: Cursor? = null
    private var tree: Uri? = null
    private var documentId: String? = null
    private val refresh = Runnable { reload() }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(refresh)
            handler.postDelayed(refresh, REFRESH_DELAY_MS)
        }
    }

    fun watch(tree: Uri, documentId: String) {
        this.tree = tree
        this.documentId = documentId
        reload()
    }

    fun reload() {
        val tree = tree ?: return
        val documentId = documentId ?: return
        val newCursor = RomFolder.queryChildren(context, tree, documentId)
        closeCursor()
        cursor = newCursor
        newCursor?.registerContentObserver(observer)
        onChange(newCursor?.let { RomFolder.entries(it) })
    }

    fun stop() {
        handler.removeCallbacks(refresh)
        closeCursor()
    }

    private fun closeCursor() {
        cursor?.let {
            it.unregisterContentObserver(observer)
            it.close()
        }
        cursor = null
    }
}
