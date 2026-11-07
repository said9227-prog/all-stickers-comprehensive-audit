package com.example.wastickers

import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where a .wastickers file was discovered. */
enum class WastickersSource {
    MEDIA_STORE,
    SAF_TREE,
    /** A document picked directly by the user through ACTION_OPEN_DOCUMENT. */
    USER_DOCUMENT
}

/** Lifecycle/status of a discovered archive. */
enum class WastickersStatus {
    READY,
    IMPORTING,
    IMPORTED,
    DUPLICATE,
    CORRUPT,
    /** A genuine split-archive segment: it needs the other parts. */
    NEEDS_PARTS,
    FAILED
}

/**
 * A single .wastickers archive that can be reached on the device.
 *
 * [id] is stable across scans (a content Uri string, prefixed by its source).
 * [firstSeenAt] and [isNew] come from the persistent discovery history, so the
 * NEW badge survives an app restart and old files are never flagged as new again.
 * [location] is a human-readable hint (SAF path / MediaStore relative path)
 * shown in the UI; it is never used as a real java.io.File path.
 */
data class WastickersFile(
    val id: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val source: WastickersSource,
    val location: String = "",
    val firstSeenAt: Long = 0L,
    val isNew: Boolean = false,
    val status: WastickersStatus = WastickersStatus.READY
)

/** Persisted discovery record for one archive. */
data class DiscoveryRecord(
    val id: String,
    val firstSeenAt: Long,
    val isNew: Boolean,
    val lastSeenAt: Long
)

/** Result of merging a fresh scan with the persisted discovery history. */
data class DiscoveryMerge(
    val files: List<WastickersFile>,
    val records: Map<String, DiscoveryRecord>,
    val newIds: Set<String>
)

/**
 * End-of-batch result for the UI. Pure JVM so the numbers shown to the user
 * can be verified against the real ImportProgress rather than guessed.
 */
data class ImportReport(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    /** Genuine split segments: not a failure, but nothing was imported. */
    val needsParts: Int,
    val finished: Boolean,
    val cancelled: Boolean
) {
    val hasIssues: Boolean get() = failed > 0 || skipped > 0 || needsParts > 0
}

/**
 * Pure, JVM-testable operations: discovery diffing, de-duplication, sorting,
 * searching, selection and formatting. No Android classes are used here.
 */
object WastickersFileOps {

    const val EXTENSION = ".wastickers"

    /**
     * Matches any file whose name ends in .wastickers, regardless of case,
     * Arabic characters or spaces. Leading/trailing whitespace is ignored.
     * This is the ONE and ONLY extension rule used by every discovery source,
     * so MediaStore, SAF trees and directly picked documents behave identically.
     */
    fun isWastickersName(name: String?): Boolean {
        if (name == null) return false
        // A single rule for every source. Leading/trailing whitespace, BOM and
        // zero-width marks are stripped so names copied from chat apps or
        // produced by OEM providers are not silently dropped. Everything else
        // (dots, .part1/.part2/.part3, Arabic, spaces, symbols, long names)
        // is intentionally preserved: the extension is the ONLY criterion.
        val cleaned = name
            .trim()
            .trim('\uFEFF', '\u200B', '\u200C', '\u200D', '\u2060')
            .trim()
        return cleaned.endsWith(EXTENSION, ignoreCase = true)
    }

    /**
     * True when a name carries a multi-part marker. This is ONLY a hint: a
     * file named pack.part2.wastickers can still be a perfectly valid
     * standalone archive. The actual decision is made by inspecting content
     * in WastickersArchiveInspector, never by this marker alone.
     */
    fun hasPartMarker(name: String?): Boolean {
        if (name == null) return false
        val cleaned = name.trim()
        val stem = if (cleaned.endsWith(EXTENSION, ignoreCase = true)) {
            cleaned.dropLast(EXTENSION.length)
        } else {
            cleaned
        }
        return PART_MARKER.containsMatchIn(stem.lowercase(Locale.ROOT))
    }

    /**
     * Human-friendly pack name derived from the file name. Multi-part markers
     * are stripped so "Rayn_Ariel tgtowabot.part2.wastickers" reads as
     * "Rayn_Ariel tgtowabot" instead of showing a misleading "part2".
     */
    fun packNameOf(name: String?): String {
        if (name.isNullOrBlank()) return "حزمة ملصقات"
        var stem = sanitizeName(name)
        if (stem.endsWith(EXTENSION, ignoreCase = true)) {
            stem = stem.dropLast(EXTENSION.length)
        }
        stem = PART_MARKER.replace(stem, "").trim().trim('_', '-', '.', ' ')
        return stem.ifBlank { "حزمة ملصقات" }
    }

    // A separator is REQUIRED before numeric-only markers (.001/.002, .z01).
    // Otherwise a perfectly normal pack named "cats123.wastickers" would be
    // mistaken for a split segment and shown as "needs the other parts".
    private val PART_MARKER = Regex(
        "(?i)(?:[._-]?part[._-]?\\d+|[._-]z\\d{2}|[._-]\\d{3}|[._-]?split[._-]?\\d*)$"
    )

