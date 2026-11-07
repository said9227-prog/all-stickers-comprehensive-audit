package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.core.model.StudioImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class StudioRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "studio")
    private val imagesDir = File(rootDir, "images")
    private val indexFile = File(rootDir, "studio.json")
    private val _images = MutableStateFlow<List<StudioImage>>(emptyList())
    val images: StateFlow<List<StudioImage>> = _images.asStateFlow()

    init {
        rootDir.mkdirs()
        imagesDir.mkdirs()
        load()
    }

    @Synchronized
    fun addFromUri(uri: Uri, preferredName: String? = null): StudioImage? {
        val id = UUID.randomUUID().toString()
        val extension = extensionFor(uri, preferredName)
        val destination = File(imagesDir, "${id}_original.$extension")
        val copied = try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            destination.exists() && destination.length() > 0L
        } catch (_: Exception) {
            false
        }
        if (!copied) {
            destination.delete()
            return null
        }
        val now = System.currentTimeMillis()
        val image = StudioImage(
            id = id,
            name = displayName(preferredName, _images.value.size + 1),
            originalPath = destination.absolutePath,
            createdAt = now,
            updatedAt = now
        )
        _images.value = (_images.value + image).sortedByDescending { it.createdAt }
        persist()
        return image
    }

    @Synchronized
    fun createFromBitmap(name: String, bitmap: Bitmap): StudioImage? {
        val id = UUID.randomUUID().toString()
        val destination = File(imagesDir, "${id}_text.png")
        val saved = saveBitmap(bitmap, destination)
        if (!saved) {
            destination.delete()
            return null
        }
        val now = System.currentTimeMillis()
        val image = StudioImage(
            id = id,
            name = displayName(name, _images.value.size + 1),
            originalPath = destination.absolutePath,
            editedPath = destination.absolutePath,
            isText = true,
            createdAt = now,
            updatedAt = now
        )
        _images.value = (_images.value + image).sortedByDescending { it.createdAt }
        persist()
        return image
    }

    @Synchronized
    fun saveEdited(id: String, name: String?, bitmap: Bitmap): StudioImage? {
        val existing = _images.value.firstOrNull { it.id == id } ?: return null
        val destination = File(imagesDir, "${id}_edited.png")
        val saved = saveBitmap(bitmap, destination)
        if (!saved) {
            destination.delete()
            return null
        }
        val updated = existing.copy(
            name = name?.trim()?.takeIf { it.isNotBlank() } ?: existing.name,
            editedPath = destination.absolutePath,
            updatedAt = System.currentTimeMillis()
        )
        _images.value = _images.value
            .map { if (it.id == id) updated else it }
            .sortedByDescending { it.createdAt }
        persist()
        return updated
    }

    @Synchronized
    fun delete(id: String) {
        val image = _images.value.firstOrNull { it.id == id } ?: return
        File(image.originalPath).delete()
        image.editedPath?.let { edited ->
            if (edited != image.originalPath) File(edited).delete()
        }
        _images.value = _images.value.filterNot { it.id == id }
        persist()
    }

    fun uriFor(image: StudioImage): String {
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            File(image.displayPath)
        ).toString()
    }

    private fun load() {
        val loaded = try {
            if (!indexFile.exists()) return
            val array = JSONArray(indexFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { continue }
                    val originalPath = item.optString("originalPath").ifBlank { continue }
                    if (!File(originalPath).exists()) continue
                    val editedPath = item.optString("editedPath").takeIf {
                        it.isNotBlank() && File(it).exists()
                    }
                    add(
                        StudioImage(
                            id = id,
                            name = item.optString("name").ifBlank { "صورة" },
                            originalPath = originalPath,
                            editedPath = editedPath,
                            isText = item.optBoolean("isText", false),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
        _images.value = loaded
    }

    private fun persist() {
        try {
            rootDir.mkdirs()
            val array = JSONArray()
            _images.value.forEach { image ->
                array.put(
                    JSONObject().apply {
                        put("id", image.id)
                        put("name", image.name)
                        put("originalPath", image.originalPath)
                        image.editedPath?.let { put("editedPath", it) }
                        put("isText", image.isText)
                        put("createdAt", image.createdAt)
                        put("updatedAt", image.updatedAt)
                    }
                )
            }
            val temporary = File(rootDir, "studio.json.tmp")
            temporary.writeText(array.toString(2))
            if (!temporary.renameTo(indexFile)) {
                indexFile.writeText(temporary.readText())
                temporary.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun saveBitmap(bitmap: Bitmap, destination: File): Boolean {
        return try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            destination.exists() && destination.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun extensionFor(uri: Uri, preferredName: String?): String {
        val fromMime = when (appContext.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/heic", "image/heif" -> "heic"
            else -> null
        }
        if (fromMime != null) return fromMime
        val fromName = preferredName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in setOf("png", "webp", "jpg", "jpeg", "heic", "heif") }
        return when (fromName) {
            "jpeg" -> "jpg"
            "heif" -> "heic"
            null -> "jpg"
            else -> fromName
        }
    }

    private fun displayName(preferredName: String?, fallbackIndex: Int): String {
        val clean = preferredName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
        return clean ?: "صورة $fallbackIndex"
    }
}
