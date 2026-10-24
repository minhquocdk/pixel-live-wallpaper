// The Rift map held to its plan: the two halves are one half turned round, the mid lane is the shortest road, the river
// is crossed once by each road, the map stands inside the frame, and the bands a phone keeps its clock and dock in stay
// dark however bright the map is.
package dev.almanac.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class RiftTest {

    private fun close(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue("expected $expected, got $actual", abs(expected - actual) < eps)
    }

    @Test
    fun `the red half is the blue half turned half round`() {
        val blue = RiftPlan.blueHalf()
        val all = RiftPlan.all()
        assertEquals(blue.size * 2, all.size)
        for (piece in blue) {
            val mirrored = all.any {
                it.kind == piece.kind &&
                    abs(it.p - (1 - piece.p)) < 1e-9 &&
                    abs(it.q - (1 - piece.q)) < 1e-9
            }
            assertTrue("no turned copy of ${piece.kind} at ${piece.p},${piece.q}", mirrored)
        }
    }

    @Test
    fun `the mid lane is the shortest way, and the two outer lanes are alike`() {
        val mid = RiftPlan.length(RiftPlan.MID)
        val left = RiftPlan.length(RiftPlan.LEFT)
        val right = RiftPlan.length(RiftPlan.rightHalf())
        assertTrue("mid $mid should be shorter than an outer lane $left", mid < left)
        close(left, right, 1e-6)
        // the outer road is longer than 1.2 times the mid road: it really does bow out
        assertTrue("outer $left vs mid $mid", left / mid > 1.2)
    }

    @Test
    fun `the river is crossed once by every road`() {
        for ((n, road) in RiftPlan.roads().withIndex()) {
            var runs = 0
            var inside = false
            for (k in 1 until road.size) {
                val steps = 40
                for (s in 0..steps) {
                    val t = s.toDouble() / steps
                    val p = lerp(road[k - 1][0], road[k][0], t)
                    val q = lerp(road[k - 1][1], road[k][1], t)
                    val now = RiftPlan.inRiver(p, q)
                    if (now && !inside) runs++
                    inside = now
                }
            }
            assertEquals("road $n crosses the river $runs times", 1, runs)
        }
    }

    @Test
    fun `every road runs from one base to the other`() {
        for ((n, road) in RiftPlan.roads().withIndex()) {
            val first = road.first()
            val last = road.last()
            val atBase = { p: Double, q: Double -> (q > 0.9 || q < 0.1) && abs(p - 0.5) < 0.08 }
            assertTrue("road $n should start at a base", atBase(first[0], first[1]))
            assertTrue("road $n should end at a base", atBase(last[0], last[1]))
        }
    }

    @Test
    fun `every tower stands on the plateau, and each side has three a lane`() {
        for (piece in RiftPlan.all()) {
            assertTrue("${piece.kind} p", piece.p in 0.0..1.0)
            assertTrue("${piece.kind} q", piece.q in 0.0..1.0)
        }
        val towers = RiftPlan.all().filter { it.kind == "tower" }
        assertEquals(18, towers.size)
        val blueMid = towers.count { it.team == 0 && abs(it.p - 0.5) < 1e-9 }
        assertEquals(3, blueMid)
        val blueLeft = towers.count { it.team == 0 && it.p < 0.45 }
        val blueRight = towers.count { it.team == 0 && it.p > 0.55 }
        assertEquals(3, blueLeft)
        assertEquals(3, blueRight)
        assertEquals(6, RiftPlan.all().count { it.kind == "inhibitor" })
        assertEquals(2, RiftPlan.all().count { it.kind == "nexus" })
        assertEquals(2, RiftPlan.all().count { it.kind == "pit" })
        assertEquals(4, RiftPlan.all().count { it.kind == "forest" })
    }

    @Test
    fun `the great pits sit on opposite banks, one violet and one ember`() {
        val pits = RiftPlan.all().filter { it.kind == "pit" }
        assertEquals(2, pits.size)
        assertNotEquals(pits[0].variant, pits[1].variant)
        val a = pits[0]
        val b = pits[1]
        close(1.0, a.p + b.p, 1e-9)
        close(1.0, a.q + b.q, 1e-9)
        // and each is off the spine, so neither blocks the mid lane
        assertTrue(abs(a.p - 0.5) > 0.1 && abs(b.p - 0.5) > 0.1)
    }

    @Test
    fun `the map fills a tall frame and keeps the clock and dock bands dark`() {
        val scene = RiftScene(W = 307, H = 682, detail = 0.8, startTod = 0.30)
        scene.render()
        val frame = scene.frame
        val w = scene.W
        val h = scene.H
        fun bandMean(from: Double, to: Double): Double {
            var sum = 0.0
            var n = 0
            for (y in (from * h).toInt() until (to * h).toInt()) {
                for (x in 0 until w) {
                    sum += luma(frame[y * w + x])
                    n++
                }
            }
            return sum / n
        }
        val top = bandMean(0.0, 0.06)
        val bottom = bandMean(0.94, 1.0)
        assertTrue("top band mean luma $top should be dark", top < 0.12)
        assertTrue("bottom band mean luma $bottom should be dark", bottom < 0.12)
        // the middle carries the map: it must be brighter than the bands
        val middle = bandMean(0.30, 0.70)
        assertTrue("middle $middle vs top $top", middle > top * 2)
    }

    @Test
    fun `the same seed draws the same map, another seed another`() {
        val a = RiftScene("twin-rivers-8", 240, 534, 0.62, startTod = 0.4)
        val b = RiftScene("twin-rivers-8", 240, 534, 0.62, startTod = 0.4)
        val c = RiftScene("heron-bay-8", 240, 534, 0.62, startTod = 0.4)
        a.step(0.5); b.step(0.5); c.step(0.5)
        a.render(); b.render(); c.render()
        assertTrue("the same seed should draw the same pixels", a.frame.contentEquals(b.frame))
        assertTrue("another seed should draw other pixels", !a.frame.contentEquals(c.frame))
    }

    @Test
    fun `night is darker than noon, and the map still reads`() {
        val noon = RiftScene(W = 240, H = 534, detail = 0.62, startTod = 0.30)
        val night = RiftScene(W = 240, H = 534, detail = 0.62, startTod = 0.78)
        noon.render()
        night.render()
        fun mean(f: IntArray): Double {
            var s = 0.0
            for (i in f.indices) s += luma(f[i])
            return s / f.size
        }
        val day = mean(noon.frame)
        val dark = mean(night.frame)
        assertTrue("night $dark should be darker than day $day", dark < day)
        assertTrue("night should still be visible, not black: $dark", dark > 0.02)
    }

    @Test
    fun `a rift scene is a live scene`() {
        val scene: LiveScene = RiftScene(W = 200, H = 444, detail = 0.52)
        assertEquals(Theme.RIFT, scene.theme)
        assertEquals("twin-rivers-8", scene.phrase)
        assertTrue(scene.seed > 0)
        assertEquals(200 * 444, scene.frame.size)
        assertTrue(scene.summary.contains("18 towers"))
        assertTrue(scene.report().isNotEmpty())
        val before = scene.frame.copyOf()
        scene.step(1.0)
        scene.render()
        assertTrue("a frame should have been drawn", !scene.frame.contentEquals(before))
    }

    @Test
    fun `the layout numbers are the ones the prompt and the spec agreed on`() {
        close(0.028, RiftPlan.INSET_X)
        close(0.120, RiftPlan.INSET_TOP)
        close(0.080, RiftPlan.INSET_BOTTOM)
        // the plateau's height to width, in a 307 x 682 frame
        val wide = (1 - 2 * RiftPlan.INSET_X) * 307
        val high = (1 - RiftPlan.INSET_TOP - RiftPlan.INSET_BOTTOM) * 682
        close(1.88, high / wide, 0.02)
        // the river is a gentle S, never straying far from the middle
        for (step in 0..20) {
            val p = step / 20.0
            assertTrue(abs(RiftPlan.riverQ(p) - 0.5) <= 0.02)
        }
        assertEquals(1.0, hypot(1.0, 0.0), 1e-9)
    }
}
