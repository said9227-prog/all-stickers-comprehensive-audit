package com.example.wastickers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface WastickersDataSource {
    /** Scans every reachable .wastickers archive. Runs on Dispatchers.IO. */
    suspend fun scan(): DiscoveryOutcome

    /** Whether at least one persistent read grant is available. */
    fun hasAnyAccess(): Boolean

    /** Whether the scoped legacy read permission is granted (API <= 32). */
    fun hasLegacyReadPermission(): Boolean
}

/**
 * Multi-source, permission-aware discovery of .wastickers archives.
 *
 * ROOT CAUSE of "files never appear": on Android 13+ (API 33+, targetSdk 36)
 * READ_EXTERNAL_STORAGE is ignored and MediaStore.Files only exposes files
 * this app itself created. Files received from WhatsApp, a browser download or
 * a file manager (e.g. gsgwh.wastickers) are therefore invisible to a plain
 * MediaStore query. The only sanctioned way to reach them is the Storage
 * Access Framework — either a user-granted directory tree or the exact
 * document picked by the user.
 *
 * Sources combined here:
 *  1. MediaStore.Files — scoped READ_EXTERNAL_STORAGE on API <= 32; on API 33+
 *     it is queried without permission but only sees app-owned files, so it is
 *     a supplement, never the primary channel.
 *  2. SAF directory trees chosen by the user, persisted with
 *     takePersistableUriPermission and traversed recursively (DocumentsContract
 *     + probing for providers that do not report the standard directory MIME).
 *  3. Documents picked directly through ACTION_OPEN_DOCUMENT, persisted and
 *     read through DocumentFile (never a java.io.File path).
 *
 * No MANAGE_EXTERNAL_STORAGE / all-files access is requested. A provider that
 * denies access is skipped and reported, never allowed to abort the scan.
 */
