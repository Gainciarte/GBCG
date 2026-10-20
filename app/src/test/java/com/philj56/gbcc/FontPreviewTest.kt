package com.philj56.gbcc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/*
 * Not a test as such: draws a sample of the app's screens (toolbar, game list rows,
 * settings texts, a button) with the app's theme, to check its typeface, into
 * app/build/skin-previews/font.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h833dp-port-440dpi")
class FontPreviewTest {
    @Test
    fun render() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_GBCC_Teal)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(MaterialToolbar(context).apply { title = "GBCG" })
        val inflater = LayoutInflater.from(context)
        for (name in listOf("Pokémon Picross", "Ñandú Adventure", "Zelda: Link's Awakening DX", "Straße & Café")) {
            val row = inflater.inflate(R.layout.entry_file, root, false)
            row.findViewById<TextView>(R.id.fileEntry).text = name
            root.addView(row)
        }
        for ((attr, text) in listOf(
            com.google.android.material.R.attr.textAppearanceTitleMedium to "Imagen",
            android.R.attr.textAppearanceListItem to "Plástico translúcido",
            android.R.attr.textAppearanceListItemSecondary to "Deja ver el interior de la consola a través de la carcasa",
            com.google.android.material.R.attr.textAppearanceBodyMedium to "¿Borrar la partida? ¡No se puede deshacer!",
        )) {
            val tv = TextView(context)
            val a = context.obtainStyledAttributes(intArrayOf(attr))
            tv.setTextAppearance(a.getResourceId(0, 0))
            a.recycle()
            tv.text = text
            tv.setPadding(40, 16, 40, 16)
            root.addView(tv)
        }
        root.addView(MaterialButton(context).apply { text = "Elegir carpeta" })

        val width = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        val bitmap = Bitmap.createBitmap(width, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val out = File("build/skin-previews").apply { mkdirs() }
        FileOutputStream(File(out, "font.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
