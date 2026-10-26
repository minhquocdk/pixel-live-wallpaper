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

    /** Water pixels that touch something that is not water: the banks, where the foam lives. */
    private val bankIdx = ArrayList<Int>(W * H / 40)

    /** The emptiness near the plateau's edge, where the fog drifts and nothing else is. */
    private val fogIdx = ArrayList<Int>(W * H / 20)

    /** Where the woods stand, so leaves can drift down from them. */
    private val woods = ArrayList<DoubleArray>()

    /** A glow's falloff, by radius: built once, reused every frame. */
    private val falloff = HashMap<Int, DoubleArray>()
    private val glowCache = HashMap<String, IntArray>()

    // Noise, precomputed once and read by offset: a per-pixel noise call costs more than the whole picture.
    private val tileN = 128
    private val fogTile: FloatArray by lazy { noiseTile(tileN, 12) { x, y -> indexRandom(x * 131 + y, seed + 31) } }
    private val swellTile: FloatArray by lazy { noiseTile(tileN, 10) { x, y -> indexRandom(x * 97 + y, seed + 5) } }
    private val rippleTile: FloatArray by lazy { noiseTile(tileN, 26) { x, y -> indexRandom(x * 61 + y, seed + 9) } }

    private fun at(tile: FloatArray, x: Int, y: Int): Double =
        tile[((y and (tileN - 1)) * tileN) + (x and (tileN - 1))].toDouble()

    /** The three lanes, as polylines read out of the mask: mid, left, right. */
    val lanes: List<List<DoubleArray>>

    private val lights = ArrayList<Light>(64)
    private val tables = HashMap<String, HourTables>()
    private var scratch: FloatArray? = null
    private var clock = 0.0
    private var tod = startTod
    private val day = if (dayLength <= 0.0) 600.0 else dayLength
    private val u = max(0.55, W / 307.0)

    private val blueTeam = palette.rift("#4fb4ff")
    private val redTeam = palette.rift("#e8564a")
    private val blueDeep = palette.rift("#1f5c94")
    private val redDeep = palette.rift("#8f2b23")
    private val fogColour = palette.rift("#1d2a3c")
    private val leafGold = palette.rift("#b7a04a")
    private val leafGreen = palette.rift("#5d8a48")
    private val lampGlow = 0xFFE7A9.toInt()
    private val nexusGlow = 0xFFDCEFFF.toInt()
    private val pitGlow = 0xFFFFB877.toInt()

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
        for (y in 1 until H - 1) {
            for (x in 1 until W - 1) {
                val i = y * W + x
                if (kind[i].toInt() != 4) continue
                if (kind[i - 1].toInt() != 4 || kind[i + 1].toInt() != 4 ||
                    kind[i - W].toInt() != 4 || kind[i + W].toInt() != 4
                ) {
                    bankIdx.add(i)
                }
            }
        }
        // the band of emptiness just outside the plateau: thick fog, no detail
        val near = (26 * u).toInt()
        for (y in 0 until H) {
            for (x in 0 until W) {
                val i = y * W + x
                if (kind[i].toInt() != 0) continue
                val insideX = x > W * 0.02 && x < W * 0.98
                val insideY = y > H * 0.02 && y < H * 0.98
                if (!insideX || !insideY) continue
                val nearEdge = x < near || x > W - near || y < near || y > H - near
                if (nearEdge) fogIdx.add(i)
            }
        }
        for (piece in RiftPlan.all()) {
            if (piece.kind != "forest") continue
            woods.add(
                doubleArrayOf(
                    RiftPlan.across(piece.p) * W,
                    RiftPlan.down(piece.q) * H,
                ),
            )
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
        drawFog()
        drawWater()
        drawSoldiers()
        drawLeaves()
        finish()
    }

    /** The emptiness is not still: fog drifts across it, very slowly, and never in the two bands a phone needs. */
    private fun drawFog() {
        if (fogIdx.isEmpty()) return
        val drift = (clock * 0.9).toInt()
        val block = max(2, (2 * u).toInt())
        // one noise read a block: the fog is a slow smear, it does not need a value a pixel
        val grid = IntArray(((W / block) + 2) * ((H / block) + 2))
        val gw = (W / block) + 2
        for (gy in 0 until (H / block) + 2) {
            for (gx in 0 until gw) {
                val n = at(fogTile, (gx * block + drift) / 2, (gy * block / 2 + drift / 3))
                grid[gy * gw + gx] = (n * 1000).toInt()
            }
        }
        for (i in fogIdx) {
            val x = i % W
            val y = i / W
            val n = grid[(y / block) * gw + (x / block)]
            if (n < 520) continue
            val k = (n - 520) * 0.00055
            frame[i] = mixPacked(frame[i], fogColour, k)
        }
    }

    /**
     * The river keeps the painter's colours; what moves is its light. A slow swell and a finer ripple lighten and
     * darken it a step, glints come and go on the crests, and foam breathes along the banks.
     */
    private fun drawWater() {
        val T = waterTime(clock)
        val flow = clock * 6
        for (k in waterIdx.indices) {
            val i = waterIdx[k]
            val x = i % W
            val y = i / W
            // the river runs across the picture, so the swell drifts sideways and the ripples a little against it
            val swell = at(swellTile, (x + flow.toInt()) / 3, (y + T.sv) / 3)
            val ripple = at(rippleTile, (x + T.r1) / 2, (y - T.r2) / 2)
            val v = swell * 0.6 + ripple * 0.4
            var c = scalePacked(base[i], 0.92 + 0.16 * v)
            // a glint on a crest, now and then, never twice in the same place
            if (v > 0.84) {
                val h = latticeHash3(x * 3 + T.glz, y * 5, 7)
                if (h < 0.035) c = mixPacked(c, 0xF4FFFF, 0.55)
            }
            frame[i] = c
        }
        // foam along the banks: two tones, breathing in and out
        for (j in bankIdx.indices) {
            val i = bankIdx[j]
            val x = i % W
            val y = i / W
            val b = sin(clock * 1.6 + x * 0.16 + y * 0.09 + (j and 7))
            if (b < 0.1) continue
            val k = (b - 0.1) * 0.45
            frame[i] = mixPacked(frame[i], if (b > 0.75) 0xE8FCFB else 0xB6DCDA, k)
        }
    }

    /** One soldier: a shadow, a cloak in the side's colour, a pale helm, and a spear that catches the light. */
    private fun soldier(x: Double, y: Double, team: Int, s: Double, step: Double) {
        val col = if (team == 0) blueTeam else redTeam
        val dark = if (team == 0) blueDeep else redDeep
        val sway = if (step < 0.5) -0.5 else 0.5
        // shadow
        blend(x, y + 1.4 * s, 0x55201a1a, 1.0)
        for (dx in -1..1) {
            for (dy in 0..1) blend(x + dx * 0.9 * s, y + 1.1 * s + dy * 0.4 * s, 0x55201a1a, if (dx == 0) 1.0 else 0.6)
        }
        // body: two rows of cloak, one of legs
        for (dx in -1..1) {
            blend(x + dx * 0.9 * s, y - 1.6 * s, col, 1.0)
            blend(x + dx * 0.9 * s, y - 0.6 * s, dark, 1.0)
        }
        blend(x + sway * 0.8 * s, y + 0.2 * s, dark, 0.9)
        // helm, one pale pixel, and a spear at the side
        blend(x, y - 2.5 * s, mixPacked(col, 0xFFF3D6, 0.62), 1.0)
        blend(x + 1.7 * s, y - 3.4 * s, 0xD8DCE8, 0.95)
        blend(x + 1.7 * s, y - 2.4 * s, 0x9AA0AE, 0.9)
        blend(x + 1.7 * s, y - 4.2 * s, 0xF2F5FF, 0.9)
    }

    private fun drawSoldiers() {
        if (lanes.isEmpty()) return
        val period = 48.0
        val march = 0.60
        val s = u
        val night = hourLight(tod).night
        for (road in lanes.indices) {
            val pts = lanes[road]
            for (k in 0 until 3) {
                for (team in 0..1) {
                    val phase = (k / 3.0 + road * 0.11) % 1.0
                    val t = (clock / period + phase) % 1.0
                    if (t > march) continue
                    val along = t / march
                    // the blue side walks up the picture, the red side down it, as the map is turned
                    val pt = pointAt(pts, if (team == 0) 1.0 - along else along)
                    val bob = if (((clock * 4 + phase * 6).toInt()) % 2 == 0) 0.0 else -0.7
                    // a faint dust puff under a marching boot
                    if (((clock * 6).toInt() + k) % 3 == 0) {
                        blend(pt[0] - s * 1.2, pt[1] + s * 1.6, 0x33d8c9a8, 0.5)
                    }
                    soldier(pt[0], pt[1] + bob, team, s, (clock * 4 + phase * 6) % 1.0)
                    if (night > 0.25) {
                        // the little lamp each soldier carries, and the glow it throws
                        blend(pt[0], pt[1] - 4.6 * s, 0xFFE9A8, 0.9)
                    }
                }
            }
            // where the two sides meet over the river, the fight: three sparks, then gone
            val clash = (clock / period + road * 0.11) % 1.0
            if (clash > 0.30 && clash < 0.42) {
                val pt = pointAt(pts, 0.5)
                val k = sin((clash - 0.30) * 40) * 0.5 + 0.5
                spark(pt[0], pt[1] - 2 * s, 5.5 * s * (0.5 + k), 0xFFF2C0, 0.5 + 0.4 * k)
            }
        }
    }

    /** A few leaves come down from the four woods, all day, so the map is never quite still. */
    private fun drawLeaves() {
        if (woods.isEmpty()) return
        val s = u
        for (k in 0 until 14) {
            val w = woods[k % woods.size]
            val t = (clock * (0.10 + 0.02 * k) + k * 0.37) % 1.0
            val x = w[0] + sin(t * 6.3 + k) * 16 * s
            val y = w[1] - 26 * s + t * 76 * s
            if (x < 1 || y < 1 || x >= W - 1 || y >= H - 1) continue
            val night = hourLight(tod).night
            val c = mixPacked(if (k % 3 == 0) leafGold else leafGreen, 0x101418, 0.25 + 0.45 * night)
            blend(x, y, c, 0.95)
            blend(x + 0.8 * s, y + 0.5 * s, mixPacked(c, 0x000000, 0.4), 0.7)
        }
    }

    private fun finish() {
        val hl = hourLight(tod)
        if (!hl.plain) {
            val key = "${(hl.night * 24).roundToInt()},${(hl.dusk * 24).roundToInt()},${hl.morning}"
            val t = tables.getOrPut(key) { hourTables(hl) }
            scratch = gradeFrame(frame, W, H, t, hl.night, lightsAt(hl.night), scratch)
        }
        // the light itself: a bloom over every lit thing, added after the grade, as real light is
        val night = hl.night
        if (night > 0.12) bloom(night)
        scrim()
    }

    /** Bloom: the glow of each lamp and crystal, and its reflection stretched down the water under it. */
    private fun bloom(night: Double) {
        val k = clamp((night - 0.12) * 2.2)
        for (l in lights) {
            val isPit = l.r > 20 * u
            val colour = when {
                isPit -> pitGlow
                l.r > 15 * u -> nexusGlow
                else -> lampGlow
            }
            val pulse = if (isPit) 0.82 + 0.18 * sin(clock * 0.8 + l.x) else 1.0
            addGlow(l.x, l.y, l.r * 1.15, colour, (0.32 + 0.30 * k) * pulse)
            // a smear on the water below, for the two great pits
            if (isPit) reflect(l.x, l.y, l.r * 0.9, colour, 0.22 * k)
        }
        // lamps carried by the soldiers, once it is properly dark
        if (k > 0.5 && lanes.isNotEmpty()) {
            val period = 48.0
            val march = 0.60
            for (road in lanes.indices) {
                val pts = lanes[road]
                for (j in 0 until 3) {
                    for (team in 0..1) {
                        val phase = (j / 3.0 + road * 0.11) % 1.0
                        val t = (clock / period + phase) % 1.0
                        if (t > march) continue
                        val pt = pointAt(pts, if (team == 0) 1.0 - t / march else t / march)
                        addGlow(pt[0], pt[1] - 4 * u, 5 * u, 0xFFD98A, 0.22 * k)
                    }
                }
            }
        }
    }

    private fun falloffFor(r: Int): DoubleArray = falloff.getOrPut(r) {
        val n = r * 2 + 1
        DoubleArray(n * n) { idx ->
            val dx = (idx % n) - r
            val dy = (idx / n) - r
            val d = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) / r
            if (d >= 1.0) 0.0 else (1.0 - d) * (1.0 - d)
        }
    }

    /**
     * A glow, laid on. The strength is quantised to sixteenths and the whole thing cached, so a lamp that pulses costs
     * a lookup and an add a pixel, not a falloff and three multiplies: thirty lamps a frame is most of the frame.
     */
    private fun addGlow(cx: Double, cy: Double, r: Double, colour: Int, strength: Double) {
        val rad = max(2, r.toInt())
        val step = (clamp(strength) * 16).roundToInt().coerceIn(0, 16)
        if (step == 0) return
        // a key with the three things in it, and nothing else: a packed integer had room for a small radius only,
        // and two radii sharing a key shared the wrong table
        val key = "$rad/${colour and 0xFFFFFF}/$step"
        val lit = glowCache.getOrPut(key) {
            val f = falloffFor(rad)
            val n = rad * 2 + 1
            val out = IntArray(n * n)
            val s = step / 16.0
            for (k in 0 until n * n) {
                val a = f[k] * s
                if (a < 0.01) continue
                out[k] = (
                    ((redOf(colour) * a).roundToInt() shl 16) or
                        ((greenOf(colour) * a).roundToInt() shl 8) or
                        (blueOf(colour) * a).roundToInt()
                    )
            }
            out
        }
        val n = rad * 2 + 1
        val x0 = (cx - rad).toInt()
        val y0 = (cy - rad).toInt()
        for (dy in 0 until n) {
            val y = y0 + dy
            if (y < 0 || y >= H) continue
            val row = y * W
            val gRow = dy * n
            for (dx in 0 until n) {
                val add = lit[gRow + dx]
                if (add == 0) continue
                val x = x0 + dx
                if (x < 0 || x >= W) continue
                val i = row + x
                val c = frame[i]
                val r2 = min(255, ((c ushr 16) and 255) + ((add ushr 16) and 255))
                val g2 = min(255, ((c ushr 8) and 255) + ((add ushr 8) and 255))
                val b2 = min(255, (c and 255) + (add and 255))
                frame[i] = (0xFF shl 24) or (r2 shl 16) or (g2 shl 8) or b2
            }
        }
    }

    /** A glow's reflection: the same light, stretched thin, only where the mask says there is water. */
    private fun reflect(cx: Double, cy: Double, r: Double, colour: Int, strength: Double) {
        val h = r * 2.2
        val w = r * 0.85
        val x0 = (cx - w).toInt()
        val x1 = (cx + w).toInt()
        val y0 = (cy + r * 0.4).toInt()
        val y1 = (cy + r * 0.4 + h).toInt()
        val f = falloffFor(max(2, w.toInt()))
        val n = max(2, w.toInt()) * 2 + 1
        for (y in y0..y1) {
            if (y < 0 || y >= H) continue
            val t = 1.0 - (y - y0).toDouble() / max(1, y1 - y0)
            for (x in x0..x1) {
                if (x < 0 || x >= W) continue
                if (kind[y * W + x].toInt() != 4) continue
                val fx = (x - x0).coerceIn(0, n - 1)
                val fy = (y - y0).coerceIn(0, n - 1)
                val a = f[fy * n + fx] * strength * t
                if (a < 0.01) continue
                val i = y * W + x
                val src = frame[i]
                frame[i] = rgb(
                    redOf(src) + (255 - redOf(src)) * a * redOf(colour) / 255.0,
                    greenOf(src) + (255 - greenOf(src)) * a * greenOf(colour) / 255.0,
                    blueOf(src) + (255 - blueOf(src)) * a * blueOf(colour) / 255.0,
                )
            }
        }
    }

    private fun spark(cx: Double, cy: Double, r: Double, colour: Int, strength: Double) {
        addGlow(cx, cy, r, colour, strength)
        for (k in 0 until 5) {
            val a = k * 1.256 + clock * 3
            blend(cx + cos(a) * r * 0.7, cy + sin(a) * r * 0.7, colour, 0.85)
        }
    }

    private fun lightsAt(night: Double): List<Light> {
        if (night < 0.12) return emptyList()
        val k = clamp((night - 0.12) * 2.4)
        return lights.map { Light(it.x, it.y, it.r * (0.65 + 0.35 * k), it.k) }
    }

    /** The two bands a phone keeps its clock and dock in, darkened, so a painted map can run to the edges. */
    private fun scrim() {
        for (y in 0 until H) {
            val ki = scrimAt(y)
            if (ki >= 256) continue
            val row = y * W
            for (x in 0 until W) {
                val c = frame[row + x]
                val r = (((c ushr 16) and 255) * ki) ushr 8
                val g = (((c ushr 8) and 255) * ki) ushr 8
                val b = ((c and 255) * ki) ushr 8
                frame[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /** The scrim never changes with the clock, so its rows are worked out once, as whole numbers out of 256. */
    private val scrimRows: IntArray by lazy {
        val top = 0.135 * H
        val bottom = 0.125 * H
        IntArray(H) { y ->
            val k = (0.62 + 0.38 * smoothstep(0.0, top, y.toDouble())) *
                (0.62 + 0.38 * smoothstep(0.0, bottom, (H - 1 - y).toDouble()))
            (k * 256.0).roundToInt()
        }
    }

    private fun scrimAt(y: Int): Int = scrimRows[y]

    override fun report(): List<String> = listOf(
        "baked rift \"$phrase\" ${W}x$H seed $seed",
        "lanes: " + lanes.joinToString(", ") { "${it.size} points" },
        "water pixels ${waterIdx.size}, banks ${bankIdx.size}, fog ${fogIdx.size}, lights ${lights.size}",
    )

    /** A pixel laid over the picture with an alpha: 0xAA in the top byte, as a bitmap packs it. */
    private fun blend(x: Double, y: Double, c: Int, k: Double) {
        val xi = x.roundToInt()
        val yi = y.roundToInt()
        if (xi < 0 || yi < 0 || xi >= W || yi >= H) return
        val i = yi * W + xi
        val a = (alphaOf(c) / 255.0) * k
        frame[i] = mixPacked(frame[i], c or (0xFF shl 24), a)
    }

    private fun pointAt(pts: List<DoubleArray>, s: Double): DoubleArray {
        val n = pts.size - 1
        if (n < 1) return pts[0]
        val f = clamp(s) * n
        val k = min(n - 1, f.toInt())
        val t = f - k
        return doubleArrayOf(lerp(pts[k][0], pts[k + 1][0], t), lerp(pts[k][1], pts[k + 1][1], t))
    }


}
