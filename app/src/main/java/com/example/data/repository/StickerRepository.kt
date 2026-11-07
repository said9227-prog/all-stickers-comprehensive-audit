package com.example.data.repository

import com.example.core.model.StickerItem
import com.example.core.model.StickerPreview
import com.example.core.model.StickerType
import com.example.data.db.StickerDao
import com.example.data.db.StickerEntity
import com.example.data.db.StickerPreviewRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StickerRepository(private val dao: StickerDao) {

    fun getStickersByPackIdFlow(packId: Long): Flow<List<StickerItem>> {
        return dao.getStickersByPackIdFlow(packId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Returns up to [maxPerPack] previews for every pack, grouped by pack id.
     * Ordering follows the sticker sort order so the pack card stays stable.
     */
    fun getStickerPreviewsFlow(maxPerPack: Int = 4): Flow<Map<Long, List<StickerPreview>>> {
        return dao.getStickerPreviewsFlow().map { rows ->
            groupStickerPreviews(rows, maxPerPack)
        }
    }

    suspend fun getStickersByPackId(packId: Long): List<StickerItem> {
        return dao.getStickersByPackId(packId).map { it.toDomain() }
    }

    suspend fun getStickerById(id: Long): StickerItem? {
        return dao.getStickerById(id)?.toDomain()
    }

    suspend fun addSticker(sticker: StickerItem): Long {
        return dao.insertSticker(StickerEntity.fromDomain(sticker))
    }

    suspend fun addStickers(stickers: List<StickerItem>): List<Long> {
        return dao.insertStickers(stickers.map { StickerEntity.fromDomain(it) })
    }

    suspend fun updateSticker(sticker: StickerItem) {
        dao.updateSticker(StickerEntity.fromDomain(sticker))
    }

    suspend fun updateStickers(stickers: List<StickerItem>) {
        dao.updateStickers(stickers.map { StickerEntity.fromDomain(it) })
    }

    suspend fun deleteSticker(id: Long) {
        dao.deleteStickerById(id)
    }

    suspend fun reorderStickers(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            dao.updateSortOrder(id, index)
        }
    }
}

/**
 * Pure mapping used by [StickerRepository.getStickerPreviewsFlow]; kept
 * standalone so it can be unit tested without a Android/Room environment.
 */
internal fun groupStickerPreviews(
    rows: List<StickerPreviewRow>,
    maxPerPack: Int
): Map<Long, List<StickerPreview>> {
    if (maxPerPack <= 0) return emptyMap()
    val grouped = LinkedHashMap<Long, MutableList<StickerPreview>>()
    rows.forEach { row ->
        val bucket = grouped.getOrPut(row.packId) { mutableListOf() }
        if (bucket.size < maxPerPack) {
            val type = try {
                StickerType.valueOf(row.type)
            } catch (_: Exception) {
                StickerType.UNSUPPORTED
            }
            val uri = row.originalUri.ifBlank { row.outputUri.orEmpty() }
            if (uri.isNotBlank()) {
                bucket += StickerPreview(
                    id = row.id,
                    packId = row.packId,
                    previewUri = uri,
                    type = type
                )
            }
        }
    }
    return grouped
}
