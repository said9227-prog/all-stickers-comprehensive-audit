package com.example.whatsapp

import com.example.core.model.StickerItem
import com.example.core.model.StickerPack

/**
 * A single WhatsApp-ready sub pack extracted from a sticker collection.
 *
 * WhatsApp only accepts packs that are fully static or fully animated, with
 * 3..30 stickers. A collection larger than that (or mixed) is therefore split
 * into several independent sub packs before export.
 */
data class WhatsAppPackPlan(
    val id: String,
    val name: String,
    val stickers: List<StickerItem>,
    val animated: Boolean,
    val chunkIndex: Int,
    val chunkCount: Int,
    val groupLabel: String
) {
    val stickerCount: Int get() = stickers.size

    val canAdd: Boolean
        get() = stickerCount >= WhatsAppStickerValidator.MIN_STICKERS &&
            stickerCount <= WhatsAppStickerValidator.MAX_STICKERS

    val missingForMinimum: Int
        get() = (WhatsAppStickerValidator.MIN_STICKERS - stickerCount).coerceAtLeast(0)

    val identifierSuffix: String
        get() = buildString {
            append(if (animated) "animated" else "static")
            if (chunkCount > 1) append('_').append(chunkIndex + 1)
        }
}

data class WhatsAppPackPlanSet(
    val plans: List<WhatsAppPackPlan>,
    val usableCount: Int,
    val staticCount: Int,
    val animatedCount: Int,
    val skippedCount: Int
) {
    val hasMixed: Boolean get() = staticCount > 0 && animatedCount > 0
    val oversized: Boolean get() = usableCount > WhatsAppStickerValidator.MAX_STICKERS
    val addablePlans: List<WhatsAppPackPlan> get() = plans.filter { it.canAdd }
}

/**
 * Splits stickers into WhatsApp-compatible packs:
 *  - animated and static stickers never share a pack;
 *  - each pack is limited to 30 stickers (31 -> 30 + 1);
 *  - packs with fewer than 3 stickers are kept so the UI can show them as
 *    "waiting for more stickers" instead of silently dropping them.
 */
object WhatsAppPackPlanner {

    fun plan(
        pack: StickerPack,
        stickers: List<StickerItem>,
        convertAnimatedToStatic: Boolean = false
    ): WhatsAppPackPlanSet {
        val usable = stickers
            .filter { it.originalUri.isNotBlank() }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val baseName = pack.name.trim().ifBlank { "ملصقاتي" }.take(96)

        val animatedFlags = usable.map { it.isAnimatedForWhatsApp() }
        val animatedCount = if (convertAnimatedToStatic) 0 else animatedFlags.count { it }
        val staticCount = usable.size - animatedCount

        val staticStickers = if (convertAnimatedToStatic) {
            usable
        } else {
            usable.filterNot { it.isAnimatedForWhatsApp() }
        }
        val animatedStickers = if (convertAnimatedToStatic) {
            emptyList()
        } else {
            usable.filter { it.isAnimatedForWhatsApp() }
        }

        val plans = mutableListOf<WhatsAppPackPlan>()
        if (staticStickers.isNotEmpty()) {
            plans += buildGroup(
                baseName = baseName,
                stickers = staticStickers,
                animated = false,
                groupLabel = "ثابت",
                includeGroupSuffix = animatedFlags.any { it }
            )
        }
        if (animatedStickers.isNotEmpty()) {
            plans += buildGroup(
                baseName = baseName,
                stickers = animatedStickers,
                animated = true,
                groupLabel = "متحرك",
                includeGroupSuffix = staticStickers.isNotEmpty()
            )
        }

        return WhatsAppPackPlanSet(
            plans = plans,
            usableCount = usable.size,
            staticCount = staticCount,
            animatedCount = animatedCount,
            skippedCount = stickers.size - usable.size
        )
    }

    private fun buildGroup(
        baseName: String,
        stickers: List<StickerItem>,
        animated: Boolean,
        groupLabel: String,
        includeGroupSuffix: Boolean
    ): List<WhatsAppPackPlan> {
        val max = WhatsAppStickerValidator.MAX_STICKERS
        val chunks = stickers.chunked(max)
        return chunks.mapIndexed { index, chunk ->
            val name = buildName(
                baseName = baseName,
                groupLabel = groupLabel,
                includeGroupSuffix = includeGroupSuffix,
                chunkIndex = index,
                chunkCount = chunks.size
            )
            WhatsAppPackPlan(
                id = "${if (animated) "animated" else "static"}_$index",
                name = name,
                stickers = chunk,
                animated = animated,
                chunkIndex = index,
                chunkCount = chunks.size,
                groupLabel = groupLabel
            )
        }
    }

    private fun buildName(
        baseName: String,
        groupLabel: String,
        includeGroupSuffix: Boolean,
        chunkIndex: Int,
        chunkCount: Int
    ): String {
        val builder = StringBuilder(baseName)
        if (includeGroupSuffix) {
            builder.append(" · ").append(groupLabel)
        }
        if (chunkCount > 1) {
            builder.append(" (").append(chunkIndex + 1).append('/').append(chunkCount).append(')')
        }
        return builder.toString().take(128)
    }
}
