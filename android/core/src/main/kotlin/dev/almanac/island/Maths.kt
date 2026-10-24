// Numbers: the few small functions every other file leans on. Kept identical to the almanac's numbers module so that a
// scene ported from examples/island grows the same island from the same seed.
package dev.almanac.island

import kotlin.math.abs
import kotlin.math.floor

const val TAU: Double = Math.PI * 2

fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0): Double = if (v < lo) lo else if (v > hi) hi else v

fun clamp(v: Float, lo: Float = 0f, hi: Float = 1f): Float = if (v < lo) lo else if (v > hi) hi else v

fun clampInt(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v

/** Part of the way from a to b. */
fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 0 below the edge, 1 past it, eased so the change starts and ends still. */
fun smoothstep(a: Double, b: Double, v: Double): Double {
    val t = clamp(if (b == a) 0.0 else (v - a) / (b - a))
    return t * t * (3 - 2 * t)
}

/** The shortest turn from one angle to another, in radians. */
fun angleDiff(a: Double, b: Double): Double {
    var d = (b - a) % TAU
    if (d > Math.PI) d -= TAU
    if (d < -Math.PI) d += TAU
    return d
}

fun wrap(v: Double, m: Int): Int {
    val r = v.toInt() % m
    return if (r < 0) r + m else r
}

/** Floor into an Int, safe for anything a scene throws at it. */
fun floorInt(v: Double): Int = floor(v).toInt()

/** A whole number folded into 0..n-1, negative inputs included. */
fun positiveMod(v: Int, n: Int): Int {
    val r = v % n
    return if (r < 0) r + n else r
}

fun nearly(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps
