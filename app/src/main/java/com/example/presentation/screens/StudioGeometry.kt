package com.example.presentation.screens

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A single animation keyframe for the selected studio layer. Position is stored
 * in the same local units used by [Text3DLayer] (offsetX/Y/Z), rotation is in
 * degrees and scale is uniform.
 */
internal data class StudioKeyframe(
    val timeMs: Long,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val scale: Float = 1f
) {
    fun lerp(other: StudioKeyframe, t: Float): StudioKeyframe {
        val clamped = t.coerceIn(0f, 1f)
        return StudioKeyframe(
            timeMs = (timeMs + (other.timeMs - timeMs) * clamped).toLong(),
            x = x + (other.x - x) * clamped,
            y = y + (other.y - y) * clamped,
            z = z + (other.z - z) * clamped,
            rotationX = rotationX + (other.rotationX - rotationX) * clamped,
            rotationY = rotationY + (other.rotationY - rotationY) * clamped,
            rotationZ = rotationZ + (other.rotationZ - rotationZ) * clamped,
            scale = scale + (other.scale - scale) * clamped
        )
    }
}

internal enum class AnimationEasing(val label: String) {
    LINEAR("خطي"),
    EASE_IN("دخول"),
    EASE_OUT("خروج"),
    EASE_IN_OUT("دخول وخروج"),
    SMOOTH("ناعم")
}

internal fun applyEasing(easing: AnimationEasing, t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return when (easing) {
        AnimationEasing.LINEAR -> x
        AnimationEasing.EASE_IN -> x * x
        AnimationEasing.EASE_OUT -> 1f - (1f - x) * (1f - x)
        AnimationEasing.EASE_IN_OUT -> if (x < 0.5f) {
            2f * x * x
        } else {
            1f - (-2f * x + 2f).pow(2) / 2f
        }
        AnimationEasing.SMOOTH -> x * x * (3f - 2f * x)
    }
}

/**
 * Samples a sorted keyframe list at [timeMs]. Returns null when there are no
 * keyframes, otherwise the interpolated pose (clamped before/after the range).
 */
internal fun sampleKeyframes(
    keyframes: List<StudioKeyframe>,
    timeMs: Long,
    easing: AnimationEasing
): StudioKeyframe? {
    if (keyframes.isEmpty()) return null
    val sorted = keyframes.sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return sorted.first()
    if (timeMs >= sorted.last().timeMs) return sorted.last()
    for (index in 0 until sorted.lastIndex) {
        val start = sorted[index]
        val end = sorted[index + 1]
        if (timeMs in start.timeMs..end.timeMs) {
            val span = (end.timeMs - start.timeMs).coerceAtLeast(1L)
            val raw = (timeMs - start.timeMs).toFloat() / span.toFloat()
            return start.lerp(end, applyEasing(easing, raw))
        }
    }
    return sorted.last()
}

internal enum class StudioAnimationPreset(val label: String) {
    ROTATE_360("دوران 360"),
    FLOATING("طفو"),
    BOUNCE("ارتداد"),
    ZOOM("تكبير"),
    FLY_IN("دخول طائر"),
    FLY_OUT("خروج طائر"),
    SHAKE("اهتزاز"),
    WAVE("موجة"),
    SPIN("لف سريع"),
    ASSEMBLE("تجميع"),
    EXPLODE("تفكيك")
}

/**
 * Builds a real keyframe track for a preset, starting from the layer's current
 * pose. The returned list is sorted and safe to feed to [sampleKeyframes].
 */
internal fun buildPresetKeyframes(
    preset: StudioAnimationPreset,
    base: StudioKeyframe,
    durationMs: Long
): List<StudioKeyframe> {
    val end = durationMs.coerceAtLeast(250L)
    fun at(fraction: Float, block: (StudioKeyframe) -> StudioKeyframe): StudioKeyframe {
        return block(base.copy(timeMs = (end * fraction).toLong()))
    }
    return when (preset) {
        StudioAnimationPreset.ROTATE_360 -> listOf(
            at(0f) { it },
            at(1f) { it.copy(rotationY = base.rotationY + 360f) }
        )
        StudioAnimationPreset.FLOATING -> listOf(
            at(0f) { it },
            at(0.5f) { it.copy(y = base.y + 0.28f) },
            at(1f) { it }
        )
        StudioAnimationPreset.BOUNCE -> listOf(
            at(0f) { it },
            at(0.25f) { it.copy(y = base.y + 0.34f) },
            at(0.5f) { it },
            at(0.75f) { it.copy(y = base.y + 0.18f) },
            at(1f) { it }
        )
        StudioAnimationPreset.ZOOM -> listOf(
            at(0f) { it.copy(scale = base.scale * 0.7f) },
            at(0.6f) { it.copy(scale = base.scale * 1.25f) },
            at(1f) { it }
        )
        StudioAnimationPreset.FLY_IN -> listOf(
            at(0f) { it.copy(x = base.x - 2.4f, z = base.z - 1.2f, scale = base.scale * 0.4f) },
            at(1f) { it }
        )
        StudioAnimationPreset.FLY_OUT -> listOf(
            at(0f) { it },
            at(1f) { it.copy(x = base.x + 2.4f, z = base.z - 1.2f, scale = base.scale * 0.4f) }
        )
        StudioAnimationPreset.SHAKE -> listOf(
            at(0f) { it },
            at(0.15f) { it.copy(x = base.x + 0.08f) },
            at(0.3f) { it.copy(x = base.x - 0.08f) },
            at(0.45f) { it.copy(x = base.x + 0.06f) },
            at(0.6f) { it.copy(x = base.x - 0.06f) },
            at(0.75f) { it.copy(x = base.x + 0.03f) },
            at(1f) { it }
        )
        StudioAnimationPreset.WAVE -> listOf(
            at(0f) { it },
            at(0.25f) { it.copy(rotationZ = base.rotationZ - 14f, y = base.y + 0.08f) },
            at(0.5f) { it.copy(rotationZ = base.rotationZ + 14f, y = base.y - 0.04f) },
            at(0.75f) { it.copy(rotationZ = base.rotationZ - 8f, y = base.y + 0.05f) },
            at(1f) { it }
        )
        StudioAnimationPreset.SPIN -> listOf(
            at(0f) { it },
            at(0.35f) { it.copy(rotationY = base.rotationY + 220f, scale = base.scale * 1.08f) },
            at(1f) { it.copy(rotationY = base.rotationY + 360f) }
        )
        StudioAnimationPreset.ASSEMBLE -> listOf(
            at(0f) { it.copy(scale = 0.05f, rotationY = base.rotationY - 120f) },
            at(0.7f) { it.copy(scale = base.scale * 1.1f, rotationY = base.rotationY + 12f) },
            at(1f) { it }
        )
        StudioAnimationPreset.EXPLODE -> listOf(
            at(0f) { it },
            at(1f) {
                it.copy(
                    x = base.x + 0.6f,
                    y = base.y + 0.35f,
                    rotationZ = base.rotationZ + 35f,
                    scale = base.scale * 0.65f
                )
            }
        )
    }.sortedBy { it.timeMs }
}

internal fun extrusionPlaneCount(extrusion: Float): Int {
    return (1f + extrusion.coerceIn(0f, 1f) * 8f).roundToInt().coerceIn(1, 9)
}

internal fun Text3DLayer.toKeyframe(timeMs: Long): StudioKeyframe = StudioKeyframe(
    timeMs = timeMs,
    x = offsetX,
    y = offsetY,
    z = offsetZ,
    rotationX = rotationX,
    rotationY = rotationY,
    rotationZ = rotationZ,
    scale = scale
)
