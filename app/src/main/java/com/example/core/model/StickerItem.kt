package com.example.core.model

data class StickerItem(
    val id: Long = 0,
    val packId: Long = 0,
    val originalUri: String,
    val originalName: String,
    val displayName: String,
    val emoji: String = "✨",
    val type: StickerType,
    val originalSize: Long = 0L,
    val convertedSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val outputUri: String? = null,
    val outputPath: String? = null,
    val conversionStatus: ConversionStatus = ConversionStatus.PENDING,
    val errorMessage: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceUri: String? = null,
    val sourceMimeType: String? = null,
    val sourceDisplayName: String? = null
) {
    val formattedOriginalSize: String
        get() = formatSize(originalSize)

    val formattedConvertedSize: String
        get() = if (convertedSize > 0) formatSize(convertedSize) else "-"

    private fun formatSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
