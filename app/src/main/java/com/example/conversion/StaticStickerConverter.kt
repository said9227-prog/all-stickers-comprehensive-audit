package com.example.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.media.BitmapUtils
import java.io.File
import java.io.FileOutputStream

object StaticStickerConverter {

    /**
     * Converts any static image to a Telegram-compliant static sticker:
     * - One dimension is 512px, other <= 512px
     * - Transparent background preserved
     * - Size <= 512 KB
     */
    fun convert(inputFile: File, outputFile: File): Boolean {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val originalBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return false

        try {
            val dimensions = BitmapUtils.calculateTelegramDimensions(
                originalBitmap.width,
                originalBitmap.height,
                forceEven = false
            )

            val scaledBitmap = BitmapUtils.scaleBitmap(
                originalBitmap,
                dimensions.width,
                dimensions.height
            )

            // Multi-pass smart compression
            var quality = 95
            var success = false

            while (quality >= 30) {
                FileOutputStream(outputFile).use { fos ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
                    } else {
                        @Suppress("DEPRECATION")
                        scaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, fos)
                    }
                }

                if (outputFile.length() <= 512 * 1024L) {
                    success = true
                    break
                }
                quality -= 15
            }

            if (!success) {
                // Try PNG if WebP fails or is too large
                FileOutputStream(outputFile).use { fos ->
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                success = outputFile.length() <= 512 * 1024L
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }

            return success
        } finally {
            originalBitmap.recycle()
        }
    }
}
