package com.example.telegram

import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.ValidationResult
import com.example.media.WebpParser
import java.io.File

data class StickerValidationFailure(
    val stickerId: Long,
    val stickerName: String,
    val errorCode: String,
    val reason: String,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val dimensions: String? = null
)

data class PackValidationResult(
    val isValid: Boolean,
    val failures: List<StickerValidationFailure>,
    val validStickers: List<StickerItem>,
    val totalCount: Int
)

object TelegramStickerValidator {

    const val MAX_VIDEO_SIZE_BYTES = 256 * 1024L // 256 KB
    const val MAX_STATIC_SIZE_BYTES = 512 * 1024L // 512 KB
    const val MAX_DURATION_MS = 3000L // 3 seconds
    const val TARGET_SIDE_PX = 512
    const val MAX_FPS = 30.0f
    const val MAX_STICKERS_PER_PACK = 120

    fun validate(file: File, isAnimated: Boolean): ValidationResult {
        if (!file.exists() || file.length() == 0L) {
            return ValidationResult(
                isValid = false,
                errors = listOf("الملف غير موجود أو فارغ")
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val fileSize = file.length()

        if (isAnimated) {
            // Check animated video sticker (WEBM VP9)
            val maxSize = MAX_VIDEO_SIZE_BYTES
            if (fileSize > maxSize) {
                errors.add("حجم الملف (${fileSize / 1024} KB) يتجاوز الحد الأقصى لتيليجرام (256 KB)")
            }

            var width = 0
            var height = 0
            var durationMs = 0L
            var codec = ""
            var hasAudio = false
            var fps = 0f

            // Inspect WEBM via MediaExtractor or file header
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        hasAudio = true
                        errors.add("يحتوي الملف على مسار صوتي (ممنوع في ملصقات تيليجرام)")
                    } else if (mime.startsWith("video/")) {
                        codec = mime
                        if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                            width = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            height = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                        }
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    }
                }
            } catch (_: Exception) {
                // MediaExtractor might not read all custom containers, fallback
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

            // Duration check
            if (durationMs > MAX_DURATION_MS + 100) { // allowance of 100ms tolerance
                errors.add("مدة الحركة (${durationMs}ms) أطول من الحد المسموح (3 ثوانٍ)")
            }

            // Dimension check: One side must be 512, other <= 512
            if (width > 0 && height > 0) {
                val matchesTelegramDimension = (width == TARGET_SIDE_PX && height <= TARGET_SIDE_PX) ||
                        (height == TARGET_SIDE_PX && width <= TARGET_SIDE_PX)
                if (!matchesTelegramDimension) {
                    errors.add("الأبعاد (${width}x${height}) لا تطابق شرط تيليجرام (أحد الأبعاد 512px والآخر 512px أو أقل)")
                }
            }

            // FPS check
            if (fps > MAX_FPS + 1.0f) {
                warnings.add("معدل الإطارات (${fps}fps) أعلى من الموصى به (30fps)")
            }

            return ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                format = "WEBM",
                codec = if (codec.isNotEmpty()) codec else "video/x-vnd.on2.vp9",
                width = width,
                height = height,
                durationMs = durationMs,
                fileSizeBytes = fileSize,
                hasAlpha = true,
                hasAudio = hasAudio,
                fps = fps
            )

        } else {
            // Static sticker check (WEBP or PNG)
            val maxSize = MAX_STATIC_SIZE_BYTES
            if (fileSize > maxSize) {
                errors.add("حجم الملصق الثابت (${fileSize / 1024} KB) أكبر من الحد المسموح (512 KB)")
            }

            // Check dimensions using BitmapFactory or WebpParser
            val webpInfo = WebpParser.parse(file)
            val width: Int
            val height: Int
            val format: String

            if (webpInfo.isWebp) {
                width = webpInfo.width
                height = webpInfo.height
                format = "WEBP"
            } else {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                width = opts.outWidth
                height = opts.outHeight
                format = opts.outMimeType ?: "IMAGE"
            }

            if (width > 0 && height > 0) {
                val matchesTelegramDimension = (width == TARGET_SIDE_PX && height <= TARGET_SIDE_PX) ||
                        (height == TARGET_SIDE_PX && width <= TARGET_SIDE_PX)
                if (!matchesTelegramDimension) {
                    errors.add("الأبعاد (${width}x${height}) غير مطابقة لمعايير تيليجرام (يجب أن يكون أحد الأبعاد 512px)")
                }
            }

            return ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                format = format,
                codec = format,
                width = width,
                height = height,
                durationMs = 0L,
                fileSizeBytes = fileSize,
                hasAlpha = true,
                hasAudio = false
            )
        }
    }

    /**
     * Validates that an emoji is present and valid for Telegram.
     */
    fun validateEmoji(emoji: String?): Boolean {
        if (emoji.isNullOrBlank()) return false
        val trimmed = emoji.trim()
        return trimmed.isNotEmpty()
    }

    /**
     * Telegram pack title: 1-64 characters.
     */
    fun validatePackTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val trimmed = title.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 64
    }

    /**
     * Telegram pack short name:
     * - Must begin with a letter (a-z, A-Z)
     * - Can contain only English letters, numbers, and underscores (a-z, 0-9, _)
     * - Length between 1 and 64 characters
     * - No Arabic characters, spaces, or hyphens allowed!
     */
    fun validatePackShortName(shortName: String?): Boolean {
        if (shortName.isNullOrBlank()) return false
        val regex = "^[a-zA-Z][a-zA-Z0-9_]{0,63}$".toRegex()
        return regex.matches(shortName.trim())
    }

    /**
     * Sanitizes a pack short name to comply with Telegram rules.
     */
    fun sanitizeShortName(name: String): String {
        // Strip non-ascii alphanumeric characters, replace spaces/hyphens with underscore
        var cleaned = name.trim()
            .replace("[\\s-]+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_]".toRegex(), "")
            .lowercase()

        // Ensure it starts with a letter
        if (cleaned.isEmpty() || !cleaned[0].isLetter()) {
            cleaned = "pack_${cleaned}"
        }

        // Limit length to 64
        if (cleaned.length > 64) {
            cleaned = cleaned.substring(0, 64)
        }

        return cleaned
    }

    /**
     * Validates the ENTIRE pack before import to ensure NO invalid sticker or metadata
     * is sent to Telegram, avoiding silent rejection.
     */
    fun validateEntirePackForImport(
        pack: StickerPack,
        stickers: List<StickerItem>
    ): PackValidationResult {
        val failures = mutableListOf<StickerValidationFailure>()
        val validList = mutableListOf<StickerItem>()

        // 1. Pack metadata check
        if (!validatePackTitle(pack.name)) {
            failures.add(
                StickerValidationFailure(
                    stickerId = 0,
                    stickerName = pack.name,
                    errorCode = "INVALID_PACK_NAME",
                    reason = "عنوان الحزمة فارغ أو يتجاوز 64 حرفًا"
                )
            )
        }

        if (!validatePackShortName(pack.shortName)) {
            val suggested = sanitizeShortName(pack.shortName.ifBlank { pack.name })
            failures.add(
                StickerValidationFailure(
                    stickerId = 0,
                    stickerName = pack.shortName,
                    errorCode = "INVALID_PACK_SHORT_NAME",
                    reason = "الاسم القصير للحزمة (${pack.shortName}) غير مطابق لقواعد تيليجرام (يجب أن يبدأ بحرف إنجليزي ويحتوي فقط على أحرف وأرقام و _). المقترح: $suggested"
                )
            )
        }

        // 2. Stickers count check
        if (stickers.isEmpty()) {
            failures.add(
                StickerValidationFailure(
                    stickerId = 0,
                    stickerName = pack.name,
                    errorCode = "EMPTY_PACK",
                    reason = "لا توجد ملصقات في الحزمة لبدء الاستيراد"
                )
            )
            return PackValidationResult(isValid = false, failures = failures, validStickers = emptyList(), totalCount = 0)
        }

        // 3. Inspect every sticker
        for (item in stickers) {
            // Check emoji
            val effectiveEmoji = item.emoji.ifBlank { pack.defaultEmoji }
            if (!validateEmoji(effectiveEmoji)) {
                failures.add(
                    StickerValidationFailure(
                        stickerId = item.id,
                        stickerName = item.displayName,
                        errorCode = "INVALID_EMOJI",
                        reason = "الملصق ليس له أي إيموجي (Emoji) مرتبط به (مطلوب في تيليجرام)"
                    )
                )
                continue
            }

            // Check file presence
            val path = item.outputPath
            if (path == null) {
                failures.add(
                    StickerValidationFailure(
                        stickerId = item.id,
                        stickerName = item.displayName,
                        errorCode = "INVALID_STICKER_FORMAT",
                        reason = "لم يتم تحويل الملصق بعد لمواصفات تيليجرام"
                    )
                )
                continue
            }

            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                failures.add(
                    StickerValidationFailure(
                        stickerId = item.id,
                        stickerName = item.displayName,
                        errorCode = "URI_NOT_READABLE",
                        reason = "ملف الملصق المحول غير موجود على القرص",
                        filePath = path
                    )
                )
                continue
            }

            // Technical validation (Static vs Video)
            val isAnimated = item.type.isAnimated
            val valResult = validate(file, isAnimated)

            if (!valResult.isValid) {
                val errorCode = if (isAnimated) "INVALID_VIDEO_STICKER" else "INVALID_STATIC_STICKER"
                val detailedReason = valResult.errors.joinToString("، ")
                failures.add(
                    StickerValidationFailure(
                        stickerId = item.id,
                        stickerName = item.displayName,
                        errorCode = errorCode,
                        reason = detailedReason,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        dimensions = "${valResult.width}x${valResult.height}"
                    )
                )
            } else {
                validList.add(item)
            }
        }

        return PackValidationResult(
            isValid = failures.isEmpty(),
            failures = failures,
            validStickers = validList,
            totalCount = stickers.size
        )
    }
}
