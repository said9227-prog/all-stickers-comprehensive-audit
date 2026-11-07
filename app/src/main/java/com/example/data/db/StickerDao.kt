package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PackWithStats(
    val id: Long,
    val name: String,
    val shortName: String,
    val author: String,
    val iconUri: String?,
    val defaultEmoji: String,
    val createdAt: Long,
    val totalCount: Int,
    val readyCount: Int
)

data class StickerPreviewRow(
    val id: Long,
    val packId: Long,
    val originalUri: String,
    val outputUri: String?,
    val type: String,
    val sortOrder: Int
)

@Dao
interface StickerDao {
    // --- Packs ---
    @Query("""
        SELECT p.id, p.name, p.shortName, p.author, p.iconUri, p.defaultEmoji, p.createdAt,
               COUNT(s.id) AS totalCount,
               SUM(CASE WHEN s.conversionStatus = 'SUCCESS' THEN 1 ELSE 0 END) AS readyCount
        FROM sticker_packs p
        LEFT JOIN stickers s ON s.packId = p.id
        GROUP BY p.id
        ORDER BY p.createdAt DESC
    """)
    fun getAllPacksWithStatsFlow(): Flow<List<PackWithStats>>

    @Query("SELECT * FROM sticker_packs WHERE id = :id LIMIT 1")
    suspend fun getPackById(id: Long): StickerPackEntity?

    @Query("SELECT * FROM sticker_packs WHERE id = :id LIMIT 1")
    fun getPackByIdFlow(id: Long): Flow<StickerPackEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPack(pack: StickerPackEntity): Long

    @Update
    suspend fun updatePack(pack: StickerPackEntity)

    @Delete
    suspend fun deletePack(pack: StickerPackEntity)

    @Query("DELETE FROM sticker_packs WHERE id = :id")
    suspend fun deletePackById(id: Long)

    // --- Stickers ---
    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY sortOrder ASC, id ASC")
    fun getStickersByPackIdFlow(packId: Long): Flow<List<StickerEntity>>

    /**
     * Lightweight projection used by the pack list to show animated thumbnails.
     * Only the columns needed for rendering are loaded, so large libraries do not
     * pull full sticker entities into memory.
     */
    @Query(
        "SELECT id, packId, originalUri, outputUri, type, sortOrder FROM stickers " +
            "ORDER BY packId ASC, sortOrder ASC, id ASC"
    )
    fun getStickerPreviewsFlow(): Flow<List<StickerPreviewRow>>

    @Query("SELECT * FROM stickers WHERE packId = :packId ORDER BY sortOrder ASC, id ASC")
    suspend fun getStickersByPackId(packId: Long): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id LIMIT 1")
    suspend fun getStickerById(id: Long): StickerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: StickerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>): List<Long>

    @Update
    suspend fun updateSticker(sticker: StickerEntity)

    @Update
    suspend fun updateStickers(stickers: List<StickerEntity>)

    @Delete
    suspend fun deleteSticker(sticker: StickerEntity)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteStickerById(id: Long)

    @Query("DELETE FROM stickers WHERE packId = :packId")
    suspend fun deleteStickersByPackId(packId: Long)

    @Query("UPDATE stickers SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
