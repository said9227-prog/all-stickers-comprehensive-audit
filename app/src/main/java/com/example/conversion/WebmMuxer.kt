package com.example.conversion

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WebmMuxer {

    fun writeEbmlId(out: OutputStream, id: Long) {
        val bytes = when {
            id > 0xFFFFFFL -> byteArrayOf(
                ((id shr 24) and 0xFF).toByte(),
                ((id shr 16) and 0xFF).toByte(),
                ((id shr 8) and 0xFF).toByte(),
                (id and 0xFF).toByte()
            )
            id > 0xFFFFL -> byteArrayOf(
                ((id shr 16) and 0xFF).toByte(),
                ((id shr 8) and 0xFF).toByte(),
                (id and 0xFF).toByte()
            )
            id > 0xFFL -> byteArrayOf(
                ((id shr 8) and 0xFF).toByte(),
                (id and 0xFF).toByte()
            )
            else -> byteArrayOf((id and 0xFF).toByte())
        }
        out.write(bytes)
    }

    fun writeVint(out: OutputStream, value: Long) {
        when {
            value < 0x7FL -> {
                out.write((0x80L or value).toInt())
            }
            value < 0x3FFFL -> {
                out.write((0x40L or (value shr 8)).toInt())
                out.write((value and 0xFF).toInt())
            }
            value < 0x1FFFFFL -> {
                out.write((0x20L or (value shr 16)).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            value < 0x0FFFFFFFL -> {
                out.write((0x10L or (value shr 24)).toInt())
                out.write(((value shr 16) and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            else -> {
                out.write((0x08L or (value shr 32)).toInt())
                out.write(((value shr 24) and 0xFF).toInt())
                out.write(((value shr 16) and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
        }
    }

    fun writeElement(out: OutputStream, id: Long, data: ByteArray) {
        writeEbmlId(out, id)
        writeVint(out, data.size.toLong())
        out.write(data)
    }

    fun writeStringElement(out: OutputStream, id: Long, str: String) {
        writeElement(out, id, str.toByteArray(Charsets.US_ASCII))
    }

    fun writeUintElement(out: OutputStream, id: Long, value: Long) {
        val bytes = when {
            value <= 0xFFL -> byteArrayOf((value and 0xFF).toByte())
            value <= 0xFFFFL -> byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
            value <= 0xFFFFFFL -> byteArrayOf(
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
            else -> ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array()
        }
        writeElement(out, id, bytes)
    }

    fun writeFloatElement(out: OutputStream, id: Long, value: Float) {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()
        writeElement(out, id, bytes)
    }

    fun createEbmlHeader(): ByteArray {
        val baos = ByteArrayOutputStream()
        writeUintElement(baos, 0x4286, 1) // EBMLVersion
        writeUintElement(baos, 0x42F7, 1) // EBMLReadVersion
        writeUintElement(baos, 0x42F2, 4) // EBMLMaxIDLength
        writeUintElement(baos, 0x42F3, 8) // EBMLMaxSizeLength
        writeStringElement(baos, 0x4282, "webm") // DocType
        writeUintElement(baos, 0x4287, 4) // DocTypeVersion
        writeUintElement(baos, 0x4285, 2) // DocTypeReadVersion

        val headerBaos = ByteArrayOutputStream()
        writeElement(headerBaos, 0x1A45DFA3, baos.toByteArray())
        return headerBaos.toByteArray()
    }

    fun createSegmentInfo(durationMs: Float): ByteArray {
        val baos = ByteArrayOutputStream()
        writeUintElement(baos, 0x2AD7B1, 1000000L) // TimecodeScale = 1ms
        writeStringElement(baos, 0x4D80, "AllStickers") // MuxingApp
        writeStringElement(baos, 0x5741, "AllStickers") // WritingApp
        writeFloatElement(baos, 0x4489, durationMs) // Duration

        val infoBaos = ByteArrayOutputStream()
        writeElement(infoBaos, 0x1549A966, baos.toByteArray())
        return infoBaos.toByteArray()
    }

    fun createTracks(width: Int, height: Int, codecId: String = "V_VP9"): ByteArray {
        val videoBaos = ByteArrayOutputStream()
        writeUintElement(videoBaos, 0xB0, width.toLong()) // PixelWidth
        writeUintElement(videoBaos, 0xBA, height.toLong()) // PixelHeight
        writeUintElement(videoBaos, 0x53C0, 1) // AlphaMode = 1 (Present)

        val trackBaos = ByteArrayOutputStream()
        writeUintElement(trackBaos, 0xD7, 1) // TrackNumber = 1
        writeUintElement(trackBaos, 0x73C5, 1) // TrackUID = 1
        writeUintElement(trackBaos, 0x83, 1) // TrackType = Video (1)
        writeStringElement(trackBaos, 0x86, codecId) // CodecID

        writeElement(trackBaos, 0xE0, videoBaos.toByteArray()) // Video Settings

        val tracksBaos = ByteArrayOutputStream()
        writeElement(tracksBaos, 0xAE, trackBaos.toByteArray()) // TrackEntry

        val containerBaos = ByteArrayOutputStream()
        writeElement(containerBaos, 0x1654AE6B, tracksBaos.toByteArray()) // Tracks
        return containerBaos.toByteArray()
    }

    fun createSimpleBlock(trackNumber: Int, relativeTimecodeMs: Int, isKeyframe: Boolean, frameData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream(frameData.size + 8)
        // Track number as VINT: 1 -> 0x81
        baos.write(0x80 or (trackNumber and 0x7F))
        // Relative timecode (signed int16 big-endian)
        baos.write((relativeTimecodeMs shr 8) and 0xFF)
        baos.write(relativeTimecodeMs and 0xFF)
        // Flags: Keyframe flag is 0x80
        val flags = if (isKeyframe) 0x80 else 0x00
        baos.write(flags)
        // Payload
        baos.write(frameData)

        val blockBaos = ByteArrayOutputStream(baos.size() + 8)
        writeElement(blockBaos, 0xA3, baos.toByteArray())
        return blockBaos.toByteArray()
    }

    fun createCluster(timecodeMs: Long, blocks: List<ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        writeUintElement(baos, 0xE7, timecodeMs) // Cluster Timecode
        blocks.forEach { baos.write(it) }

        val clusterBaos = ByteArrayOutputStream(baos.size() + 8)
        writeElement(clusterBaos, 0x1F43B675, baos.toByteArray())
        return clusterBaos.toByteArray()
    }
}
