// Seeded randomness: the same seed always gives the same numbers, so an island never needs storing, only its phrase.
// Ported from the almanac's random module (Jenkins' integer hash, FNV-1a, mulberry32, the 3D lattice hash).
package dev.almanac.island

/** One 32-bit integer, well mixed: inputs one bit apart give outputs about half their bits apart. */
fun hash32(n: Int): Int {
    var a = n
    a += 0x7ed55d16 + (a shl 12)
    a = a xor 0xc761c23c.toInt() xor (a ushr 19)
    a += 0x165667b1 + (a shl 5)
    a = (a + 0xd3a2646c.toInt()) xor (a shl 9)
    a += 0xfd7046c5.toInt() + (a shl 3)
    a = a xor 0xb55a4f09.toInt() xor (a ushr 16)
    return a
}

/** A string as a 32-bit number, by FNV-1a (its low 32 bits). */
fun fnv1a(s: String): Int {
    var h = 2166136261.toInt()
    for (i in s.indices) {
        h = h xor s[i].code
        h *= 16777619
    }
    return h
}

/** A string as a well-mixed 32-bit number: FNV-1a, then hash32, so "a1" and "a2" land far apart. */
fun hashString(s: String): Int = hash32(fnv1a(s))

/**
 * The seed a value stands for: a whole number is itself (at least 1); words are hashed, case and spaces at the ends
 * aside, to a number from 1 to 99,999; nothing is 1. So a seed shared as words grows the same world, as in the almanac.
 */
fun seedOf(value: String): Int {
    val s = value.trim()
    if (s.isNotEmpty() && s.all { it in '0'..'9' }) return maxOf(1, s.toIntOrNull() ?: 1)
    return if (s.isEmpty()) 1 else (hashString(s.lowercase()) % 99999) + 1
}

/** A seed as a number, for scenes that take one. */
fun seedOf(value: Int): Int = maxOf(1, value)

/** A number in [0, 1) for an index and a seed: the 7th tree is always the 7th tree. */
fun indexRandom(i: Int, seed: Int): Double =
    (hash32((i * 374761393) xor hash32(seed)).toLong() and 0xFFFFFFFFL) / 4294967296.0

/** One well-mixed number for a point of a grid: value noise is built by blending these. */
fun latticeHash(x: Int, y: Int, seed: Int = 0): Int =
    hash32((x * 374761393) xor hash32((y * 668265263) xor (seed * 1274126177)))

/**
 * The same hash as the almanac's, unsigned: 0 <= n < 2^32. The almanac's hash32 returns `a >>> 0`, so its `%` is a
 * non-negative remainder; a Kotlin Int's is not, and a bare `% 13` on it picks a different one cell in thirteen.
 */
fun latticeHashU(x: Int, y: Int, seed: Int = 0): Long = latticeHash(x, y, seed).toLong() and 0xFFFFFFFFL

fun latticeRandom(x: Int, y: Int, seed: Int = 0): Double =
    (latticeHash(x, y, seed).toLong() and 0xFFFFFFFFL) / 4294967296.0

/** A random number for a point of a 3D lattice (x, y and a beat of time). */
fun latticeHash3(x: Int, y: Int, z: Int): Double {
    var n = (x * 374761393 + y * 668265263 + z * 1274126177)
    n = (n xor (n ushr 13)) * 1274126177
    return ((n xor (n ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0
}

/**
 * A generator of numbers in [0, 1) from a seed: mulberry32, the same everywhere, so a seed replays a scene exactly.
 */
class SeededRandom(seed: Int) {
    private var s: Int = hash32(seed xor 0x9e3779b9.toInt()).let { if (it == 0) 1 else it }

    constructor(phrase: String) : this(hashString(phrase))

    fun next(): Double {
        s += 0x6d2b79f5
        var t = s
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }

    fun range(a: Double, b: Double): Double = a + (b - a) * next()

    fun range(a: Int, b: Int): Int = a + ((b - a + 1) * next()).toInt()

    fun chance(p: Double): Boolean = next() < p

    fun sign(): Int = if (next() < 0.5) -1 else 1

    fun <T> pick(list: List<T>): T = list[(next() * list.size).toInt().coerceAtMost(list.size - 1)]
}

/** A linear congruential generator: tiny, and the same numbers in every language. */
class LinearCongruential(seed: Int) {
    private var s: Int = seed
    fun next(): Double {
        s = s * 1664525 + 1013904223
        return (s.toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** A seed phrase made of two words and a number, from a word list of the caller's. */
fun seedWords(first: List<String>, second: List<String>, random: () -> Double = { Math.random() }): String {
    fun pick(a: List<String>) = a[(random() * a.size).toInt().coerceAtMost(a.size - 1)]
    return "${pick(first)}-${pick(second)}-${1 + (random() * 99).toInt()}"
}
