package com.philj56.gbcc

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.content.ContextCompat

/*
 * The artwork of the on-screen buttons, for each skin: the flat originals (ic_button_*,
 * vectors), or the realistic ones (real_*, from sources/make_skin.py) when "Realistic
 * buttons" is on. As on the real consoles, the GBC's realistic buttons are a different
 * colour on each shell, A and B have their letters engraved and the d-pad has arrows;
 * the DMG's are plain, with grooves on the d-pad. The d-pad's details are a layer over
 * the plain cross, which moves with the press as the flat highlight does.
 */
class ButtonArt(private val context: Context, prefs: SharedPreferences, gbc: Boolean) {
    private val realistic = prefs.getBoolean("realistic_buttons", false)
    private val dark = !gbc && prefs.getString("dmg_color", "Light") == "Dark"
    private val dmg = !gbc && !dark

    private class Real(
        val a: Int, val aPressed: Int, val b: Int, val bPressed: Int,
        val pill: Int, val pillPressed: Int, val dpad: Int
    )

    private val real = when {
        dark -> Real(R.drawable.real_button_ab_dmg_dark, R.drawable.real_button_ab_dmg_dark_pressed,
            R.drawable.real_button_ab_dmg_dark, R.drawable.real_button_ab_dmg_dark_pressed,
            R.drawable.real_button_startselect_dmg_dark, R.drawable.real_button_startselect_dmg_dark_pressed,
            R.drawable.real_dpad_dmg_dark)
        dmg -> Real(R.drawable.real_button_ab_dmg, R.drawable.real_button_ab_dmg_pressed,
            R.drawable.real_button_ab_dmg, R.drawable.real_button_ab_dmg_pressed,
            R.drawable.real_button_startselect_dmg, R.drawable.real_button_startselect_dmg_pressed,
            R.drawable.real_dpad_dmg)
        else -> when (prefs.getString("color", "Teal")) {
            "Berry" -> Real(R.drawable.real_button_a_berry, R.drawable.real_button_a_berry_pressed,
                R.drawable.real_button_b_berry, R.drawable.real_button_b_berry_pressed,
                R.drawable.real_button_startselect_berry, R.drawable.real_button_startselect_berry_pressed,
                R.drawable.real_dpad_berry)
            "Dandelion" -> Real(R.drawable.real_button_a_dandelion, R.drawable.real_button_a_dandelion_pressed,
                R.drawable.real_button_b_dandelion, R.drawable.real_button_b_dandelion_pressed,
                R.drawable.real_button_startselect_dandelion, R.drawable.real_button_startselect_dandelion_pressed,
                R.drawable.real_dpad_dandelion)
            "Grape" -> Real(R.drawable.real_button_a_grape, R.drawable.real_button_a_grape_pressed,
                R.drawable.real_button_b_grape, R.drawable.real_button_b_grape_pressed,
                R.drawable.real_button_startselect_grape, R.drawable.real_button_startselect_grape_pressed,
                R.drawable.real_dpad_grape)
            "Kiwi" -> Real(R.drawable.real_button_a_kiwi, R.drawable.real_button_a_kiwi_pressed,
                R.drawable.real_button_b_kiwi, R.drawable.real_button_b_kiwi_pressed,
                R.drawable.real_button_startselect_kiwi, R.drawable.real_button_startselect_kiwi_pressed,
                R.drawable.real_dpad_kiwi)
            "White" -> Real(R.drawable.real_button_a_white, R.drawable.real_button_a_white_pressed,
                R.drawable.real_button_b_white, R.drawable.real_button_b_white_pressed,
                R.drawable.real_button_startselect_white, R.drawable.real_button_startselect_white_pressed,
                R.drawable.real_dpad_white)
            else -> Real(R.drawable.real_button_a_teal, R.drawable.real_button_a_teal_pressed,
                R.drawable.real_button_b_teal, R.drawable.real_button_b_teal_pressed,
                R.drawable.real_button_startselect_teal, R.drawable.real_button_startselect_teal_pressed,
                R.drawable.real_dpad_teal)
        }
    }

