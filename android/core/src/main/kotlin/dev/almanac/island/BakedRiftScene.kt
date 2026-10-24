// The Rift drawn by a painter, animated by the engine.
//
// A background can only be paint: a river in it cannot move, a soldier in it cannot walk, a lamp in it cannot come on.
// So the picture is baked once (tools/rift_bake.py) into a fixed layer at the engine's own resolution, plus a mask that
// says which pixel is water, which is road and which is stone. This scene keeps the painting as it is and adds the three
// things that have to move: the water, the soldiers on the lanes the mask reveals, and the hour's light.
package dev.almanac.island

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class BakedRiftScene(
    override val phrase: String = "twin-rivers-8",
    val background: PngImage,
    val mask: PngImage,
    val maskCells: Int = 1,
    val detail: Double = 1.0,
    val dayLength: Double = 600.0,
    override val theme: Theme = Theme.RIFT,
    val startTod: Double = 0.30,
) : LiveScene {
    override val W: Int = background.w
    override val H: Int = background.h
    override val palette = Palette(theme)
    override val seed: Int = seedOf(phrase)
    override val frame = IntArray(W * H)

    private val base = background.px.copyOf()

    /** What each pixel is: 0 none, 1 ground, 2 road, 3 stone, 4 water (from the mask). */
    private val kind = ByteArray(W * H)
    private val waterIdx = ArrayList<Int>(W * H / 8)

    /** The three lanes, as polylines read out of the mask: mid, left, right. */
    val lanes: List<List<DoubleArray>>

    private val lights = ArrayList<Light>(64)
    private val tables = HashMap<String, HourTables>()
    private var scratch: FloatArray? = null
    private var clock = 0.0
    private var tod = startTod
    private val day = if (dayLength <= 0.0) 600.0 else dayLength
    private val u = max(0.55, W / 307.0)

    override val summary: String
        get() = "${lanes.size} lanes read from the mask, ${waterIdx.size} water pixels"

    init {
        require(mask.w * maskCells == W && mask.h * maskCells == H) {
            "the mask is ${mask.w}x${mask.h} cells of $maskCells, but the picture is ${W}x$H"
        }
        for (y in 0 until H) {
            for (x in 0 until W) {
                val cell = mask.px[(y / maskCells) * mask.w + (x / maskCells)]
                val k = when {
                    redOf(cell) > 127 -> 4
                    greenOf(cell) > 127 -> 2
                    blueOf(cell) > 127 -> 3
                    else -> 0
                }
                if (k == 4) waterIdx.add(y * W + x)
                if (k != 0) kind[y * W + x] = k.toByte()
            }
        }
        lanes = readLanes()
        for (piece in RiftPlan.all()) {
            val x = RiftPlan.across(piece.p) * W
            val y = RiftPlan.down(piece.q) * H
            when (piece.kind) {
                "tower" -> lights.add(Light(x, y, 9 * u, 0.7))
                "inhibitor" -> lights.add(Light(x, y, 7 * u, 0.7))
                "nexus" -> lights.add(Light(x, y, 17 * u, 1.0))
                "pit" -> lights.add(Light(x, y, 24 * u, 1.0))
                else -> {}
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- what the mask says
    private fun isRoad(x: Int, y: Int): Boolean =
        x >= 0 && y >= 0 && x < W && y < H && kind[y * W + x].toInt() == 2

    /**
     * The lanes, read out of the mask row by row: the run of road under the middle of the picture is the mid lane, the
     * leftmost run the left lane, the rightmost the right lane. Where the three merge near a base, a lane simply keeps
     * the direction it had.
     */
    private fun readLanes(): List<List<DoubleArray>> {
        val mid = ArrayList<DoubleArray>()
        val left = ArrayList<DoubleArray>()
        val right = ArrayList<DoubleArray>()
        var lastLeft = -1.0
        var lastRight = -1.0
        for (y in 0 until H step max(1, (2 * u).roundToInt())) {
            val runs = ArrayList<IntArray>() // [from, to]
            var x = 0
            while (x < W) {
                if (!isRoad(x, y)) {
                    x++
                    continue
                }
                val from = x
                while (x < W && isRoad(x, y)) x++
                if (x - from >= 2) runs.add(intArrayOf(from, x - 1))
            }
            if (runs.isEmpty()) continue
            val centre = W / 2.0
            val midRun = runs.minByOrNull { abs((it[0] + it[1]) / 2.0 - centre) } ?: continue
            val midC = (midRun[0] + midRun[1]) / 2.0
            if (abs(midC - centre) < W * 0.10) mid.add(doubleArrayOf(midC, y.toDouble()))
            val leftRun = runs.filter { it[1] < midRun[0] }.minByOrNull { it[0] }
            val rightRun = runs.filter { it[0] > midRun[1] }.maxByOrNull { it[1] }
            if (leftRun != null) {
                val c = (leftRun[0] + leftRun[1]) / 2.0
                if (lastLeft < 0 || abs(c - lastLeft) < W * 0.12) {
                    left.add(doubleArrayOf(c, y.toDouble()))
                    lastLeft = c
                }
            }
            if (rightRun != null) {
                val c = (rightRun[0] + rightRun[1]) / 2.0
                if (lastRight < 0 || abs(c - lastRight) < W * 0.12) {
                    right.add(doubleArrayOf(c, y.toDouble()))
                    lastRight = c
                }
            }
        }
        // the lanes run base to base: the left and right ones are read twice over, so keep each once, in order
        fun only(points: ArrayList<DoubleArray>): List<DoubleArray> = smooth(points)
        return listOf(only(mid), only(left), only(right)).filter { it.size > 4 }
    }

    /** A polyline thinned and averaged, so a soldier walks a line and not a stair. */
    private fun smooth(points: ArrayList<DoubleArray>): List<DoubleArray> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<DoubleArray>(points.size)
        for (k in points.indices) {
            val a = points[max(0, k - 2)]
            val b = points[k]
            val c = points[min(points.size - 1, k + 2)]
            out.add(doubleArrayOf((a[0] + b[0] + c[0]) / 3.0, (a[1] + b[1] + c[1]) / 3.0))
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------- one frame
    override fun step(dt: Double) {
        clock += dt
        tod = (startTod + clock / day) % 1.0
    }

    override fun render() {
        System.arraycopy(base, 0, frame, 0, base.size)
        drawWater()
        drawSoldiers()
        finish()
    }

    /** The water keeps the painter's colours; what moves is its light: a slow swell and now and then a glint. */
    private fun drawWater() {
        val T = waterTime(clock)
        for (k in waterIdx.indices) {
            val i = waterIdx[k]
            val x = i % W
            val y = i / W
            val swell = valueNoise((x + T.sw * 0.5) / (14.0 * u), (y + T.sv) / (10.0 * u), seed + 5)
            val ripple = valueNoise((x + T.r1 * 0.4) / (5.0 * u), (y - T.r2 * 0.3) / (4.0 * u), seed + 9)
            val v = swell * 0.65 + ripple * 0.35
            var c = scalePacked(base[i], 0.90 + 0.20 * v)
            if (v > 0.86 && ((x * 7 + y * 3 + T.tq) and 7) == 0) c = mixPacked(c, 0xF2FFFF, 0.5)
            frame[i] = c
        }
    }

    private fun drawSoldiers() {
        if (lanes.isEmpty()) return
        val period = 48.0
        val march = 0.62
        val s = u
        for (road in lanes.indices) {
            val pts = lanes[road]
            for (k in 0 until 3) {
                for (team in 0..1) {
                    val phase = k / 3.0
                    val t = (clock / period + phase) % 1.0
                    if (t > march) continue
                    val along = t / march
                    val pt = pointAt(pts, if (team == 0) 1.0 - along else along)
                    val col = if (team == 0) palette.rift("#3fa9f5") else palette.rift("#d94a3d")
                    val bob = if (((clock * 3 + phase * 6).toInt()) % 2 == 0) 0.0 else -1.0
                    val x = pt[0]
                    val y = pt[1] + bob
                    ellipse(x, y + 1.2 * s, 1.5 * s, 0.6 * s, 0x40231c26)
                    rect(x - 0.7 * s, y - 1.7 * s, x + 0.7 * s, y + 0.6 * s, col)
                    block(x, y - 2.6 * s, mixPacked(col, 0xFFF2D0, 0.6))
                }
            }
        }
    }

    private fun pointAt(pts: List<DoubleArray>, s: Double): DoubleArray {
        val n = pts.size - 1
        if (n < 1) return pts[0]
        val f = clamp(s) * n
        val k = min(n - 1, f.toInt())
        val t = f - k
        return doubleArrayOf(lerp(pts[k][0], pts[k + 1][0], t), lerp(pts[k][1], pts[k + 1][1], t))
    }

    private fun finish() {
        val hl = hourLight(tod)
        if (!hl.plain) {
            val key = "${(hl.night * 24).roundToInt()},${(hl.dusk * 24).roundToInt()},${hl.morning}"
            val t = tables.getOrPut(key) { hourTables(hl) }
            scratch = gradeFrame(frame, W, H, t, hl.night, lightsAt(hl.night), scratch)
        }
        scrim()
        // dusk and night bring their own weather: a few fireflies over the woods
        val night = hl.night
        if (night > 0.2) {
            for (k in 0 until 12) {
                val bx = latticeRandom(k, 21, seed) * W
                val by = (0.2 + latticeRandom(k, 22, seed) * 0.6) * H
                if (twinkle(clock + k, k * 1.7, 0.7) < 0.55) continue
                val x = (bx + sin(clock * 0.6 + k) * 4 * u).roundToInt()
                val y = (by + cos(clock * 0.45 + k * 1.3) * 3 * u).roundToInt()
                if (x in 0 until W && y in 0 until H) frame[y * W + x] = palette.rift("#e9ff8f")
            }
        }
    }

    private fun lightsAt(night: Double): List<Light> {
        if (night < 0.12) return emptyList()
        val k = clamp((night - 0.12) * 2.4)
        return lights.map { Light(it.x, it.y, it.r * (0.65 + 0.35 * k), it.k) }
    }

    /** The two bands a phone keeps its clock and dock in, darkened, so a painted map can run to the edges. */
    private fun scrim() {
        val top = 0.135 * H
        val bottom = 0.125 * H
        for (y in 0 until H) {
            val k = (0.62 + 0.38 * smoothstep(0.0, top, y.toDouble())) *
                (0.62 + 0.38 * smoothstep(0.0, bottom, (H - 1 - y).toDouble()))
            if (k >= 0.999) continue
            val row = y * W
            for (x in 0 until W) frame[row + x] = scalePacked(frame[row + x], k)
        }
    }

    override fun report(): List<String> = listOf(
        "baked rift \"$phrase\" ${W}x$H seed $seed",
        "lanes: " + lanes.joinToString(", ") { "${it.size} points" },
        "water pixels ${waterIdx.size}, lights ${lights.size}, mask cells $maskCells",
    )

    private fun block(x: Double, y: Double, c: Int) {
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        if (xi < 0 || yi < 0 || xi >= W || yi >= H) return
        frame[yi * W + xi] = mixPacked(frame[yi * W + xi], c, alphaOf(c) / 255.0)
    }

    private fun rect(xa: Double, ya: Double, xb: Double, yb: Double, c: Int) {
        for (y in ya.roundToInt()..yb.roundToInt()) {
            for (x in xa.roundToInt()..xb.roundToInt()) block(x.toDouble(), y.toDouble(), c)
        }
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, c: Int) {
        if (rx <= 0.0 || ry <= 0.0) return
        val xa = (cx - rx).toInt()
        val xb = (cx + rx).toInt() + 1
        val ya = (cy - ry).toInt()
        val yb = (cy + ry).toInt() + 1
        for (y in ya..yb) {
            for (x in xa..xb) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1.0) block(x.toDouble(), y.toDouble(), c)
            }
        }
    }
}
