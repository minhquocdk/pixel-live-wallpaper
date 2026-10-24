// The Rift's tall map, drawn a pixel at a time: the plan in RiftPlan projected into a phone's frame, a plateau of grass
// and lanes in dark emptiness, a river with one bridge, walled forests, its two bases and every tower between them, and
// the soldiers of both sides walking out along the roads.
//
// Everything static is drawn once into `base`; a frame copies it, moves the water and the soldiers over it, and grades
// the hour over that. The two bands where a phone keeps its clock and its dock are darkened at the end, so the map can
// run to the edges of the screen and the clock still reads.
package dev.almanac.island

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One soldier walking out along a road: which road, which side, how far into its beat, and its speed. */
private class Soldier(val road: Int, val team: Int, val phase: Double, val speed: Double)

/** One crab in the shallows: where it is across the river, and which way it is going. */
private class Crab(var p: Double, var dir: Double, val lane: Int)

class RiftScene(
    override val phrase: String = "twin-rivers-8",
    override val W: Int = 307,
    override val H: Int = 682,
    val detail: Double = 1.0,
    val dayLength: Double = 600.0,
    override val theme: Theme = Theme.RIFT,
    val startTod: Double = 0.30,
) : LiveScene {
    override val palette = Palette(theme)
    override val seed: Int = seedOf(phrase)
    override val frame = IntArray(W * H)

    private val base = IntArray(W * H)

    /** What each pixel is: 0 void, 1 grass, 2 road, 3 stone, 4 water, 5 bridge. */
    private val kind = ByteArray(W * H)

    private val waterIdx = ArrayList<Int>(8192)
    private val waterDepth = ArrayList<Double>(8192)

    private val lights = ArrayList<Light>(64)
    private val tables = HashMap<String, HourTables>()
    private var scratch: FloatArray? = null

    private var clock = 0.0
    private var tod = startTod
    private val day = if (dayLength <= 0.0) 600.0 else dayLength

    private val u = max(0.55, W / 307.0) * clamp(detail, 0.8, 1.6)

    // The plateau, in screen pixels.
    private val px0 = RiftPlan.across(0.0) * W
    private val px1 = RiftPlan.across(1.0) * W
    private val py0 = RiftPlan.down(0.0) * H
    private val py1 = RiftPlan.down(1.0) * H
    private val corner = min(px1 - px0, py1 - py0) * 0.045

    // Colours.
    private val grass = listOf("#6f9b45", "#547a37", "#7aa64d", "#5f8a3e").map { palette.rift(it) }
    private val grassDark = palette.rift("#43632f")
    private val dirt = palette.rift("#b99a68")
    private val dirtRim = palette.rift("#8f7448")
    private val stone = palette.rift("#8a8f96")
    private val stoneDark = palette.rift("#4a4a52")
    private val stoneMid = palette.rift("#6a6f78")
    private val ink = palette.rift("#22242c")
    private val gold = palette.rift("#e8c15a")
    private val blue = palette.rift("#3fa9f5")
    private val red = palette.rift("#d94a3d")
    private val blueDeep = palette.rift("#1e5f94")
    private val redDeep = palette.rift("#8c2620")
    private val violet = palette.rift("#8a5bd6")
    private val ember = palette.rift("#e07a3c")
    private val wood = palette.rift("#a9763f")
    private val woodDark = palette.rift("#6f4a26")
    private val canopy = listOf("#2c4a2e", "#3a5f38", "#47773f", "#2f5230").map { palette.rift(it) }
    private val bank = palette.rift("#4d6b45")
    private val voidTop = palette.rift("#0a0d14")
    private val voidLow = palette.rift("#171b26")

    private val waterPal = WaterPalette(
        LV = listOf("#2f7fa8", "#3a8eb2", "#46a0bd", "#54c7c0", "#3d93b4", "#327f9e", "#286a86")
            .map { palette.rift(it) }.toIntArray(),
        FOAM = palette.rift("#eafcff"),
        FOAM2 = palette.rift("#b9eef2"),
        GLINT = palette.rift("#ffffff"),
        GLINT2 = palette.rift("#d8f7ff"),
        WEED = palette.rift("#3f7f72"),
        WEED2 = palette.rift("#4d9382"),
        SAND = listOf("#8d9770", "#7a8a66", "#6c7f5e").map { palette.rift(it) }.toIntArray(),
    )

    private val soldiers = ArrayList<Soldier>(24)
    private val crabs = ArrayList<Crab>(4)
    private val blueRoads: List<List<DoubleArray>>

    override val summary: String
        get() = "${RiftPlan.all().count { it.kind == "tower" }} towers, ${RiftPlan.all().count { it.kind == "inhibitor" }} inhibitors, " +
            "${soldiers.size} soldiers, 2 pits"

    init {
        blueRoads = listOf(blueHalfOf(RiftPlan.MID), RiftPlan.LEFT, RiftPlan.rightHalf())
        buildVoid()
        buildPlateau()
        buildRoads()
        buildRiver()
        buildForests()
        buildPieces()
        buildProps()
        placeSoldiers()
    }

    // -------------------------------------------------------------------------------------------- the ground
    private fun buildVoid() {
        for (y in 0 until H) {
            val ty = y.toDouble() / H
            for (x in 0 until W) {
                val f = sin(x / (70.0 * u) + y / (160.0 * u)) * 9 + sin(y / (40.0 * u) - x / (120.0 * u)) * 6
                val mix = clamp(0.42 + f / 44.0 + ty * 0.22)
                base[y * W + x] = mixPacked(voidTop, voidLow, mix)
            }
        }
    }

    /** How far inside the plateau's edge a point is: positive inside, negative outside, in pixels. */
    private fun depth(x: Double, y: Double): Double {
        val dx = min(x - px0, px1 - x)
        val dy = min(y - py0, py1 - y)
        if (dx >= 0 && dy >= 0) {
            if (dx >= corner || dy >= corner) return min(dx, dy)
            return corner - hypot(corner - dx, corner - dy)
        }
        val ox = max(-dx, 0.0)
        val oy = max(-dy, 0.0)
        return -hypot(ox, oy)
    }

    private fun buildPlateau() {
        val cliffTop = palette.rift("#3c414c")
        val cliffMid = palette.rift("#2f333c")
        val cliffLow = palette.rift("#23262d")
        val vine = palette.rift("#4f7a3c")
        val xa = floor(px0 - 16 * u).toInt()
        val xb = ceil(px1 + 16 * u).toInt()
        val ya = floor(py0 - 16 * u).toInt()
        val yb = ceil(py1 + 16 * u).toInt()
        val cell = max(1, (2 * u).roundToInt())
        for (y in ya..yb) {
            for (x in xa..xb) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                val i = y * W + x
                val d = depth(x.toDouble(), y.toDouble())
                if (d >= 0) {
                    kind[i] = 1
                    base[i] = if (d < 3.2 * u) {
                        kind[i] = 3
                        if (d < 0.9 * u) stoneDark else stoneMid
                    } else {
                        val n = latticeRandom(x / cell, y / cell, seed)
                        val pick = when {
                            n < 0.42 -> 1
                            n < 0.72 -> 0
                            n < 0.9 -> 3
                            else -> 2
                        }
                        if (bayerThreshold(x, y) > 0.94) grassDark else grass[pick]
                    }
                } else {
                    val out = -d
                    base[i] = when {
                        out < 3 * u -> cliffTop
                        out < 7 * u -> cliffMid
                        out < 13 * u -> cliffLow
                        else -> base[i]
                    }
                    if (out < 13 * u && latticeRandom(x, y, seed + 3) > 0.93) base[i] = vine
                    if (out < 1.5 * u) kind[i] = 3
                }
            }
        }
    }

    private fun strokeRoad(pts: List<DoubleArray>, w: Double, c: Int) {
        for (k in 1 until pts.size) {
            val x0 = pts[k - 1][0]
            val y0 = pts[k - 1][1]
            val x1 = pts[k][0]
            val y1 = pts[k][1]
            val steps = max(2, ceil(hypot(x1 - x0, y1 - y0)).toInt() * 2)
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val cx = lerp(x0, x1, t)
                val cy = lerp(y0, y1, t)
                disc(cx, cy, w / 2, c, markKind = 2)
            }
        }
    }

    private fun toScreen(road: List<DoubleArray>): List<DoubleArray> =
        road.map { doubleArrayOf(RiftPlan.across(it[0]) * W, RiftPlan.down(it[1]) * H) }

    private fun buildRoads() {
        val roads = ArrayList<Pair<List<DoubleArray>, Double>>()
        roads.add(RiftPlan.MID to RiftPlan.LANE_WIDE)
        roads.add(RiftPlan.LEFT to RiftPlan.LANE_NARROW)
        roads.add(RiftPlan.rightHalf() to RiftPlan.LANE_NARROW)
        for (r in roads.toList()) roads.add(r.first.map { doubleArrayOf(1 - it[0], 1 - it[1]) } to r.second)
        val scale = px1 - px0
        for ((road, wPlan) in roads) {
            val pts = toScreen(road)
            val w = max(2.0, wPlan * scale)
            strokeRoad(pts, w + 2 * u, dirtRim)
            strokeRoad(pts, w, dirt)
            // cart tracks: two thin darker lines, one either side of the spine of the road
            for (k in 1 until pts.size) {
                val dx = pts[k][0] - pts[k - 1][0]
                val dy = pts[k][1] - pts[k - 1][1]
                val len = hypot(dx, dy)
                if (len < 0.001) continue
                val nx = -dy / len
                val ny = dx / len
                for (side in listOf(-1.0, 1.0)) {
                    val off = side * w * 0.18
                    line(
                        pts[k - 1][0] + nx * off, pts[k - 1][1] + ny * off,
                        pts[k][0] + nx * off, pts[k][1] + ny * off,
                        mixPacked(dirt, dirtRim, 0.55),
                    )
                }
            }
        }
    }

    private fun buildRiver() {
        val half = RiftPlan.RIVER_HALF * (py1 - py0)
        val xa = floor(RiftPlan.across(RiftPlan.RIVER_FROM) * W).toInt()
        val xb = ceil(RiftPlan.across(RiftPlan.RIVER_TO) * W).toInt()
        val deep = waterPal.LV[3]
        val shallow = waterPal.LV[0]
        for (x in xa..xb) {
            if (x < 0 || x >= W) continue
            val p = (x / W.toDouble() - RiftPlan.INSET_X) / (1 - 2 * RiftPlan.INSET_X)
            val cy = RiftPlan.down(RiftPlan.riverQ(p)) * H
            val ya = ceil(cy - half).toInt()
            val yb = floor(cy + half).toInt()
            for (y in ya..yb) {
                if (y < 0 || y >= H) continue
                val i = y * W + x
                if (kind[i].toInt() == 3) continue
                val t = abs(y - cy) / half
                base[i] = mixPacked(deep, shallow, t)
                kind[i] = 4
                waterIdx.add(i)
                waterDepth.add((1 - t) * 44)
            }
            for (y in intArrayOf(ya - 1, yb + 1)) {
                if (y < 0 || y >= H) continue
                val i = y * W + x
                if (kind[i].toInt() == 3 || kind[i].toInt() == 4) continue
                base[i] = if (latticeRandom(x, y, seed + 11) > 0.5) bank else mixPacked(bank, grassDark, 0.5)
            }
        }
        buildBridge()
    }

    private fun buildBridge() {
        val xa = RiftPlan.across(RiftPlan.BRIDGE_FROM) * W
        val xb = RiftPlan.across(RiftPlan.BRIDGE_TO) * W
        val half = RiftPlan.RIVER_HALF * (py1 - py0)
        val pa = RiftPlan.BRIDGE_FROM
        val pb = RiftPlan.BRIDGE_TO
        val qa = RiftPlan.riverQ(pa)
        val qb = RiftPlan.riverQ(pb)
        val top = RiftPlan.down(min(qa, qb)) * H - half - 2 * u
        val bottom = RiftPlan.down(max(qa, qb)) * H + half + 2 * u
        for (y in top.toInt()..bottom.toInt()) {
            for (x in xa.toInt()..xb.toInt()) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                val i = y * W + x
                val edge = x <= xa.toInt() + u || x >= xb.toInt() - u
                kind[i] = 5
                base[i] = when {
                    edge -> woodDark
                    (y % max(2, (2 * u).roundToInt())) == 0 -> woodDark
                    else -> wood
                }
            }
        }
        // the bridge's rails, a shade lighter, at either side
        for (y in top.toInt()..bottom.toInt()) {
            for (x in intArrayOf(xa.toInt() + 1, xb.toInt() - 1)) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                base[y * W + x] = gold
            }
        }
    }

    // -------------------------------------------------------------------------------------------- what stands on it
    private fun buildForests() {
        for (piece in RiftPlan.all()) {
            if (piece.kind != "forest") continue
            val cx = RiftPlan.across(piece.p) * W
            val cy = RiftPlan.down(piece.q) * H
            val rx = (px1 - px0) * 0.125
            val ry = (py1 - py0) * 0.100
            // the mass: one dark shape, dithered at its edge, a few crowns over it
            ellipse(cx, cy, rx, ry, canopy[0], markKind = 1)
            for (k in 1 until 4) {
                val ang = k * 2.1 + piece.p * 6.0
                val ox = cos(ang) * rx * 0.34
                val oy = sin(ang) * ry * 0.30
                ellipse(cx + ox, cy + oy, rx * 0.55, ry * 0.55, canopy[k], markKind = 1)
            }
            for (y in floor(cy - ry).toInt()..ceil(cy + ry).toInt()) {
                for (x in floor(cx - rx).toInt()..ceil(cx + rx).toInt()) {
                    if (x < 0 || y < 0 || x >= W || y >= H) continue
                    val ex = (x - cx) / rx
                    val ey = (y - cy) / ry
                    val dd = ex * ex + ey * ey
                    if (dd > 1.0) continue
                    if (dd > 0.86 && bayerThreshold(x, y) > (1.0 - dd) * 4.4) base[y * W + x] = grassDark
                    else if (latticeRandom(x, y, seed + 21) > 0.8) base[y * W + x] = canopy[3]
                }
            }
            forestWall(cx, cy, rx, ry, if (piece.p < 0.5) 0.0 else PI)
        }
    }

    private fun forestWall(cx: Double, cy: Double, rx: Double, ry: Double, gate: Double) {
        val steps = max(48, ((rx + ry) / (1.4 * u)).toInt())
        for (k in 0 until steps) {
            val a = k * 2 * PI / steps
            val diff = abs(((a - gate + PI) % (2 * PI)) - PI)
            if (diff < 0.20) continue // the gate: one way in, facing its lane
            val x = cx + cos(a) * (rx + 2.2 * u)
            val y = cy + sin(a) * (ry + 2.2 * u)
            block(x, y, stone, markKind = 3)
        }
    }

    private fun buildPieces() {
        for (piece in RiftPlan.all()) {
            val cx = RiftPlan.across(piece.p) * W
            val cy = RiftPlan.down(piece.q) * H
            when (piece.kind) {
                "nexus" -> nexusTower(cx, cy, piece.team, 1.5)
                "nexusTower" -> turret(cx, cy, piece.team, 0.85)
                "fountain" -> fountain(cx, cy)
                "tower" -> turret(cx, cy, piece.team, 1.0)
                "inhibitor" -> inhibitor(cx, cy, piece.team)
                "pit" -> pit(cx, cy, if (piece.variant == 1) ember else violet)
                "camp" -> camp(cx, cy)
                "fire" -> campfire(cx, cy)
            }
        }
        // banners along the outer wall: six a side, so the two sides are told apart at a glance
        for (k in 0 until 6) {
            val q = 0.14 + k * 0.155
            for (team in 0..1) {
                val pp = if (team == 0) 0.052 else 0.948
                val qq = if (team == 0) 1 - q else q
                banner(RiftPlan.across(pp) * W, RiftPlan.down(qq) * H, team)
            }
        }
    }

    private fun teamColour(team: Int, deep: Boolean = false): Int =
        if (team == 0) (if (deep) blueDeep else blue) else (if (deep) redDeep else red)

    private fun turret(cx: Double, cy: Double, team: Int, scale: Double) {
        val s = u * scale
        ellipse(cx, cy, 4.4 * s, 1.9 * s, stoneDark)
        rect(cx - 2.2 * s, cy - 6.5 * s, cx + 2.2 * s, cy, stone)
        rect(cx - 2.2 * s, cy - 6.5 * s, cx + 2.2 * s, cy - 5.0 * s, stoneMid)
        rect(cx - 2.2 * s, cy - 7.2 * s, cx + 2.2 * s, cy - 6.5 * s, stoneDark)
        ellipse(cx, cy - 8.4 * s, 1.7 * s, 2.3 * s, teamColour(team))
        block(cx, cy - 10.4 * s, gold)
        lights.add(Light(cx, cy - 8 * s, 9 * s, 0.75))
    }

    private fun inhibitor(cx: Double, cy: Double, team: Int) {
        val s = u * 1.1
        ellipse(cx, cy, 3.4 * s, 1.5 * s, stoneDark)
        rect(cx - 2.5 * s, cy - 1.8 * s, cx + 2.5 * s, cy, stoneMid)
        ellipse(cx, cy - 4.2 * s, 1.8 * s, 2.8 * s, teamColour(team))
        block(cx, cy - 6.4 * s, teamColour(team, deep = true))
        lights.add(Light(cx, cy - 4 * s, 7 * s, 0.7))
    }

    private fun nexusTower(cx: Double, cy: Double, team: Int, scale: Double) {
        val s = u * scale
        ellipse(cx, cy, 8.5 * s, 3.4 * s, stoneDark)
        ellipse(cx, cy - 0.6 * s, 6.6 * s, 2.4 * s, stoneMid)
        ellipse(cx, cy - 0.6 * s, 4.6 * s, 1.5 * s, gold)
        rect(cx - 2.6 * s, cy - 5.5 * s, cx + 2.6 * s, cy, stone)
        // the crystal: a tall diamond of the side's colour, with one white glint
        for (k in 0 until 7) {
            val w = (2.6 - abs(k - 3.2) * 0.55) * s
            for (x in floor(cx - w).toInt()..ceil(cx + w).toInt()) {
                block(x.toDouble(), cy - (9.6 - k) * s, if (k == 2) mixPacked(teamColour(team), 0xFFFFFF, 0.55) else teamColour(team))
            }
        }
        block(cx, cy - 11.4 * s, gold)
        lights.add(Light(cx, cy - 6 * s, 16 * s, 1.0))
    }

    private fun fountain(cx: Double, cy: Double) {
        val s = u * 1.2
        ellipse(cx, cy, 5.4 * s, 2.4 * s, stoneDark)
        ellipse(cx, cy - 0.4 * s, 4.0 * s, 1.7 * s, waterPal.LV[2])
        ellipse(cx, cy - 0.4 * s, 2.4 * s, 1.0 * s, waterPal.FOAM2)
    }

    private fun pit(cx: Double, cy: Double, glow: Int) {
        val s = u * 1.0
        ellipse(cx, cy, 11.0 * s, 8.4 * s, ink)
        ellipse(cx, cy, 9.4 * s, 7.0 * s, mixPacked(ink, glow, 0.22))
        ellipse(cx, cy, 6.4 * s, 4.6 * s, mixPacked(ink, glow, 0.55))
        ellipse(cx, cy, 3.6 * s, 2.6 * s, glow)
        ellipse(cx, cy, 1.8 * s, 1.2 * s, mixPacked(glow, 0xFFFFFF, 0.6))
        // a stone rim, one block at a time
        val steps = max(40, (10 * s * 3).toInt())
        for (k in 0 until steps) {
            val a = k * 2 * PI / steps
            block(cx + cos(a) * 11.2 * s, cy + sin(a) * 8.6 * s, if (k % 3 == 0) stone else stoneDark)
        }
        lights.add(Light(cx, cy, 26 * s, 1.0))
    }

    private fun camp(cx: Double, cy: Double) {
        val s = u
        ellipse(cx, cy, 3.2 * s, 1.4 * s, stoneDark)
        ellipse(cx, cy - 0.5 * s, 2.2 * s, 1.0 * s, stoneMid)
        block(cx, cy - 2.2 * s, mixPacked(canopy[1], 0xFFFFFF, 0.25))
    }

    private fun campfire(cx: Double, cy: Double) {
        val s = u
        ellipse(cx, cy, 2.6 * s, 1.1 * s, ink)
        block(cx, cy - 0.6 * s, ember)
        block(cx + 0.6 * s, cy - 1.2 * s, gold)
        block(cx - 0.8 * s, cy - 2.0 * s, mixPacked(ink, stone, 0.4))
        lights.add(Light(cx, cy, 12 * s, 0.9))
    }

    private fun banner(cx: Double, cy: Double, team: Int) {
        val s = u * 1.2
        rect(cx - 0.4 * s, cy - 7 * s, cx + 0.4 * s, cy, ink)
        for (k in 0 until 4) {
            rect(cx + 0.5 * s, cy - 7 * s + k, cx + 3.4 * s - k * 0.5 * s, cy - 6 * s + k, teamColour(team))
        }
    }

    private fun buildProps() {
        // bushes and loose stones along the roads, on grass only
        for (k in 0 until 260) {
            val x = latticeRandom(k, 1, seed + 41) * W
            val y = latticeRandom(k, 2, seed + 43) * H
            val xi = x.toInt()
            val yi = y.toInt()
            if (xi < 2 || yi < 2 || xi >= W - 2 || yi >= H - 2) continue
            val i = yi * W + xi
            if (kind[i].toInt() != 1) continue
            if (depth(x, y) < 5 * u) continue
            if (latticeRandom(k, 3, seed + 45) > 0.55) {
                ellipse(x, y, 2.4 * u, 1.3 * u, canopy[2])
                ellipse(x - 0.8 * u, y - 0.4 * u, 1.4 * u, 0.8 * u, canopy[3])
            } else {
                ellipse(x, y, 1.3 * u, 0.8 * u, mixPacked(stone, stoneDark, 0.4))
            }
        }
        // a few tall trees off the lanes, so the ground is not a lawn
        for (k in 0 until 90) {
            val x = latticeRandom(k, 5, seed + 51) * W
            val y = latticeRandom(k, 6, seed + 53) * H
            val xi = x.toInt()
            val yi = y.toInt()
            if (xi < 6 || yi < 6 || xi >= W - 6 || yi >= H - 6) continue
            if (kind[yi * W + xi].toInt() != 1) continue
            if (depth(x, y) < 8 * u) continue
            val nearRoad = (xi - W / 2.0).let { abs(it) < 10 * u }
            if (nearRoad) continue
            ellipse(x, y + 1.5 * u, 2.8 * u, 1.8 * u, canopy[0])
            rect(x - 0.5 * u, y, x + 0.5 * u, y + 2 * u, woodDark)
            ellipse(x, y - 1.2 * u, 1.8 * u, 1.5 * u, canopy[2])
        }
    }

    private fun placeSoldiers() {
        for (road in 0 until 3) {
            for (k in 0 until 3) {
                val phase = k / 3.0
                soldiers.add(Soldier(road, 0, phase, 1.0))
                soldiers.add(Soldier(road, 1, phase, 1.0))
            }
        }
        crabs.add(Crab(0.34, 1.0, 0))
        crabs.add(Crab(0.66, -1.0, 0))
    }

    // -------------------------------------------------------------------------------------------- one frame
    override fun step(dt: Double) {
        clock += dt
        tod = (startTod + clock / day) % 1.0
        for (c in crabs) {
            c.p += c.dir * dt * 0.010
            if (c.p > 0.44) { c.p = 0.44; c.dir = -1.0 }
            if (c.p < 0.30) { c.p = 0.30; c.dir = 1.0 }
            if (c.p > 0.70) { c.p = 0.70; c.dir = -1.0 }
            if (c.p < 0.56) { c.p = 0.56; c.dir = 1.0 }
        }
    }

    override fun render() {
        System.arraycopy(base, 0, frame, 0, base.size)
        drawWater()
        drawSoldiers()
        drawCrabs()
        drawFireflies()
        finish()
    }

    private fun drawWater() {
        val T = waterTime(clock)
        for (k in waterIdx.indices) {
            val i = waterIdx[k]
            val x = i % W
            val y = i / W
            val d = waterDepth[k]
            frame[i] = waterPixel(x, y, d, d < 3.0, 0, T, 0, waterPal)
        }
    }

    /** A point a share of the way along a road, its segments weighed by their length. */
    private fun pointAt(pts: List<DoubleArray>, s: Double): DoubleArray {
        var total = 0.0
        val segs = DoubleArray(pts.size - 1)
        for (k in 1 until pts.size) {
            segs[k - 1] = hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1])
            total += segs[k - 1]
        }
        if (total <= 0.0) return pts[0].copyOf()
        var want = clamp(s) * total
        for (k in segs.indices) {
            if (want <= segs[k] || k == segs.size - 1) {
                val t = if (segs[k] <= 0.0) 0.0 else clamp(want / segs[k])
                return doubleArrayOf(
                    lerp(pts[k][0], pts[k + 1][0], t),
                    lerp(pts[k][1], pts[k + 1][1], t),
                )
            }
            want -= segs[k]
        }
        return pts[pts.size - 1].copyOf()
    }

    private fun drawSoldiers() {
        // a wave every 48 seconds: three soldiers a lane, out of each base, meeting over the river
        val period = 48.0
        val march = 0.62
        val s = u
        for (m in soldiers) {
            var road = blueRoads[m.road]
            if (m.team == 1) road = road.map { doubleArrayOf(1 - it[0], 1 - it[1]) }
            val pts = toScreen(road)
            val t = (clock / period + m.phase) % 1.0
            if (t > march) continue // between waves: the road is empty for a moment
            val pos = pointAt(pts, t / march)
            val col = teamColour(m.team)
            val bob = if (((clock * 3 + m.phase * 6).toInt()) % 2 == 0) 0.0 else -1.0
            val x = pos[0]
            val y = pos[1] + bob
            ellipse(x, y + 1.2 * s, 1.4 * s, 0.5 * s, ink)
            rect(x - 0.6 * s, y - 1.6 * s, x + 0.6 * s, y + 0.6 * s, col)
            block(x, y - 2.4 * s, mixPacked(col, 0xFFF2D0, 0.55))
        }
    }

    private fun drawCrabs() {
        val s = u
        for (c in crabs) {
            val q = RiftPlan.riverQ(c.p)
            val x = RiftPlan.across(c.p) * W
            val y = RiftPlan.down(q) * H
            ellipse(x, y, 2.2 * s, 1.1 * s, ember)
            block(x - 1.9 * s, y - 0.4 * s, mixPacked(ember, ink, 0.4))
            block(x + 1.9 * s, y - 0.4 * s, mixPacked(ember, ink, 0.4))
            block(x - 0.9 * s, y - 1.1 * s, ink)
            block(x + 0.9 * s, y - 1.1 * s, ink)
        }
    }

    private fun drawFireflies() {
        val night = hourLight(tod).night
        if (night < 0.15) return
        val s = u
        for (k in 0 until 14) {
            val baseX = latticeRandom(k, 7, seed) * W
            val baseY = (0.25 + latticeRandom(k, 8, seed) * 0.5) * H
            val x = baseX + sin(clock * 0.7 + k) * 4 * s
            val y = baseY + cos(clock * 0.5 + k * 1.3) * 3 * s
            if (twinkle(clock, k * 1.7, 0.6) < 0.45) continue
            block(x, y, palette.rift("#e9ff8f"))
        }
    }

    private fun finish() {
        val hl = hourLight(tod)
        if (!hl.plain) {
            val key = "${(hl.night * 24).roundToInt()},${(hl.dusk * 24).roundToInt()},${hl.morning}"
            val t = tables.getOrPut(key) { hourTables(hl) }
            scratch = gradeFrame(frame, W, H, t, hl.night, lightsAt(hl.night), scratch)
        }
        scrim()
    }

    private fun lightsAt(night: Double): List<Light> {
        if (night < 0.12) return emptyList()
        val share = clamp((night - 0.12) * 2.4)
        val out = ArrayList<Light>(lights.size)
        for (l in lights) out.add(Light(l.x, l.y, l.r * (0.7 + 0.3 * share), l.k))
        return out
    }

    /** The two bands a phone keeps its clock and its dock in, darkened so the map can run to the edges. */
    private fun scrim() {
        val top = 0.135 * H
        val bottom = 0.125 * H
        for (y in 0 until H) {
            val k = (0.52 + 0.48 * smoothstep(0.0, top, y.toDouble())) *
                (0.52 + 0.48 * smoothstep(0.0, bottom, (H - 1 - y).toDouble()))
            if (k >= 0.999) continue
            val row = y * W
            for (x in 0 until W) {
                val i = row + x
                frame[i] = scalePacked(frame[i], k)
            }
        }
    }

    override fun report(): List<String> = listOf(
        "rift \"$phrase\" seed $seed ${W}x$H (detail ${"%.2f".format(detail)})",
        "${RiftPlan.all().count { it.kind == "tower" }} towers, " +
            "${RiftPlan.all().count { it.kind == "inhibitor" }} inhibitors, " +
            "${RiftPlan.all().count { it.kind == "forest" }} forests, " +
            "${RiftPlan.all().count { it.kind == "camp" }} camps, 2 pits",
        "roads: " + RiftPlan.roads().joinToString(", ") { "%.3f".format(RiftPlan.length(it)) },
        "water pixels ${waterIdx.size}, lights ${lights.size}, soldiers ${soldiers.size}",
    )

    // -------------------------------------------------------------------------------------------- pixels
    private fun put(x: Int, y: Int, c: Int, markKind: Int = -1) {
        if (x < 0 || y < 0 || x >= W || y >= H) return
        val i = y * W + x
        base[i] = c
        if (markKind >= 0) kind[i] = markKind.toByte()
    }

    private fun block(x: Double, y: Double, c: Int, markKind: Int = -1) =
        put(x.roundToInt(), y.roundToInt(), c, markKind)

    private fun rect(xa: Double, ya: Double, xb: Double, yb: Double, c: Int) {
        for (y in floor(ya).toInt()..ceil(yb).toInt()) {
            for (x in floor(xa).toInt()..ceil(xb).toInt()) put(x, y, c)
        }
    }

    private fun line(x0: Double, y0: Double, x1: Double, y1: Double, c: Int) {
        val steps = max(2, ceil(hypot(x1 - x0, y1 - y0)).toInt() * 2)
        for (s in 0..steps) {
            val t = s.toDouble() / steps
            put(lerp(x0, x1, t).roundToInt(), lerp(y0, y1, t).roundToInt(), c)
        }
    }

    private fun disc(cx: Double, cy: Double, r: Double, c: Int, markKind: Int = -1) {
        if (r <= 0.0) return
        val xa = floor(cx - r).toInt()
        val xb = ceil(cx + r).toInt()
        val ya = floor(cy - r).toInt()
        val yb = ceil(cy + r).toInt()
        val rr = r * r
        for (y in ya..yb) {
            for (x in xa..xb) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= rr) put(x, y, c, markKind)
            }
        }
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, c: Int, markKind: Int = -1) {
        if (rx <= 0.0 || ry <= 0.0) return
        val xa = floor(cx - rx).toInt()
        val xb = ceil(cx + rx).toInt()
        val ya = floor(cy - ry).toInt()
        val yb = ceil(cy + ry).toInt()
        for (y in ya..yb) {
            for (x in xa..xb) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1.0) put(x, y, c, markKind)
            }
        }
    }
}

/** The blue half of a road that runs the whole map: the points down to the river. */
private fun blueHalfOf(road: List<DoubleArray>): List<DoubleArray> {
    val out = ArrayList<DoubleArray>()
    for (pt in road) {
        out.add(pt)
        if (pt[1] <= 0.5) break
    }
    return out
}
