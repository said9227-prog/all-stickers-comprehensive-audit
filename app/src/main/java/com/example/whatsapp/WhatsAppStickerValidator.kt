package com.example.whatsapp

import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.StickerType

object WhatsAppStickerValidator {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val CANVAS_SIZE = 512
    const val MAX_STATIC_BYTES = 100 * 1024
    const val MAX_ANIMATED_BYTES = 500 * 1024
    const val MAX_DURATION_MS = 10_000L

    fun validate(pack: StickerPack, stickers: List<StickerItem>): WhatsAppPackValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val usable = stickers.filter { it.originalUri.isNotBlank() }
        val animated = usable.filter { it.isAnimatedForWhatsApp() }
        val static = usable.filterNot { it.isAnimatedForWhatsApp() }

        if (pack.name.isBlank()) {
            errors.add("اسم الحزمة مطلوب.")
        }
        if (usable.size < MIN_STICKERS) {
            errors.add("أضف ${MIN_STICKERS - usable.size} ملصقات أخرى على الأقل. الحد الأدنى 3 ملصقات.")
        }
        if (usable.size > MAX_STICKERS) {
            errors.add("الحزمة تحتوي ${usable.size} ملصقًا. الحد الأقصى 30 ملصقًا.")
        }
        if (animated.isNotEmpty() && static.isNotEmpty()) {
            errors.add("لا يمكن خلط الملصقات الثابتة والمتحركة في حزمة واتساب واحدة.")
        }
        val webmCount = usable.count { it.type == StickerType.WEBM }
        if (webmCount > 0) {
            errors.add("$webmCount ملصق بصيغة WebM غير مدعوم لواتساب. استورد نسخة WebP متحركة بدلًا منه.")
        }

        val animatedPack = when {
            animated.isNotEmpty() && static.isEmpty() -> true
            static.isNotEmpty() && animated.isEmpty() -> false
            else -> null
        }

        if (animatedPack == false) {
            val tooLarge = static.count { it.originalSize > MAX_STATIC_BYTES }
            if (tooLarge > 0) {
                warnings.add("$tooLarge ملصق ثابت سيُضغط تلقائيًا ليكون أقل من 100KB.")
            }
        } else if (animatedPack == true) {
            val tooLarge = animated.count { it.originalSize > MAX_ANIMATED_BYTES }
            val tooLong = animated.count { it.durationMs > MAX_DURATION_MS }
            if (tooLarge > 0) {
                warnings.add("$tooLarge ملصق متحرك سيُعاد ترميزه ليكون أقل من 500KB.")
            }
            if (tooLong > 0) {
                warnings.add("$tooLong ملصق متحرك ستُسرّع مدته لتناسب حد 10 ثوانٍ.")
            }
        }

        return WhatsAppPackValidation(
            stickerCount = usable.size,
            staticCount = static.size,
            animatedCount = animated.size,
            isAnimated = animatedPack,
            errors = errors,
            warnings = warnings
        )
    }
}
