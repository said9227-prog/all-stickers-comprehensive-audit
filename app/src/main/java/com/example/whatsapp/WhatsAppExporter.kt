package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoder
import com.aureusapps.android.webpandroid.encoder.WebPAnimEncoderOptions
import com.aureusapps.android.webpandroid.encoder.WebPConfig
import com.aureusapps.android.webpandroid.encoder.WebPEncoder
import com.aureusapps.android.webpandroid.encoder.WebPMuxAnimParams
import com.aureusapps.android.webpandroid.encoder.WebPPreset
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.creator.STICKER_CANVAS_SIZE
import com.example.creator.StickerCreatorEngine
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.media.ExtractedFrame
import com.example.media.WebpFrameExtractor
import com.example.media.WebpParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

sealed class WhatsAppExportResult {
    data class Success(
        val packId: String,
        val packName: String,
        val authority: String,
        val stickerCount: Int,
        val animated: Boolean,
        val warnings: List<String>
    ) : WhatsAppExportResult()

    data class Failure(
        val userMessage: String,
        val details: String? = null
    ) : WhatsAppExportResult()
}

data class WhatsAppExportOptions(
    val animated: Boolean,
    val packName: String? = null,
    val identifierSuffix: String = ""
)

object WhatsAppExporter {
    const val CONSUMER_PACKAGE = "com.whatsapp"
    const val BUSINESS_PACKAGE = "com.whatsapp.w4b"
    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val TRAY_MAX_BYTES = 50 * 1024
    private const val MAX_EMOJIS = 3

