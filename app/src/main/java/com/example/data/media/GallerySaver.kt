package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

internal object GallerySaver {
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val safeName = displayName.ifBlank { "all_stickers_${System.currentTimeMillis()}" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safeName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + FileFolder
            )
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return try {
            val uri = context.contentResolver.insert(collection, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    private const val FileFolder = "/all_stickers"
}
