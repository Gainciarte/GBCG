package com.philj56.gbcc

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager

abstract class BaseActivity : AppCompatActivity() {

    val inNightMode get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // What the content is kept clear of. Apps targeting Android 15 or higher are always
    // drawn edge to edge, behind the status and navigation bars. Fullscreen activities,
    // which hide the bars, override this to avoid only the display cutout.
    protected open val insetTypes =
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    private var themeResource: Int = 0
    private var oledNightMode: Boolean = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same edge-to-edge look on older Android versions as on Android 15 and higher
        enableEdgeToEdge()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        themeResource = getThemeResource()
        oledNightMode = getOledNightMode()
        setTheme(themeResource)
        if (inNightMode && oledNightMode) {
            theme.applyStyle(R.style.OledDark, true)
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        // Layouts whose root fits system windows (a CoordinatorLayout with an AppBarLayout)
        // already move their toolbar below the status bar, so only the top inset is
        // passed on to them; the other sides are padded here.
        val rootHandlesTop = content.getChildAt(0)?.fitsSystemWindows == true
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(insetTypes)
            view.setPadding(bars.left, if (rootHandlesTop) 0 else bars.top, bars.right, bars.bottom)
            if (rootHandlesTop) {
                WindowInsetsCompat.Builder(insets)
                    .setInsets(insetTypes, Insets.of(0, bars.top, 0, 0))
                    .build()
            } else {
                WindowInsetsCompat.CONSUMED
            }
        }
        ViewCompat.requestApplyInsets(content)
    }

    override fun onStart() {
        super.onStart()
        // If the app theme has changed out from under us
        // (e.g. when coming back to the main screen after changing the theme in settings),
        // we need to recreate the activity in order to apply the change
        if (getThemeResource() != themeResource) {
            recreate()
        } else if (inNightMode && getOledNightMode() != oledNightMode) {
            recreate()
        }
    }

    // The app takes the colour chosen for the skin, so there's a single colour setting
    private fun getThemeResource(): Int {
        return when (prefs.getString("color", null)) {
            "Berry" -> R.style.Theme_GBCC_Berry
            "Dandelion" -> R.style.Theme_GBCC_Dandelion
            "Grape" -> R.style.Theme_GBCC_Grape
            "Kiwi" -> R.style.Theme_GBCC_Kiwi
            "Teal" -> R.style.Theme_GBCC_Teal
            else -> R.style.Theme_GBCC_Teal
        }
    }

    private fun getOledNightMode(): Boolean {
        return prefs.getBoolean("oled_night_mode", false)
    }
}
