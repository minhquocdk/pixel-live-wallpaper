// The island as a living picture: the world of World.kt, the people, deer, hens, fish, gulls and the boat that move over
// it, and one frame after another as the day turns. Built for a live wallpaper: a steady state (no captions, no zoom,
// no fade), a day that comes round for ever, and every static thing drawn once.
package dev.almanac.island

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One person on a road: where along it, which way, and what they wear. */
class Person(
    val id: Int,
    val line: List<IntArray>,
    var s: Double,
    var dir: Int,
    val speed: Double,
    val stride: Double,
    val shirt: Int,
    val trousers: Int,
    val skin: Int,
    val hair: Int,
    val hat: Boolean,
    var wait: Double = 0.0,
    var phase: Double = 0.0,
)

class Deer(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val coat: Int,
    val belly: Int,
    val size: Double,
    val antlers: Boolean,
    var phase: Double = 0.0,
    var fear: Double = 0.0,
    val seed: Int = 0,
)

class Fish(var x: Double, var y: Double, var vx: Double, var vy: Double, val seed: Int, val speed: Double)

class Bird(var x: Double, var y: Double, var vx: Double, var vy: Double)

class Hen(var x: Double, var y: Double, var dir: Int, var moving: Boolean, var wait: Double, var phase: Double, val id: Int)

class Lantern(var x: Double, var y: Double, var sway: Double, val seed: Int, var age: Double)

class Fly(var x: Double, var y: Double, val seed: Int, var age: Double)

/** Maplestory leaf particle drifting down from the trees */
class MapleLeaf(var x: Double, var y: Double, var vx: Double, var vy: Double, val col: Int, var rot: Double = 0.0)

