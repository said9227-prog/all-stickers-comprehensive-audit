package com.example.whatsapp

import android.content.Context
import org.json.JSONObject
import java.io.File

object WhatsAppPackStore {
    private const val ROOT_DIR = "whatsapp/packs"
    const val PACK_FILE = "pack.json"

    fun root(context: Context): File {
        return File(context.filesDir, ROOT_DIR).apply { mkdirs() }
    }

    fun packDir(context: Context, packId: String): File {
        return File(root(context), packId)
    }

    fun listPacks(context: Context): List<Pair<File, WhatsAppStickerPackModel>> {
        val root = root(context)
        val dirs = root.listFiles { file -> file.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            if (dir.name.endsWith(".tmp")) return@mapNotNull null
            val file = File(dir, PACK_FILE)
            if (!file.exists()) return@mapNotNull null
            readPack(file)?.let { dir to it }
        }
    }

    fun readPack(file: File): WhatsAppStickerPackModel? {
        return try {
            val root = JSONObject(file.readText())
            val identifier = root.optString("identifier").ifBlank { return null }
            val stickersJson = root.optJSONArray("stickers") ?: return null
            val stickers = mutableListOf<WhatsAppStickerAsset>()
            for (index in 0 until stickersJson.length()) {
                val item = stickersJson.optJSONObject(index) ?: continue
                val fileName = item.optString("image_file")
                if (fileName.isBlank()) continue
                val emojiJson = item.optJSONArray("emojis")
                val emojis = mutableListOf<String>()
                if (emojiJson != null) {
                    for (emojiIndex in 0 until emojiJson.length()) {
                        emojis.add(emojiJson.optString(emojiIndex, "✨"))
                    }
                }
                if (emojis.isEmpty()) emojis.add("✨")
                stickers.add(
                    WhatsAppStickerAsset(
                        id = fileName,
                        fileName = fileName,
                        emojis = emojis
                    )
                )
            }
            WhatsAppStickerPackModel(
                identifier = identifier,
                name = root.optString("name").ifBlank { "ملصقاتي" },
                publisher = root.optString("publisher").ifBlank { "Sticker Studio" },
                trayImageFile = root.optString("tray_image_file").ifBlank { "tray.png" },
                imageDataVersion = root.optString("image_data_version", "1").ifBlank { "1" },
                animated = root.optBoolean("animated_sticker_pack", false),
                stickers = stickers
            )
        } catch (_: Exception) {
            null
        }
    }
}
