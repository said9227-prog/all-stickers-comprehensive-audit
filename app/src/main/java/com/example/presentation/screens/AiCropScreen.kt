package com.example.presentation.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.AuroraGradientButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class RectCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
}

private fun trimBorders(
    source: Bitmap,
    tolerance: Int = 26
): RectCrop? {
    val width = source.width
    val height = source.height
    if (width < 4 || height < 4) return null
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val reference = pixels[0]
    val refRed = (reference shr 16) and 0xFF
    val refGreen = (reference shr 8) and 0xFF
    val refBlue = reference and 0xFF
    fun isBackground(pixel: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return abs(red - refRed) <= tolerance &&
            abs(green - refGreen) <= tolerance &&
            abs(blue - refBlue) <= tolerance
    }
    var left = 0
    var right = width - 1
    var top = 0
    var bottom = height - 1
    fun columnEmpty(x: Int, from: Int, to: Int): Boolean {
        if (from > to) return true
        for (y in from..to) {
            if (!isBackground(pixels[y * width + x])) return false
        }
        return true
    }
    fun rowEmpty(y: Int, from: Int, to: Int): Boolean {
        if (from > to) return true
        for (x in from..to) {
            if (!isBackground(pixels[y * width + x])) return false
        }
        return true
    }
    while (left < right && columnEmpty(left, top, bottom)) left++
    while (right > left && columnEmpty(right, top, bottom)) right--
    while (top < bottom && rowEmpty(top, left, right)) top++
    while (bottom > top && rowEmpty(bottom, left, right)) bottom--
    if (right - left + 1 <= 3 || bottom - top + 1 <= 3) return null
    return RectCrop(left, top, right, bottom)
}

private fun applyRectCrop(source: Bitmap, rect: RectCrop): Bitmap {
    val safeWidth = rect.width.coerceIn(1, source.width)
    val safeHeight = rect.height.coerceIn(1, source.height)
    val safeLeft = rect.left.coerceIn(0, source.width - safeWidth)
    val safeTop = rect.top.coerceIn(0, source.height - safeHeight)
    return Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCropScreen(
    onNavigateBack: () -> Unit,
    onSaveImages: (List<Bitmap>, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedResult by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var trimTop by remember { mutableFloatStateOf(0f) }
    var trimBottom by remember { mutableFloatStateOf(0f) }
    var trimLeft by remember { mutableFloatStateOf(0f) }
    var trimRight by remember { mutableFloatStateOf(0f) }
    var isWorking by remember { mutableStateOf(false) }
    var appliedAutoRect by remember { mutableStateOf<RectCrop?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isWorking = true
            appliedAutoRect = null
            sourceBitmap = withContext(Dispatchers.IO) { decodeBackgroundBitmap(context, uri) }
            croppedResult = null
            previewBitmap = null
            trimTop = 0f
            trimBottom = 0f
            trimLeft = 0f
            trimRight = 0f
            isWorking = false
        }
    }

    fun manualCropRect(source: Bitmap): RectCrop {
        val left = (source.width * trimTopPercent(trimLeft)).toInt()
        val right = source.width - 1 - (source.width * trimTopPercent(trimRight)).toInt()
        val top = (source.height * trimTopPercent(trimTop)).toInt()
        val bottom = source.height - 1 - (source.height * trimTopPercent(trimBottom)).toInt()
        return RectCrop(
            left.coerceIn(0, (source.width - 2).coerceAtLeast(0)),
            top.coerceAtMost((bottom - 2).coerceAtLeast(0)),
            right.coerceAtLeast((left + 1).coerceAtMost(source.width - 1)),
            bottom.coerceAtMost(source.height - 1)
        )
    }

    LaunchedEffect(sourceBitmap, trimTop, trimBottom, trimLeft, trimRight) {
        val source = sourceBitmap ?: return@LaunchedEffect
        previewBitmap = withContext(Dispatchers.Default) {
            runCatching {
                val allTrimsZero = trimTop <= 0.001f && trimBottom <= 0.001f &&
                    trimLeft <= 0.001f && trimRight <= 0.001f
                val rect = if (allTrimsZero) {
                    appliedAutoRect ?: manualCropRect(source)
                } else {
                    manualCropRect(source)
                }
                applyRectCrop(source, rect)
            }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "قص الصور",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "قص تلقائي للحدود مع تحكم يدوي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuroraGradientButton(
                label = "اختر صورة من المعرض",
                onClick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.PhotoLibrary
            )

            val source = sourceBitmap
            if (source != null) {
                val preview = previewBitmap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF12121A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "معاينة القص",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        Text("جارٍ إعداد المعاينة…", color = Color.White)
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            val rect = withContext(Dispatchers.Default) { trimBorders(source) }
                            if (rect != null) {
                                appliedAutoRect = rect
                                croppedResult = withContext(Dispatchers.Default) {
                                    applyRectCrop(source, rect)
                                }
                                trimTop = 0f
                                trimBottom = 0f
                                trimLeft = 0f
                                trimRight = 0f
                            }
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text("قص الحدود تلقائيًا", fontSize = 12.sp)
                }

                Text(
                    text = "قص أعلى ${trimTop.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(value = trimTop, onValueChange = { trimTop = it }, valueRange = 0f..40f)
                Text(
                    text = "قص أسفل ${trimBottom.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(value = trimBottom, onValueChange = { trimBottom = it }, valueRange = 0f..40f)
                Text(
                    text = "قص يسار ${trimLeft.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(value = trimLeft, onValueChange = { trimLeft = it }, valueRange = 0f..40f)
                Text(
                    text = "قص يمين ${trimRight.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(value = trimRight, onValueChange = { trimRight = it }, valueRange = 0f..40f)

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            val result = withContext(Dispatchers.Default) {
                                croppedResult ?: applyRectCrop(source, manualCropRect(source))
                            }
                            croppedResult = result
                            onSaveImages(listOf(result), "قص_${System.currentTimeMillis()}")
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "حفظ الصورة المقصوصة في المعرض",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "اختر صورة لبدء القص. سيتم قص الحدود الملونة تلقائيًا، أو استخدم أشرطة القص اليدوية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun trimTopPercent(value: Float): Float = (value / 100f).coerceIn(0f, 0.4f)
