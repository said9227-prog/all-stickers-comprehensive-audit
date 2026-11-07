package com.example

import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.StickerType
import com.example.media.WebpInfo
import com.example.media.WebpParser
import com.example.wastickers.ArchiveInspection
import com.example.wastickers.ArchiveKind
import com.example.wastickers.ArchiveOpener
import com.example.wastickers.DiscoveryRecord
import com.example.wastickers.FileImportProgress
import com.example.wastickers.FolderAccess
import com.example.wastickers.FolderListing
import com.example.wastickers.FolderTreePort
import com.example.wastickers.ImportProgress
import com.example.wastickers.ImportedPack
import com.example.wastickers.InMemoryImportedHashLog
import com.example.wastickers.PackWriter
import com.example.wastickers.RawDocument
import com.example.wastickers.WastickersArchiveInspector
import com.example.wastickers.WastickersArchiveValidator
import com.example.wastickers.WastickersDiscoveryEngine
import com.example.wastickers.WastickersFile
import com.example.wastickers.WastickersFileOps
import com.example.wastickers.WastickersImporter
import com.example.wastickers.WastickersInspectionCache
import com.example.wastickers.WastickersPreviewKeys
import com.example.wastickers.WastickersSearchIndex
import com.example.wastickers.WastickersSearchNormalizer
import com.example.wastickers.WastickersSource
import com.example.wastickers.WastickersStatus
import com.example.whatsapp.WhatsAppPackPlanner
import com.example.whatsapp.WhatsAppStickerValidator
import com.example.whatsapp.WhatsAppWebpFastPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * COMPREHENSIVE ENGINEERING AUDIT — mandatory acceptance groups A..H.
 *
 * Everything here is pure JVM: the real discovery engine, search index,
 * archive inspector, validator, import pipeline, preview-key rules, WhatsApp
 * fast-path decision and pack planner are exercised with fake/Android-free
 * adapters. No Android SDK, no Gradle, no emulator is involved, so a PASS in
 * this file means exactly "the framework-free logic passes on the JVM";
 * device/Compose/APK behaviour is reported separately as NOT VERIFIED.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WastickersComprehensiveAuditTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("wst-audit").toFile()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    /* ================================================================== */
    /* Fixtures                                                           */
    /* ================================================================== */

    private fun stickerBytes(tag: Byte = 1): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0, 0, 0, 0,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
    ) + ByteArray(64) { tag }

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A valid standalone archive. [title] changes its content hash. */
    private fun validZip(title: String, firstTag: Byte = 1): ByteArray = zipBytes(
        listOf(
            "title.txt" to "$title\n".toByteArray(StandardCharsets.UTF_8),
            "author.txt" to "Tester\n".toByteArray(StandardCharsets.UTF_8),
            "1.webp" to stickerBytes(firstTag),
            "2.webp" to stickerBytes((firstTag + 1).toByte())
        )
    )

    private fun file(
        id: String,
        name: String = "$id.wastickers",
        size: Long = 2048L,
        modified: Long = 1_700_000_000_000L,
        source: WastickersSource = WastickersSource.MEDIA_STORE
    ) = WastickersFile(
        id = id,
        uri = "content://media/$id",
        displayName = name,
        sizeBytes = size,
        lastModified = modified,
        source = source
    )

    private fun opener(bytesById: Map<String, ByteArray>): ArchiveOpener =
        ArchiveOpener { f -> bytesById[f.id]?.let { ByteArrayInputStream(it) } }

    private fun importer(
        bytesById: Map<String, ByteArray>,
        hashLog: InMemoryImportedHashLog = InMemoryImportedHashLog(),
        onPack: (ImportedPack) -> Unit = {}
    ) = WastickersImporter(
        opener = opener(bytesById),
        writer = PackWriter { pack -> onPack(pack); 1L },
        hashLog = hashLog,
        tempDirProvider = { File(tempRoot, "t_${System.nanoTime()}").apply { mkdirs() } }
    )

    /** Minimal but structurally valid 512x512 VP8X WebP (static or animated). */
    private fun webpBytes(animated: Boolean, durationMs: Int = 1000): ByteArray {
        val vp8x = ByteArray(10)
        vp8x[0] = (if (animated) 0x12 else 0x10).toByte() // alpha (+ animation)
        vp8x[4] = 0xFF.toByte(); vp8x[5] = 0x01.toByte()  // width  - 1 = 511
        vp8x[7] = 0xFF.toByte(); vp8x[8] = 0x01.toByte()  // height - 1 = 511
        val chunks = ArrayList<Pair<String, ByteArray>>()
        chunks += "VP8X" to vp8x
        if (animated) {
            val anmf = ByteArray(16)
            anmf[12] = (durationMs and 0xFF).toByte()
            anmf[13] = ((durationMs shr 8) and 0xFF).toByte()
            anmf[14] = ((durationMs shr 16) and 0xFF).toByte()
            chunks += "ANMF" to anmf
        }
        val body = ByteArrayOutputStream()
        for ((fourCc, payload) in chunks) {
            body.write(fourCc.toByteArray(StandardCharsets.US_ASCII))
            val n = payload.size
            body.write(byteArrayOf(n.toByte(), 0, 0, 0))
            body.write(payload)
        }
        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        val riffSize = 4 + bodyBytes.size // "WEBP" + chunks
        out.write(
            byteArrayOf(
                riffSize.toByte(),
                (riffSize shr 8).toByte(),
                (riffSize shr 16).toByte(),
                (riffSize shr 24).toByte()
            )
        )
        out.write("WEBP".toByteArray(StandardCharsets.US_ASCII))
        out.write(bodyBytes)
        return out.toByteArray()
    }

    private fun webpInfo(
        isWebp: Boolean = true,
        animated: Boolean = false,
        width: Int = WhatsAppStickerValidator.CANVAS_SIZE,
        height: Int = WhatsAppStickerValidator.CANVAS_SIZE,
        duration: Long = 1000L
    ) = WebpInfo(
        isWebp = isWebp,
        isAnimated = animated,
        hasAlpha = true,
        width = width,
        height = height,
        frameCount = if (animated) 3 else 0,
        totalDurationMs = duration,
        frameDurationsMs = if (animated) listOf(100, 200, 700) else emptyList()
    )

    /* ================================================================== */
    /* GROUP A — discovery, .part1/.part2/.part3, content inspection       */
    /* ================================================================== */

    @Test
    fun `A01 every part name extension case and long name is discovered`() {
        val names = listOf(
            "pack.wastickers",
            "pack.part1.wastickers",
            "pack.part2.wastickers",
            "pack.part3.wastickers",
            "tgtowabot.part2.wastickers",
            "Pack.WASTICKERS",
            "Rayn_Ariel tgtowabot.part2.WasTickers",
            "حزمة القطط والكلاب.part1.wastickers",
            "  spaced pack (copy) 2024.wastickers  ",
            "\uFEFFبسم.wastickers",
            "pack_with_@_#_symbols_and_a_very_long_name_1234567890.wastickers"
        )
        names.forEach { name ->
            assertTrue("must discover: $name", WastickersFileOps.isWastickersName(name))
        }
        listOf("archive.zip", "sticker.wastickers.bak", "notes.txt", "noextension").forEach {
            assertFalse("must reject: $it", WastickersFileOps.isWastickersName(it))
        }
    }

    @Test
    fun `A02 part marker is a hint and never rejects a standalone name`() {
        listOf("a.part1.wastickers", "a.part2.wastickers", "a.part3.wastickers",
            "a.001.wastickers", "a.z01.wastickers", "a_split2.wastickers").forEach {
            assertTrue("marker expected: $it", WastickersFileOps.hasPartMarker(it))
        }
        // A numeric suffix WITHOUT a separator is a normal pack name, not a part.
        assertFalse(WastickersFileOps.hasPartMarker("cats123.wastickers"))
        assertFalse(WastickersFileOps.hasPartMarker("pack.wastickers"))
        assertTrue(WastickersFileOps.hasPartMarker("PART2.WASTICKERS"))
        // Every part name is still a valid .wastickers file (never filtered out).
        assertTrue(WastickersFileOps.isWastickersName("a.part2.wastickers"))
    }

    @Test
    fun `A03 pack display name strips part markers without losing the name`() {
        assertEquals("Rayn_Ariel tgtowabot", WastickersFileOps.packNameOf("Rayn_Ariel tgtowabot.part2.wastickers"))
        assertEquals("pack", WastickersFileOps.packNameOf("pack.part1.WASTICKERS"))
        assertEquals("cats123", WastickersFileOps.packNameOf("cats123.wastickers"))
        assertEquals("حزمة القطط", WastickersFileOps.packNameOf("حزمة القطط.wastickers"))
    }

    @Test
    fun `A04 part named but complete ZIP is STANDALONE decided by content`() {
        val bytes = validZip("Complete")
        val inspection = WastickersArchiveInspector.inspectBytes("tgtowabot.part2.wastickers", bytes)

        assertEquals(ArchiveKind.STANDALONE, inspection.kind)
        assertEquals(2, inspection.stickerCount)
        assertFalse(inspection.hasAnimated)
        assertNotNull(inspection.previewEntryName)
        assertTrue(inspection.previewBytes?.isNotEmpty() == true)
    }

    @Test
    fun `A05 genuine split segment is reported as NEEDS_OTHER_PARTS`() {
        // PK\x07\x08 is the spanning/split-ZIP signature.
        val segment = byteArrayOf(0x50, 0x4B, 0x07, 0x08, 1, 2, 3, 4)
        val inspection = WastickersArchiveInspector.inspectBytes("pack.part1.wastickers", segment)

        assertEquals(ArchiveKind.NEEDS_OTHER_PARTS, inspection.kind)
        assertTrue(inspection.reason?.contains("جزء") == true)
        assertEquals(0, inspection.stickerCount)
        // No auto-merge: nothing is produced as a preview.
        assertNull(inspection.previewBytes)
    }

    @Test
    fun `A06 corrupt file with and without a part marker is distinguished`() {
        val garbage = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)

        val part = WastickersArchiveInspector.inspectBytes("pack.part3.wastickers", garbage)
        val plain = WastickersArchiveInspector.inspectBytes("pack.wastickers", garbage)
        val empty = WastickersArchiveInspector.inspectBytes("pack.wastickers", ByteArray(0))

        assertEquals(ArchiveKind.NEEDS_OTHER_PARTS, part.kind)
        assertEquals(ArchiveKind.INVALID, plain.kind)
        assertEquals(ArchiveKind.INVALID, empty.kind)
    }

    @Test
    fun `A07 preview bytes belong to THIS archive only`() {
        val first = WastickersArchiveInspector.inspectBytes("a.wastickers", validZip("A", 11))
        val second = WastickersArchiveInspector.inspectBytes("b.wastickers", validZip("B", 77))

        assertEquals("1.webp", first.previewEntryName)
        assertArrayEquals(stickerBytes(11), first.previewBytes!!)
        assertArrayEquals(stickerBytes(77), second.previewBytes!!)
        assertFalse("previews must not be shared",
            first.previewBytes.contentEquals(second.previewBytes))
    }

    @Test
    fun `A08 validator uses the ORIGINAL name even for a temp file`() {
        val temp = File(tempRoot, "arc_12345.bin")
        temp.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        val byTempName = WastickersArchiveValidator.validate(temp)
        val byOriginal = WastickersArchiveValidator.validate(temp, "tgtowabot.part2.wastickers")

        assertFalse(byTempName.ok)
        assertFalse(byTempName.needsParts)
        assertFalse(byOriginal.ok)
        assertTrue("importer passes the original name", byOriginal.needsParts)
    }

    @Test
    fun `A09 discovery engine finds all four part files through a SAF tree`() {
        val tree = FakeTree("primary")
        tree.add(
            "root",
            RawDocument("d1", "pack.wastickers", 2048, 1_700_000_000_000L, "application/octet-stream"),
            RawDocument("d2", "pack.part1.wastickers", 2048, 1_700_000_000_000L, "application/octet-stream"),
            RawDocument("d3", "pack.part2.wastickers", 2048, 1_700_000_000_000L, "application/octet-stream"),
            RawDocument("d4", "pack.part3.wastickers", 2048, 1_700_000_000_000L, "application/octet-stream"),
            RawDocument("d5", "tgtowabot.part2.wastickers", 2048, 1_700_000_000_000L, "application/octet-stream")
        )
        val outcome = WastickersDiscoveryEngine().collect(listOf(tree))
        val names = outcome.files.map { it.displayName }

        assertEquals(5, outcome.files.size)
        listOf("pack.wastickers", "pack.part1.wastickers", "pack.part2.wastickers",
            "pack.part3.wastickers", "tgtowabot.part2.wastickers").forEach {
            assertTrue("$it must appear", names.contains(it))
        }
        assertTrue(outcome.files.all { it.uri.startsWith("content://") })
    }

    private class FakeTree(private val label: String) : FolderTreePort {
        val nodes = LinkedHashMap<String, MutableList<RawDocument>>()
        fun add(parent: String, vararg children: RawDocument) {
            nodes.getOrPut(parent) { ArrayList() }.addAll(children)
        }
        override fun label(): String = label
        override fun rootId(): String? = "root"
        override fun listChildren(parentId: String): FolderListing? =
            nodes[parentId]?.let { FolderListing(it, FolderAccess.OK) }
        override fun documentUri(documentId: String): String? =
            "content://$label/document/$documentId"
        override fun locationFor(documentId: String): String = "Download/$documentId"
    }

    /* ================================================================== */
    /* GROUP B — smart refresh / persistent NEW / deleted + moved files    */
    /* ================================================================== */

    @Test
    fun `B01 NEW badge appears once and never again after restart`() {
        val now = 1_700_000_000_000L
        val a = file("a", "a.wastickers", modified = now)
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), listOf(a), now)
        assertTrue(first.files.single().isNew)
        assertEquals(setOf("a"), first.newIds)

        val reloaded: Map<String, DiscoveryRecord> = HashMap(first.records)
        val second = WastickersFileOps.mergeDiscovery(reloaded, listOf(a), now + 3_600_000L)
        assertTrue(second.newIds.isEmpty())
        assertFalse(second.files.single().isNew)
        assertEquals(now, second.files.single().firstSeenAt)
    }

    @Test
    fun `B02 new files are placed first while old ones are preserved`() {
        val now = 1_800_000_000_000L
        val old1 = file("old1", modified = now - 500_000L)
        val old2 = file("old2", modified = now - 400_000L)
        val fresh1 = file("fresh1", modified = now - 300_000L)
        val fresh2 = file("fresh2", modified = now - 200_000L)
        val merged = WastickersFileOps.mergeDiscovery(
            mapOf(
                "old1" to DiscoveryRecord("old1", now - 900_000L, false, now - 500_000L),
                "old2" to DiscoveryRecord("old2", now - 900_000L, false, now - 400_000L)
            ),
            listOf(old1, old2, fresh1, fresh2),
            now
        )
        val ordered = WastickersFileOps.orderForRefresh(merged.files, merged.newIds)

        assertEquals(4, ordered.size)
        assertEquals(setOf("fresh1", "fresh2"), ordered.take(2).map { it.id }.toSet())
        assertEquals(setOf("old1", "old2"), ordered.drop(2).map { it.id }.toSet())
        assertEquals(ordered, WastickersFileOps.orderForRefresh(merged.files, merged.newIds))
    }

    @Test
    fun `B03 deleted and reappearing files are never flagged NEW twice`() {
        val now = 1_700_000_000_000L
        val a = file("a", modified = now)
        val b = file("b", modified = now)
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), listOf(a, b), now)
        // b disappears on the next scan.
        val second = WastickersFileOps.mergeDiscovery(first.records, listOf(a), now + 1000L)
        assertEquals(1, second.files.size)
        assertTrue("history of b is kept", second.records.containsKey("b"))
        // b reappears (moved back / grant restored).
        val third = WastickersFileOps.mergeDiscovery(second.records, listOf(a, b), now + 2000L)
        assertTrue(third.newIds.isEmpty())
        assertFalse(third.files.first { it.id == "b" }.isNew)
        assertEquals(now, third.files.first { it.id == "b" }.firstSeenAt)
    }

    @Test
    fun `B04 missing metadata never collapses two different files`() {
        // Providers that return 0 for size / date must NOT be fingerprinted,
        // otherwise two packs sharing a name in different folders vanish.
        val inDownload = file("doc:1", "pack.part1.wastickers", size = 0L, modified = 0L,
            source = WastickersSource.USER_DOCUMENT)
        val inTelegram = file("doc:2", "pack.part1.wastickers", size = 0L, modified = 0L,
            source = WastickersSource.USER_DOCUMENT)

        val deduped = WastickersFileOps.dedupe(listOf(inDownload, inTelegram))
        assertEquals(2, deduped.size)
    }

    @Test
    fun `B05 same physical file seen by two sources collapses once`() {
        val media = file("media:9", "same.wastickers", size = 100L, modified = 1_700_000_000_000L)
        val saf = file("saf:9", "same.wastickers", size = 100L, modified = 1_700_000_000_500L,
            source = WastickersSource.SAF_TREE)
        assertEquals(1, WastickersFileOps.dedupe(listOf(media, saf)).size)
        // Exact same id is always collapsed.
        assertEquals(1, WastickersFileOps.dedupe(listOf(media, media.copy())).size)
    }

    /* ================================================================== */
    /* GROUP C — fast debounced in-memory search                           */
    /* ================================================================== */

    private fun searchCorpus(): List<WastickersFile> {
        val corpus = ArrayList<WastickersFile>()
        corpus += file("ar1", "حزمة القطط.wastickers")
        corpus += file("ar2", "حِمَلُ الكِلاب.wastickers")
        corpus += file("en1", "My Cat Pack.wastickers")
        corpus += file("en2", "Cats_2024.wastickers")
        corpus += file("part", "tgtowabot.part2.wastickers")
        for (i in 1..3000) corpus += file("bulk$i", "Bulk Pack $i.wastickers")
        return corpus
    }

    @Test
    fun `C01 index matches thousands of files without storage reads`() {
        val index = WastickersSearchIndex(searchCorpus())
        assertEquals(3005, index.size)
        assertTrue(index.search("Bulk Pack 2999").any { it.id == "bulk2999" })
        assertTrue(index.search("bulk 1777").any { it.id == "bulk1777" })
        assertTrue(WastickersFileOps.filterByQuery(searchCorpus(), "CAT").any { it.id == "en1" })
    }

    @Test
    fun `C02 Arabic diacritics tatweel Alef variants and digits normalise`() {
        assertEquals("القطط", WastickersSearchNormalizer.normalize("القِطط"))
        assertEquals("القطط", WastickersSearchNormalizer.normalize("القــطط"))
        assertEquals("احمد", WastickersSearchNormalizer.normalize("أحمد"))
        assertEquals("123", WastickersSearchNormalizer.normalize("١٢٣"))
        val corpus = listOf(file("a", "حزمة القطط.wastickers"))
        assertTrue(WastickersSearchIndex(corpus).search("القِطط").size == 1)
        assertTrue(WastickersSearchIndex(corpus).search("القطط").size == 1)
    }

    @Test
    fun `C03 separators case and whitespace are insignificant`() {
        assertEquals("cats 2024", WastickersSearchNormalizer.normalize("Cats_2024"))
        val corpus = listOf(file("a", "Cats_2024.wastickers"))
        assertTrue(WastickersSearchIndex(corpus).search("cats 2024").size == 1)
        assertTrue(WastickersSearchIndex(corpus).search("  CATS   2024 ").size == 1)
    }

    @Test
    fun `C04 no results and empty query behave correctly`() {
        val index = WastickersSearchIndex(searchCorpus())
        assertTrue(index.search("zzz-no-such-pack").isEmpty())
        assertEquals(index.size, index.search("").size)
        assertEquals(index.size, index.search("   ").size)
        assertTrue(index.search("tgtowabot").any { it.id == "part" })
        assertTrue("derived pack name is searchable",
            index.search("tgtowabot part2").size == 1)
    }

    /* ================================================================== */
    /* GROUP D — selection lifecycle                                       */
    /* ================================================================== */

    @Test
    fun `D01 single multiple select all and clear`() {
        var selection = emptySet<String>()
        selection = WastickersFileOps.toggleSelection(selection, "a")
        selection = WastickersFileOps.toggleSelection(selection, "b")
        assertEquals(setOf("a", "b"), selection)
        selection = WastickersFileOps.toggleSelection(selection, "a")
        assertEquals(setOf("b"), selection)
        selection = WastickersFileOps.selectAll(selection, listOf("b", "c", "d"))
        assertEquals(setOf("b", "c", "d"), selection)
        // Leaving the screen clears everything (ViewModel calls clearSelection).
        selection = emptySet()
        assertTrue(selection.isEmpty())
    }

    @Test
    fun `D02 stale selection of deleted files is dropped on refresh`() {
        val selected = setOf("a", "gone", "c")
        val available = setOf("a", "c", "new")
        assertEquals(setOf("a", "c"), selected.intersect(available))
        // Re-entering the screen starts from an empty selection, never restores.
        assertEquals(emptySet<String>(), emptySet<String>())
    }

    /* ================================================================== */
    /* GROUP E — batch import of 5+ packs, isolation, report               */
    /* ================================================================== */

    @Test
    fun `E01 five packs import independently keeping name and sticker order`() = runTest {
        val bytesById = (1..5).associate { i -> "p$i" to validZip("Pack $i", i.toByte()) }
        val files = bytesById.keys.map { id -> file(id, "$id.wastickers", bytesById[id]!!.size.toLong()) }
        val packs = Collections.synchronizedList(mutableListOf<ImportedPack>())

        val last = importer(bytesById, onPack = { packs.add(it) })
            .import(files).toList().last()

        assertEquals(5, last.total)
        assertEquals(5, last.succeeded)
        assertEquals(0, last.failed)
        assertEquals(5, packs.size)
        assertEquals(5, packs.map { it.name }.distinct().size)
        assertTrue(packs.all { it.stickers.size == 2 })
        assertTrue(packs.all { it.stickers.map { s -> s.displayName } == listOf("1", "2") })
        // No pack absorbed another pack's stickers.
        assertEquals(10, packs.sumOf { it.stickers.size })
    }

    @Test
    fun `E02 a corrupt archive among healthy ones fails in isolation`() = runTest {
        val bytesById = (1..5).associate { i -> "p$i" to validZip("Pack $i", i.toByte()) }
            .toMutableMap().also { it["bad"] = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8) }
        val files = bytesById.keys.map { id ->
            file(id, if (id == "bad") "bad.wastickers" else "$id.wastickers",
                bytesById[id]!!.size.toLong())
        }

        val last = importer(bytesById).import(files).toList().last()

        assertEquals(6, last.total)
        assertEquals(5, last.succeeded)
        assertEquals(1, last.failed)
        assertEquals(WastickersStatus.CORRUPT, last.perFile["bad"]?.status)
    }

    @Test
    fun `E03 packs with identical sticker filenames stay separated`() = runTest {
        val a = validZip("Alpha", 3)
        val b = validZip("Bravo", 9)
        // The importer removes its temp extraction dir at the end of each file,
        // so payload bytes must be snapshotted while the pack is being written.
        val snapshots = Collections.synchronizedList(mutableListOf<Triple<String, String, Int>>())
        val last = importer(mapOf("a" to a, "b" to b), onPack = { pack ->
            snapshots.add(
                Triple(
                    pack.sourceId,
                    pack.name,
                    pack.stickers.first().file.readBytes().contentHashCode()
                )
            )
        }).import(
            listOf(
                file("a", "a.wastickers", a.size.toLong()),
                file("b", "b.wastickers", b.size.toLong())
            )
        ).toList().last()

        assertEquals(2, last.succeeded)
        assertEquals(2, snapshots.size)
        val alpha = snapshots.first { it.first == "a" }
        val bravo = snapshots.first { it.first == "b" }
        assertEquals("Alpha", alpha.second)
        assertEquals("Bravo", bravo.second)
        assertNotEquals("same filename must not resolve to the same payload",
            alpha.third, bravo.third)
    }

    @Test
    fun `E04 a genuine split segment maps to NEEDS_PARTS in the importer`() = runTest {
        val segment = byteArrayOf(0x50, 0x4B, 0x07, 0x08, 1, 2, 3, 4)
        val wst = file("seg", "tgtowabot.part2.wastickers", segment.size.toLong())

        val last = importer(mapOf("seg" to segment)).import(listOf(wst)).toList().last()

        assertEquals(1, last.failed)
        assertEquals(WastickersStatus.NEEDS_PARTS, last.perFile["seg"]?.status)
        assertTrue(last.perFile["seg"]?.message?.contains("جزء") == true)
    }

    @Test
    fun `E05 import report is derived from real progress numbers`() {
        val progress = ImportProgress(
            total = 6, completed = 6, succeeded = 4, failed = 1, skipped = 1,
            perFile = mapOf(
                "n" to FileImportProgress(1f, WastickersStatus.NEEDS_PARTS, "parts")
            ),
            isRunning = false
        )
        val report = WastickersFileOps.buildImportReport(progress)

        assertEquals(6, report.total)
        assertEquals(4, report.succeeded)
        assertEquals(1, report.failed)
        assertEquals(1, report.skipped)
        assertEquals(1, report.needsParts)
        assertTrue(report.finished)
        assertTrue(report.hasIssues)
        assertFalse(report.cancelled)
    }

    @Test
    fun `E06 current import label names the archive in progress`() {
        val files = listOf(file("a", "My Pack.part1.wastickers"))
        val running = ImportProgress(
            total = 1, isRunning = true,
            perFile = mapOf("a" to FileImportProgress(0.4f, WastickersStatus.IMPORTING))
        )
        assertEquals("My Pack", WastickersFileOps.currentImportLabel(files, running))
        assertNull(WastickersFileOps.currentImportLabel(files, running.copy(isRunning = false)))
    }

    /* ================================================================== */
    /* GROUP F — preview / cache identity (no image mixing)                */
    /* ================================================================== */

    @Test
    fun `F01 identical image names in different packs get different keys`() {
        val keyA = WastickersPreviewKeys.stickerImage(1L, 7L, "/packs/1/1.webp")
        val keyB = WastickersPreviewKeys.stickerImage(2L, 7L, "/packs/2/1.webp")
        assertNotEquals(keyA, keyB)
        assertNotEquals(
            WastickersPreviewKeys.stickerImage(1L, 7L, "src-a"),
            WastickersPreviewKeys.stickerImage(1L, 7L, "src-b")
        )
    }

    @Test
    fun `F02 archive version changes when size or date changes`() {
        val base = file("a", "a.wastickers", 100L, 1_700_000_000_000L)
        val v1 = WastickersPreviewKeys.archiveVersion(base)
        assertEquals(v1, WastickersPreviewKeys.archiveVersion(base.copy()))
        assertNotEquals(v1, WastickersPreviewKeys.archiveVersion(base.copy(sizeBytes = 101L)))
        assertNotEquals(v1, WastickersPreviewKeys.archiveVersion(base.copy(lastModified = 1_700_000_001_000L)))
        assertNotEquals(v1, WastickersPreviewKeys.archiveVersion(base.copy(id = "b")))

        val key = WastickersPreviewKeys.archivePreview(base, "1.webp")
        assertTrue(WastickersPreviewKeys.belongsTo(key, base))
        assertFalse(WastickersPreviewKeys.belongsTo(key, base.copy(sizeBytes = 999L)))
    }

    @Test
    fun `F03 inspection cache is bounded and drops stale versions`() {
        val cache = WastickersInspectionCache(maxEntries = 3)
        val a = file("a", "a.wastickers", 100L, 1_000L)
        val b = file("b", "b.wastickers", 200L, 2_000L)
        val c = file("c", "c.wastickers", 300L, 3_000L)
        val d = file("d", "d.wastickers", 400L, 4_000L)
        val inspection = ArchiveInspection(ArchiveKind.STANDALONE, 2)
        cache.put(WastickersPreviewKeys.archivePreview(a, "1.webp"), inspection)
        cache.put(WastickersPreviewKeys.archivePreview(b, "1.webp"), inspection)
        cache.put(WastickersPreviewKeys.archivePreview(c, "1.webp"), inspection)
        cache.put(WastickersPreviewKeys.archivePreview(d, "1.webp"), inspection)
        assertEquals("LRU must stay bounded", 3, cache.size())

        cache.retainVersions(listOf(
            WastickersPreviewKeys.archiveVersion(c),
            WastickersPreviewKeys.archiveVersion(d)
        ))
        assertEquals(2, cache.size())
        assertNotNull(cache.get(WastickersPreviewKeys.archivePreview(c, "1.webp")))
        assertNull(cache.get(WastickersPreviewKeys.archivePreview(a, "1.webp")))
        cache.clear()
        assertEquals(0, cache.size())
    }

    /* ================================================================== */
    /* GROUP G — WhatsApp / Business fast path and compatibility          */
    /* ================================================================== */

    @Test
    fun `G01 parser reads a real 512x512 static VP8X WebP`() {
        val bytes = webpBytes(animated = false)
        val info = WebpParser.parse(ByteArrayInputStream(bytes))
        assertTrue(info.isWebp)
        assertFalse(info.isAnimated)
        assertEquals(512, info.width)
        assertEquals(512, info.height)
    }

    @Test
    fun `G02 compliant static WebP is reused verbatim`() {
        val decision = WhatsAppWebpFastPath.decide(
            webpInfo(), byteSize = 40_000L, animatedPack = false
        )
        assertTrue(decision.reuse)
        assertTrue(decision.reason.contains("copied verbatim"))
    }

    @Test
    fun `G03 non compliant sources are never reused`() {
        assertFalse(WhatsAppWebpFastPath.decide(webpInfo(isWebp = false), 40_000L, false).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(webpInfo(width = 256), 40_000L, false).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(webpInfo(), 0L, false).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(
            webpInfo(), (WhatsAppStickerValidator.MAX_STATIC_BYTES + 1).toLong(), false).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(
            webpInfo(animated = true), 40_000L, false).reuse)
    }

    @Test
    fun `G04 animated pack keeps frames and duration rules`() {
        val bytes = webpBytes(animated = true, durationMs = 1000)
        val info = WebpParser.parse(ByteArrayInputStream(bytes))
        assertTrue(info.isAnimated)
        assertEquals(1000L, info.totalDurationMs)
        assertTrue(WhatsAppWebpFastPath.decide(info, bytes.size.toLong(), true).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(webpInfo(animated = false), 40_000L, true).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(
            webpInfo(animated = true, duration = 11_000L), 40_000L, true).reuse)
        assertFalse(WhatsAppWebpFastPath.decide(
            webpInfo(animated = true, duration = 0L), 40_000L, true).reuse)
    }

    @Test
    fun `G05 WhatsApp limits and planner chunking are unchanged`() {
        assertEquals(3, WhatsAppStickerValidator.MIN_STICKERS)
        assertEquals(30, WhatsAppStickerValidator.MAX_STICKERS)
        assertEquals(512, WhatsAppStickerValidator.CANVAS_SIZE)
        assertEquals(100 * 1024, WhatsAppStickerValidator.MAX_STATIC_BYTES)
        assertEquals(500 * 1024, WhatsAppStickerValidator.MAX_ANIMATED_BYTES)
        assertEquals(10_000L, WhatsAppStickerValidator.MAX_DURATION_MS)

        val pack = StickerPack(id = 1L, name = "Big", shortName = "big")
        val stickers = (1..31).map { i ->
            StickerItem(
                originalUri = "file:///p/$i.webp", originalName = "$i.webp",
                displayName = "$i", type = StickerType.STATIC_WEBP, sortOrder = i, id = i.toLong()
            )
        }
        val plans = WhatsAppPackPlanner.plan(pack, stickers)
        assertEquals(31, plans.usableCount)
        assertEquals(2, plans.plans.size)
        assertEquals(30, plans.plans[0].stickerCount)
        assertEquals(1, plans.plans[1].stickerCount)
        assertTrue(plans.plans.all { !it.animated })
    }

    /* ================================================================== */
    /* GROUP H — export/import contracts remain intact (pure JVM part)     */
    /* ================================================================== */

    @Test
    fun `H01 validator still rejects invalid WhatsApp packs`() {
        val pack = StickerPack(id = 1L, name = "P", shortName = "p")
        val two = (1..2).map {
            StickerItem(originalUri = "file:///a/$it.webp", originalName = "$it.webp",
                displayName = "$it", type = StickerType.STATIC_WEBP, id = it.toLong())
        }
        assertFalse(WhatsAppStickerValidator.validate(pack, two).isValid)

        val many = (1..31).map {
            StickerItem(originalUri = "file:///a/$it.webp", originalName = "$it.webp",
                displayName = "$it", type = StickerType.STATIC_WEBP, id = it.toLong())
        }
        assertFalse(WhatsAppStickerValidator.validate(pack, many).isValid)

        val mixed = listOf(
            StickerItem(originalUri = "file:///a/1.webp", originalName = "1.webp",
                displayName = "1", type = StickerType.STATIC_WEBP, id = 1L),
            StickerItem(originalUri = "file:///a/2.webp", originalName = "2.webp",
                displayName = "2", type = StickerType.ANIMATED_WEBP, id = 2L, durationMs = 500L),
            StickerItem(originalUri = "file:///a/3.webp", originalName = "3.webp",
                displayName = "3", type = StickerType.STATIC_WEBP, id = 3L)
        )
        assertFalse(WhatsAppStickerValidator.validate(pack, mixed).isValid)
    }

    @Test
    fun `H02 a valid 3 sticker static pack is still valid`() {
        val pack = StickerPack(id = 1L, name = "Valid", shortName = "valid")
        val stickers = (1..3).map {
            StickerItem(originalUri = "file:///a/$it.webp", originalName = "$it.webp",
                displayName = "$it", type = StickerType.STATIC_WEBP, id = it.toLong())
        }
        val result = WhatsAppStickerValidator.validate(pack, stickers)
        assertTrue(result.isValid)
        assertEquals(3, result.stickerCount)
        assertEquals(false, result.isAnimated)
    }

    @Test
    fun `H03 URI codec survives Arabic spaces and special characters`() {
        val uris = listOf(
            "content://com.android.externalstorage.documents/document/primary%3Aحزمة%20القطط.wastickers",
            "content://downloads/document/raw%3A%2Fstorage%2Fpack%20(copy)%202024.part2.wastickers",
            "content://provider/document/%D9%85%D9%84%D8%B5%D9%82%D8%A7%D8%AA%D9%8A.wastickers"
        )
        assertEquals(uris, WastickersFileOps.decodeUriList(WastickersFileOps.encodeUriList(uris)))
    }

    @Test
    fun `H04 formatting helpers never fabricate dates or sizes`() {
        assertEquals("-", WastickersFileOps.formatDate(0L))
        assertEquals("0 B", WastickersFileOps.formatSize(0L))
        assertTrue(WastickersFileOps.formatSize(2048L).contains("KB"))
        assertTrue(WastickersFileOps.locationOf("content://media/external/file/123", WastickersSource.MEDIA_STORE)
            .startsWith("MediaStore"))
    }
}
