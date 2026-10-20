package com.philj56.gbcc

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The console body behind the game screen and buttons, drawn to look like the real
 * thing: the colour of the plastic (measured on photos of real consoles) with its grain,
 * the gaps around the buttons, the speaker grille (the GBC's checkerboard of holes and
 * dimples, the DMG's six slots), the labels (embossed under the GBC's Select and Start,
 * printed in blue on the DMG) and, for translucent plastic, the inside of the console:
 * the rubber membranes under the buttons, the screw posts, the circuit board and the
 * speaker, blurred and tinted by the plastic.
 *
 * Everything is placed around where the buttons actually are (they can be moved and
 * resized), so the activities pass in their positions with SkinParts.measure() after each
 * layout. The buttons' own artwork comes from sources/make_skin.py.
 */

/** The colour and finish of the body, from the settings. */
class SkinLook(
    val gbc: Boolean, val body: Int, val plastic: Int, val translucent: Boolean, val dark: Boolean,
    val clear: Boolean = false,  // the colourless translucent plastic
) {
    companion object {
        // name: (opaque body, tint of the translucent plastic), from photos of real consoles
        private val GBC_COLOURS = mapOf(
            "Berry" to (0xFFC81E5A.toInt() to 0xFFE0558A.toInt()),
            "Dandelion" to (0xFFF0C62A.toInt() to 0xFFF6D45A.toInt()),
            "Grape" to (0xFF5753BE.toInt() to 0xFF9784CB.toInt()),  // translucent: Atomic Purple
            "Kiwi" to (0xFF84DE28.toInt() to 0xFFA2E35E.toInt()),
            "Teal" to (0xFF0A90A5.toInt() to 0xFF3CB5C5.toInt()),
            "White" to (0xFFEAE8E2.toInt() to 0xFFE2E8EB.toInt()),  // translucent: Clear
        )
        private const val DMG_LIGHT = 0xFFD8D1C1.toInt()
        private const val DMG_DARK = 0xFF161616.toInt()

        fun from(prefs: SharedPreferences, gbc: Boolean): SkinLook {
            if (!gbc) {
                val dark = prefs.getString("dmg_color", "Light") == "Dark"
                val body = if (dark) DMG_DARK else DMG_LIGHT
                return SkinLook(false, body, body, false, dark)
            }
            val colour = prefs.getString("color", "Teal")
            val (body, plastic) = GBC_COLOURS[colour] ?: GBC_COLOURS.getValue("Teal")
            val translucent = prefs.getBoolean("translucent", false)
            return SkinLook(true, body, plastic, translucent, false, translucent && colour == "White")
        }

        /** The translucent colours used to be colours of their own; now they're a switch. */
        fun migrate(prefs: SharedPreferences) {
            val colour = when (prefs.getString("color", null)) {
                "AtomicPurple" -> "Grape"
                "Clear" -> "White"
                else -> return
            }
            prefs.edit {
                putString("color", colour)
                putBoolean("translucent", true)
            }
        }
    }
}

