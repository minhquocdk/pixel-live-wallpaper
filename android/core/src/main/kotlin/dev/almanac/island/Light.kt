// Light: the day turning, how the hour grades a picture of the land, the lamps that hold the dark off round
// themselves, and the shadows of clouds drifting over it. Ported from the almanac's light and weather modules.
package dev.almanac.island

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The hour's light: how much it is night, how much the low light of dusk or dawn, and whether that is the morning. */
class HourLight(val night: Double, val dusk: Double, val morning: Boolean) {
    val plain: Boolean get() = night == 0.0 && dusk == 0.0
}

/** tod is the time of day, 0..1, with noon at 0.25 and midnight at 0.75. */
fun hourLight(tod: Double, dim: Double = 1.0): HourLight {
    val day = cos((tod - 0.25) * TAU)
    val night = clamp((-day - 0.15) * 1.6)
    val dusk = clamp(1 - abs(day) * 3.2) * (1 - night * 0.5)
    val morning = tod < 0.25 || tod > 0.9
    return HourLight(night, dusk, morning)
}

/** The hour as three tables, one a channel: night sinks to a deep blue, evening warms the shadows. */
fun hourTables(h: HourLight, dim: Double = 1.0, wet: Double = 0.0): HourTables {
    val k = h.dusk * (if (h.morning) 0.7 else 1.0)
    val wr = if (h.morning) 0.02 else 0.03
    val wg = if (h.morning) 0.05 else 0.08
    val wb = if (h.morning) 0.04 else 0.17
    val sr = if (h.morning) 6.0 else 12.0
    val sb = if (h.morning) 20.0 else 28.0
    val r = lerp(1.0, 0.34, h.night) * (1 + wr * k) * dim * lerp(1.0, 0.86, wet)
    val g = lerp(1.0, 0.43, h.night) * (1 - wg * k) * dim * lerp(1.0, 0.9, wet)
    val b = lerp(1.0, 0.72, h.night) * (1 - wb * k) * min(1.0, dim + 0.06) * lerp(1.0, 0.96, wet)
    val lift = h.night * 10
    val tr = IntArray(256)
    val tg = IntArray(256)
    val tb = IntArray(256)
    for (v in 0 until 256) {
        val sh = ((255 - v) / 255.0) * k
        tr[v] = min(255.0, v * r + lift * 0.3 + sh * sr).toInt()
        tg[v] = min(255.0, v * g + lift * 0.5).toInt()
        tb[v] = min(255.0, v * b + lift + sh * sb).toInt()
    }
    return HourTables(tr, tg, tb)
}

/** A light in the frame: a lamp, a window, the beacon. Its reach is an ellipse, as the ground is seen from above. */
class Light(val x: Double, val y: Double, val r: Double, val k: Double, val pit: Boolean = false)

/**
 * A frame graded by the hour's tables, with lamps holding off the dark: where a light reaches, the graded colour is
 * drawn back toward the ungraded one in dithered steps, never more than 85% of the way.
 */
fun gradeFrame(
    px: IntArray,
    w: Int,
    h: Int,
    tables: HourTables,
    night: Double,
    lights: List<Light>,
    scratch: FloatArray? = null,
): FloatArray? {
    val lr = tables.r
    val lg = tables.g
    val lb = tables.b
    if (lights.isEmpty() || night < 0.15) {
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0xFF shl 24) or (lr[redOf(c)] shl 16) or (lg[greenOf(c)] shl 8) or lb[blueOf(c)]
        }
        return scratch
    }
    val k = if (scratch != null && scratch.size == px.size) scratch else FloatArray(px.size)
    k.fill(0f)
    for (l in lights) {
        val xa = maxOf(0, floor(l.x - l.r).toInt())
        val xb = minOf(w - 1, Math.ceil(l.x + l.r).toInt())
        val ya = maxOf(0, floor(l.y - l.r / 1.5).toInt())
        val yb = minOf(h - 1, Math.ceil(l.y + l.r / 1.5).toInt())
        for (y in ya..yb) {
            for (x in xa..xb) {
                val dx = x - l.x
                val dy = (y - l.y) * 1.5
                val d = Math.sqrt(dx * dx + dy * dy) / l.r
                if (d < 1) {
                    val v = ((1 - d) * (1 - d) * l.k).toFloat()
                    val i = y * w + x
                    if (v > k[i]) k[i] = v
                }
            }
        }
    }
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            var keep = k[i].toDouble()
            if (keep > 0.02) {
                keep = min(0.85, floor(keep * 6 + ((x * 3 + y * 5) and 3) / 4.0) / 6)
                val r = redOf(c)
                val g = greenOf(c)
                val b = blueOf(c)
                px[i] = (0xFF shl 24) or
                    ((lr[r] + (r - lr[r]) * keep).toInt() shl 16) or
                    ((lg[g] + (g - lg[g]) * keep).toInt() shl 8) or
                    (lb[b] + (b - lb[b]) * keep).toInt()
            } else {
                px[i] = (0xFF shl 24) or (lr[redOf(c)] shl 16) or (lg[greenOf(c)] shl 8) or lb[blueOf(c)]
            }
        }
    }
    return k
}

private const val COARSE = 8
private val DARK = IntArray(256) { (it * 0.82).roundToInt() }
private val DARK_B = IntArray(256) { (it * 0.9).roundToInt() }

private class Coarse(val g: FloatArray, val gw: Int, val fx0: Double, val fy0: Double)

