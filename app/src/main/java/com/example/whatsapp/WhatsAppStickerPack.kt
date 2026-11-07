package com.example.whatsapp

import com.example.core.model.StickerItem
import com.example.core.model.StickerType

data class WhatsAppStickerAsset(
    val id: String,
    val fileName: String,
    val emojis: List<String>
)

data class WhatsAppStickerPackModel(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val imageDataVersion: String = "1",
    val animated: Boolean,
    val stickers: List<WhatsAppStickerAsset>
)

data class WhatsAppPackValidation(
    val stickerCount: Int,
    val staticCount: Int,
    val animatedCount: Int,
    val isAnimated: Boolean?,
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty() && isAnimated != null
}

fun StickerItem.isAnimatedForWhatsApp(): Boolean {
    return type == StickerType.ANIMATED_WEBP || type == StickerType.WEBM
}
