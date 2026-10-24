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
    private val grass = listOf("#5d7f3c", "#65893f", "#709543", "#7ba047").map { palette.rift(it) }
    private val grassDark = palette.rift("#48642f")
    private val grassEdge = palette.rift("#3d5529")
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
    private val canopy = listOf("#26402a", "#31502f", "#3d6338", "#2a4630").map { palette.rift(it) }
    private val leafLight = palette.rift("#4c7a41")
    private val leafHi = palette.rift("#639350")
    private val bank = palette.rift("#4d6b45")
    private val voidTop = palette.rift("#0a0d14")
    private val voidLow = palette.rift("#171b26")

    private val waterPal = WaterPalette(
        LV = listOf("#3b8aa2", "#37849c", "#337e96", "#2f7891", "#2b728b", "#276c86", "#236680")
            .map { palette.rift(it) }.toIntArray(),
        FOAM = palette.rift("#cfeeea"),
        FOAM2 = palette.rift("#a6d8d6"),
        GLINT = palette.rift("#eafcfd"),
        GLINT2 = palette.rift("#c3e8ec"),
        WEED = palette.rift("#2a7089"),
        WEED2 = palette.rift("#2d748d"),
        SAND = listOf("#6f7d63", "#66755c", "#5d6b56").map { palette.rift(it) }.toIntArray(),
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
        val cliffTop = palette.rift("#454a56")
        val cliffMid = palette.rift("#343841")
        val cliffLow = palette.rift("#262931")
        val cliffDeep = palette.rift("#1b1e24")
        val vine = palette.rift("#4f7a3c")
        val vineDark = palette.rift("#38592c")
        val xa = floor(px0 - 14 * u).toInt()
        val xb = ceil(px1 + 14 * u).toInt()
        val ya = floor(py0 - 14 * u).toInt()
        val yb = ceil(py1 + 14 * u).toInt()
        for (y in ya..yb) {
            for (x in xa..xb) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                val i = y * W + x
                val d = depth(x.toDouble(), y.toDouble())
                if (d >= 0) {
                    kind[i] = 1
                    base[i] = when {
                        d < 2.2 * u -> {
                            kind[i] = 3
                            stoneDark
                        }
                        d < 3.4 * u -> {
                            kind[i] = 3
                            stoneMid
                        }
                        // grass in patches: a broad noise, then a second finer one, and a dark line at the wall
                        else -> {
                            val broad = fractalNoise(x / (26.0 * u), y / (26.0 * u), seed, 3)
                            val fine = fractalNoise(x / (7.0 * u), y / (7.0 * u), seed + 7, 2)
                            val v = clamp(broad * 0.55 + fine * 0.45)
                            val raw = v * 3.6
                            val base4 = raw.toInt().coerceIn(0, 3)
                            val frac = raw - base4
                            val shade = (base4 + if (bayerThreshold(x, y) < frac) 1 else 0).coerceIn(0, 3)
                            when {
                                d < 4.5 * u -> grassEdge
                                else -> grass[shade]
                            }
                        }
                    }
                } else {
                    val out = -d
                    base[i] = when {
                        out < 2.4 * u -> cliffTop
                        out < 6 * u -> cliffMid
                        out < 10 * u -> cliffLow
                        out < 14 * u -> cliffDeep
                        else -> base[i]
                    }
                    // a strata line every few pixels down the cliff face
                    if (out > 2 * u && out < 12 * u && ((y + (if (x % 5 == 0) 1 else 0)) % max(3, (3 * u).roundToInt())) == 0) {
                        base[i] = scalePacked(base[i], 0.82)
                    }
                    // vines: short strands hanging off the lip, never a speckle
                    if (out < 7 * u) {
                        val strand = latticeRandom(x / 2, 0, seed + 3)
                        if (strand > 0.93) {
                            val len = 3 + (strand * 12).toInt()
                            if (out < len * u) base[i] = if (bayerThreshold(x, y) > 0.45) vine else vineDark
                        }
                    }
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
        val deep = waterPal.LV[6]
        val shallow = waterPal.LV[1]
        val rimStone = mixPacked(stoneDark, bank, 0.45)
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
                base[i] = mixPacked(deep, shallow, t * t)
                kind[i] = 4
                waterIdx.add(i)
                waterDepth.add((1 - t) * 44)
            }
            for (y in intArrayOf(ya - 1, yb + 1, ya - 2, yb + 2)) {
                if (y < 0 || y >= H) continue
                val i = y * W + x
                if (kind[i].toInt() == 3 || kind[i].toInt() == 4) continue
                val edge = abs(y - ya) < abs(y - yb)
                base[i] = when {
                    abs(y - ya) <= 1 || abs(y - yb) <= 1 -> rimStone
                    edge -> bank
                    else -> mixPacked(bank, grassDark, 0.45)
                }
            }
        }
        buildBridge()
        // the water that moves is what is left once the bridge is down: a bridge is not painted over every frame
        val keepIdx = ArrayList<Int>(waterIdx.size)
        val keepDepth = ArrayList<Double>(waterIdx.size)
        for (k in waterIdx.indices) {
            val i = waterIdx[k]
            if (kind[i].toInt() != 4) continue
            keepIdx.add(i)
            keepDepth.add(waterDepth[k])
        }
        waterIdx.clear(); waterIdx.addAll(keepIdx)
        waterDepth.clear(); waterDepth.addAll(keepDepth)
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
        // rails and posts, so the crossing reads as a bridge and not a patch of dirt
        for (y in top.toInt() - 1..bottom.toInt() + 1) {
            for (x in intArrayOf(xa.toInt() + 1, xb.toInt() - 1)) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                base[y * W + x] = woodDark
            }
            if (y % max(4, (5 * u).roundToInt()) == 0) {
                for (x in intArrayOf(xa.toInt(), xa.toInt() + 1, xb.toInt() - 1, xb.toInt())) {
                    if (x < 0 || y < 0 || x >= W || y >= H) continue
                    base[y * W + x] = gold
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------- what stands on it
    private fun buildForests() {
        for (piece in RiftPlan.all()) {
            if (piece.kind != "forest") continue
            val cx = RiftPlan.across(piece.p) * W
            val cy = RiftPlan.down(piece.q) * H
            val rx = (px1 - px0) * 0.148
            val ry = (py1 - py0) * 0.110
            // a dark outline under everything, so the wood sits on the grass
            ellipse(cx, cy + 1.4 * u, rx + 1.6 * u, ry + 1.2 * u, ink)
            ellipse(cx, cy, rx, ry, canopy[0])
            // crowns: four clumps, each lit from the upper left, so it reads as a wood and not a puddle
            val clumps = listOf(
                doubleArrayOf(-0.42, -0.34, 0.62), doubleArrayOf(0.38, -0.28, 0.58),
                doubleArrayOf(-0.30, 0.34, 0.54), doubleArrayOf(0.44, 0.30, 0.50),
            )
            for ((k, c) in clumps.withIndex()) {
                val ox = c[0] * rx
                val oy = c[1] * ry
                ellipse(cx + ox, cy + oy, rx * c[2], ry * c[2], canopy[if (k % 2 == 0) 1 else 2])
                ellipse(cx + ox - 0.16 * rx, cy + oy - 0.20 * ry, rx * c[2] * 0.58, ry * c[2] * 0.52, leafLight)
                ellipse(cx + ox - 0.26 * rx, cy + oy - 0.32 * ry, rx * c[2] * 0.26, ry * c[2] * 0.22, leafHi)
            }
            // a dithered edge, one ring of leaves, never inside the wood
            for (y in floor(cy - ry - 2 * u).toInt()..ceil(cy + ry + 2 * u).toInt()) {
                for (x in floor(cx - rx - 2 * u).toInt()..ceil(cx + rx + 2 * u).toInt()) {
                    if (x < 0 || y < 0 || x >= W || y >= H) continue
                    val ex = (x - cx) / (rx + 1.4 * u)
                    val ey = (y - cy) / (ry + 1.4 * u)
                    val dd = ex * ex + ey * ey
                    if (dd <= 1.0 && dd > 0.72 && bayerThreshold(x, y) > (1.0 - dd) * 5.5) {
                        base[y * W + x] = canopy[0]
                    }
                }
            }
            forestWall(cx, cy, rx, ry, if (piece.p < 0.5) 0.0 else PI)
        }
    }

    private fun forestWall(cx: Double, cy: Double, rx: Double, ry: Double, gate: Double) {
        val steps = max(64, ((rx + ry) / (1.1 * u)).toInt())
        for (k in 0 until steps) {
            val a = k * 2 * PI / steps
            val diff = abs(((a - gate + PI) % (2 * PI)) - PI)
            if (diff < 0.26) continue // the gate: one way in, facing its lane
            val x = cx + cos(a) * (rx + 2.6 * u)
            val y = cy + sin(a) * (ry + 2.6 * u)
            val c = when {
                k % 9 == 0 -> gold
                (k / 4) % 2 == 0 -> stone
                else -> mixPacked(stone, stoneDark, 0.35)
            }
            // two rows deep, so the wall reads as a wall and not a line of dots
            block(x, y, c, markKind = 3)
            block(x + cos(a) * 1.4 * u, y + sin(a) * 1.4 * u, stoneDark, markKind = 3)
            block(x, y + 1.6 * u, mixPacked(stoneDark, ink, 0.3), markKind = 3)
        }
        // two posts either side of the gate
        for (side in listOf(-1.0, 1.0)) {
            val a = gate + side * 0.27
            for (dy in 0..2) block(cx + cos(a) * (rx + 2.6 * u), cy + sin(a) * (ry + 2.6 * u) - dy * u, gold)
        }
    }

    private fun buildPieces() {
        for (piece in RiftPlan.all()) {
            val cx = RiftPlan.across(piece.p) * W
            val cy = RiftPlan.down(piece.q) * H
            when (piece.kind) {
                "nexus" -> {
                    basePlatform(cx, cy, piece.team)
                    nexusTower(cx, cy, piece.team, 2.0)
                }
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
        val s = u * 1.3 * scale
        ellipse(cx, cy + 0.4 * s, 6.0 * s, 2.2 * s, ink)
        ellipse(cx, cy, 5.4 * s, 2.0 * s, stoneDark)
        ellipse(cx, cy - 0.4 * s, 4.2 * s, 1.5 * s, stoneMid)
        rect(cx - 2.6 * s, cy - 7.4 * s, cx + 2.6 * s, cy, stone)
        rect(cx - 2.6 * s, cy - 7.4 * s, cx - 1.4 * s, cy, stoneMid)
        rect(cx - 3.1 * s, cy - 8.4 * s, cx + 3.1 * s, cy - 7.4 * s, stoneDark)
        // the crystal: a diamond of the side's colour with a white glint, unmistakable at thumbnail size
        val col = teamColour(team)
        for (k in 0 until 8) {
            val w = (2.2 - abs(k - 3.5) * 0.5) * s
            for (x in floor(cx - w).toInt()..ceil(cx + w).toInt()) {
                val c = if (k == 2 && x <= cx) mixPacked(col, 0xFFFFFF, 0.7) else col
                block(x.toDouble(), cy - (12.6 - k) * s, c)
            }
        }
        block(cx, cy - 13.6 * s, gold)
        lights.add(Light(cx, cy - 9 * s, 11 * s, 0.8))
    }

    private fun inhibitor(cx: Double, cy: Double, team: Int) {
        val s = u * 1.35
        ellipse(cx, cy, 3.4 * s, 1.5 * s, stoneDark)
        rect(cx - 2.5 * s, cy - 1.8 * s, cx + 2.5 * s, cy, stoneMid)
        ellipse(cx, cy - 4.2 * s, 1.8 * s, 2.8 * s, teamColour(team))
        block(cx, cy - 6.4 * s, teamColour(team, deep = true))
        lights.add(Light(cx, cy - 4 * s, 7 * s, 0.7))
    }

    /** The stone apron a base stands on: a wide, pale ring, its edge dithered into the grass. */
    private fun basePlatform(cx: Double, cy: Double, team: Int) {
        val rx = (px1 - px0) * 0.082
        val ry = (py1 - py0) * 0.033
        for (y in floor(cy - ry - 3 * u).toInt()..ceil(cy + ry + 3 * u).toInt()) {
            for (x in floor(cx - rx - 3 * u).toInt()..ceil(cx + rx + 3 * u).toInt()) {
                if (x < 0 || y < 0 || x >= W || y >= H) continue
                val ex = (x - cx) / rx
                val ey = (y - cy) / ry
                val dd = ex * ex + ey * ey
                if (dd > 1.0) continue
                when {
                    dd > 0.82 -> if (bayerThreshold(x, y) > (dd - 0.82) * 5.0) block(x.toDouble(), y.toDouble(), stoneDark, 3)
                    dd > 0.62 -> block(x.toDouble(), y.toDouble(), mixPacked(stoneDark, ink, 0.25), 3)
                    else -> block(x.toDouble(), y.toDouble(), mixPacked(stone, stoneMid, 0.55), 3)
                }
            }
        }
        // a ring of the side's colour set into the stone
        val steps = max(48, (rx * 3).toInt())
        for (k in 0 until steps) {
            val a = k * 2 * PI / steps
            if (k % 5 == 0) block(cx + cos(a) * rx * 0.86, cy + sin(a) * ry * 0.86, teamColour(team), 3)
        }
    }

    private fun nexusTower(cx: Double, cy: Double, team: Int, scale: Double) {
        val s = u * 1.25 * scale
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
        // a stone rim, one block at a time, lit on the upper left as everything else is
        val steps = max(48, (10 * s * 3).toInt())
        for (k in 0 until steps) {
            val a = k * 2 * PI / steps
            val x = cx + cos(a) * 11.6 * s
            val y = cy + sin(a) * 8.9 * s
            val lit = cos(a) < -0.2 || sin(a) < -0.5
            block(x, y, if (lit) stone else stoneDark)
            if (!lit) block(x, y + 1.0 * s, mixPacked(stoneDark, ink, 0.45))
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
        val s = u * 1.9
        ellipse(cx, cy + 0.6 * s, 2.0 * s, 0.9 * s, ink)
        rect(cx - 0.9 * s, cy - 10 * s, cx + 0.4 * s, cy, woodDark)
        rect(cx - 0.2 * s, cy - 10 * s, cx + 0.4 * s, cy, mixPacked(woodDark, gold, 0.35))
        block(cx, cy - 10.6 * s, gold)
        val col = teamColour(team)
        for (k in 0 until 6) {
            val wide = 3.2 * s - abs(k - 2) * 0.35 * s
            rect(cx + 0.8 * s, cy - 9.6 * s + k, cx + wide, cy - 8.6 * s + k, col)
        }
        block(cx + 1.2 * s, cy - 9.0 * s, mixPacked(col, 0xFFFFFF, 0.55))
    }

    private fun buildProps() {
        val tuft = palette.rift("#7fa84d")
        for (patch in 0 until 64) {
            val cx = latticeRandom(patch, 1, seed + 41) * W
            val cy = latticeRandom(patch, 2, seed + 43) * H
            if (cx < 12 * u || cy < 12 * u || cx > W - 12 * u || cy > H - 12 * u) continue
            if (depth(cx, cy) < 9 * u) continue
            if (kind[cy.toInt() * W + cx.toInt()].toInt() != 1) continue
            val roll = latticeRandom(patch, 3, seed + 45)
            val count = 5 + (roll * 9).toInt()
            for (k in 0 until count) {
                val a = k * 2.399963 + roll * 6
                val r = (0.35 + latticeRandom(patch * 31 + k, 4, seed + 47)) * 9 * u
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r * 0.7
                val xi = x.toInt()
                val yi = y.toInt()
                if (xi < 2 || yi < 2 || xi >= W - 2 || yi >= H - 2) continue
                if (kind[yi * W + xi].toInt() != 1) continue
                if (depth(x, y) < 6 * u) continue
                val pick = latticeRandom(patch * 17 + k, 5, seed + 49)
                when {
                    // a bush: two greens, lit on the upper left
                    pick < 0.55 -> {
                        ellipse(x, y, 2.6 * u, 1.4 * u, canopy[1])
                        ellipse(x - 0.7 * u, y - 0.5 * u, 1.7 * u, 0.9 * u, leafLight)
                        block(x - 1.2 * u, y - 0.9 * u, leafHi)
                    }
                    // a rock
                    pick < 0.74 -> {
                        ellipse(x, y, 1.8 * u, 1.0 * u, stoneDark)
                        ellipse(x - 0.4 * u, y - 0.3 * u, 1.1 * u, 0.6 * u, stone)
                    }
                    // a clump of tall grass
                    pick < 0.9 -> {
                        for (b in 0..2) {
                            val bx = x + (b - 1) * 0.9 * u
                            block(bx, y, tuft)
                            block(bx, y - 1.0 * u, tuft)
                            if (b != 1) block(bx, y - 1.8 * u, grass[2])
                        }
                    }
                    // a small tree
                    else -> {
                        ellipse(x, y + 1.4 * u, 2.4 * u, 1.1 * u, canopy[0])
                        rect(x - 0.6 * u, y - 1.0 * u, x + 0.6 * u, y + 1.2 * u, woodDark)
                        ellipse(x, y - 2.4 * u, 2.6 * u, 2.0 * u, canopy[1])
                        ellipse(x - 0.8 * u, y - 3.0 * u, 1.5 * u, 1.0 * u, leafLight)
                    }
                }
            }
        }
        // a scatter of single tufts along every road, the way trodden ground keeps a few
        for (k in 0 until 220) {
            val x = latticeRandom(k, 6, seed + 61) * W
            val y = latticeRandom(k, 7, seed + 63) * H
            val xi = x.toInt()
            val yi = y.toInt()
            if (xi < 2 || yi < 2 || xi >= W - 2 || yi >= H - 2) continue
            if (kind[yi * W + xi].toInt() != 1) continue
            if (depth(x, y) < 6 * u) continue
            block(x, y, if (latticeRandom(k, 8, seed + 65) > 0.6) tuft else grassDark)
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
