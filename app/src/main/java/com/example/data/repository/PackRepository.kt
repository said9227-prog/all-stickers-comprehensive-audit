package com.example.data.repository

import com.example.core.model.StickerPack
import com.example.data.db.StickerDao
import com.example.data.db.StickerPackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PackRepository(private val dao: StickerDao) {

    fun getAllPacks(): Flow<List<StickerPack>> {
        return dao.getAllPacksWithStatsFlow().map { list ->
            list.map { stats ->
                StickerPack(
                    id = stats.id,
                    name = stats.name,
                    shortName = stats.shortName,
                    author = stats.author,
                    iconUri = stats.iconUri,
                    defaultEmoji = stats.defaultEmoji,
                    createdAt = stats.createdAt,
                    stickerCount = stats.totalCount,
                    readyCount = stats.readyCount
                )
            }
        }
    }

    suspend fun getPackById(id: Long): StickerPack? {
        return dao.getPackById(id)?.toDomain()
    }

    fun getPackByIdFlow(id: Long): Flow<StickerPack?> {
        return dao.getPackByIdFlow(id).map { it?.toDomain() }
    }

    suspend fun createPack(pack: StickerPack): Long {
        return dao.insertPack(StickerPackEntity.fromDomain(pack))
    }

    suspend fun updatePack(pack: StickerPack) {
        dao.updatePack(StickerPackEntity.fromDomain(pack))
    }

    suspend fun deletePack(id: Long) {
        dao.deletePackById(id)
    }
}