    fun sanitizeName(name: String?): String {
        if (name.isNullOrBlank()) return "sticker$EXTENSION"
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        return leaf.trim().ifBlank { "sticker$EXTENSION" }
    }

    /**
     * Removes duplicates by stable id and by content fingerprint. The same
     * physical file is often returned by both MediaStore and a SAF tree, so a
     * fingerprint (name + size + modification time) is used to collapse those.
     *
     * MediaStore reports DATE_MODIFIED in seconds while SAF reports epoch
     * milliseconds; the timestamp is therefore normalised to whole seconds so
     * one file seen through two sources collapses into a single card.
     */
    fun dedupe(files: List<WastickersFile>): List<WastickersFile> {
        val seenIds = HashSet<String>(files.size)
        val fingerprintOwner = HashMap<String, String>(files.size)
        val result = ArrayList<WastickersFile>(files.size)
        for (file in files) {
            // The Uri-based id is the primary identity and is ALWAYS honoured.
            if (!seenIds.add(file.id)) continue
            val fingerprint = contentFingerprint(file)
            if (fingerprint != null && fingerprintOwner.containsKey(fingerprint)) {
                // Collapse the same physical file seen through two sources.
                // Only possible when BOTH providers reported usable metadata.
                continue
            }
            if (fingerprint != null) fingerprintOwner[fingerprint] = file.id
            result.add(file)
        }
        return result
    }

    /**
     * A fingerprint is deliberately NOT produced when a provider omits size or
     * modification time. Many DocumentsProviders return 0 for both; using
     * "name|0|0" would collapse two DIFFERENT files that merely share a name
     * (e.g. pack.part1.wastickers in Download and in a Telegram folder), which
     * is exactly the "some files never appear" symptom. In that case the stable
     * per-source Uri id keeps both cards.
     */
    private fun contentFingerprint(file: WastickersFile): String? {
        if (file.sizeBytes <= 0L || file.lastModified <= 0L) return null
        val name = file.displayName.trim().lowercase(Locale.ROOT)
        return "$name|${file.sizeBytes}|${file.lastModified / 1000L}"
    }

    fun sortNewestFirst(files: List<WastickersFile>): List<WastickersFile> =
        files.sortedWith(
            compareByDescending<WastickersFile> { it.lastModified }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )

