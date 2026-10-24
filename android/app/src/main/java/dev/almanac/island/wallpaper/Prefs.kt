// What the wallpaper remembers: its seed, how big the picture is, how often it is drawn, how long a day lasts, and
// (written by the wallpaper itself) how fast it is going, so the settings screen can say so.
package dev.almanac.island.wallpaper

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "island"
    private const val KEY_SEED = "seed"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FPS = "fps"
    private const val KEY_DAY = "day"
    private const val KEY_STATUS = "status"

    const val QUALITY_AUTO = 0
    const val QUALITY_LOW = 1
    const val QUALITY_MEDIUM = 2
    const val QUALITY_HIGH = 3

    const val DEFAULT_SEED = "heron-bay-8"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun seed(p: SharedPreferences): String = p.getString(KEY_SEED, DEFAULT_SEED) ?: DEFAULT_SEED
    fun quality(p: SharedPreferences): Int = p.getInt(KEY_QUALITY, QUALITY_AUTO)
    fun fps(p: SharedPreferences): Int = p.getInt(KEY_FPS, 30)
    fun day(p: SharedPreferences): Int = p.getInt(KEY_DAY, 10)
    fun status(p: SharedPreferences): String = p.getString(KEY_STATUS, "") ?: ""

    fun setSeed(p: SharedPreferences, seed: String) = p.edit().putString(KEY_SEED, seed).apply()
    fun setQuality(p: SharedPreferences, quality: Int) = p.edit().putInt(KEY_QUALITY, quality).apply()
    fun setFps(p: SharedPreferences, fps: Int) = p.edit().putInt(KEY_FPS, fps).apply()
    fun setDay(p: SharedPreferences, minutes: Int) = p.edit().putInt(KEY_DAY, minutes).apply()
    fun setStatus(p: SharedPreferences, status: String) = p.edit().putString(KEY_STATUS, status).apply()

    /** Every key that means "build the island again". */
    val RESTART_KEYS = setOf(KEY_SEED, KEY_QUALITY, KEY_DAY)
}
