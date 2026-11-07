package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.data.db.StickerPackEntity
import com.example.core.model.StickerType

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = StickerPackEntity::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packId"]), Index(value = ["sortOrder"])]
)
data class StickerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packId: Long,
    val originalUri: String,
    val originalName: String,
    val displayName: String,
    val emoji: String,
    val type: String,
    val originalSize: Long,
    val convertedSize: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val outputUri: String?,
    val outputPath: String?,
    val conversionStatus: String,
    val errorMessage: String?,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceUri: String? = null,
    val sourceMimeType: String? = null,
    val sourceDisplayName: String? = null
) {
    fun toDomain(): StickerItem {
        val parsedType = try {
            StickerType.valueOf(type)
        } catch (_: Exception) {
            StickerType.UNSUPPORTED
        }
        val parsedStatus = try {
            ConversionStatus.valueOf(conversionStatus)
        } catch (_: Exception) {
            ConversionStatus.PENDING
        }
        return StickerItem(
            id = id,
            packId = packId,
            originalUri = originalUri,
            originalName = originalName,
            displayName = displayName,
            emoji = emoji,
            type = parsedType,
            originalSize = originalSize,
            convertedSize = convertedSize,
            width = width,
            height = height,
            durationMs = durationMs,
            outputUri = outputUri,
            outputPath = outputPath,
            conversionStatus = parsedStatus,
            errorMessage = errorMessage,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sourceUri = sourceUri,
            sourceMimeType = sourceMimeType,
            sourceDisplayName = sourceDisplayName
        )
    }

    companion object {
        fun fromDomain(item: StickerItem): StickerEntity {
            return StickerEntity(
                id = item.id,
                packId = item.packId,
                originalUri = item.originalUri,
                originalName = item.originalName,
                displayName = item.displayName,
                emoji = item.emoji,
                type = item.type.name,
                originalSize = item.originalSize,
                convertedSize = item.convertedSize,
                width = item.width,
                height = item.height,
                durationMs = item.durationMs,
                outputUri = item.outputUri,
                outputPath = item.outputPath,
                conversionStatus = item.conversionStatus.name,
                errorMessage = item.errorMessage,
                sortOrder = item.sortOrder,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                sourceUri = item.sourceUri,
                sourceMimeType = item.sourceMimeType,
                sourceDisplayName = item.sourceDisplayName
            )
        }
    }
}
