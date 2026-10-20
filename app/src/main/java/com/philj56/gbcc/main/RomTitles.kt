package com.philj56.gbcc.main

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject

private const val PREF_TITLES = "rom_titles"

/*
 * Names the user gave to games, shown instead of the file name. The ROM file itself is
 * never renamed, so its saves, groups and other emulators sharing the folder are unaffected.
 */
object RomTitles {
    fun all(context: Context): Map<String, String> {
        val json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_TITLES, null)
            ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    /** The name to show for a ROM file name. */
    fun title(context: Context, romName: String, titles: Map<String, String> = all(context)): String =
        titles[romName] ?: RomEntry(romName, "", false).baseName

    /** Sets the name shown for a game; a blank name, or the file's own name, removes it. */
    fun set(context: Context, romName: String, title: String) {
        val titles = all(context).toMutableMap()
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed == RomEntry(romName, "", false).baseName) {
            titles.remove(romName)
        } else {
            titles[romName] = trimmed
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_TITLES, JSONObject(titles as Map<*, *>).toString())
        }
    }
}
