package com.example.wastickers

/**
 * Identity rules for sticker previews.
 *
 * ROOT-CAUSE GUARD: an image must NEVER be keyed by its file name alone. Two
 * different packs almost always contain "1.webp" / "tray.webp" / "s1.png".
 * Every cache key therefore carries (a) the stable pack/archive identity,
 * (b) the sticker identity, and (c) the source of the image bytes. A change
 * to size or modification time produces a different VERSION key, so a stale
 * preview can never be shown after a refresh.
 */
object WastickersPreviewKeys {

    /** Version of a discovered archive: changes when the file is replaced. */
    fun archiveVersion(file: WastickersFile): String =
        "wst|${file.id}|${file.sizeBytes}|${file.lastModified}"

    /** Cache key for a preview extracted from a specific archive entry. */
    fun archivePreview(file: WastickersFile, entryName: String): String =
        archivePreviewKey(archiveVersion(file), entryName)

    /** Same identity rule when only the version string is at hand (ViewModel). */
    fun archivePreviewKey(version: String, entryName: String): String =
        "$version|entry:$entryName"

    /** Cache key for a persisted sticker image inside Room-backed packs. */
    fun stickerImage(packId: Long, stickerId: Long, sourceUri: String): String =
        "pack:$packId|sticker:$stickerId|src:$sourceUri"

    /** True when a cached key still belongs to the given archive version. */
    fun belongsTo(key: String, file: WastickersFile): Boolean =
        key.startsWith(archiveVersion(file) + "|")
}

/**
 * Bounded, thread-safe LRU cache for inspection results / preview payloads.
 * Plain JVM so it can be unit tested; the Android layer stores decoded
 * bitmaps in the same way keyed by [WastickersPreviewKeys].
 */
class WastickersInspectionCache(private val maxEntries: Int = 120) {

    private data class Value(val key: String, val inspection: ArchiveInspection)

    private val map = LinkedHashMap<String, Value>(maxEntries, 0.75f, true)

    @Synchronized
    fun get(key: String): ArchiveInspection? = map[key]?.inspection

    @Synchronized
    fun put(key: String, inspection: ArchiveInspection) {
        map[key] = Value(key, inspection)
        while (map.size > maxEntries) {
            val oldest = map.entries.iterator().next().key
            map.remove(oldest)
        }
    }

    /** Drops results for archives that disappeared or changed on refresh. */
    @Synchronized
    fun retainVersions(versions: Collection<String>) {
        val keep = versions.toHashSet()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next().key
            if (keep.none { key.startsWith(it + "|") }) iterator.remove()
        }
    }

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun size(): Int = map.size
}
