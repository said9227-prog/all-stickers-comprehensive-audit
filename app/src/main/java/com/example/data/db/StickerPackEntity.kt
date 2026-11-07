package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.StickerPack

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val shortName: String,
    val author: String,
    val iconUri: String?,
    val defaultEmoji: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(stickerCount: Int = 0, readyCount: Int = 0): StickerPack {
        return StickerPack(
            id = id,
            name = name,
            shortName = shortName,
            author = author,
            iconUri = iconUri,
            defaultEmoji = defaultEmoji,
            createdAt = createdAt,
            stickerCount = stickerCount,
            readyCount = readyCount
        )
    }

    companion object {
        fun fromDomain(pack: StickerPack): StickerPackEntity {
            return StickerPackEntity(
                id = pack.id,
                name = pack.name,
                shortName = pack.shortName,
                author = pack.author,
                iconUri = pack.iconUri,
                defaultEmoji = pack.defaultEmoji,
                createdAt = pack.createdAt
            )
        }
    }
}
