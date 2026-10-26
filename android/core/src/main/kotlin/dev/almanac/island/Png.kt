// A PNG read, for the tools and for the baked maps: no dependencies, so the engine still builds anywhere. Only what a
// background needs: 8-bit channels, any colour type, no interlacing.
package dev.almanac.island

import java.io.File
import java.util.zip.Inflater

class PngImage(val w: Int, val h: Int, val px: IntArray)

/** Reads a PNG into packed 0xAARRGGBB pixels. Throws if the file is not one this reader understands. */
fun readPng(path: String): PngImage = readPng(File(path).readBytes())

fun readPng(bytes: ByteArray): PngImage {
    require(bytes.size > 8 && bytes[0] == 137.toByte() && bytes[1] == 80.toByte()) { "not a PNG" }
    var i = 8
    var w = 0
    var h = 0
    var depth = 0
    var ctype = 0
    var interlace = 0
    val idat = java.io.ByteArrayOutputStream()
    while (i + 8 <= bytes.size) {
        val n = be32(bytes, i)
        val tag = String(bytes, i + 4, 4, Charsets.US_ASCII)
        if (tag == "IHDR") {
            w = be32(bytes, i + 8)
            h = be32(bytes, i + 12)
            depth = bytes[i + 16].toInt() and 255
            ctype = bytes[i + 17].toInt() and 255
            interlace = bytes[i + 20].toInt() and 255
        } else if (tag == "IDAT") {
            idat.write(bytes, i + 8, n)
        } else if (tag == "IEND") {
            break
        }
        i += 12 + n
    }
    require(depth == 8) { "only 8-bit channels" }
    require(interlace == 0) { "interlaced PNGs are not read here" }
    val ch = when (ctype) {
        0 -> 1
        2 -> 3
        4 -> 2
        6 -> 4
        else -> throw IllegalArgumentException("colour type $ctype")
    }
    val raw = inflate(idat.toByteArray())
    val stride = w * ch
    val out = ByteArray(h * stride)
    val prev = ByteArray(stride)
    var o = 0
    for (y in 0 until h) {
        val f = raw[o].toInt() and 255
        o++
        val line = raw.copyOfRange(o, o + stride)
        o += stride
        when (f) {
            1 -> for (x in ch until stride) line[x] = (line[x] + line[x - ch]).toByte()
            2 -> for (x in 0 until stride) line[x] = (line[x] + prev[x]).toByte()
            3 -> for (x in 0 until stride) {
                val a = if (x >= ch) line[x - ch].toInt() and 255 else 0
                line[x] = (line[x] + ((a + (prev[x].toInt() and 255)) shr 1)).toByte()
            }
            4 -> for (x in 0 until stride) {
                val a = if (x >= ch) line[x - ch].toInt() and 255 else 0
                val b = prev[x].toInt() and 255
                val c = if (x >= ch) prev[x - ch].toInt() and 255 else 0
                val pa = kotlin.math.abs(b - c)
                val pb = kotlin.math.abs(a - c)
                val pc = kotlin.math.abs(a + b - 2 * c)
                val pr = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                line[x] = (line[x] + pr).toByte()
            }
        }
        System.arraycopy(line, 0, out, y * stride, stride)
        System.arraycopy(line, 0, prev, 0, stride)
    }
    val px = IntArray(w * h)
    for (k in 0 until w * h) {
        val r: Int
        val g: Int
        val b: Int
        when (ch) {
            1 -> {
                val v = out[k].toInt() and 255
                r = v; g = v; b = v
            }
            2 -> {
                val v = out[k * 2].toInt() and 255
                r = v; g = v; b = v
            }
            3 -> {
                r = out[k * 3].toInt() and 255
                g = out[k * 3 + 1].toInt() and 255
                b = out[k * 3 + 2].toInt() and 255
            }
            else -> {
                r = out[k * 4].toInt() and 255
                g = out[k * 4 + 1].toInt() and 255
                b = out[k * 4 + 2].toInt() and 255
            }
        }
        px[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return PngImage(w, h, px)
}

private fun be32(b: ByteArray, at: Int): Int =
    ((b[at].toInt() and 255) shl 24) or ((b[at + 1].toInt() and 255) shl 16) or
        ((b[at + 2].toInt() and 255) shl 8) or (b[at + 3].toInt() and 255)

private fun inflate(data: ByteArray): ByteArray {
    val inf = Inflater()
    inf.setInput(data)
    val out = java.io.ByteArrayOutputStream(data.size * 4)
    val buf = ByteArray(65536)
    while (!inf.finished()) {
        val n = inf.inflate(buf)
        if (n == 0 && inf.needsInput()) break
        out.write(buf, 0, n)
    }
    inf.end()
    return out.toByteArray()
}
