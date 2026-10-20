package com.philj56.gbcc

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat

/*
 * The console body behind the game screen and buttons. Each skin colour is a plain colour
 * under a fine plastic grain; the translucent ones (purple and clear) instead tint a
 * circuit board, as if it were seen through the plastic. The artwork comes from
 * sources/make_skin.py.
 */
object Skin {
    // Translucent plastics: their tint over the circuit board (ARGB)
    private const val ATOMIC_PURPLE_TINT = 0xB05B2E91.toInt()
    private const val CLEAR_TINT = 0x6EDCE3E8

    fun background(context: Context, prefs: SharedPreferences, gbc: Boolean): Drawable {
        val layers = ArrayList<Drawable>()
        if (gbc) {
            when (val colour = prefs.getString("color", "Teal")) {
                "AtomicPurple" -> {
                    layers.add(tiled(context, R.drawable.skin_pcb))
                    layers.add(ColorDrawable(ATOMIC_PURPLE_TINT))
                }
                "Clear" -> {
                    layers.add(tiled(context, R.drawable.skin_pcb))
                    layers.add(ColorDrawable(CLEAR_TINT))
                }
                else -> layers.add(ColorDrawable(ContextCompat.getColor(context, gbcColour(colour))))
            }
        } else {
            val dark = prefs.getString("dmg_color", "Light") == "Dark"
            layers.add(ColorDrawable(ContextCompat.getColor(
                context, if (dark) R.color.dmgDarkBackground else R.color.dmgLightBackground
            )))
        }
        layers.add(tiled(context, R.drawable.skin_texture))
        return LayerDrawable(layers.toTypedArray())
    }

    private fun gbcColour(colour: String?) = when (colour) {
        "Berry" -> R.color.gbcBerry
        "Dandelion" -> R.color.gbcDandelion
        "Grape" -> R.color.gbcGrape
        "Kiwi" -> R.color.gbcKiwi
        else -> R.color.gbcTeal
    }

    /** A bitmap repeated over the whole area, at its own pixel size (the PNGs are nodpi). */
    private fun tiled(context: Context, res: Int): Drawable =
        BitmapDrawable(context.resources, BitmapFactory.decodeResource(context.resources, res)).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
}
