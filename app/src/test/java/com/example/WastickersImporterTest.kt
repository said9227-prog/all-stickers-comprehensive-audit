package com.example

import com.example.wastickers.ArchiveOpener
import com.example.wastickers.ImportedPack
import com.example.wastickers.InMemoryImportedHashLog
import com.example.wastickers.PackWriter
import com.example.wastickers.WastickersFile
import com.example.wastickers.WastickersImporter
import com.example.wastickers.WastickersSource
import com.example.wastickers.WastickersStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * End-to-end tests for the bounded, Flow-based multi-file import pipeline.
 * Pure JVM (no Android classes): the real SafeZipExtractor, MediaDetector and
 * archive validator run, only storage access and the DB are faked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WastickersImporterTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("wst-import").toFile()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    /* ------------------------------------------------------------------ */
    /* Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    private fun webpBytes(): ByteArray = byteArrayOf(
        'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
        0, 0, 0, 0,
        'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
    ) + ByteArray(64) { 1 }

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** A valid archive; a distinct [title] produces a distinct content hash. */
    private fun validZip(title: String): ByteArray = zipBytes(
        listOf(
            "title.txt" to "$title\n".toByteArray(),
            "author.txt" to "Tester\n".toByteArray(),
            "1.webp" to webpBytes(),
            "2.webp" to webpBytes()
        )
    )

    private fun wstFile(id: String, sizeBytes: Long) = WastickersFile(
        id = id,
        uri = "content://media/$id",
        displayName = "$id.wastickers",
        sizeBytes = sizeBytes,
        lastModified = 1_700_000_000_000L,
        source = WastickersSource.MEDIA_STORE
    )

    private fun opener(
        bytesById: Map<String, ByteArray>,
        deniedIds: Set<String> = emptySet()
    ): ArchiveOpener = ArchiveOpener { file ->
        if (file.id in deniedIds) null else bytesById[file.id]?.let { ByteArrayInputStream(it) }
    }

    private fun buildImporter(
        opener: ArchiveOpener,
        hashLog: InMemoryImportedHashLog = InMemoryImportedHashLog(),
        onPack: ((ImportedPack) -> Unit)? = null
    ): WastickersImporter = WastickersImporter(
        opener = opener,
        writer = PackWriter { pack ->
            onPack?.invoke(pack)
            1L
        },
        hashLog = hashLog,
        tempDirProvider = { File(tempRoot, "t_${System.nanoTime()}").apply { mkdirs() } }
    )

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val digits = "0123456789abcdef"
        return buildString {
            for (b in digest) {
                val value = b.toInt() and 0xFF
                append(digits[value ushr 4])
                append(digits[value and 0x0F])
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Tests                                                              */
    /* ------------------------------------------------------------------ */

    @Test
    fun `imports multiple archives in one operation with per file and overall progress`() = runTest {
        val a = validZip("Alpha")
        val b = validZip("Bravo")
        val c = validZip("Charlie")
        val bytesById = mapOf("a" to a, "b" to b, "c" to c)
        val files = listOf(
            wstFile("a", a.size.toLong()),
            wstFile("b", b.size.toLong()),
            wstFile("c", c.size.toLong())
        )
        val packs = Collections.synchronizedList(mutableListOf<ImportedPack>())
        val importer = buildImporter(opener(bytesById), onPack = { packs.add(it) })

        val last = importer.import(files).toList().last()

        assertEquals(3, last.total)
        assertEquals(3, last.succeeded)
        assertEquals(3, last.completed)
        assertEquals(0, last.failed)
        assertEquals(0, last.skipped)
        assertTrue(!last.isRunning)
        assertEquals(1.0, last.overall.toDouble(), 0.001)
        assertEquals(3, last.perFile.size)
        assertTrue(last.perFile.values.all { it.status == WastickersStatus.IMPORTED })
        assertEquals(3, packs.size)
        assertEquals("Alpha", packs.firstOrNull { it.sourceId == "a" }?.name)
        assertEquals(2, packs.first().stickers.size)
    }

    @Test
    fun `a corrupt archive fails in isolation while valid ones still import`() = runTest {
        val good = validZip("Good")
        val corrupt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val bytesById = mapOf("good" to good, "bad" to corrupt)
        val files = listOf(
            wstFile("bad", corrupt.size.toLong()),
            wstFile("good", good.size.toLong())
        )
        val importer = buildImporter(opener(bytesById))

        val last = importer.import(files).toList().last()

        assertEquals(1, last.succeeded)
        assertEquals(1, last.failed)
        assertEquals(2, last.completed)
        assertEquals(WastickersStatus.CORRUPT, last.perFile["bad"]?.status)
        assertEquals(WastickersStatus.IMPORTED, last.perFile["good"]?.status)
    }

    @Test
    fun `duplicate archives in the same batch are imported only once`() = runTest {
        val duplicate = validZip("Same")
        val files = listOf(
            wstFile("d1", duplicate.size.toLong()),
            wstFile("d2", duplicate.size.toLong())
        )
        val packs = Collections.synchronizedList(mutableListOf<ImportedPack>())
        val importer = buildImporter(
            opener(mapOf("d1" to duplicate, "d2" to duplicate)),
            onPack = { packs.add(it) }
        )

        val last = importer.import(files).toList().last()

        assertEquals(1, last.succeeded)
        assertEquals(1, last.skipped)
        assertEquals(1, packs.size)
        val statuses = last.perFile.values.map { it.status }
        assertEquals(1, statuses.count { it == WastickersStatus.IMPORTED })
        assertEquals(1, statuses.count { it == WastickersStatus.DUPLICATE })
    }

    @Test
    fun `an already imported archive is skipped via the persistent hash log`() = runTest {
        val archive = validZip("Seen")
        val hashLog = InMemoryImportedHashLog().apply { add(sha256(archive)) }
        val files = listOf(wstFile("seen", archive.size.toLong()))
        val packs = Collections.synchronizedList(mutableListOf<ImportedPack>())
        val importer = buildImporter(
            opener(mapOf("seen" to archive)),
            hashLog = hashLog,
            onPack = { packs.add(it) }
        )

        val last = importer.import(files).toList().last()

        assertEquals(1, last.skipped)
        assertEquals(0, last.succeeded)
        assertEquals(WastickersStatus.DUPLICATE, last.perFile["seen"]?.status)
        assertTrue(packs.isEmpty())
    }

    @Test
    fun `permission denial fails one file without crashing the batch`() = runTest {
        val good = validZip("OK")
        val bytesById = mapOf("good" to good)
        val files = listOf(
            wstFile("denied", 0L),
            wstFile("good", good.size.toLong())
        )
        val importer = buildImporter(opener(bytesById, deniedIds = setOf("denied")))

        val last = importer.import(files).toList().last()

        assertEquals(1, last.failed)
        assertEquals(1, last.succeeded)
        assertEquals(2, last.completed)
        assertEquals(WastickersStatus.FAILED, last.perFile["denied"]?.status)
        assertEquals(WastickersStatus.IMPORTED, last.perFile["good"]?.status)
    }

    @Test
    fun `empty selection emits a terminal progress and does not crash`() = runTest {
        val importer = buildImporter(opener(emptyMap()))

        val last = importer.import(emptyList()).last()

        assertEquals(0, last.total)
        assertEquals(0, last.completed)
        assertTrue(!last.isRunning)
        assertEquals(0.0, last.overall.toDouble(), 0.001)
    }

    @Test
    fun `overall progress is the mean of per file progress and stays bounded`() = runTest {
        val a = validZip("One")
        val b = validZip("Two")
        val files = listOf(wstFile("a", a.size.toLong()), wstFile("b", b.size.toLong()))
        val importer = buildImporter(opener(mapOf("a" to a, "b" to b)))

        val emissions = importer.import(files).toList()

        assertTrue(emissions.size >= 2)
        assertTrue(emissions.all { it.overall in 0f..1f })
        assertTrue(emissions.last().isRunning.not())
        val runningOveralls = emissions.filter { it.isRunning }.map { it.overall }
        assertTrue(runningOveralls.zipWithNext().all { it.first <= it.second + 0.0001f })
    }

    @Test
    fun `cancelling the collector stops the flow cleanly without crashing`() = runTest {
        val bytesById = (1..6).associate { "c$it" to validZip("Pack $it") }
        val files = bytesById.map { (id, bytes) -> wstFile(id, bytes.size.toLong()) }
        val importer = buildImporter(opener(bytesById))

        val job = launch {
            importer.import(files).collect { }
        }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCompleted)
    }
}
