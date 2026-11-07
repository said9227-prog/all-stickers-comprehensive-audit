package com.example.wastickers

/**
 * Framework-free discovery core. All Android / SAF / ContentResolver details
 * live behind [FolderTreePort], so the real traversal, extension filtering,
 * de-duplication and error isolation can be exercised by plain JVM tests.
 *
 * This is deliberately NOT a java.io.File walk: SAF Uris are opaque and must
 * be resolved through a documents provider. The engine never assumes a real
 * path exists on disk.
 */

/** One node returned by a documents provider. [id] is provider-scoped. */
data class RawDocument(
    val id: String,
    val name: String?,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null
)

enum class FolderAccess { OK, DENIED, FAILED }

data class FolderListing(
    val children: List<RawDocument>,
    val access: FolderAccess = FolderAccess.OK
)

/**
 * Adapter over a user-granted SAF tree. A null return (or a non-OK access)
 * means "this folder cannot be read right now" — e.g. the Uri permission was
 * revoked, the provider disappeared, or the user picked an inaccessible
 * location. It must never abort the whole scan.
 */
interface FolderTreePort {
    fun label(): String
    fun rootId(): String?
    fun listChildren(parentId: String): FolderListing?
    fun documentUri(documentId: String): String?
    fun locationFor(documentId: String): String
}

data class DiscoveryOutcome(
    val files: List<WastickersFile>,
    /** Number of folder nodes that could not be read (permission/provider). */
    val inaccessibleFolders: Int = 0,
    /** Directly picked documents whose name is not a .wastickers archive. */
    val invalidFiles: Int = 0,
    /** Number of ambiguous nodes that were probed as possible directories. */
    val probedFolders: Int = 0,
    val scannedNodes: Int = 0
)

