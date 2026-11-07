package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.whatsapp.WhatsAppExporter
import com.example.whatsapp.WhatsAppPackStore
import com.example.whatsapp.WhatsAppStickerPackProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsAppStickerPackProviderTest {

    private lateinit var context: Context
    private lateinit var authority: String
    private lateinit var packId: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authority = WhatsAppExporter.authority(context)
        packId = "pack_1_test"
        val dir = WhatsAppPackStore.packDir(context, packId).apply { mkdirs() }
        File(dir, "tray.png").writeBytes(byteArrayOf(1, 2, 3))
        (1..3).forEach { index ->
            File(dir, "sticker_$index.webp").writeBytes(byteArrayOf(1, 2, 3))
        }
        File(dir, WhatsAppPackStore.PACK_FILE).writeText(
            """
            {
              "identifier": "$packId",
              "name": "Test pack",
              "publisher": "Sticker Studio",
              "tray_image_file": "tray.png",
              "animated_sticker_pack": true,
              "stickers": [
                {"image_file": "sticker_1.webp", "emojis": ["😀", "✨"]},
                {"image_file": "sticker_2.webp", "emojis": ["😂"]},
                {"image_file": "sticker_3.webp", "emojis": ["❤️"]}
              ]
            }
            """.trimIndent()
        )
    }

    @Test
    fun metadataExposesRequiredWhatsAppColumns() {
        val provider = Robolectric.buildContentProvider(WhatsAppStickerPackProvider::class.java)
            .create()
            .get()
        val cursor = provider.query(
            Uri.parse("content://$authority/metadata"),
            null,
            null,
            null,
            null
        )
        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(packId, it.getString(it.getColumnIndexOrThrow("sticker_pack_identifier")))
            assertEquals("Test pack", it.getString(it.getColumnIndexOrThrow("sticker_pack_name")))
            assertEquals("tray.png", it.getString(it.getColumnIndexOrThrow("sticker_pack_icon")))
            assertEquals("1", it.getString(it.getColumnIndexOrThrow("image_data_version")))
            assertEquals(1, it.getInt(it.getColumnIndexOrThrow("animated_sticker_pack")))
        }
    }

    @Test
    fun stickersExposeCommaSeparatedEmojiAndAssetIsReadable() {
        val provider = Robolectric.buildContentProvider(WhatsAppStickerPackProvider::class.java)
            .create()
            .get()
        val cursor = provider.query(
            Uri.parse("content://$authority/stickers/$packId"),
            null,
            null,
            null,
            null
        )
        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(
                "sticker_1.webp",
                it.getString(it.getColumnIndexOrThrow("sticker_file_name"))
            )
            assertEquals(
                "😀,✨",
                it.getString(it.getColumnIndexOrThrow("sticker_emoji"))
            )
        }

        val afd = provider.openAssetFile(
            Uri.parse("content://$authority/stickers_asset/$packId/sticker_1.webp"),
            "r"
        )
        assertNotNull(afd)
        afd!!.use {
            assertEquals(3L, it.length)
        }
    }
}