    fun authority(context: Context): String {
        return "${context.packageName}.stickercontentprovider"
    }

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, business: Boolean): Boolean {
        val packageName = if (business) BUSINESS_PACKAGE else CONSUMER_PACKAGE
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun createAddIntent(
        context: Context,
        business: Boolean,
        packId: String,
        packName: String
    ): Intent {
        return Intent(ACTION_ENABLE_STICKER_PACK).apply {
            setPackage(if (business) BUSINESS_PACKAGE else CONSUMER_PACKAGE)
            putExtra("sticker_pack_id", packId)
            putExtra("sticker_pack_authority", authority(context))
            putExtra("sticker_pack_name", packName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createInstallIntent(business: Boolean): Intent {
        val packageName = if (business) BUSINESS_PACKAGE else CONSUMER_PACKAGE
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )
    }

    fun isWhitelisted(context: Context, business: Boolean, packId: String): Boolean {
        val packageName = if (business) BUSINESS_PACKAGE else CONSUMER_PACKAGE
        val whitelistAuthority = "$packageName.provider.sticker_whitelist_check"
        val uri = Uri.Builder()
            .scheme("content")
            .authority(whitelistAuthority)
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", authority(context))
            .appendQueryParameter("identifier", packId)
            .build()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val index = cursor.getColumnIndex("result")
                index >= 0 && cursor.getInt(index) == 1
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun deleteExportedPack(context: Context, packId: String) {
        try {
            WhatsAppPackStore.packDir(context, packId).deleteRecursively()
        } catch (_: Exception) {
        }
    }

    fun deleteExportedPacks(context: Context, packDatabaseId: Long) {
        try {
            WhatsAppPackStore.root(context).listFiles { file ->
                file.isDirectory && file.name.startsWith("pack_${packDatabaseId}_")
            }?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }

    fun preparePack(
        context: Context,
        pack: StickerPack,
        stickers: List<StickerItem>,
        options: WhatsAppExportOptions? = null
    ): WhatsAppExportResult {
        val validation = if (options == null) {
            WhatsAppStickerValidator.validate(pack, stickers)
        } else {
            null
        }
        if (validation != null && !validation.isValid) {
            return WhatsAppExportResult.Failure(
                validation.errors.firstOrNull() ?: "الحزمة غير صالحة للتصدير إلى واتساب."
            )
        }

        val sortedStickers = stickers
            .filter { it.originalUri.isNotBlank() }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        if (sortedStickers.size < WhatsAppStickerValidator.MIN_STICKERS ||
            sortedStickers.size > WhatsAppStickerValidator.MAX_STICKERS
        ) {
            return WhatsAppExportResult.Failure(
                "حزمة واتساب يجب أن تحتوي بين ${WhatsAppStickerValidator.MIN_STICKERS} و" +
                    " ${WhatsAppStickerValidator.MAX_STICKERS} ملصقًا."
            )
        }

        val animated = options?.animated ?: (validation?.isAnimated == true)
        val suffix = options?.identifierSuffix
            ?.trim()
            ?.filter { it.isLetterOrDigit() || it == '_' }
            ?.take(24)
            .orEmpty()
        val uniquePart = if (suffix.isBlank()) {
            "${pack.id}_${System.currentTimeMillis()}"
        } else {
            "${pack.id}_${suffix}_${System.currentTimeMillis()}"
        }
        val packId = "pack_$uniquePart"
        val root = WhatsAppPackStore.root(context)
        val staging = File(root, "$packId.tmp")
        val destination = File(root, packId)
        val tempDir = File(context.cacheDir, "wa_export_$packId")
        val packName = (options?.packName ?: pack.name).trim().ifBlank { "ملصقاتي" }.take(128)

        return try {
            if (staging.exists()) staging.deleteRecursively()
            if (!staging.mkdirs() && !staging.isDirectory) {
                return WhatsAppExportResult.Failure("تعذر إنشاء مجلد حزمة واتساب.")
            }
            tempDir.mkdirs()

            val assets = mutableListOf<WhatsAppStickerAsset>()
            // Reuse the FIRST resolved source for the tray instead of copying
            // the same content:// Uri into a second temporary file.
            var firstSource: File? = null
            for ((index, item) in sortedStickers.withIndex()) {
                val source = resolveSourceFile(context, item, tempDir)
                    ?: return WhatsAppExportResult.Failure(
                        "تعذر قراءة الملصق: ${item.displayName.ifBlank { item.originalName }}."
                    )
                if (index == 0) firstSource = source
                val fileName = "sticker_${index + 1}.webp"
                val output = File(staging, fileName)

                // FAST PATH: an already-compliant WebP is copied verbatim.
                // This skips up to seven decode/re-encode attempts per sticker,
                // which was the dominant cost of "add to WhatsApp". Quality,
                // alpha, animation frames and frame durations are preserved
                // exactly, and verifyPack() below still validates every output.
                // An animated source in a static pack is NEVER reused: it must
                // be converted to its first frame by encodeStaticSticker().
                val info = runCatching { WebpParser.parse(source) }.getOrNull()
                val canReuse = info != null && WhatsAppWebpFastPath.decide(
                    info = info,
                    byteSize = source.length(),
                    animatedPack = animated
                ).reuse
                val encoded = if (canReuse) {
                    try {
                        source.copyTo(output, overwrite = true)
                        output.exists() && output.length() > 0L
                    } catch (_: Exception) {
                        false
                    }
                } else if (animated) {
                    encodeAnimatedSticker(context, source, output)
                } else {
                    encodeStaticSticker(context, source, output)
                }
                if (!encoded || !output.exists() || output.length() <= 0L) {
                    return WhatsAppExportResult.Failure(
                        if (animated) {
                            "تعذر ترميز الملصق المتحرك ${index + 1} كملف WebP بحجم أقل من 500KB."
                        } else {
                            "تعذر ضغط الملصق ${index + 1} ليصبح أقل من 100KB."
                        }
                    )
                }
                assets.add(
                    WhatsAppStickerAsset(
                        id = fileName,
                        fileName = fileName,
                        emojis = emojisFor(item)
                    )
                )
            }

            val traySource = firstSource
                ?: resolveSourceFile(context, sortedStickers.first(), tempDir)
                ?: return WhatsAppExportResult.Failure("تعذر إنشاء أيقونة الحزمة.")
            val trayFileName = createTrayIcon(context, traySource, staging)
                ?: return WhatsAppExportResult.Failure("تعذر إنشاء أيقونة الحزمة بحجم مناسب.")

            val packJson = JSONObject().apply {
                put("identifier", packId)
                put("name", packName)
                put("publisher", publisherName(context))
                put("tray_image_file", trayFileName)
                put("image_data_version", "1")
                put("avoid_cache", false)
                put("animated_sticker_pack", animated)
                put(
                    "stickers",
                    JSONArray().apply {
                        assets.forEach { asset ->
                            put(
                                JSONObject().apply {
                                    put("image_file", asset.fileName)
                                    put("emojis", JSONArray(asset.emojis))
                                }
                            )
                        }
                    }
                )
            }
            File(staging, WhatsAppPackStore.PACK_FILE).writeText(packJson.toString(2))

            val stagingVerification = verifyPack(staging, animated)
            if (!stagingVerification.valid) {
                logVerificationFailure(
                    context,
                    packId,
                    packName,
                    stagingVerification,
                    animated
                )
                return WhatsAppExportResult.Failure(
                    "فشل التحقق من ملفات الحزمة بعد الترميز. سُجّلت التفاصيل في صفحة التشخيص.",
                    stagingVerification.reason
                )
            }

            destination.deleteRecursively()
            val moved = staging.renameTo(destination) ||
                staging.copyRecursively(destination, overwrite = true)
            if (!moved) {
                return WhatsAppExportResult.Failure(
                    "تعذر حفظ ملفات الحزمة النهائية.",
                    "rename/copy failed from ${staging.absolutePath} to ${destination.absolutePath}"
                )
            }
            val destinationVerification = verifyPack(destination, animated)
            if (!destinationVerification.valid) {
                logVerificationFailure(
                    context,
                    packId,
                    packName,
                    destinationVerification,
                    animated
                )
                destination.deleteRecursively()
                return WhatsAppExportResult.Failure(
                    "فشل التحقق من ملفات الحزمة بعد الحفظ. سُجّلت التفاصيل في صفحة التشخيص.",
                    destinationVerification.reason
                )
            }
            staging.deleteRecursively()
            DiagnosticsRepository.logInfo(
                operation = "WHATSAPP_EXPORT",
                stage = DiagnosticStage.EXPORT,
                fileName = packId,
                message = "تم إنشاء حزمة واتساب بنجاح: ${assets.size} ملصق، animated=$animated، tray=$trayFileName"
            )

            WhatsAppExportResult.Success(
                packId = packId,
                packName = packName,
                authority = authority(context),
                stickerCount = assets.size,
                animated = animated,
                warnings = validation?.warnings ?: emptyList()
            )
        } catch (e: Exception) {
            DiagnosticsRepository.logError(
                context = context,
                operation = "WHATSAPP_EXPORT",
                stage = DiagnosticStage.EXPORT,
                fileName = packId,
                exception = e,
                customMessage = "pack=$packId، name=$packName: تعذر تجهيز الحزمة — ${e.message}",
                sourceType = if (animated) "ANIMATED_WEBP" else "STATIC_WEBP",
                sourceUri = packId,
                sourceDisplayName = packName
            )
            WhatsAppExportResult.Failure(
                "تعذر تجهيز حزمة واتساب. حاول مرة أخرى.",
                e.message
            )
        } finally {
            try { tempDir.deleteRecursively() } catch (_: Exception) {}
            try {
                if (staging.exists()) staging.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveSourceFile(context: Context, item: StickerItem, tempDir: File): File? {
        val raw = item.originalUri
        if (raw.isBlank()) return null
        val uri = Uri.parse(raw)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return File(path).takeIf { it.exists() && it.length() > 0L }
        }
        if (uri.scheme.isNullOrBlank()) {
            return File(raw).takeIf { it.exists() && it.length() > 0L }
        }
        val target = File(tempDir, "source_${item.id}_${System.currentTimeMillis()}.bin")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun emojisFor(item: StickerItem): List<String> {
        val parsed = item.emoji.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(MAX_EMOJIS)
        return parsed.ifEmpty { listOf("✨") }
    }

    private fun publisherName(context: Context): String {
        return try {
            context.applicationInfo.loadLabel(context.packageManager).toString().ifBlank { "Sticker Studio" }
        } catch (_: Exception) {
            "Sticker Studio"
        }
    }

    /**
     * Decodes the first visible frame of any supported source.
     *
     * This is what makes the animated -> static conversion possible: animated
     * WebP files are split into frames and the first frame is used, while
     * WebM/video sources fall back to MediaMetadataRetriever.
     */
    private fun decodeFirstFrame(source: File): Bitmap? {
        val info = runCatching { WebpParser.parse(source) }.getOrNull()
        if (info?.isWebp == true && info.isAnimated) {
            val frames = runCatching { WebpFrameExtractor.extractFrames(source) }
                .getOrDefault(emptyList())
            val first = frames.firstOrNull()
            frames.drop(1).forEach { runCatching { it.bitmap.recycle() } }
            if (first != null) return first.bitmap
        }
        if (info?.isWebp == true) {
            return decodeBitmap(source)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            retriever.getFrameAtTime(0L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        } ?: decodeBitmap(source)
    }

    private fun decodeBitmap(source: File): Bitmap? {
        return BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        )
    }

    private fun encodeStaticSticker(context: Context, source: File, output: File): Boolean {
        val decoded = decodeFirstFrame(source) ?: return false
        val canvas = StickerCreatorEngine.fitToCanvas(decoded, STICKER_CANVAS_SIZE)
        if (canvas !== decoded) decoded.recycle()
        var success = false
        try {
            val qualities = intArrayOf(88, 75, 62, 50, 38, 26, 16)
            for (quality in qualities) {
                output.delete()
                var encoder: WebPEncoder? = null
                try {
                    encoder = WebPEncoder(context, STICKER_CANVAS_SIZE, STICKER_CANVAS_SIZE)
                    encoder.configure(
                        WebPConfig(
                            lossless = WebPConfig.COMPRESSION_LOSSY,
                            quality = quality.toFloat(),
                            method = 6,
                            targetSize = WhatsAppStickerValidator.MAX_STATIC_BYTES,
                            alphaQuality = 100,
                            alphaCompression = WebPConfig.ALPHA_COMPRESSION_WITH_LOSSLESS,
                            alphaFiltering = WebPConfig.ALPHA_FILTERING_BEST,
                            exact = true
                        ),
                        WebPPreset.WEBP_PRESET_PICTURE
                    )
                    encoder.encode(canvas, Uri.fromFile(output))
                } catch (_: Exception) {
                } finally {
                    try { encoder?.release() } catch (_: Exception) {}
                }
                if (output.exists() && output.length() in 1L..WhatsAppStickerValidator.MAX_STATIC_BYTES.toLong()) {
                    success = true
                    break
                }
            }

            if (!success) {
                for (quality in qualities) {
                    output.delete()
                    try {
                        FileOutputStream(output).use { stream ->
                            canvas.compress(Bitmap.CompressFormat.WEBP, quality, stream)
                        }
                    } catch (_: Exception) {
                    }
                    if (output.exists() && output.length() in 1L..WhatsAppStickerValidator.MAX_STATIC_BYTES.toLong()) {
                        success = true
                        break
                    }
                }
            }
        } finally {
            canvas.recycle()
        }
        return success
    }

    private fun encodeAnimatedSticker(context: Context, source: File, output: File): Boolean {
        val info = try {
            WebpParser.parse(source)
        } catch (_: Exception) {
            null
        }
        if (info != null &&
            info.isWebp &&
            info.isAnimated &&
            info.width == STICKER_CANVAS_SIZE &&
            info.height == STICKER_CANVAS_SIZE &&
            source.length() <= WhatsAppStickerValidator.MAX_ANIMATED_BYTES &&
            info.totalDurationMs in 1L..WhatsAppStickerValidator.MAX_DURATION_MS
        ) {
            return try {
                source.copyTo(output, overwrite = true)
                output.exists() && output.length() > 0L
            } catch (_: Exception) {
                false
            }
        }

        val frames = try {
            WebpFrameExtractor.extractFrames(source)
        } catch (_: Exception) {
            emptyList()
        }
        if (frames.isEmpty()) return false

        val durations = adjustedDurations(frames.map { it.durationMs })
        if (durations == null) {
            frames.forEach { it.bitmap.recycle() }
            return false
        }

        val scaledFrames = ArrayList<ExtractedFrame>(frames.size)
        frames.forEachIndexed { index, frame ->
            val scaled = StickerCreatorEngine.fitToCanvas(frame.bitmap, STICKER_CANVAS_SIZE)
            if (scaled !== frame.bitmap) frame.bitmap.recycle()
            scaledFrames.add(ExtractedFrame(scaled, durations[index]))
        }

        var success = false
        try {
            for (quality in intArrayOf(82, 68, 54, 40, 26, 14)) {
                output.delete()
                var encoder: WebPAnimEncoder? = null
                try {
                    encoder = WebPAnimEncoder(
                        context,
                        STICKER_CANVAS_SIZE,
                        STICKER_CANVAS_SIZE,
                        WebPAnimEncoderOptions(
                            minimizeSize = true,
                            allowMixed = false,
                            animParams = WebPMuxAnimParams(
                                backgroundColor = 0,
                                loopCount = 0
                            )
                        )
                    )
                    encoder.configure(
                        WebPConfig(
                            lossless = WebPConfig.COMPRESSION_LOSSY,
                            quality = quality.toFloat(),
                            method = 6,
                            targetSize = WhatsAppStickerValidator.MAX_ANIMATED_BYTES,
                            alphaQuality = 100,
                            alphaCompression = WebPConfig.ALPHA_COMPRESSION_WITH_LOSSLESS,
                            alphaFiltering = WebPConfig.ALPHA_FILTERING_BEST,
                            exact = true
                        ),
                        WebPPreset.WEBP_PRESET_PICTURE
                    )
                    var timestamp = 0L
                    scaledFrames.forEach { frame ->
                        encoder.addFrame(timestamp, frame.bitmap)
                        timestamp += frame.durationMs
                    }
                    encoder.assemble(timestamp, Uri.fromFile(output))
                } catch (_: Exception) {
                } finally {
                    try { encoder?.release() } catch (_: Exception) {}
                }
                if (output.exists() && output.length() in 1L..WhatsAppStickerValidator.MAX_ANIMATED_BYTES.toLong()) {
                    success = true
                    break
                }
            }
        } finally {
            scaledFrames.forEach { it.bitmap.recycle() }
        }
        return success
    }

    private fun adjustedDurations(durations: List<Int>): List<Int>? {
        if (durations.isEmpty()) return null
        val safe = durations.map { it.coerceAtLeast(20) }
        val total = safe.sumOf { it.toLong() }
        if (total <= WhatsAppStickerValidator.MAX_DURATION_MS) return safe
        val ratio = WhatsAppStickerValidator.MAX_DURATION_MS.toDouble() / total
        val adjusted = safe.map { max(20, (it * ratio).toInt()) }.toMutableList()
        var sum = adjusted.sumOf { it.toLong() }
        var index = 0
        while (sum > WhatsAppStickerValidator.MAX_DURATION_MS && index < adjusted.size) {
            val reducible = adjusted[index] - 20
            if (reducible > 0) {
                val reduce = min(reducible.toLong(), sum - WhatsAppStickerValidator.MAX_DURATION_MS).toInt()
                adjusted[index] -= reduce
                sum -= reduce
            }
            index++
        }
        return if (sum <= WhatsAppStickerValidator.MAX_DURATION_MS) adjusted else null
    }

    private fun createTrayIcon(context: Context, source: File, staging: File): String? {
        val decoded = decodeFirstFrame(source) ?: return null
        val tray = StickerCreatorEngine.fitToCanvas(decoded, 96)
        if (tray !== decoded) decoded.recycle()
        val png = File(staging, "tray.png")
        try {
            FileOutputStream(png).use { stream ->
                tray.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (png.exists() && png.length() in 1L..TRAY_MAX_BYTES.toLong()) return png.name

            val webp = File(staging, "tray.webp")
            FileOutputStream(webp).use { stream ->
                tray.compress(Bitmap.CompressFormat.WEBP, 80, stream)
            }
            png.delete()
            if (webp.exists() && webp.length() in 1L..TRAY_MAX_BYTES.toLong()) return webp.name
        } catch (_: Exception) {
        } finally {
            tray.recycle()
        }
        return null
    }

    private fun verifyPack(packDir: File, animated: Boolean): PackVerification {
        val packFile = File(packDir, WhatsAppPackStore.PACK_FILE)
        val pack = WhatsAppPackStore.readPack(packFile)
            ?: return PackVerification(
                valid = false,
                reason = "تعذر قراءة ${WhatsAppPackStore.PACK_FILE}",
                file = packFile
            )
        if (pack.animated != animated) {
            return PackVerification(
                valid = false,
                reason = "نوع الحزمة غير متطابق: expected=$animated، actual=${pack.animated}",
                file = packFile
            )
        }
        if (pack.stickers.size !in WhatsAppStickerValidator.MIN_STICKERS..WhatsAppStickerValidator.MAX_STICKERS) {
            return PackVerification(
                valid = false,
                reason = "عدد الملصقات غير صالح: ${pack.stickers.size}",
                file = packFile
            )
        }
        val trayFile = File(packDir, pack.trayImageFile)
        if (!trayFile.exists() || trayFile.length() <= 0L) {
            return PackVerification(
                valid = false,
                reason = "أيقونة الحزمة مفقودة أو فارغة: ${pack.trayImageFile}",
                file = trayFile
            )
        }
        pack.stickers.forEach { asset ->
            val file = File(packDir, asset.fileName)
            if (!file.exists() || file.length() <= 0L) {
                return PackVerification(
                    valid = false,
                    reason = "ملصق مفقود أو فارغ: ${asset.fileName}",
                    file = file
                )
            }
            val info = try {
                WebpParser.parse(file)
            } catch (e: Exception) {
                return PackVerification(
                    valid = false,
                    reason = "تعذر تحليل WebP: ${asset.fileName} — ${e.message}",
                    file = file,
                    exception = e
                )
            }
            if (!info.isWebp) {
                return PackVerification(
                    valid = false,
                    reason = "الملف ليس WebP: ${asset.fileName}",
                    file = file
                )
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val parsedWidth = info.width
            val parsedHeight = info.height
            val decodedWidth = bounds.outWidth
            val decodedHeight = bounds.outHeight
            val widthOk = parsedWidth == STICKER_CANVAS_SIZE || decodedWidth == STICKER_CANVAS_SIZE
            val heightOk = parsedHeight == STICKER_CANVAS_SIZE || decodedHeight == STICKER_CANVAS_SIZE
            if (!widthOk || !heightOk) {
                return PackVerification(
                    valid = false,
                    reason = "أبعاد غير صالحة: ${asset.fileName} " +
                        "parsed=${parsedWidth}x$parsedHeight، decoded=${decodedWidth}x$decodedHeight، " +
                        "expected=${STICKER_CANVAS_SIZE}x$STICKER_CANVAS_SIZE",
                    file = file
                )
            }
            if (animated) {
                if (file.length() > WhatsAppStickerValidator.MAX_ANIMATED_BYTES) {
                    return PackVerification(
                        valid = false,
                        reason = "حجم الملصق المتحرك أكبر من 500KB: ${asset.fileName}=${file.length()} bytes",
                        file = file
                    )
                }
                if (!info.isAnimated) {
                    return PackVerification(
                        valid = false,
                        reason = "الملصق ليس متحركًا: ${asset.fileName}",
                        file = file
                    )
                }
                if (info.totalDurationMs !in 1L..WhatsAppStickerValidator.MAX_DURATION_MS) {
                    return PackVerification(
                        valid = false,
                        reason = "مدة غير صالحة: ${asset.fileName}=${info.totalDurationMs}ms",
                        file = file
                    )
                }
            } else {
                if (file.length() > WhatsAppStickerValidator.MAX_STATIC_BYTES) {
                    return PackVerification(
                        valid = false,
                        reason = "حجم الملصق الثابت أكبر من 100KB: ${asset.fileName}=${file.length()} bytes",
                        file = file
                    )
                }
                if (info.isAnimated) {
                    return PackVerification(
                        valid = false,
                        reason = "الملصق الثابت تم ترميزه كملف متحرك: ${asset.fileName}",
                        file = file
                    )
                }
            }
        }
        return PackVerification(valid = true)
    }

    private fun logVerificationFailure(
        context: Context,
        packId: String,
        packName: String,
        verification: PackVerification,
        animated: Boolean
    ) {
        DiagnosticsRepository.logError(
            context = context,
            operation = "WHATSAPP_PACK_VERIFY",
            stage = DiagnosticStage.EXPORT,
            fileName = verification.file?.name ?: WhatsAppPackStore.PACK_FILE,
            file = verification.file,
            exception = verification.exception,
            customMessage = "pack=$packId، name=$packName، animated=$animated: ${verification.reason}",
            sourceType = if (animated) "ANIMATED_WEBP" else "STATIC_WEBP",
            sourceUri = packId,
            sourceDisplayName = packName
        )
    }

    private data class PackVerification(
        val valid: Boolean,
        val reason: String = "",
        val file: File? = null,
        val exception: Exception? = null
    )

}
