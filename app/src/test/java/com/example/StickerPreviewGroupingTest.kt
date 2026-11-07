package com.example

import com.example.core.model.StickerType
import com.example.data.db.StickerPreviewRow
import com.example.data.repository.groupStickerPreviews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPreviewGroupingTest {

    @Test
    fun `groups previews by pack and preserves row order`() {
        val rows = listOf(
            row(id = 11, packId = 1, sortOrder = 1),
            row(id = 12, packId = 1, sortOrder = 0),
            row(id = 21, packId = 2, sortOrder = 0)
        )

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)

        assertEquals(setOf(1L, 2L), grouped.keys)
        // Row order is preserved; the SQL query is what applies the sort.
        assertEquals(listOf(11L, 12L), grouped.getValue(1).map { it.id })
        assertEquals(listOf(21L), grouped.getValue(2).map { it.id })
    }

    @Test
    fun `caps previews per pack`() {
        val rows = (1..10).map { index -> row(id = index.toLong(), packId = 7, sortOrder = index) }

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)

        assertEquals(4, grouped.getValue(7).size)
        assertEquals(listOf(1L, 2L, 3L, 4L), grouped.getValue(7).map { it.id })
    }

    @Test
    fun `skips rows without a usable uri`() {
        val rows = listOf(
            row(id = 1, packId = 1, sortOrder = 0, originalUri = "", outputUri = null),
            row(id = 2, packId = 1, sortOrder = 1, originalUri = "", outputUri = null),
            row(id = 3, packId = 1, sortOrder = 2, originalUri = "content://sticker/3")
        )

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)

        assertEquals(listOf(3L), grouped.getValue(1).map { it.id })
    }

    @Test
    fun `falls back to output uri when original is blank`() {
        val rows = listOf(
            row(
                id = 5,
                packId = 3,
                sortOrder = 0,
                originalUri = "",
                outputUri = "file:///out/sticker.webp"
            )
        )

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)

        assertEquals("file:///out/sticker.webp", grouped.getValue(3).single().previewUri)
    }

    @Test
    fun `unknown sticker type becomes unsupported instead of crashing`() {
        val rows = listOf(
            row(id = 9, packId = 1, sortOrder = 0, type = "NOT_A_TYPE")
        )

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)

        assertEquals(StickerType.UNSUPPORTED, grouped.getValue(1).single().type)
    }

    @Test
    fun `animated webp is flagged as animated`() {
        val rows = listOf(
            row(id = 1, packId = 1, sortOrder = 0, type = StickerType.ANIMATED_WEBP.name),
            row(id = 2, packId = 1, sortOrder = 1, type = StickerType.STATIC_WEBP.name)
        )

        val grouped = groupStickerPreviews(rows, maxPerPack = 4)
        val previews = grouped.getValue(1)

        assertTrue(previews[0].isAnimated)
        assertTrue(!previews[1].isAnimated)
    }

    @Test
    fun `non positive limit returns empty map`() {
        val rows = listOf(row(id = 1, packId = 1, sortOrder = 0))

        assertTrue(groupStickerPreviews(rows, maxPerPack = 0).isEmpty())
    }

    private fun row(
        id: Long,
        packId: Long,
        sortOrder: Int,
        originalUri: String = "content://sticker/$id",
        outputUri: String? = null,
        type: String = StickerType.STATIC_WEBP.name
    ) = StickerPreviewRow(
        id = id,
        packId = packId,
        originalUri = originalUri,
        outputUri = outputUri,
        type = type,
        sortOrder = sortOrder
    )
}
