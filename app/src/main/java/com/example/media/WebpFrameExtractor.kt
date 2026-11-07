package com.example.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ExtractedFrame(
    val bitmap: Bitmap,
    val durationMs: Int
)

object WebpFrameExtractor {

    private fun read3BytesLittleEndian(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16)
    }

    fun extractFrames(webpFile: File): List<ExtractedFrame> {
        val fileBytes = webpFile.readBytes()
        if (fileBytes.size < 12) return emptyList()

        val webpInfo = WebpParser.parse(fileBytes.inputStream())
        if (!webpInfo.isAnimated) {
            // Static WebP: single frame
            val bmp = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size) ?: return emptyList()
            return listOf(ExtractedFrame(bmp, 1000))
        }

        val canvasW = if (webpInfo.width > 0) webpInfo.width else 512
        val canvasH = if (webpInfo.height > 0) webpInfo.height else 512

        val frames = mutableListOf<ExtractedFrame>()
        var offset = 12

        val canvasBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        while (offset + 8 <= fileBytes.size) {
            val chunkFourCC = String(fileBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(fileBytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val payloadOffset = offset + 8

            if (payloadOffset + chunkSize > fileBytes.size) break

            if (chunkFourCC == "ANMF" && chunkSize >= 16) {
                val frameX = read3BytesLittleEndian(fileBytes, payloadOffset) * 2
                val frameY = read3BytesLittleEndian(fileBytes, payloadOffset + 3) * 2
                val frameW = 1 + read3BytesLittleEndian(fileBytes, payloadOffset + 6)
                val frameH = 1 + read3BytesLittleEndian(fileBytes, payloadOffset + 9)
                val frameDuration = read3BytesLittleEndian(fileBytes, payloadOffset + 12).coerceAtLeast(20)
                val flags = fileBytes[payloadOffset + 15].toInt()
                val blend = (flags and 0x01) == 0 // true = blend, false = do not blend
                val disposeToBackground = (flags and 0x02) != 0 // true = dispose to background

                val subPayloadOffset = payloadOffset + 16
                val subPayloadSize = (chunkSize - 16).toInt()

                if (subPayloadSize > 0 && subPayloadOffset + subPayloadSize <= fileBytes.size) {
                    // Wrap subpayload into a standalone WebP RIFF container
                    val baos = ByteArrayOutputStream(subPayloadSize + 12)
                    baos.write("RIFF".toByteArray(Charsets.US_ASCII))
                    val riffSize = subPayloadSize + 4
                    baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(riffSize).array())
                    baos.write("WEBP".toByteArray(Charsets.US_ASCII))
                    baos.write(fileBytes, subPayloadOffset, subPayloadSize)

                    val wrappedBytes = baos.toByteArray()
                    val frameRawBitmap = BitmapFactory.decodeByteArray(wrappedBytes, 0, wrappedBytes.size)

                    if (frameRawBitmap != null) {
                        if (!blend) {
                            canvas.drawRect(
                                frameX.toFloat(), frameY.toFloat(),
                                (frameX + frameW).toFloat(), (frameY + frameH).toFloat(),
                                clearPaint
                            )
                        }

                        canvas.drawBitmap(frameRawBitmap, frameX.toFloat(), frameY.toFloat(), paint)

                        // Copy canvas frame
                        val outputFrameBitmap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        frames.add(ExtractedFrame(outputFrameBitmap, frameDuration))

                        if (disposeToBackground) {
                            canvas.drawRect(
                                frameX.toFloat(), frameY.toFloat(),
                                (frameX + frameW).toFloat(), (frameY + frameH).toFloat(),
                                clearPaint
                            )
                        }
                    }
                }
            }

            val paddedSize = if ((chunkSize % 2L) != 0L) chunkSize + 1 else chunkSize
            offset += 8 + paddedSize.toInt()
        }

        if (frames.isEmpty()) {
            val fallback = BitmapFactory.decodeFile(webpFile.absolutePath)
            if (fallback != null) {
                frames.add(ExtractedFrame(fallback, 1000))
            }
        }

        return frames
    }
}
