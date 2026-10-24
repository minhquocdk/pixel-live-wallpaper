// The engine held to the almanac's own numbers: the same seed, the same noise, the same island. Every expected value
// here was taken from the JavaScript in src/ and examples/island/scene.js, so the port is checked against the original
// and not against itself.
package dev.almanac.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineTest {

    private fun close(expected: Double, actual: Double, eps: Double = 1e-6) {
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)
    }

    @Test
    fun `hash and seeds agree with the almanac`() {
        assertEquals(3028713910L, hash32(1).toLong() and 0xFFFFFFFFL)
        assertEquals(61159, seedOf("heron-bay-8"))
        // a whole number is itself, words are hashed to 1..99999
        assertEquals(42, seedOf("42"))
        assertEquals(1, seedOf("   "))
    }

    @Test
    fun `value noise and fractal noise agree with the almanac`() {
        close(0.737099, valueNoise(3.7, 1.2, 7))
        close(0.588917, valueNoise(10.5, 4.25, 61159))
        close(0.472541, fractalNoise(10.5, 4.25, 61159, 3))
        close(0.606661, fractalNoise(200.1, 100.9, 999, 2))
        close(0.996619, indexRandom(7, 42))
        close(0.507853, latticeRandom(3, 4, 5))
    }

    @Test
    fun `the seeded generator agrees with the almanac`() {
        val r = SeededRandom("heron-bay-8")
        close(0.663889, r.next())
        close(0.838649, r.next())
        close(0.447042, r.next())
        val r61 = SeededRandom(61159)
        close(0.904563, r61.next())
        close(0.683097, r61.next())
    }

    @Test
    fun `the island's shape agrees with the almanac`() {
        // the almanac's island stretches its shape along x (0.7, 1.6) and asks for 200 tiles across
        close(0.123157, islandHeight(50.0, 50.0, 200.0, 200.0, 61159, 0.7, 1.6, 0.6))
        close(0.683037, islandHeight(100.0, 80.0, 200.0, 200.0, 61159, 0.7, 1.6, 0.6))
        assertEquals(0.0, islandHeight(199.0, 199.0, 200.0, 200.0, 12345, 0.7, 1.6, 0.6), 1e-9)
    }

    @Test
    fun `colours are packed as a bitmap packs them and grade as the almanac's do`() {
        // 0xAARRGGBB: red highest
        assertEquals(0xFFFF8000.toInt(), hex("#ff8000"))
        assertEquals(0xFF, redOf(hex("#ff8000")))
        assertEquals(0x80, greenOf(hex("#ff8000")))
        assertEquals(0, blueOf(hex("#ff8000")))
        assertEquals(0xFF7F007F.toInt(), mixPacked(hex("#ff0000"), hex("#0000ff"), 0.5))
        assertEquals(0xFF588E2C.toInt(), gradeColour(hex("#4a8a3a"), Grade(warm = 0.3)))
        assertEquals(0xFFB57035.toInt(), hueShiftTone(hex("#caa35c"), -1.0))
        close(0.03125, bayerThreshold(0, 0))
        close(0.71875, bayerThreshold(1, 2))
        close(0.34375, bayerThreshold(3, 3))
    }

    @Test
    fun `water draws the colours the almanac draws`() {
        val p = WaterPalette(
            LV = listOf("#74cfdc", "#5ec0d7", "#4caecf", "#3e9bc5", "#3489ba", "#2c78ad", "#25679f").map { hex(it) }.toIntArray(),
            FOAM = hex("#f6fdff"),
            FOAM2 = hex("#c4ecf2"),
            GLINT = hex("#ffffff"),
            GLINT2 = hex("#dff8ff"),
            WEED = hex("#2f7db0"),
            WEED2 = hex("#3487b6"),
            SAND = listOf("#e2d3a0", "#bfd0a6", "#8cc2b4").map { hex(it) }.toIntArray(),
        )
        val t = waterTime(3.5)
        assertEquals(0xFF4CAECF.toInt(), waterPixel(10, 20, 3.0, false, 0, t, 0, p))
        assertEquals(0xFF3487B6.toInt(), waterPixel(100, 50, 12.0, true, 0, t, 0, p))
        assertEquals(0xFF2C78AD.toInt(), waterPixel(200, 100, 40.0, false, 2, t, 0, p))
    }

    @Test
    fun `the chamfer distance and the pathfinding agree with the almanac`() {
        assertEquals(
            listOf<Short>(0, 3, 6, 3, 4, 7, 6, 7, 8),
            chamferDistance(booleanArrayOf(false, true, true, true, true, true, true, true, true), 3, 3).toList(),
        )
        val path = gridAStar(
            intArrayOf(0, 0), intArrayOf(3, 0), 100,
        ) { x, y -> x in 0..4 && y in 0..2 }
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), path?.map { it[0] to it[1] })
    }

    @Test
    fun `an island grows without a phone and draws a picture`() {
        val scene = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        assertEquals(seedOf("heron-bay-8"), scene.seed)
        assertTrue("a world should have some land", scene.world.LEV.count { it >= 0 } > 500)
        scene.render()
        val colours = scene.frame.toHashSet().size
        assertTrue("the picture should be drawn, not blank: $colours colours", colours > 60)
        // the base frame is the same twice, once the world has stopped moving
        val first = scene.frame.copyOf()
        scene.render()
        assertEquals(first.toList(), scene.frame.toList())
        // but the world does move
        val a = scene.frame.copyOf()
        repeat(30) { scene.step(1.0 / 30) }
        scene.render()
        assertNotEquals(a.toList(), scene.frame.toList())
    }

    @Test
    fun `a still moment draws the same picture however many frames have been drawn`() {
        // with the time of day held still, a frame is a pure function of the world: drawing it again must change nothing
        val scene = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        scene.setTimeOfDay(0.35)
        scene.render()
        val first = scene.frame.copyOf()
        repeat(120) { scene.render() }
        val differing = first.indices.count { first[it] != scene.frame[it] }
        assertTrue("a repeated frame differs in $differing pixels", differing == 0)
    }

    @Test
    fun `a running wallpaper draws the same picture as one that has only just been made`() {
        // The wallpaper has already drawn thousands of frames when the walkers pass; a scene that has drawn them all
        // must show exactly what a scene brought to the same moment and drawn once shows. (Without a depth buffer
        // restored each frame, the trails of earlier sprites eat the later ones: the walkers sink into the ground.)
        val drawn = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        drawn.setTimeOfDay(0.35)
        repeat(150) {
            drawn.step(1.0 / 30)
            drawn.render()
        }
        val fresh = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        fresh.setTimeOfDay(0.35)
        repeat(150) { fresh.step(1.0 / 30) }
        fresh.render()
        val differing = drawn.frame.indices.count { drawn.frame[it] != fresh.frame[it] }
        assertTrue(
            "a wallpaper that has drawn 150 frames differs from a fresh one at the same moment in $differing pixels",
            differing == 0,
        )
    }

    @Test
    fun `the same seed grows the same island, another seed another`() {
        val a = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        val b = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        val c = IslandScene("amber-vale-7", W = 160, H = 96, detail = 0.62)
        assertEquals(a.world.LEV.toList(), b.world.LEV.toList())
        assertNotEquals(a.world.LEV.toList(), c.world.LEV.toList())
        assertEquals(a.world.houses.size, b.world.houses.size)
    }

    @Test
    fun `a phone-shaped frame grows an island its own shape`() {
        val scene = IslandScene("heron-bay-8", W = 216, H = 480, detail = 0.62)
        val land = scene.world.LEV.count { it >= 0 }
        assertTrue("a tall frame should still be mostly island: $land of ${scene.world.LEV.size}", land > scene.world.LEV.size * 0.2)
    }

    @Test
    fun `the maple theme grows the same island in its own colours`() {
        val plain = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62)
        val maple = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62, theme = Theme.MAPLE)
        // the same land, the same villages and houses: a theme is a skin, not another island
        assertEquals(plain.world.LEV.toList(), maple.world.LEV.toList())
        assertEquals(plain.world.houses.size, maple.world.houses.size)
        assertTrue(maple.world.villages.all { v -> v.styles.all { it == "mushroom" } })
        assertTrue("maple should drop leaves", maple.mapleLeaves.isNotEmpty())
        assertTrue("the original island has no maple leaves", plain.mapleLeaves.isEmpty())
        plain.setTimeOfDay(0.35)
        maple.setTimeOfDay(0.35)
        plain.render()
        maple.render()
        assertNotEquals(plain.frame.toList(), maple.frame.toList())
    }

    @Test
    fun `a running maple wallpaper draws the same picture as a fresh one`() {
        val drawn = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62, theme = Theme.MAPLE)
        drawn.setTimeOfDay(0.35)
        repeat(150) {
            drawn.step(1.0 / 30)
            drawn.render()
        }
        val fresh = IslandScene("heron-bay-8", W = 160, H = 96, detail = 0.62, theme = Theme.MAPLE)
        fresh.setTimeOfDay(0.35)
        repeat(150) { fresh.step(1.0 / 30) }
        fresh.render()
        assertEquals(drawn.frame.toList(), fresh.frame.toList())
    }

    @Test
    fun `a theme is read from its id, and an unknown id is the original`() {
        assertEquals(Theme.MAPLE, Theme.fromId("maple"))
        assertEquals(Theme.ORIGINAL, Theme.fromId("pokemon"))
        assertEquals(Theme.ORIGINAL, Theme.fromId(null))
    }

    @Test
    fun `a seed rolls from two word lists`() {
        val phrase = seedWords(SeedLists.FIRST, SeedLists.SECOND) { 0.5 }
        assertTrue(phrase, Regex("^[a-z]+-[a-z]+-\\d{1,2}$").matches(phrase))
        assertEquals(seedOf(phrase), seedOf(phrase))
    }
}

/** The word lists, kept here so the test does not need the app. */
object SeedLists {
    val FIRST = listOf("amber", "ash", "briar", "cedar")
    val SECOND = listOf("bay", "bank", "beach", "bluff")
}
