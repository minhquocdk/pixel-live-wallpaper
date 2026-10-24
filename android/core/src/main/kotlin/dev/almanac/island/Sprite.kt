// Sprites and the ways they are drawn: rows of characters into a sprite with an ink outline, trunks and crowns of
// leaves, filled and shaded ellipses, a sprite stamped sprite by sprite. Ported from the almanac's raster, sprites and
// relief modules, with the 32-bit colour words kept as Android needs them (0xAARRGGBB).
package dev.almanac.island

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A buffer of pixels to draw on: the almanac's `{w, h, px}`. */
class Surface(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else px[y * w + x]
}

/** A drawn thing with its anchor: the pixel of the buffer that sits where it stands. */
class Sprite(
    val w: Int,
    val h: Int,
    val px: IntArray,
    val ax: Int,
    val ay: Int,
) {
    var trunk: Int = 0
    var roofTop: Int = 0
    var chimneyX: Int = 0
    var chimneyY: Int = 0
    var tips: List<IntArray>? = null
}

fun fillRow(r: Surface, y: Int, xa0: Int, xb0: Int, c: Int) {
    if (y < 0 || y >= r.h) return
    var xa = xa0
    var xb = xb0
    if (xa < 0) xa = 0
    if (xb >= r.w) xb = r.w - 1
    if (xb >= xa) for (i in y * r.w + xa..y * r.w + xb) r.px[i] = c
}

/** The rows a filled ellipse covers, each given as a run of whole pixels. */
fun ellipseRows(cx: Double, cy: Double, rx: Double, ry: Double, row: (Int, Int, Int) -> Unit) {
    if (rx < 0.5 || ry < 0.5) {
        val x = cx.roundToInt()
        row(cy.roundToInt(), x, x)
        return
    }
    val ya = ceil(cy - ry).toInt()
    val yb = floor(cy + ry).toInt()
    for (y in ya..yb) {
        val t = (y - cy) / ry
        val dx = rx * sqrt(max(0.0, 1 - t * t))
        row(y, ceil(cx - dx).toInt(), floor(cx + dx).toInt())
    }
}

fun fillEllipse(r: Surface, cx: Double, cy: Double, rx: Double, ry: Double, c: Int) {
    ellipseRows(cx, cy, rx, ry) { y, xa, xb -> fillRow(r, y, xa, xb, c) }
}

/** An ellipse laid on the ground darkened: a shadow, a little bluer than the land it falls on. */
fun shadeEllipse(r: Surface, cx: Double, cy: Double, rx: Double, ry: Double, k: Double = 0.72) {
    val ya = max(0, ceil(cy - ry).toInt())
    val yb = min(r.h - 1, floor(cy + ry).toInt())
    for (y in ya..yb) {
        val t = (y - cy) / ry
        val dx = rx * sqrt(max(0.0, 1 - t * t))
        val xa = max(0, ceil(cx - dx).toInt())
        val xb = min(r.w - 1, floor(cx + dx).toInt())
        for (x in xa..xb) {
            val i = y * r.w + x
            val c = r.px[i]
            r.px[i] = rgb(
                (redOf(c) * (k - 0.02)),
                (greenOf(c) * k),
                min(255.0, blueOf(c) * (k + 0.06)),
            )
        }
    }
}

/** An ellipse of one colour among another, the share of it set against the Bayer pattern. */
fun ditherEllipse(r: Surface, cx: Double, cy: Double, rx: Double, ry: Double, c: Int, alpha: Double) {
    val ya = max(0, ceil(cy - ry).toInt())
    val yb = min(r.h - 1, floor(cy + ry).toInt())
    val th = alpha * 16
    for (y in ya..yb) {
        val t = (y - cy) / ry
        val dx = rx * sqrt(max(0.0, 1 - t * t))
        val xa = max(0, ceil(cx - dx).toInt())
        val xb = min(r.w - 1, floor(cx + dx).toInt())
        for (x in xa..xb) if (BAYER[((y and 3) shl 2) or (x and 3)] < th) r.px[y * r.w + x] = c
    }
}

/** A line of discs from one end to the other, its radius changing as it goes: a stroke with round caps. */
fun capsuleSteps(xa: Double, ya: Double, ra: Double, xb: Double, yb: Double, rb: Double, disc: (Double, Double, Double) -> Unit) {
    val d = hypot(xb - xa, yb - ya)
    val n = max(1.0, ceil(d * 1.5))
    for (i in 0..n.toInt()) {
        val t = i / n
        disc(xa + (xb - xa) * t, ya + (yb - ya) * t, ra + (rb - ra) * t)
    }
}

