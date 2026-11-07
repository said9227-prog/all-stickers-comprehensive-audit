package com.example.wastickers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** What a .wastickers file actually is, decided from ITS CONTENT, not its name. */
enum class ArchiveKind {
    /** A complete, standalone ZIP archive (even if the name contains .part2). */
    STANDALONE,
    /** One segment of a genuinely split ZIP; the other parts are missing. */
    NEEDS_OTHER_PARTS,
    /** Not a usable archive (corrupt, empty, or contains no stickers). */
    INVALID
}

data class ArchiveInspection(
    val kind: ArchiveKind,
    val stickerCount: Int = 0,
    val hasAnimated: Boolean = false,
    val reason: String? = null,
    val previewEntryName: String? = null,
    val previewBytes: ByteArray? = null
)

/**
 * Lightweight, single-pass content inspection of a .wastickers file.
 *
 * Design constraints (from the audit):
 *  - Never trust the extension alone: a file called pack.part2.wastickers may
 *    be a complete standalone archive, while a file called pack.wastickers may
 *    be a corrupt or split segment. Content decides.
 *  - Never auto-merge parts: a segment is reported with a clear
 *    "needs the other parts" state, no silent concatenation.
 *  - Never convert a SAF Uri to a java.io.File path. The caller supplies an
 *    InputStream from ContentResolver; this class only reads bytes.
 *  - Bounded memory and bounded work, so it can run lazily for visible cards
 *    on Dispatchers.IO without re-reading every file on each refresh.
 */
object WastickersArchiveInspector {

    const val MAX_INSPECT_BYTES = 64L * 1024 * 1024
    const val MAX_PREVIEW_BYTES = 2L * 1024 * 1024
    const val MAX_INSPECT_ENTRIES = 500

    fun inspect(name: String, opener: () -> InputStream?): ArchiveInspection {
        val stream = try {
            opener()
        } catch (_: Exception) {
            null
        } ?: return ArchiveInspection(
            ArchiveKind.INVALID,
            reason = "تعذر فتح الملف عبر ContentResolver"
        )
        val bytes = try {
            stream.use { readBounded(it) }
        } catch (_: Exception) {
            return ArchiveInspection(ArchiveKind.INVALID, reason = "تعذر قراءة الملف")
        }
        return inspectBytes(name, bytes)
    }

    fun inspectBytes(name: String, bytes: ByteArray): ArchiveInspection {
        if (bytes.isEmpty()) {
            return ArchiveInspection(ArchiveKind.INVALID, reason = "الملف فارغ")
        }
        if (bytes.size > MAX_INSPECT_BYTES) {
            return ArchiveInspection(
                ArchiveKind.INVALID,
                reason = "حجم الملف يتجاوز حد الفحص السريع"
            )
        }

        val partMarker = WastickersFileOps.hasPartMarker(name)
        val spanning = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x07.toByte() && bytes[3] == 0x08.toByte()
        val eocd = findEndOfCentralDirectory(bytes)

        // A real multi-volume ZIP points its central directory at another disk.
        val multiDisk = eocd != null && (eocd.diskNumber != 0 || eocd.centralDirectoryDisk != 0)
        if (spanning || multiDisk) {
            return ArchiveInspection(
                ArchiveKind.NEEDS_OTHER_PARTS,
                reason = "هذا جزء من أرشيف ZIP مقسّم — يلزم إحضار بقية الأجزاء، ولا يتم دمجها تلقائياً"
            )
        }

        val parsed = parseEntries(bytes)
        if (eocd == null || !parsed.readable) {
            return if (partMarker) {
                ArchiveInspection(
                    ArchiveKind.NEEDS_OTHER_PARTS,
                    reason = "الملف ليس أرشيفاً مكتملاً ويحمل اسم جزء (.part) — يلزم إحضار بقية الأجزاء"
                )
            } else {
                ArchiveInspection(ArchiveKind.INVALID, reason = "الملف ليس أرشيف ZIP صالحاً")
            }
        }
        if (parsed.stickerCount == 0) {
            return ArchiveInspection(
                ArchiveKind.INVALID,
                reason = "لا يحتوي الأرشيف على ملصقات صالحة"
            )
        }
        return ArchiveInspection(
            kind = ArchiveKind.STANDALONE,
            stickerCount = parsed.stickerCount,
            hasAnimated = parsed.hasAnimated,
            previewEntryName = parsed.previewEntryName,
            previewBytes = parsed.previewBytes
        )
    }

    private data class Parsed(
        val readable: Boolean,
        val stickerCount: Int,
        val hasAnimated: Boolean,
        val previewEntryName: String?,
        val previewBytes: ByteArray?
    )

    private fun parseEntries(bytes: ByteArray): Parsed {
        var count = 0
        var stickerCount = 0
        var hasAnimated = false
        var previewName: String? = null
        var preview: ByteArray? = null
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count++
                    if (count > MAX_INSPECT_ENTRIES) break
                    if (!entry.isDirectory) {
                        val leaf = entry.name.substringAfterLast('/')
                        val isSticker = WastickersArchiveValidator.isStickerName(leaf)
                        if (isSticker) {
                            stickerCount++
                            val payload = readEntryBounded(zip)
                            if (leaf.endsWith(".webm", ignoreCase = true) ||
                                looksAnimatedWebp(payload)
                            ) {
                                hasAnimated = true
                            }
                            // The FIRST sticker is the real preview of THIS
                            // archive. It is carried with the file's stable
                            // identity so a card reused for another archive
                            // can never display the previous pack's image.
                            if (previewName == null && payload.isNotEmpty()) {
                                previewName = leaf
                                preview = payload
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            Parsed(true, stickerCount, hasAnimated, previewName, preview)
        } catch (_: Exception) {
            Parsed(false, stickerCount, hasAnimated, previewName, preview)
        }
    }

    private fun readEntryBounded(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total <= MAX_PREVIEW_BYTES) {
            val read = zip.read(buffer)
            if (read <= 0) break
            total += read
            out.write(buffer, 0, read)
        }
        // Drain large entries so the next entry header can be found.
        if (total > MAX_PREVIEW_BYTES) {
            val sink = ByteArray(8192)
            while (zip.read(sink) > 0) { /* discard */ }
        }
        return out.toByteArray()
    }

    private fun looksAnimatedWebp(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val limit = minOf(bytes.size - 4, 512)
        for (i in 0..limit) {
            if (bytes[i] == 'A'.code.toByte() && bytes[i + 1] == 'N'.code.toByte() &&
                bytes[i + 2] == 'I'.code.toByte() && bytes[i + 3] == 'M'.code.toByte()
            ) return true
        }
        return false
    }

    private data class Eocd(val diskNumber: Int, val centralDirectoryDisk: Int)

    /** Returns null when the archive has no End Of Central Directory record. */
    private fun findEndOfCentralDirectory(bytes: ByteArray): Eocd? {
        val minOffset = maxOf(0, bytes.size - 65557)
        var i = bytes.size - 22
        while (i >= minOffset && i + 22 <= bytes.size) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x05.toByte() && bytes[i + 3] == 0x06.toByte()
            ) {
                val disk = (bytes[i + 4].toInt() and 0xFF) or
                    ((bytes[i + 5].toInt() and 0xFF) shl 8)
                val cdDisk = (bytes[i + 6].toInt() and 0xFF) or
                    ((bytes[i + 7].toInt() and 0xFF) shl 8)
                return Eocd(disk, cdDisk)
            }
            i--
        }
        return null
    }

    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total <= MAX_INSPECT_BYTES) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
