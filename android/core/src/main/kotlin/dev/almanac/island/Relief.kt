// Roofs seen from above in pixel art: where a gable or hip roof's ridge, slopes and eaves fall, how a slope shades
// from ridge to eave, and the texture of its covering (tiles in staggered rows, thatch, boards with nails), each a
// pixel's shade given as a word the caller colours. Ported from the almanac's roofs module.
package dev.almanac.island

import kotlin.math.roundToInt

class RoofPlan(
    val x0: Int,
    val x1: Int,
    val yN: Int,
    val yS: Int,
    val yR: Int,
    val inset: Int,
    val w: Int,
    val depth: Int,
)

/** A roof seen from above, its south eave's middle at (cx, by): eaves, ridge and (for a hip) how far its ends slope in. */
fun roofPlan(kind: String, w: Int, d: Int, eave: Int, cx: Int, by: Int): RoofPlan {
    val rw = w + eave * 2
    val rd = d + eave * 2
    val x0 = (cx - rw / 2.0).roundToInt()
    val x1 = x0 + rw - 1
    val yS = by
    val yN = by - rd + 1
    val yR = yN + (rd * 0.3).roundToInt()
    val inset = if (kind == "hip") (rd * 0.42).roundToInt() else 0
    return RoofPlan(x0, x1, yN, yS, yR, inset, rw, rd)
}

/** How the south slope shades at a row: dark along its eave, lit just under the ridge. */
fun slopeShade(y: Int, yR: Int, yS: Int): String? {
    val u = (y - yR) / Math.max(1, yS - yR).toDouble()
    return when {
        u > 0.8 -> "eave"
        u < 0.16 -> "ridge"
        else -> null
    }
}

/** A roof covering's texture at a pixel, its rows parallel to the eave: tiles, thatch, boards, lacquered boards. */
fun roofTexel(style: String, x: Int, y: Int, x0: Int, y0: Int): String? {
    if (style == "tile" || style == "redtile") {
        val mortar = style == "redtile"
        val r = (y - y0) % 4
        val row = (y - y0) / 4
        val j = (x - x0 + (row % 2) * 3) % 6
        if (r == 3) return if (mortar) "mortar" else "seam"
        if (j == 0) return if (mortar) "mortar" else "seam"
        if (j == 1 && r < 2) return "lit"
        if (r == 2 && !mortar) return "shaded"
        return null
    }
    if (style == "thatch") {
        val r = (y - y0) % 5
        val n = (x * 7 + y * 13 + ((x * y) % 7)) % 11
        if (r == 4 && (x * 3 + y) % 4 != 0) return "edge"
        if (r == 0 && n < 4) return "lit"
        if (n == 5 || n == 9) return "deep"
        return if (n < 2) "glow" else null
    }
    if (style == "plank") {
        val k = (x - x0) % 6
        if (k == 5) return "edge"
        if (k == 0) return "lit"
        if (k == 2 && (y - y0) % 9 == 4) return "deep"
        return null
    }
    if (style == "vermilion") return if ((x - x0) % 4 == 3) "edge" else if ((y - y0) % 7 == 6) "dim" else null
    return null
}
