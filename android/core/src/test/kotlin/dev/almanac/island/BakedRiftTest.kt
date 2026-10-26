// The baked map: a painted background the engine animates. The tests skip themselves when the build has no painting,
// so the engine still checks on its own.
package dev.almanac.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BakedRiftTest {

    private val dir = File("../app/src/main/assets/rift")

    private fun scenes(): Triple<PngImage, PngImage, BakedRiftScene> {
        assumeTrue("no baked background in this build", File(dir, "background.png").exists())
        val bg = readPng(File(dir, "background.png").path)
        val mask = readPng(File(dir, "masks.png").path)
        return Triple(bg, mask, BakedRiftScene("twin-rivers-8", bg, mask, detail = bg.w / 307.0, startTod = 0.30))
    }

    @Test
    fun `the painting is at the engine's own size, with a mask to match`() {
        val (bg, mask, scene) = scenes()
        assertEquals(bg.w, scene.W)
        assertEquals(bg.h, scene.H)
        assertEquals(bg.w, mask.w)
        assertEquals(bg.h, mask.h)
        assertTrue("a tall map", bg.h > bg.w * 1.8)
    }

    @Test
    fun `the mask finds the water, the roads and three lanes to walk`() {
        val (_, _, scene) = scenes()
        assertTrue("the river has water pixels", scene.summary.contains("water pixels"))
        val report = scene.report().joinToString("\n")
        val water = Regex("water pixels (\\d+)").find(report)!!.groupValues[1].toInt()
        assertTrue("water is $water pixels, expected a river of some length", water > 2000)
        assertEquals("three lanes: mid, left, right", 3, scene.lanes.size)
        // every lane runs most of the way down the picture
        for (lane in scene.lanes) {
            val top = lane.first()[1]
            val bottom = lane.last()[1]
            assertTrue("lane spans $top..$bottom of ${scene.H}", bottom - top > scene.H * 0.55)
        }
    }

    @Test
    fun `the same painting animates the same way twice`() {
        val (bg, mask, _) = scenes()
        val a = BakedRiftScene("twin-rivers-8", bg, mask, detail = bg.w / 307.0, startTod = 0.3)
        val b = BakedRiftScene("twin-rivers-8", bg, mask, detail = bg.w / 307.0, startTod = 0.3)
        repeat(20) { a.step(1.0 / 30.0); b.step(1.0 / 30.0) }
        a.render(); b.render()
        assertTrue(a.frame.contentEquals(b.frame))
        // the water really moves: a second later the same frame is not the same frame
        val before = a.frame.copyOf()
        repeat(20) { a.step(1.0 / 30.0) }
        a.render()
        assertTrue("the water should have moved", !a.frame.contentEquals(before))
    }

    @Test
    fun `night is darker than noon, and both stay inside the frame`() {
        val (bg, mask, _) = scenes()
        val day = BakedRiftScene("twin-rivers-8", bg, mask, detail = bg.w / 307.0, startTod = 0.30)
        val night = BakedRiftScene("twin-rivers-8", bg, mask, detail = bg.w / 307.0, startTod = 0.80)
        day.render(); night.render()
        fun mean(f: IntArray): Double {
            var s = 0.0
            for (c in f) s += luma(c)
            return s / f.size
        }
        val d = mean(day.frame)
        val n = mean(night.frame)
        assertTrue("night $n should be darker than day $d", n < d)
        assertTrue("night should still be visible: $n", n > 0.02)
        assertEquals(day.W * day.H, day.frame.size)
    }
}
