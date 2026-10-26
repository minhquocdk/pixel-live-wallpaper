// The plan of the Rift's tall map: every place written in plan coordinates — p across, q down, both 0..1 — so the same
// map can be projected into a phone held upright or (later) a tablet held sideways by changing one projection.
//
// The map is 180-degree symmetric about its centre: one half is written here, the other is that half turned round. The
// mid lane is the straight spine down the middle, the two outer lanes bow out to the left and right edges, a river
// crosses at the middle with one bridge, and the two great pits sit on opposite banks, as on the map this is drawn
// after. Nothing here draws: it is all places and lengths, so the tests can hold them to the plan.
package dev.almanac.island

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** One thing that stands on the map: a tower, a forest, a pit. `team` 0 is blue, 1 is red; `variant` picks the skin. */
class RiftPiece(val kind: String, val p: Double, val q: Double, val team: Int, val variant: Int = 0)

object RiftPlan {
    /** The plateau runs nearly to the left and right edges, and keeps clear of the clock and the dock. */
    const val INSET_X = 0.028
    const val INSET_TOP = 0.120
    const val INSET_BOTTOM = 0.080

    const val RIVER_HALF = 0.024
    const val RIVER_FROM = 0.100
    const val RIVER_TO = 0.900
    const val BRIDGE_FROM = 0.462
    const val BRIDGE_TO = 0.538

    const val LANE_WIDE = 0.055
    const val LANE_NARROW = 0.042

    /** A plan coordinate, across, as a share of the picture's width. */
    fun across(p: Double): Double = INSET_X + p * (1 - 2 * INSET_X)

    /** A plan coordinate, down, as a share of the picture's height. */
    fun down(q: Double): Double = INSET_TOP + q * (1 - INSET_TOP - INSET_BOTTOM)

    /** The river's centre line: a gentle S across the middle of the map. */
    fun riverQ(p: Double): Double = 0.5 + 0.013 * sin(2 * PI * 1.2 * (p - 0.12) + 0.7)

    fun inRiver(p: Double, q: Double): Boolean =
        p >= RIVER_FROM && p <= RIVER_TO && abs(q - riverQ(p)) <= RIVER_HALF

    fun isOnBridge(p: Double): Boolean = p >= BRIDGE_FROM && p <= BRIDGE_TO

    /** The map turned half round: the red half is the blue half seen this way. */
    fun turned(p: Double, q: Double): DoubleArray = doubleArrayOf(1 - p, 1 - q)

    /** The road down the exact middle: the shortest way between the two bases, and the map's spine. */
    val MID: List<DoubleArray> = listOf(
        doubleArrayOf(0.500, 0.930), doubleArrayOf(0.500, 0.860), doubleArrayOf(0.500, 0.780),
        doubleArrayOf(0.500, 0.690), doubleArrayOf(0.500, 0.610), doubleArrayOf(0.500, 0.545),
        doubleArrayOf(0.500, 0.500), doubleArrayOf(0.500, 0.455), doubleArrayOf(0.500, 0.390),
        doubleArrayOf(0.500, 0.310), doubleArrayOf(0.500, 0.220), doubleArrayOf(0.500, 0.140),
        doubleArrayOf(0.500, 0.070),
    )

    /** The left outer road of the blue half: out of the base, along the edge, and back in at the river. */
    val LEFT: List<DoubleArray> = listOf(
        doubleArrayOf(0.500, 0.930), doubleArrayOf(0.445, 0.905), doubleArrayOf(0.365, 0.880),
        doubleArrayOf(0.300, 0.865), doubleArrayOf(0.232, 0.828), doubleArrayOf(0.185, 0.780),
        doubleArrayOf(0.150, 0.720), doubleArrayOf(0.132, 0.660), doubleArrayOf(0.124, 0.590),
        doubleArrayOf(0.125, 0.500),
    )

    /** The right outer road of the blue half: the left one mirrored across the spine. */
    fun rightHalf(): List<DoubleArray> = LEFT.map { doubleArrayOf(1 - it[0], it[1]) }

    /** Every road on the map: each half's three, and their turns. */
    fun roads(): List<List<DoubleArray>> {
        val blue = listOf(MID, LEFT, rightHalf())
        val red = blue.map { road -> road.map { doubleArrayOf(1 - it[0], 1 - it[1]) } }
        return blue + red
    }

    fun length(road: List<DoubleArray>): Double {
        var sum = 0.0
        for (i in 1 until road.size) {
            sum += hypot(road[i][0] - road[i - 1][0], road[i][1] - road[i - 1][1])
        }
        return sum
    }

    /** What stands on the blue half. The red half is this, turned. */
    fun blueHalf(): List<RiftPiece> {
        val out = ArrayList<RiftPiece>(48)
        out.add(RiftPiece("nexus", 0.500, 0.930, 0))
        out.add(RiftPiece("fountain", 0.500, 0.972, 0))
        out.add(RiftPiece("nexusTower", 0.445, 0.905, 0))
        out.add(RiftPiece("nexusTower", 0.555, 0.905, 0))
        out.add(RiftPiece("inhibitor", 0.365, 0.862, 0))
        out.add(RiftPiece("inhibitor", 0.500, 0.848, 0))
        out.add(RiftPiece("inhibitor", 0.635, 0.862, 0))
        for (q in listOf(0.795, 0.690, 0.585)) out.add(RiftPiece("tower", 0.500, q, 0))
        for (at in listOf(0.300 to 0.865, 0.232 to 0.828, 0.150 to 0.720)) out.add(RiftPiece("tower", at.first, at.second, 0))
        for (at in listOf(0.700 to 0.865, 0.768 to 0.828, 0.850 to 0.720)) out.add(RiftPiece("tower", at.first, at.second, 0))
        // the great pits: the ember one on the blue side's lower bank, the violet one on the other
        out.add(RiftPiece("pit", 0.715, 0.538, 0, 1))
        out.add(RiftPiece("forest", 0.255, 0.630, 0, 0))
        out.add(RiftPiece("forest", 0.745, 0.630, 0, 1))
        out.add(RiftPiece("fire", 0.300, 0.660, 0))
        out.add(RiftPiece("fire", 0.700, 0.600, 0))
        for (at in listOf(0.300 to 0.600, 0.350 to 0.680, 0.205 to 0.690)) out.add(RiftPiece("camp", at.first, at.second, 0))
        for (at in listOf(0.700 to 0.600, 0.650 to 0.680, 0.795 to 0.690)) out.add(RiftPiece("camp", at.first, at.second, 0))
        return out
    }

    private fun turn(piece: RiftPiece): RiftPiece {
        val team = when (piece.team) {
            0 -> 1
            1 -> 0
            else -> piece.team
        }
        val variant = if (piece.kind == "pit") (if (piece.variant == 1) 2 else 1) else piece.variant
        return RiftPiece(piece.kind, 1 - piece.p, 1 - piece.q, team, variant)
    }

    /** Everything that stands on the whole map: the blue half and its turn. */
    fun all(): List<RiftPiece> = blueHalf() + blueHalf().map { turn(it) }

    /** The plan's share of a length in plan units, as pixels of a W x H picture. */
    fun px(p: Double, w: Int): Double = across(p) * w
    fun py(q: Double, h: Int): Double = down(q) * h
}
