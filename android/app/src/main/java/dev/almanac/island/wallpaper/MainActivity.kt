// The wallpaper's one screen: the seed (with a button that rolls a new one), how big the picture is and how often it is
// drawn, how long a day lasts, what the wallpaper is doing right now, and the way to the launcher's own picker.
package dev.almanac.island.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import dev.almanac.island.seedOf

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var seed: EditText
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            status.text = Prefs.status(prefs).ifBlank { getString(R.string.not_set) }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs.of(this)

        seed = findViewById(R.id.seed)
        status = findViewById(R.id.status)
        seed.setText(Prefs.seed(prefs))
        seed.setOnEditorActionListener { _, _, _ ->
            applySeed()
            true
        }

        findViewById<Button>(R.id.random).setOnClickListener {
            val phrase = SeedWords.random()
            seed.setText(phrase)
            applySeed()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(seed.windowToken, 0)
        }

        findViewById<Button>(R.id.apply).setOnClickListener { applySeed() }

        findViewById<Button>(R.id.set_wallpaper).setOnClickListener { openPicker() }

        findRadioGroup(R.id.theme).setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.theme_maple -> "maple"
                R.id.theme_rift -> "rift"
                else -> "original"
            }
            Prefs.setTheme(prefs, theme)
        }
        findRadioGroup(R.id.quality).setOnCheckedChangeListener { _, id ->
            val q = when (id) {
                R.id.quality_low -> Prefs.QUALITY_LOW
                R.id.quality_medium -> Prefs.QUALITY_MEDIUM
                R.id.quality_high -> Prefs.QUALITY_HIGH
                else -> Prefs.QUALITY_AUTO
            }
            Prefs.setQuality(prefs, q)
        }
        findRadioGroup(R.id.fps).setOnCheckedChangeListener { _, id ->
            Prefs.setFps(
                prefs,
                when (id) {
                    R.id.fps_24 -> 24
                    R.id.fps_60 -> 60
                    else -> 30
                },
            )
        }
        findRadioGroup(R.id.day).setOnCheckedChangeListener { _, id ->
            Prefs.setDay(
                prefs,
                when (id) {
                    R.id.day_5 -> 5
                    R.id.day_20 -> 20
                    R.id.day_40 -> 40
                    else -> 10
                },
            )
        }
        showChoices()
    }

    private fun findRadioGroup(id: Int): RadioGroup = findViewById(id)

    /** Ticks the choices the wallpaper is set to now. */
    private fun showChoices() {
        findRadioGroup(R.id.theme).check(
            when (Prefs.theme(prefs)) {
                "maple" -> R.id.theme_maple
                "rift" -> R.id.theme_rift
                else -> R.id.theme_original
            },
        )
        when (Prefs.quality(prefs)) {
            Prefs.QUALITY_LOW -> R.id.quality_low
            Prefs.QUALITY_MEDIUM -> R.id.quality_medium
            Prefs.QUALITY_HIGH -> R.id.quality_high
            else -> R.id.quality_auto
        }.let { findRadioGroup(R.id.quality).check(it) }
        when (Prefs.fps(prefs)) {
            24 -> R.id.fps_24
            60 -> R.id.fps_60
            else -> R.id.fps_30
        }.let { findRadioGroup(R.id.fps).check(it) }
        when (Prefs.day(prefs)) {
            5 -> R.id.day_5
            20 -> R.id.day_20
            40 -> R.id.day_40
            else -> R.id.day_10
        }.let { findRadioGroup(R.id.day).check(it) }
    }

    private fun applySeed() {
        val phrase = seed.text.toString().trim().ifEmpty { Prefs.DEFAULT_SEED }
        Prefs.setSeed(prefs, phrase)
        seed.setText(phrase)
        Toast.makeText(
            this,
            getString(R.string.applied, phrase, seedOf(phrase)),
            Toast.LENGTH_SHORT,
        ).show()
        handler.postDelayed({ status.text = Prefs.status(prefs).ifBlank { getString(R.string.not_set) } }, 400)
    }

    /** Asks the launcher for its live wallpaper picker, on this wallpaper already chosen where it can be. */
    private fun openPicker() {
        val component = ComponentName(this, IslandWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(direct)
        } catch (t: Throwable) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (t2: Throwable) {
                Toast.makeText(this, getString(R.string.no_picker), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }
}
