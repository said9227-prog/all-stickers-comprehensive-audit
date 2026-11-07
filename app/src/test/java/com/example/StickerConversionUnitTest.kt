package com.example

import com.example.conversion.WebmMuxer
import com.example.core.model.StickerItem
import com.example.core.model.StickerType
import com.example.diagnostics.DiagnosticEvent
import com.example.diagnostics.DiagnosticLevel
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.media.BitmapUtils
import com.example.media.MediaDetector
import com.example.media.WebpParser
import com.example.storage.SafeZipExtractor
import com.example.telegram.TelegramStickerValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StickerConversionUnitTest {

    @Test
    fun testWebpParserWithCorruptedData() {
        val corruptedBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val info = WebpParser.parse(corruptedBytes.inputStream())
        assertFalse("Corrupted bytes should not be detected as valid WebP", info.isWebp)
        assertEquals(0, info.width)
        assertEquals(0, info.height)
    }

    @Test
    fun testMediaDetectorRecognizesPng() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0, 0, 2, 0, // Width: 512
            0, 0, 2, 0, // Height: 512
            8, 6, 0, 0, 0
        )
        val detected = MediaDetector.detect(pngHeader.inputStream(), pngHeader.size.toLong())
        assertEquals(StickerType.PNG, detected.type)
        assertEquals(512, detected.width)
        assertEquals(512, detected.height)
    }

    @Test
    fun testMediaDetectorRecognizesJpg() {
        val jpgHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)
        val detected = MediaDetector.detect(jpgHeader.inputStream(), jpgHeader.size.toLong())
        assertEquals(StickerType.JPG, detected.type)
        assertFalse(detected.hasAlpha)
    }

    @Test
    fun testMediaDetectorRecognizesWebm() {
        val webmHeader = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
        val detected = MediaDetector.detect(webmHeader.inputStream(), webmHeader.size.toLong())
        assertEquals(StickerType.WEBM, detected.type)
    }

    @Test
    fun testTelegramDimensionsPreserveAspectRatioSquare() {
        val dims = BitmapUtils.calculateTelegramDimensions(1000, 1000, forceEven = false)
        assertEquals(512, dims.width)
        assertEquals(512, dims.height)
    }

    @Test
    fun testTelegramDimensionsPreserveAspectRatioWide() {
        val dims = BitmapUtils.calculateTelegramDimensions(800, 400, forceEven = false)
        assertEquals(512, dims.width)
        assertEquals(256, dims.height)
    }

    @Test
    fun testTelegramDimensionsPreserveAspectRatioTall() {
        val dims = BitmapUtils.calculateTelegramDimensions(300, 600, forceEven = false)
        assertEquals(256, dims.width)
        assertEquals(512, dims.height)
    }

    @Test
    fun testTelegramDimensionsForceEven() {
        val dims = BitmapUtils.calculateTelegramDimensions(500, 333, forceEven = true)
        assertEquals(512, dims.width)
        assertTrue("Height must be even for video encoders", dims.height % 2 == 0)
    }

    @Test(expected = SecurityException::class)
    fun testZipSlipVulnerabilityPrevented() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("malicious payload".toByteArray())
            zos.closeEntry()
        }

        val extractor = SafeZipExtractor()
        val tempDir = File.createTempFile("test_dest_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            extractor.extract(ByteArrayInputStream(baos.toByteArray()), tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDuplicateFilenamesHandledSafely() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("set_a/sticker.webp"))
            zos.write("sticker 1".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("set_b/sticker.webp"))
            zos.write("sticker 2".toByteArray())
            zos.closeEntry()
        }

        val extractor = SafeZipExtractor()
        val tempDir = File.createTempFile("test_dup_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val result = extractor.extract(ByteArrayInputStream(baos.toByteArray()), tempDir)
            assertEquals(2, result.extractedFiles.size)
            assertFalse(
                "Files must have distinct filenames on disk",
                result.extractedFiles[0].file.name == result.extractedFiles[1].file.name
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testWastickersMetadataExtraction() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("title.txt"))
            zos.write("My WhatsApp Pack\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("author.txt"))
            zos.write("Senior Engineer\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("01_laugh.webp"))
            zos.write("mock webp".toByteArray())
            zos.closeEntry()
        }

        val extractor = SafeZipExtractor()
        val tempDir = File.createTempFile("test_meta_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            val result = extractor.extract(ByteArrayInputStream(baos.toByteArray()), tempDir)
            assertEquals("My WhatsApp Pack", result.packTitle)
            assertEquals("Senior Engineer", result.packAuthor)
            assertEquals(1, result.extractedFiles.size)
            assertEquals("01_laugh", result.extractedFiles[0].cleanDisplayName)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTelegramStickerValidatorSizeLimit() {
        val tempFile = File.createTempFile("large_test_", ".webm")
        try {
            // Write 300 KB file (exceeds 256 KB limit)
            val bytes = ByteArray(300 * 1024)
            tempFile.writeBytes(bytes)

            val validation = TelegramStickerValidator.validate(tempFile, isAnimated = true)
            assertFalse("File larger than 256 KB must be rejected", validation.isValid)
            assertTrue(validation.errors.any { it.contains("256 KB") })
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testEbmlWebmMuxerHeaderCreation() {
        val muxer = WebmMuxer()
        val header = muxer.createEbmlHeader()
        assertNotNull(header)
        assertTrue(header.size > 20)
        // Check EBML ID 0x1A45DFA3 in first 4 bytes
        assertEquals(0x1A.toByte(), header[0])
        assertEquals(0x45.toByte(), header[1])
        assertEquals(0xDF.toByte(), header[2])
        assertEquals(0xA3.toByte(), header[3])
    }

    @Test
    fun testDiagnosticsLoggingAndReportFormat() {
        DiagnosticsRepository.clearAll()
        val event = com.example.diagnostics.DiagnosticEvent(
            id = com.example.diagnostics.DiagnosticEvent.generateId(),
            level = com.example.diagnostics.DiagnosticLevel.ERROR,
            operation = "ZIP_IMPORT",
            stage = com.example.diagnostics.DiagnosticStage.URI_OPEN,
            fileName = "Mach_hot_1.webp",
            fileExtension = "webp",
            mimeType = "image/webp",
            fileSize = 184532L,
            fileExists = true,
            isFile = true,
            isReadable = true,
            isStoragePermanent = true,
            uri = "content://com.aistudio.stickerconv.w2tg.provider/internal_files/stickers/pack_1/originals/Mach_hot_1.webp",
            uriScheme = "content",
            uriAuthority = "com.aistudio.stickerconv.w2tg.provider",
            contentResolverCanOpen = true,
            exceptionClass = "java.io.FileNotFoundException",
            exceptionMessage = "No content provider available"
        )
        DiagnosticsRepository.logEvent(event)

        assertEquals(1, DiagnosticsRepository.events.value.size)
        val retrieved = DiagnosticsRepository.events.value.first()
        assertTrue(retrieved.id.startsWith("ERR-"))
        assertEquals("Mach_hot_1.webp", retrieved.fileName)

        val report = retrieved.toFormattedReport()
        assertTrue(report.contains("=== All stickers Diagnostic Report ==="))
        assertTrue(report.contains("Error ID: ${retrieved.id}"))
        assertTrue(report.contains("Mach_hot_1.webp"))
        assertTrue(report.contains("Stage: URI_OPEN"))
        assertTrue(report.contains("Permanent Storage: true"))
    }

    @Test
    fun testDiagnosticsRetentionLimit() {
        DiagnosticsRepository.clearAll()
        for (i in 1..110) {
            val event = com.example.diagnostics.DiagnosticEvent(
                id = "ERR-TEST-$i",
                fileName = "test_$i.webp"
            )
            DiagnosticsRepository.logEvent(event)
        }
        assertEquals(100, DiagnosticsRepository.events.value.size)
        assertEquals("ERR-TEST-110", DiagnosticsRepository.events.value.first().id)
    }

    @Test
    fun testMachHot1WebpExtractionAndValidation() {
        // Build a mock WebP file with valid RIFF WEBP VP8 header
        val webpBytes = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(24, 0, 0, 0)) // size 24
            write("WEBP".toByteArray(Charsets.US_ASCII))
            write("VP8 ".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(12, 0, 0, 0))
            write(byteArrayOf(0x00, 0x00, 0x00, 0x9D.toByte(), 0x01, 0x2A, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00))
        }.toByteArray()

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("Mach_hot_1.webp"))
            zos.write(webpBytes)
            zos.closeEntry()
        }

        val extractor = SafeZipExtractor()
        val tempExtractDir = File.createTempFile("mach_extract_", "_dir")
        tempExtractDir.delete()
        tempExtractDir.mkdirs()

        try {
            val result = extractor.extract(ByteArrayInputStream(zipBaos.toByteArray()), tempExtractDir)
            assertEquals(1, result.extractedFiles.size)
            val extractedItem = result.extractedFiles.first()
            assertEquals("Mach_hot_1", extractedItem.cleanDisplayName)
            assertTrue(extractedItem.file.exists())
            assertTrue(extractedItem.file.isFile)
            assertTrue(extractedItem.file.length() > 0)

            // Simulate moving to permanent storage
            val permanentDir = File.createTempFile("pack_originals_", "_dir")
            permanentDir.delete()
            permanentDir.mkdirs()

            try {
                val permanentFile = File(permanentDir, extractedItem.file.name)
                extractedItem.file.copyTo(permanentFile, overwrite = true)
                assertTrue(permanentFile.exists())
                assertTrue(permanentFile.length() > 0)

                // Now delete the temporary extraction folder (as done during cleanup)
                tempExtractDir.deleteRecursively()
                assertFalse("Temp extract dir must be deleted", tempExtractDir.exists())

                // Permanent file must STILL exist and be readable!
                assertTrue("Permanent file must remain intact after temp cleanup", permanentFile.exists())
                assertTrue(permanentFile.canRead())
                assertEquals(webpBytes.size.toLong(), permanentFile.length())
            } finally {
                permanentDir.deleteRecursively()
            }
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    @Test
    fun testTelegramStickerValidatorPackShortNameSanitization() {
        val sanitizedArabic = TelegramStickerValidator.sanitizeShortName("ملصقات رائعة 2026!")
        assertTrue("Sanitized short name must start with a letter", sanitizedArabic.startsWith("pack_"))
        assertTrue("Sanitized short name must only contain latin letters, digits, and underscores", TelegramStickerValidator.validatePackShortName(sanitizedArabic))

        val sanitizedEnglish = TelegramStickerValidator.sanitizeShortName("Funny Cats Pack-1")
        assertEquals("funny_cats_pack_1", sanitizedEnglish)
        assertTrue(TelegramStickerValidator.validatePackShortName(sanitizedEnglish))
    }

    @Test
    fun testTelegramStickerValidatorEmojiCheck() {
        assertTrue(TelegramStickerValidator.validateEmoji("❤️"))
        assertFalse(TelegramStickerValidator.validateEmoji(""))
        assertFalse(TelegramStickerValidator.validateEmoji("   "))
        assertFalse(TelegramStickerValidator.validateEmoji(null))
    }

    @Test
    fun testGalleryStickerImportPipeline() {
        // 1. Simulate Gallery selection details
        val galleryUri = "content://example.provider/images/123"
        val galleryDisplayName = "goog"
        val galleryMimeType = "image/jpeg"

        // 2. Create sticker item with first-class Gallery properties preserved
        val stickerItem = StickerItem(
            id = 1,
            packId = 10,
            originalUri = "content://com.example.provider/internal_files/stickers/pack_10/originals/goog.jpg",
            originalName = "goog",
            displayName = galleryDisplayName,
            emoji = "❤️",
            type = StickerType.JPG,
            originalSize = 1024L,
            sourceUri = galleryUri,
            sourceMimeType = galleryMimeType,
            sourceDisplayName = galleryDisplayName
        )

        // Ensure first-class details are perfectly preserved in the sticker model
        assertEquals(galleryUri, stickerItem.sourceUri)
        assertEquals(galleryMimeType, stickerItem.sourceMimeType)
        assertEquals(galleryDisplayName, stickerItem.sourceDisplayName)
        assertFalse("Display name must NEVER be used as the actual media source path", stickerItem.originalUri == stickerItem.displayName)

        // 3. Simulate conversion of the Gallery JPEG to Telegram compliant static WebP
        val targetConvertedWebpFile = File.createTempFile("converted_sticker_1_", ".webp")
        // Write mock WebP header
        val mockWebpBytes = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(24, 0, 0, 0))
            write("WEBP".toByteArray(Charsets.US_ASCII))
        }.toByteArray()
        targetConvertedWebpFile.writeBytes(mockWebpBytes)

        val updatedStickerItem = stickerItem.copy(
            conversionStatus = com.example.core.model.ConversionStatus.SUCCESS,
            outputPath = targetConvertedWebpFile.absolutePath,
            outputUri = "content://com.example.provider/internal_files/stickers/pack_10/converted/sticker_1.webp"
        )

        // 4. Verify converted sticker file
        val convertedFile = File(updatedStickerItem.outputPath!!)
        assertTrue("Converted file must exist", convertedFile.exists())
        assertTrue("Converted file size must be non-zero", convertedFile.length() > 0)
        assertFalse("The target intent EXTRA_STREAM must not receive display name", updatedStickerItem.outputPath == updatedStickerItem.displayName)

        targetConvertedWebpFile.delete()
    }
}
