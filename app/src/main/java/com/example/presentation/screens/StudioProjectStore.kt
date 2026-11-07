package com.example.presentation.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

internal data class StudioProjectData(
    val frames: List<Text3DFrame>,
    val activeFrameId: Long,
    val nextFrameId: Long,
    val nextLayerId: Long,
    val keyframes: List<StudioKeyframe>,
    val modelKeyframes: List<StudioKeyframe> = emptyList(),
    val cameraKeyframes: List<StudioCameraKeyframe> = emptyList(),
    val durationMs: Long,
    val easing: AnimationEasing,
    val loop: Boolean,
    val reverse: Boolean,
    val speed: Float
)

/**
 * Saves the complete editable studio project (layers, frames, hierarchy,
 * transforms, materials, camera/light settings and keyframes) as JSON plus the
 * image layers as PNG files. Reloading restores an editable project, not just a
 * flat screenshot.
 */
internal object StudioProjectStore {

    private const val VERSION = 1
    private const val PROJECT_FILE = "project.json"

    fun projectDir(context: Context): File =
        File(context.filesDir, "studio_project").apply { mkdirs() }

    private fun imagesDir(context: Context): File =
        File(projectDir(context), "images").apply { mkdirs() }

    fun hasProject(context: Context): Boolean =
        File(projectDir(context), PROJECT_FILE).isFile

    fun save(context: Context, data: StudioProjectData): Boolean {
        return try {
            val dir = projectDir(context)
            val imageDir = imagesDir(context)
            val root = JSONObject()
            root.put("version", VERSION)
            root.put("activeFrameId", data.activeFrameId)
            root.put("nextFrameId", data.nextFrameId)
            root.put("nextLayerId", data.nextLayerId)

            val timeline = JSONObject()
            timeline.put("durationMs", data.durationMs)
            timeline.put("easing", data.easing.name)
            timeline.put("loop", data.loop)
            timeline.put("reverse", data.reverse)
            timeline.put("speed", data.speed.toDouble())
            root.put("timeline", timeline)

            val keyframeArray = JSONArray()
            data.keyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                keyframeArray.put(
                    JSONObject().apply {
                        put("timeMs", keyframe.timeMs)
                        put("x", keyframe.x.toDouble())
                        put("y", keyframe.y.toDouble())
                        put("z", keyframe.z.toDouble())
                        put("rotationX", keyframe.rotationX.toDouble())
                        put("rotationY", keyframe.rotationY.toDouble())
                        put("rotationZ", keyframe.rotationZ.toDouble())
                        put("scale", keyframe.scale.toDouble())
                    }
                )
            }
            root.put("keyframes", keyframeArray)

            val modelKeyframeArray = JSONArray()
            data.modelKeyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                modelKeyframeArray.put(keyframe.toJson())
            }
            root.put("modelKeyframes", modelKeyframeArray)

