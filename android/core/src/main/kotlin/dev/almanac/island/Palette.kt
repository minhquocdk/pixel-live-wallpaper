// The island's colours: the same palette the almanac's island scene uses, passed through one film grade, with a
// pixel artist's ramps (each colour two steps darker to two lighter, turning in hue as it goes). Ported from
// examples/island/scene.js so a seed gives the same island on Android.
package dev.almanac.island

import kotlin.math.min

/** The whole palette passes through one grade, as a film's does. */
val LOOK: Grade = Grade(sat = 1.08, warm = 0.05, contrast = 1.04)

/** A marker pixel: alpha 0xFE, so it never passes for a colour; it says a light lights up after dark, and whose. */
fun marker(type: Int, id: Int = 0): Int = (0xFE shl 24) or (type shl 16) or (id and 0xFFFF)

fun isMarker(c: Int): Boolean = (c ushr 24) == 0xFE

object Marks {
    const val WIN = 1
    const val LAMP = 2
    const val TIP = 3
    const val BEACON = 4
    const val FIRE = 5
}

/** The three lightness tables of the hour (see Light.hourTables). */
class HourTables(val r: IntArray, val g: IntArray, val b: IntArray)

/** Everything the island is coloured with. Built once per scene. */
class Palette {
    private val ramps = HashMap<Int, IntArray>()

    /** A "#rrggbb" colour through the film grade the whole palette passes through. */
    fun hex(s: String): Int = gradeColour(dev.almanac.island.hex(s), LOOK)

    /** A colour's ramp, two steps darker to two lighter, each step turning in hue as a pixel artist's does. */
    fun tone(c: Int, k: Int): Int {
        val r = ramps.getOrPut(c) {
            doubleArrayOf(-1.7, -0.85, 0.0, 0.75, 1.5).map { s -> if (s != 0.0) hueShiftTone(c, s) else c }
                .toIntArray()
        }
        return r[(k + 2).coerceIn(0, 4)]
    }

    val sand = hex("#ead9a2")
    val wetSand = hex("#d3bd86")
    val lush = hex("#74c25a")
    val dry = hex("#a7c463")
    val wood1 = hex("#58a44c")
    val wood2 = hex("#468f48")
    val heath = hex("#86a05c")
    val moor = hex("#a08f6a")
    val rock = hex("#a39c8e")
    val snow = hex("#e9ece6")
    val earth = hex("#a8774f")
    val stone = hex("#8f8c87")
    val dirt = hex("#cfac72")
    val plank = hex("#a9763f")
    val bank = hex("#9c7c4e")
    val wheat = hex("#e2c25c")
    val greens = hex("#6aab4b")
    val cabbage = hex("#93c979")
    val fallow = hex("#a87b53")
    val paddy = hex("#93d3cf")
    val sprout = hex("#5fb35a")
    val flowers = listOf("#fff4e0", "#ffd75a", "#ff9fb8", "#c9a9ff").map { hex(it) }
    val bark = listOf(hex("#5b3b26"), hex("#87593a"))
    val inkTree = hex("#1e2b22")
    val ink = hex("#24191b")
    val plaster = hex("#ece0c6")
    val timber = hex("#c9996a")
    val door = hex("#5d3b25")
    val glass = hex("#33496c")
    val lit = hex("#ffd98a")
    val lamp = hex("#fff0b0")
    val pole = hex("#3b2f2c")
    val fire = listOf(hex("#fff3b0"), hex("#ffb347"), hex("#f0632e"))
    val smoke = hex("#d9d6d2")
    val rain = hex("#a9c2e2")
    val star = hex("#e6eeff")
    val firefly = hex("#e9ff8f")
    val lantern = hex("#ffb04a")
    val text = hex("#f6eed8")
    val accent = hex("#ffd36e")
    val textInk = hex("#1a1824")
    val mark = hex("#c8c0b0")
    val fog = hex("#ebe8f2")
    val gull = hex("#fbfbf6")
    val gullShade = hex("#b9c0c8")
    val sail = hex("#f8f3e6")
    val hull = hex("#8a5432")
    val beacon = hex("#fff6c8")
    val tower = hex("#f2efe8")
    val towerRed = hex("#c8473c")
    val tip = hex("#ffc2e0")
    val tipBright = hex("#fff4fb")

