package com.example.presentation.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.StickerPack
import com.example.data.repository.UserTemplateRepository
import com.example.creator.FontLibrary
import com.example.creator.STICKER_CANVAS_SIZE
import com.example.creator.STICKER_TEMPLATES
import com.example.creator.StickerAdjustments
import com.example.creator.StickerBlendMode
import com.example.creator.StickerCreatorEngine
import com.example.creator.StickerEditorMode
import com.example.creator.StickerFilter
import com.example.creator.StickerTemplateCategory
import com.example.creator.StickerTextAlign
import com.example.creator.StickerTextElement
import com.example.creator.StickerWatermark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EditorSnapshot(
    val base: Bitmap?,
    val drawLayer: Bitmap?,
    val background: Bitmap?,
    val elements: List<StickerTextElement>,
    val adjustments: StickerAdjustments,
    val backgroundColor: Int,
    val watermarkText: String,
    val watermarkColor: Int,
    val watermarkOpacity: Float,
    val watermarkSize: Float
)

private val TEXT_COLORS = listOf(
    AndroidColor.WHITE,
    AndroidColor.BLACK,
    0xFFFF4D6D.toInt(),
    0xFFFFD166.toInt(),
    0xFF06D6A0.toInt(),
    0xFF118AB2.toInt(),
    0xFFEF476F.toInt(),
    0xFF9B5DE5.toInt()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerEditorScreen(
    mode: StickerEditorMode,
    initialUri: String?,
    packs: List<StickerPack>,
    onNavigateBack: () -> Unit,
    onSaveSticker: (Bitmap, String, String, Long?, String?) -> Unit,
    studioImageId: String? = null,
    studioImageName: String? = null,
    onSaveStudioImage: ((String?, String, Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var baseRevision by remember { mutableStateOf(0) }
    var drawLayer by remember { mutableStateOf<Bitmap?>(null) }
    var drawRevision by remember { mutableStateOf(0) }
    var adjustments by remember { mutableStateOf(StickerAdjustments()) }
    var canvasBackgroundColor by remember { mutableStateOf(AndroidColor.TRANSPARENT) }
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var watermarkText by remember { mutableStateOf("") }
    var watermarkColor by remember { mutableStateOf(AndroidColor.WHITE) }
    var watermarkOpacity by remember { mutableStateOf(0.85f) }
    var watermarkSize by remember { mutableStateOf(32f) }
    var selectedFilter by remember { mutableStateOf<StickerFilter?>(null) }
    var elements by remember { mutableStateOf<List<StickerTextElement>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    var isLoading by remember { mutableStateOf(mode == StickerEditorMode.IMAGE) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var eraserMode by remember { mutableStateOf(false) }
    var eraserRadius by remember { mutableStateOf(24f) }
    var brushMode by remember { mutableStateOf(false) }
    var brushColor by remember { mutableStateOf(0xFFFF4D6D.toInt()) }
    var brushRadius by remember { mutableStateOf(16f) }
    var brushOpacity by remember { mutableStateOf(1f) }
    var showGrid by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }

    var showCropDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showStudioNameDialog by remember { mutableStateOf(false) }
    var selectedTemplateCategory by remember { mutableStateOf<StickerTemplateCategory?>(null) }
    var editingElement by remember { mutableStateOf<StickerTextElement?>(null) }
    var textDraftId by remember { mutableStateOf<Long?>(null) }
    var textOriginal by remember { mutableStateOf<StickerTextElement?>(null) }
    var inlineDraftId by remember { mutableStateOf<Long?>(null) }
    var inlineOriginal by remember { mutableStateOf<StickerTextElement?>(null) }
    var inlineUndoPushed by remember { mutableStateOf(false) }
    var showTemplateNameDialog by remember { mutableStateOf(false) }
    var templateToSave by remember { mutableStateOf<StickerTextElement?>(null) }

    val templateRepository = remember { UserTemplateRepository(context) }
    val userTemplates by templateRepository.templates.collectAsState()

    LaunchedEffect(Unit) {
        FontLibrary.initialize(context)
    }

    val undoStack = remember { mutableStateListOf<EditorSnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorSnapshot>() }

    fun snapshot() = EditorSnapshot(
        base = baseBitmap?.copy(Bitmap.Config.ARGB_8888, true),
        drawLayer = drawLayer?.copy(Bitmap.Config.ARGB_8888, true),
        background = backgroundBitmap?.copy(Bitmap.Config.ARGB_8888, true),
        elements = elements,
        adjustments = adjustments,
        backgroundColor = canvasBackgroundColor,
        watermarkText = watermarkText,
        watermarkColor = watermarkColor,
        watermarkOpacity = watermarkOpacity,
        watermarkSize = watermarkSize
    )

    fun pushUndo() {
        undoStack.add(snapshot())
        if (undoStack.size > 20) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(snapshot())
        baseBitmap = previous.base
        drawLayer = previous.drawLayer
        backgroundBitmap = previous.background
        elements = previous.elements
        adjustments = previous.adjustments
        canvasBackgroundColor = previous.backgroundColor
        watermarkText = previous.watermarkText
        watermarkColor = previous.watermarkColor
        watermarkOpacity = previous.watermarkOpacity
        watermarkSize = previous.watermarkSize
        selectedFilter = null
        selectedId = null
        inlineDraftId = null
        inlineOriginal = null
        inlineUndoPushed = false
        baseRevision++
        drawRevision++
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(snapshot())
        baseBitmap = next.base
        drawLayer = next.drawLayer
        backgroundBitmap = next.background
        elements = next.elements
        adjustments = next.adjustments
        canvasBackgroundColor = next.backgroundColor
        watermarkText = next.watermarkText
        watermarkColor = next.watermarkColor
        watermarkOpacity = next.watermarkOpacity
        watermarkSize = next.watermarkSize
        selectedFilter = null
        selectedId = null
        inlineDraftId = null
        inlineOriginal = null
        inlineUndoPushed = false
        baseRevision++
        drawRevision++
    }

    fun moveElement(id: Long, delta: Int) {
        val index = elements.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, elements.lastIndex)
        if (target == index) return
        pushUndo()
        val mutable = elements.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        elements = mutable
    }

    fun duplicateElement(element: StickerTextElement) {
        pushUndo()
        val copy = element.copy(
            id = System.nanoTime(),
            x = (element.x + 0.05f).coerceIn(0.04f, 0.96f),
            y = (element.y + 0.05f).coerceIn(0.04f, 0.96f)
        )
        elements = elements + copy
        selectedId = copy.id
    }

    fun toggleVisibility(element: StickerTextElement) {
        pushUndo()
        elements = elements.map {
            if (it.id == element.id) it.copy(visible = !it.visible) else it
        }
    }

    fun updateElement(updated: StickerTextElement) {
        elements = elements.map { if (it.id == updated.id) updated else it }
    }

    fun startInlineTextDraft() {
        pushUndo()
        val draft = StickerTextElement(text = "")
        elements = elements + draft
        selectedId = draft.id
        inlineDraftId = draft.id
        inlineOriginal = null
        inlineUndoPushed = true
        eraserMode = false
        brushMode = false
    }

    fun updateInlineElement(updated: StickerTextElement) {
        if (!inlineUndoPushed) {
            pushUndo()
            inlineUndoPushed = true
        }
        updateElement(updated)
    }

    fun cancelInlineEditing() {
        val original = inlineOriginal
        val draftId = inlineDraftId
        if (original != null) {
            elements = elements.map { if (it.id == original.id) original else it }
        } else if (draftId != null) {
            elements = elements.filterNot { it.id == draftId }
        }
        if (inlineUndoPushed && undoStack.isNotEmpty()) {
            undoStack.removeAt(undoStack.lastIndex)
        }
        selectedId = null
        inlineDraftId = null
        inlineOriginal = null
        inlineUndoPushed = false
    }

    fun deleteInlineElement(element: StickerTextElement) {
        if (inlineDraftId == element.id && inlineOriginal == null) {
            cancelInlineEditing()
            return
        }
        pushUndo()
        elements = elements.filterNot { it.id == element.id }
        selectedId = null
        inlineDraftId = null
        inlineOriginal = null
        inlineUndoPushed = false
    }

    fun openTextEditor(element: StickerTextElement?) {
        pushUndo()
        val target = element ?: StickerTextElement(text = "")
        if (element == null) {
            elements = elements + target
        }
        selectedId = target.id
        editingElement = target
        textDraftId = target.id
        textOriginal = target
        showTextDialog = true
        eraserMode = false
        brushMode = false
    }

    fun applyLiveText(updated: StickerTextElement) {
        elements = elements.map { if (it.id == updated.id) updated else it }
    }

    fun finishTextEditing(updated: StickerTextElement?, cancelled: Boolean) {
        val draftId = textDraftId
        if (cancelled) {
            val original = textOriginal
            val survivesAsDraft = original != null &&
                original.text.isNotBlank() &&
                inlineDraftId == draftId
            if (original != null && original.text.isNotBlank()) {
                elements = elements.map { if (it.id == original.id) original else it }
            } else if (draftId != null) {
                elements = elements.filterNot { it.id == draftId }
            }
            if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex)
            if (draftId != null && inlineDraftId == draftId && !survivesAsDraft) {
                if (inlineUndoPushed && undoStack.isNotEmpty()) {
                    undoStack.removeAt(undoStack.lastIndex)
                }
                inlineDraftId = null
                inlineOriginal = null
                inlineUndoPushed = false
                if (selectedId == draftId) selectedId = null
            } else if (!survivesAsDraft && draftId != null && selectedId == draftId) {
                selectedId = null
            }
        } else if (updated != null) {
            if (updated.text.isBlank()) {
                elements = elements.filterNot { it.id == updated.id }
                selectedId = null
                if (inlineDraftId == updated.id) {
                    inlineDraftId = null
                    inlineOriginal = null
                    inlineUndoPushed = false
                }
            } else {
                applyLiveText(updated)
                selectedId = updated.id
                if (inlineDraftId == updated.id) {
                    inlineDraftId = null
                    inlineOriginal = updated
                    inlineUndoPushed = false
                } else if (inlineOriginal?.id == updated.id) {
                    inlineOriginal = updated
                }
            }
        }
        showTextDialog = false
        editingElement = null
        textDraftId = null
        textOriginal = null
    }

    LaunchedEffect(selectedId) {
        val current = elements.find { it.id == selectedId }
        if (current == null) {
            inlineDraftId = null
            inlineOriginal = null
            inlineUndoPushed = false
        } else if (!current.isEmoji && current.id != inlineDraftId) {
            inlineOriginal = current
            inlineUndoPushed = false
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    StickerCreatorEngine.loadBitmap(context, selectedUri)
                }
                if (loaded != null) {
                    pushUndo()
                    backgroundBitmap = loaded
                } else {
                    Toast.makeText(
                        context,
                        "تعذر تحميل صورة الخلفية",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(initialUri, mode) {
        if (mode == StickerEditorMode.IMAGE && !initialUri.isNullOrBlank()) {
            isLoading = true
            loadError = null
            val loaded = withContext(Dispatchers.IO) {
                val source = StickerCreatorEngine.loadBitmap(context, Uri.parse(initialUri))
                source to source?.let { StickerCreatorEngine.cropToCanvas(it) }
            }
            sourceBitmap = loaded.first
            baseBitmap = loaded.second
            isLoading = false
            if (loaded.first == null) {
                loadError = "تعذر فتح الصورة. جرّب صورة أخرى من المعرض."
            } else {
                showCropDialog = true
            }
        } else {
            isLoading = false
            if (mode == StickerEditorMode.IMAGE) {
                loadError = "لم يتم اختيار صورة. ارجع واختر صورة من المعرض أو الكاميرا."
            }
        }
    }

    val pushUndoLatest = rememberUpdatedState<() -> Unit> { pushUndo() }
    val baseLatest = rememberUpdatedState(baseBitmap)
    val drawLayerLatest = rememberUpdatedState(drawLayer)
    val previewSizeLatest = rememberUpdatedState(previewSize)
    val elementsLatest = rememberUpdatedState(elements)
    val selectedIdLatest = rememberUpdatedState(selectedId)
    val eraserRadiusLatest = rememberUpdatedState(eraserRadius)
    val eraserModeLatest = rememberUpdatedState(eraserMode)
    val brushModeLatest = rememberUpdatedState(brushMode)
    val brushColorLatest = rememberUpdatedState(brushColor)
    val brushRadiusLatest = rememberUpdatedState(brushRadius)
    val brushOpacityLatest = rememberUpdatedState(brushOpacity)

    val gestureModifier = Modifier.pointerInput(eraserMode, brushMode, previewSize) {
        when {
            eraserModeLatest.value -> {
                detectDragGestures(
                    onDragStart = { offset ->
                        pushUndoLatest.value()
                        val bitmap = baseLatest.value
                        val size = previewSizeLatest.value
                        if (bitmap != null && size.width > 0 && size.height > 0) {
                            StickerCreatorEngine.erase(
                                bitmap,
                                offset.x / size.width * STICKER_CANVAS_SIZE,
                                offset.y / size.height * STICKER_CANVAS_SIZE,
                                eraserRadiusLatest.value
                            )
                            baseRevision++
                        }
                    },
                    onDrag = { change, _ ->
                        val bitmap = baseLatest.value
                        val size = previewSizeLatest.value
                        if (bitmap != null && size.width > 0 && size.height > 0) {
                            StickerCreatorEngine.erase(
                                bitmap,
                                change.position.x / size.width * STICKER_CANVAS_SIZE,
                                change.position.y / size.height * STICKER_CANVAS_SIZE,
                                eraserRadiusLatest.value
                            )
                            baseRevision++
                        }
                    }
                )
            }
            brushModeLatest.value -> {
                var activeLayer: Bitmap? = null
                var lastPoint = Offset.Zero
                detectDragGestures(
                    onDragStart = { offset ->
                        pushUndoLatest.value()
                        val size = previewSizeLatest.value
                        if (size.width > 0 && size.height > 0) {
                            val layer = drawLayerLatest.value ?: Bitmap.createBitmap(
                                STICKER_CANVAS_SIZE,
                                STICKER_CANVAS_SIZE,
                                Bitmap.Config.ARGB_8888
                            )
                            activeLayer = layer
                            drawLayer = layer
                            lastPoint = Offset(
                                offset.x / size.width * STICKER_CANVAS_SIZE,
                                offset.y / size.height * STICKER_CANVAS_SIZE
                            )
                            StickerCreatorEngine.drawBrushStroke(
                                layer,
                                lastPoint.x,
                                lastPoint.y,
                                lastPoint.x,
                                lastPoint.y,
                                brushColorLatest.value,
                                brushRadiusLatest.value,
                                brushOpacityLatest.value
                            )
                            drawRevision++
                        }
                    },
                    onDrag = { change, _ ->
                        val layer = activeLayer ?: return@detectDragGestures
                        val size = previewSizeLatest.value
                        if (size.width > 0 && size.height > 0) {
                            val next = Offset(
                                change.position.x / size.width * STICKER_CANVAS_SIZE,
                                change.position.y / size.height * STICKER_CANVAS_SIZE
                            )
                            StickerCreatorEngine.drawBrushStroke(
                                layer,
                                lastPoint.x,
                                lastPoint.y,
                                next.x,
                                next.y,
                                brushColorLatest.value,
                                brushRadiusLatest.value,
                                brushOpacityLatest.value
                            )
                            lastPoint = next
                            drawRevision++
                        }
                    }
                )
            }
            else -> {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val size = previewSizeLatest.value
                    if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
                    val hitId = StickerCreatorEngine.hitTestText(
                        elementsLatest.value,
                        down.position.x / size.width,
                        down.position.y / size.height
                    )
                    if (hitId == null) {
                        selectedId = null
                        return@awaitEachGesture
                    }
                    if (hitId != selectedIdLatest.value) {
                        selectedId = hitId
                        return@awaitEachGesture
                    }
                    val id = hitId
                    pushUndoLatest.value()
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val currentSize = previewSizeLatest.value
                        if (currentSize.width > 0 && currentSize.height > 0) {
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            elements = elementsLatest.value.map { element ->
                                if (element.id == id) {
                                    element.copy(
                                        x = (element.x + pan.x / currentSize.width).coerceIn(0.04f, 0.96f),
                                        y = (element.y + pan.y / currentSize.height).coerceIn(0.04f, 0.96f),
                                        scale = (element.scale * zoom).coerceIn(0.2f, 5f),
                                        rotation = element.rotation + rotation
                                    )
                                } else {
                                    element
                                }
                            }
                        }
                        event.changes.forEach { it.consume() }
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
        }
    }

    val canSave = !isLoading && (
        baseBitmap != null ||
            drawLayer != null ||
            backgroundBitmap != null ||
            watermarkText.isNotBlank() ||
            elements.isNotEmpty()
        )
    val selectedElement = elements.find { it.id == selectedId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "استوديو الملصقات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mode == StickerEditorMode.IMAGE) "ملصق من صورة" else "ملصق نصي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            when {
                                !studioImageId.isNullOrBlank() -> {
                                    scope.launch {
                                        val output = withContext(Dispatchers.Default) {
                                            StickerCreatorEngine.render(
                                                baseBitmap,
                                                drawLayer,
                                                elements,
                                                adjustments,
                                                backgroundColor = canvasBackgroundColor,
                                                backgroundBitmap = backgroundBitmap,
                                                watermark = StickerWatermark(
                                                    text = watermarkText,
                                                    color = watermarkColor,
                                                    opacity = watermarkOpacity,
                                                    size = watermarkSize
                                                )
                                            )
                                        }
                                        onSaveStudioImage?.invoke(
                                            studioImageId,
                                            studioImageName ?: "ملصق معدل",
                                            output
                                        )
                                        Toast.makeText(
                                            context,
                                            "جارٍ حفظ تعديلات الصورة...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                onSaveStudioImage != null -> showStudioNameDialog = true
                                else -> showSaveDialog = true
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "حفظ الملصق")
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .onSizeChanged { previewSize = it }
                    .then(gestureModifier)
                    .testTag("sticker_editor_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val checkerOffset = (baseRevision + drawRevision) % 2
                    val cells = 16
                    val cell = size.width / cells
                    for (row in 0 until cells) {
                        for (column in 0 until cells) {
                            drawRect(
                                color = if ((row + column + checkerOffset) % 2 == 0) {
                                    Color(0xFFF7F7F7)
                                } else {
                                    Color(0xFFE2E2E2)
                                },
                                topLeft = Offset(column * cell, row * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }

                    val native = drawContext.canvas.nativeCanvas
                    native.save()
                    native.scale(
                        size.width / STICKER_CANVAS_SIZE,
                        size.height / STICKER_CANVAS_SIZE
                    )
                    StickerCreatorEngine.drawComposite(
                        native,
                        baseBitmap,
                        drawLayer,
                        elements,
                        adjustments,
                        STICKER_CANVAS_SIZE,
                        canvasBackgroundColor,
                        backgroundBitmap,
                        StickerWatermark(
                            text = watermarkText,
                            color = watermarkColor,
                            opacity = watermarkOpacity,
                            size = watermarkSize
                        )
                    )
                    selectedElement?.let { element ->
                        val bounds = StickerCreatorEngine.textBounds(
                            element,
                            STICKER_CANVAS_SIZE
                        )
                        if (bounds != null) {
                            native.save()
                            native.translate(
                                element.x * STICKER_CANVAS_SIZE,
                                element.y * STICKER_CANVAS_SIZE
                            )
                            native.rotate(element.rotation)
                            native.scale(element.scale, element.scale)
                            val selectionPaint = android.graphics.Paint(
                                android.graphics.Paint.ANTI_ALIAS_FLAG
                            ).apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 2.2f
                                color = AndroidColor.argb(235, 34, 211, 238)
                                pathEffect = android.graphics.DashPathEffect(
                                    floatArrayOf(9f, 7f),
                                    0f
                                )
                            }
                            native.drawRect(bounds, selectionPaint)
                            val handlePaint = android.graphics.Paint(
                                android.graphics.Paint.ANTI_ALIAS_FLAG
                            ).apply {
                                style = android.graphics.Paint.Style.FILL
                                color = AndroidColor.WHITE
                            }
                            val handleRadius = 5.5f
                            native.drawCircle(bounds.left, bounds.top, handleRadius, handlePaint)
                            native.drawCircle(bounds.right, bounds.top, handleRadius, handlePaint)
                            native.drawCircle(bounds.left, bounds.bottom, handleRadius, handlePaint)
                            native.drawCircle(bounds.right, bounds.bottom, handleRadius, handlePaint)
                            native.drawCircle(
                                bounds.centerX(),
                                bounds.top - 18f,
                                handleRadius + 1.5f,
                                handlePaint
                            )
                            native.restore()
                        }
                    }
                    native.restore()

                    if (showGrid) {
                        val step = size.width / 8f
                        for (index in 1 until 8) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(step * index, 0f),
                                end = Offset(step * index, size.height),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(0f, step * index),
                                end = Offset(size.width, step * index),
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (loadError != null) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                if (!isLoading && loadError == null && elements.isEmpty() && baseBitmap == null && drawLayer == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ابدأ بإضافة نص أو إيموجي",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "أو اختر قالبًا جاهزًا من الأسفل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (eraserMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "وضع الممحاة — مرّر إصبعك على الصورة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (brushMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "وضع الفرشاة — ارسم بحرية فوق الملصق",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val inlineElement = selectedElement?.takeIf { !it.isEmoji }
                if (inlineElement != null) {
                    InlineTextEditorCard(
                        element = inlineElement,
                        onUpdate = { updateInlineElement(it) },
                        onAdvanced = { openTextEditor(inlineElement) },
                        onCancel = { cancelInlineEditing() },
                        onDelete = { deleteInlineElement(inlineElement) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { startInlineTextDraft() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_add_text"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة نص", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showEmojiDialog = true
                            eraserMode = false
                            brushMode = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_add_emoji"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.EmojiEmotions, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيموجي", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "خلفية الملصق",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            pushUndo()
                            canvasBackgroundColor = AndroidColor.TRANSPARENT
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (canvasBackgroundColor == AndroidColor.TRANSPARENT) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("شفافة", fontSize = 11.sp)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TEXT_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (canvasBackgroundColor == color) 3.dp else 1.dp,
                                    color = if (canvasBackgroundColor == color) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    pushUndo()
                                    canvasBackgroundColor = color
                                }
                                .testTag("canvas_background_${color}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { backgroundPicker.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("background_gallery_button"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("خلفية من المعرض", fontSize = 11.sp)
                    }
                    backgroundBitmap?.let { preview ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "معاينة الخلفية",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                pushUndo()
                                backgroundBitmap = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("مسح", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "العلامة المائية",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (watermarkText.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        pushUndo()
                                        watermarkText = ""
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 2.dp
                                    )
                                ) {
                                    Text("مسح", fontSize = 11.sp)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = {
                                if (watermarkText.isBlank() && it.isNotBlank()) pushUndo()
                                watermarkText = it
                            },
                            label = { Text("نص العلامة المائية") },
                            placeholder = { Text("@اسمك") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watermark_text_input")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TEXT_COLORS.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .border(
                                            width = if (color == watermarkColor) 3.dp else 1.dp,
                                            color = if (color == watermarkColor) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            pushUndo()
                                            watermarkColor = color
                                        }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الشفافية", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { watermarkOpacity = it },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                "${(watermarkOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الحجم", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = watermarkSize,
                                onValueChange = { watermarkSize = it },
                                valueRange = 12f..96f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                "${watermarkSize.toInt()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (mode == StickerEditorMode.IMAGE || baseBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (sourceBitmap != null) showCropDialog = true },
                            enabled = sourceBitmap != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("قص", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val current = baseBitmap ?: return@OutlinedButton
                                pushUndo()
                                baseBitmap = StickerCreatorEngine.rotate90(current)
                                drawLayer = drawLayer?.let { StickerCreatorEngine.rotate90(it) }
                                baseRevision++
                                drawRevision++
                            },
                            enabled = baseBitmap != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("تدوير", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val current = baseBitmap ?: return@OutlinedButton
                                pushUndo()
                                scope.launch {
                                    val working = current.copy(Bitmap.Config.ARGB_8888, true)
                                        ?: return@launch
                                    val removed = withContext(Dispatchers.Default) {
                                        StickerCreatorEngine.removeSolidBackground(working, 38)
                                    }
                                    baseBitmap = working
                                    baseRevision++
                                    Toast.makeText(
                                        context,
                                        if (removed > 0) "تمت إزالة الخلفية ($removed بكسل)" else "لم يتم العثور على خلفية موحدة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = baseBitmap != null,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("خلفية", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                eraserMode = !eraserMode
                                if (eraserMode) {
                                    brushMode = false
                                    selectedId = null
                                }
                            },
                            enabled = baseBitmap != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(if (eraserMode) "إيقاف" else "ممحاة", fontSize = 11.sp)
                        }
                    }

                    if (eraserMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("حجم الممحاة", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = eraserRadius,
                                onValueChange = { eraserRadius = it },
                                valueRange = 8f..80f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text("${eraserRadius.toInt()}px", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val current = baseBitmap ?: return@OutlinedButton
                            pushUndo()
                            baseBitmap = StickerCreatorEngine.flip(current, true)
                            drawLayer = drawLayer?.let { StickerCreatorEngine.flip(it, true) }
                            baseRevision++
                            drawRevision++
                        },
                        enabled = baseBitmap != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("قلب أفقي", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val current = baseBitmap ?: return@OutlinedButton
                            pushUndo()
                            baseBitmap = StickerCreatorEngine.flip(current, false)
                            drawLayer = drawLayer?.let { StickerCreatorEngine.flip(it, false) }
                            baseRevision++
                            drawRevision++
                        },
                        enabled = baseBitmap != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("قلب عمودي", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            brushMode = !brushMode
                            if (brushMode) {
                                eraserMode = false
                                selectedId = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (brushMode) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (brushMode) "إيقاف" else "فرشاة", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { showGrid = !showGrid },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (showGrid) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (showGrid) "الشبكة" else "شبكة", fontSize = 10.sp)
                    }
                }

                if (brushMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("لون الفرشاة", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TEXT_COLORS.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(color))
                                            .border(
                                                width = if (color == brushColor) 3.dp else 1.dp,
                                                color = if (color == brushColor) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable { brushColor = color }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الحجم", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = brushRadius,
                                    onValueChange = { brushRadius = it },
                                    valueRange = 4f..48f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                Text("${brushRadius.toInt()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الشفافية", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = brushOpacity,
                                    onValueChange = { brushOpacity = it },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )
                                Text("${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAdjustments = !showAdjustments },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (showAdjustments) "إخفاء ضبط الألوان" else "ضبط الألوان والفلاتر", fontSize = 12.sp)
                }

                if (showAdjustments) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AdjustmentsCard(
                        adjustments = adjustments,
                        selectedFilter = selectedFilter,
                        onFilterSelected = { filter ->
                            selectedFilter = filter
                            adjustments = filter.adjustments
                        },
                        onAdjustmentChange = {
                            selectedFilter = null
                            adjustments = it
                        },
                        onReset = {
                            selectedFilter = StickerFilter.NONE
                            adjustments = StickerAdjustments()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "قوالب سريعة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedTemplateCategory = null },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedTemplateCategory == null) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("الكل", fontSize = 11.sp)
                    }
                    StickerTemplateCategory.entries.forEach { category ->
                        OutlinedButton(
                            onClick = { selectedTemplateCategory = category },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedTemplateCategory == category) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("${category.emoji} ${category.label}", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STICKER_TEMPLATES
                        .filter { selectedTemplateCategory == null || it.category == selectedTemplateCategory }
                        .forEach { template ->
                            OutlinedButton(
                                onClick = {
                                    val element = StickerTextElement(
                                        text = template.text,
                                        color = template.color,
                                        strokeColor = template.strokeColor,
                                        fontSize = template.fontSize,
                                        strokeWidth = 7f,
                                        shadow = true,
                                        fontStyle = template.fontStyle,
                                        decoration = template.decoration
                                    )
                                    pushUndo()
                                    elements = elements + element
                                    selectedId = element.id
                                    eraserMode = false
                                    brushMode = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 4.dp
                                )
                            ) {
                                Text(template.label, fontSize = 12.sp)
                            }
                        }
                }

                if (userTemplates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "قوالبي المحفوظة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        userTemplates.forEach { template ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        modifier = Modifier
                                            .clickable {
                                                val element = template.toElement()
                                                pushUndo()
                                                elements = elements + element
                                                selectedId = element.id
                                                eraserMode = false
                                                brushMode = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = template.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = template.text.take(14),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(
                                        onClick = { templateRepository.delete(template.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف القالب",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (elements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "عناصر الملصق",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        elements.forEach { element ->
                            OutlinedButton(
                                onClick = {
                                    selectedId = element.id
                                    eraserMode = false
                                    brushMode = false
                                },
                                modifier = Modifier.testTag("select_element_${element.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (element.id == selectedId) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 2.dp
                                )
                            ) {
                                Text(
                                    text = if (element.isEmoji) {
                                        element.text
                                    } else {
                                        element.text.take(10).ifBlank { "نص" }
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تراجع", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إعادة", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val currentBase = baseBitmap
                            if (currentBase == null && sourceBitmap == null) {
                                selectedId = null
                                return@OutlinedButton
                            }
                            pushUndo()
                            if (sourceBitmap != null) {
                                baseBitmap = StickerCreatorEngine.cropToCanvas(sourceBitmap!!)
                            }
                            baseRevision++
                        },
                        enabled = sourceBitmap != null,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("editor_reset_image"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إعادة ضبط", fontSize = 12.sp)
                    }
                }

                if (selectedElement != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ElementControlsCard(
                        element = selectedElement,
                        onUpdate = { updateElement(it) },
                        onEditText = { openTextEditor(selectedElement) },
                        onDelete = {
                            pushUndo()
                            elements = elements.filterNot { it.id == selectedElement.id }
                            selectedId = null
                        },
                        onMoveForward = { moveElement(selectedElement.id, 1) },
                        onMoveBackward = { moveElement(selectedElement.id, -1) },
                        onDuplicate = { duplicateElement(selectedElement) },
                        onToggleVisibility = { toggleVisibility(selectedElement) },
                        onSaveAsTemplate = {
                            templateToSave = selectedElement
                            showTemplateNameDialog = true
                        },
                        onBlendModeChange = { mode ->
                            pushUndo()
                            updateElement(selectedElement.copy(blendMode = mode))
                        }
                    )
                }
            }
        }
    }

    if (showCropDialog && sourceBitmap != null) {
        CropStickerDialog(
            source = sourceBitmap!!,
            onDismiss = { showCropDialog = false },
            onApply = { zoom, offsetX, offsetY ->
                pushUndo()
                baseBitmap = StickerCreatorEngine.cropToCanvas(
                    sourceBitmap!!,
                    zoom,
                    offsetX,
                    offsetY
                )
                baseRevision++
                showCropDialog = false
            }
        )
    }

    if (showEmojiDialog) {
        EmojiPickerDialog(
            onDismiss = { showEmojiDialog = false },
            onPick = { emoji ->
                val element = StickerTextElement(
                    text = emoji,
                    isEmoji = true,
                    fontSize = 102f,
                    strokeWidth = 0f,
                    shadow = false
                )
                pushUndo()
                elements = elements + element
                selectedId = element.id
                showEmojiDialog = false
            }
        )
    }

    if (showTextDialog) {
        TextElementDialog(
            initial = editingElement,
            onDismiss = { finishTextEditing(null, cancelled = true) },
            onLiveChange = { applyLiveText(it) },
            onConfirm = { updated -> finishTextEditing(updated, cancelled = false) }
        )
    }

    if (showStudioNameDialog) {
        StudioImageNameDialog(
            title = "حفظ في الاستوديو",
            initialName = studioImageName ?: "ملصق نصي",
            onDismiss = { showStudioNameDialog = false },
            onConfirm = { name ->
                showStudioNameDialog = false
                scope.launch {
                    val output = withContext(Dispatchers.Default) {
                        StickerCreatorEngine.render(
                            baseBitmap,
                            drawLayer,
                            elements,
                            adjustments,
                            backgroundColor = canvasBackgroundColor,
                            backgroundBitmap = backgroundBitmap,
                            watermark = StickerWatermark(
                                text = watermarkText,
                                color = watermarkColor,
                                opacity = watermarkOpacity,
                                size = watermarkSize
                            )
                        )
                    }
                    onSaveStudioImage?.invoke(null, name, output)
                    Toast.makeText(
                        context,
                        "جارٍ حفظ الملصق في الاستوديو...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    if (showTemplateNameDialog && templateToSave != null) {
        StudioImageNameDialog(
            title = "حفظ القالب",
            initialName = "قالب ${userTemplates.size + 1}",
            confirmLabel = "حفظ القالب",
            description = "سيُحفظ النص مع الخط والتأثير والزخرفة، ويمكنك إعادة استخدامه وتعديله لاحقًا.",
            onDismiss = {
                showTemplateNameDialog = false
                templateToSave = null
            },
            onConfirm = { name ->
                templateToSave?.let { element ->
                    templateRepository.save(element, name)
                    Toast.makeText(context, "تم حفظ القالب", Toast.LENGTH_SHORT).show()
                }
                showTemplateNameDialog = false
                templateToSave = null
            }
        )
    }

    if (showSaveDialog) {
        SaveStickerDialog(
            packs = packs,
            onDismiss = { showSaveDialog = false },
            onSave = { name, emoji, packId, newPackName ->
                showSaveDialog = false
                scope.launch {
                    val output = withContext(Dispatchers.Default) {
                        StickerCreatorEngine.render(
                            baseBitmap,
                            drawLayer,
                            elements,
                            adjustments,
                            backgroundColor = canvasBackgroundColor,
                            backgroundBitmap = backgroundBitmap,
                            watermark = StickerWatermark(
                                text = watermarkText,
                                color = watermarkColor,
                                opacity = watermarkOpacity,
                                size = watermarkSize
                            )
                        )
                    }
                    onSaveSticker(output, name, emoji, packId, newPackName)
                    Toast.makeText(context, "جارٍ حفظ الملصق وتحويله...", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun InlineTextEditorCard(
    element: StickerTextElement,
    onUpdate: (StickerTextElement) -> Unit,
    onAdvanced: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_text_editor"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "تعديل النص",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف النص",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = element.text,
                onValueChange = { onUpdate(element.copy(text = it)) },
                label = { Text("النص") },
                placeholder = { Text("اكتب عبارة الملصق...") },
                minLines = 1,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inline_text_input")
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الحجم", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = element.fontSize,
                    onValueChange = { onUpdate(element.copy(fontSize = it)) },
                    valueRange = 16f..150f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    "${element.fontSize.toInt()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("اللون", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TEXT_COLORS.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (color == element.color) 3.dp else 1.dp,
                                color = if (color == element.color) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                            .clickable { onUpdate(element.copy(color = color)) }
                    )
                }
            }

            Text("المحاذاة", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerTextAlign.entries.forEach { align ->
                    OutlinedButton(
                        onClick = { onUpdate(element.copy(textAlign = align)) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (element.textAlign == align) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = align.label,
                            fontSize = 11.sp,
                            fontWeight = if (element.textAlign == align) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نص مائل",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = element.italic,
                    onCheckedChange = { onUpdate(element.copy(italic = it)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("إلغاء", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onAdvanced,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("inline_advanced_options"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("خيارات متقدمة", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AdjustmentsCard(
    adjustments: StickerAdjustments,
    selectedFilter: StickerFilter?,
    onFilterSelected: (StickerFilter) -> Unit,
    onAdjustmentChange: (StickerAdjustments) -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ضبط الألوان والفلاتر",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("إعادة ضبط", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickerFilter.entries.forEach { filter ->
                    OutlinedButton(
                        onClick = { onFilterSelected(filter) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (filter == selectedFilter) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(filter.label, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AdjustmentSlider(
                label = "السطوع",
                value = adjustments.brightness,
                range = -100f..100f,
                onValueChange = { onAdjustmentChange(adjustments.copy(brightness = it)) }
            )
            AdjustmentSlider(
                label = "التباين",
                value = adjustments.contrast * 100f,
                range = -100f..100f,
                onValueChange = { onAdjustmentChange(adjustments.copy(contrast = it / 100f)) },
                displayValue = "${(adjustments.contrast * 100).toInt()}"
            )
            AdjustmentSlider(
                label = "التشبع",
                value = adjustments.saturation,
                range = 0f..2f,
                onValueChange = { onAdjustmentChange(adjustments.copy(saturation = it)) },
                displayValue = "${(adjustments.saturation * 100).toInt()}%"
            )
            AdjustmentSlider(
                label = "تدرج اللون",
                value = adjustments.hue,
                range = -180f..180f,
                onValueChange = { onAdjustmentChange(adjustments.copy(hue = it)) }
            )
            AdjustmentSlider(
                label = "الدفء",
                value = adjustments.warmth,
                range = -1f..1f,
                onValueChange = { onAdjustmentChange(adjustments.copy(warmth = it)) },
                displayValue = "${(adjustments.warmth * 100).toInt()}"
            )
            AdjustmentSlider(
                label = "التظليل",
                value = adjustments.vignette,
                range = 0f..1f,
                onValueChange = { onAdjustmentChange(adjustments.copy(vignette = it)) },
                displayValue = "${(adjustments.vignette * 100).toInt()}%"
            )
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(68.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = displayValue ?: value.toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun ElementControlsCard(
    element: StickerTextElement,
    onUpdate: (StickerTextElement) -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
    onMoveForward: () -> Unit,
    onMoveBackward: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onBlendModeChange: (StickerBlendMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (element.isEmoji) "إيموجي" else "نص: ${element.text.take(24)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!element.isEmoji) {
                    OutlinedButton(
                        onClick = onEditText,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 2.dp
                        )
                    ) {
                        Text("تعديل", fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف العنصر",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onMoveBackward,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("للخلف", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onMoveForward,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("للأمام", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onDuplicate,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("نسخ", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onToggleVisibility,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(if (element.visible) "إخفاء" else "إظهار", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onSaveAsTemplate,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("حفظ كقالب", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("دمج الطبقة", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StickerBlendMode.entries.forEach { mode ->
                    OutlinedButton(
                        onClick = { onBlendModeChange(mode) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (mode == element.blendMode) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(mode.label, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("الحجم", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = element.fontSize,
                onValueChange = { onUpdate(element.copy(fontSize = it)) },
                valueRange = if (element.isEmoji) 48f..200f else 16f..160f
            )

            Text("الدوران", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = element.rotation,
                onValueChange = { onUpdate(element.copy(rotation = it)) },
                valueRange = -180f..180f
            )

            Text("الشفافية", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = element.opacity,
                onValueChange = { onUpdate(element.copy(opacity = it)) },
                valueRange = 0.1f..1f
            )

            if (!element.isEmoji) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اللون", style = MaterialTheme.typography.bodySmall)
                    TEXT_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (color == element.color) 2.dp else 1.dp,
                                    color = if (color == element.color) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .clickable { onUpdate(element.copy(color = color)) }
                        )
                    }
                }
            }
        }
    }
}
