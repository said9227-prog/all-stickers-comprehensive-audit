package com.example.whatsapp

import com.example.media.WebpInfo

/**
 * Decides whether a sticker can be handed to WhatsApp WITHOUT re-encoding.
 *
 * The old path decoded and re-encoded EVERY static sticker through up to seven
 * quality attempts even when the source was already a compliant 512x512 WebP
 * under 100KB. That was the dominant cost of "add to WhatsApp".
 *
 * This is a pure, side-effect-free decision so the optimisation is unit
 * testable on the JVM without the native WebP encoders. When it says "reuse",
 * the exporter copies the original bytes verbatim: quality, alpha, animation
 * frames and frame durations are preserved exactly.
 */
object WhatsAppWebpFastPath {

    data class Decision(val reuse: Boolean, val reason: String)

    fun decide(
        info: WebpInfo,
        byteSize: Long,
        animatedPack: Boolean,
        canvasSize: Int = WhatsAppStickerValidator.CANVAS_SIZE,
        maxBytes: Int = if (animatedPack) {
            WhatsAppStickerValidator.MAX_ANIMATED_BYTES
        } else {
            WhatsAppStickerValidator.MAX_STATIC_BYTES
        },
        maxDurationMs: Long = WhatsAppStickerValidator.MAX_DURATION_MS
    ): Decision {
        if (!info.isWebp) return Decision(false, "source is not WebP")
        if (byteSize <= 0L) return Decision(false, "source is empty")
        if (byteSize > maxBytes) return Decision(false, "source exceeds WhatsApp size limit")
        if (info.width != canvasSize || info.height != canvasSize) {
            return Decision(false, "source is not ${canvasSize}x${canvasSize}")
        }

        return if (animatedPack) {
            when {
                !info.isAnimated -> Decision(false, "static source in an animated pack")
                info.totalDurationMs <= 0L -> Decision(false, "unknown animation duration")
                info.totalDurationMs > maxDurationMs -> Decision(false, "animation too long")
                else -> Decision(true, "compliant animated WebP: copied verbatim")
            }
        } else {
            // An animated source in a static pack MUST be converted to its
            // first frame; never silently turn animation into a still image
            // without the user's explicit "convert animated to static" choice.
            if (info.isAnimated) {
                Decision(false, "animated source must be converted for a static pack")
            } else {
                Decision(true, "compliant static WebP: copied verbatim")
            }
        }
    }
}
