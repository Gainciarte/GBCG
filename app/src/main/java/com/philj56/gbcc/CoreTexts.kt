package com.philj56.gbcc

import android.content.Context

/*
 * The texts the emulator core draws on the game screen (its menu, in cpp/core/menu.c,
 * and its messages), in English as the core writes them, with the string resource that
 * translates each one. GLActivity hands them to the core in the app's language.
 */
object CoreTexts {
    private val texts = listOf(
        // Menu labels
        "Save state:" to R.string.core_menu_save_state,
        "Load state:" to R.string.core_menu_load_state,
        "Autosave:" to R.string.core_menu_autosave,
        "Frameblend:" to R.string.core_menu_frameblend,
        "Link cable:" to R.string.core_menu_link_cable,
        "Shader:" to R.string.core_menu_shader,
        "Turbo mult:" to R.string.core_menu_turbo,
        "Vsync:" to R.string.core_menu_vsync,
        "Interlacing:" to R.string.core_menu_interlacing,
        "Cheats:" to R.string.core_menu_cheats,
        "Palette:" to R.string.core_menu_palette,
        "Reset:" to R.string.core_menu_reset,
        // Menu values
        "On" to R.string.core_on,
        "Off" to R.string.core_off,
        "Disconnected" to R.string.core_disconnected,
        "Loopback" to R.string.core_loopback,
        "Printer" to R.string.core_printer,
        "Unlimited" to R.string.core_unlimited,
        "Press A" to R.string.core_reset_press,
        "Press A again" to R.string.core_reset_confirm,
        // Shader names, from the core's window.c
        "Colour Correct" to R.string.core_shader_colour_correct,
        "Subpixel" to R.string.settings_shader_subpixel,
        "Dot Matrix" to R.string.settings_shader_dot_matrix,
        "Nothing" to R.string.settings_shader_nothing,
        // Palette names, from the core's palettes.c
        "Default" to R.string.palette_default,
        "Monochrome" to R.string.palette_monochrome,
        "Brown" to R.string.palette_brown,
        "Red" to R.string.palette_red,
        "Dark-Brown" to R.string.palette_dark_brown,
        "Pastel" to R.string.palette_pastel,
        "Orange" to R.string.palette_orange,
        "Yellow" to R.string.palette_yellow,
        "Blue" to R.string.palette_blue,
        "Dark-Blue" to R.string.palette_dark_blue,
        "Grey" to R.string.palette_grey,
        "Green" to R.string.palette_green,
        "Dark-Green" to R.string.palette_dark_green,
        "Invert" to R.string.palette_invert,
        // Messages, from the core's input.c and save.c ("%d" stands for a number)
        "Printer connected" to R.string.core_msg_printer_connected,
        "Printer disconnected" to R.string.core_msg_printer_disconnected,
        "Frame blending enabled" to R.string.core_msg_frameblend_on,
        "Frame blending disabled" to R.string.core_msg_frameblend_off,
        "Vsync enabled" to R.string.core_msg_vsync_on,
        "Vsync disabled" to R.string.core_msg_vsync_off,
        "Link cable connected" to R.string.core_msg_link_on,
        "Link cable disconnected" to R.string.core_msg_link_off,
        "Autosave enabled" to R.string.core_msg_autosave_on,
        "Autosave disabled" to R.string.core_msg_autosave_off,
        "Background playback enabled" to R.string.core_msg_background_play_on,
        "Background playback disabled" to R.string.core_msg_background_play_off,
        "Interlacing enabled" to R.string.core_msg_interlacing_on,
        "Interlacing disabled" to R.string.core_msg_interlacing_off,
        "Cheats enabled" to R.string.core_msg_cheats_on,
        "Cheats disabled" to R.string.core_msg_cheats_off,
        "Saved state %d" to R.string.core_msg_state_saved,
        "State %d not found" to R.string.core_msg_state_not_found,
        "Loaded state %d" to R.string.core_msg_state_loaded,
    )

    val keys: Array<String> get() = texts.map { it.first }.toTypedArray()

    /** The translations, in Latin-1 (the core font's encoding); other characters become "?". */
    fun values(context: Context): Array<ByteArray> =
        texts.map { context.getString(it.second).toByteArray(Charsets.ISO_8859_1) }.toTypedArray()
}