/** The island, growing and living, drawn a frame at a time. */
class IslandScene(
    val phrase: String = World.DefaultPhrase,
    val W: Int = World.ArtWidth,
    val H: Int = World.ArtHeight,
    val detail: Double = 1.0,
    val dayLength: Double = 300.0,
    val theme: Theme = Theme.ORIGINAL,
) {
    val palette = Palette(theme)
    val world = World(phrase, W, H, detail, palette, theme)
    val seed: Int get() = world.seed

    val frame = IntArray(W * H)

    private val base = IntArray(W * H)
    private val zb = IntArray(W * H)

    // The depth and the markers of the ground alone, kept from when the island was built: every frame starts from
    // these, so a sprite that passed over a pixel a moment ago cannot hide what passes over it next. (Without this,
    // the depth only ever grows, and after a while the walkers are eaten away by the trails of those before them.)
    private val baseZb = IntArray(W * H)
    private val baseEm = IntArray(W * H)
    private val em = IntArray(W * H)
    private val topc = IntArray(world.n)
    private val topcAo = IntArray(world.n)

    // the water, recomputed every frame (everything else on the ground is drawn once)
    private var waterCount = 0
    private var waterIdx = IntArray(4096)
    private var waterX = IntArray(4096)
    private var waterY = IntArray(4096)
    private var waterD = FloatArray(4096)
    private var waterNear = BooleanArray(4096)
    private var waterSh = ByteArray(4096)
    private var waterKind = ByteArray(4096)
    private val wobRow = IntArray(world.GH)
    private val waterMap = ByteArray(W * H)

    private var gradeScratch: FloatArray? = null
    private var cloudScratch: FloatArray? = null
    private val tables = HashMap<String, HourTables>()

    private var clock = 0.0
    var tod = 0.3
        private set

    val people = ArrayList<Person>()
    val deer = ArrayList<Deer>()
    val fish = ArrayList<Fish>()
    val birds = ArrayList<Bird>()
    val hens = ArrayList<Hen>()
    val lanterns = ArrayList<Lantern>()
    val flies = ArrayList<Fly>()
    val mapleLeaves = ArrayList<MapleLeaf>() // only for MAPLE theme
    private var leafRespawns = 0
    private var boatAngle = 0.0
    private var boatX = 0.0
    private var boatY = 0.0
    private var boatReady = false

    // the whale: a head that swims a slow loop, and a spine that follows it, under the water and out of reach
    private var whaleX = 0.0
    private var whaleY = 0.0
    private var spine: Array<DoubleArray> = Array(10) { DoubleArray(2) }
    private var whaleShape: BooleanArray = BooleanArray(0)

    // screen places for the lights
    private val houseScreen = HashMap<Int, IntArray>()
    private val lampScreen = ArrayList<IntArray>()
    private var towerScreen: IntArray? = null
    private var summitScreen = IntArray(2)
    private var fireScreen = IntArray(2)
    private val summitTips = ArrayList<IntArray>()

    init {
        buildSky()
        buildStatic()
        startLife()
    }

    // ------------------------------------------------------------------------------------------ the ground, once
    private fun buildSky() {
        // the ground's own colours: sand, grass, wood, heath, rock, with tufts and flowers, and the shade at a wall's foot
        for (y in 0 until world.GH) {
            for (x in 0 until W) {
                val i = world.at(x, y)
                val l = world.LEV[i]
                val m = world.MO[i]
                val g = world.GR[i]
                val baseCol = when {
                    l <= 0 -> if (ditheredBorder((9 - world.dLand[i] / 3.0) / 6, x, y, g.toDouble())) palette.wetSand else palette.sand
                    l <= 2 -> if (ditheredBorder((0.47 - m) * 5, x, y, g.toDouble())) palette.dry else palette.lush
                    l <= 4 -> if (ditheredBorder((m - 0.55) * 4, x, y, g.toDouble())) palette.wood2 else palette.wood1
                    l == 5 -> if (ditheredBorder((0.45 - m) * 4, x, y, g.toDouble())) palette.moor else palette.heath
                    else -> if (ditheredBorder((fractalNoise(x / (14 * detail), y / (14 * detail), seed + 21, 2) - 0.62) * 4, x, y, g.toDouble())) palette.rock else palette.moor
                }
                var c = when {
                    g < 0.1 -> palette.tone(baseCol, -1)
                    g > 0.93 -> palette.tone(baseCol, 1)
                    else -> baseCol
                }
                if (l in 1..4 && (x * 7 + y * 3) % 11 == 0 && m > 0.38) c = palette.tone(baseCol, -1)
                if (l in 1..2 && g > 0.986) c = palette.flowers[latticeHash(x, y, 4) and 3]
                topc[i] = c
                val back = world.lev(x, y - 1) > l
                topcAo[i] = if (back) palette.tone(c, -1) else c
            }
        }
    }

    private fun addWater(idx: Int, x: Int, y: Int, d: Double, near: Boolean, sh: Int, kind: Int) {
        if (waterCount >= waterIdx.size) {
            val cap = waterIdx.size * 2
            waterIdx = waterIdx.copyOf(cap)
            waterX = waterX.copyOf(cap)
            waterY = waterY.copyOf(cap)
            waterD = waterD.copyOf(cap)
            waterNear = waterNear.copyOf(cap)
            waterSh = waterSh.copyOf(cap)
            waterKind = waterKind.copyOf(cap)
        }
        waterIdx[waterCount] = idx
        waterX[waterCount] = x
        waterY[waterCount] = y
        waterD[waterCount] = d.toFloat()
        waterNear[waterCount] = near
        waterSh[waterCount] = sh.toByte()
        waterKind[waterCount] = kind.toByte()
        waterMap[idx] = kind.toByte()
        waterCount++
    }

    private fun buildStatic() {
        zb.fill(-1)
        em.fill(0)
        val d = detail
        for (y in 0 until world.GH) {
            for (x in 0 until W) {
                val i = world.at(x, y)
                val sea = world.E[i] < world.cut
                val l = if (sea) 0 else max(0, world.LEV[i])
                val lift = l * world.lift
                val sy = y - lift
                if (sy >= H) continue
                val river = !sea && world.riv[i]
                val road = world.path[i] != 0
                var c = 0
                if (sea && world.pierPx[i] != 0) {
                    c = world.pierPx[i]
                } else if (sea) {
                    val behind = if (y > 0 && world.LEV[i - W] > 0 && world.E[i - W] >= world.cut) 2
                    else if (y > 1 && world.LEV[i - 2 * W] > 0 && world.E[i - 2 * W] >= world.cut) 1 else 0
                    addWater(sy * W + x, x, y, world.dSea[i] / 3.0, world.dBeach[i] <= world.dSea[i] + 4 * d, behind, 1)
                } else if (river && road) {
                    c = if ((x + y) % 3 != 0) palette.tone(palette.plank, if ((x and 3) == 0) -1 else 0) else palette.tone(palette.plank, 1)
                } else if (river) {
                    addWater(sy * W + x, x, y, world.dRiv[i] / 3.0 + 1, false, 0, 2)
                } else {
                    c = groundStatic(i, x, y, l, road)
                }
                if (sy >= 0) {
                    if (c != 0) base[sy * W + x] = c
                    zb[sy * W + x] = y
                }
                if (y + 1 >= world.GH || lift == 0) continue
                val below = i + W
                val lf = if (world.E[below] < world.cut) 0 else max(0, world.LEV[below])
                val bottom = min(H, y + 1 - lf * world.lift)
                for (r in max(0, sy + 1) until bottom) {
                    val yy = r - sy - 1
                    val k = r * W + x
                    val wc = when {
                        river -> intArrayOf(
                            palette.P.FOAM, palette.PR.LV[0], palette.P.FOAM2, palette.PR.LV[1],
                            palette.P.FOAM, palette.PR.LV[2], palette.P.FOAM2, palette.PR.LV[0],
                        )[(yy + x * 5) and 7]
                        road -> if (yy and 1 != 0) palette.tone(palette.stone, -1) else palette.tone(palette.stone, 1)
                        yy == 0 -> palette.tone(topc[i], -1)
                        else -> palette.walls[palette.wallKind(l)]!![((yy and 15) shl 4) or (x and 15)]
                    }
                    base[k] = wc
                    zb[k] = y
                    waterMap[k] = 0
                }
            }
        }
        System.arraycopy(base, 0, frame, 0, base.size)
        System.arraycopy(zb, 0, baseZb, 0, zb.size)
        System.arraycopy(em, 0, baseEm, 0, em.size)
        // where the lights stand: the houses, the lamps, the tower, the summit tree
        world.houses.forEach { h ->
            val sp = world.houseSprite(h)
            val g = ground(h.x0 + (h.w shr 1), h.y0 + h.depth - 1)
            houseScreen[h.id] = intArrayOf(g[0], g[1] - (2 * d).roundToInt())
        }
        world.lamps.forEach { lp ->
            val g = ground(lp.x, lp.y)
            lampScreen.add(intArrayOf(g[0], g[1] + (3 * d).roundToInt()))
        }
        if (world.lightX >= 0) {
            val g = ground(world.lightX, world.lightY)
            towerScreen = intArrayOf(g[0], g[1] - (7 * d).roundToInt())
        }
        ground(world.peakX, world.peakY).let {
            summitScreen = it
            fireScreen = intArrayOf(it[0], it[1])
        }
        world.summitTree?.tips?.forEach { tip -> summitTips.add(intArrayOf(summitScreen[0] + tip[0], summitScreen[1] + tip[1])) }
        val hill = world.villages[1]
        fireScreen = ground(hill.x, hill.y)
    }

    private fun groundStatic(i: Int, x: Int, y: Int, l: Int, road: Boolean): Int {
        if (road) {
            val p = world.path[i]
            return if (world.lev(x, y - 1) > l) palette.tone(p, -1) else p
        }
        val f = world.field[i]
        if (f != 0) return if (world.lev(x, y - 1) > l) palette.tone(f, -1) else f
        return topcAo[i]
    }

    /** Where a thing standing at a point of the world shows on the picture: its terrace lifts it into view. */
    fun ground(x: Int, y: Int): IntArray {
        val l = max(0, world.lev(x, y))
        return intArrayOf(x, y - l * world.lift)
    }

    // ------------------------------------------------------------------------------------------ the life
    private fun startLife() {
        val d = detail
        val r = SeededRandom(seed + 5)
        world.roads.forEachIndexed { ri, line ->
            if (line.size < 2) return@forEachIndexed
            for (k in 0 until (if (ri == 0) 4 else 2)) {
                val hue = r.range(0.0, 360.0)
                val shirt = hslToRgb(hue, r.range(0.35, 0.65), r.range(0.42, 0.58))
                people.add(
                    Person(
                        id = people.size, line = line, s = r.range(0.0, (line.size - 1).toDouble()), dir = r.sign(),
                        speed = r.range(7 * d, 11 * d), stride = r.range(0.9, 1.3), shirt = shirt,
                        trousers = palette.tone(shirt, -2),
                        skin = palette.hex(listOf("#f1c9a5", "#d9a77e", "#b07a52", "#8a5a3a")[r.range(0, 3)]),
                        hair = palette.hex(listOf("#2b1d14", "#5a3a22", "#c9a26a", "#d8d2c8", "#1e1a1a")[r.range(0, 4)]),
                        hat = r.chance(0.35), phase = r.range(0.0, 6.28),
                    ),
                )
            }
        }
        val meadow = world.meadow
        for (k in 0 until 4) {
            val hue = r.range(24.0, 36.0)
            // MapleStory slimes: green/blue/orange bobbing slimes — cute, not scary
            val isMaple = theme == Theme.MAPLE
            val slimeCols = listOf(palette.slimeGreen, palette.slimeBlue, palette.slimeOrange, palette.mushCapRed)
            deer.add(
                Deer(
                    x = meadow.x + r.range(-10.0, 10.0) * d,
                    y = meadow.y + r.range(-6.0, 6.0) * d,
                    vx = r.range(-1.0, 1.0), vy = r.range(-1.0, 1.0),
                    coat = if (isMaple) slimeCols[k % slimeCols.size] else hslToRgb(hue, r.range(0.35, 0.5), r.range(0.4, 0.5)),
                    belly = if (isMaple) palette.tone(slimeCols[k % slimeCols.size], 1) else hslToRgb(hue, 0.25, 0.62),
                    size = if (isMaple) r.range(0.75, 0.95) else r.range(0.85, 1.1), antlers = if (isMaple) false else r.chance(0.4), seed = k,
                ),
            )
        }
        // a fawn after its mother — MapleStory: baby slime
        deer.add(
            Deer(
                x = meadow.x + r.range(-8.0, 8.0) * d, y = meadow.y + r.range(-5.0, 5.0) * d,
                vx = 0.5, vy = 0.3,
                coat = if (theme == Theme.MAPLE) palette.slimeGreen else hslToRgb(r.range(24.0, 36.0), 0.4, 0.55),
                belly = if (theme == Theme.MAPLE) palette.hex("#eaffd5") else hslToRgb(0.0, 0.0, 0.75),
                size = 0.62, antlers = false, seed = 9,
            ),
        )
        val bay = seaPoint(world.villages[0].x, world.villages[0].y, 36.0 * d, 70.0 * d)
        for (k in 0 until 16) {
            fish.add(
                Fish(
                    x = bay[0] + r.range(-8.0, 8.0) * d, y = bay[1] + r.range(-5.0, 5.0) * d,
                    vx = r.range(-6.0, 6.0), vy = r.range(-4.0, 4.0), seed = k, speed = r.range(0.9, 1.1),
                ),
            )
        }
        // the whale keeps to the deep, where the fish do not go
        val deep = seaPoint(W / 2, H / 2, 75.0 * d, 999.0 * d)
        whaleX = deep[0]
        whaleY = deep[1]
        spine = Array(10) { k -> doubleArrayOf(deep[0] - k * 3 * d, deep[1]) }
        whaleShape = BooleanArray(W * H)
        for (k in 0 until 7) birds.add(Bird(-40.0 - k * 6 * d, 44 * d + k * 2, 0.0, 0.0))
        world.housesByVillage.getOrNull(0)?.let { list ->
            for (k in 0 until 6) {
                val h = list.getOrNull(k % max(1, list.size))
                val x = if (h != null) h.x0 + r.range(0.0, h.w.toDouble()) else world.villages[0].x.toDouble()
                val y = if (h != null) h.y0 + r.range(0.0, h.depth.toDouble()) else world.villages[0].y.toDouble()
                hens.add(Hen(x, y, r.sign(), false, r.range(0.0, 1.0), r.range(0.0, 6.28), k))
            }
        }
        for (k in 0 until 6) {
            val meadow2 = world.meadow
            flies.add(Fly(meadow2.x + r.range(-30.0, 30.0), meadow2.y + r.range(-20.0, 20.0), seed + k, 0.0))
        }
        // MapleStory: falling maple leaves drifting from the autumn canopy
        if (theme == Theme.MAPLE) {
            val leafCols = listOf(palette.mapleRed, palette.mapleOrange, palette.mapleYellow)
            for (k in 0 until (18 * d).toInt()) {
                val t = if (world.trees.isEmpty()) null else world.trees[k % world.trees.size]
                val baseX = t?.x?.toDouble() ?: (W * 0.5)
                val baseY = t?.y?.toDouble() ?: (world.GH * 0.5)
                mapleLeaves.add(MapleLeaf(
                    x = baseX + r.range(-20.0, 20.0) * d,
                    y = baseY + r.range(-10.0, 10.0) * d,
                    vx = r.range(-2.0, 2.0),
                    vy = r.range(4.0, 10.0),
                    col = leafCols[r.range(0, leafCols.size - 1)],
                    rot = r.range(0.0, 6.28)
                ))
            }
        }
        val centre = if (world.pierX >= 0) {
            doubleArrayOf(world.pierX + world.pierDirX * 16 * d, world.pierY + world.pierDirY * 16 * d)
        } else seaPoint(world.villages[0].x, world.villages[0].y, 45.0 * d, 80.0 * d)
        boatX = centre[0]
        boatY = centre[1]
        boatReady = true
    }

    private fun seaPoint(fromX: Int, fromY: Int, dmin: Double, dmax: Double): DoubleArray {
        var best: DoubleArray? = null
        var bestD = 1e9
        val s = max(1, (3 * detail).roundToInt())
        var y = (8 * detail).roundToInt()
        while (y < H - (8 * detail).roundToInt()) {
            var x = (8 * detail).roundToInt()
            while (x < W - (8 * detail).roundToInt()) {
                val i = world.at(x, y)
                val dd = world.dSea[i].toDouble()
                if (world.LEV[i] < 0 && dd >= dmin && dd <= dmax) {
                    val dist = hypot((x - fromX).toDouble(), (y - fromY).toDouble())
                    if (dist < bestD) {
                        bestD = dist
                        best = doubleArrayOf(x.toDouble(), y.toDouble())
                    }
                }
                x += s
            }
            y += s
        }
        return best ?: doubleArrayOf(W / 2.0, H * 0.75)
    }

    private fun inSea(x: Double, y: Double, depth: Double): Boolean {
        val ix = x.roundToInt()
        val iy = y.roundToInt()
        if (ix < 2 || iy < 2 || ix >= W - 2 || iy >= H - 2) return false
        val i = world.at(ix, iy)
        return world.LEV[i] < 0 && world.dSea[i] >= depth
    }

    private fun onGround(x: Double, y: Double, level: Int): Boolean {
        val ix = x.roundToInt()
        val iy = y.roundToInt()
        val i = world.at(clampInt(ix, 0, W - 1), clampInt(iy, 0, world.GH - 1))
        return world.lev(ix, iy) == level && !world.riv[i] && world.res[i] != 1
    }

    /** Puts the day at a given time (0..1, noon 0.25, midnight 0.75), for a still. */
    fun setTimeOfDay(t: Double) {
        tod = t
        clockAtFixedDay = true
    }

    private var clockAtFixedDay = false

    // switches for looking at one layer at a time (the preview tool sets them)
    var drawWater = true
    var drawLifeFlag = true
    var drawShadowsFlag = true
    var drawGradeFlag = true

    /** The world a moment later. */
    fun step(dt: Double) {
        clock += dt
        if (!clockAtFixedDay) tod = (0.3 + clock / dayLength) % 1.0
        val d = detail
        for (p in people) {
            if (p.wait > 0) {
                p.wait -= dt
                continue
            }
            p.s += p.dir * p.speed * dt
            p.phase += p.speed * dt * strideRate(p.stride)
            if (p.s <= 0 || p.s >= p.line.size - 1) {
                p.s = clamp(p.s, 0.0, (p.line.size - 1).toDouble())
                p.dir = -p.dir
                p.wait = 0.6
            }
        }
        // the herd keeps together and wanders; when someone comes near, it runs from them
        val threats = people.map { p ->
            val k = clampInt(p.s.roundToInt(), 0, p.line.size - 1)
            doubleArrayOf(p.line[k][0].toDouble(), p.line[k][1] - 1.0)
        }
        var cx = 0.0
        var cy = 0.0
        for (de in deer) {
            cx += de.x
            cy += de.y
        }
        cx /= deer.size
        cy /= deer.size
        for (de in deer) {
            var ax = (cx - de.x) * 0.12
            var ay = (cy - de.y) * 0.12
            for (o in deer) {
                if (o === de) continue
                val dx = de.x - o.x
                val dy = de.y - o.y
                val d2 = dx * dx + dy * dy
                val gap = if (theme == Theme.MAPLE) 11 * d else 7 * d // slimes are rounder: keep them apart
                if (d2 in 0.01..gap * gap) {
                    ax += dx * 8.0 / (d2 + 1)
                    ay += dy * 8.0 / (d2 + 1)
                }
            }
            ax += sin(clock * 0.37 + de.seed) * 0.9 * d
            ay += cos(clock * 0.31 + de.seed) * 0.9 * d
            var fear = max(de.fear - dt, 0.0)
            for (th in threats) {
                val dx = de.x - th[0]
                val dy = de.y - th[1]
                val dd = hypot(dx, dy)
                if (dd < 26 * d && dd > 0.01) {
                    fear = 1.0
                    ax += dx / dd * 30
                    ay += dy / dd * 30
                }
            }
            de.fear = fear
            de.vx += ax * dt
            de.vy += ay * dt
            val sp = hypot(de.vx, de.vy)
            val maxSp = if (fear > 0) 16 * d else 6 * d
            if (sp > maxSp) {
                de.vx *= maxSp / sp
                de.vy *= maxSp / sp
            }
            val nx = de.x + de.vx * dt
            val ny = de.y + de.vy * dt
            val meadow = world.meadow
            if (onGround(nx, ny, meadow.level) && hypot(nx - meadow.x, ny - meadow.y) < 34 * d) {
                de.phase += hypot(nx - de.x, ny - de.y) * strideRate(0.9)
                de.x = nx
                de.y = ny
            } else {
                de.vx = (meadow.x - de.x) * 0.3
                de.vy = (meadow.y - de.y) * 0.3
            }
        }
        // fish keep to the bay, a shoal that turns together
        for (f in fish) {
            f.vx += sin(clock * 0.6 + f.seed * 1.7) * 6 * dt
            f.vy += cos(clock * 0.5 + f.seed * 2.1) * 6 * dt
            val sp = hypot(f.vx, f.vy)
            val maxSp = 15.0 * d * f.speed
            if (sp > maxSp) {
                f.vx *= maxSp / sp
                f.vy *= maxSp / sp
            }
            val nx = f.x + f.vx * dt
            val ny = f.y + f.vy * dt
            if (inSea(nx, ny, 4.0 * d)) {
                f.x = nx
                f.y = ny
            } else {
                f.vx = -f.vx
                f.vy = -f.vy
            }
        }
        // the whale's head swims a slow loop; its spine follows, each joint bending no more than a spine bends
        if (spine.size == 10 && whaleShape.isNotEmpty()) {
            val wa = clock * 0.35
            spine[0] = doubleArrayOf(whaleX + cos(wa) * 34 * d, whaleY + sin(wa * 1.3) * 9 * d)
            for (k in 1 until spine.size) {
                val px = spine[k - 1][0]
                val py = spine[k - 1][1]
                val back = if (k > 1) {
                    Math.atan2(spine[k - 1][1] - spine[k - 2][1], spine[k - 1][0] - spine[k - 2][0]) + PI
                } else {
                    Math.atan2(spine[k][1] - py, spine[k][0] - px)
                }
                val heading = Math.atan2(spine[k][1] - py, spine[k][0] - px)
                val ang = back + clamp(angleDiff(back, heading), -0.35, 0.35)
                spine[k] = doubleArrayOf(px + cos(ang) * 3.2 * d, py + sin(ang) * 3.2 * d)
            }
        }
        // gulls in a V across the sky
        val u = clamp((clock % 60.0) / 60.0)
        val lx = lerp(-30.0, W + 60.0, u)
        val ly = 46 * d + sin(u * 5) * 10 * d
        val la = Math.atan2(cos(u * 5) * 10 * d * 5, W + 90.0)
        birds.forEachIndexed { k, b ->
            val slotX: Double
            val slotY: Double
            if (k == 0) {
                slotX = lx
                slotY = ly
            } else {
                val side = if (k % 2 == 1) 1 else -1
                val rank = ((k + 1) / 2).toDouble()
                val back = rank * 9 * d
                slotX = lx - cos(la) * back + cos(la + PI / 2) * side * rank * 7 * d
                slotY = ly - sin(la) * back + sin(la + PI / 2) * side * rank * 7 * d
            }
            var vx = b.vx + (-30 * (b.x - slotX) - 8 * b.vx) * dt
            var vy = b.vy + (-30 * (b.y - slotY) - 8 * b.vy) * dt
            b.x += vx * dt
            b.y += vy * dt
            b.vx = vx
            b.vy = vy
        }
        for (h in hens) {
            if (h.wait > 0) {
                h.wait -= dt
                h.moving = false
                continue
            }
            h.moving = true
            h.phase += dt * 5
            val nx = h.x + h.dir * 4.0 * d * dt
            if (!onGround(nx, h.y, world.lev(nx.roundToInt(), h.y.roundToInt()))) {
                h.dir = -h.dir
                h.wait = 0.4
            } else h.x = nx
            if (indexRandom((clock * 10).toInt() + h.id, seed) < 0.004) h.wait = 0.6
        }
        for (l in lanterns) {
            l.age += dt
            l.y -= 9 * dt * d
            l.x += sin(clock * 2 + l.seed) * 3 * dt * d
        }
        lanterns.removeAll { it.y < -4 || it.age > 14 }
        while (lanterns.size < 14 * d && indexRandom((clock * 8).toInt(), seed + 3) < 0.25) {
            val v = world.villages[lanterns.size % 2]
            lanterns.add(
                Lantern(
                    x = v.x + (indexRandom(lanterns.size, seed + 4) - 0.5) * 20 * d,
                    y = v.y - 6 * d - v.level * world.lift,
                    sway = indexRandom(lanterns.size, seed + 5) * 6,
                    seed = lanterns.size, age = 0.0,
                ),
            )
        }
        for (f in flies) {
            f.x += sin(clock * 1.3 + f.seed) * 5 * dt * d
            f.y += cos(clock * 1.1 + f.seed * 1.7) * 4 * dt * d
        }
        // maple leaves drifting down with a gentle sway; one that leaves the picture starts again under a crown
        if (theme == Theme.MAPLE && world.trees.isNotEmpty()) {
            mapleLeaves.forEachIndexed { k, leaf ->
                leaf.x += (leaf.vx + sin(clock * 0.8 + leaf.rot) * 9 * d) * dt
                leaf.y += leaf.vy * dt
                leaf.rot += dt * 1.2
                if (leaf.y > H + 8 || leaf.x < -12 || leaf.x > W + 12) {
                    leafRespawns++
                    val n = leafRespawns * 31 + k
                    val t = world.trees[(indexRandom(n, seed + 61) * world.trees.size).toInt().coerceIn(0, world.trees.size - 1)]
                    leaf.x = t.x + (indexRandom(n, seed + 62) - 0.5) * 16 * d
                    leaf.y = t.y - max(0, world.lev(t.x, t.y)) * world.lift - 10 * d
                    leaf.vx = (indexRandom(n, seed + 63) - 0.5) * 4
                }
            }
        }
        boatAngle = clock * 0.22
    }

    // ------------------------------------------------------------------------------------------ one frame
    /** Draws the picture at the scene's time of day into [frame]. */
    fun render() {
        System.arraycopy(base, 0, frame, 0, base.size)
        System.arraycopy(baseZb, 0, zb, 0, zb.size)
        System.arraycopy(baseEm, 0, em, 0, em.size)
        // the water, moving
        val wt = waterTime(clock)
        for (y in 0 until world.GH) wobRow[y] = Math.round(sin(y * 0.35 + clock * 2.2)).toInt()
        if (drawWater) for (k in 0 until waterCount) {
            val x = waterX[k]
            val y = waterY[k]
            frame[waterIdx[k]] = waterPixel(
                x, y, waterD[k].toDouble(), waterNear[k], waterSh[k].toInt(), wt, wobRow[y],
                if (waterKind[k].toInt() == 1) palette.P else palette.PR,
            )
        }
        if (drawLifeFlag) {
            drawLife()
            drawBoat()
            drawGulls()
        }
        val storm = 0.0
        if (clock > 0.0 && drawShadowsFlag) {
            cloudScratch = cloudShadows(
                frame, W, H, clock * 9 * detail, clock * 3 * detail, seed, 0.3 + storm * 0.75,
                tod < 0.4 || tod > 0.95 || storm > 0.3, cloudScratch,
            )
        }
        // the hour's light, with the lamps, windows and fires holding the dark off round themselves
        val hl = hourLight(tod)
        if (!hl.plain && drawGradeFlag) {
            val key = "${(hl.night * 24).roundToInt()},${(hl.dusk * 24).roundToInt()},${hl.morning}"
            val t = tables.getOrPut(key) { hourTables(hl) }
            gradeScratch = gradeFrame(frame, W, H, t, hl.night, lightsAt(hl.night), gradeScratch)
        }
        glow()
    }

    private fun lightsAt(night: Double): List<Light> {
        val out = ArrayList<Light>()
        if (night < 0.12) return out
        val share = clamp((night - 0.15) * 2.5) * 0.9
        val d = detail
        for (h in world.houses) {
            val sc = houseScreen[h.id] ?: continue
            if (windowLit(h.id, seed, clock, share, 0.85)) out.add(Light(sc[0].toDouble(), sc[1].toDouble(), 13 + h.w * 0.4 * d, 0.9))
        }
        for (lp in lampScreen) out.add(Light(lp[0].toDouble(), lp[1].toDouble(), 15 * d, 0.85))
        out.add(Light(fireScreen[0].toDouble(), fireScreen[1] - 1.0, (20 + indexRandom(clock.toInt(), 7) * 4) * d, 1.0))
        towerScreen?.let { if (blink(clock, 5.0, 0.0, 0.3)) out.add(Light(it[0].toDouble(), it[1].toDouble(), 34.0 * d, 1.0)) }
        for (tip in summitTips) out.add(Light(tip[0].toDouble(), tip[1].toDouble(), 10.0 * d, 0.75))
        for (f in flies) if (twinkle(clock, f.seed * 3.0, 0.5) > 0.6) out.add(Light(f.x, f.y, 5.0 * d, 0.6))
        for (l in lanterns) if (l.y > 4) out.add(Light(l.x, l.y, 11.0 * d, 0.85))
        return out
    }

    private fun drawLife() {
        val d = detail
        val items = ArrayList<Item>(world.trees.size + world.houses.size + 64)
        for (tr in world.trees) {
            val sp = tr.sprite ?: continue
            val sx = tr.x
            val sy = tr.y - max(0, world.lev(tr.x, tr.y)) * world.lift
            shadeEllipse(frameSurface, sx + 2.0 * d, sy.toDouble(), (2.5 + 3.6 * tr.size) * d, (1.2 + 1.2 * tr.size) * d, 0.78)
            val trunk = sp.trunk
            items.add(
                Item(
                    tr.y.toDouble(), sp, sx, sy,
                    sway = { hh -> if (hh > trunk) sin(clock * 2.3 + tr.phase + tr.x * 0.04) * (hh - trunk) * 0.05 else 0.0 },
                ),
            )
        }
        for (p in world.palms) {
            val sx = p.x
            val sy = p.y - max(0, world.lev(p.x, p.y)) * world.lift
            items.add(Item(p.y.toDouble(), null, sx, sy, palm = p))
        }
        for (rd in world.reeds) {
            val sx = rd.x
            val sy = rd.y - max(0, world.lev(rd.x, rd.y)) * world.lift
            items.add(Item(rd.y.toDouble(), null, sx, sy, reed = rd))
        }
        world.summitTree?.let { sp ->
            val sx = summitScreen[0]
            val sy = summitScreen[1]
            shadeEllipse(frameSurface, sx + 4.0 * d, sy.toDouble(), 12 * d, 3 * d, 0.75)
            items.add(Item(world.peakY.toDouble(), sp, sx, sy, sway = { hh -> if (hh > 8) sin(clock * 1.4) * (hh - 8) * 0.03 else 0.0 }))
        }
        for (h in world.houses) {
            val sp = world.houseSprite(h)
            val g = ground(h.x0 + (h.w shr 1), h.y0 + h.depth - 1)
            shadeEllipse(frameSurface, g[0] + 3.0 * d, g[1] + 1.0, h.w * 0.65, 2.2 * d, 0.72)
            items.add(Item((h.y0 + h.depth - 1).toDouble(), sp, g[0], g[1]))
        }
        for (lp in world.lamps) {
            val g = ground(lp.x, lp.y)
            items.add(Item(lp.y.toDouble(), world.post, g[0], g[1]))
        }
        towerScreen?.let { g -> world.tower?.let { items.add(Item(world.lightY.toDouble(), it, g[0], g[1])) } }
        // the people on the roads
        for (p in people) {
            val k = clampInt(p.s.roundToInt(), 0, p.line.size - 1)
            val x = p.line[k][0]
            val y = p.line[k][1] - 1
            val sA = footStep(p.phase, 1.0)
            val sB = footStep(p.phase + PI, 1.0)
            val ga = p.wait <= 0 && sA > 0.4
            val gb = p.wait <= 0 && sB > 0.4
            val sp = personSprite(p, ga, gb)
            val g = ground(x, y)
            items.add(Item(y.toDouble(), sp, g[0], g[1], flip = p.dir < 0))
        }
        for (de in deer) {
            val grazing = hypot(de.vx, de.vy) < 1.6 * d
            val ph = (Math.round(((de.phase % 6.283) / 6.283) * 8).toInt()) % 8
            val sp = deerSprite(de, (ph / 8.0) * 6.283, grazing)
            val g = ground(de.x.roundToInt(), de.y.roundToInt())
            items.add(Item(de.y, sp, g[0], g[1], flip = de.vx < 0))
        }
        for (h in hens) {
            val sp = world.hen[if (h.moving) 0 else ((clock * 3 + h.id).toInt() and 1)]
            val g = ground(h.x.roundToInt(), h.y.roundToInt())
            items.add(Item(h.y, sp, g[0], g[1], flip = h.dir < 0))
        }
        items.sortBy { it.z }
        for (it in items) stamp(it)
        // Maple leaves drifting over the island — soft, not covering houses
        if (theme == Theme.MAPLE) drawMapleLeaves()
        // under the water: the whale's shadow (a darkening of the sea it swims through), and the fish
        if (whaleShape.size == frame.size) {
            java.util.Arrays.fill(whaleShape, false)
            for (k in spine.indices) {
                val r = WHALE_RADII[k] * d
                ellipseMask(whaleShape, spine[k][0], spine[k][1], r * 1.25, r)
            }
            val tail = spine[spine.size - 1]
            ellipseMask(whaleShape, tail[0], tail[1] - 2 * d, 1.2 * d, 2.4 * d)
            ellipseMask(whaleShape, tail[0], tail[1] + 2 * d, 1.2 * d, 2.4 * d)
            for (i in frame.indices) if (whaleShape[i] && waterKindAt(i) == 1) frame[i] = scalePacked(frame[i], 0.66)
        }
        for (f in fish) {
            val l = hypot(f.vx, f.vy).let { if (it == 0.0) 1.0 else it }
            for (k in 0 until 3) {
                val x = (f.x - f.vx / l * k).roundToInt()
                val y = (f.y - f.vy / l * k).roundToInt()
                if (x in 0 until W && y in 0 until H) {
                    val i = y * W + x
                    val wmKind = waterKindAt(i)
                    if (wmKind == 1) frame[i] = scalePacked(frame[i], if (k != 0) 0.62 else 0.5)
                }
            }
        }
    }

    /** Paints an ellipse into a mask: what a whale's body is made of, one joint at a time. */
    private fun ellipseMask(mask: BooleanArray, cx: Double, cy: Double, rx: Double, ry: Double) {
        if (rx <= 0 || ry <= 0) return
        val x0 = max(0, floor(cx - rx).toInt())
        val x1 = min(W - 1, floor(cx + rx).toInt())
        val y0 = max(0, floor(cy - ry).toInt())
        val y1 = min(H - 1, floor(cy + ry).toInt())
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1.0) mask[y * W + x] = true
            }
        }
    }

    /** What shows at a picture pixel: 0 land, 1 sea, 2 river. */
    private fun waterKindAt(idx: Int): Int = waterMap[idx].toInt()

    /** For the preview tool: what the engine knows about a picture pixel, and what colour the last render gave it. */
    fun describe(px: Int, py: Int): String {
        val i = py * W + px
        var k = -1
        for (j in 0 until waterCount) if (waterIdx[j] == i) k = j
        val w = if (k >= 0) {
            "water(#${waterX[k]},${waterY[k]} d=${"%.2f".format(waterD[k])} near=${waterNear[k]} sh=${waterSh[k]} kind=${waterKind[k]})"
        } else "land"
        return "picture $px,$py: $w zb=${zb[i]} colour=#%06x".format(frame[i] and 0xFFFFFF)
    }

    private class Item(
        val z: Double,
        val sprite: Sprite?,
        val sx: Int,
        val sy: Int,
        val sway: ((Double) -> Double)? = null,
        val flip: Boolean = false,
        val palm: Palm? = null,
        val reed: Reed? = null,
    )

    /** The frame seen as a surface, so the almanac's drawing functions can lay shadows straight onto it. */
    private val frameSurface: Surface by lazy { Surface(W, H, frame) }
    private val palmSurface: Surface by lazy { world.palmSurface() }
    private val palmAnchor: IntArray by lazy { world.palmAnchor(palmSurface) }

    private fun stamp(item: Item) {
        val sp = item.sprite
        if (sp == null) {
            val palm = item.palm
            if (palm != null) {
                palmSurface.px.fill(0)
                world.drawPalm(palmSurface, palm, palmAnchor[0], palmAnchor[1], clock, 1.0, 1.0)
                stampSurface(palmSurface, palmAnchor[0], palmAnchor[1], item.sx, item.sy, item.z.toInt())
            } else drawReed(item)
            return
        }
        stampSprite(sp, item.sx, item.sy, item.z.toInt(), item.flip, item.sway)
    }

    /** A sprite stamped at a point of the picture, each pixel only where nothing nearer is already drawn. */
    private fun stampSprite(sp: Sprite, sx: Int, sy: Int, z: Int, flip: Boolean = false, sway: ((Double) -> Double)? = null) {
        val ox = sx - (if (flip) sp.w - 1 - sp.ax else sp.ax)
        val oy = sy - sp.ay
        for (sy2 in max(0, -oy) until min(sp.h, H - oy)) {
            val row = sy2 * sp.w
            val dx = if (sway != null) sway((sp.ay - sy2).toDouble()).roundToInt() else 0
            val py = oy + sy2
            for (sx2 in max(0, -ox) until min(sp.w, W - ox)) {
                val c = sp.px[row + if (flip) sp.w - 1 - sx2 else sx2]
                if (c == 0) continue
                val px = ox + sx2 + dx
                if (px < 0 || px >= W) continue
                put(px, py, z, c)
            }
        }
    }

    /** A surface (a palm, a swaying tree) stamped at a point of the picture, through the depth test. */
    private fun stampSurface(surf: Surface, anchorX: Int, anchorY: Int, sx: Int, sy: Int, z: Int) {
        val ox = sx - anchorX
        val oy = sy - anchorY
        for (yy in 0 until surf.h) {
            val py = oy + yy
            if (py < 0 || py >= H) continue
            for (xx in 0 until surf.w) {
                val c = surf.px[yy * surf.w + xx]
                if (c == 0) continue
                val px = ox + xx
                if (px < 0 || px >= W) continue
                put(px, py, z, c)
            }
        }
    }

    private fun put(px: Int, py: Int, z: Int, c: Int) {
        val i = py * W + px
        if (zb[i] > z) return
        zb[i] = z
        if (isMarker(c)) {
            em[i] = c
            frame[i] = palette.markDay[(c shr 16) and 255]
        } else {
            frame[i] = c
            em[i] = 0
        }
    }

    private fun drawMapleLeaves() {
        for (leaf in mapleLeaves) {
            val x = leaf.x.roundToInt()
            val y = leaf.y.roundToInt()
            if (x < -2 || x >= W + 2 || y < -2 || y >= H + 2) continue
            // gentle rotation by picking one of two pixel patterns
            val rot = (leaf.rot % (2 * PI))
            val dx = if (rot < PI) 1 else -1
            // 3-pixel leaf
            if (x in 0 until W && y in 0 until H) frame[y * W + x] = leaf.col
            if (x + dx in 0 until W && y in 0 until H) frame[y * W + x + dx] = palette.tone(leaf.col, -1)
            if (x in 0 until W && y + 1 in 0 until H) frame[(y + 1) * W + x] = palette.tone(leaf.col, 1)
        }
    }

    private fun drawReed(item: Item) {
        val rd = item.reed ?: return
        val sway = sin(clock * 2.6 + rd.id) * 1.5 * detail
        for (j in 0..(4 * detail).roundToInt()) {
            val x = item.sx + (sway * j / (4 * detail)).roundToInt()
            val y = item.sy - j
            if (x in 0 until W && y in 0 until H) {
                frame[y * W + x] = if (j == (4 * detail).roundToInt()) palette.hex("#c9b46a") else palette.leaves["meadow"]!![min(3, j)]
            }
        }
    }

    private fun drawBoat() {
        if (!boatReady) return
        val d = detail
        val ba = boatAngle
        val bx = boatX + cos(ba) * 22 * d
        val by = boatY + sin(ba) * 7 * d
        val bob = Math.round(sin(clock * 1.4) * 1.5 - 1).toInt()
        if (!inSea(bx, by, 6 * d)) return
        for (k in 2 until 12) {
            val wx = (bx + sin(ba) * k * 1.6 * d).roundToInt()
            val wy = (by - cos(ba) * k * 0.5 * d + 1).roundToInt()
            if (wx in 0 until W && wy in 0 until H && waterKindAt(wy * W + wx) == 1 && (k + clock.toInt()) % 3 != 0) {
                frame[wy * W + wx] = if (k < 6) palette.P.FOAM else palette.P.FOAM2
            }
        }
        // the boat is drawn above everything on the sea, with the depth of the water it floats on
        stampSprite(world.boat, bx.roundToInt(), by.roundToInt() + bob, 100000, sin(ba) > 0)
    }

    private fun drawGulls() {
        val d = detail
        birds.forEachIndexed { k, b ->
            shadeEllipse(frameSurface, b.x + 14 * d, b.y + 30 * d, 2 * d, 1 * d, 0.7)
            stampSprite(world.gull[if (((clock * 6).toInt() + k) % 6 < 3) 0 else 1], b.x.roundToInt(), b.y.roundToInt(), 100000)
        }
    }

    private fun glow() {
        val hl = hourLight(tod)
        val on = hl.night > 0.18
        if (!on) return
        val share = clamp((hl.night - 0.15) * 2.5) * 0.9
        val d = detail
        for (i in frame.indices) {
            val m = em[i]
            if (m == 0) continue
            when ((m shr 16) and 255) {
                Marks.WIN -> if (windowLit(m and 0xFFFF, seed, clock, share, 0.85)) frame[i] = palette.lit
                Marks.LAMP -> frame[i] = palette.lamp
                Marks.TIP -> {
                    val c = if (twinkle(clock, (m and 0xFFFF) * 1.7) > 0.8) palette.tipBright else palette.tip
                    for (dd in intArrayOf(0, 1, W, W + 1)) if (i + dd < frame.size) frame[i + dd] = c
                }
                Marks.BEACON -> if (blink(clock, 5.0, 0.0, 0.3)) frame[i] = palette.beacon
            }
        }
        // the fire in the hill village's square
        val fx = fireScreen[0]
        val fy = fireScreen[1]
        for (k in 0 until 7) {
            val x = fx - 1 + (k % 3)
            val y = fy - 1 - k / 3 - (if (indexRandom(clock.toInt() * 7 + k, 11) < 0.5) 1 else 0)
            if (x in 0 until W && y in 0 until H) frame[y * W + x] = palette.fire[if (k < 3) 2 else if (k < 6) 1 else 0]
        }
        // lanterns rising
        for (l in lanterns) {
            val x = l.x.roundToInt()
            val y = l.y.roundToInt()
            if (y < 2) continue
            for ((dx, dy, c) in listOf(
                Triple(0, 0, palette.lamp), Triple(1, 0, palette.lit),
                Triple(0, 1, palette.lantern), Triple(1, 1, palette.lantern),
                Triple(0, 2, palette.tone(palette.lantern, -1)), Triple(1, 2, palette.tone(palette.lantern, -1)),
            )) {
                if (x + dx in 0 until W && y + dy in 0 until H) frame[(y + dy) * W + x + dx] = c
            }
        }
        // stars in the sea, each twinkling on its own
        for (y in 0 until H) {
            for (x in 0 until W) {
                val i = y * W + x
                if (waterKindAt(i) != 1 || latticeHashU(x, y, seed) % 487L != 0L) continue
                val k = twinkle(clock, (latticeHashU(x, y, 3) % 628L) / 100.0, 0.6)
                if (k > 0.55) frame[i] = mixPacked(frame[i], palette.star, (k - 0.4) * hl.night)
            }
        }
        for (f in flies) {
            val x = f.x.roundToInt()
            val y = f.y.roundToInt()
            if (x in 0 until W && y in 0 until H && twinkle(clock, f.seed * 3.0, 0.5) > 0.6) frame[y * W + x] = palette.firefly
        }
    }

    // ------------------------------------------------------------------------------------------ the small sprites
    private fun footStep(phase: Double, stride: Double): Double = max(0.0, cos(phase) * stride)

    private fun strideRate(stride: Double): Double = PI / (2 * stride)

    private val personCache = HashMap<Int, Sprite>()

    /** The sprite a walker is drawn with right now (for the preview tool: what its anchor is, beside where it stands). */
    fun personSpriteOf(p: Person): Sprite = personSprite(p, false, false)
    private fun personSprite(p: Person, legA: Boolean, legB: Boolean): Sprite {
        val key = (p.id shl 2) or ((if (legA) 1 else 0) shl 1) or (if (legB) 1 else 0)
        return personCache.getOrPut(key) { upscale(buildPerson(p, if (legA) 1 else 0, if (legB) 1 else 0), detail) }
    }

    private fun buildPerson(p: Person, lA: Int, lB: Int): Sprite {
        val r = Surface(9, 12)
        val ax = 4
        val ay = 10
        for ((x, l) in listOf(3 to lA, 5 to lB)) {
            for (y in 8..9 - l) r.px[y * 9 + x] = p.trousers
        }
        for (y in 5..7) for (x in 3..5) r.px[y * 9 + x] = if (x == 5) palette.tone(p.shirt, -1) else p.shirt
        r.px[6 * 9 + 2] = palette.tone(p.shirt, -1)
        r.px[6 * 9 + 6] = palette.tone(p.shirt, -1)
        for (y in 2..4) for (x in 3..5) r.px[y * 9 + x] = if (y == 2) p.hair else p.skin
        if (p.hat) {
            if (theme == Theme.MAPLE) {
                // tiny mushroom cap hat — Henesys beginner
                fillEllipse(r, 4.0, 1.8, 3.4, 2.0, palette.mushCapRed)
                r.px[1 * 9 + 3] = palette.mushSpot
                r.px[1 * 9 + 5] = palette.mushSpot
            } else {
                for (x in 2..6) r.px[2 * 9 + x] = palette.hex("#e0c068")
                r.px[1 * 9 + 4] = palette.hex("#c9a24a")
                r.px[1 * 9 + 3] = palette.hex("#e0c068")
                r.px[1 * 9 + 5] = palette.hex("#e0c068")
            }
        }
        outlinePass(r, palette.ink)
        return Sprite(r.w, r.h, r.px, ax, ay)
    }

    private val deerCache = HashMap<Int, Sprite>()
    private fun deerSprite(de: Deer, phase: Double, grazing: Boolean): Sprite {
        // Maple slimes hop; cache with theme + phase
        val baseKey = (de.seed shl 6) or ((Math.round(phase / 6.283 * 8).toInt() and 7) shl 1) or (if (grazing) 1 else 0)
        val key = if (theme == Theme.MAPLE) baseKey or (1 shl 20) else baseKey
        return deerCache.getOrPut(key) {
            val raw = if (theme == Theme.MAPLE) buildSlime(de, phase) else buildDeer(de, phase, grazing)
            upscale(raw, detail)
        }
    }

    /** MapleStory slime — round, bouncy, two eyes, tiny smile. Pure original art, Henesys vibe. */
    private fun buildSlime(de: Deer, phase: Double): Sprite {
        val r = Surface(14, 12)
        val ax = 7
        val ay = 10
        val s = de.size
        // hop: slimes squish and stretch
        val hop = max(0.0, sin(phase * 2.0)) * 1.6
        val squishX = 1.0 + hop * 0.08
        val squishY = 1.0 - hop * 0.10
        val bodyRx = 4.2 * s * squishX
        val bodyRy = 3.2 * s * squishY
        val bodyY = 6.5 - hop * 0.7
        // body
        fillEllipse(r, 7.0, bodyY, bodyRx, bodyRy, de.coat)
        // belly highlight
        fillEllipse(r, 7.0, bodyY + 0.8, bodyRx * 0.62, bodyRy * 0.55, palette.tone(de.coat, 1))
        // bottom squish contact
        fillEllipse(r, 7.0, 9.0 - hop * 0.3, bodyRx * 0.85, 1.0, palette.tone(de.coat, -1))
        // eyes
        val eyeY = (bodyY - 1.0).roundToInt()
        for ((dx, isLeft) in listOf(-2 to true, 2 to false)) {
            val ex = (7 + dx).coerceIn(1, 12)
            r.px[eyeY * 14 + ex] = palette.ink
            r.px[eyeY * 14 + ex] = if (isLeft) palette.ink else palette.ink
            // white glint
            if (eyeY - 1 >= 0) r.px[(eyeY - 1) * 14 + ex] = palette.hex("#ffffff")
            // blush
            val bx = ex + (if (isLeft) -2 else 2)
            if (bx in 0..13 && eyeY + 1 < 12) r.px[(eyeY + 1) * 14 + bx] = palette.hex("#ff8fb1")
        }
        // smile
        val mouthY = (bodyY + 0.6).roundToInt()
        r.px[mouthY * 14 + 7] = palette.ink
        if (7 - 1 >= 0) r.px[mouthY * 14 + 6] = palette.ink
        if (7 + 1 <= 13) r.px[mouthY * 14 + 8] = palette.ink
        outlinePass(r, palette.ink)
        return Sprite(r.w, r.h, r.px, ax, ay)
    }

    private fun buildDeer(de: Deer, phase: Double, grazing: Boolean): Sprite {
        val r = Surface(17, 13)
        val ax = 8
        val ay = 10
        val s = de.size
        fun leg(hx: Double, off: Double, side: Int) {
            val st = sin(phase + off) * s
            val x = Math.round(hx + st).toInt().coerceIn(0, 16)
            val top = 7
            val bottom = 9 - (if (max(0.0, cos(phase + off)) > 0.5) 1 else 0)
            for (y in top..bottom) r.px[y * 17 + x] = if (side == 1) de.coat else palette.tone(de.coat, -1)
        }
        leg(5.5, 0.0, 0)
        leg(5.5, PI * 0.5, 1)
        leg(10.5, PI, 0)
        leg(10.5, PI * 1.5, 1)
        fillEllipse(r, 8.0, 5.5, 3.6 * s, 1.9 * s, de.coat)
        fillEllipse(r, 8.0, 6.5, 2.6 * s, 0.7, de.belly)
        val hy = if (grazing) 7.0 else 3.4
        fillEllipse(r, 8 + 4.1 * s, hy, 1.5, 1.3, palette.tone(de.coat, 1))
        r.px[Math.round(hy - 1.6).toInt() * 17 + Math.round(8 + 3.6 * s).toInt()] = de.coat
        if (de.antlers && !grazing) {
            r.px[Math.round(hy - 2.4).toInt() * 17 + Math.round(8 + 3.4 * s).toInt()] = palette.hex("#d8c09a")
            r.px[Math.round(hy - 3.2).toInt() * 17 + Math.round(8 + 3 * s).toInt()] = palette.hex("#d8c09a")
        }
        r.px[4 * 17 + Math.round(8 - 3.8 * s).toInt()] = de.belly
        outlinePass(r, palette.ink)
        return Sprite(r.w, r.h, r.px, ax, ay)
    }
}

/** The whale's thickness along its spine: a head, shoulders, a tapering tail (the almanac's smoothCurve, sampled). */
private val WHALE_RADII = doubleArrayOf(
    2.6, 2.8479296855154193, 3.266565056139816, 3.3732444444444445, 2.9754831275720166,
    2.315005761316872, 1.6683555555555558, 1.3120757201646094, 1.0914951989026067, 0.8,
)
