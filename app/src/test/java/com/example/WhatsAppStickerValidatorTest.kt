package com.example

import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.StickerType
import com.example.whatsapp.WhatsAppStickerValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppStickerValidatorTest {

    private fun pack(name: String = "حزمة اختبار") = StickerPack(
        id = 1,
        name = name,
        shortName = "test_pack"
    )

    private fun sticker(
        id: Long,
        type: StickerType = StickerType.PNG,
        size: Long = 40_000,
        duration: Long = 0
    ) = StickerItem(
        id = id,
        packId = 1,
        originalUri = "file:///tmp/sticker_$id.png",
        originalName = "sticker_$id.png",
        displayName = "ملصق $id",
        type = type,
        originalSize = size,
        durationMs = duration
    )

    @Test
    fun fewerThanThreeStickersIsInvalid() {
        val result = WhatsAppStickerValidator.validate(
            pack(),
            listOf(sticker(1), sticker(2))
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun threeStaticStickersAreValid() {
        val result = WhatsAppStickerValidator.validate(
            pack(),
            listOf(sticker(1), sticker(2), sticker(3))
        )
        assertTrue(result.isValid)
        assertTrue(result.isAnimated == false)
    }

    @Test
    fun mixedStaticAndAnimatedIsInvalid() {
        val result = WhatsAppStickerValidator.validate(
            pack(),
            listOf(
                sticker(1, StickerType.PNG),
                sticker(2, StickerType.ANIMATED_WEBP, duration = 2_000),
                sticker(3, StickerType.PNG)
            )
        )
        assertFalse(result.isValid)
    }

    @Test
    fun blankPackNameIsInvalid() {
        val result = WhatsAppStickerValidator.validate(
            pack(name = " "),
            listOf(sticker(1), sticker(2), sticker(3))
        )
        assertFalse(result.isValid)
    }
}