    /**
     * SMART REFRESH ordering.
     *
     * Newly discovered archives (ids in [newIds]) are placed at the top, newest
     * discovery first; everything else keeps its previous relative order and
     * is sorted by modification date. No file is dropped, no fake date is
     * invented for providers that report 0, and the stable id is the final
     * tie-breaker so the order is deterministic across refreshes.
     */
    fun orderForRefresh(files: List<WastickersFile>, newIds: Set<String>): List<WastickersFile> {
        if (files.isEmpty()) return files
        return files.sortedWith(
            compareByDescending<WastickersFile> { if (it.id in newIds) 1 else 0 }
                .thenByDescending { if (it.id in newIds) it.firstSeenAt else 0L }
                .thenByDescending { it.lastModified }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }

    /** Builds the end-of-batch summary from the real per-file progress map. */
    fun buildImportReport(progress: ImportProgress): ImportReport = ImportReport(
        total = progress.total,
        succeeded = progress.succeeded,
        failed = progress.failed,
        skipped = progress.skipped,
        needsParts = progress.perFile.values.count {
            it.status == WastickersStatus.NEEDS_PARTS
        },
        finished = !progress.isRunning,
        cancelled = progress.isCancelled
    )

    /** Name of the archive currently being imported, for the progress UI. */
    fun currentImportLabel(files: List<WastickersFile>, progress: ImportProgress?): String? {
        if (progress == null || !progress.isRunning) return null
        val activeId = progress.perFile.entries
            .firstOrNull { it.value.status == WastickersStatus.IMPORTING }
            ?.key ?: return null
        return files.firstOrNull { it.id == activeId }
            ?.let { packNameOf(it.displayName) }
    }

    /**
     * Live search by file name AND derived pack name. Delegates to the
     * pre-normalised in-memory index so Arabic/English, case, diacritics and
     * insignificant whitespace differences all behave consistently.
     *
     * The expensive normalisation is performed ONCE per immutable list snapshot
     * and reused for every keystroke. Callers pass the same List instance until
     * a refresh produces a new one, which is exactly how the ViewModel's
     * debounced query flow works. Rebuilding 5000 haystacks on each keystroke
     * would defeat the whole point of an in-memory index.
     */
    @Volatile
    private var indexSource: List<WastickersFile>? = null

    @Volatile
    private var indexValue: WastickersSearchIndex? = null

    @Synchronized
    private fun indexFor(files: List<WastickersFile>): WastickersSearchIndex {
        val cached = indexValue
        val source = indexSource
        if (cached != null && source === files && source.size == files.size) return cached
        val built = WastickersSearchIndex(files)
        indexSource = files
        indexValue = built
        return built
    }

    fun filterByQuery(files: List<WastickersFile>, query: String): List<WastickersFile> =
        indexFor(files).search(query)

    /**
     * Builds a readable location hint from a content Uri. Never treats the Uri
     * as a filesystem path: SAF Uris are opaque and must be opened through the
     * ContentResolver. Only used for display.
     */
    fun locationOf(uri: String, source: WastickersSource): String {
        if (uri.isBlank()) return ""
        return try {
            if (!uri.startsWith("content://")) return uri.takeLast(96)
            val rest = uri.removePrefix("content://")
            val authority = rest.substringBefore('/')
            val rawPath = rest.substringAfter('/', "")
            val decoded = URLDecoder.decode(rawPath, "UTF-8")
            val leaf = decoded.takeLast(96).ifBlank { authority }
            when (source) {
                WastickersSource.MEDIA_STORE -> "MediaStore / $leaf"
                WastickersSource.SAF_TREE -> leaf
                WastickersSource.USER_DOCUMENT -> leaf
            }
        } catch (_: Exception) {
            uri.takeLast(96)
        }
    }

    /**
     * Merges a scan into the persistent history.
     *
     * - A file that has never been seen gets firstSeenAt = now, isNew = true.
     * - An already known file keeps its original firstSeenAt but is no longer
     *   NEW: the badge is shown once, on the scan that first discovered it.
     *   This also holds after an app restart, so old files are never re-flagged.
     * - Records of files that disappeared are kept on purpose, so a returning
     *   file is never reported as new again.
     */
    fun mergeDiscovery(
        previous: Map<String, DiscoveryRecord>,
        scanned: List<WastickersFile>,
        now: Long
    ): DiscoveryMerge {
        val merged = LinkedHashMap<String, DiscoveryRecord>(previous)
        val newIds = LinkedHashSet<String>()
        val files = ArrayList<WastickersFile>(scanned.size)

        for (file in scanned) {
            val record = previous[file.id]
            if (record == null) {
                merged[file.id] = DiscoveryRecord(
                    id = file.id,
                    firstSeenAt = now,
                    isNew = true,
                    lastSeenAt = now
                )
                newIds.add(file.id)
                files.add(file.copy(firstSeenAt = now, isNew = true))
            } else {
                merged[file.id] = record.copy(isNew = false, lastSeenAt = now)
                files.add(
                    file.copy(
                        firstSeenAt = record.firstSeenAt,
                        isNew = false
                    )
                )
            }
        }
        return DiscoveryMerge(files = files, records = merged, newIds = newIds)
    }

    fun markSeen(
        records: Map<String, DiscoveryRecord>,
        ids: Collection<String>
    ): Map<String, DiscoveryRecord> {
        if (ids.isEmpty()) return records
        val target = ids.toHashSet()
        val updated = LinkedHashMap<String, DiscoveryRecord>(records)
        for ((id, record) in records) {
            if (target.contains(id) && record.isNew) {
                updated[id] = record.copy(isNew = false)
            }
        }
        return updated
    }

    fun toggleSelection(current: Set<String>, id: String): Set<String> {
        val next = current.toMutableSet()
        if (!next.add(id)) next.remove(id)
        return next
    }

    fun selectAll(current: Set<String>, ids: Collection<String>): Set<String> =
        current.toHashSet().apply { addAll(ids) }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        }
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    /** Stable numeric date so it reads well in both Arabic and English. */
    fun formatDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "-"
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(epochMillis))
    }

    /* --- Pure persistence codec for direct document Uri grants ------------
     * Keeping encode/decode here (instead of inside the SharedPreferences
     * class) lets the "survives app restart" behaviour be verified with a
     * plain JVM unit test, while the store still uses it for real persistence.
     * --------------------------------------------------------------------- */
    fun encodeUriList(uris: Collection<String>): String =
        uris.filter { it.isNotBlank() }.joinToString(URI_SEP.toString()) { encodeHex(it) }

    fun decodeUriList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(URI_SEP)
            .mapNotNull { decodeHex(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private const val URI_SEP = '\u001D'
    private const val HEX = "0123456789abcdef"

    private fun encodeHex(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val v = byte.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private fun decodeHex(encoded: String): String? {
        if (encoded.length % 2 != 0) return null
        val bytes = ByteArray(encoded.length / 2)
        var i = 0
        while (i < bytes.size) {
            val high = HEX.indexOf(encoded[i * 2])
            val low = HEX.indexOf(encoded[i * 2 + 1])
            if (high < 0 || low < 0) return null
            bytes[i] = ((high shl 4) or low).toByte()
            i++
        }
        return String(bytes, Charsets.UTF_8)
    }
}
