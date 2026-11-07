package com.example.media

import android.graphics.Bitmap
import android.graphics.Color
import java.io.OutputStream
import java.util.zip.Deflater

/**
 * A single frame of an animated GIF.
 */
data class GifFrame(
    val bitmap: Bitmap,
    val delayMs: Int = 100
)

/**
 * Pure-Kotlin animated GIF encoder (no native dependencies).
 *
 * Builds a global palette from the frames, quantizes each frame to palette
 * indices, LZW-compresses the pixel data and writes a standards-compliant
 * GIF89a with per-frame Graphic Control Extension (delay + disposal).
 *
 * Usage (on a background coroutine):
 *     AnimatedGifEncoder.encode(frames, outputStream)
 */
object AnimatedGifEncoder {

    private const val MAX_COLORS = 256
    private const val GRID_LEVELS = 6 // per channel for the reverse color lookup grid

    /**
     * Encodes [frames] into a GIF written to [output].
     * Returns the number of frames written.
     */
    fun encode(frames: List<GifFrame>, output: OutputStream): Int {
        if (frames.isEmpty()) return 0
        output.use { out ->
            val first = frames.first()
            val width = first.bitmap.width
            val height = first.bitmap.height
            val palette = buildPalette(frames)

            writeHeader(out)
            writeLogicalScreenDescriptor(out, width, height, palette.size)
            writeGlobalColorTable(out, palette)
            writeNetscapeExtension(out) // looping animation

            for ((index, frame) in frames.withIndex()) {
                val indices = quantize(frame.bitmap, palette)
                writeGraphicControlExtension(out, frame.delayMs, isLast = index == frames.lastIndex)
                writeImageDescriptor(out, 0, 0, width, height)
                writeImageData(out, indices, palette.size)
            }

            writeTrailer(out)
            out.flush()
        }
        return frames.size
    }

    // ------------------------------------------------------------------
    // Palette building
    // ------------------------------------------------------------------

