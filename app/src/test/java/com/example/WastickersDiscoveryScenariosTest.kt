package com.example

import com.example.wastickers.DiscoveryRecord
import com.example.wastickers.FolderAccess
import com.example.wastickers.FolderListing
import com.example.wastickers.FolderTreePort
import com.example.wastickers.RawDocument
import com.example.wastickers.WastickersArchiveValidator
import com.example.wastickers.WastickersDiscoveryEngine
import com.example.wastickers.WastickersFile
import com.example.wastickers.WastickersFileOps
import com.example.wastickers.WastickersSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The 13 acceptance scenarios for .wastickers discovery and import.
 *
 * These are NOT just string-matcher tests: every discovery scenario drives the
 * real WastickersDiscoveryEngine through a fake documents provider that models
 * SAF trees with subfolders, denied folders, lost grants and non-standard MIME
 * types. Import scenarios drive the real WastickersArchiveValidator. The
 * full Flow-based import pipeline is additionally covered by
 * WastickersImporterTest, and real-device SAF integration by
 * WastickersStorageInstrumentedTest (androidTest).
 */
class WastickersDiscoveryScenariosTest {

    private lateinit var tempDir: File
    private val createdFiles = ArrayList<File>()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("wst-scenario").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        createdFiles.forEach { runCatching { it.delete() } }
    }

    /* ------------------------------------------------------------------ */
    /* Fake SAF documents provider                                        */
    /* ------------------------------------------------------------------ */

    private val dirMime = "vnd.android.document/directory"

    private fun doc(
        id: String,
        name: String,
        mime: String = "application/octet-stream",
        size: Long = 2048L,
        modified: Long = 1_700_000_000_000L
    ) = RawDocument(id, name, size, modified, mime)

    private class FakeTree(
        private val treeLabel: String,
        private val grantRoot: Boolean = true
    ) : FolderTreePort {
        val nodes = LinkedHashMap<String, MutableList<RawDocument>>()
        val denied = HashSet<String>()

        fun add(parentId: String, vararg children: RawDocument) {
            nodes.getOrPut(parentId) { ArrayList() }.addAll(children)
        }

        override fun label(): String = treeLabel
        override fun rootId(): String? = if (grantRoot) "root" else null
        override fun listChildren(parentId: String): FolderListing? {
            if (parentId in denied) {
                return FolderListing(emptyList(), FolderAccess.DENIED)
            }
            val children = nodes[parentId] ?: return null
            return FolderListing(children, FolderAccess.OK)
        }
        override fun documentUri(documentId: String): String? =
            "content://$treeLabel/document/$documentId"
        override fun locationFor(documentId: String): String =
            "Download/Stickers/$documentId"
    }

    private fun engine() = WastickersDiscoveryEngine()

    private fun names(outcome: com.example.wastickers.DiscoveryOutcome) =
        outcome.files.map { it.displayName }

    /* ------------------------------------------------------------------ */
    /* Archive fixtures                                                   */
    /* ------------------------------------------------------------------ */

    private fun webpBytes(): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0, 0, 0, 0, 'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(),
        'P'.code.toByte()
    ) + ByteArray(64) { 1 }

    private fun zipFile(vararg entries: Pair<String, ByteArray>): File {
        val target = File(tempDir, "arc_${System.nanoTime()}.zip")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        createdFiles.add(target)
        return target
    }

    private fun rawFile(bytes: ByteArray): File {
        val target = File(tempDir, "raw_${System.nanoTime()}.bin")
        target.writeBytes(bytes)
        createdFiles.add(target)
        return target
    }

    /* ================================================================== */
    /* 1 - 9 : discovery                                                   */
    /* ================================================================== */

    @Test
    fun `01 discovers gsgwh wastickres`() {
        val tree = FakeTree("primary")
        tree.add("root", doc("d1", "gsgwh.wastickers"))

        val outcome = engine().collect(listOf(tree))

        assertTrue("gsgwh.wastickers 应被发现", names(outcome).contains("gsgwh.wastickers"))
        assertEquals(1, outcome.files.size)
        val found = outcome.files.single()
        assertTrue(found.uri.startsWith("content://"))
        assertEquals(WastickersSource.SAF_TREE, found.source)
        assertTrue(found.location.isNotBlank())
    }

    @Test
    fun `02 discovers uppercase WASTICKERS extension`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("d1", "GSGWH.WASTICKERS"),
            doc("d2", "Pack.WasTickers"),
            doc("d3", "  spaced.WASTICKERS  ")
        )

        val outcome = engine().collect(listOf(tree))

        assertEquals(3, outcome.files.size)
        assertTrue(names(outcome).contains("GSGWH.WASTICKERS"))
        assertTrue(names(outcome).contains("Pack.WasTickers"))
        assertTrue(names(outcome).contains("spaced.WASTICKERS"))
    }

    @Test
    fun `03 discovers files inside subfolders including non standard directory MIME`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("sub", "Stickers", mime = dirMime),
            doc("rootfile", "top.wastickers")
        )
        tree.add(
            "sub",
            doc("deep", "Packs 2024", mime = "application/octet-stream"),
            doc("note", "readme.txt", mime = "text/plain")
        )
        tree.add("deep", doc("nested", "حزمة في مجلد.wastickers"))

        val outcome = engine().collect(listOf(tree))

        assertEquals(2, outcome.files.size)
        assertTrue(names(outcome).contains("top.wastickers"))
        assertTrue(names(outcome).contains("حزمة في مجلد.wastickers"))
        // The ambiguous node had to be probed to reach the nested pack.
        assertTrue(outcome.probedFolders >= 1)
    }

    @Test
    fun `04 discovers Arabic filenames`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("d1", "ملصقاتي.wastickers"),
            doc("d2", "حزمة القطط والكلاب.wastickers")
        )

        val outcome = engine().collect(listOf(tree))

        assertEquals(2, outcome.files.size)
        assertTrue(names(outcome).contains("ملصقاتي.wastickers"))
        assertTrue(names(outcome).contains("حزمة القطط والكلاب.wastickers"))
    }

    @Test
    fun `05 discovers filenames containing spaces`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("d1", "My Sticker Pack.wastickers"),
            doc("d2", "pack (copy) 2024.wastickers")
        )

        val outcome = engine().collect(listOf(tree))

        assertEquals(2, outcome.files.size)
        assertTrue(names(outcome).contains("My Sticker Pack.wastickers"))
        assertTrue(names(outcome).contains("pack (copy) 2024.wastickers"))
    }

    @Test
    fun `06 does not show unwanted extensions`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("d1", "photo.png", mime = "image/png"),
            doc("d2", "notes.txt", mime = "text/plain"),
            doc("d3", "archive.zip", mime = "application/zip"),
            doc("d4", "sticker.wastickers.bak", mime = "application/octet-stream"),
            doc("d5", "real one.wastickers")
        )

        val outcome = engine().collect(listOf(tree))

        assertEquals(1, outcome.files.size)
        assertEquals("real one.wastickers", outcome.files.single().displayName)
    }

    @Test
    fun `07 handles inaccessible folder without crashing the scan`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            doc("okDir", "Good", mime = dirMime),
            doc("badDir", "Restricted", mime = dirMime)
        )
        tree.denied.add("badDir")
        tree.add("okDir", doc("good", "visible.wastickers"))
        tree.add("badDir", doc("hidden", "hidden.wastickers"))

        val outcome = engine().collect(listOf(tree))

        assertTrue(names(outcome).contains("visible.wastickers"))
        assertFalse(names(outcome).contains("hidden.wastickers"))
        assertTrue("不可访问目录应被计数", outcome.inaccessibleFolders >= 1)
    }

    @Test
    fun `08 handles SAF permission loss and revoked grant`() {
        val lostGrant = FakeTree("revoked", grantRoot = false)
        val healthy = FakeTree("healthy")
        healthy.add("root", doc("d1", "still.wastickers"))

        val outcome = engine().collect(listOf(lostGrant, healthy))

        assertTrue(names(outcome).contains("still.wastickers"))
        assertTrue("丢失的授权应被报告", outcome.inaccessibleFolders >= 1)
    }

    @Test
    fun `09 deduplicates repeated discovery results across sources`() {
        // MediaStore reports seconds*1000, SAF reports milliseconds. The engine
        // normalises the timestamp so the same physical file shown by two
        // sources collapses into one card.
        val flat = listOf(
            WastickersFile(
                id = "media:1",
                uri = "content://media/1",
                displayName = "same.wastickers",
                sizeBytes = 100L,
                lastModified = 1_700_000_000_000L,
                source = WastickersSource.MEDIA_STORE
            ),
            WastickersFile(
                id = "media:1",
                uri = "content://media/1",
                displayName = "same.wastickers",
                sizeBytes = 100L,
                lastModified = 1_700_000_000_000L,
                source = WastickersSource.MEDIA_STORE
            )
        )
        val treeA = FakeTree("primary")
        treeA.add(
            "root",
            doc("d1", "same.wastickers", size = 100L, modified = 1_700_000_000_500L)
        )
        val treeB = FakeTree("primary")
        treeB.add(
            "root",
            doc("d1", "same.wastickers", size = 100L, modified = 1_700_000_000_700L)
        )

        val outcome = engine().collect(listOf(treeA, treeB), flat)

        assertEquals("同一文件跨源重复应合并为一条", 1, outcome.files.size)
    }

    /* ================================================================== */
    /* 10 - 12 : import integrity                                          */
    /* ================================================================== */

    @Test
    fun `10 imports a valid wastickres archive`() {
        val archive = zipFile(
            "title.txt" to "Valid Pack\n".toByteArray(),
            "author.txt" to "Tester\n".toByteArray(),
            "1.webp" to webpBytes(),
            "2.webp" to webpBytes()
        )

        val report = WastickersArchiveValidator.validate(archive)

        assertTrue("有效归档应通过导入前校验", report.ok)
        assertEquals(2, report.stickerCount)
        assertTrue(WastickersArchiveValidator.isSafeEntryName("1.webp"))
    }

    @Test
    fun `11 rejects a corrupt file`() {
        val corrupt = rawFile(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val empty = rawFile(ByteArray(0))

        val corruptReport = WastickersArchiveValidator.validate(corrupt)
        val emptyReport = WastickersArchiveValidator.validate(empty)

        assertFalse(corruptReport.ok)
        assertFalse(emptyReport.ok)
        assertFalse(WastickersArchiveValidator.isZipMagic(corrupt))
    }

    @Test
    fun `12 rejects a malicious zip slip archive`() {
        val malicious = zipFile(
            "../evil.webp" to webpBytes(),
            "1.webp" to webpBytes()
        )

        val report = WastickersArchiveValidator.validate(malicious)

        assertFalse("含 ../ 的归档必须被拒绝", report.ok)
        assertFalse(WastickersArchiveValidator.isSafeEntryName("../evil.webp"))
        assertFalse(WastickersArchiveValidator.isSafeEntryName("/abs.webp"))
        assertFalse(WastickersArchiveValidator.isSafeEntryName("a\\b.webp"))
        assertTrue(
            "Zip Slip 拒绝原因应可辨识",
            report.reason?.contains("Zip Slip") == true
        )
    }

    /* ================================================================== */
    /* 13 : URI permission persistence across app restart                  */
    /* ================================================================== */

    @Test
    fun `13 URI permission persistence survives app restart`() {
        val uris = listOf(
            "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fgsgwh.wastickers",
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fحزمة%20الملصقات.wastickers",
            "content://com.android.externalstorage.documents/document/primary%3AMy%20Stickers%2FPack.WASTICKERS"
        )

        // "Write to disk" through the same codec used by DocumentPermissionStore.
        val persisted = WastickersFileOps.encodeUriList(uris)
        // "App restart": a brand-new store instance reads it back.
        val restored = WastickersFileOps.decodeUriList(persisted)

        assertEquals(uris, restored)
        assertEquals(3, restored.size)

        // Duplicate grants must not accumulate over repeated restarts.
        val withDuplicate = WastickersFileOps.decodeUriList(
            WastickersFileOps.encodeUriList(uris + uris.first())
        )
        assertEquals(3, withDuplicate.size)

        // Discovery history must also survive a restart without re-flagging
        // an old file as NEW.
        val now = 1_700_000_000_000L
        val file = WastickersFile(
            id = "doc:${uris.first()}",
            uri = uris.first(),
            displayName = "gsgwh.wastickers",
            sizeBytes = 2048L,
            lastModified = now,
            source = WastickersSource.USER_DOCUMENT
        )
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), listOf(file), now)
        val reloaded: Map<String, DiscoveryRecord> = HashMap(first.records)
        val afterRestart = WastickersFileOps.mergeDiscovery(
            reloaded, listOf(file), now + 3_600_000L
        )
        assertTrue(afterRestart.newIds.isEmpty())
        assertEquals(now, afterRestart.files.single().firstSeenAt)
    }
}
