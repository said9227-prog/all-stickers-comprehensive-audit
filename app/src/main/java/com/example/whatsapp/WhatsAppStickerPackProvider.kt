package com.example.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import java.io.File
import java.io.FileNotFoundException

class WhatsAppStickerPackProvider : ContentProvider() {

    private lateinit var authority: String
    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val appContext = context ?: return false
        authority = WhatsAppExporter.authority(appContext)
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, METADATA, METADATA_CODE)
            addURI(authority, "$METADATA/*", METADATA_SINGLE_CODE)
            addURI(authority, "$STICKERS/*", STICKERS_CODE)
            addURI(authority, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)
        }
        DiagnosticsRepository.logInfo(
            operation = "WHATSAPP_PROVIDER",
            stage = DiagnosticStage.EXPORT,
            fileName = "provider",
            message = "Provider ready: authority=$authority، package=${appContext.packageName}"
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val appContext = context ?: return null
        val result = when (matcher.match(uri)) {
            METADATA_CODE -> metadataCursor(appContext, null, uri)
            METADATA_SINGLE_CODE -> metadataCursor(appContext, uri.lastPathSegment, uri)
            STICKERS_CODE -> stickersCursor(appContext, uri.lastPathSegment, uri)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        DiagnosticsRepository.logInfo(
            operation = "WHATSAPP_PROVIDER",
            stage = DiagnosticStage.EXPORT,
            fileName = uri.lastPathSegment ?: "metadata",
            message = "WhatsApp query: $uri، rows=${result.count}"
        )
        return result
    }

    override fun getType(uri: Uri): String? {
        return when (matcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            METADATA_SINGLE_CODE -> "vnd.android.cursor.item/vnd.$authority.metadata"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            STICKERS_ASSET_CODE -> when (
                uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
            ) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != STICKERS_ASSET_CODE) {
            throw FileNotFoundException("Unsupported URI: $uri")
        }
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != STICKERS_ASSET) {
            throw FileNotFoundException("Invalid sticker asset URI: $uri")
        }
        val appContext = context ?: throw FileNotFoundException("Provider unavailable")
        val packId = segments[1]
        val fileName = segments[2]
        if (packId.isBlank() || fileName.isBlank()) {
            throw FileNotFoundException("Invalid sticker asset path")
        }

        val packDir = WhatsAppPackStore.packDir(appContext, packId).canonicalFile
        val file = File(packDir, fileName).canonicalFile
        if (file.parentFile != packDir) {
            throw FileNotFoundException("Invalid sticker asset path")
        }
        val packFile = File(packDir, WhatsAppPackStore.PACK_FILE)
        if (!packFile.exists()) {
            throw FileNotFoundException("Sticker pack not found")
        }
        if (!file.exists() || !file.isFile) {
            throw FileNotFoundException("Sticker asset not found: $fileName")
        }

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        DiagnosticsRepository.logInfo(
            operation = "WHATSAPP_PROVIDER",
            stage = DiagnosticStage.EXPORT,
            fileName = fileName,
            message = "WhatsApp read asset: pack=$packId، file=$fileName، bytes=${file.length()}"
        )
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun metadataCursor(
        appContext: android.content.Context,
        onlyId: String?,
        uri: Uri
    ): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        WhatsAppPackStore.listPacks(appContext).forEach { (_, pack) ->
            if (onlyId != null && pack.identifier != onlyId) return@forEach
            cursor.addRow(
                arrayOf<Any>(
                    pack.identifier,
                    pack.name,
                    pack.publisher,
                    pack.trayImageFile,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    pack.imageDataVersion,
                    0,
                    if (pack.animated) 1 else 0
                )
            )
        }
        cursor.setNotificationUri(appContext.contentResolver, uri)
        return cursor
    }

    private fun stickersCursor(
        appContext: android.content.Context,
        packId: String?,
        uri: Uri
    ): Cursor {
        val cursor = MatrixCursor(STICKER_COLUMNS)
        if (packId.isNullOrBlank()) return cursor
        val pack = WhatsAppPackStore.listPacks(appContext)
            .firstOrNull { it.second.identifier == packId }
            ?.second
            ?: return cursor
        pack.stickers.forEach { sticker ->
            cursor.addRow(
                arrayOf<Any>(
                    sticker.fileName,
                    sticker.emojis.joinToString(","),
                    ""
                )
            )
        }
        cursor.setNotificationUri(appContext.contentResolver, uri)
        return cursor
    }

    companion object {
        private const val METADATA_CODE = 1
        private const val METADATA_SINGLE_CODE = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack"
        )

        private val STICKER_COLUMNS = arrayOf(
            "sticker_file_name",
            "sticker_emoji",
            "sticker_accessibility_text"
        )
    }
}
