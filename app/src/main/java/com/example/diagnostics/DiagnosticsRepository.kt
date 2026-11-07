package com.example.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.media.WebpParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object DiagnosticsRepository {

    private const val MAX_EVENTS = 100

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    @Synchronized
    fun logEvent(event: DiagnosticEvent) {
        val current = _events.value.toMutableList()
        current.add(0, event) // latest first
        if (current.size > MAX_EVENTS) {
            _events.value = current.take(MAX_EVENTS)
        } else {
            _events.value = current
        }
    }

    fun logError(
        context: Context,
        operation: String,
        stage: DiagnosticStage,
        fileName: String,
        file: File? = null,
        uriString: String? = null,
        exception: Throwable? = null,
        customMessage: String? = null,
        sourceType: String? = null,
        sourceUri: String? = null,
        sourceMimeType: String? = null,
        sourceDisplayName: String? = null,
        sourceUriReadStatus: String? = null
    ): DiagnosticEvent {
        val id = DiagnosticEvent.generateId()

        // Inspect file if available
        val exists = file?.exists() == true
        val isFile = file?.isFile == true
        val isReadable = file?.canRead() == true
        val fileSize = file?.length() ?: 0L
        val absPath = file?.absolutePath ?: (if (uriString?.startsWith("/") == true) uriString else "")
        val isPermanent = absPath.contains("/files/stickers/")

        // Inspect URI and ContentResolver
        val parsedUri = try {
            when {
                uriString.isNullOrBlank() && file != null -> Uri.fromFile(file)
                !uriString.isNullOrBlank() -> Uri.parse(uriString)
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        val scheme = parsedUri?.scheme ?: ""
        val authority = parsedUri?.authority ?: ""
        val uriPath = parsedUri?.path ?: ""

        var canOpen = false
        var crError: String? = null

        if (parsedUri != null) {
            try {
                if (scheme == "content" || scheme == "file") {
                    val stream = context.contentResolver.openInputStream(parsedUri)
                    if (stream != null) {
                        canOpen = true
                        stream.close()
                    } else {
                        crError = "ContentResolver returned null stream"
                    }
                } else if (file != null && exists && isReadable) {
                    canOpen = true
                } else {
                    crError = "Unsupported or missing URI scheme: '$scheme'"
                }
            } catch (e: Throwable) {
                canOpen = false
                crError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        // WebP metadata inspection if possible
        var isAnimatedWebp: Boolean? = null
        var width: Int? = null
        var height: Int? = null
        var frameCount: Int? = null
        var durationMs: Long? = null

        if (file != null && exists && isReadable) {
            try {
                val info = WebpParser.parse(file)
                if (info.isWebp) {
                    isAnimatedWebp = info.isAnimated
                    width = info.width
                    height = info.height
                    frameCount = info.frameCount
                    durationMs = info.totalDurationMs
                }
            } catch (_: Exception) {}
        }

        // Stack trace
        val stackTrace = exception?.let { ex ->
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val ext = fileName.substringAfterLast('.', file?.extension ?: "")
        val mimeType = when (ext.lowercase()) {
            "webp" -> "image/webp"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }

        val event = DiagnosticEvent(
            id = id,
            level = DiagnosticLevel.ERROR,
            operation = operation,
            stage = stage,
            fileName = fileName.ifBlank { file?.name ?: "unknown" },
            fileExtension = ext,
            mimeType = mimeType,
            fileSize = fileSize,
            fileExists = exists,
            isFile = isFile,
            isReadable = isReadable,
            absolutePath = absPath,
            isStoragePermanent = isPermanent,
            uri = uriString ?: parsedUri?.toString() ?: "",
            uriScheme = scheme,
            uriAuthority = authority,
            uriPath = uriPath,
            contentResolverCanOpen = canOpen,
            contentResolverError = crError,
            isAnimatedWebp = isAnimatedWebp,
            width = width,
            height = height,
            frameCount = frameCount,
            durationMs = durationMs,
            exceptionClass = exception?.javaClass?.name,
            exceptionMessage = customMessage ?: exception?.message,
            cause = exception?.cause?.toString(),
            stackTrace = stackTrace,
            sourceType = sourceType,
            sourceUri = sourceUri,
            sourceMimeType = sourceMimeType,
            sourceDisplayName = sourceDisplayName,
            sourceUriReadStatus = sourceUriReadStatus
        )

        logEvent(event)
        return event
    }

    fun logInfo(
        operation: String,
        stage: DiagnosticStage,
        fileName: String,
        message: String
    ) {
        val event = DiagnosticEvent(
            id = DiagnosticEvent.generateId(),
            level = DiagnosticLevel.INFO,
            operation = operation,
            stage = stage,
            fileName = fileName,
            exceptionMessage = message
        )
        logEvent(event)
    }

    fun copyToClipboard(context: Context, event: DiagnosticEvent) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Diagnostic Report ${event.id}", event.toFormattedReport())
        clipboard.setPrimaryClip(clip)
    }

    fun createShareIntent(event: DiagnosticEvent): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnostic Report: ${event.id}")
            putExtra(Intent.EXTRA_TEXT, event.toFormattedReport())
        }
    }

    fun clearAll() {
        _events.value = emptyList()
    }

    fun deleteById(id: String) {
        _events.value = _events.value.filter { it.id != id }
    }
}
