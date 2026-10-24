// Noise: numbers that vary smoothly from place to place, the same at the same place for the same seed. Land, water and
// the wobble of anything alive are this noise at some scale. Ported from the almanac's noise module.
package dev.almanac.island

import kotlin.math.floor

private fun fade(t: Double): Double = t * t * (3 - 2 * t)

/** Value noise: a number in 0..1 that varies smoothly across the plane, the four nearest lattice points blended. */
fun valueNoise(x: Double, y: Double, seed: Int = 0): Double {
    val xi = floor(x)
    val yi = floor(y)
    val fx = x - xi
    val fy = y - yi
    val u = fade(fx)
    val v = fade(fy)
    val x0 = xi.toInt()
    val y0 = yi.toInt()
    val a = latticeRandom(x0, y0, seed)
    val b = latticeRandom(x0 + 1, y0, seed)
    val c = latticeRandom(x0, y0 + 1, seed)
    val d = latticeRandom(x0 + 1, y0 + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

/** Fractal noise (fractional Brownian motion): value noise at several scales added together, each finer one fainter. */
fun fractalNoise(x: Double, y: Double, seed: Int = 0, octaves: Int = 4, gain: Double = 0.5, lacunarity: Double = 2.0): Double {
    var s = 0.0
    var a = 1.0
    var t = 0.0
    var xx = x
    var yy = y
    for (o in 0 until octaves) {
        s += a * valueNoise(xx, yy, seed + o * 101)
        t += a
        a *= gain
        xx *= lacunarity
        yy *= lacunarity
    }
    return s / t
}

/** Ridged noise: fractal noise folded at its middle, its highs thin sharp lines (cracks, veins, ridges). */
fun ridgedNoise(x: Double, y: Double, seed: Int = 0, octaves: Int = 3): Double {
    var s = 0.0
    var a = 1.0
    var t = 0.0
    var xx = x
    var yy = y
    for (o in 0 until octaves) {
        s += a * (1 - kotlin.math.abs(valueNoise(xx, yy, seed + o * 57) * 2 - 1))
        t += a
        a *= 0.5
        xx *= 2.0
        yy *= 2.0
    }
    return s / t
}

/** Value noise along a line: a number in 0..1 that wanders smoothly as x moves. */
fun noiseLine(x: Double, seed: Int = 0): Double = valueNoise(x, seed * 0.731 + 7.3, seed)

/**
 * A square of value noise that tiles: its right edge meets its left and its bottom its top, so it can be scrolled for
 * ever. A grid of cell values, read round, blended as value noise blends.
 */
fun noiseTile(size: Int, cells: Int, lattice: (Int, Int) -> Double): FloatArray {
    val out = FloatArray(size * size)
    val f = size.toDouble() / cells
    for (y in 0 until size) {
        val gy = y / f
        val yi = floor(gy)
        val v = fade(gy - yi)
        val y0 = yi.toInt()
        for (x in 0 until size) {
            val gx = x / f
            val xi = floor(gx)
            val u = fade(gx - xi)
            val x0 = xi.toInt()
            val a = lattice(positiveMod(x0, cells), positiveMod(y0, cells))
            val b = lattice(positiveMod(x0 + 1, cells), positiveMod(y0, cells))
            val c = lattice(positiveMod(x0, cells), positiveMod(y0 + 1, cells))
            val d = lattice(positiveMod(x0 + 1, cells), positiveMod(y0 + 1, cells))
            out[y * size + x] = (a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v).toFloat()
        }
    }
    return out
}
