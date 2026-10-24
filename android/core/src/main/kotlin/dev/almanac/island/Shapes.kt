// Ways across a grid and over a land: the chamfer distance, terraces, a river that winds, plots laid at an angle, the
// shortest path through free cells, a line of cells, a flood fill, and where roads scatter things. Ported from the
// almanac's land, paths and scatter modules.
package dev.almanac.island

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The height of an island at a tile, 0..1: fractal noise, lowered toward the edge and raised toward the middle. The
 * shape is stretched along one axis (the almanac's island is long along x), so a tall screen still gets a round island.
 */
fun islandHeight(
    x: Double,
    y: Double,
    sizeX: Double,
    sizeY: Double,
    seed: Int,
    stretchX: Double = 0.7,
    stretchY: Double = 1.6,
    rough: Double = 0.6,
    featureSize: Double = sizeX,
): Double {
    val dx = (x + 0.5) / sizeX - 0.5
    val dy = (y + 0.5) / sizeY - 0.5
    val shape = hypot(dx * stretchX, dy * stretchY) * 2
    var v = fractalNoise(x / (featureSize * 0.28), y / (featureSize * 0.28), seed % 9973, 4, rough) * 1.25 - 0.1
    v = clamp(v * (1 - clamp(shape, 0.0, 1.2).pow(2.2)) + 0.25 * (1 - shape))
    return v
}

/** How many terraces stand at a point: its height above the cut in steps, from none to the most there are. */
fun terraceLevel(above: Double, step: Double, levels: Int): Int =
    max(0.0, min((levels - 1).toDouble(), floor(above / step))).toInt()

/** A river that winds on its own: it runs where a slow fractal noise crosses its middle value. */
class River(val depth: Double, val band: Double, val width: Double)

fun windingRiver(x: Double, y: Double, seed: Int, level: Double): River {
    val v = fractalNoise(x / 300, y / 300, seed, 3)
    val band = abs(v - 0.5) * 2
    val width = level * 0.2
    return River((width - band) / width, band, width)
}

/** The next heading of a path that wanders by itself: it turns toward a heading that drifts with noise. */
fun wanderingHeading(a: Double, s: Double, seed: Int, bias: Double = 0.0, turn: Double = 0.06): Double {
    val want = bias +
        1.15 * (fractalNoise(s / 560, 3.1, seed + 5, 3) - 0.5) * 2 +
        0.3 * (valueNoise(s / 120, 9.7, seed + 6) - 0.5) * 2
    return a + clamp(angleDiff(a, want), -turn, turn)
}

/** Plots laid at an angle (paddy fields, salt pans): which plot a point is in, and how far from its nearest edge. */
class Plot(val iu: Int, val iv: Int, val edge: Double)

fun plotGrid(x: Double, y: Double, size: Double, angle: Double): Plot {
    val u = x * cos(angle) + y * sin(angle)
    val v = -x * sin(angle) + y * cos(angle)
    val iu = floor(u / size)
    val iv = floor(v / size)
    val fu = u - iu * size
    val fv = v - iv * size
    return Plot(iu.toInt(), iv.toInt(), min(min(fu, fv), min(size - fu, size - fv)))
}

/**
 * The chamfer distance transform: for every cell marked nonzero, how far it is from the nearest zero cell, a step
 * across costing 3 and diagonally 4. Two passes over the grid, down and right then up and left.
 */
fun chamferDistance(mask: BooleanArray, w: Int, h: Int): ShortArray {
    // 60000 would not fit a Short: the almanac's Uint16Array does, so 32767 stands for "no zero cell reached"
    val inf: Short = Short.MAX_VALUE
    val d = ShortArray(w * h)
    for (i in d.indices) d[i] = if (mask[i]) inf else 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            var v = d[i].toInt()
            if (v == 0) continue
            if (x > 0) v = min(v, d[i - 1] + 3)
            if (y > 0) {
                v = min(v, d[i - w] + 3)
                if (x > 0) v = min(v, d[i - w - 1] + 4)
                if (x < w - 1) v = min(v, d[i - w + 1] + 4)
            }
            d[i] = v.toShort()
        }
    }
    for (y in h - 1 downTo 0) {
        for (x in w - 1 downTo 0) {
            val i = y * w + x
            var v = d[i].toInt()
            if (v == 0) continue
            if (x < w - 1) v = min(v, d[i + 1] + 3)
            if (y < h - 1) {
                v = min(v, d[i + w] + 3)
                if (x < w - 1) v = min(v, d[i + w + 1] + 4)
                if (x > 0) v = min(v, d[i + w - 1] + 4)
            }
            d[i] = v.toShort()
        }
    }
    return d
}

