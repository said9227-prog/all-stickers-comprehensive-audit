package com.example.creator

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads bundled Google Fonts and optional on-demand fonts from the official
 * Google Fonts GitHub repository. Downloaded fonts live in filesDir/fonts.
 */
object FontLibrary {

    private val cache = mutableMapOf<String, Typeface>()
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun typeface(id: String?, fallback: Typeface): Typeface {
        if (id.isNullOrBlank()) return fallback
        synchronized(cache) {
            cache[id]?.let { return it }
        }
        val font = findStickerFont(id) ?: return fallback
        val context = applicationContext ?: return fallback
        val loaded = try {
            when {
                font.assetPath != null -> {
                    Typeface.createFromAsset(context.assets, font.assetPath)
                }
                downloadedFile(context, font).exists() -> {
                    Typeface.createFromFile(downloadedFile(context, font))
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (loaded != null) {
            synchronized(cache) { cache[id] = loaded }
            return loaded
        }
        return fallback
    }

    fun isAvailable(context: Context, font: StickerFont): Boolean {
        return try {
            when {
                font.assetPath != null -> true
                else -> downloadedFile(context, font).let { it.exists() && it.length() > 1024L }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun downloadedFile(context: Context, font: StickerFont): File {
        return File(File(context.filesDir, "fonts"), "${font.id}.ttf")
    }

    suspend fun download(context: Context, font: StickerFont): Result<File> {
        val url = font.downloadUrl
            ?: return Result.failure(IllegalStateException("هذا الخط مضمّن بالفعل"))
        return withContext(Dispatchers.IO) {
            try {
                val destination = downloadedFile(context, font)
                destination.parentFile?.mkdirs()
                val temporary = File(destination.parentFile, "${destination.name}.tmp")
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                try {
                    val response = connection.responseCode
                    if (response !in 200..299) {
                        throw IllegalStateException("تعذر تنزيل الخط (HTTP $response)")
                    }
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (!temporary.exists() || temporary.length() < 1024L) {
                    temporary.delete()
                    throw IllegalStateException("ملف الخط غير صالح")
                }
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                synchronized(cache) { cache.remove(font.id) }
                Result.success(destination)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