private fun coarse(w: Int, h: Int, x: Double, y: Double, scratch: FloatArray?, f: (Double, Double) -> Double): Coarse {
    val gw = Math.ceil(w / COARSE.toDouble()).toInt() + 2
    val gh = Math.ceil(h / COARSE.toDouble()).toInt() + 2
    val x0 = floor(x / COARSE).toInt()
    val y0 = floor(y / COARSE).toInt()
    val g = if (scratch != null && scratch.size == gw * gh) scratch else FloatArray(gw * gh)
    for (j in 0 until gh) {
        for (i in 0 until gw) {
            val wx = (x0 + i) * COARSE.toDouble()
            val wy = (y0 + j) * COARSE.toDouble()
            g[j * gw + i] = f(wx, wy).toFloat()
        }
    }
    return Coarse(g, gw, x - x0 * COARSE, y - y0 * COARSE)
}

/** The shadows of clouds over a frame: a bent fractal noise read on a coarse grid, darkening the land where it is high. */
fun cloudShadows(
    px: IntArray,
    w: Int,
    h: Int,
    x: Double,
    y: Double,
    seed: Int,
    cover: Double,
    strong: Boolean,
    scratch: FloatArray?,
): FloatArray {
    val c = coarse(w, h, x, y, scratch) { wx, wy ->
        fractalNoise(wx / 120 + 0.55 * sin(wy / 53 + 1.3), wy / 84 + 0.55 * sin(wx / 67 + 0.7), seed, 3)
    }
    val g = c.g
    val gw = c.gw
    val fx0 = c.fx0
    val fy0 = c.fy0
    val thr = 0.74 - cover * 0.36
    for (py in 0 until h) {
        val gy = (py + fy0) / COARSE
        val j = floor(gy).toInt()
        val v = gy - j
        val r0 = j * gw
        val r1 = r0 + gw
        for (pxx in 0 until w) {
            val gx = (pxx + fx0) / COARSE
            val i = floor(gx).toInt()
            val u = gx - i
            val n = (g[r0 + i] * (1 - u) + g[r0 + i + 1] * u) * (1 - v) + (g[r1 + i] * (1 - u) + g[r1 + i + 1] * u) * v
            if (n < thr) continue
            if (n < thr + 0.035 && ((pxx + py) and 1) != 0) continue
            if (!strong && ((pxx xor py) and 1) != 0) continue
            val idx = py * w + pxx
            val col = px[idx]
            px[idx] = (0xFF shl 24) or (DARK[redOf(col)] shl 16) or (DARK[greenOf(col)] shl 8) or DARK_B[blueOf(col)]
        }
    }
    return g
}

/** Banks of fog over a frame: thick fog laid over the land, middling fog in an ordered dither, thin fog as a veil. */
fun fogBanks(px: IntArray, w: Int, h: Int, x: Double, y: Double, seed: Int, colour: Int, density: Double, scratch: FloatArray?): FloatArray {
    val c = coarse(w, h, x, y, scratch) { wx, wy ->
        fractalNoise(wx / 70 + 0.5 * sin(wy / 37 + 0.4), wy / 46 + 0.5 * sin(wx / 43 + 2.1), seed, 3)
    }
    val g = c.g
    val gw = c.gw
    for (py in 0 until h) {
        val gy = (py + c.fy0) / COARSE
        val j = floor(gy).toInt()
        val v = gy - j
        val r0 = j * gw
        val r1 = r0 + gw
        for (pxx in 0 until w) {
            val gx = (pxx + c.fx0) / COARSE
            val i = floor(gx).toInt()
            val u = gx - i
            val n = (g[r0 + i] * (1 - u) + g[r0 + i + 1] * u) * (1 - v) + (g[r1 + i] * (1 - u) + g[r1 + i + 1] * u) * v
            val a = density * 1.15 - 0.42 + (n - 0.5) * 1.5
            if (a <= 0) continue
            val k = py * w + pxx
            if (a >= 1) px[k] = mixPacked(px[k], colour, 0.86)
            else if (BAYER[((py and 3) shl 2) or (pxx and 3)] < a * 16) px[k] = mixPacked(px[k], colour, 0.74)
            else if (a > 0.3) px[k] = mixPacked(px[k], colour, 0.22)
        }
    }
    return g
}

/** A flash of lightning: everything lit for an instant, then again a beat later, then gone. */
fun lightningFlash(px: IntArray, left: Double) {
    val k = if (left > 0.26 || (left > 0.1 && left < 0.18)) 1.0 else if (left > 0.18) 0.0 else 0.35
    if (k <= 0) return
    for (i in px.indices) {
        val c = px[i]
        val r = redOf(c)
        val g = greenOf(c)
        val b = blueOf(c)
        px[i] = (0xFF shl 24) or
            ((r + (255 - r) * 0.6 * k).toInt() shl 16) or
            ((g + (255 - g) * 0.62 * k).toInt() shl 8) or
            (b + (255 - b) * 0.7 * k).toInt()
    }
}

/** A star's brightness at a moment: a slow swell and fade, each star at its own phase. */
fun twinkle(t: Double, phase: Double, depth: Double = 0.4): Double = 1 - depth + depth * sin(t * 2 + phase)

/** A light turning on and off on a beat: the beacon, the lighthouse. */
fun blink(t: Double, period: Double, phase: Double, duty: Double): Boolean =
    ((t / period + phase) % 1.0 + 1.0) % 1.0 < duty

/** Whether a window is lit at a moment: a share of them, on their own slow beats. */
fun windowLit(id: Int, seed: Int, t: Double, share: Double, litShare: Double): Boolean {
    if (share <= 0) return false
    val own = indexRandom(id, seed + 17)
    if (own > litShare) return false
    val beat = sin(t * (0.4 + own) + own * 12)
    return beat > -0.75
}
