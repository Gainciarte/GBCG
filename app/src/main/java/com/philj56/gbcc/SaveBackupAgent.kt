/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onFullBackup(data: FullBackupDataOutput?) {
        val zipfile = filesDir.resolve("saves.zip")
        val zip = ZipOutputStream(FileOutputStream(zipfile))
        zip.setMethod(ZipOutputStream.DEFLATED)
        zip.setLevel(9)
        filesDir.resolve("saves").walk().forEach { file ->
            if (file.extension == "sav") {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.close()
        fullBackupFile(zipfile, data)

        // Backup shared prefs
        val prefs = filesDir.resolve("../shared_prefs")
        if (prefs.exists()) {
            prefs.listFiles()?.forEach {
                fullBackupFile(it, data)
            }
        }

        // Backup per-game configs & cheats
        val config = filesDir.resolve("config")
        if (config.exists()) {
            config.listFiles()?.forEach {
                fullBackupFile(it, data)
            }
        }
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor?,
        size: Long,
        destination: File?,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        if (data != null && destination != null) {
            when(type) {
                TYPE_DIRECTORY -> destination.mkdirs()
                TYPE_FILE -> {
                    destination.parentFile?.mkdirs()
                    // The restore stream carries every file in turn, so it must not be
                    // closed here, and exactly `size` bytes belong to this file (a single
                    // read() can return fewer)
                    val input = FileInputStream(data.fileDescriptor)
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var remaining = size
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count <= 0) {
                                break
                            }
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                }
            }

            if (destination.extension == "zip") {
                destination.inputStream().use { input ->
                    ZipInputStream(input).use { zip ->
                        val saveDir = filesDir.resolve("saves")
                        saveDir.mkdirs()

                        var entry = zip.nextEntry
                        while (entry != null) {
                            // Only the file name, so an entry can't point outside the folder
                            val file = saveDir.resolve(File(entry.name).name)
                            file.outputStream().use { zip.copyTo(it) }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            }
        }
    }
}