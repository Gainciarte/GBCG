package com.philj56.gbcc.main

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val PREF_GROUPS = "rom_groups"
private const val PREF_SELECTED = "rom_group_selected"

/** A group the user made. Games are kept by file name, like their saves. */
data class RomGroup(val name: String, val roms: MutableSet<String> = mutableSetOf())

/*
 * The groups the list can show: the automatic ones (all games, and Game Boy Color and
 * Game Boy games by extension) and the ones the user creates.
 */
object RomGroups {
    const val ALL = "all"
    const val GBC = "gbc"
    const val GB = "gb"
    private const val CUSTOM_PREFIX = "group:"

    fun customKey(name: String) = CUSTOM_PREFIX + name
    fun customName(key: String): String? = key.takeIf { it.startsWith(CUSTOM_PREFIX) }?.removePrefix(CUSTOM_PREFIX)

    fun load(context: Context): MutableList<RomGroup> {
        val json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_GROUPS, null)
            ?: return mutableListOf()
        return try {
            val array = JSONArray(json)
            MutableList(array.length()) { i ->
                val group = array.getJSONObject(i)
                val roms = group.getJSONArray("roms")
                RomGroup(group.getString("name"), MutableList(roms.length()) { roms.getString(it) }.toMutableSet())
            }
        } catch (e: JSONException) {
            mutableListOf()
        }
    }

    fun save(context: Context, groups: List<RomGroup>) {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().put("name", group.name).put("roms", JSONArray(group.roms.sorted())))
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_GROUPS, array.toString())
        }
    }

    fun selected(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SELECTED, ALL) ?: ALL

    fun select(context: Context, key: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_SELECTED, key)
        }
    }

    /** The ROMs a group shows; null if the group doesn't exist (any more). */
    fun filter(key: String, roms: List<RomEntry>, groups: List<RomGroup>): List<RomEntry>? = when (key) {
        ALL -> roms
        GBC -> roms.filter { it.isGbc }
        GB -> roms.filter { !it.isGbc }
        else -> groups.firstOrNull { it.name == customName(key) }?.let { group ->
            roms.filter { it.name in group.roms }
        }
    }
}