class DeviceWastickersScanner(
    private val context: Context,
    private val treePermissionStore: TreePermissionStore,
    private val documentPermissionStore: DocumentPermissionStore,
    private val engine: WastickersDiscoveryEngine = WastickersDiscoveryEngine()
) : WastickersDataSource {

    override suspend fun scan(): DiscoveryOutcome = withContext(Dispatchers.IO) {
        val flat = ArrayList<WastickersFile>()

        // 1. Shared media collection (limited visibility by design on 13+).
        scanMediaStore(flat)

        // 2. Exact documents the user picked — the reliable 13+ channel.
        val (invalidFiles, revokedDocuments) = scanDirectDocuments(flat)

        // 3. User-granted SAF trees, recursively traversed.
        val trees = ArrayList<FolderTreePort>()
        var revokedTrees = 0
        for (treeUri in treePermissionStore.grantedTreeUris()) {
            if (!hasPersistedReadPermission(treeUri)) {
                // The grant was revoked in system settings: forget it instead
                // of pretending it grants access to the whole phone.
                treePermissionStore.remove(treeUri)
                revokedTrees++
                continue
            }
            trees.add(SafTreePort(treeUri))
        }

        val outcome = engine.collect(trees, flat)
        outcome.copy(
            invalidFiles = invalidFiles,
            inaccessibleFolders = outcome.inaccessibleFolders +
                revokedTrees + revokedDocuments
        )
    }

    override fun hasAnyAccess(): Boolean =
        treePermissionStore.hasAny() ||
            documentPermissionStore.hasAny() ||
            hasLegacyReadPermission()

    override fun hasLegacyReadPermission(): Boolean {
        if (Build.VERSION.SDK_INT > 32) return false
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean {
        val target = uri.toString()
        return context.contentResolver.persistedUriPermissions.any { grant ->
            grant.isReadPermission &&
                (grant.uri.toString() == target ||
                    target.startsWith(grant.uri.toString() + "/"))
        }
    }

    /* ------------------------------------------------------------------ */
    /* 1. MediaStore                                                       */
    /* ------------------------------------------------------------------ */

    private fun scanMediaStore(out: MutableList<WastickersFile>) {
        // On API <= 32 a query over shared files requires the scoped read
        // permission. On API 33+ no permission is needed, but MediaStore only
        // returns files owned by this app — still useful for packs exported by
        // the app itself.
        val mayQuery = Build.VERSION.SDK_INT >= 33 || hasLegacyReadPermission()
        if (!mayQuery) return

        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL_LEGACY)
            }

            // Deliberately NO SQL "LIKE '%.wastickers'" selection: its case
            // sensitivity depends on the provider's SQLite collation, and an
            // uppercase .WASTICKERS could be filtered out before Kotlin ever
            // sees it. Every candidate is filtered by the single Kotlin rule
            // WastickersFileOps.isWastickersName instead.
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.RELATIVE_PATH
                )
            } else {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
            }

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else -1

                var rows = 0
                while (cursor.moveToNext() && rows < MAX_MEDIA_ROWS &&
                    out.size < MAX_DOCUMENTS
                ) {
                    rows++
                    val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        cursor.getString(nameIdx)
                    } else null
                    if (!WastickersFileOps.isWastickersName(name)) continue

                    val rowId = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    if (rowId < 0L) continue

                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        cursor.getLong(sizeIdx)
                    } else 0L
                    val modifiedSeconds = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) {
                        cursor.getLong(dateIdx)
                    } else 0L
                    val relativePath = if (pathIdx >= 0 && !cursor.isNull(pathIdx)) {
                        cursor.getString(pathIdx).orEmpty()
                    } else ""

                    val uri = Uri.withAppendedPath(collection, rowId.toString()).toString()
                    out.add(
                        WastickersFile(
                            id = "media:$rowId",
                            uri = uri,
                            displayName = WastickersFileOps.sanitizeName(name),
                            sizeBytes = size,
                            lastModified = modifiedSeconds * 1000L,
                            source = WastickersSource.MEDIA_STORE,
                            location = relativePath.ifBlank {
                                WastickersFileOps.locationOf(uri, WastickersSource.MEDIA_STORE)
                            }
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked or provider restriction: SAF sources still work.
        } catch (_: Exception) {
            // A broken MediaStore provider must not break discovery.
        }
    }

    /* ------------------------------------------------------------------ */
    /* 2. Directly picked documents (ACTION_OPEN_DOCUMENT)                 */
    /* ------------------------------------------------------------------ */

    /** @return pair of (invalid file count, revoked/unreadable grant count). */
    private fun scanDirectDocuments(out: MutableList<WastickersFile>): Pair<Int, Int> {
        var invalid = 0
        var revoked = 0
        for (uri in documentPermissionStore.grantedDocumentUris()) {
            // DocumentFile resolves the SAF Uri through the ContentResolver.
            // Never convert it to a File path — SAF Uris are opaque.
            val name = try {
                DocumentFile.fromSingleUri(context, uri)?.name
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
            if (name == null) {
                // Permission was revoked or the document was deleted.
                documentPermissionStore.remove(uri)
                revoked++
                continue
            }
            if (!WastickersFileOps.isWastickersName(name)) {
                // The picker uses */* (because .wastickers has no registered
                // MIME type), so a wrong file may be selected. Report it as an
                // invalid file instead of silently dropping it.
                invalid++
                continue
            }

            val doc = try {
                DocumentFile.fromSingleUri(context, uri)
            } catch (_: Exception) {
                null
            }
            val size = try {
                doc?.length() ?: 0L
            } catch (_: Exception) {
                0L
            }
            val modified = try {
                doc?.lastModified() ?: 0L
            } catch (_: Exception) {
                0L
            }

            val uriString = uri.toString()
            out.add(
                WastickersFile(
                    id = "doc:$uriString",
                    uri = uriString,
                    displayName = WastickersFileOps.sanitizeName(name),
                    sizeBytes = size,
                    lastModified = modified,
                    source = WastickersSource.USER_DOCUMENT,
                    location = WastickersFileOps.locationOf(
                        uriString, WastickersSource.USER_DOCUMENT
                    )
                )
            )
        }
        return invalid to revoked
    }

    /* ------------------------------------------------------------------ */
    /* 3. SAF tree adapter -> WastickersDiscoveryEngine                    */
    /* ------------------------------------------------------------------ */

    private inner class SafTreePort(private val treeUri: Uri) : FolderTreePort {

        override fun label(): String = treeUri.toString()

        override fun rootId(): String? = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            null
        }

        override fun listChildren(parentId: String): FolderListing? {
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            } catch (_: Exception) {
                return FolderListing(emptyList(), FolderAccess.FAILED)
            }

            val cursor = try {
                context.contentResolver.query(
                    childrenUri,
                    DeviceWastickersScanner.CHILD_PROJECTION,
                    null,
                    null,
                    null
                )
            } catch (_: SecurityException) {
                return FolderListing(emptyList(), FolderAccess.DENIED)
            } catch (_: Exception) {
                return FolderListing(emptyList(), FolderAccess.FAILED)
            } ?: return FolderListing(emptyList(), FolderAccess.DENIED)

            val children = ArrayList<RawDocument>()
            cursor.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (c.moveToNext() && children.size < MAX_DOCUMENTS) {
                    val docId = if (idIdx >= 0 && !c.isNull(idIdx)) {
                        c.getString(idIdx)
                    } else null
                    if (docId.isNullOrEmpty()) continue
                    val name = if (nameIdx >= 0 && !c.isNull(nameIdx)) {
                        c.getString(nameIdx)
                    } else null
                    val modified = if (dateIdx >= 0 && !c.isNull(dateIdx)) {
                        c.getLong(dateIdx)
                    } else 0L
                    val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                        c.getLong(sizeIdx)
                    } else 0L
                    val mime = if (mimeIdx >= 0 && !c.isNull(mimeIdx)) {
                        c.getString(mimeIdx)
                    } else ""
                    children.add(RawDocument(docId, name, size, modified, mime))
                }
            }
            return FolderListing(children, FolderAccess.OK)
        }

        override fun documentUri(documentId: String): String? = try {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
        } catch (_: Exception) {
            null
        }

        override fun locationFor(documentId: String): String =
            documentId.takeLast(96)
    }

    private companion object {
        const val VOLUME_EXTERNAL_LEGACY = "external"
        const val MAX_DOCUMENTS = 5000
        const val MAX_MEDIA_ROWS = 20000

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
    }
}