/** The cells on the line from one point to another (Bresenham), each plotted once, in order. */
fun lineCells(xa0: Double, ya0: Double, xb0: Double, yb0: Double, plot: (Int, Int) -> Unit) {
    var xa = Math.round(xa0).toInt()
    var ya = Math.round(ya0).toInt()
    val xb = Math.round(xb0).toInt()
    val yb = Math.round(yb0).toInt()
    val dx = abs(xb - xa)
    val dy = -abs(yb - ya)
    val sx = if (xa < xb) 1 else -1
    val sy = if (ya < yb) 1 else -1
    var e = dx + dy
    for (n in 0 until 4096) {
        plot(xa, ya)
        if (xa == xb && ya == yb) break
        val e2 = 2 * e
        if (e2 >= dy) {
            e += dy
            xa += sx
        }
        if (e2 <= dx) {
            e += dx
            ya += sy
        }
    }
}

/** Everything reachable from a start, by a step function: the flood fill a patch of land is found with. */
fun floodFill(start: Int, next: (Int) -> IntArray): HashSet<Int> {
    val seen = HashSet<Int>()
    val todo = ArrayDeque<Int>()
    seen.add(start)
    todo.addLast(start)
    while (todo.isNotEmpty()) {
        val t = todo.removeLast()
        for (n in next(t)) {
            if (seen.add(n)) todo.addLast(n)
        }
    }
    return seen
}

/** The nearest cell that is free, in rings round the one asked for. */
fun nearestFreeCell(cx: Int, cy: Int, free: (Int, Int) -> Boolean, radius: Int = 14): IntArray? {
    if (free(cx, cy)) return intArrayOf(cx, cy)
    for (d in 1..radius) {
        for (i in -d..d) {
            if (free(cx + i, cy - d)) return intArrayOf(cx + i, cy - d)
            if (free(cx + i, cy + d)) return intArrayOf(cx + i, cy + d)
            if (free(cx - d, cy + i)) return intArrayOf(cx - d, cy + i)
            if (free(cx + d, cy + i)) return intArrayOf(cx + d, cy + i)
        }
    }
    return null
}

/** A heap of cells, each with the cost it is expected to come to: the smallest comes out first. */
private class CellHeap {
    private var p = DoubleArray(64)
    private var xs = IntArray(64)
    private var ys = IntArray(64)
    var size = 0
        private set

    val isEmpty: Boolean get() = size == 0
    val peekX: Int get() = xs[0]
    val peekY: Int get() = ys[0]

    private fun grow() {
        if (size < p.size) return
        p = p.copyOf(p.size * 2)
        xs = xs.copyOf(xs.size * 2)
        ys = ys.copyOf(ys.size * 2)
    }

    private fun swap(i: Int, j: Int) {
        val tp = p[i]; p[i] = p[j]; p[j] = tp
        val tx = xs[i]; xs[i] = xs[j]; xs[j] = tx
        val ty = ys[i]; ys[i] = ys[j]; ys[j] = ty
    }

    fun push(priority: Double, x: Int, y: Int) {
        grow()
        var i = size
        p[i] = priority
        xs[i] = x
        ys[i] = y
        size++
        while (i > 0) {
            val parent = (i - 1) shr 1
            if (p[parent] <= p[i]) break
            swap(i, parent)
            i = parent
        }
    }

