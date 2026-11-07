package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiStickerException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class GeminiStickerClient {

    suspend fun generateImages(
        apiKey: String,
        prompt: String,
        count: Int,
        aspectRatio: String,
        transparent: Boolean,
        inputImage: Bitmap? = null
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            throw GeminiStickerException("مفتاح Gemini غير موجود. أضف المفتاح من شاشة الإعدادات.")
        }
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            throw GeminiStickerException("الرجاء كتابة وصف للملصق قبل التوليد.")
        }

        val targetCount = count.coerceIn(1, 4)
        val ratio = aspectRatio.trim()
        val requestPrompt = if (ratio.isBlank()) {
            cleanPrompt
        } else {
            "$cleanPrompt\nAspect ratio: $ratio."
        }

        val results = mutableListOf<Bitmap>()
        var lastError: Exception? = null

        for (index in 0 until targetCount) {
            if (results.size >= targetCount) break
            val remaining = targetCount - results.size
            try {
                val bitmaps = requestOnce(key, requestPrompt, remaining, inputImage)
                bitmaps.forEach { bitmap ->
                    if (results.size < targetCount) {
                        results += if (transparent) applyTransparency(bitmap) else toArgb(bitmap)
                    }
                }
            } catch (error: GeminiStickerException) {
                lastError = error
                if (results.isEmpty()) throw error else break
            } catch (error: Exception) {
                lastError = error
                if (results.isEmpty()) {
                    throw GeminiStickerException(
                        "تعذر الاتصال بخدمة Gemini. تحقق من اتصالك بالإنترنت وحاول مجددًا.",
                        error
                    )
                }
                break
            }
        }

        if (results.isEmpty()) {
            val failure = lastError
            if (failure is GeminiStickerException) throw failure
            throw GeminiStickerException(
                "لم تُرجع خدمة Gemini أي صورة. جرّب وصفًا مختلفًا أو أعد المحاولة."
            )
        }

        results
    }

    private fun requestOnce(
        apiKey: String,
        prompt: String,
        maxImages: Int,
        inputImage: Bitmap?
    ): List<Bitmap> {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (inputImage != null && inputImage.width > 0 && inputImage.height > 0) {
            val encodedImage = encodeBitmap(inputImage)
            if (encodedImage.isNotBlank()) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", encodedImage)
                    )
                )
            }
        }
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("TEXT").put("IMAGE")
                )
            )
        }.toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60_000
            readTimeout = 180_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (responseCode !in 200..299) {
                val detail = parseErrorMessage(responseText)
                throw GeminiStickerException(
                    "فشل الاتصال بخدمة Gemini (رمز $responseCode). " +
                        detail.ifBlank {
                            "تحقق من صحة المفتاح والاتصال بالإنترنت."
                        }
                )
            }

            val bitmaps = parseImages(responseText)
            if (bitmaps.isEmpty()) {
                throw GeminiStickerException(
                    "لم تُرجع خدمة Gemini أي صورة. جرّب وصفًا مختلفًا أو أعد المحاولة."
                )
            }
            return bitmaps.take(maxImages)
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeBitmap(bitmap: Bitmap): String = try {
        val bytes = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            stream.toByteArray()
        }
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
        ""
    }

    private fun parseErrorMessage(responseText: String): String {
        return try {
            val json = JSONObject(responseText)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseImages(responseText: String): List<Bitmap> {
        val images = mutableListOf<Bitmap>()
        try {
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            for (candidateIndex in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(candidateIndex) ?: continue
                val parts = candidate
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: continue
                for (partIndex in 0 until parts.length()) {
                    val part = parts.optJSONObject(partIndex) ?: continue
                    val inlineData = part.optJSONObject("inlineData")
                        ?: part.optJSONObject("inline_data")
                        ?: continue
                    val encoded = inlineData.optString("data")
                    if (encoded.isBlank()) continue
                    val bytes = try {
                        Base64.decode(encoded, Base64.DEFAULT)
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    val bitmap = try {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    images += bitmap
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return images
    }

    private fun toArgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val converted = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        Canvas(converted).drawBitmap(bitmap, 0f, 0f, null)
        return converted
    }

    private fun applyTransparency(source: Bitmap): Bitmap {
        val bitmap = toArgb(source)
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val width = output.width
        val height = output.height
        if (width <= 0 || height <= 0) return output

        val total = width * height
        val pixels = IntArray(total)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        fun isNearWhite(pixel: Int): Boolean {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 12) return false
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val maxChannel = maxOf(red, green, blue)
            val minChannel = minOf(red, green, blue)
            return red >= 236 &&
                green >= 236 &&
                blue >= 236 &&
                maxChannel - minChannel <= 16
        }

        fun push(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            val index = y * width + x
            if (visited[index] || !isNearWhite(pixels[index])) return
            visited[index] = true
            queue[tail] = index
            tail += 1
        }

        for (x in 0 until width) {
            push(x, 0)
            push(x, height - 1)
        }
        for (y in 0 until height) {
            push(0, y)
            push(width - 1, y)
        }

        while (head < tail) {
            val index = queue[head]
            head += 1
            val x = index % width
            val y = index / width
            pixels[index] = pixels[index] and 0x00FFFFFF
            push(x - 1, y)
            push(x + 1, y)
            push(x, y - 1)
            push(x, y + 1)
        }

        val removed = tail
        if (removed == 0 || removed < total / 50 || removed > total * 3 / 4) {
            return bitmap
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
