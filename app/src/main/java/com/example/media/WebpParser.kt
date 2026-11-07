package com.example.media

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WebpInfo(
    val isWebp: Boolean,
    val isAnimated: Boolean,
    val hasAlpha: Boolean,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val totalDurationMs: Long,
    val frameDurationsMs: List<Int>
)

object WebpParser {

    fun parse(file: File): WebpInfo {
        return file.inputStream().use { parse(it) }
    }

    fun parse(stream: InputStream): WebpInfo {
        val bytes = stream.readBytes()
        if (bytes.size < 12) {
            return WebpInfo(isWebp = false, isAnimated = false, hasAlpha = false, width = 0, height = 0, frameCount = 0, totalDurationMs = 0L, frameDurationsMs = emptyList())
        }

        // RIFF header
        val isRiff = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        val isWebp = bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()

        if (!isRiff || !isWebp) {
            return WebpInfo(isWebp = false, isAnimated = false, hasAlpha = false, width = 0, height = 0, frameCount = 0, totalDurationMs = 0L, frameDurationsMs = emptyList())
        }

        var offset = 12
        var isAnimated = false
        var hasAlpha = false
        var width = 0
        var height = 0
        var frameCount = 0
        var totalDuration = 0L
        val frameDurations = mutableListOf<Int>()

        while (offset + 8 <= bytes.size) {
            val chunkFourCC = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val payloadOffset = offset + 8

            if (payloadOffset + chunkSize > bytes.size) {
                break
            }

            when (chunkFourCC) {
                "VP8X" -> {
                    if (chunkSize >= 10) {
                        val flags = bytes[payloadOffset].toInt()
                        isAnimated = (flags and 0x02) != 0
                        hasAlpha = (flags and 0x10) != 0

                        // 24-bit canvas width (1-based: 1 + value)
                        val w0 = bytes[payloadOffset + 4].toInt() and 0xFF
                        val w1 = bytes[payloadOffset + 5].toInt() and 0xFF
                        val w2 = bytes[payloadOffset + 6].toInt() and 0xFF
                        width = 1 + (w0 or (w1 shl 8) or (w2 shl 16))

                        // 24-bit canvas height (1-based: 1 + value)
                        val h0 = bytes[payloadOffset + 7].toInt() and 0xFF
                        val h1 = bytes[payloadOffset + 8].toInt() and 0xFF
                        val h2 = bytes[payloadOffset + 9].toInt() and 0xFF
                        height = 1 + (h0 or (h1 shl 8) or (h2 shl 16))
                    }
                }
                "VP8 " -> {
                    // Simple lossy WebP (no VP8X chunk). The first byte of the
                    // VP8 frame tag carries the key-frame flag; bytes 3..5 are
                    // the 0x9d 0x01 0x2a start code. The previous implementation
                    // tested the start-code byte instead of the frame tag, so
                    // opaque WebP files (the common case for photos) were
                    // reported as 0x0 and failed WhatsApp verification.
                    if (chunkSize >= 10 && width == 0) {
                        val frameTag = bytes[payloadOffset].toInt() and 0xFF
                        val keyframe = (frameTag and 0x01) == 0
                        val hasStartCode = (bytes[payloadOffset + 3].toInt() and 0xFF) == 0x9D &&
                            (bytes[payloadOffset + 4].toInt() and 0xFF) == 0x01 &&
                            (bytes[payloadOffset + 5].toInt() and 0xFF) == 0x2A
                        if (keyframe && hasStartCode) {
                            val w = (bytes[payloadOffset + 6].toInt() and 0xFF) or
                                    ((bytes[payloadOffset + 7].toInt() and 0x3F) shl 8)
                            val h = (bytes[payloadOffset + 8].toInt() and 0xFF) or
                                    ((bytes[payloadOffset + 9].toInt() and 0x3F) shl 8)
                            width = w
                            height = h
                        }
                    }
                }
                "VP8L" -> {
                    // Lossless WebP
                    hasAlpha = true
                    if (chunkSize >= 5 && width == 0) {
                        val b1 = bytes[payloadOffset + 1].toInt() and 0xFF
                        val b2 = bytes[payloadOffset + 2].toInt() and 0xFF
                        val b3 = bytes[payloadOffset + 3].toInt() and 0xFF
                        val b4 = bytes[payloadOffset + 4].toInt() and 0xFF
                        width = 1 + (((b2 and 0x3F) shl 8) or b1)
                        height = 1 + (((b4 and 0x0F) shl 10) or (b3 shl 2) or ((b2 and 0xC0) shr 6))
                    }
                }
                "ANMF" -> {
                    frameCount++
                    isAnimated = true
                    if (chunkSize >= 16) {
                        // Duration is 24-bit unsigned int at offset 12 in ANMF payload
                        val d0 = bytes[payloadOffset + 12].toInt() and 0xFF
                        val d1 = bytes[payloadOffset + 13].toInt() and 0xFF
                        val d2 = bytes[payloadOffset + 14].toInt() and 0xFF
                        val dur = d0 or (d1 shl 8) or (d2 shl 16)
                        frameDurations.add(dur)
                        totalDuration += dur
                    }
                }
            }

            // RIFF chunks are padded to even 16-bit boundaries
            val paddedSize = if ((chunkSize % 2L) != 0L) chunkSize + 1 else chunkSize
            offset += 8 + paddedSize.toInt()
        }

        if (frameCount == 0 && isAnimated) {
            frameCount = 1
        }

        return WebpInfo(
            isWebp = true,
            isAnimated = isAnimated,
            hasAlpha = hasAlpha,
            width = width,
            height = height,
            frameCount = frameCount,
            totalDurationMs = totalDuration,
            frameDurationsMs = frameDurations
        )
    }
}
