package com.example

import com.example.wastickers.DiscoveryRecord
import com.example.wastickers.WastickersFile
import com.example.wastickers.WastickersFileOps
import com.example.wastickers.WastickersSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for WASTICKERS discovery, refresh merging, persistent NEW
 * detection, search, sorting and multi-selection.
 */
class WastickersDiscoveryTest {

    private fun file(
        id: String,
        name: String,
        size: Long = 2048L,
        modified: Long = 1_000L,
        source: WastickersSource = WastickersSource.MEDIA_STORE
    ) = WastickersFile(
        id = id,
        uri = "content://media/$id",
        displayName = name,
        sizeBytes = size,
        lastModified = modified,
        source = source
    )

    @Test
    fun `extension detection is case insensitive`() {
        assertTrue(WastickersFileOps.isWastickersName("Pack.WASTICKERS"))
        assertTrue(WastickersFileOps.isWastickersName("حزمة.wastickers"))
        assertFalse(WastickersFileOps.isWastickersName("pack.zip"))
        assertFalse(WastickersFileOps.isWastickersName(null))
    }

    @Test
    fun `dedupe removes repeated ids and cross-source duplicates`() {
        val files = listOf(
            file("a", "one.wastickers"),
            file("a", "one.wastickers"),
            file("media:1", "shared.wastickers", 100L, 500L, WastickersSource.MEDIA_STORE),
            file("saf:1", "shared.wastickers", 100L, 500L, WastickersSource.SAF_TREE)
        )
        assertEquals(2, WastickersFileOps.dedupe(files).size)
    }

    @Test
    fun `files are ordered by newest modification first`() {
        val files = listOf(
            file("old", "old.wastickers", modified = 100L),
            file("new", "new.wastickers", modified = 900L),
            file("mid", "mid.wastickers", modified = 500L)
        )
        assertEquals(
            listOf("new", "mid", "old"),
            WastickersFileOps.sortNewestFirst(files).map { it.id }
        )
    }

    @Test
    fun `first scan marks every file as new with a discovery date`() {
        val now = 1_700_000_000_000L
        val scanned = listOf(file("a", "a.wastickers"), file("b", "b.wastickers"))
        val merge = WastickersFileOps.mergeDiscovery(emptyMap(), scanned, now)

        assertEquals(setOf("a", "b"), merge.newIds)
        assertTrue(merge.files.all { it.isNew })
        assertTrue(merge.files.all { it.firstSeenAt == now })
    }

    @Test
    fun `refresh discovers only genuinely new files`() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 60_000L
        val original = listOf(file("a", "a.wastickers"), file("b", "b.wastickers"))
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), original, t1)

        val rescan = original + file("c", "c.wastickers", modified = t2)
        val second = WastickersFileOps.mergeDiscovery(first.records, rescan, t2)

        assertEquals(setOf("c"), second.newIds)
        assertEquals(3, second.files.size)
        assertTrue(second.files.first { it.id == "c" }.isNew)
        assertFalse(second.files.first { it.id == "a" }.isNew)
        assertFalse(second.files.first { it.id == "b" }.isNew)
    }

    @Test
    fun `discovery date is stable across refreshes and app restarts`() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 3_600_000L
        val scanned = listOf(file("a", "a.wastickers"))
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), scanned, t1)

        // Simulate restart: records are reloaded from persistent storage.
        val reloaded: Map<String, DiscoveryRecord> = first.records
        val afterRestart = WastickersFileOps.mergeDiscovery(reloaded, scanned, t2)

        assertEquals(t1, afterRestart.files.single().firstSeenAt)
        assertTrue(afterRestart.newIds.isEmpty())
        assertFalse(afterRestart.files.single().isNew)
    }

    @Test
    fun `a returning file is never reported as new again`() {
        val t1 = 1_000L
        val scanned = listOf(file("a", "a.wastickers"))
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), scanned, t1)
        val gone = WastickersFileOps.mergeDiscovery(first.records, emptyList(), t1 + 10)
        val back = WastickersFileOps.mergeDiscovery(gone.records, scanned, t1 + 20)

        assertTrue(back.newIds.isEmpty())
        assertFalse(back.files.single().isNew)
    }

    @Test
    fun `mark seen clears new flag but keeps discovery date`() {
        val t1 = 1_000L
        val scanned = listOf(file("a", "a.wastickers"), file("b", "b.wastickers"))
        val first = WastickersFileOps.mergeDiscovery(emptyMap(), scanned, t1)
        val marked = WastickersFileOps.markSeen(first.records, listOf("a"))

        assertFalse(marked.getValue("a").isNew)
        assertTrue(marked.getValue("b").isNew)
        assertEquals(t1, marked.getValue("a").firstSeenAt)
    }

    @Test
    fun `search is live and case insensitive`() {
        val files = listOf(
            file("1", "Cats.wastickers"),
            file("2", "حزمة_القطط.wastickers"),
            file("3", "DOGS.wastickers")
        )
        assertEquals(1, WastickersFileOps.filterByQuery(files, "cats").size)
        assertEquals(1, WastickersFileOps.filterByQuery(files, "CATS").size)
        assertEquals(1, WastickersFileOps.filterByQuery(files, "القطط").size)
        assertEquals(3, WastickersFileOps.filterByQuery(files, "  ").size)
    }

    @Test
    fun `search shows no results for an unknown keyword`() {
        val files = listOf(file("1", "cats.wastickers"))
        assertTrue(WastickersFileOps.filterByQuery(files, "zzz").isEmpty())
    }

    @Test
    fun `multi selection supports single multiple select all and clear`() {
        var selection = emptySet<String>()
        selection = WastickersFileOps.toggleSelection(selection, "a")
        assertEquals(setOf("a"), selection)
        selection = WastickersFileOps.toggleSelection(selection, "b")
        assertEquals(setOf("a", "b"), selection)
        selection = WastickersFileOps.toggleSelection(selection, "a")
        assertEquals(setOf("b"), selection)
        selection = WastickersFileOps.selectAll(selection, listOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), selection)
        selection = emptySet()
        assertTrue(selection.isEmpty())
    }

    @Test
    fun `size and date formatting are human readable`() {
        assertEquals("0 B", WastickersFileOps.formatSize(0L))
        assertEquals("512 B", WastickersFileOps.formatSize(512L))
        assertEquals("1.5 KB", WastickersFileOps.formatSize(1536L))
        assertEquals("2.00 MB", WastickersFileOps.formatSize(2L * 1024 * 1024))
        assertEquals("-", WastickersFileOps.formatDate(0L))
        assertTrue(WastickersFileOps.formatDate(1_700_000_000_000L).contains("2023"))
    }
}
