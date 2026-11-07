package com.example.telegram

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class TelegramImportResult {
    data class Success(
        val intent: Intent,
        val validStickerCount: Int,
        val stickerNames: List<String>
    ) : TelegramImportResult()

    data class Error(
        val errorCode: String,
        val userMessage: String,
        val details: String? = null
    ) : TelegramImportResult()
}

object TelegramExporter {

    private fun getFileProviderAuthority(context: Context): String {
        return "${context.packageName}.provider"
    }

    /**
     * Checks if any supported Telegram package is installed on the device.
     */
    fun isTelegramInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val telegramPackages = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.thunderdog.challegram",
            "org.telegram.plus"
        )
        for (pkg in telegramPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (_: Exception) {
                // Ignore
            }
        }
        return false
    }

    /**
     * Prepares and validates all stickers for the official Telegram Sticker Import API.
     * Enforces:
     * - Telegram app installation check
     * - File existence, size, and readability checks
     * - Technical format validation (Static WebP/PNG <= 512KB, Video WEBM VP9 <= 256KB, 512px)
     * - Emoji requirement check for EVERY sticker
     * - ContentProvider URI generation & ContentResolver openability validation
     * - ClipData grant permission attachment
     */
    fun prepareTelegramImport(
        context: Context,
        pack: StickerPack,
        stickers: List<StickerItem>
    ): TelegramImportResult {
        // 1. Telegram App Check
        if (!isTelegramInstalled(context)) {
            DiagnosticsRepository.logError(
                context = context,
                operation = "TELEGRAM_IMPORT",
                stage = DiagnosticStage.IMPORT,
                fileName = pack.name,
                customMessage = "Telegram application is not installed on this device"
            )
            return TelegramImportResult.Error(
                errorCode = "TELEGRAM_NOT_INSTALLED",
                userMessage = "تطبيق تيليجرام غير مثبت على الجهاز."
            )
        }

        // 2. Empty Pack Check
        if (stickers.isEmpty()) {
            return TelegramImportResult.Error(
                errorCode = "TELEGRAM_IMPORT_FILE_MISSING",
                userMessage = "لا توجد ملصقات في الحزمة لتصديرها."
            )
        }

        // 3. Emoji Requirement Check
        for (item in stickers) {
            val emoji = item.emoji.ifBlank { pack.defaultEmoji }
            if (emoji.isBlank()) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.VALIDATE,
                    fileName = item.displayName,
                    customMessage = "TELEGRAM_IMPORT_EMOJI_MISSING: Sticker '${item.displayName}' lacks an assigned emoji"
                )
                return TelegramImportResult.Error(
                    errorCode = "TELEGRAM_IMPORT_EMOJI_MISSING",
                    userMessage = "يجب تحديد إيموجي للملصق '${item.displayName}' قبل إضافته إلى Telegram."
                )
            }
        }

        val authority = getFileProviderAuthority(context)
        val uriList = ArrayList<Uri>()
        val emojiList = ArrayList<String>()
        val validNames = mutableListOf<String>()

        // 4. File Verification & ContentResolver Validation
        for (item in stickers) {
            val path = item.outputPath
            
            // Map original source details for diagnostics
            val srcUri = item.sourceUri
            val srcType = when {
                srcUri == null -> "LOCAL_FILE"
                srcUri.startsWith("content://") -> "GALLERY_URI"
                srcUri.startsWith("zip_entry://") -> "ZIP_ENTRY"
                else -> "LOCAL_FILE"
            }
            var srcReadStatus = "FAILED"
            if (!srcUri.isNullOrBlank()) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(srcUri))?.use {
                        srcReadStatus = "SUCCESS"
                    }
                } catch (_: Exception) {}
            }

            if (path.isNullOrBlank()) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.VALIDATE,
                    fileName = item.displayName,
                    customMessage = "TELEGRAM_IMPORT_FILE_MISSING: Sticker has not been converted yet",
                    sourceType = srcType,
                    sourceUri = srcUri,
                    sourceMimeType = item.sourceMimeType,
                    sourceDisplayName = item.sourceDisplayName,
                    sourceUriReadStatus = srcReadStatus
                )
                return TelegramImportResult.Error(
                    errorCode = "TELEGRAM_IMPORT_FILE_MISSING",
                    userMessage = "الملصق '${item.displayName}' غير متحول بعد. يرجى تحويله أولاً."
                )
            }

            val file = File(path)
            if (!file.exists() || !file.isFile || file.length() == 0L || !file.canRead()) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.VALIDATE,
                    fileName = item.displayName,
                    file = file,
                    customMessage = "TELEGRAM_IMPORT_FILE_MISSING: File missing or not readable at path $path",
                    sourceType = srcType,
                    sourceUri = srcUri,
                    sourceMimeType = item.sourceMimeType,
                    sourceDisplayName = item.sourceDisplayName,
                    sourceUriReadStatus = srcReadStatus
                )
                return TelegramImportResult.Error(
                    errorCode = "TELEGRAM_IMPORT_FILE_MISSING",
                    userMessage = "ملف الملصق '${item.displayName}' مفقود أو غير قابل للقراءة."
                )
            }

            // Technical validation against Telegram specifications
            val validation = TelegramStickerValidator.validate(file, item.type.isAnimated)
            if (!validation.isValid) {
                val errorDetails = validation.errors.joinToString("، ")
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.VALIDATE,
                    fileName = item.displayName,
                    file = file,
                    customMessage = "INVALID_STICKER_FORMAT: $errorDetails",
                    sourceType = srcType,
                    sourceUri = srcUri,
                    sourceMimeType = item.sourceMimeType,
                    sourceDisplayName = item.sourceDisplayName,
                    sourceUriReadStatus = srcReadStatus
                )
                return TelegramImportResult.Error(
                    errorCode = if (item.type.isAnimated) "INVALID_VIDEO_STICKER" else "INVALID_STATIC_STICKER",
                    userMessage = "الملصق '${item.displayName}' غير مطابق لمواصفات تيليجرام: $errorDetails"
                )
            }

            // Generate ContentProvider URI
            val uri = try {
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: Exception) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.URI_CREATE,
                    fileName = item.displayName,
                    file = file,
                    exception = e,
                    customMessage = "TELEGRAM_IMPORT_PROVIDER_ERROR: Failed to generate FileProvider URI",
                    sourceType = srcType,
                    sourceUri = srcUri,
                    sourceMimeType = item.sourceMimeType,
                    sourceDisplayName = item.sourceDisplayName,
                    sourceUriReadStatus = srcReadStatus
                )
                return TelegramImportResult.Error(
                    errorCode = "TELEGRAM_IMPORT_PROVIDER_ERROR",
                    userMessage = "فشل إنشاء رابط المشاركة للملصق '${item.displayName}'."
                )
            }

            // Validate ContentResolver can open the URI
            var canOpenStream = false
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    canOpenStream = true
                }
            } catch (e: Exception) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "TELEGRAM_IMPORT",
                    stage = DiagnosticStage.URI_OPEN,
                    fileName = item.displayName,
                    file = file,
                    uriString = uri.toString(),
                    exception = e,
                    customMessage = "TELEGRAM_IMPORT_URI_OPEN_FAILED: ContentResolver stream open failed",
                    sourceType = srcType,
                    sourceUri = srcUri,
                    sourceMimeType = item.sourceMimeType,
                    sourceDisplayName = item.sourceDisplayName,
                    sourceUriReadStatus = srcReadStatus
                )
            }

            if (!canOpenStream) {
                return TelegramImportResult.Error(
                    errorCode = "TELEGRAM_IMPORT_URI_OPEN_FAILED",
                    userMessage = "تعذر فتح رابط مشاركة الملصق '${item.displayName}'."
                )
            }

            // Log diagnostic info for each verified sticker with full source details
            DiagnosticsRepository.logError(
                context = context,
                operation = "TELEGRAM_IMPORT",
                stage = DiagnosticStage.IMPORT,
                fileName = item.displayName,
                file = file,
                uriString = uri.toString(),
                customMessage = "Sticker validated successfully for Telegram import",
                sourceType = srcType,
                sourceUri = srcUri,
                sourceMimeType = item.sourceMimeType,
                sourceDisplayName = item.sourceDisplayName,
                sourceUriReadStatus = srcReadStatus
            )

            uriList.add(uri)
            val emoji = item.emoji.ifBlank { pack.defaultEmoji }
            emojiList.add(emoji)
            validNames.add(item.displayName)
        }

        if (uriList.isEmpty()) {
            return TelegramImportResult.Error(
                errorCode = "TELEGRAM_IMPORT_FILE_MISSING",
                userMessage = "لم يتم العثور على أي ملصقات صالحة للاستيراد."
            )
        }

        // 5. Build Intent with ClipData + Intent Extras
        val intent = Intent("org.telegram.messenger.CREATE_STICKER_PACK").apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            putStringArrayListExtra("STICKER_EMOJIS", emojiList)
            putExtra("IMPORTER", context.packageName)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // ClipData is MANDATORY for granting URI read permissions across apps in Android 7.0+
            val clipData = ClipData.newRawUri("sticker", uriList[0])
            for (i in 1 until uriList.size) {
                clipData.addItem(ClipData.Item(uriList[i]))
            }
            this.clipData = clipData
        }

        return TelegramImportResult.Success(
            intent = intent,
            validStickerCount = uriList.size,
            stickerNames = validNames
        )
    }

    /**
     * Builds a clean ZIP archive with the final Telegram sticker files and metadata json.
     * The archive can be shared with any app or sent to the official @Stickers bot.
     */
    fun buildTelegramStickersZip(
        context: Context,
        pack: StickerPack,
        stickers: List<StickerItem>,
        destinationZipFile: File
    ): Boolean {
        val readyStickers = stickers.filter {
            it.conversionStatus == ConversionStatus.SUCCESS && !it.outputPath.isNullOrBlank()
        }
        if (readyStickers.isEmpty()) return false

        try {
            destinationZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationZipFile))).use { zipOut ->
                val exportedEntries = mutableListOf<Pair<StickerItem, String>>()

                readyStickers.forEachIndexed { index, item ->
                    val file = File(item.outputPath!!)
                    if (!file.exists() || !file.canRead()) return@forEachIndexed

                    val extension = file.extension.ifBlank {
                        if (item.type.isAnimated) "webm" else "webp"
                    }
                    val sanitizedName = sanitizeFileBaseName(item.displayName).ifBlank { "sticker_${index + 1}" }
                    val entryName = String.format(
                        java.util.Locale.US,
                        "%03d_%s.%s",
                        index + 1,
                        sanitizedName,
                        extension
                    )
                    zipOut.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    exportedEntries.add(item to entryName)
                }

                if (exportedEntries.isEmpty()) return false

                val metadataJson = buildString {
                    appendLine("{")
                    appendLine("  \"title\": \"${jsonEscape(pack.name)}\",")
                    appendLine("  \"short_name\": \"${jsonEscape(pack.shortName)}\",")
                    appendLine("  \"author\": \"${jsonEscape(pack.author)}\",")
                    appendLine("  \"default_emoji\": \"${jsonEscape(pack.defaultEmoji)}\",")
                    appendLine("  \"stickers_count\": ${exportedEntries.size},")
                    appendLine("  \"stickers\": [")
                    exportedEntries.forEachIndexed { index, (item, entryName) ->
                        val emoji = item.emoji.ifBlank { pack.defaultEmoji }
                        val animated = item.type.isAnimated
                        val comma = if (index == exportedEntries.lastIndex) "" else ","
                        appendLine(
                            "    {\"index\": ${index + 1}, \"file\": \"${jsonEscape(entryName)}\", " +
                                "\"name\": \"${jsonEscape(item.displayName)}\", " +
                                "\"emoji\": \"${jsonEscape(emoji)}\", " +
                                "\"type\": \"${if (animated) "video" else "static"}\"}$comma"
                        )
                    }
                    appendLine("  ]")
                    append("}")
                }

                zipOut.putNextEntry(ZipEntry("pack_metadata.json"))
                zipOut.write(metadataJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // A second copy with a generic name for tools that expect metadata.json.
                zipOut.putNextEntry(ZipEntry("metadata.json"))
                zipOut.write(metadataJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Shares all validated sticker files directly as multiple attachments.
     */
    fun createShareStickersIntent(context: Context, stickers: List<StickerItem>): Intent? {
        val ready = stickers.filter {
            it.conversionStatus == ConversionStatus.SUCCESS && !it.outputPath.isNullOrBlank() && File(it.outputPath).exists()
        }
        if (ready.isEmpty()) return null

        val authority = getFileProviderAuthority(context)
        val uriList = ArrayList<Uri>()
        for (item in ready) {
            try {
                uriList.add(FileProvider.getUriForFile(context, authority, File(item.outputPath!!)))
            } catch (_: Exception) {
                // Skip files that cannot be exposed by the provider
            }
        }
        if (uriList.isEmpty()) return null

        val clipData = ClipData.newRawUri("stickers", uriList[0])
        for (i in 1 until uriList.size) {
            clipData.addItem(ClipData.Item(uriList[i]))
        }

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.clipData = clipData
        }
    }

    /**
     * Opens the Telegram app page in the Play Store when Telegram is not installed.
     */
    fun createTelegramInstallIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=org.telegram.messenger")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun sanitizeFileBaseName(name: String): String {
        return name.trim()
            .replace("[^\\w\\p{L}.-]".toRegex(), "_")
            .trim('_', '.', '-')
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Prepares standard Android Sharesheet Intent for sharing the ZIP file.
     */
    fun createShareZipIntent(context: Context, zipFile: File): Intent {
        val authority = getFileProviderAuthority(context)
        val uri = FileProvider.getUriForFile(context, authority, zipFile)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("zip", uri)
        }
    }
}
