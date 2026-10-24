// One seed, one island: the world behind the wallpaper, grown from a phrase with nothing but the almanac's techniques.
// A seed becomes noise and the noise an island; terraces rise, the sea comes in, the land is painted, a river finds its
// way down, a forest grows, villages and roads appear. Ported from examples/island/scene.js.
//
// The world is laid out in its own pixels: the almanac's 384 x 216, or `detail` times as many across, in which case the
// island's map grows to match (so a bigger picture is a finer island, not a stretched one).
package dev.almanac.island

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class House(
    val id: Int,
    val village: Int,
    val x0: Int,
    val y0: Int,
    val w: Int,
    val depth: Int,
    val level: Int,
    val hw: Int,
    val kind: String,
    val style: String,
    val roof: Int,
    val wall: Int,
    val door: Int,
    val chimney: Boolean,
)

class Village(
    val x: Int,
    val y: Int,
    val level: Int,
    val index: Int,
    val name: String,
    val styles: List<String>,
    val roofs: List<String>,
    val count: Int,
)

class Tree(
    val id: Int,
    val x: Int,
    val y: Int,
    val level: Int,
    val leaf: List<Int>,
    val size: Double,
    val phase: Double,
    val order: Double,
    val sprite: Sprite?,
)

class Palm(val x: Int, val y: Int, val lean: Int, val id: Int)
class Reed(val x: Int, val y: Int, val id: Int)
class Lamp(val x: Int, val y: Int)
class Spot(val x: Int, val y: Int, val level: Int, val score: Double, val i: Int)

/**
 * An island grown from a seed: terrain, villages, roads, the forest and every sprite that does not move. The life that
 * moves over it is in IslandScene.
 */
