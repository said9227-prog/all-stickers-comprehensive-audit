package com.example.wastickers

import com.example.core.model.StickerType
import com.example.media.MediaDetector
import com.example.storage.SafeZipExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ImportedSticker(
    val file: File,
    val displayName: String,
    val type: StickerType,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long
)

data class ImportedPack(
    val sourceId: String,
    val name: String,
    val author: String,
    val stickers: List<ImportedSticker>
)

/** Opens a SAF/MediaStore Uri. Returns null when access is denied. */
fun interface ArchiveOpener {
    fun open(file: WastickersFile): InputStream?
}

/** Persists one extracted pack (Room in production, a fake in tests). */
fun interface PackWriter {
    suspend fun write(pack: ImportedPack): Long
}

data class FileImportProgress(
    val progress: Float,
    val status: WastickersStatus,
    val message: String? = null
)

data class ImportProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val perFile: Map<String, FileImportProgress> = emptyMap(),
    val isRunning: Boolean = false,
    val isCancelled: Boolean = false
) {
    /** Overall progress = mean of the per-file progress values. */
    val overall: Float
        get() = if (total <= 0) 0f else {
            perFile.values.sumOf { it.progress.toDouble() }
                .div(total.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Safe, bounded multi-file .wastickers import pipeline.
 *
 * - All reading / hashing / inflation happens on Dispatchers.IO.
 * - At most [concurrency] archives are processed in parallel (default 2), so
 *   memory usage stays bounded and the UI thread is never blocked.
 * - Each archive is streamed to a temp file, never held fully in memory.
 * - A corrupt / denied / duplicate archive fails in isolation; the rest keep
 *   running. Resources are released in finally blocks.
 */
class WastickersImporter(
    private val opener: ArchiveOpener,
    private val writer: PackWriter,
    private val hashLog: ImportedHashLog,
    private val tempDirProvider: () -> File,
    private val extractor: SafeZipExtractor = SafeZipExtractor(),
    private val concurrency: Int = DEFAULT_CONCURRENCY
) {

    fun import(files: List<WastickersFile>): Flow<ImportProgress> = channelFlow {
        val total = files.size
        if (total == 0) {
            send(ImportProgress())
            return@channelFlow
        }

        val producerScope = this
        val perFile = ConcurrentHashMap<String, FileImportProgress>()
        val succeeded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)
        val snapshotLock = Mutex()
        // Thread-safe "claim" set: two identical archives in the same batch
        // cannot both pass the duplicate check when running in parallel.
        // Set.add is atomic here (backed by ConcurrentHashMap), and the
        // placeholder is removed in finally as a plain non-suspend operation,
        // which stays reliable even while the flow is being cancelled.
        val pendingHashes: MutableSet<String> =
            Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        fun current(running: Boolean) = ImportProgress(
            total = total,
            completed = completed.get(),
            succeeded = succeeded.get(),
            failed = failed.get(),
            skipped = skipped.get(),
            perFile = HashMap(perFile),
            isRunning = running,
            isCancelled = cancelled.get()
        )

        /** Cheap non-blocking emission; used from extractor progress callbacks. */
        fun update(
            id: String,
            progress: Float,
            status: WastickersStatus,
            message: String? = null
        ) {
            perFile[id] = FileImportProgress(progress.coerceIn(0f, 1f), status, message)
            producerScope.trySend(current(running = true))
        }

        suspend fun snapshot(running: Boolean): ImportProgress =
            snapshotLock.withLock { current(running) }

        suspend fun processFile(file: WastickersFile) {
            var archive: File? = null
            var extractDir: File? = null
            var claimedHash: String? = null
            try {
                update(file.id, 0.02f, WastickersStatus.IMPORTING)

                val source = opener.open(file)
                    ?: throw java.io.IOException("تعذر فتح الملف: الصلاحية مرفوضة أو الملف محذوف")
                val digest = MessageDigest.getInstance("SHA-256")
                val dir = tempDirProvider().apply { if (!exists()) mkdirs() }
                val archiveFile = File(dir, "arc_${System.nanoTime()}.bin")
                archive = archiveFile

                val digestInput = DigestInputStream(source, digest)
                var copied = 0L
                try {
                    FileOutputStream(archiveFile).use { out ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (digestInput.read(buffer).also { len = it } >= 0) {
                            if (len == 0) continue
                            if (!producerScope.isActive) {
                                throw CancellationException("تم إلغاء الاستيراد")
                            }
                            copied += len
                            if (copied > WastickersArchiveValidator.MAX_ARCHIVE_BYTES) {
                                throw SecurityException("حجم الأرشيف يتجاوز الحد المسموح")
                            }
                            out.write(buffer, 0, len)
                            val readRatio = if (file.sizeBytes > 0L) {
                                copied.toDouble() / file.sizeBytes.toDouble()
                            } else 0.0
                            update(
                                file.id,
                                (0.02 + 0.30 * readRatio.coerceAtMost(1.0)).toFloat(),
                                WastickersStatus.IMPORTING
                            )
                        }
                    }
                } finally {
                    try {
                        digestInput.close()
                    } catch (_: Exception) {
                    }
                }

                if (copied == 0L) throw java.io.IOException("الملف فارغ")

                val hash = digest.digest().toHexString()
                // add() is atomic: true means we are the first to claim this
                // content hash in the current batch. A second identical file
                // (or one already imported) is skipped without importing again.
                val claimed = pendingHashes.add(hash)
                if (!claimed || hashLog.contains(hash)) {
                    // Only drop the placeholder if we were the one who added it.
                    if (claimed) pendingHashes.remove(hash)
                    skipped.incrementAndGet()
                    completed.incrementAndGet()
                    update(file.id, 1f, WastickersStatus.DUPLICATE, "ملف مكرر — تم تخطيه")
                    return
                }
                claimedHash = hash

                val report = WastickersArchiveValidator.validate(archiveFile, file.displayName)
                if (!report.ok) {
                    failed.incrementAndGet()
                    completed.incrementAndGet()
                    val status = if (report.needsParts) {
                        WastickersStatus.NEEDS_PARTS
                    } else {
                        WastickersStatus.CORRUPT
                    }
                    update(file.id, 1f, status, report.reason)
                    return
                }

                update(file.id, 0.35f, WastickersStatus.IMPORTING)
                val extractionDir = File(dir, "ext_${System.nanoTime()}")
                extractDir = extractionDir
                val result = extractor.extract(
                    inputStream = archiveFile.inputStream().buffered(),
                    destinationDir = extractionDir,
                    onProgress = { value ->
                        update(
                            file.id,
                            0.35f + 0.60f * value.coerceIn(0f, 1f),
                            WastickersStatus.IMPORTING
                        )
                    },
                    progressTargetBytes = archiveFile.length(),
                    sourceCompressedSize = archiveFile.length(),
                    isCancelled = { !producerScope.isActive },
                    allowedExtensions = WastickersArchiveValidator.STICKER_EXTENSIONS
                )

                val stickers = result.extractedFiles.mapNotNull { item ->
                    val raw = item.file
                    if (!raw.isFile || raw.length() == 0L || raw.length() > MAX_STICKER_BYTES) {
                        null
                    } else {
                        val detected = MediaDetector.detect(raw)
                        if (detected.type == StickerType.UNSUPPORTED) {
                            null
                        } else {
                            ImportedSticker(
                                file = raw,
                                displayName = item.cleanDisplayName,
                                type = detected.type,
                                width = detected.width,
                                height = detected.height,
                                durationMs = detected.durationMs,
                                sizeBytes = raw.length()
                            )
                        }
                    }
                }

                if (stickers.isEmpty()) {
                    failed.incrementAndGet()
                    completed.incrementAndGet()
                    update(file.id, 1f, WastickersStatus.CORRUPT, "لا يحتوي الأرشيف على ملصقات صالحة")
                    return
                }

                val packName = result.packTitle?.takeIf { it.isNotBlank() }
                    ?: WastickersFileOps.packNameOf(file.displayName)

                writer.write(
                    ImportedPack(
                        sourceId = file.id,
                        name = packName,
                        author = result.packAuthor.orEmpty(),
                        stickers = stickers
                    )
                )
                hashLog.add(hash)

                succeeded.incrementAndGet()
                completed.incrementAndGet()
                update(file.id, 1f, WastickersStatus.IMPORTED)
            } catch (e: CancellationException) {
                // Never swallow cancellation: let the collector cancel the flow.
                throw e
            } catch (e: Exception) {
                failed.incrementAndGet()
                completed.incrementAndGet()
                val status = if (e is SecurityException) {
                    WastickersStatus.CORRUPT
                } else {
                    WastickersStatus.FAILED
                }
                update(file.id, 1f, status, e.message)
            } finally {
                // Release temporary resources for every file, success or failure.
                try {
                    archive?.delete()
                } catch (_: Exception) {
                }
                try {
                    extractDir?.deleteRecursively()
                } catch (_: Exception) {
                }
                // Release the in-batch duplicate placeholder. On success the
                // hash is already in hashLog; on failure/cancel this allows a
                // later retry. Plain non-suspend so it runs during cancellation.
                claimedHash?.let { pendingHashes.remove(it) }
            }
        }

        send(snapshot(running = true))
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            for (file in files) {
                launch {
                    semaphore.withPermit { processFile(file) }
                }
            }
        }
        send(snapshot(running = false))
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val DEFAULT_CONCURRENCY = 2
        const val MAX_STICKER_BYTES = 8L * 1024 * 1024
    }
}

private fun ByteArray.toHexString(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(digits[value ushr 4])
        out.append(digits[value and 0x0F])
    }
    return out.toString()
}
