// Colour: packed 32-bit colours (as an Android Bitmap holds them, 0xAARRGGBB: alpha highest), read from "#rrggbb",
// mixed, shaded, moved round the colour wheel, graded as a film is; and the two dithering tools a pixel artist uses.
// Ported from the almanac's colour and pixels modules.
package dev.almanac.island

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

fun redOf(c: Int): Int = (c ushr 16) and 255
fun greenOf(c: Int): Int = (c ushr 8) and 255
fun blueOf(c: Int): Int = c and 255
fun alphaOf(c: Int): Int = (c ushr 24) and 255

/** A packed colour from three channels, as a Bitmap keeps them: 0xAARRGGBB (red highest, alpha over it). */
fun rgb(r: Double, g: Double, b: Double): Int =
    (0xFF shl 24) or ((r.toInt() and 255) shl 16) or ((g.toInt() and 255) shl 8) or (b.toInt() and 255)

fun rgb(r: Int, g: Int, b: Int): Int = rgb(r.toDouble(), g.toDouble(), b.toDouble())

/** A "#rrggbb" (or "#rgb") colour, opaque. */
fun hex(s: String): Int {
    val t = s.removePrefix("#")
    val full = if (t.length == 3) t.map { "$it$it" }.joinToString("") else t
    val n = full.toLongOrNull(16) ?: 0x808080
    return rgb(((n shr 16) and 255).toInt(), ((n shr 8) and 255).toInt(), (n and 255).toInt())
}

/** Part of the way from one colour to another, channel by channel. */
fun mixPacked(a: Int, b: Int, t: Double): Int {
    if (t <= 0) return a or (0xFF shl 24)
    if (t >= 1) return b or (0xFF shl 24)
    val r = redOf(a) + (redOf(b) - redOf(a)) * t
    val g = greenOf(a) + (greenOf(b) - greenOf(a)) * t
    val bl = blueOf(a) + (blueOf(b) - blueOf(a)) * t
    return rgb(r, g, bl)
}

/** A colour with each channel multiplied by k and held at 255. */
fun scalePacked(c: Int, k: Double): Int =
    rgb(min(255.0, redOf(c) * k), min(255.0, greenOf(c) * k), min(255.0, blueOf(c) * k))

/** How bright a colour looks, 0..1 (BT.601 luma). */
fun luma(c: Int): Double = (0.299 * redOf(c) + 0.587 * greenOf(c) + 0.114 * blueOf(c)) / 255

fun rgbToHsl(c: Int): DoubleArray {
    val r = redOf(c) / 255.0
    val g = greenOf(c) / 255.0
    val b = blueOf(c) / 255.0
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    val d = mx - mn
    if (d == 0.0) return doubleArrayOf(0.0, 0.0, l)
    val s = d / (1 - abs(2 * l - 1))
    val h = when (mx) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return doubleArrayOf((h * 60 + 360) % 360, s, l)
}

fun hslToRgb(h: Double, s: Double, l: Double): Int {
    val (r, g, b) = hslToRgbF(h, s, l)
    return rgb(r, g, b)
}

/** The same, unrounded in 0..255: a grade warms the floats and rounds once, as the almanac does. */
fun hslToRgbF(h: Double, s: Double, l: Double): DoubleArray {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs(((h / 60) % 2) - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0.0)
        h < 120 -> Triple(x, c, 0.0)
        h < 180 -> Triple(0.0, c, x)
        h < 240 -> Triple(0.0, x, c)
        h < 300 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return doubleArrayOf((r + m) * 255, (g + m) * 255, (b + m) * 255)
}

/** A hue moved part of the way toward another, the short way round; a far hue only leans. */
fun hueToward(h: Double, to: Double, t: Double): Double {
    val d = ((to - h + 540) % 360) - 180
    val far = if (abs(d) > 100) 0.25 else 1.0
    return (h + d * t * far + 360) % 360
}

/** A pixel artist's shade: darker turns the hue toward blue-violet and makes it richer, lighter toward warm yellow. */
fun hueShiftTone(c: Int, k: Double): Int {
    val hsl = rgbToHsl(c)
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    val to = if (k < 0) 250.0 else 52.0
    val d = ((to - h + 540) % 360) - 180
    val pale = if (l > 0.72) 0.45 else 1.0
    val hue = h + d * min(0.32, abs(k) * 0.075) * pale
    val sat = max(0.0, min(1.0, s * if (k < 0) 1 + 0.07 * -k * pale else 1 - 0.14 * k))
    val light = max(
        0.02,
        min(
            0.97,
            l * (if (k < 0) 0.8.pow(-k) else 1.0) + (if (k > 0) (1 - l) * (1 - 0.72.pow(k)) else 0.0),
        ),
    )
    return hslToRgb(hue, sat, light)
}

/** A grade's settings, as gradeColour takes them. */
data class Grade(
    val sat: Double = 1.0,
    val warm: Double = 0.0,
    val contrast: Double = 1.0,
    val light: Double = 0.0,
    val hue: Double = 0.0,
    val hueMix: Double = 0.0,
) {
    val plain: Boolean get() = sat == 1.0 && warm == 0.0 && contrast == 1.0 && light == 0.0 && hueMix == 0.0
}

/** A colour graded as a film is: hue moved, saturation scaled, lightness raised, contrast stretched, warmed. */
fun gradeColour(c: Int, grade: Grade): Int {
    if (grade.plain) return c
    val hsl = rgbToHsl(c)
    var h = hsl[0]
    var s = hsl[1]
    var l = hsl[2]
    if (grade.hueMix != 0.0) h = hueToward(h, grade.hue, grade.hueMix)
    s = clamp(s * grade.sat)
    l = clamp(0.5 + (clamp(l + grade.light) - 0.5) * grade.contrast)
    val c2 = hslToRgbF(h, s, l)
    var r = c2[0]
    var g = c2[1]
    var b = c2[2]
    if (grade.warm != 0.0) {
        r += 46 * grade.warm
        g += 12 * grade.warm
        b -= 46 * grade.warm
    }
    return rgbClamped(r, g, b)
}

/** A colour clamped to the byte range, rounded, then packed (the almanac's rgbToHex, then packRgb). */
fun rgbClamped(r: Double, g: Double, b: Double): Int =
    rgb(
        clamp(r, 0.0, 255.0).roundToInt(),
        clamp(g, 0.0, 255.0).roundToInt(),
        clamp(b, 0.0, 255.0).roundToInt(),
    )

val BAYER: IntArray = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** The ordered-dithering threshold at a pixel: the 4x4 Bayer matrix, as a number in 0..1. */
fun bayerThreshold(x: Int, y: Int): Double = (BAYER[((y and 3) shl 2) or (x and 3)] + 0.5) / 16

/** Whether a pixel near a border belongs to the other region: the share set against the Bayer pattern. */
fun ditheredBorder(f: Double, x: Int, y: Int, n: Double): Boolean =
    f > 0 && f > bayerThreshold(x, y) * 0.5 + 0.25 * (n - 0.5)

/** A whole number 0..n-1 from a share, for picking from a list. */
fun pickIndex(share: Double, n: Int): Int = floor(share * n).toInt().coerceIn(0, n - 1)
