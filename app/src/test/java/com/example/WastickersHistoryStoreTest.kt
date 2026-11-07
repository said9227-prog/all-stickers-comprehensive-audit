package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.wastickers.DiscoveryHistoryStore
import com.example.wastickers.DocumentPermissionStore
import com.example.wastickers.ImportedHashLog
import com.example.wastickers.SharedPrefsImportedHashLog
import com.example.wastickers.TreePermissionStore
import com.example.wastickers.WastickersFile
import com.example.wastickers.WastickersFileOps
import com.example.wastickers.WastickersSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies that discovery history really survives an app restart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WastickersHistoryStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DiscoveryHistoryStore(context).clear()
        DocumentPermissionStore(context).clear()
        val trees = TreePermissionStore(context)
        trees.grantedTreeUris().forEach { trees.remove(it) }
    }

    private fun file(id: String) = WastickersFile(
        id = id,
        uri = "content://media/$id",
        displayName = "$id.wastickers",
        sizeBytes = 1024L,
        lastModified = 1_000L,
        source = WastickersSource.MEDIA_STORE
    )

    @Test
    fun `new flag and discovery date survive store recreation`() {
        val firstStore = DiscoveryHistoryStore(context)
        val merge = WastickersFileOps.mergeDiscovery(emptyMap(), listOf(file("a")), 12_345L)
        firstStore.save(merge.records)

        // New instance == app restarted.
        val reloaded = DiscoveryHistoryStore(context).load()
        assertTrue(reloaded.getValue("a").isNew)
        assertEquals(12_345L, reloaded.getValue("a").firstSeenAt)

        val second = WastickersFileOps.mergeDiscovery(reloaded, listOf(file("a")), 99_999L)
        assertTrue(second.newIds.isEmpty())
        assertFalse(second.files.single().isNew)
        assertEquals(12_345L, second.files.single().firstSeenAt)
    }

    @Test
    fun `mark seen persists and keeps old files from being new again`() {
        val store = DiscoveryHistoryStore(context)
        val merge = WastickersFileOps.mergeDiscovery(
            emptyMap(), listOf(file("a"), file("b")), 1_000L
        )
        store.save(merge.records)
        store.markSeen(listOf("a"))

        val reloaded = DiscoveryHistoryStore(context).load()
        assertFalse(reloaded.getValue("a").isNew)
        assertTrue(reloaded.getValue("b").isNew)
        assertEquals(1_000L, reloaded.getValue("a").firstSeenAt)

        val rescan = WastickersFileOps.mergeDiscovery(
            reloaded, listOf(file("a"), file("b")), 5_000L
        )
        assertTrue(rescan.newIds.isEmpty())
        assertTrue(rescan.files.none { it.isNew })
    }

    @Test
    fun `ids with special characters survive persistence`() {
        val store = DiscoveryHistoryStore(context)
        val oddId = "saf:content://com.provider/tree/document/x%2Fy|z"
        val merge = WastickersFileOps.mergeDiscovery(
            emptyMap(),
            listOf(file(oddId)),
            7_000L
        )
        store.save(merge.records)

        val reloaded = DiscoveryHistoryStore(context).load()
        assertTrue(reloaded.containsKey(oddId))
        assertTrue(reloaded.getValue(oddId).isNew)
    }

    @Test
    fun `imported hash log prevents re-import after restart`() {
        val log: ImportedHashLog = SharedPrefsImportedHashLog(context)
        assertFalse(log.contains("abc123"))
        log.add("abc123")

        val reloaded: ImportedHashLog = SharedPrefsImportedHashLog(context)
        assertTrue(reloaded.contains("abc123"))
        assertEquals(setOf("abc123"), reloaded.all())
    }

    @Test
    fun `direct document Uri grants survive app restart`() {
        // ACTION_OPEN_DOCUMENT results must still be readable after the
        // process is killed; Arabic names and spaces must survive the codec.
        val uris = listOf(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fgsgwh.wastickers"),
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3Aحزمة%20الملصقات.wastickers")
        )
        DocumentPermissionStore(context).addAll(uris)

        val reloaded = DocumentPermissionStore(context)
        assertEquals(2, reloaded.grantedDocumentUris().size)
        assertTrue(reloaded.hasAny())
        assertEquals(
            uris.map { it.toString() }.toSet(),
            reloaded.grantedDocumentUris().map { it.toString() }.toSet()
        )

        // A revoked grant is dropped, not treated as access to the whole phone.
        reloaded.remove(uris.first())
        assertEquals(1, reloaded.grantedDocumentUris().size)
    }

    @Test
    fun `tree grants survive app restart and can be revoked`() {
        val tree = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        )
        TreePermissionStore(context).add(tree)

        val reloaded = TreePermissionStore(context)
        assertTrue(reloaded.hasAny())
        assertEquals(1, reloaded.grantedTreeUris().size)

        reloaded.remove(tree)
        assertFalse(TreePermissionStore(context).hasAny())
    }
}