/** Where the controls are, in the coordinates of the view the skin is the background of. */
class SkinParts(
    val screen: RectF,        // the screen with its border
    val dpad: RectF,          // the cross
    val a: RectF,             // the buttons' circles
    val b: RectF,
    val select: RectF,        // the pills, before rotation
    val start: RectF,
    val pillRotation: Float,  // degrees, around each pill's centre
    val avoid: List<RectF>,   // anything else the grille and the labels must stay clear of
) {
    companion object {
        fun measure(
            root: View, screen: List<View>, dpad: View, a: View, b: View,
            select: View, start: View, avoid: List<View>
        ): SkinParts {
            val origin = IntArray(2).also { root.getLocationInWindow(it) }
            fun centre(v: View): PointF {
                val p = IntArray(2).also { (v.parent as View).getLocationInWindow(it) }
                return PointF(
                    p[0] - origin[0] + v.left + v.width / 2f + v.translationX,
                    p[1] - origin[1] + v.top + v.height / 2f + v.translationY
                )
            }
            fun box(v: View, w: Float, h: Float): RectF {
                val c = centre(v)
                return RectF(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
            }
            // The images fill their views as fitCenter does: squares for the buttons and
            // the d-pad, 3:1 for the pills
            fun round(v: View) = min(v.width, v.height) * v.scaleX
            fun pill(v: View) = min(v.width.toFloat(), v.height * 3f) * v.scaleX
            fun bounds(v: View) = box(v, v.width * v.scaleX, v.height * v.scaleY)

            val screenRect = RectF(bounds(screen.first()))
            screen.drop(1).forEach { screenRect.union(bounds(it)) }
            return SkinParts(
                screenRect,
                box(dpad, round(dpad), round(dpad)),
                box(a, round(a), round(a)),
                box(b, round(b), round(b)),
                box(select, pill(select), pill(select) / 3),
                box(start, pill(start), pill(start) / 3),
                start.rotation,
                avoid.filter { it.visibility == View.VISIBLE }.map { bounds(it) }
            )
        }
    }
}

class SkinDrawable(context: Context, private val look: SkinLook) : Drawable() {
    private companion object {
        // Colours of the inside of a real console
        const val PCB_GREEN = 0xFF2C664B.toInt()
        const val PCB_TRACE = 0xFF3E8662.toInt()
        const val PCB_PAD = 0xFFC9CCC6.toInt()
        const val RUBBER = 0xFF9AD0DA.toInt()
        const val RUBBER_EDGE = 0xFF6FAEBC.toInt()
        const val FROST = 0xFFE8EDED.toInt()
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Set after each layout; the body is drawn plain until then. */
    var parts: SkinParts? = null
        set(value) {
            field = value
            geometry = null
            invalidateSelf()
        }

    private val texture = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        val res = if (look.gbc) R.drawable.skin_texture else R.drawable.skin_texture_dmg
        val bitmap = BitmapFactory.decodeResource(context.resources, res)
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** What depends on the layout, worked out once per layout. */
    private class Geometry(
        val speaker: Speaker?,
        val slots: Slots?,
        val screws: List<PointF>,
        val interior: Bitmap?,
    )
    private class Speaker(val x: Float, val y: Float, val r: Float)
    private class Slots(val x: Float, val y: Float, val length: Float, val spacing: Float, val width: Float)
    private var geometry: Geometry? = null

    override fun onBoundsChange(bounds: Rect) {
        geometry = null
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val p = parts
        val g = if (p != null && w > 0 && h > 0) geometry ?: build(p, w, h).also { geometry = it } else null

        val interior = g?.interior
        if (interior != null) {
            canvas.drawBitmap(interior, null, RectF(0f, 0f, w, h), bitmapPaint)
            // Light scattered by the plastic: little by the clear one, a lot by frosted colours
            fill.color = if (look.clear) 0x30E6EEF2 else ColorUtils.setAlphaComponent(look.plastic, 0x60)
            canvas.drawRect(0f, 0f, w, h, fill)
        } else {
            fill.color = look.body
            canvas.drawRect(0f, 0f, w, h, fill)
        }
        canvas.drawRect(0f, 0f, w, h, texture)
        if (p == null || g == null) {
            return
        }
        if (!look.gbc) {
            drawDmgRecesses(canvas, p)
        }
        drawGaps(canvas, p)
        g.speaker?.let { drawGbcGrille(canvas, it) }
        g.slots?.let { drawDmgGrille(canvas, it, p.pillRotation) }
        if (look.gbc) drawGbcLabels(canvas, p) else drawDmgLabels(canvas, p)
    }

    private fun build(p: SkinParts, w: Float, h: Float): Geometry {
        val speaker = if (look.gbc) placeSpeaker(p, w, h) else null
        val slots = if (!look.gbc) placeSlots(p, w, h) else null
        val screws = if (look.translucent) placeScrews(p, w, h, speaker) else emptyList()
        val interior = if (look.translucent) renderInterior(p, w, h, speaker, screws) else null
        return Geometry(speaker, slots, screws, interior)
    }

    // ---- Shape of the body -------------------------------------------------------------

    private fun portrait(w: Float, h: Float) = h > w

    /** Whether a point is on the body, at least m inside its edge (its corners are rounded
     * as the layouts round them: black corner images over the skin). */
    private fun inside(x: Float, y: Float, m: Float, w: Float, h: Float): Boolean {
        if (x < m || y < m || x > w - m || y > h - m) {
            return false
        }
        fun corner(cx: Float, cy: Float, r: Float, inX: Boolean, inY: Boolean) =
            !(inX && inY) || hypot(x - cx, y - cy) <= r - m
        val t = dp(16f)
        if (!corner(t, t, t, (x < t), (y < t)) || !corner(w - t, t, t, (x > w - t), (y < t))) {
            return false
        }
        if (portrait(w, h) && look.gbc) {
            // The GBC's bottom edge is one wide curve
            val ry = dp(32f)
            if (y > h - ry) {
                val ex = (x - w / 2) / (w / 2 - m)
                val ey = (y - (h - ry)) / (ry - m)
                return ex * ex + ey * ey <= 1
            }
            return true
        }
        val bl = dp(16f)
        val br = if (portrait(w, h)) dp(64f) else dp(16f)  // the DMG's big bottom right corner
        return corner(bl, h - bl, bl, (x < bl), (y > h - bl)) &&
                corner(w - br, h - br, br, (x > w - br), (y > h - br))
    }

    private fun circleInside(x: Float, y: Float, r: Float, m: Float, w: Float, h: Float): Boolean {
        if (!inside(x, y, m, w, h)) return false
        for (k in 0 until 12) {
            val t = k * Math.PI.toFloat() / 6
            if (!inside(x + r * cos(t), y + r * sin(t), m, w, h)) return false
        }
        return true
    }

    private fun circleHits(x: Float, y: Float, r: Float, rect: RectF): Boolean {
        val nx = x.coerceIn(rect.left, rect.right)
        val ny = y.coerceIn(rect.top, rect.bottom)
        return hypot(x - nx, y - ny) < r
    }

    private fun blockers(p: SkinParts, margin: Float): List<RectF> =
        (listOf(p.screen, p.dpad, p.a, p.b, rotatedBounds(p.select, p.pillRotation),
            rotatedBounds(p.start, p.pillRotation)) + p.avoid).map { RectF(it).apply { inset(-margin, -margin) } }

    private fun rotatedBounds(r: RectF, degrees: Float): RectF {
        val t = Math.toRadians(degrees.toDouble())
        val c = kotlin.math.abs(cos(t)).toFloat()
        val s = kotlin.math.abs(sin(t)).toFloat()
        val hw = (r.width() * c + r.height() * s) / 2
        val hh = (r.width() * s + r.height() * c) / 2
        return RectF(r.centerX() - hw, r.centerY() - hh, r.centerX() + hw, r.centerY() + hh)
    }

    // ---- Speaker grille ----------------------------------------------------------------

    /** The GBC's grille goes below the A and B buttons, as big as the real one (about a
     * fifth of the width) if there's room, and smaller, or not at all, if there isn't. */
    private fun placeSpeaker(p: SkinParts, w: Float, h: Float): Speaker? {
        val blocked = blockers(p, dp(8f))
        var r = min(w, h) * 0.105f
        val ideal = PointF(p.a.centerX() - dp(6f), max(p.select.centerY(), p.b.bottom + r + dp(8f)) + dp(14f))
        val step = dp(4f)
        while (r >= dp(24f)) {
            var best: PointF? = null
            var bestDistance = Float.MAX_VALUE
            for (i in -20..20) for (j in -20..20) {
                val x = ideal.x + i * step
                val y = ideal.y + j * step
                val d = hypot(x - ideal.x, y - ideal.y)
                if (d >= bestDistance || !circleInside(x, y, r, dp(10f), w, h)) continue
                if (blocked.any { circleHits(x, y, r, it) }) continue
                best = PointF(x, y)
                bestDistance = d
            }
            if (best != null) return Speaker(best.x, best.y, r)
            r -= dp(3f)
        }
        return null
    }

    /** The DMG's six slots go in the bottom right corner, at right angles to the pills. */
    private fun placeSlots(p: SkinParts, w: Float, h: Float): Slots? {
        val blocked = blockers(p, dp(8f))
        val side = min(w, h)
        val t = Math.toRadians(p.pillRotation.toDouble())
        val ux = cos(t).toFloat()
        val uy = sin(t).toFloat()
        var scale = 1f
        val ideal = PointF(w - side * 0.24f, h - side * 0.16f)
        val step = dp(4f)
        while (scale >= 0.6f) {
            val length = side * 0.14f * scale
            val spacing = side * 0.046f * scale
            val width = side * 0.014f * scale
            var best: PointF? = null
            var bestDistance = Float.MAX_VALUE
            for (i in -25..25) for (j in -25..25) {
                val x = ideal.x + i * step
                val y = ideal.y + j * step
                val d = hypot(x - ideal.x, y - ideal.y)
                if (d >= bestDistance) continue
                if (slotPoints(x, y, ux, uy, length, spacing).all { pt ->
                        inside(pt.x, pt.y, dp(10f) + width, w, h) &&
                                blocked.none { circleHits(pt.x, pt.y, width, it) }
                    }) {
                    best = PointF(x, y)
                    bestDistance = d
                }
            }
            if (best != null) return Slots(best.x, best.y, length, spacing, width)
            scale -= 0.1f
        }
        return null
    }

    /** Points along each slot, to check that the grille fits. */
    private fun slotPoints(x: Float, y: Float, ux: Float, uy: Float, length: Float, spacing: Float): List<PointF> {
        val vx = -uy
        val vy = ux
        val points = ArrayList<PointF>()
        for (k in 0 until 6) {
            val ox = x + ux * (k - 2.5f) * spacing
            val oy = y + uy * (k - 2.5f) * spacing
            for (f in floatArrayOf(-0.5f, -0.25f, 0f, 0.25f, 0.5f)) {
                points.add(PointF(ox + vx * f * length, oy + vy * f * length))
            }
        }
        return points
    }

    private fun drawGbcGrille(canvas: Canvas, s: Speaker) {
        // An 8 x 8 grid cut to a circle; through-holes and blind dimples alternate
        val pitch = 2 * s.r / 8.1f
        val hole = pitch * 0.25f
        for (i in 0 until 8) for (j in 0 until 8) {
            val gx = i - 3.5f
            val gy = j - 3.5f
            if (gx * gx + gy * gy > 4.05f * 4.05f) continue
            val x = s.x + gx * pitch
            val y = s.y + gy * pitch
            if ((i + j) % 2 == 0) drawHole(canvas, x, y, hole) else drawDimple(canvas, x, y, hole)
        }
    }

    private fun drawHole(canvas: Canvas, x: Float, y: Float, r: Float) {
        // The far wall of the hole catches the light on its lower right
        fill.shader = null
        fill.color = 0x40FFFFFF
        canvas.drawCircle(x, y, r, fill)
        fill.color = 0xE0101010.toInt()
        canvas.drawCircle(x - r * 0.1f, y - r * 0.1f, r * 0.9f, fill)
        // Shadow just above the hole's rim
        stroke.shader = null
        stroke.color = 0x30000000
        stroke.strokeWidth = r * 0.25f
        canvas.drawArc(x - r * 1.1f, y - r * 1.1f, x + r * 1.1f, y + r * 1.1f, 180f, 90f, false, stroke)
    }

    private fun drawDimple(canvas: Canvas, x: Float, y: Float, r: Float) {
        // A shallow cup: dark on its upper left inside, lit on its lower right
        fill.shader = LinearGradient(
            x - r, y - r, x + r, y + r,
            intArrayOf(0x55000000, 0x00000000, 0x00000000, 0x50FFFFFF),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    private fun drawDmgGrille(canvas: Canvas, s: Slots, rotation: Float) {
        canvas.save()
        canvas.rotate(rotation, s.x, s.y)
        val half = s.length / 2
        stroke.strokeCap = Paint.Cap.ROUND
        for (k in 0 until 6) {
            val x = s.x + (k - 2.5f) * s.spacing
            // Lit lip on the right, dark groove, shadow on the left
            stroke.shader = null
            stroke.color = 0x50FFFFFF
            stroke.strokeWidth = s.width * 1.05f
            canvas.drawLine(x + s.width * 0.35f, s.y - half, x + s.width * 0.35f, s.y + half, stroke)
            stroke.color = 0x38000000
            stroke.strokeWidth = s.width * 1.1f
            canvas.drawLine(x - s.width * 0.25f, s.y - half, x - s.width * 0.25f, s.y + half, stroke)
            stroke.color = 0xD0161616.toInt()
            stroke.strokeWidth = s.width * 0.75f
            canvas.drawLine(x, s.y - half, x, s.y + half, stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT
        canvas.restore()
    }

    // ---- Gaps and recesses ---------------------------------------------------------------

    /** The dark gap between each control and the hole in the shell it sits in. */
    private fun drawGaps(canvas: Canvas, p: SkinParts) {
        fill.shader = null
        for ((grow, alpha) in listOf(dp(2.2f) to 0x30, dp(1.2f) to 0x60)) {
            fill.color = Color.argb(alpha, 0, 0, 0)
            for (r in listOf(p.a, p.b)) {
                canvas.drawCircle(r.centerX(), r.centerY(), r.width() / 2 + grow, fill)
            }
            val s = p.dpad.width()
            val lo = s / 3
            val rr = s * 0.1f + grow
            canvas.drawRoundRect(p.dpad.left + lo - grow, p.dpad.top - grow, p.dpad.right - lo + grow,
                p.dpad.bottom + grow, rr, rr, fill)
            canvas.drawRoundRect(p.dpad.left - grow, p.dpad.top + lo - grow, p.dpad.right + grow,
                p.dpad.bottom - lo + grow, rr, rr, fill)
            for (r in listOf(p.select, p.start)) {
                canvas.save()
                canvas.rotate(p.pillRotation, r.centerX(), r.centerY())
                val pr = r.height() / 2 + grow
                canvas.drawRoundRect(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow, pr, pr, fill)
                canvas.restore()
            }
        }
    }

    /** The DMG's sunken areas: a dish under the d-pad, a slanted capsule under A and B and a
     * well around each pill, plus the little arrows moulded around the d-pad. */
    private fun drawDmgRecesses(canvas: Canvas, p: SkinParts) {
        // A recess: a touch darker inside, its upper left wall in shade and its lower right
        // wall catching the light, both soft
        fun recess(left: Float, top: Float, right: Float, bottom: Float, radius: Float, angle: Float = 0f,
                   cx: Float = (left + right) / 2, cy: Float = (top + bottom) / 2) {
            canvas.save()
            canvas.rotate(angle, cx, cy)
            fill.shader = null
            fill.color = 0x0E000000
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)
            for ((width, alpha) in listOf(dp(4f) to 0x14, dp(2f) to 0x22, dp(1f) to 0x30)) {
                stroke.strokeWidth = width
                stroke.shader = LinearGradient(left, top, right, bottom,
                    Color.argb(alpha * 2, 0, 0, 0), Color.argb(alpha * 2, 255, 255, 255), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(left + width / 2, top + width / 2, right - width / 2, bottom - width / 2,
                    radius, radius, stroke)
            }
            stroke.shader = null
            canvas.restore()
        }
        // Dish under the d-pad
        val dr = p.dpad.width() * 0.62f
        val dx = p.dpad.centerX()
        val dy = p.dpad.centerY()
        recess(dx - dr, dy - dr, dx + dr, dy + dr, dr)
        // Capsule under A and B
        val angle = Math.toDegrees(atan2((p.a.centerY() - p.b.centerY()).toDouble(),
            (p.a.centerX() - p.b.centerX()).toDouble())).toFloat()
        val cx = (p.a.centerX() + p.b.centerX()) / 2
        val cy = (p.a.centerY() + p.b.centerY()) / 2
        val half = hypot(p.a.centerX() - p.b.centerX(), p.a.centerY() - p.b.centerY()) / 2
        val cr = max(p.a.width(), p.b.width()) / 2 + dp(9f)
        recess(cx - half - cr, cy - cr, cx + half + cr, cy + cr, cr, angle, cx, cy)
        // Wells around the pills
        for (r in listOf(p.select, p.start)) {
            val g = dp(4f)
            recess(r.left - g, r.top - g, r.right + g, r.bottom + g, r.height() / 2 + g, p.pillRotation,
                r.centerX(), r.centerY())
        }
        stroke.shader = null
        // Arrows around the d-pad, just outside the dish
        val t = dp(3.2f)
        for ((ax, ay, rot) in listOf(
            Triple(dx, dy - dr - t * 1.6f, 0f), Triple(dx + dr + t * 1.6f, dy, 90f),
            Triple(dx, dy + dr + t * 1.6f, 180f), Triple(dx - dr - t * 1.6f, dy, 270f))) {
            canvas.save()
            canvas.rotate(rot, ax, ay)
            val path = android.graphics.Path().apply {
                moveTo(ax, ay - t); lineTo(ax + t, ay + t * 0.7f); lineTo(ax - t, ay + t * 0.7f); close()
            }
            fill.color = 0x45FFFFFF
            canvas.save(); canvas.translate(-dp(0.5f), -dp(0.5f)); canvas.drawPath(path, fill); canvas.restore()
            fill.color = 0x30000000
            canvas.save(); canvas.translate(dp(0.5f), dp(0.5f)); canvas.drawPath(path, fill); canvas.restore()
            canvas.restore()
        }
    }

    // ---- Labels --------------------------------------------------------------------------

    /** The GBC's "SELECT" and "START" are moulded into the plastic below the pills. */
    private fun drawGbcLabels(canvas: Canvas, p: SkinParts) {
        text.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        text.textSize = dp(13f) * (p.select.width() / dp(64f)).coerceIn(0.7f, 1.4f)
        text.letterSpacing = 0.02f
        text.textScaleX = 1f
        for ((r, label) in listOf(p.select to "SELECT", p.start to "START")) {
            var y = r.bottom + text.textSize * 1.25f
            if (y + text.descent() > bounds.height() - dp(6f)) {
                y = r.top - text.textSize * 0.45f
            }
            embossed(canvas, label, r.centerX(), y)
        }
    }

    private fun embossed(canvas: Canvas, label: String, x: Float, y: Float) {
        val o = dp(0.7f)
        text.color = 0x70FFFFFF
        canvas.drawText(label, x - o, y - o, text)
        text.color = 0x50000000
        canvas.drawText(label, x + o, y + o, text)
        text.color = ColorUtils.setAlphaComponent(if (look.translucent) look.plastic else look.body, 0xE0)
        canvas.drawText(label, x, y, text)
    }

    /** The DMG's labels are printed in navy blue (light grey on the dark skin), in a bold,
     * wide face: "B" and "A" well below the buttons, "SELECT" and "START" below the pills,
     * turned like them. */
    private fun drawDmgLabels(canvas: Canvas, p: SkinParts) {
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        text.color = if (look.dark) 0xFF8D93B5.toInt() else 0xFF2A2D6A.toInt()
        text.letterSpacing = 0.12f
        text.textScaleX = 1.15f
        text.textSize = dp(11f) * (p.a.width() / dp(64f)).coerceIn(0.7f, 1.4f)
        for ((r, label) in listOf(p.b to "B", p.a to "A")) {
            canvas.drawText(label, r.centerX() + r.width() * 0.08f, r.bottom + text.textSize * 1.9f, text)
        }
        text.textSize = dp(10f) * (p.select.width() / dp(64f)).coerceIn(0.7f, 1.4f)
        for ((r, label) in listOf(p.select to "SELECT", p.start to "START")) {
            canvas.save()
            canvas.rotate(p.pillRotation, r.centerX(), r.centerY())
            canvas.drawText(label, r.centerX(), r.bottom + text.textSize * 1.3f, text)
            canvas.restore()
        }
    }

    // ---- Inside of the translucent plastic -----------------------------------------------

    /** Screw posts, where a real GBC has them, wherever they don't run into anything. */
    private fun placeScrews(p: SkinParts, w: Float, h: Float, speaker: Speaker?): List<PointF> {
        val d = p.dpad
        val candidates = listOf(
            PointF((d.right + p.b.left) / 2, d.centerY() - d.height() * 0.15f),
            PointF(d.right - d.width() * 0.05f, d.bottom + dp(14f)),
            PointF(d.left + dp(4f), d.bottom + dp(18f)),
            PointF(p.b.centerX() - p.b.width() * 0.2f, p.b.bottom + dp(20f)),
            PointF((p.a.centerX() + p.b.centerX()) / 2 + dp(6f), max(p.a.bottom, p.b.bottom) + dp(4f)),
            PointF(p.a.right + dp(14f), p.a.bottom + dp(8f)),
            PointF(p.screen.left + dp(16f), p.screen.bottom + dp(22f)),
            PointF(p.screen.right - dp(16f), p.screen.bottom + dp(22f)),
            PointF(d.right + dp(16f), d.centerY() + d.height() * 0.1f),
            PointF((d.right + p.b.left) / 2, d.top + dp(4f)),
            PointF(p.start.right + dp(22f), p.start.top - dp(26f)),
            PointF(p.select.left - dp(22f), p.select.top - dp(18f)),
            PointF((p.select.centerX() + p.start.centerX()) / 2, p.select.top - dp(30f)),
        )
        val r = dp(9f)
        // Just outside the rubber around the d-pad, as on the real one
        val ring = d.width() * 0.66f
        val blocked = blockers(p, dp(6f)) + listOfNotNull(boardRect(p, h)?.let { RectF(it).apply { inset(-dp(6f), -dp(6f)) } }) + listOf(
            RectF(d.centerX() - ring, d.centerY() - ring, d.centerX() + ring, d.centerY() + ring)
        )
        val chosen = ArrayList<PointF>()
        for (c in candidates) {
            if (!circleInside(c.x, c.y, r, dp(12f), w, h)) continue
            if (blocked.any { circleHits(c.x, c.y, r, it) }) continue
            if (speaker != null && hypot(c.x - speaker.x, c.y - speaker.y) < speaker.r + dp(10f) + r) continue
            if (chosen.any { hypot(c.x - it.x, c.y - it.y) < dp(24f) }) continue
            chosen.add(c)
        }
        return chosen
    }

    /** The inside, as seen through the plastic. The GBC's plastic is frosted, even the clear
     * one: what's more than a few millimetres behind it melts into the plastic's own light
     * colour, and only what touches the shell shows, as on photos of real ones: the rubber
     * pads under the buttons, a small board low on the left, a few parts under Select and
     * Start, the speaker and the screw posts. It's drawn at a reduced size and scaled up,
     * which blurs it as the plastic does, then tinted by the plastic. */
    private fun renderInterior(p: SkinParts, w: Float, h: Float, speaker: Speaker?, screws: List<PointF>): Bitmap {
        val scale = 0.45f
        val bitmap = Bitmap.createBitmap(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val random = Random(1998)
        c.drawColor(FROST)
        paint.color = 0x14203830
        val main = RectF(dp(16f), p.screen.bottom + dp(24f), w - dp(16f), h - dp(26f))
        if (main.height() > dp(40f)) c.drawRoundRect(main, dp(10f), dp(10f), paint)
        line.color = 0x1A000000
        line.strokeWidth = dp(2f)
        for (k in 1..3) {
            val y = p.screen.bottom + (h - p.screen.bottom) * k / 4f
            c.drawLine(dp(12f), y, dp(26f), y, line)
            c.drawLine(w - dp(26f), y, w - dp(12f), y, line)
        }

        // The small board low on the left, dense with parts (laid out on a grid, so nothing
        // overlaps)
        val board = boardRect(p, h)
        if (board != null) {
            paint.color = PCB_GREEN
            c.drawRoundRect(board, dp(3f), dp(3f), paint)
            val grid = BoardGrid(w, h, dp(4f))
            grid.keepOnly(RectF(board).apply { inset(dp(2f), dp(2f)) })
            drawTraces(c, line, paint, grid, random, grid.freeCells() / 25)
            drawParts(c, paint, line, grid, random, chips = 2, transistors = 4, small = grid.freeCells() / 2)
        }

        // A few parts under Select and Start, further back, so fainter
        paint.color = 0xFFAEB5B7.toInt()
        val px = (p.select.centerX() + p.start.centerX()) / 2
        val py = max(p.select.bottom, p.start.bottom) + dp(24f)
        if (py + dp(10f) < h - dp(28f)) {
            for (k in -3..3) {
                val x = px + k * dp(11f)
                c.drawRect(x - dp(3f), py + (k % 2) * dp(4f), x + dp(3f), py + dp(5f) + (k % 2) * dp(4f), paint)
            }
        }

        drawMembranes(c, p, paint, line)
        speaker?.let { drawSpeaker(c, it, paint, line) }
        // Screw posts, hollow, seen end on
        for (sc in screws) {
            paint.color = 0xFFF7F9F9.toInt()
            c.drawCircle(sc.x, sc.y, dp(8.5f), paint)
            line.color = 0xFFB4BCBE.toInt()
            line.strokeWidth = dp(1.4f)
            c.drawCircle(sc.x, sc.y, dp(8.5f), line)
            paint.color = 0xFF8E979A.toInt()
            c.drawCircle(sc.x, sc.y, dp(3.2f), paint)
        }

        // Seen through the plastic (frosted coloured plastic lets more light through than
        // its colour alone would, so it tints with a lighter shade)
        paint.color = if (look.clear) 0xFFE3EBEA.toInt() else ColorUtils.blendARGB(look.plastic, Color.WHITE, 0.3f)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        c.drawRect(0f, 0f, w, h, paint)
        paint.xfermode = null

        // Where the plastic is thicker, its edges show: the wall around the shell and the
        // screw posts (bright, refracting edges in clear plastic; deeper colour in tinted)
        if (look.clear) {
            line.color = 0x66FFFFFF
            line.strokeWidth = dp(2.5f)
            c.drawRoundRect(dp(4f), dp(4f), w - dp(4f), h - dp(4f), dp(16f), dp(16f), line)
            line.color = 0x33FFFFFF
            line.strokeWidth = dp(1.5f)
            c.drawRoundRect(dp(9f), dp(9f), w - dp(9f), h - dp(9f), dp(12f), dp(12f), line)
            for (sc in screws) {
                line.color = 0x70FFFFFF
                line.strokeWidth = dp(2f)
                c.drawCircle(sc.x, sc.y, dp(9f), line)
            }
        } else {
            line.color = ColorUtils.setAlphaComponent(darker(look.plastic, 0.8f), 0xC0)
            line.strokeWidth = dp(5f)
            c.drawRoundRect(dp(6f), dp(6f), w - dp(6f), h - dp(6f), dp(18f), dp(18f), line)
            for (sc in screws) {
                line.color = richer(look.plastic)
                line.strokeWidth = dp(3.5f)
                c.drawCircle(sc.x, sc.y, dp(8f), line)
            }
        }
        return bitmap
    }

    /** The little board low on the left, about square as on the real one, if there's room. */
    private fun boardRect(p: SkinParts, h: Float): RectF? {
        val board = RectF(dp(14f), p.dpad.centerY() + p.dpad.height() * 0.68f,
            min(p.select.left - dp(12f), p.dpad.centerX() + p.dpad.width() * 0.32f), h - dp(30f))
        board.top = max(board.top, board.bottom - board.width() * 1.05f)
        return if (board.width() > dp(36f) && board.height() > dp(36f)) board else null
    }

    /** Cells of the board: which are taken by parts, and which can't take parts or traces. */
    private class BoardGrid(w: Float, h: Float, val cell: Float) {
        val cols = max(1, (w / cell).toInt())
        val rows = max(1, (h / cell).toInt())
        val noParts = Array(cols) { BooleanArray(rows) }
        val noTraces = Array(cols) { BooleanArray(rows) }

        fun block(r: RectF, all: Boolean) {
            for (i in col(r.left)..col(r.right)) for (j in row(r.top)..row(r.bottom)) {
                noParts[i][j] = true
                if (all) noTraces[i][j] = true
            }
        }

        /** Nothing goes outside r. */
        fun keepOnly(r: RectF) {
            for (i in 0 until cols) for (j in 0 until rows) {
                if (!r.contains(i * cell + cell / 2, j * cell + cell / 2)) {
                    noParts[i][j] = true
                    noTraces[i][j] = true
                }
            }
        }

        fun freeCells() = noParts.sumOf { column -> column.count { !it } }

        fun col(x: Float) = (x / cell).toInt().coerceIn(0, cols - 1)
        fun row(y: Float) = (y / cell).toInt().coerceIn(0, rows - 1)
        fun free(i: Int, j: Int, w: Int, h: Int): Boolean {
            if (i < 1 || j < 1 || i + w >= cols || j + h >= rows) return false
            for (a in i until i + w) for (b in j until j + h) if (noParts[a][b]) return false
            return true
        }
        fun take(i: Int, j: Int, w: Int, h: Int) {
            for (a in i until i + w) for (b in j until j + h) noParts[a][b] = true
        }
    }

    /** Traces wander across the board in straight runs with 45 degree turns, ending in vias. */
    private fun drawTraces(c: Canvas, line: Paint, paint: Paint, grid: BoardGrid, random: Random, count: Int) {
        val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1))
        line.color = PCB_TRACE
        for (k in 0 until count * 4) {  // some starts land on taken cells
            var i = 1 + random.nextInt(grid.cols - 2)
            var j = 1 + random.nextInt(grid.rows - 2)
            if (grid.noTraces[i][j]) continue
            var d = random.nextInt(4) * 2  // start straight
            val path = android.graphics.Path()
            fun at(a: Int) = a * grid.cell + grid.cell / 2
            path.moveTo(at(i), at(j))
            val x0 = at(i)
            val y0 = at(j)
            var steps = 0
            for (seg in 0 until 2 + random.nextInt(3)) {
                val run = 3 + random.nextInt(14)
                var moved = 0
                while (moved < run) {
                    val ni = i + dirs[d][0]
                    val nj = j + dirs[d][1]
                    if (ni < 1 || nj < 1 || ni >= grid.cols - 1 || nj >= grid.rows - 1 || grid.noTraces[ni][nj]) break
                    i = ni; j = nj; moved++
                }
                if (moved == 0) break
                steps += moved
                path.lineTo(at(i), at(j))
                d = (d + if (random.nextBoolean()) 1 else 7) % 8
            }
            if (steps < 3) continue
            line.strokeWidth = if (random.nextInt(5) == 0) dp(2f) else dp(1.1f)
            c.drawPath(path, line)
            for ((x, y) in listOf(x0 to y0, at(i) to at(j))) {
                paint.color = PCB_PAD
                c.drawCircle(x, y, dp(1.8f), paint)
                paint.color = 0xFF1E3A2C.toInt()
                c.drawCircle(x, y, dp(0.7f), paint)
            }
        }
    }

    /** Chips and small parts on the free cells, with their outline printed on the board. */
    private fun drawParts(
        c: Canvas, paint: Paint, line: Paint, grid: BoardGrid, random: Random,
        chips: Int, transistors: Int, small: Int
    ) {
        fun place(w: Int, h: Int, tries: Int, gap: Int = 1): Pair<Int, Int>? {
            repeat(tries) {
                val i = random.nextInt(grid.cols)
                val j = random.nextInt(grid.rows)
                if (grid.free(i - gap, j - gap, w + 2 * gap, h + 2 * gap)) {
                    grid.take(i - gap, j - gap, w + 2 * gap, h + 2 * gap)
                    return i to j
                }
            }
            return null
        }
        fun rect(i: Int, j: Int, w: Int, h: Int) =
            RectF(i * grid.cell, j * grid.cell, (i + w) * grid.cell, (j + h) * grid.cell)
        fun silkscreen(r: RectF) {
            line.color = 0xB0E6E8E0.toInt()
            line.strokeWidth = dp(0.6f)
            c.drawRect(RectF(r).apply { inset(-dp(1.5f), -dp(1.5f)) }, line)
        }
        fun pins(r: RectF, horizontal: Boolean, vertical: Boolean) {
            paint.color = PCB_PAD
            val pitch = dp(2f)
            if (horizontal) {
                var x = r.left + pitch
                while (x < r.right - pitch / 2) {
                    c.drawRect(x - dp(0.5f), r.top - dp(1.8f), x + dp(0.5f), r.bottom + dp(1.8f), paint)
                    x += pitch
                }
            }
            if (vertical) {
                var y = r.top + pitch
                while (y < r.bottom - pitch / 2) {
                    c.drawRect(r.left - dp(1.8f), y - dp(0.5f), r.right + dp(1.8f), y + dp(0.5f), paint)
                    y += pitch
                }
            }
        }
        fun body(r: RectF) {
            paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, 0xFF2A2B2E.toInt(), 0xFF151618.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, dp(0.8f), dp(0.8f), paint)
            paint.shader = null
        }

        // Small chips, transistors, then resistors and capacitors
        repeat(chips) {
            val wide = random.nextBoolean()
            val (w, h) = if (wide) 6 to 3 else 3 to 6
            place(w, h, 300)?.let { (i, j) ->
                val r = rect(i, j, w, h)
                pins(r, horizontal = wide, vertical = !wide); body(r); silkscreen(r)
            }
        }
        repeat(transistors) {
            place(2, 2, 200)?.let { (i, j) ->
                val r = rect(i, j, 2, 2).apply { inset(dp(2f), dp(2.5f)) }
                paint.color = PCB_PAD
                c.drawRect(r.left, r.bottom, r.left + dp(1f), r.bottom + dp(2f), paint)
                c.drawRect(r.right - dp(1f), r.bottom, r.right, r.bottom + dp(2f), paint)
                c.drawRect(r.centerX() - dp(0.5f), r.top - dp(2f), r.centerX() + dp(0.5f), r.top, paint)
                body(r)
            }
        }
        repeat(small) {
            val horizontal = random.nextBoolean()
            val (w, h) = if (horizontal) 2 to 1 else 1 to 2
            place(w, h, 40, gap = 0)?.let { (i, j) ->
                val r = rect(i, j, w, h).apply { inset(if (horizontal) dp(0.6f) else dp(0.9f), if (horizontal) dp(0.9f) else dp(0.6f)) }
                val resistor = random.nextInt(3) != 0
                paint.color = PCB_PAD
                c.drawRect(r, paint)
                paint.color = if (resistor) 0xFF1E1E20.toInt() else 0xFFB9925E.toInt()
                val e = dp(1.2f)
                if (horizontal) c.drawRect(r.left + e, r.top, r.right - e, r.bottom, paint)
                else c.drawRect(r.left, r.top + e, r.right, r.bottom - e, paint)
            }
        }
    }

    /** The rubber pads under the buttons, pale cyan rubber: a ring around the d-pad, a ring
     * around A and around B joined by a bridge, and a darker capsule around Select and Start. */
    private fun drawMembranes(c: Canvas, p: SkinParts, paint: Paint, line: Paint) {
        // A band of rubber around the d-pad
        val dr = p.dpad.width() * 0.6f
        val band = p.dpad.width() * 0.11f
        line.color = RUBBER
        line.strokeWidth = band
        c.drawCircle(p.dpad.centerX(), p.dpad.centerY(), dr, line)
        line.color = RUBBER_EDGE
        line.strokeWidth = dp(1.5f)
        c.drawCircle(p.dpad.centerX(), p.dpad.centerY(), dr + band / 2, line)
        c.drawCircle(p.dpad.centerX(), p.dpad.centerY(), dr - band / 2, line)

        // A band around A and around B, and the bridge joining them
        val rr = max(p.a.width(), p.b.width()) / 2 + dp(5f)
        val ab = rr * 0.42f
        line.color = RUBBER
        line.strokeWidth = ab * 1.4f
        c.drawLine(p.b.centerX(), p.b.centerY(), p.a.centerX(), p.a.centerY(), line)
        line.strokeWidth = ab
        for (r in listOf(p.a, p.b)) c.drawCircle(r.centerX(), r.centerY(), rr, line)
        line.color = RUBBER_EDGE
        line.strokeWidth = dp(1.5f)
        for (r in listOf(p.a, p.b)) c.drawCircle(r.centerX(), r.centerY(), rr + ab / 2, line)

        val g = dp(6f)
        val pills = if (hypot(p.select.centerX() - p.start.centerX(), p.select.centerY() - p.start.centerY()) <
            p.select.width() * 2.5f) listOf(RectF(p.select).apply { union(p.start) }) else listOf(p.select, p.start)
        for (r in pills) {
            val box = RectF(r).apply { inset(-g, -g) }
            paint.color = 0xFF56616A.toInt()
            c.drawRoundRect(box, box.height() / 2, box.height() / 2, paint)
            line.color = 0xFF3A434B.toInt()
            line.strokeWidth = dp(1.5f)
            c.drawRoundRect(box, box.height() / 2, box.height() / 2, line)
        }
    }

    /** The speaker: a dark rim around a bronze cone with its rings and a cap in the middle. */
    private fun drawSpeaker(c: Canvas, s: Speaker, paint: Paint, line: Paint) {
        paint.color = 0xFF3B3E42.toInt()
        c.drawCircle(s.x, s.y, s.r + dp(6f), paint)
        paint.shader = RadialGradient(s.x - s.r * 0.15f, s.y - s.r * 0.15f, s.r + dp(1f),
            intArrayOf(0xFFD4A873.toInt(), 0xFFB98A55.toInt(), 0xFF8C6336.toInt()), floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP)
        c.drawCircle(s.x, s.y, s.r + dp(1f), paint)
        paint.shader = null
        line.color = 0xFF7E5A33.toInt()
        line.strokeWidth = dp(1.2f)
        for (k in listOf(0.38f, 0.58f, 0.8f)) c.drawCircle(s.x, s.y, s.r * k, line)
        paint.color = 0xFF6E4C2A.toInt()
        c.drawCircle(s.x, s.y, s.r * 0.2f, paint)
    }

    private fun darker(colour: Int, f: Float) =
        Color.rgb((Color.red(colour) * f).toInt(), (Color.green(colour) * f).toInt(), (Color.blue(colour) * f).toInt())

    /** The plastic's colour, deeper and a little lighter, as it looks in the screw posts. */
    private fun richer(colour: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        hsl[1] = min(1f, hsl[1] * 1.35f + 0.1f)
        hsl[2] = min(0.85f, hsl[2] * 1.05f + 0.04f)
        return ColorUtils.HSLToColor(hsl)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
