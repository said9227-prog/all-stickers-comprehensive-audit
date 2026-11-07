package com.example.conversion

import com.example.core.model.ValidationResult
import com.example.media.MediaDetector
import com.example.telegram.TelegramStickerValidator
import java.io.File

data class CompressionResult(
    val success: Boolean,
    val outputFile: File,
    val originalSize: Long,
    val convertedSize: Long,
    val qualityLabelAr: String,
    val validationResult: ValidationResult
)

class CompressionEngine {

    fun compressAndConvert(inputFile: File, targetOutputFile: File): CompressionResult {
        val originalSize = inputFile.length()
        val detected = MediaDetector.detect(inputFile)
        val isAnimated = detected.type.isAnimated

        if (!isAnimated) {
            // Static sticker compression
            val success = StaticStickerConverter.convert(inputFile, targetOutputFile)
            val valResult = TelegramStickerValidator.validate(targetOutputFile, isAnimated = false)
            val qualityLabel = when {
                targetOutputFile.length() < 200 * 1024L -> "عالية (High)"
                targetOutputFile.length() < 400 * 1024L -> "جيدة (Standard)"
                else -> "مضغوطة (Compressed)"
            }
            return CompressionResult(
                success = success && valResult.isValid,
                outputFile = targetOutputFile,
                originalSize = originalSize,
                convertedSize = targetOutputFile.length(),
                qualityLabelAr = qualityLabel,
                validationResult = valResult
            )
        }

        // Animated sticker smart compression engine:
        // Pass 1: High bitrate (250 kbps), 30 fps
        AnimatedWebpConverter.convert(inputFile, targetOutputFile, targetBitrate = 250_000, fpsCap = 30)
        var valResult = TelegramStickerValidator.validate(targetOutputFile, isAnimated = true)
        if (valResult.isValid) {
            return CompressionResult(
                success = true,
                outputFile = targetOutputFile,
                originalSize = originalSize,
                convertedSize = targetOutputFile.length(),
                qualityLabelAr = "عالية (High)",
                validationResult = valResult
            )
        }

        // Pass 2: Lower bitrate (160 kbps), 25 fps
        AnimatedWebpConverter.convert(inputFile, targetOutputFile, targetBitrate = 160_000, fpsCap = 25)
        valResult = TelegramStickerValidator.validate(targetOutputFile, isAnimated = true)
        if (valResult.isValid) {
            return CompressionResult(
                success = true,
                outputFile = targetOutputFile,
                originalSize = originalSize,
                convertedSize = targetOutputFile.length(),
                qualityLabelAr = "متوسطة (Medium)",
                validationResult = valResult
            )
        }

        // Pass 3: Lower bitrate (100 kbps), 20 fps
        AnimatedWebpConverter.convert(inputFile, targetOutputFile, targetBitrate = 100_000, fpsCap = 20)
        valResult = TelegramStickerValidator.validate(targetOutputFile, isAnimated = true)
        if (valResult.isValid) {
            return CompressionResult(
                success = true,
                outputFile = targetOutputFile,
                originalSize = originalSize,
                convertedSize = targetOutputFile.length(),
                qualityLabelAr = "مضغوطة (Optimized)",
                validationResult = valResult
            )
        }

        // Pass 4: Maximum compression (70 kbps), 15 fps
        AnimatedWebpConverter.convert(inputFile, targetOutputFile, targetBitrate = 70_000, fpsCap = 15)
        valResult = TelegramStickerValidator.validate(targetOutputFile, isAnimated = true)

        return CompressionResult(
            success = valResult.isValid,
            outputFile = targetOutputFile,
            originalSize = originalSize,
            convertedSize = targetOutputFile.length(),
            qualityLabelAr = "أقصى ضغط (Max Compression)",
            validationResult = valResult
        )
    }
}
