package com.example.core.model

data class StickerPack(
    val id: Long = 0,
    val name: String,
    val shortName: String,
    val author: String = "",
    val iconUri: String? = null,
    val defaultEmoji: String = "✨",
    val createdAt: Long = System.currentTimeMillis(),
    val stickerCount: Int = 0,
    val readyCount: Int = 0
)