/** The ink round every drawn pixel of a surface, one pixel wide, four neighbours deep. */
fun outlinePass(r: Surface, c: Int, xa: Int = 0, ya: Int = 0, xb: Int = r.w - 1, yb: Int = r.h - 1) {
    val w = r.w
    val p = r.px
    val hit = ArrayList<Int>()
    val x0 = max(1, xa - 1)
    val y0 = max(1, ya - 1)
    val x1 = min(w - 2, xb + 1)
    val y1 = min(r.h - 2, yb + 1)
    for (y in y0..y1) {
        for (x in x0..x1) {
            val i = y * w + x
            if (p[i] == 0 && (p[i - 1] != 0 || p[i + 1] != 0 || p[i - w] != 0 || p[i + w] != 0)) hit.add(i)
        }
    }
    for (i in hit) p[i] = c
}

/** The ink round a sprite's drawn pixels: a copy of the pixels, edged. */
fun outlineSprite(px: IntArray, w: Int, h: Int, ink: Int): IntArray {
    val out = px.copyOf()
    for (y in 0 until h) {
        for (x in 0 until w) {
            if (px[y * w + x] != 0) continue
            if (
                (x > 0 && px[y * w + x - 1] != 0) ||
                (x < w - 1 && px[y * w + x + 1] != 0) ||
                (y > 0 && px[(y - 1) * w + x] != 0) ||
                (y < h - 1 && px[(y + 1) * w + x] != 0)
            ) out[y * w + x] = ink
        }
    }
    return out
}

/**
 * A sprite from rows of characters: each character's colour from colourOf, '.' and ' ' left clear, an ink outline round
 * it, and its anchor at the middle of its foot.
 */
fun spriteFromRows(
    rows: List<String>,
    colourOf: (Char) -> Int,
    ink: Int,
    outline: Boolean = true,
    ax: Int? = null,
    ay: Int? = null,
): Sprite {
    val iw = rows.maxOf { it.length }
    val ih = rows.size
    val w = iw + 2
    val h = ih + 2
    var px = IntArray(w * h)
    rows.forEachIndexed { y, row ->
        for (x in row.indices) {
            val ch = row[x]
            if (ch == '.' || ch == ' ') continue
            px[(y + 1) * w + x + 1] = colourOf(ch)
        }
    }
    if (outline) px = outlineSprite(px, w, h, ink)
    return Sprite(w, h, px, ax?.plus(1) ?: (w shr 1), ay?.plus(1) ?: (h - 2))
}

/** A template of characters laid over a grid of characters, at a row. */
fun layTemplate(grid: Array<CharArray>, rows: List<String>, top: Int) {
    val w = grid[0].size
    val h = grid.size
    rows.forEachIndexed { y, r ->
        for (x in 0 until w) {
            val c = if (x < r.length) r[x] else '.'
            if (c != '.' && top + y >= 0 && top + y < h) grid[top + y][x] = c
        }
    }
}

/** A buffer of pixels to draw a sprite on. */
fun buffer(w: Int, h: Int): Surface = Surface(w, h)

/** How far a drawn thing reaches from its corner: [left, top, right, bottom], or null when nothing is drawn. */
fun drawnBounds(r: Surface): IntArray? {
    var xa = r.w
    var ya = r.h
    var xb = -1
    var yb = -1
    for (y in 0 until r.h) {
        for (x in 0 until r.w) {
            if (r.px[y * r.w + x] != 0) {
                if (x < xa) xa = x
                if (x > xb) xb = x
                if (y < ya) ya = y
                if (y > yb) yb = y
            }
        }
    }
    return if (xb < 0) null else intArrayOf(xa, ya, xb, yb)
}

/** A part of a surface cut out, its anchor moved with it. */
fun cutSprite(r: Surface, xa0: Int, ya0: Int, xb0: Int, yb0: Int, ax: Int, ay: Int): Sprite {
    val xa = max(0, xa0)
    val ya = max(0, ya0)
    val xb = min(r.w - 1, xb0)
    val yb = min(r.h - 1, yb0)
    val w = xb - xa + 1
    val h = yb - ya + 1
    val px = IntArray(w * h)
    for (y in 0 until h) r.px.copyInto(px, y * w, (ya + y) * r.w + xa, (ya + y) * r.w + xa + w)
    return Sprite(w, h, px, ax - xa, ay - ya)
}

/** A sprite trimmed to what it draws, its anchor moved with it. */
fun trim(r: Surface, ax: Int, ay: Int): Sprite? {
    val b = drawnBounds(r) ?: return null
    return cutSprite(r, b[0], b[1], b[2], b[3], ax, ay)
}

/** A sprite drawn onto another surface at a point, drawn pixels only, whole (a dead pixel stays clear). */
fun blitSprite(r: Surface, s: Sprite, x: Double, y: Double, flip: Boolean = false) {
    val ox = x.roundToInt() - (if (flip) s.w - 1 - s.ax else s.ax)
    val oy = y.roundToInt() - s.ay
    val w = r.w
    val h = r.h
    val ya = max(0, -oy)
    val yb = min(s.h, h - oy)
    val xa = max(0, -ox)
    val xb = min(s.w, w - ox)
    for (sy in ya until yb) {
        val row = sy * s.w
        val drow = (oy + sy) * w + ox
        if (flip) {
            for (sx in xa until xb) {
                val c = s.px[row + s.w - 1 - sx]
                if (c != 0) r.px[drow + sx] = c
            }
        } else {
            for (sx in xa until xb) {
                val c = s.px[row + sx]
                if (c != 0) r.px[drow + sx] = c
            }
        }
    }
}

