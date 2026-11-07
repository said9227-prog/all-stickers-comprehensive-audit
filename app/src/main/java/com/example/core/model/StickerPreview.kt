package com.example.core.model

/**
 * A lightweight sticker representation used for pack thumbnails on the home screen.
 * The full [StickerItem] is only loaded when a pack is opened.
 */
data class StickerPreview(
    val id: Long,
    val packId: Long,
    val previewUri: String,
    val type: StickerType
) {
    val isAnimated: Boolean get() = type == StickerType.ANIMATED_WEBP || type == StickerType.WEBM
}
