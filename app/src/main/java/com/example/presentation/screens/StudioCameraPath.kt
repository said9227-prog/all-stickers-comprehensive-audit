package com.example.presentation.screens

import io.github.sceneview.math.Position

/**
 * One keyframe of a camera motion path. The camera travels from keyframe to
 * keyframe over time during playback/export.
 */
data class StudioCameraKeyframe(
    val timeMs: Long,
    val position: Position,
    val lookAt: Position = Position(0f, 0f, 0f)
) {
    fun lerp(other: StudioCameraKeyframe, t: Float): StudioCameraKeyframe {
        val c = t.coerceIn(0f, 1f)
        return StudioCameraKeyframe(
            timeMs = (timeMs + (other.timeMs - timeMs) * c).toLong(),
            position = Position(
                x = position.x + (other.position.x - position.x) * c,
                y = position.y + (other.position.y - position.y) * c,
                z = position.z + (other.position.z - position.z) * c
            ),
            lookAt = Position(
                x = lookAt.x + (other.lookAt.x - lookAt.x) * c,
                y = lookAt.y + (other.lookAt.y - lookAt.y) * c,
                z = lookAt.z + (other.lookAt.z - lookAt.z) * c
            )
        )
    }
}

/**
 * Built-in camera path presets. Each preset returns keyframes spanning
 * [0, durationMs].
 */
enum class CameraPathPreset(val labelAr: String) {
    ORBIT("مدار حول المركز"),
    PAN_LEFT_RIGHT("تحريك يمين ويسار"),
    ZOOM_IN_OUT("تقريب وبعد"),
    SWEEP("تمرير عرضي"),
    CIRCLE("دائرة كاملة")
}

internal fun buildCameraPathPreset(
    preset: CameraPathPreset,
    durationMs: Long
): List<StudioCameraKeyframe> {
    val d = durationMs.coerceAtLeast(1000L)
    val half = d / 2
    fun at(fraction: Float, x: Float, y: Float, z: Float, lx: Float = 0f, ly: Float = 0f, lz: Float = 0f) =
        StudioCameraKeyframe(
            timeMs = (d * fraction).toLong(),
            position = Position(x, y, z),
            lookAt = Position(lx, ly, lz)
        )
    return when (preset) {
        CameraPathPreset.ORBIT -> listOf(
            at(0f, 2.6f, 1.6f, 4.0f),
            at(0.25f, 4.0f, 1.6f, 2.6f),
            at(0.5f, 2.6f, 1.6f, -4.0f),
            at(0.75f, -4.0f, 1.6f, 2.6f),
            at(1f, 2.6f, 1.6f, 4.0f)
        )
        CameraPathPreset.PAN_LEFT_RIGHT -> listOf(
            at(0f, -4.0f, 0.5f, 0f),
            at(0.5f, 4.0f, 0.5f, 0f),
            at(1f, -4.0f, 0.5f, 0f)
        )
        CameraPathPreset.ZOOM_IN_OUT -> listOf(
            at(0f, 0f, 0f, 5.5f),
            at(0.4f, 0f, 0f, 2.2f),
            at(0.6f, 0f, 0f, 2.2f),
            at(1f, 0f, 0f, 5.5f)
        )
        CameraPathPreset.SWEEP -> listOf(
            at(0f, -4.3f, 1.2f, 0f),
            at(0.5f, 0f, 3.2f, -4.3f),
            at(1f, 4.3f, 1.2f, 0f)
        )
        CameraPathPreset.CIRCLE -> listOf(
            at(0f, 4.3f, 0.2f, 0f),
            at(0.25f, 0f, 0.2f, 4.3f),
            at(0.5f, -4.3f, 0.2f, 0f),
            at(0.75f, 0f, 0.2f, -4.3f),
            at(1f, 4.3f, 0.2f, 0f)
        )
    }.sortedBy { it.timeMs }
}

/**
 * Samples a sorted camera keyframe list at [timeMs]. Returns null when empty.
 */
internal fun sampleCameraPath(
    keyframes: List<StudioCameraKeyframe>,
    timeMs: Long
): StudioCameraKeyframe? {
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
            return start.lerp(end, raw)
        }
    }
    return sorted.last()
}
