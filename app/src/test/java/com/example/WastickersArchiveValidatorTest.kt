package com.example

import com.example.storage.SafeZipExtractor
import com.example.wastickers.WastickersArchiveValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Integrity, ZIP Bomb and Zip Slip protection tests. */
class WastickersArchiveValidatorTest {

    private fun webpBytes(): ByteArray {
        // Minimal RIFF/WEBP header; MediaDetector + WebpParser tolerate the
        // stub payload and classify it as a static WebP sticker.
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        ) + ByteArray(64) { 1 }
    }

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

    private fun writeZip(entries: List<Pair<String, ByteArray>>): File {
        val dir = Files.createTempDirectory("wst-validator").toFile()
        val file = File(dir, "test.wastickers")
        file.writeBytes(zipBytes(entries))
        return file
    }

    private fun validEntries() = listOf(
        "title.txt" to "My Pack\n".toByteArray(),
        "author.txt" to "Tester\n".toByteArray(),
        "1.webp" to webpBytes(),
        "2.webp" to webpBytes(),
        "3.webp" to webpBytes()
    )

    @Test
    fun `valid wastickres archive passes validation`() {
        val report = WastickersArchiveValidator.validate(writeZip(validEntries()))
        assertTrue(report.ok)
        assertEquals(3, report.stickerCount)
        assertEquals(5, report.entryCount)
    }

    @Test
    fun `corrupt bytes are rejected`() {
        val dir = Files.createTempDirectory("wst-corrupt").toFile()
        val file = File(dir, "broken.wastickers")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val report = WastickersArchiveValidator.validate(file)
        assertFalse(report.ok)
        assertTrue(WastickersArchiveValidator.isZipMagic(file).not())
    }

    @Test
    fun `zip slip entry name is rejected`() {
        val report = WastickersArchiveValidator.validate(
            writeZip(listOf("../evil.webp" to webpBytes()))
        )
        assertFalse(report.ok)
        assertTrue(report.reason!!.contains("Zip Slip"))
        assertFalse(WastickersArchiveValidator.isSafeEntryName("../../a/b.webp"))
        assertFalse(WastickersArchiveValidator.isSafeEntryName("/absolute.webp"))
        assertTrue(WastickersArchiveValidator.isSafeEntryName("stickers/1.webp"))
    }

    @Test
    fun `zip bomb compression ratio is rejected`() {
        // 8 MB of zeros deflate to a few KB -> ratio >> 500.
        val bomb = ByteArray(8 * 1024 * 1024)
        val report = WastickersArchiveValidator.validate(
            writeZip(listOf("bomb.webp" to bomb))
        )
        assertFalse(report.ok)
        assertTrue(report.reason!!.contains("ZIP Bomb"))
    }

    @Test
    fun `archive without any sticker is rejected`() {
        val report = WastickersArchiveValidator.validate(
            writeZip(listOf("title.txt" to "Empty\n".toByteArray()))
        )
        assertFalse(report.ok)
        assertEquals(0, report.stickerCount)
    }

    @Test
    fun `extractor parses metadata and extracts only allowed stickers`() {
        val archive = writeZip(
            validEntries() + ("readme.exe" to byteArrayOf(1, 2, 3))
        )
        val dest = Files.createTempDirectory("wst-extract").toFile()
        val result = SafeZipExtractor().extract(
            inputStream = archive.inputStream(),
            destinationDir = dest,
            allowedExtensions = WastickersArchiveValidator.STICKER_EXTENSIONS
        )
        assertEquals("My Pack", result.packTitle)
        assertEquals("Tester", result.packAuthor)
        assertEquals(3, result.extractedFiles.size)
        assertTrue(result.extractedFiles.all { it.file.exists() && it.file.length() > 0 })
        assertFalse(File(dest, "readme.exe").exists())
    }

    @Test
    fun `extractor blocks zip slip while streaming`() {
        val archive = writeZip(listOf("../../evil.webp" to webpBytes()))
        val dest = Files.createTempDirectory("wst-slip").toFile()
        assertThrows(SecurityException::class.java) {
            SafeZipExtractor().extract(archive.inputStream(), dest)
        }
    }

    @Test
    fun `extractor enforces the entry count limit`() {
        val entries = (1..SafeZipExtractor.MAX_ENTRIES + 1).map {
            "$it.webp" to byteArrayOf(1, 2, 3, 4)
        }
        val archive = writeZip(entries)
        val dest = Files.createTempDirectory("wst-limit").toFile()
        assertThrows(SecurityException::class.java) {
            SafeZipExtractor().extract(archive.inputStream(), dest)
        }
    }

    @Test
    fun `extractor reports progress and releases resources`() {
        val archive = writeZip(validEntries())
        val dest = Files.createTempDirectory("wst-progress").toFile()
        val progress = mutableListOf<Float>()
        val result = SafeZipExtractor().extract(
            inputStream = archive.inputStream(),
            destinationDir = dest,
            onProgress = { progress.add(it) },
            progressTargetBytes = archive.length()
        )
        assertNotNull(result)
        assertTrue(progress.isNotEmpty())
        assertEquals(1f, progress.last(), 0.0001f)
        assertTrue(progress.zipWithNext().all { it.first <= it.second })
    }
}
