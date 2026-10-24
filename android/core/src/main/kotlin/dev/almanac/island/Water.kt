// Water seen from above, drawn a pixel at a time: a surface you can see into (weed on the bed, sand in the shallows by
// a beach), broad swells and fine ripples drifting across it, glints that come and go on the crests, foam that laps a
// beach, and shade at the foot of a bank. Everything that moves is a few tiles of wrapping noise read at offsets that
// grow with time. Ported from the almanac's water module.
package dev.almanac.island

import kotlin.math.sin

/** A water palette: seven depths, foam, glints, weed and the three sands of a beach. */
class WaterPalette(
    val LV: IntArray,
    val FOAM: Int,
    val FOAM2: Int,
    val GLINT: Int,
    val GLINT2: Int,
    val WEED: Int,
    val WEED2: Int,
    val SAND: IntArray,
)

/** The water's clock at a moment: the offsets its swells, ripples and glints have drifted to, shared by every pixel. */
class WaterClock(val t: Double, val tq: Int) {
    val sw: Int = (t * 6).toInt()
    val sv: Int = (t * 2).toInt()
    val r1: Int = (t * 15).toInt()
    val r2: Int = (t * 10).toInt()
    val r2v: Int = (t * 4).toInt()
    val glz: Int = (t * 3).toInt()
}

/** The water's clock at a moment, at the frame rate the foam flickers at (15 a second). */
fun waterTime(t: Double): WaterClock = WaterClock(t, (t * 15).toInt())

private const val TILE = 128

private fun tile(cells: Int, seed: Int): FloatArray {
    val rnd = LinearCongruential(seed)
    val g = DoubleArray(cells * cells) { rnd.next() }
    return noiseTile(TILE, cells) { x, y -> g[y * cells + x] }
}

private val SWELL: FloatArray by lazy { tile(8, 11) }
private val RIP1: FloatArray by lazy { tile(32, 23) }
private val RIP2: FloatArray by lazy { tile(26, 37) }
private val BED: FloatArray by lazy { tile(10, 51) }

/**
 * One pixel of water: foam lapping a beach or a line along a wall; deeper water darker; swells and ripples lightening
 * and darkening it a step; glints on the crests; sand under the shallows; weed on the bed.
 */
fun waterPixel(wx: Int, wy: Int, d: Double, near: Boolean, sh: Int, T: WaterClock, wob: Int, P: WaterPalette): Int {
    val t = T.t
    val tq = T.tq
    if (near) {
        val fw = 1.4 + 1.6 * (1 + sin(t * 1.5 + wx * 0.11 + wy * 0.05))
        if (d < fw || (d < fw + 1 && ((wx + wy + tq) and 1) != 0)) return if (d < fw - 1) P.FOAM else P.FOAM2
    } else if (d <= 1) return if ((wx + tq) % 4 != 0) P.FOAM2 else P.FOAM
    var lv = if (d > 48) 4 else if (d > 16) 3 else 2
    val s1 = SWELL[((wy + T.sv) and 127) * TILE + ((wx + T.sw) and 127)]
    if (s1 < 0.33) lv++ else if (s1 > 0.7) lv--
    val r =
        (
            RIP1[((wy * 3) and 127) * TILE + ((wx + T.r1) and 127)] +
                RIP2[((wy * 3 + T.r2v) and 127) * TILE + ((wx - T.r2 + 64) and 127)]
            ) * 0.5
    if (r > 0.7) lv -= 2 else if (r > 0.62) lv-- else if (r < 0.33) lv++
    if (sh != 0) lv += sh
    if (r > 0.6 && d > 2) {
        val gx = (wx + T.r1) shr 2
        val beat = T.glz + (latticeHash3(gx, wy, 3) * 9).toInt()
        val g = latticeHash3(gx, wy, beat)
        if (g < 0.02) return if (g < 0.01) P.GLINT else P.GLINT2
    }
    if (near && d < 12) return if (r > 0.66) P.LV[1] else P.SAND[if (d < 5) 0 else if (d < 8) 1 else 2]
    if (d > 3) {
        val b = BED[((wy shr 1) and 127) * TILE + (((wx + wob) shr 1) and 127)]
        if (b > 0.69 || (b > 0.65 && ((wx + wy) and 1) != 0)) {
            return if (b > 0.73 && ((wx xor wy) and 2) != 0) P.WEED2 else P.WEED
        }
    }
    return P.LV[if (lv < 0) 0 else if (lv > 6) 6 else lv]
}