    val P = WaterPalette(
        LV = listOf("#74cfdc", "#5ec0d7", "#4caecf", "#3e9bc5", "#3489ba", "#2c78ad", "#25679f").map { hex(it) }.toIntArray(),
        FOAM = hex("#f6fdff"),
        FOAM2 = hex("#c4ecf2"),
        GLINT = hex("#ffffff"),
        GLINT2 = hex("#dff8ff"),
        WEED = hex("#2f7db0"),
        WEED2 = hex("#3487b6"),
        SAND = listOf("#e2d3a0", "#bfd0a6", "#8cc2b4").map { hex(it) }.toIntArray(),
    )
    val PR = WaterPalette(
        LV = listOf("#9ee6dc", "#86d9d6", "#6fcad0", "#5cb8c8", "#4ea6bf", "#4494b4", "#3b83a8").map { hex(it) }.toIntArray(),
        FOAM = P.FOAM,
        FOAM2 = P.FOAM2,
        GLINT = P.GLINT,
        GLINT2 = P.GLINT2,
        WEED = hex("#4a9a8a"),
        WEED2 = hex("#58a890"),
        SAND = P.SAND,
    )

    val greys = listOf("#16182a", "#2a2b3c", "#40404e", "#575562", "#706c76", "#8b8690", "#a7a1a8", "#c7c1c1", "#ebe5d6")
        .map { hex(it) }
    val clay = listOf("#7e7a74", "#8f8a82", "#a09a90", "#b0a99e", "#bfb8ac", "#cdc6ba", "#dbd5ca").map { hex(it) }

    /** The colour a marker's pixel shows by day (0 none, then window, pole, bud, beacon, fire). */
    val markDay = intArrayOf(0, glass, pole, hex("#f3a9c8"), beacon, fire[1])

    fun greyOf(v: Double, x: Int, y: Int): Int =
        greys[clampInt((v * 8 + bayerThreshold(x, y)).toInt(), 0, 8)]

    val leaves = mapOf(
        "meadow" to listOf("#2f6b35", "#4d9a43", "#78c25a", "#a8df7c").map { hex(it) },
        "wood" to listOf("#24532f", "#3b7d3c", "#5aa148", "#86c767").map { hex(it) },
        "pine" to listOf("#1d4433", "#2e6444", "#468556", "#6aa874").map { hex(it) },
        "blossom" to listOf("#7a4a6a", "#c9739c", "#f0a3c3", "#ffd6e6").map { hex(it) },
    )

    val crops = intArrayOf(wheat, greens, cabbage, paddy, fallow, 0, wheat, 0)

    /** Every wall faces south, toward us: the light comes from the upper left, a little toward us. */
    private val lightDir = doubleArrayOf(-0.45, 0.89)
    val wallK: Double = 1 + (-0.18 - 0.22 * (1 - (0.0 * lightDir[0] + 1.0 * lightDir[1])) * 0.5)

    /** Wall textures, 16x16 tiles, by material. */
    val walls: Map<String, IntArray> = buildMap {
        for ((kind, base) in listOf(
            "earth" to earth,
            "stone" to stone,
            "snow" to hex("#cfd8e1"),
            "sand" to hex("#c6a876"),
        )) {
            put(
                kind,
                IntArray(256) { k ->
                    scalePacked(base, min(1.08, wallShade(kind, k and 15, k shr 4)) * wallK)
                },
            )
        }
    }

    fun wallKind(level: Int): String = if (level <= 3) "earth" else if (level <= 5) "stone" else "snow"
}

/** A hash of two integers into 0..1, three decimals (the wall textures' grain). */
private fun grain(x: Int, y: Int): Double =
    (((x * 374761393 + y * 668265263) * 1274126177).toLong() and 0xFFFFFFFFL) % 1000 / 1000.0

/** A wall's lightness at a texel of a 16-pixel tile, by its stuff (see the almanac's relief.wallShade). */
fun wallShade(kind: String, x: Int, y: Int): Double {
    if (y == 0) return 1.0
    if (kind == "stone") {
        val course = (y - 1) / 5
        val off = if (course and 1 != 0) 4 else 0
        val mortar = (y - 1) % 5 == 4 || (x + off) % 8 == 7
        return if (mortar) 0.55 else 0.84 + (grain((x + off) / 8, course) - 0.5) * 0.12 - ((y - 1) % 5) * 0.015
    }
    if (kind == "earth") {
        return (if (((y - 1) / 3) and 1 != 0) 0.8 else 0.72) - (if (grain(x, y) < 0.12) 0.12 else 0.0) +
            (if (y == 1) 0.1 else 0.0)
    }
    if (kind == "snow") return if (y < 3) 0.98 else 0.86 - (if (grain(x shr 1, y shr 2) < 0.3) 0.05 else 0.0)
    return 0.9 - (if (grain(x, y) < 0.18) 0.12 else 0.0) - (if (y > 10) 0.05 else 0.0)
}
