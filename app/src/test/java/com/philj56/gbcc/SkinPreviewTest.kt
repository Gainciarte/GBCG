package com.philj56.gbcc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/*
 * Not a test as such: draws the skins as they look on a phone (a 1080 x 2400 screen, with
 * the controls where the portrait layout puts them on a Redmi Note 13), with Android's
 * own drawing code, into app/build/skin-previews. Run it with
 * gradlew testDebugUnitTest --tests "*SkinPreviewTest*" to check the artwork without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h833dp-port-440dpi")
class SkinPreviewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File("build/skin-previews").apply { mkdirs() }

    // Measured on a screenshot of the game screen (pixels, below the status bar strip)
    private var width = 1080
    private var height = 2290
    private fun portraitParts(dmg: Boolean) = SkinParts(
        screen = RectF(17f, 22f, 1063f, 974f),
        dpad = RectF(44f, 1254f, 470f, 1680f),
        a = RectF(816f, 1336f, 991f, 1511f),
        b = RectF(596f, 1424f, 772f, 1599f),
        select = RectF(343f, 2054f, 517f, 2112f),
        start = RectF(563f, 2054f, 737f, 2112f),
        pillRotation = if (dmg) -25f else 0f,
        avoid = listOf(RectF(462f, 1090f, 600f, 1144f)),
    )

    // The landscape layout, measured on a screenshot: screen in the middle, d-pad and
    // Select/Start (one above the other) on the left, A and B on the right
    private fun landscapeParts(dmg: Boolean) = SkinParts(
        screen = RectF(654f, 0f, 1860f, 1080f),
        dpad = RectF(132f, 288f, 630f, 786f),
        a = RectF(2180f, 322f, 2356f, 498f),
        b = RectF(1960f, 410f, 2136f, 586f),
        select = RectF(432f, 960f, 606f, 1018f),
        start = RectF(432f, 828f, 606f, 886f),
        pillRotation = if (dmg) -25f else 0f,
        avoid = emptyList(),
    )

    @Test
    fun render() {
        render("gbc_teal_real", gbc = true, colour = "Teal", translucent = false, realistic = true)
        render("gbc_berry_real", gbc = true, colour = "Berry", translucent = false, realistic = true)
        render("gbc_teal_flat", gbc = true, colour = "Teal", translucent = false, realistic = false)
        render("gbc_grape_translucent", gbc = true, colour = "Grape", translucent = true, realistic = true)
        render("gbc_white_translucent", gbc = true, colour = "White", translucent = true, realistic = true)
        render("gbc_kiwi_real", gbc = true, colour = "Kiwi", translucent = false, realistic = true)
        render("gbc_dandelion_real", gbc = true, colour = "Dandelion", translucent = false, realistic = true)
        render("dmg_light_real", gbc = false, colour = "Teal", translucent = false, realistic = true)
        render("dmg_dark_real", gbc = false, colour = "Teal", translucent = false, realistic = true, dark = true)
        width = 2400
        height = 1080
        render("land_gbc_grape_translucent", gbc = true, colour = "Grape", translucent = true, realistic = true)
        render("land_dmg_light_real", gbc = false, colour = "Teal", translucent = false, realistic = true)
        render("land_gbc_berry_real", gbc = true, colour = "Berry", translucent = false, realistic = true)
        width = 1080
        height = 2290
    }

    private fun render(
        name: String, gbc: Boolean, colour: String, translucent: Boolean, realistic: Boolean,
        dark: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            putString("color", colour)
            putBoolean("translucent", translucent)
            putBoolean("realistic_buttons", realistic)
            putString("dmg_color", if (dark) "Dark" else "Light")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = if (width > height) landscapeParts(!gbc) else portraitParts(!gbc)

        val skin = SkinDrawable(context, SkinLook.from(prefs, gbc))
        skin.setBounds(0, 0, width, height)
        skin.parts = p
        skin.draw(canvas)

        // The screen with its border, as the layout draws them
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (gbc) 0xFF262E31.toInt() else if (dark) 0xFF484649.toInt() else 0xFF918E92.toInt()
        canvas.drawRoundRect(p.screen, 40f, 40f, paint)
        paint.color = 0xFF9BA37F.toInt()
        canvas.drawRect(RectF(p.screen).apply { inset(44f, 44f) }, paint)

        // The buttons
        val art = ButtonArt(context, prefs, gbc)
        fun put(d: android.graphics.drawable.Drawable, r: RectF, rotation: Float = 0f) {
            canvas.save()
            canvas.rotate(rotation, r.centerX(), r.centerY())
            d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            d.draw(canvas)
            canvas.restore()
        }
        put(art.a(), p.a)
        put(art.b(), p.b)
        put(art.startSelect(), p.select, p.pillRotation)
        put(art.startSelect(), p.start, p.pillRotation)
        val dpad = context.getDrawable(art.dpad)!!.mutate()
        art.dpadTint?.let { dpad.setTint(context.getColor(it)) }
        put(dpad, p.dpad)

        // The black corners the layout puts over the skin
        paint.color = Color.BLACK
        val d = context.resources.displayMetrics.density
        val outside = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            val body = Path()
            if (gbc && height > width) {
                body.addRoundRect(RectF(0f, 0f, width.toFloat(), height - 32 * d), floatArrayOf(16 * d, 16 * d, 16 * d, 16 * d, 0f, 0f, 0f, 0f), Path.Direction.CW)
                body.addOval(RectF(0f, height - 64 * d, width.toFloat(), height.toFloat()), Path.Direction.CW)
                body.addRect(0f, height - 64 * d, width.toFloat(), height - 32 * d, Path.Direction.CW)
            } else {
                val br = if (height > width) 64 * d else 16 * d
                body.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    floatArrayOf(16 * d, 16 * d, 16 * d, 16 * d, br, br, 16 * d, 16 * d), Path.Direction.CW)
            }
            op(body, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(outside, paint)

        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
