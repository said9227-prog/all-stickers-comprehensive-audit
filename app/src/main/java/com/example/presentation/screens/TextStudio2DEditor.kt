package com.example.presentation.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.creator.FontLibrary
import com.example.creator.StickerCreatorEngine
import com.example.creator.StickerFontLanguage
import com.example.creator.StickerTextAlign
import com.example.creator.StickerTextElement
import com.example.creator.StickerWatermark
import com.example.creator.stickerFontsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val STUDIO_2D_SECTIONS = listOf(
    "النص",
    "الخط",
    "التنسيق",
    "الحركة",
    "الطبقات",
    "الخلفية",
    "التوقيع"
)

@Composable
internal fun TextStudio2DEditor(
    onExport: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FontLibrary.initialize(context)

    val elements = remember {
        mutableStateListOf(
            StickerTextElement(
                text = "اكتب هنا",
                fontId = "cairo",
                x = 0.5f,
                y = 0.45f
            )
        )
    }
    var selectedId by remember { mutableStateOf<Long?>(elements.first().id) }
    var backgroundColor by remember { mutableStateOf(0x00000000) }
    var backgroundImage by remember { mutableStateOf<Bitmap?>(null) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("All Stickers") }
    var watermarkColor by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.55f) }
    var watermarkSize by remember { mutableFloatStateOf(40f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fontLanguageTab by remember { mutableStateOf(StickerFontLanguage.ARABIC) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var sliderPushed by remember { mutableStateOf(false) }
    var sectionIndex by rememberSaveable { mutableStateOf(0) }
    var typingUndoKey by remember { mutableStateOf<Long?>(null) }
    val undoStack = remember { mutableStateListOf<Text2DSnapshot>() }
    val redoStack = remember { mutableStateListOf<Text2DSnapshot>() }
    val selectedElement = elements.firstOrNull { it.id == selectedId }

    fun currentSnapshot() = Text2DSnapshot(
        elements = elements.toList(),
        selectedId = selectedId,
        backgroundColor = backgroundColor,
        backgroundImage = backgroundImage,
        watermarkEnabled = watermarkEnabled,
        watermarkText = watermarkText,
        watermarkColor = watermarkColor,
        watermarkOpacity = watermarkOpacity,
        watermarkSize = watermarkSize
    )

    fun applySnapshot(snapshot: Text2DSnapshot) {
        elements.clear()
        elements.addAll(snapshot.elements)
        selectedId = snapshot.selectedId
        backgroundColor = snapshot.backgroundColor
        backgroundImage = snapshot.backgroundImage
        watermarkEnabled = snapshot.watermarkEnabled
        watermarkText = snapshot.watermarkText
        watermarkColor = snapshot.watermarkColor
        watermarkOpacity = snapshot.watermarkOpacity
        watermarkSize = snapshot.watermarkSize
    }

    fun pushUndo() {
        val snapshot = currentSnapshot()
        if (undoStack.lastOrNull() != snapshot) {
            undoStack.add(snapshot)
            if (undoStack.size > 60) undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.add(currentSnapshot())
        applySnapshot(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.add(currentSnapshot())
        applySnapshot(snapshot)
    }

    fun updateSelected(record: Boolean = true, block: (StickerTextElement) -> StickerTextElement) {
        val index = elements.indexOfFirst { it.id == selectedId }
        if (index < 0) return
        if (record) pushUndo()
        elements[index] = block(elements[index])
    }

    fun addElement(text: String = "نص جديد") {
        pushUndo()
        val element = StickerTextElement(
            text = text,
            fontId = "cairo",
            x = 0.3f + (elements.size % 4) * 0.14f,
            y = 0.3f + (elements.size % 3) * 0.16f
        )
        elements.add(element)
        selectedId = element.id
    }

    fun duplicateSelected() {
        val current = selectedElement ?: return
        pushUndo()
        val copy = current.copy(
            id = System.nanoTime(),
            x = (current.x + 0.05f).coerceAtMost(0.95f),
            y = (current.y + 0.05f).coerceAtMost(0.95f)
        )
        elements.add(copy)
        selectedId = copy.id
    }

    fun deleteSelected() {
        if (selectedElement == null || elements.size <= 1) return
        pushUndo()
        val index = elements.indexOfFirst { it.id == selectedId }
        if (index >= 0) elements.removeAt(index)
        selectedId = elements.lastOrNull()?.id
    }

    fun resetCanvas() {
        pushUndo()
        elements.clear()
        elements.add(
            StickerTextElement(
                text = "اكتب هنا",
                fontId = "cairo",
                x = 0.5f,
                y = 0.45f
            )
        )
        selectedId = elements.first().id
        backgroundColor = 0x00000000
        backgroundImage = null
        watermarkEnabled = false
    }

    fun nudgeSelected(dx: Float, dy: Float) {
        updateSelected { element ->
            element.copy(
                x = (element.x + dx).coerceIn(0f, 1f),
                y = (element.y + dy).coerceIn(0f, 1f)
            )
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodeBackgroundBitmap(context, uri)
            }
            if (decoded != null) {
                pushUndo()
                backgroundImage = decoded
            }
        }
    }

    val activeWatermark = if (watermarkEnabled && watermarkText.isNotBlank()) {
        StickerWatermark(
            text = watermarkText,
            color = watermarkColor,
            opacity = watermarkOpacity,
            size = watermarkSize
        )
    } else {
        null
    }

    val transparentBackground = backgroundColor == 0x00000000 && backgroundImage == null

    LaunchedEffect(
        elements.toList(),
        backgroundColor,
        backgroundImage,
        watermarkEnabled,
        watermarkText,
        watermarkColor,
        watermarkOpacity,
        watermarkSize
    ) {
        delay(16)
        val rendered = withContext(Dispatchers.Default) {
            StickerCreatorEngine.render(
                baseBitmap = null,
                elements = elements.toList(),
                backgroundColor = backgroundColor,
                backgroundBitmap = backgroundImage,
                watermark = activeWatermark ?: StickerWatermark()
            )
        }
        previewBitmap = rendered
    }

    LaunchedEffect(selectedId) {
        typingUndoKey = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0A0F2C), Color(0xFF141034))
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .onSizeChanged { previewSize = it }
            ) {
                if (transparentBackground) {
                    CheckerboardCanvas(modifier = Modifier.matchParentSize())
                }
                val previewImage = previewBitmap
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(selectedId, elements.size) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var moved = false
                                    var cumulativeZoom = 1f
                                    var cumulativeRotation = 0f
                                    var cumulativePanX = 0f
                                    var cumulativePanY = 0f
                                    var isMultiTouch = false
                                    var activeId: Long? = null
                                    var base: StickerTextElement? = null
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        if (event.changes.size > 1) isMultiTouch = true
                                        val movement = event.changes
                                            .filter { it.positionChanged() }
                                            .maxOfOrNull { it.positionChange().getDistance() } ?: 0f
                                        if (!moved && movement > viewConfiguration.touchSlop) {
                                            val size = previewSize
                                            val hit = StickerCreatorEngine.hitTestText(
                                                elements = elements.toList(),
                                                normalizedX = down.position.x /
                                                    size.width.coerceAtLeast(1),
                                                normalizedY = down.position.y /
                                                    size.height.coerceAtLeast(1)
                                            )
                                            activeId = hit ?: selectedId
                                            base = elements.toList()
                                                .firstOrNull { it.id == activeId }
                                            if (base != null) {
                                                selectedId = base.id
                                                pushUndo()
                                                moved = true
                                            } else {
                                                moved = false
                                                break
                                            }
                                        }
                                        if (moved && base != null && activeId != null) {
                                            val size = previewSize
                                            val next = if (isMultiTouch) {
                                                cumulativeZoom *= event.calculateZoom()
                                                cumulativeRotation += event.calculateRotation()
                                                base.copy(
                                                    scale = (base.scale * cumulativeZoom)
                                                        .coerceIn(0.15f, 6f),
                                                    rotation = base.rotation + cumulativeRotation
                                                )
                                            } else {
                                                val pan = event.calculatePan()
                                                cumulativePanX += pan.x /
                                                    size.width.coerceAtLeast(1)
                                                cumulativePanY += pan.y /
                                                    size.height.coerceAtLeast(1)
                                                base.copy(
                                                    x = (base.x + cumulativePanX)
                                                        .coerceIn(0f, 1f),
                                                    y = (base.y + cumulativePanY)
                                                        .coerceIn(0f, 1f)
                                                )
                                            }
                                            val index =
                                                elements.indexOfFirst { it.id == activeId }
                                            if (index >= 0) elements[index] = next
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .pointerInput(elements.size) {
                                detectTapGestures { offset ->
                                    val size = previewSize
                                    if (size.width > 0 && size.height > 0) {
                                        val hit = StickerCreatorEngine.hitTestText(
                                            elements = elements.toList(),
                                            normalizedX = offset.x / size.width,
                                            normalizedY = offset.y / size.height
                                        )
                                        if (hit != null) selectedId = hit
                                    }
                                }
                            }
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
            Text(
                text = "اضغط لاختيار النص • اسحب للتحريك • إصبعان للتكبير والتدوير",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        StudioSectionBar(
            sections = STUDIO_2D_SECTIONS,
            selectedIndex = sectionIndex,
            onSelect = { sectionIndex = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10152B))
        ) {
            key(sectionIndex) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when (sectionIndex) {
                        0 -> {
                            PanelLabel("نص الطبقة المحددة")
                            OutlinedTextField(
                                value = selectedElement?.text ?: "",
                                onValueChange = { value ->
                                    val id = selectedElement?.id
                                    if (id == null) {
                                        addElement(value)
                                    } else {
                                        if (typingUndoKey != id) {
                                            pushUndo()
                                            typingUndoKey = id
                                        }
                                        val index = elements.indexOfFirst { it.id == id }
                                        if (index >= 0) {
                                            elements[index] = elements[index].copy(text = value)
                                        }
                                    }
                                },
                                label = { Text("اكتب النص ويظهر فوراً") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PanelLabel("إجراءات سريعة")
                            StudioToolRow {
                                ToolChip("نص جديد +") { addElement() }
                                ToolChip("نسخ", enabled = selectedElement != null) {
                                    duplicateSelected()
                                }
                                ToolChip(
                                    "حذف",
                                    enabled = selectedElement != null && elements.size > 1
                                ) { deleteSelected() }
                                ToolChip("خيارات متقدمة", enabled = selectedElement != null) {
                                    showAdvancedDialog = true
                                }
                            }
                        }
                        1 -> {
                            PanelLabel("لغة الخطوط")
                            StudioToolRow {
                                ToolChip(
                                    "خطوط عربية",
                                    selected = fontLanguageTab == StickerFontLanguage.ARABIC
                                ) { fontLanguageTab = StickerFontLanguage.ARABIC }
                                ToolChip(
                                    "English",
                                    selected = fontLanguageTab == StickerFontLanguage.ENGLISH
                                ) { fontLanguageTab = StickerFontLanguage.ENGLISH }
                            }
                            PanelLabel("الخط (${selectedElement?.fontId ?: "-"})")
                            StudioToolRow {
                                stickerFontsFor(fontLanguageTab).forEach { font ->
                                    ToolChip(
                                        label = font.arabicName.ifBlank { font.id },
                                        selected = selectedElement?.fontId == font.id
                                    ) {
                                        updateSelected { it.copy(fontId = font.id) }
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (selectedElement != null) {
                                SliderField(
                                    "حجم الخط", selectedElement.fontSize, 20f..120f,
                                    selectedElement.fontSize.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        updateSelected(record = false) { it.copy(fontSize = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                SliderField(
                                    "مقياس النص", selectedElement.scale, 0.35f..3f,
                                    "${selectedElement.scale.format2()}x",
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        updateSelected(record = false) { it.copy(scale = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                SliderField(
                                    "التدوير", selectedElement.rotation, -180f..180f,
                                    "${selectedElement.rotation.format2()}°",
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        updateSelected(record = false) { it.copy(rotation = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                SliderField(
                                    "الشفافية", selectedElement.opacity, 0.1f..1f,
                                    "${(selectedElement.opacity * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        updateSelected(record = false) { it.copy(opacity = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                SliderField(
                                    "سماكة الحدود", selectedElement.strokeWidth, 0f..24f,
                                    selectedElement.strokeWidth.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        updateSelected(record = false) {
                                            it.copy(strokeWidth = value)
                                        }
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                PanelLabel("لون النص")
                                ColorPaletteRow(
                                    selectedItem = selectedElement.color,
                                    onPick = { color -> updateSelected { it.copy(color = color) } }
                                )
                                PanelLabel("لون الحدود")
                                ColorPaletteRow(
                                    selectedItem = selectedElement.strokeColor,
                                    onPick = { color ->
                                        updateSelected { it.copy(strokeColor = color) }
                                    }
                                )
                                PanelLabel("المحاذاة")
                                StudioToolRow {
                                    StickerTextAlign.entries.forEach { align ->
                                        ToolChip(
                                            align.label,
                                            selected = selectedElement.textAlign == align
                                        ) {
                                            updateSelected { it.copy(textAlign = align) }
                                        }
                                    }
                                }
                                SwitchField(
                                    label = "نص مائل",
                                    checked = selectedElement.italic,
                                    onCheckedChange = { checked ->
                                        updateSelected { it.copy(italic = checked) }
                                    }
                                )
                                SwitchField(
                                    label = "ظل النص",
                                    checked = selectedElement.shadow,
                                    onCheckedChange = { checked ->
                                        updateSelected { it.copy(shadow = checked) }
                                    }
                                )
                            } else {
                                Text(
                                    "أضف نصاً للبدء.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        3 -> {
                            PanelLabel("تحريك دقيق")
                            StudioToolRow {
                                ToolChip("X-", enabled = selectedElement != null) {
                                    nudgeSelected(-0.02f, 0f)
                                }
                                ToolChip("X+", enabled = selectedElement != null) {
                                    nudgeSelected(0.02f, 0f)
                                }
                                ToolChip("Y-", enabled = selectedElement != null) {
                                    nudgeSelected(0f, -0.02f)
                                }
                                ToolChip("Y+", enabled = selectedElement != null) {
                                    nudgeSelected(0f, 0.02f)
                                }
                            }
                            PanelLabel("الحجم والدوران")
                            StudioToolRow {
                                ToolChip("تكبير", enabled = selectedElement != null) {
                                    updateSelected {
                                        it.copy(scale = (it.scale + 0.08f).coerceAtMost(6f))
                                    }
                                }
                                ToolChip("تصغير", enabled = selectedElement != null) {
                                    updateSelected {
                                        it.copy(scale = (it.scale - 0.08f).coerceAtLeast(0.15f))
                                    }
                                }
                                ToolChip("دوران ↺", enabled = selectedElement != null) {
                                    updateSelected { it.copy(rotation = it.rotation - 15f) }
                                }
                                ToolChip("دوران ↻", enabled = selectedElement != null) {
                                    updateSelected { it.copy(rotation = it.rotation + 15f) }
                                }
                            }
                            PanelLabel("إعادة الضبط")
                            StudioToolRow {
                                ToolChip("توسيط", enabled = selectedElement != null) {
                                    updateSelected { it.copy(x = 0.5f, y = 0.45f) }
                                }
                                ToolChip("تصفير الدوران", enabled = selectedElement != null) {
                                    updateSelected { it.copy(rotation = 0f) }
                                }
                            }
                        }
                        4 -> {
                            PanelLabel("طبقات النص (${elements.size})")
                            StudioToolRow {
                                elements.forEach { element ->
                                    ToolChip(
                                        label = "نص: " + element.text.ifBlank { "فارغ" }
                                            .take(12),
                                        selected = element.id == selectedId
                                    ) { selectedId = element.id }
                                }
                            }
                            PanelLabel("أدوات")
                            StudioToolRow {
                                ToolChip("نص جديد +") { addElement() }
                                ToolChip("نسخ", enabled = selectedElement != null) {
                                    duplicateSelected()
                                }
                                ToolChip(
                                    "حذف",
                                    enabled = selectedElement != null && elements.size > 1
                                ) { deleteSelected() }
                            }
                        }
                        5 -> {
                            PanelLabel("الخلفية")
                            StudioToolRow {
                                ToolChip("شفافة", selected = transparentBackground) {
                                    pushUndo()
                                    backgroundColor = 0x00000000
                                    backgroundImage = null
                                }
                                ToolChip("صورة من المعرض") {
                                    backgroundPicker.launch("image/*")
                                }
                                ToolChip("إزالة الصورة", enabled = backgroundImage != null) {
                                    pushUndo()
                                    backgroundImage = null
                                }
                            }
                            if (backgroundImage == null) {
                                PanelLabel("لون الخلفية")
                                ColorPaletteRow(
                                    selectedItem = if (transparentBackground) {
                                        null
                                    } else {
                                        backgroundColor
                                    },
                                    onPick = { color ->
                                        pushUndo()
                                        backgroundColor = color
                                    }
                                )
                            }
                        }
                        else -> {
                            PanelLabel("العلامة المائية")
                            SwitchField(
                                label = "تفعيل العلامة المائية",
                                checked = watermarkEnabled,
                                onCheckedChange = { checked ->
                                    pushUndo()
                                    watermarkEnabled = checked
                                }
                            )
                            if (watermarkEnabled) {
                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { value -> watermarkText = value },
                                    label = { Text("نص العلامة المائية") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                PanelLabel("لون العلامة")
                                ColorPaletteRow(
                                    selectedItem = watermarkColor,
                                    onPick = { color -> watermarkColor = color }
                                )
                                SliderField(
                                    "الشفافية", watermarkOpacity, 0.05f..1f,
                                    "${(watermarkOpacity * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        watermarkOpacity = value
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                                SliderField(
                                    "حجم الخط", watermarkSize, 16f..100f,
                                    watermarkSize.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed) {
                                            pushUndo()
                                            sliderPushed = true
                                        }
                                        watermarkSize = value
                                    },
                                    onValueChangeFinished = { sliderPushed = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolChip("تراجع", enabled = undoStack.isNotEmpty()) { undo() }
            ToolChip("إعادة", enabled = redoStack.isNotEmpty()) { redo() }
            ToolChip("مسح") { resetCanvas() }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF22D3EE))
                        )
                    )
                    .clickable {
                        val output = StickerCreatorEngine.render(
                            baseBitmap = null,
                            elements = elements.toList(),
                            backgroundColor = backgroundColor,
                            backgroundBitmap = backgroundImage,
                            watermark = activeWatermark ?: StickerWatermark()
                        )
                        onExport(output)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "تصدير",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAdvancedDialog && selectedElement != null) {
        TextElementDialog(
            initial = selectedElement,
            onDismiss = { showAdvancedDialog = false },
            onLiveChange = { updated ->
                val index = elements.indexOfFirst { it.id == updated.id }
                if (index >= 0) elements[index] = updated
            },
            onConfirm = { updated ->
                pushUndo()
                val index = elements.indexOfFirst { it.id == updated.id }
                if (index >= 0) elements[index] = updated
                showAdvancedDialog = false
            }
        )
    }
}