    private fun buildPalette(frames: List<GifFrame>): IntArray {
        // Coarse 3D histogram over the frames to find popular colors.
        val bins = IntArray(GRID_LEVELS * GRID_LEVELS * GRID_LEVELS)
        val binColor = IntArray(bins.size)
        var binCount = 0

        for (frame in frames) {
            val bmp = frame.bitmap
            val w = bmp.width
            val h = bmp.height
            // Sample every pixel (could subsample large frames, but studio exports are small).
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val rgb = bmp.getPixel(x, y)
                    val bi = binIndex(rgb)
                    bins[bi]++
                    if (binColor[bi] == 0 && rgb != 0) binColor[bi] = rgb
                    if (binColor[bi] == 0) binColor[bi] = rgb
                    if (binCount == 0 || binColor[bi] != 0) {}
                }
            }
        }

        // Collect non-empty bins with their representative colors.
        val entries = mutableListOf<Pair<Int, Int>>() // (count, color)
        for (i in bins.indices) {
            if (bins[i] > 0) {
                val color = if (binColor[i] != 0) binColor[i] else nearestGridColor(i)
                entries += Pair(bins[i], color)
            }
        }
        entries.sortByDescending { it.first }

        val palette = IntArray(MAX_COLORS)
        var size = 0
        for ((_, color) in entries) {
            if (size >= MAX_COLORS) break
            palette[size] = color
            size++
        }
        // Ensure index 0 is transparent black (used for transparency disposal).
        palette[0] = 0
        return palette.copyOf(size.coerceAtLeast(1))
    }

    private fun binIndex(rgb: Int): Int {
        val r = (Color.red(rgb) * GRID_LEVELS / 256).coerceIn(0, GRID_LEVELS - 1)
        val g = (Color.green(rgb) * GRID_LEVELS / 256).coerceIn(0, GRID_LEVELS - 1)
        val b = (Color.blue(rgb) * GRID_LEVELS / 256).coerceIn(0, GRID_LEVELS - 1)
        return r * GRID_LEVELS * GRID_LEVELS + g * GRID_LEVELS + b
    }

    private fun nearestGridColor(bin: Int): Int {
        val levels = GRID_LEVELS
        val r = (bin / (levels * levels)) * 255 / (levels - 1)
        val g = ((bin % (levels * levels)) / levels) * 255 / (levels - 1)
        val b = (bin % levels) * 255 / (levels - 1)
        return Color.rgb(r, g, b)
    }

    // ------------------------------------------------------------------
    // Quantize a bitmap to palette indices
    // ------------------------------------------------------------------

    private fun quantize(bitmap: Bitmap, palette: IntArray): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val indices = ByteArray(w * h)
        // Build a reverse lookup grid: coarse RGB -> nearest palette index.
        val grid = IntArray(GRID_LEVELS * GRID_LEVELS * GRID_LEVELS) { -1 }
        for (i in palette.indices) {
            val bi = binIndex(palette[i])
            if (grid[bi] == -1) grid[bi] = i
        }
        // Fill empty grid cells by scanning palette (rare path).
        for (i in grid.indices) {
            if (grid[i] == -1) {
                grid[i] = nearestPaletteIndex(nearestGridColor(i), palette)
            }
        }
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                indices[idx] = grid[binIndex(bitmap.getPixel(x, y))].toByte()
                idx++
            }
        }
        return indices
    }

    private fun nearestPaletteIndex(color: Int, palette: IntArray): Int {
        var best = 0
        var bestDist = Int.MAX_VALUE
        val cr = Color.red(color)
        val cg = Color.green(color)
        val cb = Color.blue(color)
        for (i in palette.indices) {
            val d = (cr - Color.red(palette[i])).let { it * it } +
                (cg - Color.green(palette[i])).let { it * it } +
                (cb - Color.blue(palette[i])).let { it * it }
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    // ------------------------------------------------------------------
    // GIF writers
    // ------------------------------------------------------------------

    private fun writeHeader(out: OutputStream) {
        out.write("GIF89a".toByteArray())
    }

    private fun writeLogicalScreenDescriptor(out: OutputStream, w: Int, h: Int, paletteSize: Int) {
        writeLE16(out, w)
        writeLE16(out, h)
        // packed: global color table flag=1, color resolution=7, sort=0, size=paletteSize-1 bits
        val paletteBits = (32 - Integer.numberOfLeadingZeros(paletteSize - 1)).coerceAtLeast(1).coerceAtMost(8)
        val packed = 0x80 or ((7 - 1) shl 3) or (paletteBits - 1)
        out.write(packed)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio
    }

    private fun writeGlobalColorTable(out: OutputStream, palette: IntArray) {
        for (color in palette) {
            out.write(Color.red(color))
            out.write(Color.green(color))
            out.write(Color.blue(color))
        }
    }

    private fun writeNetscapeExtension(out: OutputStream) {
        out.write(0x21.toInt()) // extension introducer
        out.write(0xFF) // application extension
        out.write(11) // block size
        out.write("NETSCAPE2.0".toByteArray())
        out.write(3) // data sub-block size
        out.write(1)
        writeLE16(out, 0) // loop forever
        out.write(0) // block terminator
    }

    private fun writeGraphicControlExtension(out: OutputStream, delayMs: Int, isLast: Boolean) {
        out.write(0x21.toInt())
        out.write(0xF9) // graphic control label
        out.write(4) // block size
        // packed: disposal=2 (restore background), user input=0, transparent color=1 (index 0)
        var packed = 0x02 shl 2
        if (isLast) packed = packed or 0x01
        out.write(packed)
        writeLE16(out, (delayMs.coerceAtLeast(2) / 10).toInt()) // delay in 1/100s
        out.write(0) // transparent color index
        out.write(0) // block terminator
    }

    private fun writeImageDescriptor(out: OutputStream, x: Int, y: Int, w: Int, h: Int) {
        out.write(0x2C.toInt()) // image separator
        writeLE16(out, x)
        writeLE16(out, y)
        writeLE16(out, w)
        writeLE16(out, h)
        out.write(0) // packed: no local color table, not interlaced
    }

    private fun writeImageData(out: OutputStream, indices: ByteArray, paletteSize: Int) {
        val minCodeSize = (32 - Integer.numberOfLeadingZeros(paletteSize - 1)).coerceAtLeast(1).coerceAtMost(8)
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        val lzw = LzwEncoder(minCodeSize, clearCode, eoiCode)
        val compressed = lzw.encode(indices.toList().map { it.toInt() and 0xFF })
        out.write(minCodeSize)
        var pos = 0
        while (pos < compressed.size) {
            val chunk = (255.coerceAtMost(compressed.size - pos))
            out.write(chunk)
            out.write(compressed, pos, chunk)
            pos += chunk
        }
        out.write(0) // block terminator
    }

    private fun writeTrailer(out: OutputStream) {
        out.write(0x3B.toInt())
    }

    private fun writeLE16(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write(value shr 8 and 0xFF)
    }

    // ------------------------------------------------------------------
    // LZW encoder (variable bit width, LSB-first bit packing)
    // ------------------------------------------------------------------

    private class LzwEncoder(
        private val minCodeSize: Int,
        private val clearCode: Int,
        private val eoiCode: Int
    ) {
        private val table = mutableMapOf<Pair<Int, Int>, Int>()
        private var nextCode = eoiCode + 1
        private var codeSize = minCodeSize + 1
        private var accum = 0
        private var accumBits = 0
        private val out = mutableListOf<Byte>()

        fun encode(pixels: List<Int>): ByteArray {
            emitCode(clearCode)
            var prev = -1
            for (pixel in pixels) {
                val key = Pair(prev, pixel)
                if (prev == -1) {
                    prev = pixel
                    continue
                }
                if (table.containsKey(key)) {
                    prev = table[key]!!
                } else {
                    emitCode(prev)
                    if (nextCode < 4096) {
                        table[key] = nextCode
                        nextCode++
                        if (nextCode == (1 shl codeSize)) {
                            codeSize = (codeSize + 1).coerceAtMost(12)
                        }
                    }
                    prev = pixel
                }
            }
            if (prev != -1) emitCode(prev)
            emitCode(eoiCode)
            flushBits()
            return out.toByteArray()
        }

        private fun emitCode(code: Int) {
            accum = accum or (code shl accumBits)
            accumBits += codeSize
            while (accumBits >= 8) {
                out.add((accum and 0xFF).toByte())
                accum = accum ushr 8
                accumBits -= 8
            }
        }

        private fun flushBits() {
            if (accumBits > 0) {
                out.add((accum and 0xFF).toByte())
                accum = 0
                accumBits = 0
            }
        }
    }
}
