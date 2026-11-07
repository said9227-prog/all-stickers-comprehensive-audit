package com.example.conversion

import android.content.Context
import android.net.Uri
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.data.repository.StickerRepository
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.storage.TempFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class BatchConversionProgress(
    val current: Int,
    val total: Int,
    val successCount: Int,
    val failedCount: Int,
    val currentStickerName: String,
    val isFinished: Boolean = false
)

class StickerConversionEngine(
    private val context: Context,
    private val tempFileManager: TempFileManager,
    private val stickerRepository: StickerRepository
) {
    private val compressionEngine = CompressionEngine()

    suspend fun convertSticker(sticker: StickerItem): StickerItem = withContext(Dispatchers.IO) {
        val packDir = tempFileManager.getPackConvertedDirectory(sticker.packId)
        val isAnimated = sticker.type.isAnimated
        val extension = if (isAnimated) "webm" else "webp"
        val outFile = File(packDir, "sticker_${sticker.id}.$extension")

        // Prepare input file
        val tempInputFile = tempFileManager.createTempFile("in_${sticker.id}_", ".tmp")
        try {
            var streamOpened = false
            if (sticker.originalUri.startsWith("content://")) {
                val originalUri = Uri.parse(sticker.originalUri)
                context.contentResolver.openInputStream(originalUri)?.use { input ->
                    FileOutputStream(tempInputFile).use { output ->
                        input.copyTo(output)
                    }
                    streamOpened = true
                }
            } else {
                val srcPath = if (sticker.originalUri.startsWith("file://")) {
                    Uri.parse(sticker.originalUri).path ?: sticker.originalUri.removePrefix("file://")
                } else {
                    sticker.originalUri
                }
                val srcFile = File(srcPath)
                if (srcFile.exists() && srcFile.length() > 0L) {
                    srcFile.copyTo(tempInputFile, overwrite = true)
                    streamOpened = true
                }
            }

            if (!streamOpened || !tempInputFile.exists() || tempInputFile.length() == 0L) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "STICKER_CONVERSION",
                    stage = DiagnosticStage.CONVERT,
                    fileName = sticker.displayName,
                    uriString = sticker.originalUri,
                    customMessage = "Could not open or copy original sticker for conversion"
                )
                return@withContext sticker.copy(
                    conversionStatus = ConversionStatus.FAILED,
                    errorMessage = "تعذر قراءة ملف الملصق الأصلي"
                )
            }

            val result = compressionEngine.compressAndConvert(tempInputFile, outFile)

            if (result.success && outFile.exists() && outFile.length() > 0) {
                val outputUri = tempFileManager.getFileProviderUri(outFile)
                val updated = sticker.copy(
                    outputUri = outputUri.toString(),
                    outputPath = outFile.absolutePath,
                    convertedSize = outFile.length(),
                    width = result.validationResult.width,
                    height = result.validationResult.height,
                    durationMs = result.validationResult.durationMs,
                    conversionStatus = ConversionStatus.SUCCESS,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                stickerRepository.updateSticker(updated)
                return@withContext updated
            } else {
                val errorReason = if (result.validationResult.errors.isNotEmpty()) {
                    result.validationResult.errors.joinToString("، ")
                } else {
                    "فشل تشفير الملف بمواصفات تيليجرام"
                }
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "STICKER_CONVERSION",
                    stage = DiagnosticStage.CONVERT,
                    fileName = sticker.displayName,
                    file = outFile,
                    customMessage = errorReason
                )
                val updated = sticker.copy(
                    conversionStatus = ConversionStatus.FAILED,
                    errorMessage = errorReason,
                    updatedAt = System.currentTimeMillis()
                )
                stickerRepository.updateSticker(updated)
                return@withContext updated
            }
        } catch (e: Exception) {
            DiagnosticsRepository.logError(
                context = context,
                operation = "STICKER_CONVERSION",
                stage = DiagnosticStage.CONVERT,
                fileName = sticker.displayName,
                uriString = sticker.originalUri,
                exception = e,
                customMessage = "Conversion failed with exception: ${e.message}"
            )
            val updated = sticker.copy(
                conversionStatus = ConversionStatus.FAILED,
                errorMessage = e.message ?: "خطأ غير متوقع أثناء التحويل",
                updatedAt = System.currentTimeMillis()
            )
            stickerRepository.updateSticker(updated)
            return@withContext updated
        } finally {
            try { tempInputFile.delete() } catch (_: Exception) {}
        }
    }

    suspend fun convertBatch(
        stickers: List<StickerItem>,
        onProgress: (BatchConversionProgress) -> Unit
    ): List<StickerItem> = withContext(Dispatchers.IO) {
        var successCount = 0
        var failedCount = 0
        val results = mutableListOf<StickerItem>()

        for ((index, item) in stickers.withIndex()) {
            coroutineContext.ensureActive()

            onProgress(
                BatchConversionProgress(
                    current = index + 1,
                    total = stickers.size,
                    successCount = successCount,
                    failedCount = failedCount,
                    currentStickerName = item.displayName
                )
            )

            val converted = convertSticker(item)
            if (converted.conversionStatus == ConversionStatus.SUCCESS) {
                successCount++
            } else {
                failedCount++
            }
            results.add(converted)
        }

        onProgress(
            BatchConversionProgress(
                current = stickers.size,
                total = stickers.size,
                successCount = successCount,
                failedCount = failedCount,
                currentStickerName = "",
                isFinished = true
            )
        )

        results
    }
}
