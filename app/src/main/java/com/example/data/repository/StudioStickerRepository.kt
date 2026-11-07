package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.core.model.StudioSticker
import com.example.core.model.StudioStickerType
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Loads the offline sticker catalog bundled inside the app.
 *
 * Catalog file: src/main/assets/stickers/catalog.json
 *
 * The actual image/model asset files must be placed under
 * src/main/assets/stickers/ and bundled with the APK (works fully offline).
 */
class StudioStickerRepository(private val context: Context) {

    private val catalog by lazy { loadCatalog() }

    val allStickers: List<StudioSticker>
        get() = catalog

    fun stickersByType(type: StudioStickerType): List<StudioSticker> =
        catalog.filter { it.type == type }

    fun stickersByCategory(category: String): List<StudioSticker> =
        catalog.filter { it.category.equals(category, ignoreCase = true) }

    fun categories(): List<String> =
        catalog.map { it.category }.distinct().sorted()

    fun stickerById(id: String): StudioSticker? =
        catalog.firstOrNull { it.id == id }

    /** Reads a bundled asset as a Bitmap (for normal sticker previews). Returns null if missing. */
    fun loadStickerBitmap(assetPath: String): Bitmap? =
        openAsset(assetPath).use { input ->
            if (input == null) return@use null
            BitmapFactory.decodeStream(input)
        }

    /** Returns true if the bundled asset exists. */
    fun assetExists(assetPath: String): Boolean =
        runCatching {
            context.assets.open(assetPath).use { Unit }
            true
        }.getOrDefault(false)

    private fun openAsset(path: String): InputStream? =
        runCatching { context.assets.open(path) }.getOrNull()

    private fun loadCatalog(): List<StudioSticker> {
        return try {
            val stream = context.assets.open("stickers/catalog.json")
            stream.use { input ->
                val json = input.readBytes().toString(StandardCharsets.UTF_8)
                parseCatalog(json)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCatalog(json: String): List<StudioSticker> {
        val result = mutableListOf<StudioSticker>()
        try {
            val root = org.json.JSONObject(json)
            val array = root.optJSONArray("stickers") ?: org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { "sticker_$i" }
                val name = obj.optString("name").ifBlank { id }
                val typeName = obj.optString("type", "NORMAL_IMAGE")
                val type = when (typeName) {
                    "MODEL_3D" -> StudioStickerType.MODEL_3D
                    else -> StudioStickerType.NORMAL_IMAGE
                }
                val assetPath = obj.optString("assetPath").ifBlank { continue }
                val previewAssetPath = obj.optString("previewAssetPath").takeIf { it.isNotBlank() }
                val category = obj.optString("category").ifBlank { "عام" }
                val tags = obj.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it) }
                } ?: emptyList()
                result += StudioSticker(
                    id = id,
                    name = name,
                    type = type,
                    assetPath = assetPath,
                    previewAssetPath = previewAssetPath,
                    category = category,
                    tags = tags
                )
            }
        } catch (_: Exception) {
            // ignore malformed catalog
        }
        return result
    }
}