/** A sprite drawn swaying: each row shifted by the sway at its height (a tree in the wind). */
fun blitSway(r: Surface, s: Sprite, x: Double, y: Double, sway: (Double) -> Double) {
    val ox = x.roundToInt() - s.ax
    val oy = y.roundToInt() - s.ay
    val w = r.w
    val h = r.h
    for (sy in max(0, -oy) until min(s.h, h - oy)) {
        val dx = ox + sway((s.ay - sy).toDouble()).roundToInt()
        val row = sy * s.w
        val drow = (oy + sy) * w
        for (sx in max(0, -dx) until min(s.w, w - dx)) {
            val c = s.px[row + sx]
            if (c != 0) r.px[drow + dx + sx] = c
        }
    }
}

/** A trunk: two tones, the left edge lit, widening at its foot. */
fun barkTrunk(r: Surface, x: Double, yTop: Double, yBase: Int, w: Int, bark: List<Int>, lean: Int = 0) {
    for (y in yTop.roundToInt()..yBase) {
        val u = (y - yTop) / max(1.0, yBase - yTop)
        val xx = (x + lean * (1 - u)).roundToInt()
        val ww = w + (if (u > 0.8) 1 else 0)
        for (k in 0 until ww) {
            val px = xx - ww / 2 + k
            val py = y
            if (px >= 0 && py >= 0 && px < r.w && py < r.h) {
                r.px[py * r.w + px] = if (k == 0 && ww > 1) bark[1] else bark[0]
            }
        }
    }
}

/**
 * A tree's crown: clumps of ellipses spread through a ball, each pixel coloured by how near the middle of the whole
 * crown it is and how near its own clump's lit side, four shades of leaf from dark to light, and blossom on it.
 */
fun leafCrown(
    r: Surface,
    rng: SeededRandom,
    cx: Double,
    cy: Double,
    rx: Double,
    ry: Double,
    leaf: List<Int>,
    clumpsWanted: Int = 0,
    spread: Double = 0.62,
    bloom: Int = 0,
    bloomEvery: Int = 9,
    bloomSeed: Int = 0,
): List<DoubleArray> {
    val w = r.w
    val h = r.h
    val clumps = ArrayList<DoubleArray>()
    val n = if (clumpsWanted > 0) clumpsWanted else max(4, Math.round((rx * ry) / 7).toInt())
    for (k in 0 until n) {
        val a = rng.range(0.0, Math.PI * 2)
        val d = sqrt(rng.next()) * spread
        val cr = rng.range(0.34, 0.52) * min(rx, ry) + 0.8
        clumps.add(doubleArrayOf(cx + Math.cos(a) * d * rx, cy + Math.sin(a) * d * ry, cr))
    }
    clumps.add(doubleArrayOf(cx, cy, min(rx, ry) * 0.6))
    val mark = 0xFF000001.toInt()
    val x0 = floor(cx - rx - 4).toInt()
    val x1 = ceil(cx + rx + 4).toInt()
    val y0 = floor(cy - ry - 4).toInt()
    val y1 = ceil(cy + ry + 4).toInt()
    for (c in clumps) fillEllipse(r, c[0], c[1], c[2], c[2], mark)
    val inside = ArrayList<Int>()
    for (y in max(0, y0)..min(h - 1, y1)) {
        for (x in max(0, x0)..min(w - 1, x1)) if (r.px[y * w + x] == mark) inside.add(y * w + x)
    }
    for (i in inside) {
        val x = i % w
        val y = i / w
        var best = 1e9
        var bj = 0
        for (j in clumps.indices) {
            val c = clumps[j]
            val d = hypot(x - c[0], y - c[1]) / c[2]
            if (d < best) {
                best = d
                bj = j
            }
        }
        val c = clumps[bj]
        val whole = (-(x - cx) / rx) * 0.55 - ((y - cy) / ry) * 0.85
        val local = 1 - hypot(x - (c[0] - c[2] * 0.32), y - (c[1] - c[2] * 0.42)) / (c[2] * 1.05)
        val t = whole * 0.55 + (local - 0.42) * 1.25 + (if ((x * 7 + y * 13) % 5 == 0) 0.1 else 0.0)
        r.px[i] = if (t > 0.72) leaf[3] else if (t > 0.18) leaf[2] else if (t > -0.42) leaf[1] else leaf[0]
    }
    if (bloom != 0) {
        for (i in inside) {
            val x = i % w
            val y = i / w
            if ((x * 31 + y * 17 + bloomSeed) % bloomEvery == 0 && r.px[i] != leaf[0]) r.px[i] = bloom
        }
    }
    return clumps
}
