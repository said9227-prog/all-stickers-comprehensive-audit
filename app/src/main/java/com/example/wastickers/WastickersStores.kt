package com.example.wastickers

import android.content.Context
import android.net.Uri

/* --------------------------------------------------------------------------
 * Small hex codec. Ids are content Uris and can contain almost any character,
 * so they are hex-encoded before being written into a single preference value.
 * This stays pure Kotlin (no android.util.Base64 / java.util.Base64) which
 * keeps it usable on the project's minSdk 24 and easy to unit test.
 * -------------------------------------------------------------------------- */
private const val HEX = "0123456789abcdef"

private fun encodeId(id: String): String {
    val bytes = id.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        out.append(HEX[value ushr 4])
        out.append(HEX[value and 0x0F])
    }
    return out.toString()
}

private fun decodeId(encoded: String): String {
    if (encoded.length % 2 != 0) return ""
    val bytes = ByteArray(encoded.length / 2)
    var index = 0
    while (index < bytes.size) {
        val high = HEX.indexOf(encoded[index * 2])
        val low = HEX.indexOf(encoded[index * 2 + 1])
        if (high < 0 || low < 0) return ""
        bytes[index] = ((high shl 4) or low).toByte()
        index++
    }
    return String(bytes, Charsets.UTF_8)
}

/**
 * Persistent discovery history. Survives process death, so the NEW badge does
 * not disappear when the app is closed, and old files are never flagged again.
 */
class DiscoveryHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    fun load(): Map<String, DiscoveryRecord> {
        val raw = prefs.getString(KEY_RECORDS, null).orEmpty()
        if (raw.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, DiscoveryRecord>()
        for (line in raw.split(RECORD_SEP)) {
            if (line.isEmpty()) continue
            val parts = line.split(FIELD_SEP)
            if (parts.size != 4) continue
            val id = decodeId(parts[0])
            if (id.isEmpty()) continue
            val firstSeenAt = parts[1].toLongOrNull() ?: continue
            val isNew = parts[2] == "1"
            val lastSeenAt = parts[3].toLongOrNull() ?: firstSeenAt
            result[id] = DiscoveryRecord(id, firstSeenAt, isNew, lastSeenAt)
        }
        return result
    }

    fun save(records: Map<String, DiscoveryRecord>) {
        val encoded = buildString {
            for ((id, record) in records) {
                if (isNotEmpty()) append(RECORD_SEP)
                append(encodeId(id))
                append(FIELD_SEP)
                append(record.firstSeenAt)
                append(FIELD_SEP)
                append(if (record.isNew) '1' else '0')
                append(FIELD_SEP)
                append(record.lastSeenAt)
            }
        }
        prefs.edit().putString(KEY_RECORDS, encoded).commit()
    }

    /** User acknowledgement: clears NEW but keeps the discovery date. */
    fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        save(WastickersFileOps.markSeen(load(), ids))
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).commit()
    }

    private companion object {
        const val PREFS_NAME = "wastickers_discovery"
        const val KEY_RECORDS = "records_v1"
        const val RECORD_SEP = '\u001E'
        const val FIELD_SEP = '\u001F'
    }
}

/**
 * Content hashes of archives that were already imported. Used to prevent
 * importing the same pack twice (and to collapse duplicates inside one batch).
 */
interface ImportedHashLog {
    fun contains(hash: String): Boolean
    fun add(hash: String)
    fun all(): Set<String>
}

class SharedPrefsImportedHashLog(context: Context) : ImportedHashLog {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Synchronized so concurrent imports cannot lose hashes through a
    // read-modify-write race. @Synchronized is reentrant, so contains() may
    // safely call all() on the same instance.
    @Synchronized
    override fun contains(hash: String): Boolean = all().contains(hash)

    @Synchronized
    override fun add(hash: String) {
        val updated = all().toHashSet()
        updated.add(hash)
        prefs.edit().putStringSet(KEY_HASHES, updated).commit()
    }

    @Synchronized
    override fun all(): Set<String> =
        prefs.getStringSet(KEY_HASHES, null)?.toHashSet() ?: emptySet()

    private companion object {
        const val PREFS_NAME = "wastickers_imports"
        const val KEY_HASHES = "hashes_v1"
    }
}

/** In-memory log used by unit tests. */
class InMemoryImportedHashLog : ImportedHashLog {
    private val hashes = HashSet<String>()
    override fun contains(hash: String): Boolean = synchronized(hashes) { hashes.contains(hash) }
    override fun add(hash: String) { synchronized(hashes) { hashes.add(hash) } }
    override fun all(): Set<String> = synchronized(hashes) { hashes.toHashSet() }
}

/** Persisted Storage Access Framework tree Uri grants. */
class TreePermissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    @Synchronized
    fun grantedTreeUris(): List<Uri> =
        prefs.getStringSet(KEY_URIS, null)
            ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            ?: emptyList()

    @Synchronized
    fun add(uri: Uri) {
        val updated = grantedTreeUris().map { it.toString() }.toHashSet()
        updated.add(uri.toString())
        prefs.edit().putStringSet(KEY_URIS, updated).commit()
    }

    @Synchronized
    fun remove(uri: Uri) {
        val updated = grantedTreeUris().map { it.toString() }.toHashSet()
        updated.remove(uri.toString())
        prefs.edit().putStringSet(KEY_URIS, updated).commit()
    }

    @Synchronized
    fun hasAny(): Boolean = grantedTreeUris().isNotEmpty()

    private companion object {
        const val PREFS_NAME = "wastickers_tree_permissions"
        const val KEY_URIS = "tree_uris_v1"
    }
}

/**
 * Persisted grants for documents the user picked directly with
 * ACTION_OPEN_DOCUMENT. This is the most reliable discovery channel on
 * Android 13+ where MediaStore cannot see files owned by other apps: the user
 * points at the exact .wastickers file and the read grant is kept across
 * restarts with takePersistableUriPermission.
 *
 * Values are hex-encoded through WastickersFileOps so Uris containing
 * Arabic characters, spaces or separators survive storage intact.
 */
class DocumentPermissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    @Synchronized
    fun grantedDocumentUris(): List<Uri> =
        WastickersFileOps.decodeUriList(prefs.getString(KEY_URIS, null))
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

    @Synchronized
    fun addAll(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val updated = LinkedHashSet(
            WastickersFileOps.decodeUriList(prefs.getString(KEY_URIS, null))
        )
        updated.addAll(uris.map { it.toString() })
        prefs.edit()
            .putString(KEY_URIS, WastickersFileOps.encodeUriList(updated))
            .commit()
    }

    @Synchronized
    fun remove(uri: Uri) {
        val updated = WastickersFileOps.decodeUriList(prefs.getString(KEY_URIS, null))
            .filter { it != uri.toString() }
        prefs.edit()
            .putString(KEY_URIS, WastickersFileOps.encodeUriList(updated))
            .commit()
    }

    @Synchronized
    fun hasAny(): Boolean = grantedDocumentUris().isNotEmpty()

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_URIS).commit()
    }

    private companion object {
        const val PREFS_NAME = "wastickers_document_permissions"
        const val KEY_URIS = "document_uris_v1"
    }
}
