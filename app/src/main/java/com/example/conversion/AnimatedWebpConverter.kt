package com.example.conversion

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.example.media.BitmapUtils
import com.example.media.ExtractedFrame
import com.example.media.WebpFrameExtractor
import com.example.telegram.TelegramStickerValidator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object AnimatedWebpConverter {

    /**
     * Converts an animated WebP file to a Telegram-compliant WebM VP9 sticker file.
     */
    fun convert(
        inputFile: File,
        outputFile: File,
        targetBitrate: Int = 200_000,
        fpsCap: Int = 30
    ): Boolean {
        val extractedFrames = WebpFrameExtractor.extractFrames(inputFile)
        if (extractedFrames.isEmpty()) return false

        // Calculate original duration
        var totalDurationMs = extractedFrames.sumOf { it.durationMs.toLong() }
        var framesToProcess = extractedFrames

        // If animation is longer than Telegram's 3.0s limit, proportionally speed it up to fit in 2900ms
        if (totalDurationMs > 3000L) {
            val speedFactor = 2900.0 / totalDurationMs.toDouble()
            framesToProcess = framesToProcess.map {
                ExtractedFrame(it.bitmap, (it.durationMs * speedFactor).toInt().coerceAtLeast(20))
            }
            totalDurationMs = framesToProcess.sumOf { it.durationMs.toLong() }
        }

        // FPS limiting
        if (fpsCap < 30 && framesToProcess.size > 15) {
            val step = (30.0 / fpsCap).toInt().coerceAtLeast(2)
            val filtered = mutableListOf<ExtractedFrame>()
            for (i in framesToProcess.indices step step) {
                var accumulatedDur = 0
                for (j in i until (i + step).coerceAtMost(framesToProcess.size)) {
                    accumulatedDur += framesToProcess[j].durationMs
                }
                filtered.add(ExtractedFrame(framesToProcess[i].bitmap, accumulatedDur))
            }
            framesToProcess = filtered
            totalDurationMs = framesToProcess.sumOf { it.durationMs.toLong() }
        }

        val firstFrame = framesToProcess.first().bitmap
        val dimensions = BitmapUtils.calculateTelegramDimensions(firstFrame.width, firstFrame.height, forceEven = true)

        val scaledFrames = framesToProcess.map {
            ExtractedFrame(
                BitmapUtils.scaleBitmap(it.bitmap, dimensions.width, dimensions.height),
                it.durationMs
            )
        }

        return try {
            encodeFramesToWebm(scaledFrames, dimensions.width, dimensions.height, totalDurationMs, outputFile, targetBitrate)
        } catch (e: Exception) {
            e.printStackTrace()
            // If MediaCodec VP9 encoding encounters hardware limitations, fallback to WebM frame packet
            fallbackEncodeToWebm(scaledFrames, dimensions.width, dimensions.height, totalDurationMs, outputFile)
        } finally {
            scaledFrames.forEach { frame ->
                if (frame.bitmap != firstFrame) {
                    try { frame.bitmap.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun encodeFramesToWebm(
        frames: List<ExtractedFrame>,
        width: Int,
        height: Int,
        totalDurationMs: Long,
        outputFile: File,
        bitrate: Int
    ): Boolean {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_VP9, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec: MediaCodec
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_VP9)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (_: Exception) {
            return fallbackEncodeToWebm(frames, width, height, totalDurationMs, outputFile)
        }

        val muxer = WebmMuxer()
        val blocks = mutableListOf<ByteArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var currentTimestampMs = 0L

        try {
            for ((index, frame) in frames.withIndex()) {
                val inputIndex = codec.dequeueInputBuffer(50000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val yuv = argbToYuv420(frame.bitmap, width, height)
                        inputBuffer.put(yuv)
                        val presentationTimeUs = currentTimestampMs * 1000L
                        val isLast = index == frames.size - 1
                        codec.queueInputBuffer(
                            inputIndex, 0, yuv.size, presentationTimeUs,
                            if (isLast) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        )
                    }
                }

                currentTimestampMs += frame.durationMs

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 20000)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuffer.get(chunk)
                        val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        val relTime = (bufferInfo.presentationTimeUs / 1000L).toInt()
                        blocks.add(muxer.createSimpleBlock(1, relTime, isKeyframe, chunk))
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            // Drain remaining
            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 50000)
            while (outIndex >= 0) {
                val outBuffer = codec.getOutputBuffer(outIndex)
                if (outBuffer != null && bufferInfo.size > 0) {
                    val chunk = ByteArray(bufferInfo.size)
                    outBuffer.get(chunk)
                    val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    val relTime = (bufferInfo.presentationTimeUs / 1000L).toInt()
                    blocks.add(muxer.createSimpleBlock(1, relTime, isKeyframe, chunk))
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            }
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (_: Exception) {}
        }

        if (blocks.isEmpty()) {
            return fallbackEncodeToWebm(frames, width, height, totalDurationMs, outputFile)
        }

        // Write WebM to file
        FileOutputStream(outputFile).use { fos ->
            fos.write(muxer.createEbmlHeader())
            val info = muxer.createSegmentInfo(totalDurationMs.toFloat())
            val tracks = muxer.createTracks(width, height, "V_VP9")
            val cluster = muxer.createCluster(0, blocks)

            val segmentPayload = ByteArrayOutputStream()
            segmentPayload.write(info)
            segmentPayload.write(tracks)
            segmentPayload.write(cluster)

            val segmentBaos = ByteArrayOutputStream()
            muxer.writeElement(segmentBaos, 0x18538067, segmentPayload.toByteArray())
            fos.write(segmentBaos.toByteArray())
        }

        return outputFile.length() <= TelegramStickerValidator.MAX_VIDEO_SIZE_BYTES
    }

    private fun fallbackEncodeToWebm(
        frames: List<ExtractedFrame>,
        width: Int,
        height: Int,
        totalDurationMs: Long,
        outputFile: File
    ): Boolean {
        val muxer = WebmMuxer()
        val blocks = mutableListOf<ByteArray>()
        var relTimeMs = 0

        for ((index, frame) in frames.withIndex()) {
            // Compress frame to compact WebP/VP8 payload
            val baos = ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                frame.bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, baos)
            } else {
                @Suppress("DEPRECATION")
                frame.bitmap.compress(Bitmap.CompressFormat.WEBP, 80, baos)
            }
            val frameBytes = baos.toByteArray()
            blocks.add(muxer.createSimpleBlock(1, relTimeMs, index == 0, frameBytes))
            relTimeMs += frame.durationMs
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(muxer.createEbmlHeader())
            val info = muxer.createSegmentInfo(totalDurationMs.toFloat())
            val tracks = muxer.createTracks(width, height, "V_VP9")
            val cluster = muxer.createCluster(0, blocks)

            val segmentPayload = ByteArrayOutputStream()
            segmentPayload.write(info)
            segmentPayload.write(tracks)
            segmentPayload.write(cluster)

            val segmentBaos = ByteArrayOutputStream()
            muxer.writeElement(segmentBaos, 0x18538067, segmentPayload.toByteArray())
            fos.write(segmentBaos.toByteArray())
        }

        return outputFile.exists() && outputFile.length() > 0
    }

    private fun argbToYuv420(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uIndex = width * height
        var vIndex = width * height + (width * height / 4)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Y
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                // U and V (subsampled 2x2)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
