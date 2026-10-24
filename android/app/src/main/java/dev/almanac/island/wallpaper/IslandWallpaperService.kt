// The live wallpaper: the island scene drawn onto the wallpaper's surface, a frame at a time.
//
// How it stays light on the battery, and quick, on the phone rather than in a benchmark:
//  - the ground, the houses, the trees, the roads and the terraces are drawn ONCE, when the island is grown, and kept
//    as a base frame; a frame is that base copied and only what moves drawn over it (water, people, animals, gulls,
//    the boat, the hour's light, the lamps);
//  - everything that stands still is its own sprite, built once (each house, each tree);
//  - the picture is drawn at its own resolution (a few hundred pixels across, as pixel art should be), then blitted to
//    the screen as one whole bitmap with no filtering, so the phone never fills a 1440-pixel screen per pixel;
//  - in Auto, the picture's size follows how fast the phone really draws it: the engine keeps the time each frame takes
//    and steps the picture down (or up) until a frame fits in its share of the frame's time;
//  - nothing is drawn while the wallpaper is not visible (another app in front, or the screen off), and the island is
//    built on a background thread so the launcher never waits.
package dev.almanac.island.wallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import dev.almanac.island.IslandScene
import dev.almanac.island.LiveScene
import dev.almanac.island.BakedRiftScene
import dev.almanac.island.RiftScene
import dev.almanac.island.readPng
import dev.almanac.island.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class IslandWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = IslandEngine()

    inner class IslandEngine : WallpaperService.Engine() {
        private val prefs: SharedPreferences = Prefs.of(this@IslandWallpaperService)
        private val worker = HandlerThread("island-draw").apply { start() }
        private val handler = Handler(worker.looper)

        private var visible = false
        private var surfaceReady = false
        private var width = 0
        private var height = 0

        // what the island is: grown again when the seed, the size or the day's length changes
        private var scene: LiveScene? = null
        private var building = false
        private var pendingRebuild = false
        private var seedPhrase = Prefs.seed(prefs)
        private var builtPhrase = ""
        private var builtMinutes = 0
        private var artW = 0
        private var artH = 0
        private var detail = 1.0
        private var dayMinutes = Prefs.day(prefs)
        private var targetFps = Prefs.fps(prefs)
        private var quality = Prefs.quality(prefs)
        private var themeId = Prefs.theme(prefs)
        private var builtTheme = ""

        // the frame, and the bitmap it is handed over in
        private var bitmap: Bitmap? = null
        private var pixels: IntArray? = null
        private var skyFill = Color.rgb(18, 50, 79)

        // timing
        private var nextFrameAt = 0L
        private var lastFrameAt = 0L
        private val frameCosts = FloatArray(30)
        private var frameCostIndex = 0
        private var framesDrawn = 0
        private var startedAt = 0L
        private var lastStatusAt = 0L
        private var lastAdaptAt = 0L
        private var measuredFps = 0f
        private var grownInMs = 0L

        // the picture's scale in Auto, moved by how fast frames really come
        private var autoScale = 1.0

        private val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
            isDither = false
        }

        private val gesture: GestureDetector = GestureDetector(
            this@IslandWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                // a new island from a new seed, without leaving the home screen
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    Prefs.setSeed(prefs, SeedWords.random())
                    return true
                }
            },
        )

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "seed" -> {
                    val phrase = Prefs.seed(p)
                    if (phrase != seedPhrase) {
                        seedPhrase = phrase
                        handler.post { rebuild() }
                    }
                }
                "quality" -> {
                    quality = Prefs.quality(p)
                    autoScale = 1.0
                    handler.post { rebuild() }
                }
                "day" -> {
                    dayMinutes = Prefs.day(p)
                    handler.post { rebuild() }
                }
                "theme" -> {
                    val t = Prefs.theme(p)
                    if (t != themeId) {
                        themeId = t
                        handler.post { rebuild() }
                    }
                }
                "fps" -> targetFps = Prefs.fps(p)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (!visible || !surfaceReady) return
                val now = SystemClock.uptimeMillis()
                val interval = 1000L / targetFps
                if (now < nextFrameAt) {
                    handler.postDelayed(this, nextFrameAt - now)
                    return
                }
                drawFrame(now)
                nextFrameAt = max(now + 1, nextFrameAt + interval)
                if (now - nextFrameAt > interval * 3) nextFrameAt = now + interval
                handler.postDelayed(this, max(1L, nextFrameAt - SystemClock.uptimeMillis()))
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            targetFps = Prefs.fps(prefs)
            quality = Prefs.quality(prefs)
            dayMinutes = Prefs.day(prefs)
            seedPhrase = Prefs.seed(prefs)
            themeId = Prefs.theme(prefs)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            handler.removeCallbacksAndMessages(null)
            worker.quitSafely()
            bitmap?.recycle()
            bitmap = null
            scene = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(visibleFlag: Boolean) {
            visible = visibleFlag
            if (visible) {
                startedAt = SystemClock.uptimeMillis()
                framesDrawn = 0
                nextFrameAt = startedAt
                handler.removeCallbacks(tick)
                handler.post(tick)
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            handler.post { rebuild() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            if (w == width && h == height) return
            width = w
            height = h
            handler.post { rebuild() }
        }

        override fun onTouchEvent(event: MotionEvent) {
            gesture.onTouchEvent(event)
        }

        /** Grows the island again for the screen it is on, off the thread that draws, so nothing stutters meanwhile. */
        private fun rebuild() {
            if (!surfaceReady || width <= 0 || height <= 0) return
            if (building) {
                pendingRebuild = true
                return
            }
            val (w, h, d) = pictureSize()
            // the same seed, at the same size, with days the same length: the island standing already is this island
            val same = scene != null && w == artW && h == artH && seedPhrase == builtPhrase && dayMinutes == builtMinutes &&
                themeId == builtTheme
            if (same) {
                startTicking()
                return
            }
            building = true
            handler.post {
                val started = SystemClock.uptimeMillis()
                val fresh: LiveScene? = try {
                    val th = Theme.fromId(themeId)
                    if (th == Theme.RIFT) {
                        // a painted map, if the build carries one: the picture is the fixed layer, the engine adds
                        // the water, the soldiers and the hour; otherwise the map is drawn from code alone
                        val baked = bakedRift(w, h)
                        if (baked != null) {
                            BakedRiftScene(
                                phrase = seedPhrase, background = baked.first, mask = baked.second,
                                detail = d, dayLength = dayMinutes * 60.0, theme = th,
                            )
                        } else {
                            RiftScene(
                                phrase = seedPhrase, W = w, H = h, detail = d, dayLength = dayMinutes * 60.0,
                                theme = th,
                            )
                        }
                    } else {
                        IslandScene(
                            phrase = seedPhrase, W = w, H = h, detail = d, dayLength = dayMinutes * 60.0,
                            theme = th,
                        )
                    }
                } catch (t: Throwable) {
                    null
                }
                grownInMs = SystemClock.uptimeMillis() - started
                if (fresh != null) {
                    scene = fresh
                    artW = w
                    artH = h
                    detail = d
                    builtPhrase = fresh.phrase
                    builtMinutes = dayMinutes
                    builtTheme = fresh.theme.id
                    pixels = IntArray(w * h)
                    bitmap?.recycle()
                    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    skyFill = fresh.palette.P.LV[3] or (0xFF shl 24)
                    frameCostIndex = 0
                    framesDrawn = 0
                    startedAt = SystemClock.uptimeMillis()
                    lastFrameAt = 0L
                    startTicking()
                    publishStatus(SystemClock.uptimeMillis())
                }
                building = false
                if (pendingRebuild) {
                    pendingRebuild = false
                    rebuild()
                }
            }
        }

        /** Sets the frames going again from this moment, without waiting for the old schedule. */
        private fun startTicking() {
            if (!visible || !surfaceReady) return
            handler.removeCallbacks(tick)
            nextFrameAt = SystemClock.uptimeMillis()
            handler.post(tick)
        }

        /**
         * The picture's size in its own pixels, and how much finer than the almanac's 384 x 216. A wallpaper is a big
         * screen; drawing it a pixel at a time would cost more than it shows, so the picture stays a few hundred pixels
         * across and is blitted up whole, which is also what pixel art wants.
         */
        /** The baked background and its mask, if this build has them at this exact size. */
        private fun bakedRift(w: Int, h: Int): Pair<dev.almanac.island.PngImage, dev.almanac.island.PngImage>? {
            return try {
                val assets = context.assets
                fun load(name: String) = dev.almanac.island.readPng(assets.open("rift/$name").readBytes())
                val bg = load("background.png")
                val mk = load("masks.png")
                if (bg.w != w || bg.h != h) null else bg to mk
            } catch (t: Throwable) {
                null
            }
        }

        private fun pictureSize(): Triple<Int, Int, Double> {
            val budget = when (quality) {
                Prefs.QUALITY_LOW -> 90_000.0
                Prefs.QUALITY_MEDIUM -> 170_000.0
                Prefs.QUALITY_HIGH -> 420_000.0
                else -> 210_000.0
            } * (autoScale * autoScale)
            val ratio = height.toDouble() / width
            val w = sqrt(budget / ratio).roundToInt().coerceIn(224, 720)
            val h = (w * ratio).roundToInt().coerceIn(140, 1600)
            return Triple(w, h, (w / 384.0).coerceIn(0.55, 2.4))
        }

        /** One frame: step the world, render the picture, blit it to the surface whole. */
        private fun drawFrame(now: Long) {
            val sc = scene
            if (sc == null) {
                drawPlaceholder()
                return
            }
            val dt = if (lastFrameAt == 0L) 1.0 / targetFps else min(0.1, (now - lastFrameAt) / 1000.0)
            lastFrameAt = now
            val started = System.nanoTime()
            sc.step(dt)
            sc.render()
            val renderMs = (System.nanoTime() - started) / 1_000_000f
            frameCosts[frameCostIndex % frameCosts.size] = renderMs
            frameCostIndex++
            framesDrawn++
            val px = pixels ?: return
            val bmp = bitmap ?: return
            System.arraycopy(sc.frame, 0, px, 0, px.size)
            bmp.setPixels(px, 0, sc.W, 0, 0, sc.W, sc.H)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                if (!holder.surface.isValid) return
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(skyFill)
                    canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), paint)
                }
            } catch (t: Throwable) {
                // the surface can go away between the check and the lock
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (t: Throwable) {
                        // the surface went away
                    }
                }
            }
            adaptQuality(now)
            publishStatus(now)
        }

        private fun drawPlaceholder() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                if (!holder.surface.isValid) return
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(skyFill)
                    val p = Paint().apply {
                        color = Color.argb(170, 236, 230, 214)
                        textSize = 24f
                        isAntiAlias = true
                    }
                    canvas.drawText("growing $seedPhrase…", 28f, height * 0.5f, p)
                }
            } catch (t: Throwable) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (t: Throwable) {
                    }
                }
            }
        }

        /**
         * In Auto, the picture grows or shrinks to fit the time a frame has: if frames take more than 70% of it, the
         * picture shrinks a step; if they take less than a third, it grows one. At most once a second, so it settles
         * instead of hunting.
         */
        private fun adaptQuality(now: Long) {
            if (quality != Prefs.QUALITY_AUTO) return
            if (now - lastAdaptAt < 1000) return
            lastAdaptAt = now
            val avg = averageCost()
            if (avg <= 0f) return
            val budget = 1000f / targetFps
            val before = autoScale
            if (avg > budget * 0.7) autoScale = max(0.55, autoScale * 0.88)
            else if (avg < budget * 0.35) autoScale = min(1.8, autoScale * 1.06)
            if (before != autoScale) {
                val (w, h, _) = pictureSize()
                if (w != artW || h != artH) rebuild()
            }
        }

        private fun averageCost(): Float {
            val n = min(frameCostIndex, frameCosts.size)
            if (n == 0) return 0f
            var sum = 0f
            for (i in 0 until n) sum += frameCosts[i]
            return sum / n
        }

        private fun publishStatus(now: Long) {
            if (now - lastStatusAt < 2000) return
            lastStatusAt = now
            val seconds = (now - startedAt) / 1000.0
            if (seconds > 0.5) measuredFps = (framesDrawn / seconds).toFloat()
            Prefs.setStatus(prefs, statusText())
        }

        private fun statusText(): String {
            val sc = scene
            return buildString {
                appendLine("seed      ${sc?.phrase ?: seedPhrase}")
                appendLine("theme     ${sc?.theme?.id ?: themeId}")
                appendLine("seed no.  ${sc?.seed ?: "–"}")
                appendLine("picture   ${artW} x ${artH} px  (detail ${"%.2f".format(detail)})")
                appendLine("one frame ${"%.1f".format(averageCost())} ms  ·  ${"%.0f".format(measuredFps)} fps")
                appendLine("grown in  $grownInMs ms")
                appendLine("map       ${sc?.summary ?: "–"}")
            }
        }
    }
}
