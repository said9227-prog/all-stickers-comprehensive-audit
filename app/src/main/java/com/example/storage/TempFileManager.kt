package com.example.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class TempFileManager(private val context: Context) {

    private val tempDir: File
        get() {
            val dir = File(context.cacheDir, "sticker_temp")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val stickersDir: File
        get() {
            val dir = File(context.filesDir, "stickers")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun createTempFile(prefix: String = "conv_", suffix: String = ".tmp"): File {
        return File.createTempFile(prefix, suffix, tempDir)
    }

    fun createTempDirectory(prefix: String = "batch_"): File {
        val dir = File(tempDir, "${prefix}${UUID.randomUUID()}")
        dir.mkdirs()
        return dir
    }

    fun getPackDirectory(packId: Long): File {
        val dir = File(stickersDir, "pack_$packId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPackOriginalsDirectory(packId: Long): File {
        val dir = File(getPackDirectory(packId), "originals")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPackConvertedDirectory(packId: Long): File {
        val dir = File(getPackDirectory(packId), "converted")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /**
     * Moves or copies a file to the managed permanent stickers storage,
     * ensuring it is not stored in a volatile cache directory.
     */
    fun saveToPackOriginals(packId: Long, sourceFile: File, preferredName: String): Pair<File, Uri> {
        val originalsDir = getPackOriginalsDirectory(packId)
        val cleanName = preferredName.replace("[^\\w.\\-]".toRegex(), "_")
        var destFile = File(originalsDir, cleanName)
        var count = 1
        while (destFile.exists()) {
            val nameWithoutExt = cleanName.substringBeforeLast('.')
            val ext = cleanName.substringAfterLast('.', "")
            val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_$count.$ext" else "${nameWithoutExt}_$count"
            destFile = File(originalsDir, newName)
            count++
        }

        sourceFile.copyTo(destFile, overwrite = true)

        if (!destFile.exists() || destFile.length() == 0L) {
            throw java.io.IOException("Failed to save sticker to managed storage: ${destFile.absolutePath}")
        }

        val uri = getFileProviderUri(destFile)
        return Pair(destFile, uri)
    }

    /**
     * Saves a stream from SAF to the managed permanent stickers storage.
     */
    fun saveStreamToPackOriginals(packId: Long, inputStream: InputStream, preferredName: String): Pair<File, Uri> {
        val originalsDir = getPackOriginalsDirectory(packId)
        val cleanName = preferredName.replace("[^\\w.\\-]".toRegex(), "_")
        var destFile = File(originalsDir, cleanName)
        var count = 1
        while (destFile.exists()) {
            val nameWithoutExt = cleanName.substringBeforeLast('.')
            val ext = cleanName.substringAfterLast('.', "")
            val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_$count.$ext" else "${nameWithoutExt}_$count"
            destFile = File(originalsDir, newName)
            count++
        }

        FileOutputStream(destFile).use { out ->
            inputStream.copyTo(out)
        }

        if (!destFile.exists() || destFile.length() == 0L) {
            throw java.io.IOException("Failed to save stream to managed storage: ${destFile.absolutePath}")
        }

        val uri = getFileProviderUri(destFile)
        return Pair(destFile, uri)
    }

    fun cleanTempDirectory() {
        try {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                tempDir.mkdirs()
            }
        } catch (_: Exception) {
            // Ignore temporary deletion failure
        }
    }

    fun deletePackFiles(packId: Long) {
        try {
            val dir = File(stickersDir, "pack_$packId")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }
}
