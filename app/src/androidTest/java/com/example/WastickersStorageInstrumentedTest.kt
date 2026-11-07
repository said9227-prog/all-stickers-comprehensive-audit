package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastickers.DeviceWastickersScanner
import com.example.wastickers.DocumentPermissionStore
import com.example.wastickers.TreePermissionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL device / emulator storage integration tests.
 *
 * The unit tests use fake documents providers; these tests exercise the
 * actual ContentResolver, MediaStore and SharedPreferences on a device.
 * They need NO manual interaction: the app writes its own .wastickers file
 * into MediaStore and then asks the production scanner to find it.
 *
 * The full SAF flow (user picking a folder / files in the system picker) cannot
 * be automated without user interaction and is verified manually on device.
 * These tests are NOT run in this environment (no Android device/emulator).
 */
@RunWith(AndroidJUnit4::class)
class WastickersStorageInstrumentedTest {

    private lateinit var context: Context
    private val insertedUris = ArrayList<Uri>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        DocumentPermissionStore(context).clear()
        val trees = TreePermissionStore(context)
        trees.grantedTreeUris().forEach { trees.remove(it) }
    }

    @After
    fun tearDown() {
        for (uri in insertedUris) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        DocumentPermissionStore(context).clear()
    }

    private fun scanner() = DeviceWastickersScanner(
        context,
        TreePermissionStore(context),
        DocumentPermissionStore(context)
    )

    @Test
    fun appOwnedWastickersInsertedThroughMediaStoreIsDiscovered() {
        assumeTrue("MediaStore.Downloads requires API 29+", Build.VERSION.SDK_INT >= 29)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "gsgwh.wastickers")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/AllStickersDiscoveryTest")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return
        insertedUris.add(uri)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            // A minimal ZIP header is enough; discovery only reads metadata.
            out.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        val outcome = runBlocking { scanner().scan() }

        assertTrue(
            "MediaStore 中本应用写入的 .wastickers 应被发现",
            outcome.files.any { it.displayName == "gsgwh.wastickers" }
        )
        assertTrue(outcome.files.none { !it.displayName.endsWith(".wastickers", true) })
    }

    @Test
    fun revokedOrInvalidSafTreeGrantsAreDroppedWithoutCrashing() {
        val trees = TreePermissionStore(context)
        trees.add(Uri.parse("content://invalid.provider/tree/primary%3ADownload"))

        val outcome = runBlocking { scanner().scan() }

        assertTrue("失效授权应被报告，而不是静默当作无文件", outcome.inaccessibleFolders >= 1)
        assertEquals(0, TreePermissionStore(context).grantedTreeUris().size)
    }

    @Test
    fun directDocumentGrantsSurviveStoreRecreationOnDevice() {
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/" +
                "primary%3ADownload%2Fحزمة%20الملصقات.wastickers"
        )
        DocumentPermissionStore(context).addAll(listOf(uri))

        val reloaded = DocumentPermissionStore(context)
        assertTrue(reloaded.hasAny())
        assertEquals(1, reloaded.grantedDocumentUris().size)
        assertEquals(
            uri.toString(),
            reloaded.grantedDocumentUris().first().toString()
        )
    }
}
