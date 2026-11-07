package com.example.presentation.components

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.model.StickerType
import com.example.diagnostics.DiagnosticEvent
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.presentation.dialogs.DiagnosticDetailDialog
import com.example.presentation.dialogs.StickerErrorQuickDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@Composable
fun AnimatedStickerView(
    uriString: String?,
    stickerType: StickerType,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    /**
     * Full identity of THIS sticker: pack id + sticker id + source Uri.
     *
     * Never key the preview by the image file name alone ("1.webp"): two
     * different packs legitimately contain identically named stickers, and a
     * recycled LazyColumn card would otherwise keep showing the PREVIOUS
     * pack's image. Defaults to the Uri so existing callers stay correct.
     */
    cacheIdentity: String = uriString.orEmpty()
) {
    val context = LocalContext.current
    // Deliberately has NO key: this holder survives identity changes so an
    // in-flight load for an OLD sticker can be detected and discarded after
    // the composable is reused for a different pack.
    val activeRequest = remember {
        java.util.concurrent.atomic.AtomicReference<Any?>(null)
    }

    var drawable by remember(cacheIdentity) { mutableStateOf<Drawable?>(null) }
    var staticBitmap by remember(cacheIdentity) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(cacheIdentity) { mutableStateOf(true) }
    var hasError by remember(cacheIdentity) { mutableStateOf(false) }
    var errorEvent by remember(cacheIdentity) { mutableStateOf<DiagnosticEvent?>(null) }
    var showQuickErrorDialog by remember { mutableStateOf(false) }
    var showFullDetailDialog by remember { mutableStateOf(false) }

    val isWebm = stickerType == StickerType.WEBM || uriString?.endsWith(".webm", ignoreCase = true) == true

    if (isWebm && !uriString.isNullOrBlank()) {
        if (!autoPlay) {
            // Static placeholder for off-screen / non-focused previews: never
            // spin up a MediaPlayer for every card in the pack list.
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "ملصق فيديو",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
            return
        }
        // Video WebM player. key(cacheIdentity) recreates the player when a
        // recycled card is reused for a different pack; otherwise AndroidView
        // keeps the old surface and its old data source.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            key(cacheIdentity) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                var mediaPlayer: android.media.MediaPlayer? = null

                                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                    try {
                                        mediaPlayer = android.media.MediaPlayer().apply {
                                            setSurface(android.view.Surface(surface))
                                            val uri = when {
                                                uriString.startsWith("content://") || uriString.startsWith("file://") -> Uri.parse(uriString)
                                                else -> Uri.fromFile(File(uriString))
                                            }
                                            setDataSource(ctx, uri)
                                            isLooping = true
                                            setVolume(0f, 0f) // stickers have no audio
                                            prepareAsync()
                                            setOnPreparedListener { start() }
                                        }
                                    } catch (e: Exception) {
                                        hasError = true
                                        errorEvent = DiagnosticsRepository.logError(
                                            context = ctx,
                                            operation = "WEBM_PREVIEW",
                                            stage = DiagnosticStage.PREVIEW,
                                            fileName = uriString.substringAfterLast('/'),
                                            uriString = uriString,
                                            exception = e,
                                            customMessage = "Failed to play WebM video sticker: ${e.message}"
                                        )
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    LaunchedEffect(cacheIdentity) {
        val requestToken = Any()
        activeRequest.set(requestToken)

        // Publish ONLY if this request is still the current one. When a
        // LazyColumn recycles the card for another pack, the new effect
        // replaces the token, so a cancelled/in-flight load can no longer
        // overwrite the new sticker's image with the old pack's bytes.
        fun publish(
            newDrawable: Drawable?,
            newBitmap: android.graphics.Bitmap?,
            newError: DiagnosticEvent?
        ) {
            if (activeRequest.get() === requestToken) {
                drawable = newDrawable
                staticBitmap = newBitmap
                isLoading = false
                hasError = newError != null
                errorEvent = newError
            }
        }

        if (uriString.isNullOrBlank()) {
            publish(
                null,
                null,
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "STICKER_PREVIEW",
                    stage = DiagnosticStage.PREVIEW,
                    fileName = "empty_uri",
                    customMessage = "URI is null or blank"
                )
            )
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                var outDrawable: Drawable? = null
                var outBitmap: android.graphics.Bitmap? = null

                if (uriString.startsWith("content://")) {
                    val contentUri = Uri.parse(uriString)
                    // Test if ContentResolver can open it
                    val testStream = try {
                        context.contentResolver.openInputStream(contentUri)
                    } catch (e: Exception) {
                        null
                    }

                    if (testStream == null) {
                        throw java.io.FileNotFoundException("ContentResolver returned null for URI: $uriString")
                    }
                    testStream.close()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, contentUri)
                        val decoded = ImageDecoder.decodeDrawable(source)
                        if (autoPlay && decoded is Animatable) {
                            decoded.start()
                        }
                        outDrawable = decoded
                    } else {
                        context.contentResolver.openInputStream(contentUri)?.use { s ->
                            outBitmap = BitmapFactory.decodeStream(s)
                        }
                    }
                } else {
                    // File path or file:// URI
                    val actualPath = if (uriString.startsWith("file://")) {
                        Uri.parse(uriString).path ?: uriString.removePrefix("file://")
                    } else {
                        uriString
                    }
                    val file = File(actualPath)

                    if (!file.exists() || !file.isFile || file.length() == 0L) {
                        throw java.io.FileNotFoundException("File does not exist or is empty at path: $actualPath")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(file)
                        val decoded = ImageDecoder.decodeDrawable(source)
                        if (autoPlay && decoded is Animatable) {
                            decoded.start()
                        }
                        outDrawable = decoded
                    } else {
                        FileInputStream(file).use { s ->
                            outBitmap = BitmapFactory.decodeStream(s)
                        }
                    }
                }

                publish(outDrawable, outBitmap, null)
            } catch (e: Exception) {
                val targetFile = if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                    File(uriString.removePrefix("file://"))
                } else null

                publish(
                    null,
                    null,
                    DiagnosticsRepository.logError(
                        context = context,
                        operation = "STICKER_PREVIEW",
                        stage = DiagnosticStage.PREVIEW,
                        fileName = uriString.substringAfterLast('/'),
                        file = targetFile,
                        uriString = uriString,
                        exception = e,
                        customMessage = "تعذر فك ترميز وعرض الملصق: ${e.message}"
                    )
                )
            }
        }
    }

    DisposableEffect(drawable) {
        onDispose {
            (drawable as? Animatable)?.stop()
        }
    }

    Box(
        modifier = modifier.background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            hasError -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        .clickable { showQuickErrorDialog = true }
                        .padding(4.dp)
                        .testTag("preview_error_box")
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "تعذر عرض الملصق",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "تعذر العرض",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    if (errorEvent != null) {
                        Text(
                            text = errorEvent!!.id.takeLast(9),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            drawable != null -> {
                val d = drawable!!
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(d)
                            if (autoPlay && d is Animatable && !d.isRunning) {
                                d.start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            staticBitmap != null -> {
                Image(
                    bitmap = staticBitmap!!.asImageBitmap(),
                    contentDescription = "ملصق",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    if (showQuickErrorDialog && errorEvent != null) {
        StickerErrorQuickDialog(
            event = errorEvent!!,
            onDismiss = { showQuickErrorDialog = false },
            onViewFullDetails = {
                showQuickErrorDialog = false
                showFullDetailDialog = true
            }
        )
    }

    if (showFullDetailDialog && errorEvent != null) {
        DiagnosticDetailDialog(
            event = errorEvent!!,
            onDismiss = { showFullDetailDialog = false }
        )
    }
}
