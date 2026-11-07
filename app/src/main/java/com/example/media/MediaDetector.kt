package com.example.media

import com.example.core.model.StickerType
import java.io.File
import java.io.InputStream

data class DetectedMedia(
    val type: StickerType,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val hasAlpha: Boolean = true,
    val fileSize: Long = 0L
)

object MediaDetector {

    fun detect(file: File): DetectedMedia {
        if (!file.exists() || file.length() == 0L) {
            return DetectedMedia(type = StickerType.UNSUPPORTED, fileSize = file.length())
        }

        return file.inputStream().use { stream ->
            detect(stream, file.length())
        }
    }

    fun detect(stream: InputStream, fileSize: Long = 0L): DetectedMedia {
        val header = ByteArray(64)
        val read = stream.read(header)
        if (read < 4) {
            return DetectedMedia(type = StickerType.UNSUPPORTED, fileSize = fileSize)
        }

        // 1. WebP check (RIFF....WEBP)
        if (read >= 12 &&
            header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        ) {
            // Further parse WebP via WebpParser with remaining stream
            val fullBytes = header.copyOf(read) + stream.readBytes()
            val webpInfo = WebpParser.parse(fullBytes.inputStream())
            val type = if (webpInfo.isAnimated) StickerType.ANIMATED_WEBP else StickerType.STATIC_WEBP
            return DetectedMedia(
                type = type,
                width = webpInfo.width,
                height = webpInfo.height,
                durationMs = webpInfo.totalDurationMs,
                hasAlpha = webpInfo.hasAlpha,
                fileSize = if (fileSize > 0) fileSize else fullBytes.size.toLong()
            )
        }

        // 2. PNG check (89 50 4E 47 0D 0A 1A 0A)
        if (read >= 8 &&
            (header[0].toInt() and 0xFF) == 0x89 &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'N'.code.toByte() &&
            header[3] == 'G'.code.toByte()
        ) {
            // Read IHDR dimensions (offset 16-23)
            var w = 0
            var h = 0
            if (read >= 24) {
                w = ((header[16].toInt() and 0xFF) shl 24) or
                        ((header[17].toInt() and 0xFF) shl 16) or
                        ((header[18].toInt() and 0xFF) shl 8) or
                        (header[19].toInt() and 0xFF)
                h = ((header[20].toInt() and 0xFF) shl 24) or
                        ((header[21].toInt() and 0xFF) shl 16) or
                        ((header[22].toInt() and 0xFF) shl 8) or
                        (header[23].toInt() and 0xFF)
            }
            return DetectedMedia(
                type = StickerType.PNG,
                width = w,
                height = h,
                hasAlpha = true,
                fileSize = fileSize
            )
        }

        // 3. JPG / JPEG check (FF D8 FF)
        if (read >= 3 &&
            (header[0].toInt() and 0xFF) == 0xFF &&
            (header[1].toInt() and 0xFF) == 0xD8 &&
            (header[2].toInt() and 0xFF) == 0xFF
        ) {
            return DetectedMedia(
                type = StickerType.JPG,
                hasAlpha = false,
                fileSize = fileSize
            )
        }

        // 4. WEBM check (1A 45 DF A3)
        if (read >= 4 &&
            (header[0].toInt() and 0xFF) == 0x1A &&
            (header[1].toInt() and 0xFF) == 0x45 &&
            (header[2].toInt() and 0xFF) == 0xDF &&
            (header[3].toInt() and 0xFF) == 0xA3
        ) {
            return DetectedMedia(
                type = StickerType.WEBM,
                hasAlpha = true,
                fileSize = fileSize
            )
        }

        return DetectedMedia(
            type = StickerType.UNSUPPORTED,
            fileSize = fileSize
        )
    }
}
