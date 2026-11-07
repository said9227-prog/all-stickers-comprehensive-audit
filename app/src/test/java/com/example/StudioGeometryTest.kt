package com.example

import com.example.presentation.screens.AnimationEasing
import com.example.presentation.screens.CameraPathPreset
import com.example.presentation.screens.StudioCameraKeyframe
import com.example.presentation.screens.buildCameraPathPreset
import com.example.presentation.screens.sampleCameraPath
import com.example.presentation.screens.StudioAnimationPreset
import com.example.presentation.screens.StudioKeyframe
import com.example.presentation.screens.applyEasing
import com.example.presentation.screens.buildPresetKeyframes
import com.example.presentation.screens.extrusionPlaneCount
import com.example.presentation.screens.sampleKeyframes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.sceneview.math.Position

class StudioGeometryTest {

    @Test
    fun `linear easing is identity`() {
        assertEquals(0f, applyEasing(AnimationEasing.LINEAR, 0f), 0.0001f)
        assertEquals(0.5f, applyEasing(AnimationEasing.LINEAR, 0.5f), 0.0001f)
        assertEquals(1f, applyEasing(AnimationEasing.LINEAR, 1f), 0.0001f)
    }

    @Test
    fun `ease in and ease out differ at midpoint`() {
        val easeIn = applyEasing(AnimationEasing.EASE_IN, 0.5f)
        val easeOut = applyEasing(AnimationEasing.EASE_OUT, 0.5f)
        assertEquals(0.25f, easeIn, 0.0001f)
        assertEquals(0.75f, easeOut, 0.0001f)
        assertEquals(0.5f, applyEasing(AnimationEasing.EASE_IN_OUT, 0.5f), 0.0001f)
        assertEquals(0.5f, applyEasing(AnimationEasing.SMOOTH, 0.5f), 0.0001f)
    }

    @Test
    fun `easing clamps out of range input`() {
        assertEquals(0f, applyEasing(AnimationEasing.LINEAR, -3f), 0.0001f)
        assertEquals(1f, applyEasing(AnimationEasing.SMOOTH, 9f), 0.0001f)
    }

    @Test
    fun `sample returns null when there are no keyframes`() {
        assertNull(sampleKeyframes(emptyList(), 100L, AnimationEasing.LINEAR))
    }

    @Test
    fun `sample interpolates linearly between two keyframes`() {
        val start = StudioKeyframe(timeMs = 0L, x = 0f, scale = 1f)
        val end = StudioKeyframe(timeMs = 1000L, x = 10f, scale = 3f)
        val pose = sampleKeyframes(listOf(start, end), 500L, AnimationEasing.LINEAR)
        assertNotNull(pose)
        assertEquals(5f, pose!!.x, 0.01f)
        assertEquals(2f, pose.scale, 0.01f)
        assertEquals(500L, pose.timeMs)
    }

    @Test
    fun `sample clamps before first and after last keyframe`() {
        val start = StudioKeyframe(timeMs = 200L, x = 4f)
        val end = StudioKeyframe(timeMs = 800L, x = 8f)
        val before = sampleKeyframes(listOf(start, end), 0L, AnimationEasing.LINEAR)!!
        val after = sampleKeyframes(listOf(start, end), 5000L, AnimationEasing.LINEAR)!!
        assertEquals(4f, before.x, 0.01f)
        assertEquals(8f, after.x, 0.01f)
    }

    @Test
    fun `sample works with unsorted input`() {
        val end = StudioKeyframe(timeMs = 1000L, x = 10f)
        val start = StudioKeyframe(timeMs = 0L, x = 0f)
        val pose = sampleKeyframes(listOf(end, start), 250L, AnimationEasing.LINEAR)!!
        assertEquals(2.5f, pose.x, 0.01f)
    }

    @Test
    fun `presets produce sorted tracks that start at zero`() {
        val base = StudioKeyframe(timeMs = 0L, x = 1f, y = 2f, scale = 1f)
        StudioAnimationPreset.entries.forEach { preset ->
            val track = buildPresetKeyframes(preset, base, 5000L)
            assertTrue("$preset needs at least two keyframes", track.size >= 2)
            assertEquals("$preset starts at 0ms", 0L, track.first().timeMs)
            assertEquals("$preset ends at duration", 5000L, track.last().timeMs)
            for (index in 1 until track.size) {
                assertTrue(
                    "$preset keyframes must be sorted",
                    track[index].timeMs >= track[index - 1].timeMs
                )
            }
        }
    }

    @Test
    fun `rotate 360 preset turns a full revolution`() {
        val track = buildPresetKeyframes(
            StudioAnimationPreset.ROTATE_360,
            StudioKeyframe(timeMs = 0L),
            4000L
        )
        assertEquals(360f, track.last().rotationY, 0.01f)
    }

    @Test
    fun `floating preset rises then returns to start`() {
        val track = buildPresetKeyframes(
            StudioAnimationPreset.FLOATING,
            StudioKeyframe(timeMs = 0L, y = 3f),
            5000L
        )
        assertEquals(3, track.size)
        assertTrue(track[1].y > track[0].y)
        assertEquals(track[0].y, track[2].y, 0.0001f)
    }

    @Test
    fun `extrusion plane count is clamped to the supported range`() {
        assertEquals(1, extrusionPlaneCount(0f))
        assertEquals(9, extrusionPlaneCount(1f))
        assertEquals(5, extrusionPlaneCount(0.5f))
        assertEquals(1, extrusionPlaneCount(-1f))
        assertEquals(9, extrusionPlaneCount(42f))
    }

    @Test
    fun `camera presets are sorted and span the whole duration`() {
        CameraPathPreset.entries.forEach { preset ->
            val track = buildCameraPathPreset(preset, 4000L)
            assertTrue("$preset needs keyframes", track.size >= 3)
            assertEquals(0L, track.first().timeMs)
            assertEquals(4000L, track.last().timeMs)
            for (index in 1 until track.size) {
                assertTrue(track[index].timeMs >= track[index - 1].timeMs)
            }
        }
    }

    @Test
    fun `camera path interpolation is clamped and smooth`() {
        val start = StudioCameraKeyframe(0L, Position(0f, 0f, 4f))
        val end = StudioCameraKeyframe(1000L, Position(4f, 0f, 0f))
        val middle = sampleCameraPath(listOf(start, end), 500L)!!
        assertEquals(2f, middle.position.x, 0.01f)
        assertEquals(2f, middle.position.z, 0.01f)
        assertEquals(0f, sampleCameraPath(listOf(start, end), -50L)!!.position.x, 0f)
        assertEquals(4f, sampleCameraPath(listOf(start, end), 9000L)!!.position.x, 0f)
    }

    @Test
    fun `preset with tiny duration still produces a valid track`() {
        val track = buildPresetKeyframes(
            StudioAnimationPreset.BOUNCE,
            StudioKeyframe(timeMs = 0L),
            10L
        )
        assertTrue(track.size >= 2)
        assertTrue(track.last().timeMs >= track.first().timeMs)
    }
}