    private fun flat(gbcArt: Int, dmgArt: Int, darkArt: Int) = when {
        dark -> darkArt
        dmg -> dmgArt
        else -> gbcArt
    }

    private fun pressable(normal: Int, pressed: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ContextCompat.getDrawable(context, pressed))
        addState(intArrayOf(), ContextCompat.getDrawable(context, normal))
    }

    private fun drawable(id: Int) = ContextCompat.getDrawable(context, id)!!

    // A new drawable on each call: each button needs its own, for its pressed state
    fun a(): Drawable = if (realistic) pressable(real.a, real.aPressed) else drawable(
        flat(R.drawable.ic_button_ab_selector, R.drawable.ic_button_ab_dmg_selector,
            R.drawable.ic_button_ab_dmg_dark_selector))

    fun b(): Drawable = if (realistic) pressable(real.b, real.bPressed) else drawable(
        flat(R.drawable.ic_button_ab_selector, R.drawable.ic_button_ab_dmg_selector,
            R.drawable.ic_button_ab_dmg_dark_selector))

    fun startSelect(): Drawable = if (realistic) pressable(real.pill, real.pillPressed) else drawable(
        flat(R.drawable.ic_button_startselect_selector, R.drawable.ic_button_startselect_dmg_selector,
            R.drawable.ic_button_startselect_dmg_dark_selector))

    val dpad = if (realistic) real.dpad else R.drawable.ic_button_dpad_background

    /** The flat d-pad of the dark DMG is the usual one, tinted with this colour. */
    val dpadTint: Int? = if (!realistic && dark) R.color.dmgDarkDpad else null

    /** The details on the realistic d-pad's face (the GBC's arrows and dimple, the DMG's grooves
     * and dome): a layer of their own, so they move with the face when it's pressed. */
    private val details = when {
        !realistic -> null
        dark || dmg -> R.drawable.real_dpad_details_dmg
        else -> R.drawable.real_dpad_details_gbc
    }

    private val over = HashMap<Int, Drawable>()

    /** What goes over the d-pad while no direction is pressed: the same thin highlight for
     * both styles (the realistic cross has the flat one's shape), and the realistic one's
     * details. */
    fun dpadReleased(): Drawable = dpadPressed(0)

    /** What goes over the d-pad for the directions pressed (1 up, 2 down, 4 left, 8 right):
     * the highlight shifts towards them, in both styles, and the details with it. */
    fun dpadPressed(state: Int): Drawable = over.getOrPut(state) {
        val highlight = drawable(when (state) {
            1 -> R.drawable.ic_button_dpad_highlight_pressed_up
            2 -> R.drawable.ic_button_dpad_highlight_pressed_down
            4 -> R.drawable.ic_button_dpad_highlight_pressed_left
            5 -> R.drawable.ic_button_dpad_highlight_pressed_up_left
            6 -> R.drawable.ic_button_dpad_highlight_pressed_down_left
            8 -> R.drawable.ic_button_dpad_highlight_pressed_right
            9 -> R.drawable.ic_button_dpad_highlight_pressed_up_right
            10 -> R.drawable.ic_button_dpad_highlight_pressed_down_right
            else -> R.drawable.ic_button_dpad_highlight
        })
        val d = details ?: return@getOrPut highlight
        // The flat highlight moves 2 % of the d-pad's size; the details follow it
        val dx = (if (state and 8 != 0) 1 else 0) - (if (state and 4 != 0) 1 else 0)
        val dy = (if (state and 2 != 0) 1 else 0) - (if (state and 1 != 0) 1 else 0)
        LayerDrawable(arrayOf(ShiftedDrawable(drawable(d), dx * 0.02f, dy * 0.02f), highlight))
    }
}

/** Draws another drawable moved by a fraction of its size. */
private class ShiftedDrawable(private val inner: Drawable, private val fx: Float, private val fy: Float) : Drawable() {
    override fun onBoundsChange(bounds: Rect) {
        inner.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.width() * fx, bounds.height() * fy)
        inner.draw(canvas)
        canvas.restore()
    }

    override fun getIntrinsicWidth() = inner.intrinsicWidth

    override fun getIntrinsicHeight() = inner.intrinsicHeight

    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
