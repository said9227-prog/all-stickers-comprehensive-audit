package com.example.wastickers

import java.io.File
import java.util.zip.ZipFile

/**
 * Integrity + security validation of a .wastickers archive before extraction.
 *
 * It reads only the ZIP central directory (no entry is inflated), so a corrupt
 * or malicious archive is rejected cheaply and without allocating large buffers.
 * Defence in depth: SafeZipExtractor repeats the checks while streaming.
 */
object WastickersArchiveValidator {

    const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024       // 100 MB compressed
    const val MAX_TOTAL_UNCOMPRESSED = 200L * 1024 * 1024  // 200 MB inflated
    const val MAX_SINGLE_FILE_SIZE = 50L * 1024 * 1024     // 50 MB per entry
    const val MAX_ENTRIES = 500
    const val MAX_COMPRESSION_RATIO = 500.0

    val STICKER_EXTENSIONS: Set<String> = setOf("webp", "png", "jpg", "jpeg", "webm")
    private val METADATA_NAMES = setOf("title.txt", "author.txt")

    data class Report(
        val ok: Boolean,
        val reason: String? = null,
        val entryCount: Int = 0,
        val totalUncompressed: Long = 0L,
        val stickerCount: Int = 0,
        /** True when the failure means "the other split parts are missing". */
        val needsParts: Boolean = false
    )

    /** Cheap magic-number check: PK\x03\x04 / PK\x05\x06 / PK\x07\x08. */
    fun isZipMagic(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    (header[2] == 0x03.toByte() && header[3] == 0x04.toByte() ||
                        header[2] == 0x05.toByte() && header[3] == 0x06.toByte() ||
                        header[2] == 0x07.toByte() && header[3] == 0x08.toByte())
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Rejects absolute paths, parent traversal and unsafe separators. */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.length > 255) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains("..") || name.contains("\\")) return false
        if (name.contains(":")) return false
        return true
    }

    fun isMetadataName(name: String): Boolean =
        name.substringAfterLast('/').lowercase() in METADATA_NAMES

    fun isStickerName(name: String): Boolean {
        val leaf = name.substringAfterLast('/')
        val extension = leaf.substringAfterLast('.', "").lowercase()
        return extension in STICKER_EXTENSIONS
    }

    /**
     * [displayName] MUST be the original archive name. The importer streams a
     * SAF Uri into a randomly named temp file (arc_*.bin), so relying on
     * [File.getName] there would hide the .part1/.part2/.part3 marker and a
     * genuine split segment would be misreported as a corrupt standalone pack.
     */
    fun validate(file: File, displayName: String = file.name): Report {
        if (!file.isFile || file.length() == 0L) {
            return Report(false, "الملف فارغ أو غير موجود")
        }
        if (file.length() > MAX_ARCHIVE_BYTES) {
            return Report(false, "حجم الأرشيف يتجاوز الحد المسموح")
        }
        if (!isZipMagic(file)) {
            // A name carrying .part1/.part2/.part3 that is not even a ZIP is
            // a segment of a split archive, NOT a corrupt standalone pack.
            // Never auto-merge; tell the user the other parts are required.
            val needsParts = WastickersFileOps.hasPartMarker(displayName)
            return Report(
                false,
                if (needsParts) {
                    "هذا جزء من أرشيف مقسّم — يلزم إحضار بقية الأجزاء، ولا يتم دمجها تلقائياً"
                } else {
                    "الملف ليس أرشيف ZIP صالحاً"
                },
                needsParts = needsParts
            )
        }

        return try {
            ZipFile(file).use { zip ->
                var count = 0
                var totalUncompressed = 0L
                var stickerCount = 0
                val entries = zip.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count++
                    if (count > MAX_ENTRIES) {
                        return Report(false, "عدد الملفات يتجاوز الحد المسموح ($MAX_ENTRIES)", count)
                    }
                    if (!isSafeEntryName(entry.name)) {
                        return Report(false, "اسم ملف غير آمن (Zip Slip): ${entry.name}", count)
                    }
                    val size = entry.size
                    if (size > MAX_SINGLE_FILE_SIZE) {
                        return Report(false, "حجم ملف داخل الأرشيف يتجاوز الحد المسموح", count)
                    }
                    val uncompressed = if (size > 0L) size else 0L
                    totalUncompressed += uncompressed
                    if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED) {
                        return Report(false, "الحجم الإجمالي بعد فك الضغط يتجاوز الحد المسموح", count)
                    }

                    // Zip bomb: absurd compression ratio between the central
                    // directory sizes. A few MB of slack avoids false positives
                    // on small, legitimately well-compressed sticker packs.
                    val compressed = entry.compressedSize
                    if (compressed > 0L && uncompressed > 0L) {
                        val ratio = uncompressed.toDouble() / compressed.toDouble()
                        if (ratio > MAX_COMPRESSION_RATIO && uncompressed > 4L * 1024 * 1024) {
                            return Report(false, "نسبة ضغط مشبوهة (ZIP Bomb)", count)
                        }
                    }

                    if (!entry.isDirectory && !isMetadataName(entry.name) && isStickerName(entry.name)) {
                        stickerCount++
                    }
                }

                when {
                    count == 0 -> Report(false, "الأرشيف فارغ", count)
                    stickerCount == 0 -> Report(false, "لا يحتوي الأرشيف على ملصقات صالحة", count, totalUncompressed)
                    else -> Report(true, null, count, totalUncompressed, stickerCount)
                }
            }
        } catch (e: Exception) {
            val needsParts = WastickersFileOps.hasPartMarker(displayName)
            Report(
                false,
                if (needsParts) {
                    "جزء أرشيف غير مكتمل — يلزم إحضار بقية الأجزاء: ${e.message ?: ""}"
                } else {
                    "الأرشيف تالف: ${e.message ?: "خطأ غير معروف"}"
                },
                needsParts = needsParts
            )
        }
    }
}