    fun pop() {
        size--
        if (size == 0) return
        p[0] = p[size]
        xs[0] = xs[size]
        ys[0] = ys[size]
        var i = 0
        while (true) {
            val l = 2 * i + 1
            val r = l + 1
            var m = i
            if (l < size && p[l] < p[m]) m = l
            if (r < size && p[r] < p[m]) m = r
            if (m == i) break
            swap(m, i)
            i = m
        }
    }
}

/**
 * The shortest path across a grid of cells, A* with a heap: eight ways to a cell, diagonals only where both sides are
 * free. Returns the cells from a to b, or null when there is no way (or the budget runs out).
 */
fun gridAStar(a: IntArray, b: IntArray, budget: Int = 5000, free: (Int, Int) -> Boolean): List<IntArray>? {
    // cells may be negative: offset to stay whole and apart; packed into the two halves of an Int, so unpack with
    // ushr/and, never with arithmetic on the (overflowed) whole
    fun key(x: Int, y: Int) = ((x + 32768) shl 16) or ((y + 32768) and 0xFFFF)
    fun h(x: Int, y: Int): Double {
        val dx = abs(x - b[0]).toDouble()
        val dy = abs(y - b[1]).toDouble()
        return max(dx, dy) + 0.414 * min(dx, dy)
    }
    val goal = key(b[0], b[1])
    val g = HashMap<Int, Double>()
    val came = HashMap<Int, Int>()
    val heap = CellHeap()
    val startKey = key(a[0], a[1])
    g[startKey] = 0.0
    heap.push(h(a[0], a[1]), a[0], a[1])
    var seen = 0
    while (!heap.isEmpty && seen++ < budget) {
        val x = heap.peekX
        val y = heap.peekY
        heap.pop()
        val k = key(x, y)
        val gk = g[k] ?: continue
        if (k == goal) {
            val out = ArrayList<IntArray>()
            out.add(intArrayOf(x, y))
            var c = came[k]
            while (c != null) {
                out.add(intArrayOf((c ushr 16) - 32768, (c and 0xFFFF) - 32768))
                c = came[c]
            }
            out.reverse()
            return out
        }
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (!free(nx, ny) || (dx != 0 && dy != 0 && (!free(x + dx, y) || !free(x, y + dy)))) continue
                val nk = key(nx, ny)
                val ng = gk + (if (dx != 0 && dy != 0) 1.414 else 1.0)
                if (ng < (g[nk] ?: Double.MAX_VALUE)) {
                    g[nk] = ng
                    came[nk] = k
                    heap.push(ng + h(nx, ny), nx, ny)
                }
            }
        }
    }
    return null
}

/** Where something is scattered along a line, every so often, nudged by up to 40% of a slot either way. */
class Scatter(val i: Int, val x: Double, val variant: Int, val size: Double)

fun scatterAlong(from: Double, to: Double, every: Double, seed: Int, chance: Double = 0.7, variants: Int = 4): List<Scatter> {
    val out = ArrayList<Scatter>()
    val i0 = floor(from / every).toInt()
    val i1 = ceil(to / every).toInt()
    for (i in i0..i1) {
        if (indexRandom(i, seed + 31) > chance) continue
        out.add(
            Scatter(
                i = i,
                x = (i + (indexRandom(i, seed + 37) - 0.5) * 0.8) * every,
                variant = (indexRandom(i, seed + 41) * variants).toInt(),
                size = lerp(0.85, 1.15, indexRandom(i, seed + 43)),
            ),
        )
    }
    return out
}

/** The angle from one point to another. */
fun headingTo(x0: Double, y0: Double, x1: Double, y1: Double): Double = atan2(y1 - y0, x1 - x0)

/** How far apart two points are. */
fun dist(x0: Double, y0: Double, x1: Double, y1: Double): Double = hypot(x1 - x0, y1 - y0)