class WastickersDiscoveryEngine(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxDocuments: Int = DEFAULT_MAX_DOCUMENTS,
    private val maxProbes: Int = DEFAULT_MAX_PROBES
) {

    /**
     * Collects .wastickers archives from every readable source and merges them.
     *
     * @param trees user-granted SAF trees (recursively traversed)
     * @param flatFiles already-resolved files from MediaStore and directly
     *                  picked documents (ACTION_OPEN_DOCUMENT)
     */
    fun collect(
        trees: List<FolderTreePort>,
        flatFiles: List<WastickersFile> = emptyList(),
        treeSource: WastickersSource = WastickersSource.SAF_TREE
    ): DiscoveryOutcome {
        val ctx = CollectContext(treeSource)
        for (tree in trees) {
            if (ctx.budgetExhausted()) break
            val root = try {
                tree.rootId()
            } catch (_: Exception) {
                null
            }
            if (root.isNullOrEmpty()) {
                // A grant that can no longer be resolved is reported as an
                // inaccessible folder instead of being treated as "no files".
                ctx.inaccessible++
                continue
            }
            walk(tree, root, 0, ctx)
        }

        val combined = ArrayList<WastickersFile>(flatFiles.size + ctx.files.size)
        combined.addAll(flatFiles)
        combined.addAll(ctx.files)

        return DiscoveryOutcome(
            files = WastickersFileOps.sortNewestFirst(WastickersFileOps.dedupe(combined)),
            inaccessibleFolders = ctx.inaccessible,
            probedFolders = ctx.probes,
            scannedNodes = ctx.scannedNodes
        )
    }

    private fun walk(
        port: FolderTreePort,
        parentId: String,
        depth: Int,
        ctx: CollectContext
    ) {
        if (depth > maxDepth || ctx.budgetExhausted()) return
        if (!ctx.visited.add(port.label() + "|" + parentId)) return

        val listing = safeList(port, parentId, ctx) ?: return
        consume(port, listing.children, depth, ctx)
    }

    private fun consume(
        port: FolderTreePort,
        children: List<RawDocument>,
        depth: Int,
        ctx: CollectContext
    ) {
        for (child in children) {
            if (ctx.budgetExhausted()) return
            ctx.scannedNodes++

            // 1. The single extension rule, applied to every source. Covers
            //    gsgwh.wastickers, GSGWH.WASTICKERS, Pack.WasTickers, Arabic
            //    names and names containing spaces.
            if (WastickersFileOps.isWastickersName(child.name)) {
                val uri = try {
                    port.documentUri(child.id)
                } catch (_: Exception) {
                    null
                }
                if (!uri.isNullOrBlank()) {
                    val location = try {
                        port.locationFor(child.id)
                    } catch (_: Exception) {
                        ""
                    }
                    ctx.files.add(
                        WastickersFile(
                            id = "saf:$uri",
                            uri = uri,
                            displayName = WastickersFileOps.sanitizeName(child.name),
                            sizeBytes = child.sizeBytes,
                            lastModified = child.lastModified,
                            source = ctx.source,
                            location = location.ifBlank {
                                WastickersFileOps.locationOf(uri, ctx.source)
                            }
                        )
                    )
                }
                continue
            }

            // 2. Standard SAF directory marker.
            if (isExplicitDirectory(child)) {
                walk(port, child.id, depth + 1, ctx)
                continue
            }

            // 3. ROOT CAUSE FIX: many OEM / third-party DocumentsProviders do
            //    not return the canonical directory MIME type for subfolders
            //    (empty, "application/octet-stream", or a custom value). Those
            //    folders were previously treated as files, so recursion stopped
            //    at the first level and packs in subfolders were never shown.
            //    Probe ambiguous nodes once: if the provider returns a readable
            //    child listing, treat it as a directory and descend. Probes are
            //    bounded so a broken provider cannot cause unbounded queries.
            if (ctx.probes < maxProbes && shouldProbeAsDirectory(child)) {
                ctx.probes++
                // A probe is a hypothesis, not a folder read: a plain file
                // simply yields no children. Do NOT count it as an
                // inaccessible folder, otherwise the UI would show a false
                // "permission revoked" warning for every ordinary file.
                val probe = try {
                    port.listChildren(child.id)
                } catch (_: Exception) {
                    null
                }
                if (probe != null && probe.access == FolderAccess.OK &&
                    probe.children.isNotEmpty()
                ) {
                    ctx.visited.add(port.label() + "|" + child.id)
                    consume(port, probe.children, depth + 1, ctx)
                }
            }
        }
    }

    private fun safeList(
        port: FolderTreePort,
        parentId: String,
        ctx: CollectContext
    ): FolderListing? = try {
        val listing = port.listChildren(parentId)
        when {
            listing == null -> {
                ctx.inaccessible++
                null
            }
            listing.access != FolderAccess.OK -> {
                ctx.inaccessible++
                null
            }
            else -> listing
        }
    } catch (_: SecurityException) {
        ctx.inaccessible++
        null
    } catch (_: Exception) {
        ctx.inaccessible++
        null
    }

    private fun isExplicitDirectory(doc: RawDocument): Boolean =
        doc.mimeType?.trim()?.equals(DIR_MIME, ignoreCase = true) == true

    private fun shouldProbeAsDirectory(doc: RawDocument): Boolean {
        val mime = doc.mimeType?.trim().orEmpty()
        val name = doc.name.orEmpty()
        // A name with an extension is almost always a regular file. Probing
        // those would issue one ContentResolver query per file and produce
        // false "folder inaccessible" reports on providers that answer with
        // an empty listing for non-directories.
        if (name.substringAfterLast('/').contains('.')) return false
        if (name.isBlank()) return false
        // Providers that omit COLUMN_MIME_TYPE, or report a generic binary
        // type for folders, are handled here.
        return mime.isEmpty() ||
            mime.equals("application/octet-stream", ignoreCase = true) ||
            mime.equals("application/unknown", ignoreCase = true)
    }

    private inner class CollectContext(val source: WastickersSource) {
        val files = ArrayList<WastickersFile>()
        val visited = HashSet<String>()
        var inaccessible = 0
        var probes = 0
        var scannedNodes = 0

        fun budgetExhausted(): Boolean = files.size >= maxDocuments
    }

    private companion object {
        const val DIR_MIME = "vnd.android.document/directory"
        const val DEFAULT_MAX_DEPTH = 12
        const val DEFAULT_MAX_DOCUMENTS = 5000
        const val DEFAULT_MAX_PROBES = 256
    }
}
