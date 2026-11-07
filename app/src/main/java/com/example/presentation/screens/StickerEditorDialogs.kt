package com.example.presentation.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.StickerPack
import com.example.creator.FontLibrary
import com.example.creator.STICKER_FONTS
import com.example.creator.StickerFont
import com.example.creator.StickerFontCategory
import com.example.creator.StickerFontLanguage
import com.example.creator.StickerBlendMode
import com.example.creator.StickerCreatorEngine
import com.example.creator.StickerFontStyle
import com.example.creator.StickerTextAlign
import com.example.creator.StickerTextDecoration
import com.example.creator.StickerTextEffect
import com.example.creator.StickerTextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import com.example.presentation.components.AuroraDialog
import com.example.presentation.components.AuroraGradientButton

private val EDITOR_COLORS = listOf(
    AndroidColor.WHITE,
    AndroidColor.BLACK,
    0xFFFF4D6D.toInt(),
    0xFFFFD166.toInt(),
    0xFF06D6A0.toInt(),
    0xFF118AB2.toInt(),
    0xFFEF476F.toInt(),
    0xFF9B5DE5.toInt(),
    0xFFFF9F1C.toInt(),
    0xFFE0E0E0.toInt()
)


@Composable
fun CropStickerDialog(
    source: Bitmap,
    onDismiss: () -> Unit,
    onApply: (zoom: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val previewSize = 286.dp
    val maxOffsetX = StickerCreatorEngine.clampOffsetForZoom(source, zoom, 1f, 0f).first
    val maxOffsetY = StickerCreatorEngine.clampOffsetForZoom(source, zoom, 0f, 1f).second
    val preview by produceState<Bitmap?>(
        null,
        source,
        zoom,
        offsetX,
        offsetY
    ) {
        value = withContext(Dispatchers.Default) {
            StickerCreatorEngine.cropToCanvas(source, zoom, offsetX, offsetY, 256)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "قص الصورة وتأطيرها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "كبّر الصورة وحرّكها داخل الإطار المربع.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(previewSize)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview!!.asImageBitmap(),
                            contentDescription = "معاينة القص",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "جارٍ المعاينة...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(previewSize)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تكبير", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = zoom,
                        onValueChange = {
                            zoom = it
                            val clamped = StickerCreatorEngine.clampOffsetForZoom(
                                source,
                                zoom,
                                offsetX,
                                offsetY
                            )
                            offsetX = clamped.first
                            offsetY = clamped.second
                        },
                        valueRange = 1f..4f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(String.format(java.util.Locale.US, "%.1fx", zoom), fontSize = 11.sp)
                }

                if (maxOffsetX > 0.001f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("أفقي", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
                            onValueChange = {
                                offsetX = StickerCreatorEngine.clampOffsetForZoom(
                                    source,
                                    zoom,
                                    it,
                                    offsetY
                                ).first
                            },
                            valueRange = -maxOffsetX..maxOffsetX,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                if (maxOffsetY > 0.001f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("رأسي", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = offsetY.coerceIn(-maxOffsetY, maxOffsetY),
                            onValueChange = {
                                offsetY = StickerCreatorEngine.clampOffsetForZoom(
                                    source,
                                    zoom,
                                    offsetX,
                                    it
                                ).second
                            },
                            valueRange = -maxOffsetY..maxOffsetY,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = { onApply(zoom, offsetX, offsetY) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apply_crop_button")
                    ) {
                        Text("تطبيق")
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val visibleEmojis = searchEmojis(query, selectedGroup)

    AuroraDialog(
        onDismissRequest = onDismiss,
        title = "مكتبة الإيموجي",
        subtitle = "${ALL_EMOJIS.size} إيموجي حديث • ابحث بالعربية أو الإنجليزية",
        icon = Icons.Default.AutoAwesome,
        showDismiss = true,
        dismissLabel = "إغلاق"
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث عن إيموجي") },
            placeholder = { Text("قلب، ضحك، ورد، party...") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emoji_search_input")
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { selectedGroup = null },
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selectedGroup == null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        Color.Transparent
                    }
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 2.dp
                )
            ) {
                Text("الكل", fontSize = 11.sp)
            }
            EMOJI_GROUPS.forEach { group ->
                OutlinedButton(
                    onClick = { selectedGroup = group.label },
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedGroup == group.label) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        }
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text("${group.emoji} ${group.label}", fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (visibleEmojis.isEmpty()) {
            Text(
                text = "لا توجد نتائج. جرّب كلمة أخرى مثل: قلب، نجمة، قهوة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(visibleEmojis) { emoji ->
                    TextButton(
                        onClick = { onPick(emoji) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FontLibraryDialog(
    selectedFontId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    FontLibrary.initialize(context)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<StickerFontCategory?>(null) }
    var language by remember { mutableStateOf<StickerFontLanguage?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val visibleFonts = STICKER_FONTS.filter { font ->
        (language == null || font.supportsArabic == (language == StickerFontLanguage.ARABIC)) &&
            (category == null || font.category == category) &&
            (query.isBlank() ||
                font.name.contains(query, ignoreCase = true) ||
                font.arabicName.contains(query, ignoreCase = true))
    }

    AuroraDialog(
        onDismissRequest = onDismiss,
        title = "مكتبة Google Fonts",
        subtitle = "خطوط عربية وإنجليزية من مستودع Google Fonts الرسمي على GitHub",
        icon = Icons.Default.AutoAwesome,
        showDismiss = true,
        dismissLabel = "إغلاق"
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث عن خط") },
            placeholder = { Text("Noto, Cairo, عربي...") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { language = null },
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (language == null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 2.dp
                ),
                modifier = Modifier.testTag("font_language_all")
            ) {
                Text("كل الخطوط", fontSize = 11.sp)
            }
            StickerFontLanguage.entries.forEach { item ->
                OutlinedButton(
                    onClick = { language = item },
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = if (language == item) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp
                    ),
                    modifier = Modifier.testTag("font_language_${item.name}")
                ) {
                    Text(item.label, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { category = null },
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (category == null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        Color.Transparent
                    }
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp,
                    vertical = 2.dp
                )
            ) {
                Text("الكل", fontSize = 11.sp)
            }
            StickerFontCategory.entries.forEach { item ->
                OutlinedButton(
                    onClick = { category = item },
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = if (category == item) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        }
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text("${item.emoji} ${item.label}", fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (visibleFonts.isEmpty()) {
                Text(
                    text = "لا توجد خطوط مطابقة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            visibleFonts.forEach { font ->
                val available = FontLibrary.isAvailable(context, font)
                key(font.id, refreshKey) {
                    M3Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (font.id == selectedFontId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.055f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = available) { onSelect(font.id) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = font.arabicName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (font.supportsArabic) {
                                        Text(
                                            text = "  عربي",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (font.id == selectedFontId) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = font.name + " • " + font.previewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            when {
                                available -> {
                                    Text(
                                        text = "استخدام",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                font.isRemote -> {
                                    IconButton(
                                        enabled = downloadingId == null,
                                        onClick = {
                                            downloadingId = font.id
                                            scope.launch {
                                                val result = FontLibrary.download(context, font)
                                                downloadingId = null
                                                refreshKey++
                                                if (result.isSuccess) {
                                                    onSelect(font.id)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "تم تنزيل الخط ${font.arabicName}",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        result.exceptionOrNull()?.message
                                                            ?: "تعذر تنزيل الخط",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    ) {
                                        if (downloadingId == font.id) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = "تنزيل الخط",
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextElementDialog(
    initial: StickerTextElement?,
    onDismiss: () -> Unit,
    onLiveChange: (StickerTextElement) -> Unit = {},
    onConfirm: (StickerTextElement) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial?.text ?: "") }
    var fontSize by remember(initial) { mutableFloatStateOf(initial?.fontSize ?: 56f) }
    var color by remember(initial) { mutableStateOf(initial?.color ?: AndroidColor.WHITE) }
    var strokeColor by remember(initial) { mutableStateOf(initial?.strokeColor ?: AndroidColor.BLACK) }
    var strokeWidth by remember(initial) { mutableFloatStateOf(initial?.strokeWidth ?: 6f) }
    var shadow by remember(initial) { mutableStateOf(initial?.shadow ?: true) }
    var opacity by remember(initial) { mutableFloatStateOf(initial?.opacity ?: 1f) }
    var fontStyle by remember(initial) {
        mutableStateOf(initial?.fontStyle ?: StickerFontStyle.SANS_BOLD)
    }
    var fontId by remember(initial) { mutableStateOf(initial?.fontId ?: "") }
    var decoration by remember(initial) {
        mutableStateOf(initial?.decoration ?: StickerTextDecoration.NONE)
    }
    var effect by remember(initial) {
        mutableStateOf(initial?.effect ?: StickerTextEffect.NONE)
    }
    var effectStartColor by remember(initial) {
        mutableStateOf(initial?.effectStartColor ?: 0xFFFFD166.toInt())
    }
    var effectEndColor by remember(initial) {
        mutableStateOf(initial?.effectEndColor ?: 0xFFFF4D6D.toInt())
    }
    var effectIntensity by remember(initial) {
        mutableFloatStateOf(initial?.effectIntensity ?: 1f)
    }
    var italic by remember(initial) { mutableStateOf(initial?.italic ?: false) }
    var textAlign by remember(initial) {
        mutableStateOf(initial?.textAlign ?: StickerTextAlign.CENTER)
    }
    var lineSpacing by remember(initial) { mutableFloatStateOf(initial?.lineSpacing ?: 1f) }
    var surroundEmoji by remember(initial) { mutableStateOf(initial?.surroundEmoji ?: "") }
    var showFontLibrary by remember { mutableStateOf(false) }
    var showSurroundEmojiPicker by remember { mutableStateOf(false) }

    val selectedFontName = STICKER_FONTS.firstOrNull { it.id == fontId }?.arabicName
        ?: fontStyle.label

    fun buildElement(): StickerTextElement = StickerTextElement(
        id = initial?.id ?: System.nanoTime(),
        text = text,
        isEmoji = false,
        x = initial?.x ?: 0.5f,
        y = initial?.y ?: 0.5f,
        scale = initial?.scale ?: 1f,
        rotation = initial?.rotation ?: 0f,
        fontSize = fontSize,
        color = color,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        shadow = shadow,
        opacity = opacity,
        fontStyle = fontStyle,
        fontId = fontId,
        italic = italic,
        textAlign = textAlign,
        lineSpacing = lineSpacing,
        surroundEmoji = surroundEmoji,
        decoration = decoration,
        effect = effect,
        effectStartColor = effectStartColor,
        effectEndColor = effectEndColor,
        effectIntensity = effectIntensity,
        visible = initial?.visible ?: true,
        blendMode = initial?.blendMode ?: StickerBlendMode.NORMAL
    )

    LaunchedEffect(
        text,
        fontSize,
        color,
        strokeColor,
        strokeWidth,
        shadow,
        opacity,
        fontStyle,
        fontId,
        italic,
        textAlign,
        lineSpacing,
        surroundEmoji,
        decoration,
        effect,
        effectStartColor,
        effectEndColor,
        effectIntensity
    ) {
        onLiveChange(buildElement())
    }

    AuroraDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "إضافة نص سحري" else "تعديل النص",
        subtitle = "خطوط Google Fonts • تأثيرات ذهبية ونارية ونيون",
        icon = Icons.Default.AutoAwesome,
        confirmLabel = "تم",
        onConfirm = {
            val updated = buildElement()
            onLiveChange(updated)
            onConfirm(updated)
        },
        confirmEnabled = text.isNotBlank()
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("النص") },
                placeholder = { Text("اكتب عبارة الملصق...") },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sticker_text_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("مكتبة الخطوط", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            AuroraGradientButton(
                label = "الخط الحالي: $selectedFontName",
                onClick = { showFontLibrary = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.AutoAwesome
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("خطوط النظام", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerFontStyle.entries.forEach { style ->
                    OutlinedButton(
                        onClick = {
                            fontStyle = style
                            fontId = ""
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (fontId.isBlank() && fontStyle == style) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        modifier = Modifier.testTag("font_style_${style.name}")
                    ) {
                        Text(
                            text = style.label,
                            fontWeight = if (fontId.isBlank() && fontStyle == style) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نص مائل",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = italic, onCheckedChange = { italic = it })
            }
            Text("محاذاة النص", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerTextAlign.entries.forEach { align ->
                    OutlinedButton(
                        onClick = { textAlign = align },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (textAlign == align) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.testTag("text_align_${align.name}")
                    ) {
                        Text(
                            text = align.label,
                            fontSize = 11.sp,
                            fontWeight = if (textAlign == align) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
            Text(
                text = "تباعد الأسطر: ${"%.1f".format(lineSpacing)}×",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = lineSpacing,
                onValueChange = { lineSpacing = it },
                valueRange = 0.7f..2.5f
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("تأثير النص", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerTextEffect.entries.forEach { item ->
                    OutlinedButton(
                        onClick = { effect = item },
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (effect == item) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 9.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.testTag("text_effect_${item.name}")
                    ) {
                        Text(
                            text = "${item.emoji} ${item.label}",
                            fontWeight = if (effect == item) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (effect != StickerTextEffect.NONE) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("ألوان التأثير", style = MaterialTheme.typography.bodySmall)
                ColorSwatchRow(selected = effectStartColor, onSelect = { effectStartColor = it })
                Spacer(modifier = Modifier.height(4.dp))
                ColorSwatchRow(selected = effectEndColor, onSelect = { effectEndColor = it })
                Text(
                    text = "شدة التأثير: ${(effectIntensity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = effectIntensity,
                    onValueChange = { effectIntensity = it },
                    valueRange = 0.2f..2f
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("الزخرفة", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerTextDecoration.entries.forEach { item ->
                    OutlinedButton(
                        onClick = { decoration = item },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (decoration == item) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        modifier = Modifier.testTag("text_decoration_${item.name}")
                    ) {
                        Text(
                            text = item.label,
                            fontWeight = if (decoration == item) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (surroundEmoji.isBlank()) {
                        "إيموجي حول النص"
                    } else {
                        "الإيموجي المحيط: $surroundEmoji"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { showSurroundEmojiPicker = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text("اختيار", fontSize = 11.sp)
                }
                if (surroundEmoji.isNotBlank()) {
                    TextButton(onClick = { surroundEmoji = "" }) {
                        Text("مسح", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("حجم النص", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 16f..150f
            )

            Text("لون النص", style = MaterialTheme.typography.bodySmall)
            ColorSwatchRow(selected = color, onSelect = { color = it })

            Spacer(modifier = Modifier.height(8.dp))
            Text("لون الحدود", style = MaterialTheme.typography.bodySmall)
            ColorSwatchRow(selected = strokeColor, onSelect = { strokeColor = it })

            Spacer(modifier = Modifier.height(8.dp))
            Text("سماكة الحدود", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = strokeWidth,
                onValueChange = { strokeWidth = it },
                valueRange = 0f..20f
            )

            Text("الشفافية", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                valueRange = 0.1f..1f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ظل خلف النص",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = shadow,
                    onCheckedChange = { shadow = it }
                )
            }
        }
    }

    if (showFontLibrary) {
        FontLibraryDialog(
            selectedFontId = fontId,
            onSelect = {
                fontId = it
                showFontLibrary = false
            },
            onDismiss = { showFontLibrary = false }
        )
    }

    if (showSurroundEmojiPicker) {
        EmojiPickerDialog(
            onDismiss = { showSurroundEmojiPicker = false },
            onPick = {
                surroundEmoji = it
                showSurroundEmojiPicker = false
            }
        )
    }
}

@Composable
fun SaveStickerDialog(
    packs: List<StickerPack>,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, packId: Long?, newPackName: String?) -> Unit
) {
    var name by remember { mutableStateOf("ملصق ${packs.size + 1}") }
    var emoji by remember { mutableStateOf("😀") }
    var createNewPack by remember { mutableStateOf(packs.isEmpty()) }
    var selectedPackId by remember { mutableStateOf(packs.firstOrNull()?.id) }
    var newPackName by remember { mutableStateOf("ملصقاتي") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    AuroraDialog(
        onDismissRequest = onDismiss,
        title = "حفظ الملصق",
        subtitle = "اختر الاسم والإيموجي والحزمة",
        icon = Icons.Default.AutoAwesome,
        confirmLabel = "حفظ",
        onConfirm = {
            onSave(
                name.ifBlank { "ملصق" },
                emoji.ifBlank { "😀" },
                if (createNewPack) null else selectedPackId,
                if (createNewPack) newPackName.ifBlank { "ملصقاتي" } else null
            )
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الملصق") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_sticker_name")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text("الإيموجي") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_sticker_emoji")
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showEmojiPicker = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("بحث", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ALL_EMOJIS.take(24).forEach { quickEmoji ->
                    OutlinedButton(
                        onClick = { emoji = quickEmoji },
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(quickEmoji, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "اختر الحزمة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                packs.forEach { pack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                createNewPack = false
                                selectedPackId = pack.id
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !createNewPack && selectedPackId == pack.id,
                            onClick = {
                                createNewPack = false
                                selectedPackId = pack.id
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${pack.stickerCount} ملصق",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createNewPack = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = createNewPack,
                        onClick = { createNewPack = true }
                    )
                    Text(
                        text = "حزمة جديدة",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (createNewPack) {
                    OutlinedTextField(
                        value = newPackName,
                        onValueChange = { newPackName = it },
                        label = { Text("اسم الحزمة الجديدة") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("save_new_pack_name")
                    )
                }
            }
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onDismiss = { showEmojiPicker = false },
            onPick = {
                emoji = it
                showEmojiPicker = false
            }
        )
    }
}

@Composable
private fun ColorSwatchRow(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EDITOR_COLORS.forEach { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (color == selected) 2.dp else 1.dp,
                        color = if (color == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelect(color) }
            )
        }
    }
}


@Composable
fun StudioImageNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String = "حفظ",
    description: String = "سيُحفظ الملصق كصورة مستقلة في الاستوديو، ويمكنك تعديله لاحقًا.",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AuroraDialog(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = "احفظ تصميمك وأعد تعديله وقتما تشاء",
        icon = Icons.Default.AutoAwesome,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(name.trim()) },
        confirmEnabled = name.isNotBlank()
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("الاسم") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_image_name_input")
        )
    }
}