            val cameraKeyframeArray = JSONArray()
            data.cameraKeyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                cameraKeyframeArray.put(
                    JSONObject().apply {
                        put("timeMs", keyframe.timeMs)
                        put("x", keyframe.position.x.toDouble())
                        put("y", keyframe.position.y.toDouble())
                        put("z", keyframe.position.z.toDouble())
                        put("lx", keyframe.lookAt.x.toDouble())
                        put("ly", keyframe.lookAt.y.toDouble())
                        put("lz", keyframe.lookAt.z.toDouble())
                    }
                )
            }
            root.put("cameraKeyframes", cameraKeyframeArray)

            val framesArray = JSONArray()
            data.frames.forEach { frame ->
                framesArray.put(frameToJson(context, imageDir, frame))
            }
            root.put("frames", framesArray)

            File(dir, PROJECT_FILE).writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun load(context: Context): StudioProjectData? {
        return try {
            val dir = projectDir(context)
            val file = File(dir, PROJECT_FILE)
            if (!file.isFile) return null
            val imageDir = imagesDir(context)
            val root = JSONObject(file.readText())

            val frames = mutableListOf<Text3DFrame>()
            val framesArray = root.optJSONArray("frames") ?: JSONArray()
            for (index in 0 until framesArray.length()) {
                val frame = framesArray.optJSONObject(index) ?: continue
                frames += frameFromJson(imageDir, frame)
            }
            if (frames.isEmpty()) return null

            val keyframes = mutableListOf<StudioKeyframe>()
            val keyframeArray = root.optJSONArray("keyframes") ?: JSONArray()
            for (index in 0 until keyframeArray.length()) {
                val obj = keyframeArray.optJSONObject(index) ?: continue
                keyframes += StudioKeyframe(
                    timeMs = obj.optLong("timeMs"),
                    x = obj.optDouble("x", 0.0).toFloat(),
                    y = obj.optDouble("y", 0.0).toFloat(),
                    z = obj.optDouble("z", 0.0).toFloat(),
                    rotationX = obj.optDouble("rotationX", 0.0).toFloat(),
                    rotationY = obj.optDouble("rotationY", 0.0).toFloat(),
                    rotationZ = obj.optDouble("rotationZ", 0.0).toFloat(),
                    scale = obj.optDouble("scale", 1.0).toFloat()
                )
            }

            val modelKeyframes = mutableListOf<StudioKeyframe>()
            val modelKeyframeArray = root.optJSONArray("modelKeyframes") ?: JSONArray()
            for (index in 0 until modelKeyframeArray.length()) {
                modelKeyframeArray.optJSONObject(index)?.let { modelKeyframes += it.toKeyframe() }
            }

            val cameraKeyframes = mutableListOf<StudioCameraKeyframe>()
            val cameraKeyframeArray = root.optJSONArray("cameraKeyframes") ?: JSONArray()
            for (index in 0 until cameraKeyframeArray.length()) {
                val obj = cameraKeyframeArray.optJSONObject(index) ?: continue
                cameraKeyframes += StudioCameraKeyframe(
                    timeMs = obj.optLong("timeMs"),
                    position = io.github.sceneview.math.Position(
                        obj.optDouble("x", 0.0).toFloat(),
                        obj.optDouble("y", 0.0).toFloat(),
                        obj.optDouble("z", 0.0).toFloat()
                    ),
                    lookAt = io.github.sceneview.math.Position(
                        obj.optDouble("lx", 0.0).toFloat(),
                        obj.optDouble("ly", 0.0).toFloat(),
                        obj.optDouble("lz", 0.0).toFloat()
                    )
                )
            }

            val timeline = root.optJSONObject("timeline") ?: JSONObject()
            StudioProjectData(
                frames = frames,
                activeFrameId = root.optLong("activeFrameId", frames.first().id),
                nextFrameId = root.optLong("nextFrameId", (frames.maxOfOrNull { it.id } ?: 0L) + 1L),
                nextLayerId = root.optLong("nextLayerId", 2L),
                keyframes = keyframes,
                modelKeyframes = modelKeyframes,
                cameraKeyframes = cameraKeyframes,
                durationMs = timeline.optLong("durationMs", 5000L),
                easing = runCatching {
                    AnimationEasing.valueOf(timeline.optString("easing", AnimationEasing.EASE_IN_OUT.name))
                }.getOrDefault(AnimationEasing.EASE_IN_OUT),
                loop = timeline.optBoolean("loop", true),
                reverse = timeline.optBoolean("reverse", false),
                speed = timeline.optDouble("speed", 1.0).toFloat()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun frameToJson(context: Context, imageDir: File, frame: Text3DFrame): JSONObject {
        return JSONObject().apply {
            put("id", frame.id)
            put("name", frame.name)
            put("locked", frame.locked)
            put("visible", frame.visible)
            put("snapshot", snapshotToJson(context, imageDir, frame.id, frame.snapshot))
        }
    }

    private fun frameFromJson(imageDir: File, obj: JSONObject): Text3DFrame {
        val id = obj.optLong("id", 1L)
        val snapshot = obj.optJSONObject("snapshot")?.let { snapshotFromJson(imageDir, id, it) }
            ?: emptyStudioSnapshot()
        return Text3DFrame(
            id = id,
            name = obj.optString("name", "الإطار $id"),
            locked = obj.optBoolean("locked", false),
            visible = obj.optBoolean("visible", true),
            snapshot = snapshot
        )
    }

    private fun snapshotToJson(
        context: Context,
        imageDir: File,
        frameId: Long,
        snapshot: StudioSnapshot
    ): JSONObject {
        return JSONObject().apply {
            put("selectedLayerId", snapshot.selectedLayerId ?: JSONObject.NULL)
            put("metalStyle", snapshot.metalStyle.name)
            put("shapeStyle", snapshot.shapeStyle.name)
            put("autoRotate", snapshot.autoRotate)
            put("manualRotation", snapshot.manualRotation.toDouble())
            put("baseX", snapshot.baseX.toDouble())
            put("baseY", snapshot.baseY.toDouble())
            put("baseZ", snapshot.baseZ.toDouble())
            put("baseRotationX", snapshot.baseRotationX.toDouble())
            put("baseRotationY", snapshot.baseRotationY.toDouble())
            put("baseRotationZ", snapshot.baseRotationZ.toDouble())
            put("baseScale", snapshot.baseScale.toDouble())
            put("moveTogether", snapshot.moveTogether)
            put("unifiedColor", snapshot.unifiedColor)
            put("baseColorOverride", snapshot.baseColorOverride ?: JSONObject.NULL)
            put("lightMode", snapshot.lightMode.name)
            put("lightAngle", snapshot.lightAngle.toDouble())
            put("lightElevation", snapshot.lightElevation.toDouble())
            put("backgroundMode", snapshot.backgroundMode.name)
            put("backgroundColor", snapshot.backgroundColor)
            put("backgroundImage", saveBitmap(imageDir, "bg_$frameId.png", snapshot.backgroundImage) ?: JSONObject.NULL)
            put("watermarkEnabled", snapshot.watermarkEnabled)
            put("watermarkText", snapshot.watermarkText)
            put("watermarkColor", snapshot.watermarkColor)
            put("watermarkOpacity", snapshot.watermarkOpacity.toDouble())

            val layers = JSONArray()
            snapshot.layers.forEach { layer ->
                layers.put(layerToJson(context, imageDir, frameId, layer))
            }
            put("layers", layers)
        }
    }

    private fun snapshotFromJson(imageDir: File, frameId: Long, obj: JSONObject): StudioSnapshot {
        val layers = mutableListOf<Text3DLayer>()
        val layersArray = obj.optJSONArray("layers") ?: JSONArray()
        for (index in 0 until layersArray.length()) {
            val layerObj = layersArray.optJSONObject(index) ?: continue
            layers += layerFromJson(imageDir, frameId, layerObj)
        }
        if (layers.isEmpty()) {
            layers += Text3DLayer(id = 1L)
        }
        return StudioSnapshot(
            layers = layers,
            selectedLayerId = obj.optNullableLong("selectedLayerId"),
            metalStyle = runCatching {
                MetalStyle.valueOf(obj.optString("metalStyle", MetalStyle.GOLD.name))
            }.getOrDefault(MetalStyle.GOLD),
            shapeStyle = runCatching {
                ShapeStyle.valueOf(obj.optString("shapeStyle", ShapeStyle.SLAB.name))
            }.getOrDefault(ShapeStyle.SLAB),
            autoRotate = obj.optBoolean("autoRotate", true),
            manualRotation = obj.optDouble("manualRotation", 0.0).toFloat(),
            baseX = obj.optDouble("baseX", 0.0).toFloat(),
            baseY = obj.optDouble("baseY", 0.0).toFloat(),
            baseZ = obj.optDouble("baseZ", 0.0).toFloat(),
            baseRotationX = obj.optDouble("baseRotationX", 0.0).toFloat(),
            baseRotationY = obj.optDouble("baseRotationY", 0.0).toFloat(),
            baseRotationZ = obj.optDouble("baseRotationZ", 0.0).toFloat(),
            baseScale = obj.optDouble("baseScale", 1.0).toFloat(),
            moveTogether = obj.optBoolean("moveTogether", true),
            unifiedColor = obj.optBoolean("unifiedColor", false),
            baseColorOverride = obj.optNullableInt("baseColorOverride"),
            lightMode = runCatching {
                LightMode.valueOf(obj.optString("lightMode", LightMode.ALL_SIDES.name))
            }.getOrDefault(LightMode.ALL_SIDES),
            lightAngle = obj.optDouble("lightAngle", 45.0).toFloat(),
            lightElevation = obj.optDouble("lightElevation", 35.0).toFloat(),
            backgroundMode = runCatching {
                BackgroundMode.valueOf(obj.optString("backgroundMode", BackgroundMode.TRANSPARENT.name))
            }.getOrDefault(BackgroundMode.TRANSPARENT),
            backgroundColor = obj.optInt("backgroundColor", 0xFF10152E.toInt()),
            backgroundImage = loadBitmap(imageDir, obj.optNullableString("backgroundImage")),
            watermarkEnabled = obj.optBoolean("watermarkEnabled", false),
            watermarkText = obj.optString("watermarkText", "All Stickers"),
            watermarkColor = obj.optInt("watermarkColor", 0xFFFFFFFF.toInt()),
            watermarkOpacity = obj.optDouble("watermarkOpacity", 0.55).toFloat()
        )
    }

    private fun layerToJson(
        context: Context,
        imageDir: File,
        frameId: Long,
        layer: Text3DLayer
    ): JSONObject {
        return JSONObject().apply {
            put("id", layer.id)
            put("text", layer.text)
            put("fontId", layer.fontId)
            put("style", layer.style.name)
            put("color", layer.color ?: JSONObject.NULL)
            put("visible", layer.visible)
            put("locked", layer.locked)
            put("offsetX", layer.offsetX.toDouble())
            put("offsetY", layer.offsetY.toDouble())
            put("offsetZ", layer.offsetZ.toDouble())
            put("rotationX", layer.rotationX.toDouble())
            put("rotationY", layer.rotationY.toDouble())
            put("rotationZ", layer.rotationZ.toDouble())
            put("scale", layer.scale.toDouble())
            put("curve", layer.curve.toDouble())
            put("animation", layer.animation.name)
            put("fontSize", layer.fontSize.toDouble())
            put("letterSpacing", layer.letterSpacing.toDouble())
            put("wordSpacing", layer.wordSpacing.toDouble())
            put("lineSpacing", layer.lineSpacing.toDouble())
            put("alignment", layer.alignment)
            put("outlineWidth", layer.outlineWidth.toDouble())
            put("outlineColor", layer.outlineColor)
            put("extrusion", layer.extrusion.toDouble())
            put("bevel", layer.bevel.toDouble())
            put("opacity", layer.opacity.toDouble())
            put("metallicOverride", layer.metallicOverride.toDouble())
            put("roughnessOverride", layer.roughnessOverride.toDouble())
            put("reflectanceOverride", layer.reflectanceOverride.toDouble())
            put(
                "textureImage",
                saveBitmap(
                    imageDir,
                    "texture_${frameId}_${layer.id}.png",
                    layer.textureBitmap
                ) ?: JSONObject.NULL
            )
            put(
                "imageLayer",
                saveBitmap(
                    imageDir,
                    "image_${frameId}_${layer.id}.png",
                    layer.imageBitmap
                ) ?: JSONObject.NULL
            )
        }
    }

    private fun layerFromJson(imageDir: File, frameId: Long, obj: JSONObject): Text3DLayer {
        return Text3DLayer(
            id = obj.optLong("id", 1L),
            text = obj.optString("text", "ملصق 3D"),
            fontId = obj.optString("fontId", "cairo"),
            style = runCatching {
                MetalStyle.valueOf(obj.optString("style", MetalStyle.GOLD.name))
            }.getOrDefault(MetalStyle.GOLD),
            color = obj.optNullableInt("color"),
            visible = obj.optBoolean("visible", true),
            locked = obj.optBoolean("locked", false),
            offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
            offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
            offsetZ = obj.optDouble("offsetZ", 0.0).toFloat(),
            rotationX = obj.optDouble("rotationX", 0.0).toFloat(),
            rotationY = obj.optDouble("rotationY", 0.0).toFloat(),
            rotationZ = obj.optDouble("rotationZ", 0.0).toFloat(),
            scale = obj.optDouble("scale", 1.0).toFloat(),
            curve = obj.optDouble("curve", 0.0).toFloat(),
            animation = runCatching {
                LayerAnimation.valueOf(obj.optString("animation", LayerAnimation.NONE.name))
            }.getOrDefault(LayerAnimation.NONE),
            textureBitmap = loadBitmap(imageDir, obj.optNullableString("textureImage")),
            imageBitmap = loadBitmap(imageDir, obj.optNullableString("imageLayer")),
            fontSize = obj.optDouble("fontSize", 100.0).toFloat(),
            letterSpacing = obj.optDouble("letterSpacing", 0.0).toFloat(),
            wordSpacing = obj.optDouble("wordSpacing", 0.0).toFloat(),
            lineSpacing = obj.optDouble("lineSpacing", 1.0).toFloat(),
            alignment = obj.optInt("alignment", 1),
            outlineWidth = obj.optDouble("outlineWidth", 0.0).toFloat(),
            outlineColor = obj.optInt("outlineColor", 0xFF000000.toInt()),
            extrusion = obj.optDouble("extrusion", 1.0).toFloat(),
            bevel = obj.optDouble("bevel", 0.0).toFloat(),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            metallicOverride = obj.optDouble("metallicOverride", -1.0).toFloat(),
            roughnessOverride = obj.optDouble("roughnessOverride", -1.0).toFloat(),
            reflectanceOverride = obj.optDouble("reflectanceOverride", -1.0).toFloat()
        )
    }

    private fun saveBitmap(dir: File, name: String, bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            val file = File(dir, name)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            name
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBitmap(dir: File, name: String?): Bitmap? {
        if (name.isNullOrBlank()) return null
        return try {
            val file = File(dir, name)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
    }

    private fun StudioKeyframe.toJson(): JSONObject = JSONObject().apply {
        put("timeMs", timeMs)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("z", z.toDouble())
        put("rotationX", rotationX.toDouble())
        put("rotationY", rotationY.toDouble())
        put("rotationZ", rotationZ.toDouble())
        put("scale", scale.toDouble())
    }

    private fun JSONObject.toKeyframe(): StudioKeyframe = StudioKeyframe(
        timeMs = optLong("timeMs"),
        x = optDouble("x", 0.0).toFloat(),
        y = optDouble("y", 0.0).toFloat(),
        z = optDouble("z", 0.0).toFloat(),
        rotationX = optDouble("rotationX", 0.0).toFloat(),
        rotationY = optDouble("rotationY", 0.0).toFloat(),
        rotationZ = optDouble("rotationZ", 0.0).toFloat(),
        scale = optDouble("scale", 1.0).toFloat()
    )
}
