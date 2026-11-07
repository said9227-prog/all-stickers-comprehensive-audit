package com.example

import com.example.media.WebpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WebpParserTest {

    @Test
    fun `parses dimensions of simple lossy VP8 without VP8X`() {
        val info = WebpParser.parse(ByteArrayInputStream(simpleVp8Webp(512, 512)))

        assertTrue(info.isWebp)
        assertFalse(info.isAnimated)
        assertEquals(512, info.width)
        assertEquals(512, info.height)
    }

    private fun simpleVp8Webp(width: Int, height: Int): ByteArray {
        val vp8 = ByteArray(10)
        vp8[0] = 0x00 // key-frame
        vp8[3] = 0x9D.toByte()
        vp8[4] = 0x01
        vp8[5] = 0x2A
        vp8[6] = (width and 0xFF).toByte()
        vp8[7] = ((width shr 8) and 0x3F).toByte()
        vp8[8] = (height and 0xFF).toByte()
        vp8[9] = ((height shr 8) and 0x3F).toByte()
        return riffWebp("VP8 ", vp8)
    }

    private fun riffWebp(chunkId: String, payload: ByteArray): ByteArray {
        val paddedPayload = if (payload.size % 2 == 1) {
            payload + ByteArray(1)
        } else {
            payload
        }
        val totalSize = 12 + 8 + paddedPayload.size
        val output = ByteArrayOutputStream(totalSize)
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.write(le32(totalSize - 8))
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write(chunkId.toByteArray(Charsets.US_ASCII))
        output.write(le32(payload.size))
        output.write(paddedPayload)
        return output.toByteArray()
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )
}