class World(
    val phrase: String,
    val W: Int = ArtWidth,
    val H: Int = ArtHeight,
    val detail: Double = 1.0,
    val palette: Palette = Palette(),
    val theme: Theme = palette.theme,
) {
    companion object {
        const val ArtWidth = 384
        const val ArtHeight = 216
        const val Levels = 7
        const val MapTiles = 200
        const val Zo = 0.5
        const val Rough = 0.6
        const val Land = 0.44
        const val DefaultPhrase = "heron-bay-8"
    }

    /** The lift one terrace stands above the one below, in pixels. */
    val lift: Int = max(2.0, 4 * detail).roundToInt()

    val seed: Int = seedOf(phrase)

    /** Diagnostics: what the island was grown from, and what grew on it. */
    fun report(): List<String> {
        val hist = IntArray(Levels + 1)
        for (i in 0 until n) hist[if (LEV[i] < 0) 0 else LEV[i] + 1]++
        val rivCount = riv.count { it }
        return listOf(
            "seed $seed \"$phrase\" ${W}x${H} (${GH} rows)",
            *diag.toTypedArray(),
            "levels (sea,0..6): " + hist.joinToString(","),
            "river pixels $rivCount, cut ${"%.4f".format(cut)}, step ${"%.4f".format(step)}",
            "${houses.size} houses, ${trees.size} trees, ${palms.size} palms, ${reeds.size} reeds, ${lamps.size} lamps, ${roads.size} roads, lighthouse ${lightX},${lightY}",
        )
    }

    /** The world's rows: a few more than the picture's, since high land is lifted into view. */
    val GH: Int = H + (32 * detail).roundToInt()

    val n: Int = W * GH
    fun at(x: Int, y: Int): Int = y * W + x

    val E: FloatArray = FloatArray(n)
    val LEV: IntArray = IntArray(n)
    val MO: FloatArray = FloatArray(n)
    val FO: FloatArray = FloatArray(n)
    val GR: FloatArray = FloatArray(n)
    val OCT: Array<FloatArray> = Array(4) { FloatArray(n) }
    val patches: IntArray = IntArray(n) { -1 }

    lateinit var dSea: ShortArray
    lateinit var dLand: ShortArray
    lateinit var dBeach: ShortArray
    val riv: BooleanArray = BooleanArray(n)
    lateinit var dRiv: ShortArray
    lateinit var dToRiv: ShortArray

    val res: IntArray = IntArray(n)
    val field: IntArray = IntArray(n)
    val path: IntArray = IntArray(n)
    val pierPx: IntArray = IntArray(n)

    val houses = ArrayList<House>()
    val villages = ArrayList<Village>()
    val trees = ArrayList<Tree>()
    val palms = ArrayList<Palm>()
    val reeds = ArrayList<Reed>()
    val lamps = ArrayList<Lamp>()
    val roads = ArrayList<List<IntArray>>()
    val housesByVillage = ArrayList<List<House>>()
    val diag = ArrayList<String>()

    var peak = 0
    var peakX = 0
    var peakY = 0
    var cut = 0.0
    var step = 0.0

    var lightX = -1
    var lightY = -1
    var pierX = -1
    var pierY = -1
    var pierDirX = 0.0
    var pierDirY = 0.0
    var meadow = Spot(0, 0, 0, 0.0, 0)

    val summitTree: Sprite?
    val gull: Array<Sprite>
    val boat: Sprite
    val tower: Sprite?
    val hen: Array<Sprite>
    val post: Sprite
    val houseSprites: HashMap<Int, Sprite> = HashMap()
    val treeSprites: HashMap<Long, Sprite?> = HashMap()

    init {
        growTerrain()
        findShore()
        findRiver()
        placeVillages()
        layFields()
        layRoads()
        growForest()
        summitTree = buildSummitTree()
        gull = arrayOf(
            spriteFromRows(listOf("#...#", ".#.#.", "..#.."), { palette.gull }, palette.ink, outline = false),
            spriteFromRows(listOf(".....", "##.##", "..#.."), { palette.gull }, palette.ink, outline = false),
        ).map { upscale(it, detail) }.toTypedArray()
        boat = upscale(buildBoat(), detail)
        tower = if (lightX >= 0) {
            upscale(
                spriteFromRows(
                    listOf(".bbb.", ".rrr.", ".www.", ".rrr.", ".www.", ".rrr.", "sssss"),
                    { ch ->
                        when (ch) {
                            'b' -> marker(Marks.BEACON)
                            'r' -> palette.towerRed
                            'w' -> palette.tower
                            else -> palette.stone
                        }
                    },
                    palette.ink,
                ),
                detail,
            )
        } else null
        post = upscale(
            spriteFromRows(listOf("l", "p", "p", "p"), { ch -> if (ch == 'l') marker(Marks.LAMP) else palette.pole }, palette.ink),
            detail,
        )
        hen = arrayOf(
            spriteFromRows(listOf(".r.", "wwo", "ww."), { ch -> when (ch) { 'r' -> palette.hex("#d8402e"); 'o' -> palette.hex("#e8a03a"); else -> palette.hex("#f7f2e6") } }, palette.ink),
            spriteFromRows(listOf("...", "ww.", "wwo"), { ch -> if (ch == 'o') palette.hex("#e8a03a") else palette.hex("#f7f2e6") }, palette.ink),
        ).map { upscale(it, detail) }.toTypedArray()
    }

    fun houseSprite(h: House): Sprite = houseSprites.getOrPut(h.id) { buildHouse(h) }

    // ---------------------------------------------------------------------------------------------- the land
    private fun growTerrain() {
        // the island keeps its shape whatever the picture's size: more pixels across is a finer island, not a stretched
        // one, and the same seed gives the same island at any resolution. A frame taller than it is wide gets a map
        // taller than it is wide too, so the island fills a phone's screen as it fills a 16:9 one.
        val tilesX = MapTiles.toDouble()
        val tilesY = MapTiles * (H.toDouble() / W) * (ArtWidth.toDouble() / ArtHeight)
        val z = Zo / detail
        val wide = W >= H * 1.15
        val sx = if (wide) 0.7 else 0.9
        val sy = if (wide) 1.6 else 1.1
        for (y in 0 until GH) {
            for (x in 0 until W) {
                val i = at(x, y)
                val tx = (x - W / 2.0) * z + tilesX / 2.0
                val ty = (y - H / 2.0) * z + tilesY / 2.0
                E[i] = islandHeight(tx, ty, tilesX, tilesY, seed, sx, sy, Rough).toFloat()
                for (k in 0 until 4) {
                    OCT[k][i] = clamp(
                        fractalNoise(tx / (tilesX * 0.28), ty / (tilesX * 0.28), seed % 9973, k + 1, Rough) * 1.25 - 0.1,
                    ).toFloat()
                }
                MO[i] = fractalNoise(x / (70 * detail), y / (70 * detail), seed + 3, 3).toFloat()
                FO[i] = fractalNoise(x / (38 * detail), y / (38 * detail), seed + 5, 3).toFloat()
                GR[i] = latticeRandom(x, y, seed).toFloat()
            }
        }
        val sorted = FloatArray(W * H) { E[it] }.also { it.sort() }
        cut = sorted[(sorted.size * (1 - Land)).toInt().coerceIn(0, sorted.size - 1)].toDouble()
        val landList = sorted.filter { it >= cut }.map { it.toDouble() }
        val landE = landList.toDoubleArray()
        step = (landE[(landE.size * 0.97).toInt().coerceAtMost(landE.size - 1)] - cut) / (Levels - 1)
        for (i in 0 until n) LEV[i] = if (E[i] < cut) -1 else terraceLevel(E[i] - cut, step, Levels)

        // specks of land too small to stand on go back under the sea
        for (i in 0 until n) {
            if (LEV[i] < 0 || patches[i] >= 0) continue
            val patch = floodFill(i) { j ->
                val x = j % W
                intArrayOf(j - 1, j + 1, j - W, j + W).filter { k ->
                    k in 0 until n && LEV[k] >= 0 && abs((k % W) - x) <= 1
                }.toIntArray()
            }
            for (j in patch) patches[j] = i
            if (patch.size < 40 * detail * detail) {
                for (j in patch) {
                    LEV[j] = -1
                    E[j] = (cut - 0.002).toFloat()
                    patches[j] = -1
                }
            }
        }
        for (i in 0 until W * H) if (E[i] > E[peak]) peak = i
        peakX = peak % W
        peakY = peak / W
    }

    fun lev(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= W || y >= GH) -1 else LEV[at(x, y)]

    private fun findShore() {
        fun mask(f: (Int) -> Boolean): BooleanArray = BooleanArray(n) { f(it) }
        dSea = chamferDistance(mask { LEV[it] < 0 }, W, GH)
        dLand = chamferDistance(mask { LEV[it] >= 0 }, W, GH)
        dBeach = chamferDistance(mask { LEV[it] != 0 }, W, GH)
    }

    fun onMain(x: Int, y: Int): Boolean =
        patches[at(clampInt(x, 0, W - 1), clampInt(y, 0, GH - 1))] == patches[peak]

    private fun findRiver() {
        val cand = BooleanArray(n) { i ->
            LEV[i] >= 0 && windingRiver(i % W.toDouble(), (i / W).toDouble(), seed, 0.06).depth > 0
        }
        var src = -1
        for (i in 0 until n) if (cand[i] && (src < 0 || E[i] > E[src])) src = i
        val ord = IntArray(n) { -1 }
        var front = if (src >= 0) intArrayOf(src) else IntArray(0)
        var pass = 0
        if (src >= 0) ord[src] = 0
        while (front.isNotEmpty()) {
            pass++
            val next = ArrayList<Int>()
            for (i in front) {
                for (j in intArrayOf(i - 1, i + 1, i - W, i + W)) {
                    if (j in 0 until n && cand[j] && ord[j] < 0) {
                        ord[j] = pass
                        next.add(j)
                    }
                }
            }
            front = next.toIntArray()
        }
        for (i in 0 until n) if (ord[i] >= 0) riv[i] = true
        dRiv = chamferDistance(riv, W, GH)
        dToRiv = chamferDistance(BooleanArray(n) { !riv[it] }, W, GH)
    }

    // ---------------------------------------------------------------------------------------------- villages
    private fun flatness(x: Int, y: Int, r: Int): Double {
        val l = lev(x, y)
        var same = 0
        var all = 0
        val s = max(1, (2 * detail).roundToInt())
        var dy = -r
        while (dy <= r) {
            var dx = -r
            while (dx <= r) {
                if (dx * dx + dy * dy <= r * r) {
                    all++
                    val ix = clampInt(x + dx, 0, W - 1)
                    val iy = clampInt(y + dy, 0, GH - 1)
                    if (lev(x + dx, y + dy) == l && !riv[at(ix, iy)]) same++
                }
                dx += s
            }
            dy += s
        }
        return if (all == 0) 0.0 else same.toDouble() / all
    }

    private fun spots(clear: Double): List<Spot> {
        val out = ArrayList<Spot>()
        val margin = (24 * detail).roundToInt()
        val s = max(1, (4 * detail).roundToInt())
        var y = margin
        while (y < H - margin) {
            var x = margin
            while (x < W - margin) {
                val i = at(x, y)
                val l = LEV[i]
                if (l in 1..5 && onMain(x, y) && dToRiv[i] >= clear) {
                    out.add(Spot(x, y, l, flatness(x, y, (15 * detail).roundToInt()), i))
                }
                x += s
            }
            y += s
        }
        return out
    }

    private fun placeVillages() {
        var sites: List<Spot> = emptyList()
        for (clear in listOf(30.0, 12.0)) {
            val all = spots(clear * detail)
            for ((flat, top) in listOf(0.9 to 3, 0.8 to 3, 0.7 to 4, 0.5 to 5, -1.0 to 5)) {
                sites = all.filter { it.score > flat && it.level <= top }
                if (sites.size >= 2) break
            }
            if (sites.size >= 2) break
        }
        if (sites.isEmpty()) sites = listOf(Spot(peakX, peakY, LEV[peak], 1.0, peak))
        fun far(a: Spot, b: Spot) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        val harbour = sites.filter { it.level == 1 }.minByOrNull { dBeach[it.i].toInt() } ?: sites[0]
        val hill = sites.filter { it.level >= 2 && far(it, harbour) > 110 * detail }
            .minByOrNull { abs(dToRiv[it.i] - 75 * detail) } ?: sites.maxByOrNull { far(it, harbour) }!!
        if (theme == Theme.MAPLE) {
            // Henesys mushroom village + hill maple cottages
            villages.add(Village(harbour.x, harbour.y, harbour.level, 0, "henesys", listOf("mushroom", "mushroom"), listOf("#e85d4c", "#5bb0e0"), 8))
            villages.add(Village(hill.x, hill.y, hill.level, 1, "ellinia", listOf("mushroom", "mushroom"), listOf("#f5a623", "#d93a2b"), 8))
        } else {
            villages.add(Village(harbour.x, harbour.y, harbour.level, 0, "harbour", listOf("thatch", "plank"), listOf("#caa35c", "#8d6b4b"), 8))
            villages.add(Village(hill.x, hill.y, hill.level, 1, "hill", listOf("redtile", "tile"), listOf("#bb5840", "#5c719c"), 8))
        }

        val d = detail
        villages.forEach { v ->
            for (dy in -((5 * d).roundToInt())..((5 * d).roundToInt())) {
                for (dx in -((6 * d).roundToInt())..((6 * d).roundToInt())) {
                    res[at(clampInt(v.x + dx, 0, W - 1), clampInt(v.y + dy, 0, GH - 1))] = 2
                }
            }
            val r = SeededRandom(seed + 101 * (v.index + 1))
            var tries = 0
            var made = 0
            val list = ArrayList<House>()
            while (tries < 1500 && made < v.count) {
                tries++
                val a = r.range(0.0, PI * 2)
                val rad = r.range(8 * d, (22 + tries / 60.0) * d)
                val w = r.range((8 * d).roundToInt(), (12 * d).roundToInt())
                val dd = r.range((5 * d).roundToInt(), (7 * d).roundToInt())
                val x0 = (v.x + cos(a) * rad * 1.3 - w / 2.0).roundToInt()
                val y0 = (v.y + sin(a) * rad * 0.8 - dd / 2.0).roundToInt()
                val l = lev(x0, y0)
                var ok = l >= 1
                var y = y0 - (2 * d).roundToInt()
                while (y <= y0 + dd + (1 * d).roundToInt() && ok) {
                    var x = x0 - (2 * d).roundToInt()
                    while (x <= x0 + w + (1 * d).roundToInt() && ok) {
                        val i = at(clampInt(x, 0, W - 1), clampInt(y, 0, GH - 1))
                        if (lev(x, y) != l || riv[i] || res[i] != 0 || dLand[i] < 12 * d || dToRiv[i] < 9 * d) ok = false
                        x++
                    }
                    y++
                }
                if (!ok) continue
                for (yy in y0 - (1 * d).roundToInt()..y0 + dd) {
                    for (xx in x0 - (1 * d).roundToInt()..x0 + w) {
                        val i = at(clampInt(xx, 0, W - 1), clampInt(yy, 0, GH - 1))
                        res[i] = 1
                    }
                }
                val k = r.range(0, 1)
                val h = House(
                    id = houses.size,
                    village = v.index,
                    x0 = x0,
                    y0 = y0,
                    w = w,
                    depth = dd,
                    level = l,
                    hw = r.range((4 * d).roundToInt(), (6 * d).roundToInt()),
                    kind = if (r.chance(0.5)) "gable" else "hip",
                    style = v.styles[k],
                    roof = palette.hex(v.roofs[k]),
                    wall = if (r.chance(0.6)) palette.plaster else palette.timber,
                    door = r.range(max(1, (1 * d).roundToInt()), max(2, w - (3 * d).roundToInt())),
                    chimney = r.chance(0.45),
                )
                houses.add(h)
                list.add(h)
                made++
            }
            housesByVillage.add(list)
        }
    }

    private fun layFields() {
        val d = detail
        villages.forEach { v ->
            val ang = 0.35 + v.index * 0.5
            val cs = cos(ang)
            val sn = sin(ang)
            for (y in v.y - (44 * d).roundToInt()..v.y + (44 * d).roundToInt()) {
                for (x in v.x - (60 * d).roundToInt()..v.x + (60 * d).roundToInt()) {
                    if (x < 0 || y < 0 || x >= W || y >= GH) continue
                    val i = at(x, y)
                    val dist = hypot((x - v.x) * 0.8, (y - v.y).toDouble())
                    if (dist < 15 * d || dist > (30 + 12 * FO[i]) * d || LEV[i] != v.level || riv[i] || res[i] != 0 ||
                        dToRiv[i] < 6 * d || dLand[i] < 15 * d
                    ) continue
                    val p = plotGrid((x - v.x).toDouble(), (y - v.y).toDouble(), 7 * d, ang)
                    val crop = palette.crops[latticeHash(p.iu, p.iv, seed) and 7]
                    if (crop == 0) continue
                    val u = (x - v.x) * cs + (y - v.y) * sn
                    val c = if (p.edge < 0.9 * d) {
                        palette.bank
                    } else if (crop == palette.paddy) {
                        if ((x + 2 * y) % max(2, (4 * d).roundToInt()) == 0) palette.sprout else crop
                    } else {
                        if (u.toInt() and 1 != 0) palette.tone(crop, -1) else crop
                    }
                    field[i] = c
                    res[i] = 3
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- roads
    private val pc: Int get() = max(2, (3 * detail).roundToInt())

    private fun cellFree(cx: Int, cy: Int): Boolean {
        val x = cx * pc + 1
        val y = cy * pc + 1
        val m = (3 * detail).roundToInt()
        if (x < m || y < m || x >= W - m || y >= H - m) return false
        val i = at(x, y)
        if (LEV[i] < 0 || res[i] == 1) return false
        var lo = 9
        var hi = -9
        val s = max(1, (3 * detail).roundToInt())
        var dy = -s
        while (dy <= s) {
            var dx = -s
            while (dx <= s) {
                val l = lev(x + dx, y + dy)
                lo = min(lo, l)
                hi = max(hi, l)
                dx += s
            }
            dy += s
        }
        return hi - lo <= 1
    }

    private fun layRoads() {
        val d = detail
        val hs = villages[0]
        var pier: IntArray? = null
        var r = 4.0 * d
        while (r < 90 * d && pier == null) {
            for (a in 0 until 64) {
                val x = (hs.x + cos(a / 64.0 * PI * 2) * r).roundToInt()
                val y = (hs.y + sin(a / 64.0 * PI * 2) * r).roundToInt()
                if (lev(x, y) == 0 && dLand[at(x, y)] <= 6 * d && dLand[at(x, y)] >= 3 * d) {
                    pier = intArrayOf(x, y)
                    break
                }
            }
            r += max(1.0, 2 * d)
        }
        if (pier != null) {
            pierX = pier[0]
            pierY = pier[1]
            var best = -1.0
            var dirX = 0.0
            var dirY = 1.0
            for (a in 0 until 16) {
                val dx = cos(a / 16.0 * PI * 2)
                val dy = sin(a / 16.0 * PI * 2)
                val x = (pierX + dx * 14 * d).roundToInt()
                val y = (pierY + dy * 14 * d).roundToInt()
                if (x >= 0 && y >= 0 && x < W && y < H && LEV[at(x, y)] < 0 && dSea[at(x, y)] > best) {
                    best = dSea[at(x, y)].toDouble()
                    dirX = dx
                    dirY = dy
                }
            }
            pierDirX = dirX
            pierDirY = dirY
            val across = max(1, (1 * d).roundToInt())
            for (k in 0..(16 * d).roundToInt()) {
                val x = (pierX + dirX * k).roundToInt()
                val y = (pierY + dirY * k).roundToInt()
                for (oy in 0..across) {
                    for (ox in 0..across) {
                        val i = at(clampInt(x + ox, 0, W - 1), clampInt(y + oy, 0, GH - 1))
                        if (LEV[i] >= 0) continue
                        pierPx[i] = when (roofTexel("plank", x + ox, y + oy, pierX, pierY)) {
                            "edge", "deep" -> palette.tone(palette.plank, -1)
                            "lit" -> palette.tone(palette.plank, 1)
                            else -> palette.plank
                        }
                    }
                }
            }
        }
        val routes = ArrayList<Pair<IntArray, IntArray>>()
        routes.add(intArrayOf(villages[0].x, villages[0].y) to intArrayOf(villages[1].x, villages[1].y))
        routes.add(intArrayOf(villages[1].x, villages[1].y) to intArrayOf(peakX, peakY + (6 * d).roundToInt()))
        if (pier != null) routes.add(intArrayOf(villages[0].x, villages[0].y) to intArrayOf(pierX, pierY))
        routes.forEachIndexed { ri, route ->
            val a = route.first
            val b = route.second
            val ca = nearestFreeCell(Math.round((a[0] - 1) / pc.toDouble()).toInt(), Math.round((a[1] - 1) / pc.toDouble()).toInt(), ::cellFree, (20 * d).roundToInt()) ?: return@forEachIndexed
            val cb = nearestFreeCell(Math.round((b[0] - 1) / pc.toDouble()).toInt(), Math.round((b[1] - 1) / pc.toDouble()).toInt(), ::cellFree, (20 * d).roundToInt()) ?: return@forEachIndexed
            val cells = gridAStar(ca, cb, (60000 * d).roundToInt(), ::cellFree) ?: return@forEachIndexed
            val jitter = 2.4 * d
            val pts = cells.mapIndexed { k, c ->
                intArrayOf(
                    (c[0] * pc + 1 + Math.round((noiseLine(k * 0.3, seed + ri) - 0.5) * jitter).toInt()),
                    (c[1] * pc + 1 + Math.round((noiseLine(k * 0.3 + 50, seed + ri) - 0.5) * jitter).toInt()),
                )
            }
            val line = ArrayList<IntArray>()
            for (k in 1 until pts.size) {
                lineCells(pts[k - 1][0].toDouble(), pts[k - 1][1].toDouble(), pts[k][0].toDouble(), pts[k][1].toDouble()) { x, y ->
                    val last = line.lastOrNull()
                    if (last == null || last[0] != x || last[1] != y) line.add(intArrayOf(x, y))
                }
            }
            line.forEach { p ->
                val x = p[0]
                val y = p[1]
                val w = max(1, (1 * d).roundToInt())
                for (oy in 0..w) {
                    for (ox in 0..w) {
                        val i = at(clampInt(x + ox, 0, W - 1), clampInt(y + oy, 0, GH - 1))
                        if (LEV[i] < 0) continue
                        path[i] = when {
                            GR[i] < 0.2 -> palette.tone(palette.dirt, -1)
                            GR[i] > 0.85 -> palette.tone(palette.dirt, 1)
                            else -> palette.dirt
                        }
                    }
                }
                val wide = (3 * d).roundToInt()
                for (dy in -wide..wide) {
                    for (dx in -wide..wide) {
                        val i = at(clampInt(x + dx, 0, W - 1), clampInt(y + dy, 0, GH - 1))
                        if (res[i] == 0) res[i] = 4
                    }
                }
            }
            if (line.size >= 2) roads.add(line)
        }
        diag.add("roads: " + roads.joinToString(", ") { "${it.size}" })
        // lanterns along the roads, every so often, a step to the side
        roads.forEachIndexed { ri, line ->
            if (line.size < 14 * d) return@forEachIndexed
            for (s in scatterAlong(6 * d, line.size - 6 * d, 26 * d, seed + ri, 0.85)) {
                val k = clampInt(Math.round(s.x).toInt(), 1, line.size - 2)
                val x = line[k][0]
                val y = line[k][1]
                val dx = line[k + 1][0] - line[k - 1][0]
                val dy = line[k + 1][1] - line[k - 1][1]
                val side = if (s.variant and 1 != 0) 1 else -1
                val len = hypot(dx.toDouble(), dy.toDouble()).let { if (it == 0.0) 1.0 else it }
                val lx = (x - (dy / len) * 3 * d * side).roundToInt()
                val ly = (y + (dx / len) * 3 * d * side).roundToInt()
                if (lev(lx, ly) >= 0 && !riv[at(clampInt(lx, 0, W - 1), clampInt(ly, 0, GH - 1))]) lamps.add(Lamp(lx, ly))
            }
        }
        meadow = deerMeadow()
    }

    private fun deerMeadow(): Spot {
        var best = Spot(peakX, peakY, LEV[peak], -1e9, peak)
        val hill = villages[1]
        val s = max(1, (6 * detail).roundToInt())
        var y = (20 * detail).roundToInt()
        while (y < H - (20 * detail).roundToInt()) {
            var x = (20 * detail).roundToInt()
            while (x < W - (20 * detail).roundToInt()) {
                if (lev(x, y) in 1..2 && res[at(x, y)] == 0) {
                    var free = 0
                    val rx = (20 * detail).roundToInt()
                    val ry = (14 * detail).roundToInt()
                    var dy = -ry
                    while (dy <= ry) {
                        var dx = -rx
                        while (dx <= rx) {
                            val ix = clampInt(x + dx, 0, W - 1)
                            val iy = clampInt(y + dy, 0, GH - 1)
                            if (lev(ix, iy) == lev(x, y) && res[at(ix, iy)] == 0 && !riv[at(ix, iy)]) free++
                            dx += max(1, (4 * detail).roundToInt())
                        }
                        dy += max(1, (4 * detail).roundToInt())
                    }
                    val score = free - abs(hypot((x - hill.x).toDouble(), (y - hill.y).toDouble()) - 60 * detail) * 0.2
                    if (score > best.score) best = Spot(x, y, lev(x, y), score, at(x, y))
                }
                x += s
            }
            y += s
        }
        return best
    }

    // ---------------------------------------------------------------------------------------------- the forest
    private fun growForest() {
        val d = detail
        var seen = 0
        var failLevel = 0
        var failRes = 0
        var failRiv = 0
        var failLand = 0
        var failBelow = 0
        var failChance = 0
        val grid = max(3, (6 * d).roundToInt())
        for (gy in 0 until GH / grid) {
            for (gx in 0 until W / grid) {
                val id = gy * 97 + gx
                val x = Math.round(gx * grid + indexRandom(id, seed + 1) * (grid - 1)).toInt()
                val y = Math.round(gy * grid + indexRandom(id, seed + 2) * (grid - 1)).toInt()
                if (x < 2 * d || x >= W - 2 * d || y < 4 * d || y >= GH - 1) continue
                seen++
                val i = at(x, y)
                val l = LEV[i]
                val f = FO[i]
                if (l < 1) { failLevel++; continue }
                if (res[i] != 0) { failRes++; continue }
                if (dToRiv[i] < 7 * d) { failRiv++; continue }
                if (dLand[i] < 9 * d) { failLand++; continue }
                if (lev(x, y + 1) != l) { failBelow++; continue }
                val p = when {
                    l <= 2 -> if (f > 0.56) 0.75 else 0.06
                    l <= 4 -> if (f > 0.44) 0.88 else 0.22
                    l == 5 -> if (f > 0.45) 0.55 else 0.1
                    else -> 0.12
                }
                if (indexRandom(id, seed + 3) > p) { failChance++; continue }
                val blossom = l <= 2 && indexRandom(id, seed + 4) < 0.18
                val cone = l >= 5 || (l >= 3 && indexRandom(id, seed + 5) < 0.4)
                val size = 0.72 + indexRandom(id, seed + 6) * 0.45
                val lean = Math.round((indexRandom(id, seed + 7) - 0.5) * 2).toInt()
                val leaf = when {
                    theme == Theme.MAPLE && blossom -> palette.leaves["maple"]!!
                    theme == Theme.MAPLE && cone -> palette.leaves["pine"]!!
                    theme == Theme.MAPLE && l <= 2 -> palette.leaves["mapleGreen"]!!
                    theme == Theme.MAPLE -> palette.leaves["maple"]!!
                    blossom -> palette.leaves["blossom"]!!
                    cone -> palette.leaves["pine"]!!
                    l <= 2 -> palette.leaves["meadow"]!!
                    else -> palette.leaves["wood"]!!
                }
                val sprite = buildTree(leaf, cone, lean, size, seed * 7 + id)
                trees.add(
                    Tree(
                        trees.size, x, y, l, leaf, size, indexRandom(id, seed + 8) * 6.28,
                        1 - f + indexRandom(id, seed + 9) * 0.25, sprite,
                    ),
                )
            }
        }
        diag.add("trees: seen $seen, level $failLevel, res $failRes, river $failRiv, land $failLand, below $failBelow, chance $failChance, made ${trees.size}")
        // palms on the beaches
        val pgrid = max(4, (9 * d).roundToInt())
        for (gy in 0 until GH / pgrid) {
            for (gx in 0 until W / pgrid) {
                val id = gy * 61 + gx
                val x = Math.round(gx * pgrid + indexRandom(id, seed + 11) * (pgrid - 1)).toInt()
                val y = Math.round(gy * pgrid + indexRandom(id, seed + 12) * (pgrid - 1)).toInt()
                val i = at(clampInt(x, 0, W - 1), clampInt(y, 0, GH - 1))
                if (lev(x, y) != 0 || dLand[i] < 5 * d || res[i] != 0 || indexRandom(id, seed + 13) > 0.45 || palms.size > 11 * d) continue
                palms.add(Palm(x, y, if (indexRandom(id, seed + 14) < 0.5) -((3 * d).roundToInt()) else (3 * d).roundToInt(), id))
            }
        }
        // reeds along the river
        val every = max(2, (13 * d).roundToInt())
        var reedSeen = 0
        for (i in 0 until W * H) {
            if (riv[i] || LEV[i] < 1 || dToRiv[i] < 3 * d || dToRiv[i] > 5 * d || res[i] != 0) continue
            reedSeen++
            if (latticeHashU(i % W, i / W, seed) % every == 0L) reeds.add(Reed(i % W, i / W, reeds.size))
        }
        diag.add("reeds: candidates $reedSeen, every $every, made ${reeds.size}, lamps ${lamps.size}")
        // a lighthouse on the highest islet, or on the headland furthest from the summit
        for (i in 0 until W * H) {
            val x = i % W
            val y = i / W
            if (LEV[i] < 0 || dLand[i] < 7 * d || onMain(x, y) || y < 12 * d || x < 6 * d || x > W - 6 * d) continue
            if (lightX < 0 || E[i] > E[at(lightX, lightY)]) {
                lightX = x
                lightY = y
            }
        }
        if (lightX < 0) {
            var best = -1.0
            for (i in 0 until W * H) {
                val x = i % W
                val y = i / W
                if (LEV[i] >= 0 && dLand[i] < 18 * d && dLand[i] > 9 * d && res[i] == 0 &&
                    y > 14 * d && x > 8 * d && x < W - 8 * d
                ) {
                    val dd = hypot((x - peakX).toDouble(), (y - peakY).toDouble())
                    if (dd > best) {
                        best = dd
                        lightX = x
                        lightY = y
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- the sprites
    private fun buildTree(leaf: List<Int>, cone: Boolean, lean: Int, size: Double, treeSeed: Int): Sprite? {
        val d = detail
        val s = size * d
        val w = (34 * d).roundToInt()
        val h = (44 * d).roundToInt()
        val ax = w / 2
        val ay = h - (3 * d).roundToInt()
        val r = Surface(w, h)
        val rnd = SeededRandom(treeSeed)
        if (s < 0.2) {
            r.px[ay * w + ax] = leaf[2]
            r.px[(ay - 1) * w + ax] = leaf[3]
        } else if (!cone) {
            val rx = (2.4 + 4 * s) * d
            val ry = rx * 0.84
            val th = max(1.0, Math.round(1 + 3.2 * s).toDouble() * d)
            val cy = ay - th - ry * 0.8
            barkTrunk(r, ax.toDouble(), cy + ry * 0.3, ay, if (s > 0.55) (2 * d).roundToInt() else max(1, d.roundToInt()), palette.bark, (lean * d).roundToInt())
            leafCrown(
                r, rnd, (ax + lean * d).toDouble(), cy, rx, ry, leaf,
                bloom = if (leaf === palette.leaves["blossom"]) palette.hex("#fff0f6") else 0,
                bloomEvery = max(3, (7 * d).roundToInt()), bloomSeed = treeSeed,
            )
        } else {
            val th = max(1.0, Math.round(1 + 2 * s).toDouble() * d)
            barkTrunk(r, ax.toDouble(), ay - th - 6 * s * d, ay, max(1, d.roundToInt()), palette.bark, 0)
            for (j in 0 until 3) {
                val rx = (3.6 - j * 1.05) * (0.45 + s * 0.62) * d
                leafCrown(
                    r, rnd, ax.toDouble(), ay - th - rx * 0.55 - j * 4 * d * (0.45 + s * 0.55), rx, rx * 0.72, leaf,
                    clumpsWanted = 4,
                )
            }
        }
        outlinePass(r, palette.inkTree)
        return trim(r, ax, ay)
    }

    /** The summit's great tree: a broad crown of blossom on a thick trunk, its tips marked to glow after dark. */
    private fun buildSummitTree(): Sprite? {
        val d = detail
        val w = (44 * d).roundToInt()
        val h = (40 * d).roundToInt()
        val ax = w / 2
        val ay = h - (3 * d).roundToInt()
        val r = Surface(w, h)
        val rnd = SeededRandom(seed + 77)
        barkTrunk(r, ax.toDouble(), (ay - 16 * d), ay, (3 * d).roundToInt(), palette.bark, 0)
        val summitLeaf = if (theme == Theme.MAPLE) palette.leaves["maple"]!! else palette.leaves["blossom"]!!
        val summitBloom = if (theme == Theme.MAPLE) palette.hex("#ffe9a8") else palette.hex("#fff0f6")
        val crown = leafCrown(
            r, rnd, ax.toDouble(), ay - (17 * d), (12 * d), (9 * d), summitLeaf,
            bloom = summitBloom, bloomEvery = max(3, (6 * d).roundToInt()), bloomSeed = seed,
        )
        for ((k, c) in crown.withIndex()) {
            if (k % 2 != 0) continue
            val mx = c[0].roundToInt().coerceIn(0, w - 1)
            val my = c[1].roundToInt().coerceIn(0, h - 1)
            if (r.px[my * w + mx] != 0) r.px[my * w + mx] = marker(Marks.TIP, k)
        }
        outlinePass(r, palette.inkTree)
        return trim(r, ax, ay)
    }

    /** A house: its roof seen from above, and its front wall below, with a door and windows that light after dark. */
    private fun buildHouse(h: House): Sprite {
        // MapleStory mushroom houses: bright dome caps with white spots — Henesys style
        if (theme == Theme.MAPLE && h.style.startsWith("mushroom")) {
            return buildMushroomHouse(h)
        }
        val d = detail
        val w = h.w
        val dd = h.depth
        val hw = h.hw
        val r = Surface(w + (6 * d).roundToInt(), dd + hw + (6 * d).roundToInt())
        val ax = (w shr 1) + (3 * d).roundToInt()
        val ay = dd + hw + (3 * d).roundToInt()
        val by = ay - hw
        val plan = roofPlan(h.kind, w, dd, max(1, d.roundToInt()), ax, by)
        val tex = mapOf(
            "seam" to -1, "mortar" to 1, "lit" to 1, "shaded" to -1, "edge" to -1, "deep" to -2, "glow" to 1, "dim" to -1,
        )
        for (y in plan.yN..plan.yS) {
            for (x in plan.x0..plan.x1) {
                if (x < 0 || y < 0 || x >= r.w || y >= r.h) continue
                var facet = if (y < plan.yR) "n" else "s"
                if (h.kind == "hip") {
                    val dw = (x - plan.x0) / max(1, plan.inset).toDouble()
                    val de = (plan.x1 - x) / max(1, plan.inset).toDouble()
                    val dn = (y - plan.yN) / max(1, plan.yR - plan.yN).toDouble()
                    val ds = (plan.yS - y) / max(1, plan.yS - plan.yR).toDouble()
                    val m = min(min(dw, de), min(dn, ds))
                    facet = when (m) {
                        dw -> "w"
                        de -> "e"
                        dn -> "n"
                        else -> "s"
                    }
                }
                var k = if (facet == "n") 1 else if (facet == "w") 0 else if (facet == "e") -1 else 0
                val t = roofTexel(h.style, x, y, plan.x0, if (facet == "n") plan.yN else plan.yR)
                if (t != null) k += tex[t] ?: 0
                if (facet == "s") {
                    when (slopeShade(y, plan.yR, plan.yS)) {
                        "eave" -> k -= 1
                        "ridge" -> k += 1
                    }
                }
                if (y == plan.yR && facet != "e") k = 2
                r.px[y * r.w + x] = palette.tone(h.roof, clampInt(k, -2, 2))
            }
        }
        if (h.chimney) {
            for (y in plan.yN + 1..plan.yN + (3 * d).roundToInt()) {
                for (x in plan.x1 - (4 * d).roundToInt()..plan.x1 - (3 * d).roundToInt()) {
                    if (x >= 0 && y >= 0 && x < r.w && y < r.h) {
                        r.px[y * r.w + x] = if (y == plan.yN + 1) palette.tone(palette.stone, 1) else palette.stone
                    }
                }
            }
        }
        for (y in by + 1..ay) {
            for (x in plan.x0 + 1..plan.x1 - 1) {
                if (x < 0 || y < 0 || x >= r.w || y >= r.h) continue
                val u = x - plan.x0 - 1
                val row = y - by - 1
                var c = if (row == 0) palette.tone(h.wall, -2) else if (u == 0 || u == w - 1) palette.tone(h.wall, -1) else h.wall
                val near = (1 * d).roundToInt()
                val pane = (u == near || u == near + 1 || u == w - (2 * d).roundToInt() || u == w - (3 * d).roundToInt()) &&
                    (u < h.door - near || u > h.door + (2 * d).roundToInt())
                if (u >= h.door && u <= h.door + max(1, (2 * d).roundToInt()) && row >= hw - (3 * d).roundToInt()) {
                    c = if (row == hw - (3 * d).roundToInt()) palette.tone(palette.door, -1) else palette.door
                } else if (hw >= (4 * d).roundToInt() && row in 1..min((2 * d).roundToInt(), hw - (3 * d).roundToInt()) && pane) {
                    c = marker(Marks.WIN, h.id)
                }
                r.px[y * r.w + x] = c
            }
        }
        outlinePass(r, palette.ink)
        val sp = Sprite(r.w, r.h, r.px, ax, ay)
        sp.roofTop = plan.yN - ay
        sp.chimneyX = plan.x1 - (3.5 * d).roundToInt() - ax
        sp.chimneyY = plan.yN + 1 - ay
        return sp
    }

    /**
     * Henesys-style mushroom house (inspired by MapleStory, drawn from scratch): a cream stem with a round door and a
     * window that lights after dark, under a domed cap with white spots, a lit crown and a shaded rim.
     */
    private fun buildMushroomHouse(h: House): Sprite {
        val d = detail
        val w = h.w
        val pad = (5 * d).roundToInt() + 2
        val stemH = h.hw + (3 * d).roundToInt()
        val capRx = w / 2.0 + 2.2 * d
        val capRy = h.depth * 0.42 + 3.2 * d
        val top = (2 * capRy).roundToInt() + 3
        val rw = w + 2 * pad
        val rh = top + stemH + (3 * d).roundToInt()
        val r = Surface(rw, rh)
        val ax = pad + w / 2
        val ay = top + stemH
        val by = ay - stemH // where the stem meets the cap
        // the stem: a little narrower than the house's footprint, lit on the left, shaded on the right
        val sx0 = pad + max(1, (1 * d).roundToInt())
        val sx1 = pad + w - 1 - max(1, (1 * d).roundToInt())
        val sw = sx1 - sx0 + 1
        val doorW = max(2, (2.4 * d).roundToInt())
        val doorX = (sx0 + clampInt(h.door, 1, max(1, sw - doorW - 1))).coerceAtMost(sx1 - doorW)
        val doorTop = ay - max(3, (3.6 * d).roundToInt())
        val winR = max(1.0, 1.1 * d)
        val winX = if (doorX - sx0 > sx1 - doorX - doorW) (sx0 + doorX) / 2.0 else (doorX + doorW + sx1) / 2.0
        val winY = by + 2.6 * d + 1
        for (y in by..ay) {
            for (x in sx0..sx1) {
                var c = when {
                    x == sx0 -> palette.tone(h.wall, 1)
                    x >= sx1 - max(0, (0.6 * d).roundToInt()) -> palette.tone(h.wall, -1)
                    else -> h.wall
                }
                if (y <= by + max(1, (1 * d).roundToInt())) c = palette.tone(h.wall, -2) // the cap's shadow
                val inDoor = x >= doorX && x < doorX + doorW && y >= doorTop
                val archCut = y == doorTop && (x == doorX || x == doorX + doorW - 1) && doorW > 2
                if (inDoor && !archCut) c = if (x == doorX) palette.tone(palette.door, -1) else palette.door
                val dx = x - winX
                val dy = y - winY
                if (!inDoor && dx * dx + dy * dy <= winR * winR + 0.3) c = marker(Marks.WIN, h.id)
                r.px[y * rw + x] = c
            }
        }
        // the cap: a dome whose underside sits on the stem's top
        val cx = ax - 0.5
        val cy = by + 1 - capRy
        val capC = h.roof
        ellipseRows(cx, cy, capRx, capRy) { y, xa, xb ->
            if (y < 0 || y >= rh) return@ellipseRows
            for (x in max(0, xa)..min(rw - 1, xb)) {
                val u = (x - cx) / capRx
                val v = (y - cy) / capRy
                val k = when {
                    v > 0.55 -> -1 // the rim, in its own shade
                    (u + 0.35) * (u + 0.35) + (v + 0.45) * (v + 0.45) < 0.12 -> 1 // the lit crown, upper left
                    u > 0.62 -> -1
                    else -> 0
                }
                r.px[y * rw + x] = palette.tone(capC, k)
            }
        }
        // the gills: a dark line where the rim turns under
        val gy = (cy + capRy).roundToInt()
        if (gy in 0 until rh) for (x in (cx - capRx * 0.82).roundToInt()..(cx + capRx * 0.82).roundToInt()) {
            if (x in 0 until rw && r.px[gy * rw + x] != 0) r.px[gy * rw + x] = palette.tone(capC, -2)
        }
        // white spots, the same for a house every time
        val spot = SeededRandom(h.id * 997 + seed)
        val spots = 3 + (w / (6 * d)).toInt().coerceAtMost(3)
        for (k in 0 until spots) {
            val a = (k + spot.range(0.0, 0.6)) / spots
            val sx = cx + (a - 0.5) * capRx * 1.35
            val sy = cy + spot.range(-0.55, 0.15) * capRy
            val sr = spot.range(0.8, 1.5) * d
            ellipseRows(sx, sy, sr, sr * 0.85) { y, xa, xb ->
                for (x in xa..xb) {
                    if (x in 0 until rw && y in 0 until rh && r.px[y * rw + x] != 0) r.px[y * rw + x] = palette.mushSpot
                }
            }
        }
        // a stubby chimney through the cap on some houses
        if (h.chimney) {
            val chX = (cx + capRx * 0.45).roundToInt()
            val chTop = (cy - capRy * 0.95).roundToInt()
            for (yy in chTop..chTop + (2.5 * d).roundToInt()) for (xx in chX..chX + max(1, d.roundToInt())) {
                if (xx in 0 until rw && yy in 0 until rh) r.px[yy * rw + xx] = if (yy == chTop) palette.tone(palette.stone, 1) else palette.stone
            }
        }
        outlinePass(r, palette.ink)
        val sp = Sprite(rw, rh, r.px, ax, ay)
        sp.roofTop = (cy - capRy).roundToInt() - ay
        sp.chimneyX = (cx + capRx * 0.45).roundToInt() - ax
        sp.chimneyY = (cy - capRy * 0.95).roundToInt() - ay
        return sp
    }

    private fun buildBoat(): Sprite {
        val grid = Array(8) { CharArray(9) { '.' } }
        layTemplate(grid, listOf("....m....", "...sm....", "..ssm....", ".sssm....", "sssssm..."), 0)
        layTemplate(grid, listOf(".hhhhhhh.", "..hhhhh.."), 5)
        val pal = mapOf('m' to palette.hex("#6b4a2a"), 's' to palette.sail, 'h' to palette.hull)
        return spriteFromRows(grid.map { String(it) }, { ch -> pal[ch] ?: 0 }, palette.ink)
    }

    /** A palm: a leaning trunk and fronds that sway from its crown, drawn into a surface at its own anchor. */
    fun drawPalm(layer: Surface, p: Palm, anchorX: Int, anchorY: Int, t: Double, k: Double, wind: Double) {
        val d = detail
        val r = layer
        val ax = anchorX
        val ay = anchorY
        val height = Math.round(13 * d * k).toInt()
        if (height < 2) return
        barkTrunk(
            r, ax.toDouble(), (ay - height).toDouble(), ay, max(1, d.roundToInt()),
            listOf(palette.hex("#7a5a3a"), palette.hex("#a8845a")), Math.round(p.lean * k).toInt(),
        )
        val cx = ax + Math.round(p.lean * k).toInt()
        val cy = ay - height
        for (f in 0 until 6) {
            val ang = -2.6 + f * 1.05
            val segs = 4
            var px = cx.toDouble()
            var py = cy.toDouble()
            var lastX = px
            var lastY = py
            for (j in 1..segs) {
                val sway = (0.25 + 0.2 * wind) * sin(t * 2.2 + f + p.id * 0.7 + j * 0.6) * j * 0.4 * d
                px = cx + cos(ang) * j * (7 * d / segs) + sway
                py = cy + sin(ang) * j * (7 * d / segs) * 0.55 + j * 0.6 * d
                lineCells(lastX, lastY, px, py) { x, y ->
                    if (x in 0 until r.w && y in 0 until r.h) {
                        r.px[y * r.w + x] = when {
                            j < 2 -> palette.leaves["meadow"]!![3]
                            j < 4 -> palette.leaves["meadow"]!![2]
                            else -> palette.leaves["meadow"]!![1]
                        }
                    }
                }
                lastX = px
                lastY = py
            }
        }
        r.px[(cy * r.w + cx).coerceIn(0, r.px.size - 1)] = palette.hex("#6b4a2a")
        outlinePass(r, palette.inkTree)
    }

    /** The size of the surface a palm is drawn on, and where its foot sits. */
    fun palmSurface(): Surface = Surface((30 * detail).roundToInt(), (30 * detail).roundToInt())

    fun palmAnchor(s: Surface): IntArray = intArrayOf(s.w / 2, s.h - (3 * detail).roundToInt())
}

/** A sprite drawn `k` times as tall and wide, for the small templates when the world is drawn larger. */
fun upscale(s: Sprite, k: Double): Sprite {
    val n = k.roundToInt()
    if (n <= 1) return s
    val w = s.w * n
    val h = s.h * n
    val px = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) px[y * w + x] = s.px[(y / n) * s.w + (x / n)]
    }
    return Sprite(w, h, px, s.ax * n, s.ay * n)
}
