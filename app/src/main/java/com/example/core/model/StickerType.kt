package com.example.core.model

enum class StickerType(val displayNameAr: String, val isAnimated: Boolean) {
    STATIC_WEBP("WebP ثابت", false),
    ANIMATED_WEBP("Animated WebP", true),
    PNG("صورة PNG", false),
    JPG("صورة JPG", false),
    WEBM("فيديو WEBM", true),
    UNSUPPORTED("غير مدعوم", false)
}
