package com.example.storage

import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedPackResult(
    val packTitle: String?,
    val packAuthor: String?,
    val extractedFiles: List<ExtractedFileItem>
)

data class ExtractedFileItem(
    val file: File,
    val originalEntryName: String,
    val cleanDisplayName: String
)

/**
 * Streaming, hardened ZIP extractor shared by the existing single-archive
 * importer and the new multi-file WASTICKERS pipeline.
 *
 * All default parameters keep the original `extract(inputStream, destinationDir)`
 * call sites unchanged.
 */
class SafeZipExtractor {

    companion object {
        const val MAX_ENTRIES = 500
        const val MAX_TOTAL_SIZE_BYTES = 200L * 1024 * 1024 // 200 MB
        const val MAX_SINGLE_FILE_SIZE = 50L * 1024 * 1024 // 50 MB
        const val MAX_ENTRY_NAME_LENGTH = 255
        const val MAX_COMPRESSION_RATIO = 500.0
    }

    /**
     * @param onProgress receives a value in [0, 1] while entries are inflated.
     * @param progressTargetBytes rough byte target used to scale the progress.
     * @param sourceCompressedSize compressed size of the source archive, used
     *        for an additional ZIP-bomb ratio guard (0 when unknown).
     * @param isCancelled polled between reads so a cancelled import stops early.
     * @param allowedExtensions when non-null only these extensions are written
     *        (metadata text files are still parsed). Used to reject unexpected
     *        payloads from a .wastickers archive.
     */
    fun extract(
        inputStream: InputStream,
        destinationDir: File,
        onProgress: ((Float) -> Unit)? = null,
        progressTargetBytes: Long = 0L,
        sourceCompressedSize: Long = 0L,
        isCancelled: () -> Boolean = { false },
        allowedExtensions: Set<String>? = null
    ): ExtractedPackResult {
        if (!destinationDir.exists()) destinationDir.mkdirs()
        val destCanonicalPath = destinationDir.canonicalPath

        var packTitle: String? = null
        var packAuthor: String? = null
        val extractedFiles = mutableListOf<ExtractedFileItem>()
        val target = progressTargetBytes.coerceIn(1L, MAX_TOTAL_SIZE_BYTES)

        val zipIn = ZipInputStream(BufferedInputStream(inputStream))
        try {
            var totalBytesRead = 0L
            var totalEntries = 0
            val usedFilenames = mutableMapOf<String, Int>()

            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                if (isCancelled()) throw CancellationException("تم إلغاء فك الضغط")
                totalEntries++
                if (totalEntries > MAX_ENTRIES) {
                    throw SecurityException("تم تجاوز الحد الأقصى المسموح لعدد الملفات في الأرشيف ($MAX_ENTRIES)")
                }

                val entryName = entry.name
                if (entryName.isBlank() || entryName.length > MAX_ENTRY_NAME_LENGTH ||
                    entryName.startsWith("/") || entryName.contains("\\") ||
                    entryName.contains("..")
                ) {
                    throw SecurityException("اسم ملف غير آمن (Zip Slip): $entryName")
                }

                // Zip Slip path traversal security check against the real path.
                val targetFileCheck = File(destinationDir, entryName)
                val testCanonical = targetFileCheck.canonicalPath
                if (!testCanonical.startsWith(destCanonicalPath + File.separator) &&
                    testCanonical != destCanonicalPath
                ) {
                    throw SecurityException("محاولة Zip Slip غير آمنة: $entryName")
                }

                // Declared sizes (when present) provide an early ZIP-bomb guard.
                if (entry.size > MAX_SINGLE_FILE_SIZE) {
                    throw SecurityException("حجم الملف $entryName يتجاوز الحد الأقصى المسموح")
                }
                if (entry.size > 0L && entry.compressedSize > 0L) {
                    val ratio = entry.size.toDouble() / entry.compressedSize.toDouble()
                    if (ratio > MAX_COMPRESSION_RATIO && entry.size > 4L * 1024 * 1024) {
                        throw SecurityException("نسبة ضغط مشبوهة (ZIP Bomb): $entryName")
                    }
                }

                // Normalize path
                val cleanName = File(entryName).name
                if (cleanName.isBlank() || cleanName.startsWith(".") ||
                    cleanName.startsWith("__MACOSX")
                ) {
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }

                // Handle metadata text files
                if (cleanName.equals("title.txt", ignoreCase = true)) {
                    packTitle = zipIn.bufferedReader().readLine()?.trim()
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }
                if (cleanName.equals("author.txt", ignoreCase = true)) {
                    packAuthor = zipIn.bufferedReader().readLine()?.trim()
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }

                if (!entry.isDirectory) {
                    val extension = cleanName.substringAfterLast('.', "").lowercase()
                    if (allowedExtensions != null && extension !in allowedExtensions) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    // Determine unique safe filename for duplicate prevention
                    val baseName = cleanName.substringBeforeLast('.')
                    val count = usedFilenames.getOrDefault(cleanName, 0)
                    usedFilenames[cleanName] = count + 1

                    val safeDiskName = if (count == 0) {
                        cleanName
                    } else {
                        "${baseName}_${String.format(java.util.Locale.US, "%03d", count)}.$extension"
                    }

                    val outFile = File(destinationDir, safeDiskName)
                    val outCanonical = outFile.canonicalPath
                    if (!outCanonical.startsWith(destCanonicalPath + File.separator) &&
                        outCanonical != destCanonicalPath
                    ) {
                        throw SecurityException("محاولة Zip Slip غير آمنة: $entryName")
                    }

                    // Write file with size limits; resources are always released.
                    var fileBytes = 0L
                    val buffer = ByteArray(8192)
                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            fileBytes += len
                            totalBytesRead += len
                            if (fileBytes > MAX_SINGLE_FILE_SIZE) {
                                throw SecurityException("حجم الملف $cleanName يتجاوز الحد الأقصى المسموح")
                            }
                            if (totalBytesRead > MAX_TOTAL_SIZE_BYTES) {
                                throw SecurityException("حجم الأرشيف الإجمالي يتجاوز الحد الأقصى المسموح")
                            }
                            val allowedInflated =
                                sourceCompressedSize.toDouble() * MAX_COMPRESSION_RATIO +
                                    (4L * 1024 * 1024).toDouble()
                            if (sourceCompressedSize > 0L && totalBytesRead.toDouble() > allowedInflated) {
                                throw SecurityException("نسبة ضغط مشبوهة (ZIP Bomb)")
                            }
                            if (isCancelled()) throw CancellationException("تم إلغاء فك الضغط")
                            fos.write(buffer, 0, len)
                            onProgress?.invoke(
                                (totalBytesRead.toDouble() / target.toDouble())
                                    .toFloat().coerceIn(0f, 0.99f)
                            )
                        }
                    }

                    extractedFiles.add(
                        ExtractedFileItem(
                            file = outFile,
                            originalEntryName = entryName,
                            cleanDisplayName = baseName
                        )
                    )
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            onProgress?.invoke(1f)
        } finally {
            // Release the ZIP stream (and therefore the caller's source stream)
            // even when validation throws or the import is cancelled.
            try {
                zipIn.close()
            } catch (_: Exception) {
            }
        }

        return ExtractedPackResult(
            packTitle = packTitle,
            packAuthor = packAuthor,
            extractedFiles = extractedFiles
        )
    }
}
