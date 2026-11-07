package com.example.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticEvent(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: DiagnosticLevel = DiagnosticLevel.ERROR,
    val operation: String = "MEDIA_OPERATION",
    val stage: DiagnosticStage = DiagnosticStage.PREVIEW,
    val fileName: String = "",
    val fileExtension: String = "",
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val fileExists: Boolean = false,
    val isFile: Boolean = false,
    val isReadable: Boolean = false,
    val absolutePath: String = "",
    val isStoragePermanent: Boolean = false,
    val uri: String = "",
    val uriScheme: String = "",
    val uriAuthority: String = "",
    val uriPath: String = "",
    val contentResolverCanOpen: Boolean = false,
    val contentResolverError: String? = null,
    val isAnimatedWebp: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameCount: Int? = null,
    val durationMs: Long? = null,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val cause: String? = null,
    val stackTrace: String? = null,
    val appVersion: String = "1.0",
    val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
    val deviceModel: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    val buildType: String = if (com.example.BuildConfig.DEBUG) "debug" else "release",
    val sourceType: String? = null,
    val sourceUri: String? = null,
    val sourceMimeType: String? = null,
    val sourceDisplayName: String? = null,
    val sourceUriReadStatus: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))

    fun toFormattedReport(): String {
        return buildString {
            appendLine("=== All stickers Diagnostic Report ===")
            appendLine("Error ID: $id")
            appendLine("Time: $formattedTime")
            appendLine("Level: ${level.name}")
            appendLine("Operation: $operation")
            appendLine("Stage: ${stage.name}")
            appendLine("App: All stickers")
            appendLine("Version: $appVersion ($buildType)")
            appendLine("Android SDK: $androidVersion")
            appendLine("Device: $deviceModel")
            appendLine("-------------------------------------------")
            
            if (!sourceUri.isNullOrBlank()) {
                appendLine("SOURCE:")
                appendLine("  Source Type: ${sourceType ?: "UNKNOWN"}")
                appendLine("  Original URI: $sourceUri")
                appendLine("  Original MIME: ${sourceMimeType ?: "UNKNOWN"}")
                appendLine("  Original Display Name: ${sourceDisplayName ?: "UNKNOWN"}")
                appendLine("  Original URI Read: ${sourceUriReadStatus ?: "UNKNOWN"}")
                appendLine("-------------------------------------------")
            }

            appendLine("MEDIA FILE:")
            appendLine("  Actual File: ${absolutePath.ifBlank { "NOT_CREATED" }}")
            appendLine("  Extension: $fileExtension")
            appendLine("  MIME: $mimeType")
            appendLine("  Size: $fileSize bytes (${fileSize / 1024} KB)")
            appendLine("  Exists: $fileExists")
            appendLine("  Is File: $isFile")
            appendLine("  Readable: $isReadable")
            appendLine("  Permanent Storage: $isStoragePermanent")
            appendLine("-------------------------------------------")
            appendLine("URI Details:")
            appendLine("  File URI: $uri")
            appendLine("  Scheme: $uriScheme")
            appendLine("  Authority: $uriAuthority")
            appendLine("  Path: $uriPath")
            appendLine("  ContentResolver Open: ${if (contentResolverCanOpen) "SUCCESS" else "FAILED"}")
            if (!contentResolverError.isNullOrBlank()) {
                appendLine("  ContentResolver Error: $contentResolverError")
            }
            appendLine("-------------------------------------------")
            if (isAnimatedWebp != null || width != null || height != null || durationMs != null) {
                appendLine("Media Specifications:")
                if (isAnimatedWebp != null) appendLine("  Animated: $isAnimatedWebp")
                if (width != null && height != null) appendLine("  Dimensions: ${width}x${height} px")
                if (frameCount != null) appendLine("  Frames: $frameCount")
                if (durationMs != null) appendLine("  Duration: ${durationMs} ms")
                appendLine("-------------------------------------------")
            }
            if (!exceptionClass.isNullOrBlank() || !exceptionMessage.isNullOrBlank()) {
                appendLine("Exception:")
                appendLine("  Class: $exceptionClass")
                appendLine("  Message: $exceptionMessage")
                if (!cause.isNullOrBlank()) {
                    appendLine("  Cause: $cause")
                }
                if (!stackTrace.isNullOrBlank()) {
                    appendLine("Stack Trace:")
                    appendLine(stackTrace.trim())
                }
                appendLine("-------------------------------------------")
            }
            appendLine("=== End Diagnostic Report ===")
        }
    }

    companion object {
        fun generateId(): String {
            val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val randHex = Integer.toHexString((Math.random() * 0xFFFF).toInt()).uppercase().padStart(4, '0')
            return "ERR-$dateStr-$randHex"
        }
    }
}
