package com.philj56.gbcc.main

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject

private const val PREF_LAST_PLAYED = "rom_last_played"

/** When each game was last played, by file name (like its saves), to list recent games first. */
object RomHistory {
    fun lastPlayed(context: Context): Map<String, Long> {
        val json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_LAST_PLAYED, null)
            ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    fun markPlayed(context: Context, romName: String) {
        val times = lastPlayed(context).toMutableMap()
        times[romName] = System.currentTimeMillis()
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_LAST_PLAYED, JSONObject(times as Map<*, *>).toString())
        }
    }

    /** Played games first, most recent at the top; the rest keep their order. */
    fun sortRecentFirst(roms: List<RomEntry>, lastPlayed: Map<String, Long>): List<RomEntry> =
        roms.sortedByDescending { lastPlayed[it.name] ?: 0L }
}
