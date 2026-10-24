// A still (or a strip of stills) from the island scene, written as a PNG: the same engine the wallpaper runs, without
// Android, so the picture can be looked at on a laptop and compared with the almanac's own island.
//
//   PreviewMain --seed heron-bay-8 --t 13 --out /tmp/island.png
//   PreviewMain --seed amber-vale-7 --strip 0,40,80 --out /tmp/strip.png
//   PreviewMain --seed heron-bay-8 --theme maple --out /tmp/maple.png
package dev.almanac.island.preview

import dev.almanac.island.IslandScene
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

private fun arg(args: Array<String>, name: String, fallback: String): String {
    val k = args.indexOf(name)
    return if (k >= 0 && k + 1 < args.size) args[k + 1] else fallback
}

/** Writes RGBA pixels as a PNG (8 bits a channel, no alpha filtering), as the almanac's Node tools do. */
fun writePng(path: String, w: Int, h: Int, px: IntArray, scale: Int = 1) {
    val ow = w * scale
    val oh = h * scale
    val raw = ByteArray((ow * 4 + 1) * oh)
    var o = 0
    for (y in 0 until oh) {
        raw[o++] = 0 // filter: none
        val sy = y / scale
        for (x in 0 until ow) {
            val c = px[sy * w + (x / scale)]
            // colours are packed the way an Android Bitmap keeps them (0xAARRGGBB), so red is highest
            raw[o++] = ((c shr 16) and 255).toByte()
            raw[o++] = ((c shr 8) and 255).toByte()
            raw[o++] = (c and 255).toByte()
            raw[o++] = 255.toByte()
        }
    }
    val deflater = Deflater(Deflater.BEST_SPEED)
    deflater.setInput(raw)
    deflater.finish()
    val compressed = ByteArray(raw.size + 1024)
    var len = 0
    val buf = ByteArray(65536)
    while (!deflater.finished()) {
        val n = deflater.deflate(buf)
        if (len + n > compressed.size) {
            val bigger = compressed.copyOf(maxOf(compressed.size * 2, len + n))
            System.arraycopy(compressed, 0, bigger, 0, len)
            len = len.also { System.arraycopy(bigger, it, bigger, it, n) }
        }
        System.arraycopy(buf, 0, compressed, len, n)
        len += n
    }
    deflater.end()
    val out = FileOutputStream(File(path))
    out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    val ihdr = ByteArray(13)
    fun be32(a: ByteArray, at: Int, v: Int) {
        a[at] = (v ushr 24).toByte()
        a[at + 1] = (v ushr 16).toByte()
        a[at + 2] = (v ushr 8).toByte()
        a[at + 3] = v.toByte()
    }
    be32(ihdr, 0, ow)
    be32(ihdr, 4, oh)
    ihdr[8] = 8
    ihdr[9] = 6
    chunk(out, "IHDR", ihdr)
    chunk(out, "IDAT", compressed.copyOf(len))
    chunk(out, "IEND", ByteArray(0))
    out.close()
}

private fun chunk(out: FileOutputStream, type: String, data: ByteArray) {
    val tb = type.toByteArray(Charsets.US_ASCII)
    val len = ByteArray(4)
    len[0] = (data.size ushr 24).toByte()
    len[1] = (data.size ushr 16).toByte()
    len[2] = (data.size ushr 8).toByte()
    len[3] = data.size.toByte()
    out.write(len)
    out.write(tb)
    out.write(data)
    val crc = CRC32()
    crc.update(tb)
    crc.update(data)
    val c = crc.value.toInt()
    out.write(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte()))
}

fun main(args: Array<String>) {
    val phrase = arg(args, "--seed", "heron-bay-8")
    val w = arg(args, "--w", "384").toInt()
    val h = arg(args, "--h", "216").toInt()
    val detail = arg(args, "--detail", "1").toDouble()
    val scale = arg(args, "--scale", "1").toInt()
    val out = arg(args, "--out", "/tmp/island.png")
    val fixedDay = args.contains("--fixed-day")
    val fps = 30.0
    val t0 = System.nanoTime()
    val theme = dev.almanac.island.Theme.fromId(arg(args, "--theme", "original"))
    val scene = IslandScene(phrase, w, h, detail, theme = theme)
    val built = (System.nanoTime() - t0) / 1e6
    if (args.contains("--noise")) {
        for ((x, y, sd, oct) in listOf(
            doubleArrayOf(3.7, 1.2, 7.0, 1.0), doubleArrayOf(10.5, 4.25, 61159.0, 3.0),
            doubleArrayOf(0.5, 0.5, 0.0, 4.0), doubleArrayOf(123.4, 56.7, 42.0, 3.0), doubleArrayOf(200.1, 100.9, 999.0, 2.0),
        )) {
            println("valueNoise ${x} ${y} ${sd.toInt()} = ${"%.6f".format(dev.almanac.island.valueNoise(x, y, sd.toInt()))}")
            println("fractalNoise ${x} ${y} ${sd.toInt()} ${oct.toInt()} = ${"%.6f".format(dev.almanac.island.fractalNoise(x, y, sd.toInt(), oct.toInt()))}")
        }
        println("hash32(1) = ${dev.almanac.island.hash32(1).toLong() and 0xFFFFFFFFL}")
        println("indexRandom(7,42) = ${"%.6f".format(dev.almanac.island.indexRandom(7, 42))}")
        println("latticeRandom(3,4,5) = ${"%.6f".format(dev.almanac.island.latticeRandom(3, 4, 5))}")
        val r = dev.almanac.island.SeededRandom("heron-bay-8")
        println("seeded " + List(3) { "%.6f".format(r.next()) }.joinToString(" "))
        println("seedOf('heron-bay-8') = ${dev.almanac.island.seedOf("heron-bay-8")}")
        for (k in 0 until 3) {
            val rr = dev.almanac.island.SeededRandom(61159 + k)
            println("seeded61159 " + List(2) { "%.6f".format(rr.next()) }.joinToString(" "))
        }
        return
    }
    if (args.contains("--colours")) {
        val pal = dev.almanac.island.Palette()
        for (h in listOf("#caa35c", "#8d6b4b", "#ead9a2", "#74c25a", "#ff8000")) {
            val raw = dev.almanac.island.hex(h)
            println("$h raw #%06x graded #%06x".format(raw and 0xFFFFFF, pal.hex(h) and 0xFFFFFF))
        }
        val g = dev.almanac.island.gradeColour(dev.almanac.island.hex("#caa35c"), dev.almanac.island.LOOK)
        println("gradeColour direct #%06x".format(g and 0xFFFFFF))
        val hsl = dev.almanac.island.rgbToHsl(dev.almanac.island.hex("#caa35c"))
        println("hsl ${hsl[0]} ${hsl[1]} ${hsl[2]} -> #%06x".format(dev.almanac.island.hslToRgb(hsl[0], hsl[1], hsl[2]) and 0xFFFFFF))
        return
    }
    if (args.contains("--no-water")) scene.drawWater = false
    if (args.contains("--no-life")) scene.drawLifeFlag = false
    if (args.contains("--no-shadows")) scene.drawShadowsFlag = false
    if (args.contains("--no-grade")) scene.drawGradeFlag = false
    if (args.contains("--house")) {
        // the first house sprite, as characters: one letter a colour
        val h = scene.world.houses[0]
        val sp = scene.world.houseSprite(h)
        val seen = LinkedHashMap<Int, Char>()
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        for (y in 0 until sp.h) {
            for (x in 0 until sp.w) {
                val c = sp.px[y * sp.w + x]
                if (c == 0) { sb.append('.'); continue }
                sb.append(seen.getOrPut(c) { alphabet[seen.size % alphabet.length] })
            }
            sb.append('\n')
        }
        println("house ${h.w}x${h.depth} hw ${h.hw} kind ${h.kind} style ${h.style} village ${h.village} roof #%06x ax ${sp.ax} ay ${sp.ay}".format(h.roof and 0xFFFFFF))
        for ((c, ch) in seen) println("  $ch = #%06x".format(c and 0xFFFFFF))
        println(sb)
    }
    val t = arg(args, "--t", "13").toDouble()
    val steps = (t * fps).toInt()
    repeat(steps) { scene.step(1.0 / fps) }
    if (fixedDay) scene.setTimeOfDay(arg(args, "--tod", "0.3").toDouble())
    // a wallpaper draws frame after frame; --frames n draws n of them and keeps the last, as the wallpaper would
    val frames = arg(args, "--frames", "1").toInt()
    var renderMs = 0.0
    for (f in 0 until maxOf(1, frames)) {
        if (f > 0) scene.step(1.0 / fps)
        val t1 = System.nanoTime()
        scene.render()
        renderMs = (System.nanoTime() - t1) / 1e6
    }
    writePng(out, w, h, scene.frame, scale)
    if (args.contains("--debug")) println(scene.world.report().joinToString("\n"))
    if (args.contains("--people")) {
        // where each walker stands: world cell, the screen point it is stamped at, and how far above that its feet are
        for (p in scene.people) {
            val k = Math.round(p.s).toInt().coerceIn(0, p.line.size - 1)
            val x = p.line[k][0]
            val y = p.line[k][1] - 1
            val g = scene.ground(x, y)
            val sp = scene.personSpriteOf(p)
            println("person ${p.id}: world $x,$y screen ${g[0]},${g[1]} sprite ${sp.w}x${sp.h} anchor ${sp.ax},${sp.ay}")
        }
    }
    if (args.contains("--sprites")) {
        // a sheet of the small sprites, on a checker so transparency shows
        val wd = scene.world
        val sheet = IntArray(384 * 216) { i ->
            val x = i % 384
            val y = i / 384
            if (((x / 4) + (y / 4)) % 2 == 0) 0xFF2a2b3c.toInt() else 0xFF40404e.toInt()
        }
        val surf = dev.almanac.island.Surface(384, 216, sheet)
        var x = 40
        var y = 40
        wd.houses.take(6).forEach { h ->
            val sp = wd.houseSprite(h)
            dev.almanac.island.blitSprite(surf, sp, x.toDouble(), y.toDouble())
            x += 56
        }
        x = 40
        y = 110
        wd.trees.take(8).forEach { tr ->
            tr.sprite?.let {
                dev.almanac.island.blitSprite(surf, it, x.toDouble(), y.toDouble())
                x += 40
            }
        }
        val h = wd.houses[0]
        println("house ${h.w}x${h.depth} hw ${h.hw} kind ${h.kind} style ${h.style} roof #%06x".format(h.roof and 0xFFFFFF))
        val sp0 = wd.houseSprite(h)
        println("sprite ${sp0.w}x${sp0.h} ax ${sp0.ax} ay ${sp0.ay} roofTop ${sp0.roofTop}")
        val rowsDrawn = (0 until sp0.h).count { y -> (0 until sp0.w).any { x -> sp0.px[y * sp0.w + x] != 0 } }
        println("rows with pixels: $rowsDrawn of ${sp0.h}")
        val tr = wd.trees.first { it.sprite != null }
        println("tree at ${tr.x},${tr.y} size ${tr.size} sprite ${tr.sprite!!.w}x${tr.sprite!!.h}")
        wd.post.let { dev.almanac.island.blitSprite(surf, it, 340.0, 110.0) }
        wd.tower?.let { dev.almanac.island.blitSprite(surf, it, 340.0, 150.0) }
        writePng(arg(args, "--out", "/tmp/sprites.png"), 384, 216, sheet, scale)
        println("sprites written")
        return
    }
    if (args.contains("--map")) {
        val wd = scene.world
        val sb = StringBuilder()
        sb.append("seed ${wd.seed} cut ${"%.4f".format(wd.cut)} step ${"%.4f".format(wd.step)}\n")
        var y = 0
        while (y < wd.GH) {
            var x = 0
            while (x < wd.W) {
                val l = wd.LEV[wd.at(x, y)]
                sb.append(if (l < 0) '.' else ('0' + l))
                x += 4
            }
            sb.append('\n')
            y += 8
        }
        println(sb)
    }
    if (args.contains("--palette")) {
        val p = scene.palette
        for ((k, v) in listOf("sand" to p.sand, "bank" to p.bank, "earth" to p.earth, "dirt" to p.dirt, "lush" to p.lush, "clay0" to p.clay[0]))
            println("$k #%06x".format(v and 0xFFFFFF))
    }
    val report = buildString {
        appendLine("seed ${scene.seed} \"$phrase\" ${w}x${h} detail $detail")
        appendLine("${scene.world.houses.size} houses, ${scene.world.trees.size} trees, ${scene.world.palms.size} palms, ${scene.world.reeds.size} reeds, ${scene.world.roads.size} roads")
        appendLine("built in ${"%.1f".format(built)} ms, one frame in ${"%.1f".format(renderMs)} ms, written $out")
    }
    print(report)
    if (args.contains("--probe")) {
        val wd = scene.world
        val nums = args.drop(args.indexOf("--probe") + 1).takeWhile { !it.startsWith("--") }.map { it.toInt() }
        for (k in nums.indices step 2) {
            val x = nums[k]
            val y = nums[k + 1]
            val i = wd.at(x, y)
            println("world $x,$y E=${"%.4f".format(wd.E[i])} cut=${"%.4f".format(wd.cut)} LEV=${wd.LEV[i]} dSea=${wd.dSea[i]} dLand=${wd.dLand[i]} riv=${wd.riv[i]} res=${wd.res[i]} field=${wd.field[i]} path=${wd.path[i]}")
            println("   " + scene.describe(x, y))
        }
        return
    }
    if (args.contains("--bench")) {
        val n = arg(args, "--frames", "60").toInt()
        val times = DoubleArray(n)
        for (k in 0 until n) {
            scene.step(1.0 / 30)
            val a = System.nanoTime()
            scene.render()
            times[k] = (System.nanoTime() - a) / 1e6
        }
        times.sort()
        println("render ${w}x${h} detail $detail: min ${"%.1f".format(times[0])} ms, median ${"%.1f".format(times[n / 2])} ms, max ${"%.1f".format(times[n - 1])} ms")
        val steps = DoubleArray(n)
        for (k in 0 until n) {
            val a = System.nanoTime()
            scene.step(1.0 / 30)
            steps[k] = (System.nanoTime() - a) / 1e6
        }
        steps.sort()
        println("step: median ${"%.2f".format(steps[n / 2])} ms")
        return
    }
}
