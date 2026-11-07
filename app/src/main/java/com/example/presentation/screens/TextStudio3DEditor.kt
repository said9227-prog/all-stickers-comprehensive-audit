package com.example.presentation.screens

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.creator.FontLibrary
import com.example.creator.StickerFontLanguage
import com.example.creator.StickerWatermark
import com.example.creator.stickerFontsFor
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.texture.ImageTexture
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.example.core.model.StudioSticker
import com.example.core.model.StudioStickerType
import com.example.data.repository.StudioStickerRepository
import com.example.media.AnimatedGifEncoder
import com.example.media.GifFrame
import com.example.media.Mp4Exporter
import java.io.File
import java.io.OutputStream
import kotlin.math.sin

    private val STUDIO_3D_SECTIONS = listOf(
        "الإطارات",
        "الطبقات",
        "النص",
        "الخط",
        "التأثيرات",
        "الحركة",
        "الخامة",
        "القاعدة",
        "الإضاءة",
        "الخلفية",
        "التوقيع",
        "النموذج",
        "الهندسة",
        "الكاميرا",
        "الأنيميشن",
        "المشروع",
        "الملصقات"
    )

@Composable
internal fun TextStudio3DEditor(
    onExport: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FontLibrary.initialize(context)

    val preferences = remember {
        context.getSharedPreferences("sticker_settings", android.content.Context.MODE_PRIVATE)
    }
    val initialAutoRotate = remember {
        preferences.getBoolean("autoRotate3D", true)
    }
    val initialStyle = remember {
        runCatching {
            MetalStyle.valueOf(
                preferences.getString("last3dStyle", MetalStyle.GOLD.name) ?: MetalStyle.GOLD.name
            )
        }.getOrDefault(MetalStyle.GOLD)
    }

    var layers by remember {
        mutableStateOf(
            listOf(
                Text3DLayer(
                    id = 1L,
                    text = "ملصق 3D",
                    fontId = "cairo",
                    style = initialStyle
                )
            )
        )
    }
    var selectedLayerId by remember { mutableStateOf<Long?>(1L) }
    var nextLayerId by remember { mutableLongStateOf(2L) }
    var metalStyle by remember { mutableStateOf(initialStyle) }
    var shapeStyle by remember { mutableStateOf(ShapeStyle.SLAB) }
    var autoRotate by remember { mutableStateOf(initialAutoRotate) }
    var manualRotation by remember { mutableFloatStateOf(0f) }
    var modelFile by remember { mutableStateOf<File?>(null) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    var modelOffsetX by remember { mutableFloatStateOf(1.65f) }
    var modelOffsetY by remember { mutableFloatStateOf(-0.35f) }
    var modelOffsetZ by remember { mutableFloatStateOf(0f) }
    var modelRotationX by remember { mutableFloatStateOf(0f) }
    var modelRotationY by remember { mutableFloatStateOf(0f) }
    var modelRotationZ by remember { mutableFloatStateOf(0f) }
    var modelScale by remember { mutableFloatStateOf(1f) }

    // --- Timeline / keyframes (Phase 4) ---
    val keyframes = remember { mutableStateListOf<StudioKeyframe>() }
    var timelineDurationMs by remember { mutableFloatStateOf(5000f) }
    var timelineSpeed by remember { mutableFloatStateOf(1f) }
    var timelinePlaying by remember { mutableStateOf(false) }
    var timelineLoop by remember { mutableStateOf(true) }
    var timelineReverse by remember { mutableStateOf(false) }
    var timelineEasing by remember { mutableStateOf(AnimationEasing.EASE_IN_OUT) }
    var timelineStartNanos by remember { mutableLongStateOf(0L) }
    var timelineManualMs by remember { mutableFloatStateOf(0f) }
    var timelinePlayheadMs by remember { mutableLongStateOf(0L) }

    var baseX by remember { mutableFloatStateOf(0f) }
    var baseY by remember { mutableFloatStateOf(0f) }
    var baseZ by remember { mutableFloatStateOf(0f) }
    var baseRotationX by remember { mutableFloatStateOf(0f) }
    var baseRotationY by remember { mutableFloatStateOf(0f) }
    var baseRotationZ by remember { mutableFloatStateOf(0f) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var moveTogether by remember { mutableStateOf(true) }
    var unifiedColor by remember { mutableStateOf(false) }
    var baseColorOverride by remember { mutableStateOf<Int?>(null) }

    var lightMode by remember { mutableStateOf(LightMode.ALL_SIDES) }
    var lightAngle by remember { mutableFloatStateOf(45f) }
    var lightElevation by remember { mutableFloatStateOf(35f) }

    var backgroundMode by remember { mutableStateOf(BackgroundMode.TRANSPARENT) }
    var backgroundColor by remember { mutableStateOf(0xFF10152E.toInt()) }
    var backgroundImage by remember { mutableStateOf<Bitmap?>(null) }

    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("All Stickers") }
    var watermarkColor by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.55f) }

    var dragMode by remember { mutableStateOf(false) }
    var moveTarget3D by remember { mutableStateOf(0) }
    var sliderPushed3D by remember { mutableStateOf(false) }
    var sectionIndex by rememberSaveable { mutableStateOf(0) }
    var fontLanguage3D by remember { mutableStateOf(StickerFontLanguage.ARABIC) }
    var typingUndoKey by remember { mutableStateOf<Long?>(null) }

    // --- Offline sticker catalog (bundled inside the app, no internet) ---
    val stickerRepository = StudioStickerRepository(context)
    var stickerSearch by remember { mutableStateOf("") }
    var stickerCategoryFilter by remember { mutableStateOf<String?>(null) }

    // --- Camera path recording (تحريك الكاميرا عبر مسارات) ---
    val cameraKeyframes = remember { mutableStateListOf<StudioCameraKeyframe>() }
    var cameraPathActive by remember { mutableStateOf(false) }
    var cameraPathPreset by remember { mutableStateOf<CameraPathPreset?>(null) }
    var cameraPathRecording by remember { mutableStateOf(false) }
    var cameraRecordTickMs by remember { mutableLongStateOf(0L) }
    var sceneViewRef by remember { mutableStateOf<SceneView?>(null) }

    // --- Finger-motion recording for 3D text / model keyframes ---
    val modelKeyframes = remember { mutableStateListOf<StudioKeyframe>() }
    var recordMotion by remember { mutableStateOf(false) }

    // --- Export state ---
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    val filteredStickers: List<StudioSticker> = stickerRepository.allStickers.filter { sticker ->
        val matchesCategory = stickerCategoryFilter == null || sticker.category == stickerCategoryFilter
        val matchesSearch = stickerSearch.isBlank() ||
            sticker.name.contains(stickerSearch, ignoreCase = true) ||
            sticker.tags.any { it.contains(stickerSearch, ignoreCase = true) }
        matchesCategory && matchesSearch
    }

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId } ?: layers.firstOrNull()

    val frames = remember {
        mutableStateListOf(
            Text3DFrame(
                id = 1L,
                name = "الإطار 1",
                locked = false,
                visible = true,
                snapshot = emptyStudioSnapshot(metalStyle = initialStyle)
            )
        )
    }
    var activeFrameId by remember { mutableLongStateOf(1L) }
    var nextFrameId by remember { mutableLongStateOf(2L) }
    val activeFrame = frames.firstOrNull { it.id == activeFrameId } ?: frames.first()

    val undoStack = remember { mutableStateListOf<StudioSnapshot>() }
    val redoStack = remember { mutableStateListOf<StudioSnapshot>() }

    fun currentSnapshot() = StudioSnapshot(
        layers = layers,
        selectedLayerId = selectedLayerId,
        metalStyle = metalStyle,
        shapeStyle = shapeStyle,
        autoRotate = autoRotate,
        manualRotation = manualRotation,
        baseX = baseX,
        baseY = baseY,
        baseZ = baseZ,
        baseRotationX = baseRotationX,
        baseRotationY = baseRotationY,
        baseRotationZ = baseRotationZ,
        baseScale = baseScale,
        moveTogether = moveTogether,
        unifiedColor = unifiedColor,
        baseColorOverride = baseColorOverride,
        lightMode = lightMode,
        lightAngle = lightAngle,
        lightElevation = lightElevation,
        backgroundMode = backgroundMode,
        backgroundColor = backgroundColor,
        backgroundImage = backgroundImage,
        watermarkEnabled = watermarkEnabled,
        watermarkText = watermarkText,
        watermarkColor = watermarkColor,
        watermarkOpacity = watermarkOpacity
    )

    fun applySnapshot(snapshot: StudioSnapshot) {
        layers = snapshot.layers
        selectedLayerId = snapshot.selectedLayerId
        nextLayerId = maxOf(nextLayerId, (snapshot.layers.maxOfOrNull { it.id } ?: 0L) + 1L)
        metalStyle = snapshot.metalStyle
        shapeStyle = snapshot.shapeStyle
        autoRotate = snapshot.autoRotate
        manualRotation = snapshot.manualRotation
        baseX = snapshot.baseX
        baseY = snapshot.baseY
        baseZ = snapshot.baseZ
        baseRotationX = snapshot.baseRotationX
        baseRotationY = snapshot.baseRotationY
        baseRotationZ = snapshot.baseRotationZ
        baseScale = snapshot.baseScale
        moveTogether = snapshot.moveTogether
        unifiedColor = snapshot.unifiedColor
        baseColorOverride = snapshot.baseColorOverride
        lightMode = snapshot.lightMode
        lightAngle = snapshot.lightAngle
        lightElevation = snapshot.lightElevation
        backgroundMode = snapshot.backgroundMode
        backgroundColor = snapshot.backgroundColor
        backgroundImage = snapshot.backgroundImage
        watermarkEnabled = snapshot.watermarkEnabled
        watermarkText = snapshot.watermarkText
        watermarkColor = snapshot.watermarkColor
        watermarkOpacity = snapshot.watermarkOpacity
    }

    fun storeActiveFrame() {
        val index = frames.indexOfFirst { it.id == activeFrameId }
        if (index >= 0) {
            frames[index] = frames[index].copy(snapshot = currentSnapshot())
        }
    }

    fun pushUndo() {
        storeActiveFrame()
        val snapshot = frames.firstOrNull { it.id == activeFrameId }?.snapshot ?: currentSnapshot()
        if (undoStack.lastOrNull() != snapshot) {
            undoStack.add(snapshot)
            if (undoStack.size > 80) undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.add(currentSnapshot())
        applySnapshot(snapshot)
        storeActiveFrame()
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.add(currentSnapshot())
        applySnapshot(snapshot)
        storeActiveFrame()
    }

    fun updateLayer(id: Long, record: Boolean = true, block: (Text3DLayer) -> Text3DLayer) {
        if (record) pushUndo()
        layers = layers.map { if (it.id == id) block(it) else it }
    }

    fun updateSelected(record: Boolean = true, block: (Text3DLayer) -> Text3DLayer) {
        val id = selectedLayer?.id ?: return
        updateLayer(id, record, block)
    }

    fun addLayer(arabic: Boolean) {
        pushUndo()
        val id = nextLayerId
        nextLayerId += 1L
        val layer = Text3DLayer(
            id = id,
            text = if (arabic) "نص عربي" else "Text 3D",
            fontId = if (arabic) "cairo" else "anton",
            style = metalStyle,
            offsetX = 0.12f * (layers.size % 4),
            offsetY = 0.18f * (layers.size % 3),
            offsetZ = 0.05f * layers.size
        )
        layers = layers + layer
        selectedLayerId = id
    }

    fun addImageLayer(bitmap: Bitmap) {
        pushUndo()
        val id = nextLayerId
        nextLayerId += 1L
        layers = layers + Text3DLayer(
            id = id,
            text = "",
            fontId = "cairo",
            style = metalStyle,
            imageBitmap = bitmap,
            offsetY = 0.2f
        )
        selectedLayerId = id
    }

    fun duplicateSelected() {
        val current = selectedLayer ?: return
        pushUndo()
        val id = nextLayerId
        nextLayerId += 1L
        val index = layers.indexOfFirst { it.id == current.id }
        val copy = current.copy(
            id = id,
            text = current.text + " 2",
            offsetX = current.offsetX + 0.22f,
            offsetZ = current.offsetZ + 0.05f
        )
        layers = layers.toMutableList().apply {
            add((index + 1).coerceAtMost(size), copy)
        }
        selectedLayerId = id
    }

    fun deleteSelected() {
        val current = selectedLayer ?: return
        if (layers.size <= 1) {
            Toast.makeText(context, "يجب إبقاء طبقة واحدة على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        pushUndo()
        val index = layers.indexOfFirst { it.id == current.id }
        if (index >= 0) layers = layers.toMutableList().apply { removeAt(index) }
        selectedLayerId = layers.getOrNull(index)?.id ?: layers.lastOrNull()?.id
    }

    fun moveSelectedLayer(offset: Int) {
        val current = selectedLayer ?: return
        val index = layers.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val target = (index + offset).coerceIn(0, layers.lastIndex)
        if (target == index) return
        pushUndo()
        val list = layers.toMutableList()
        val removed = list.removeAt(index)
        list.add(target, removed)
        layers = list
    }

    fun resetScene() {
        pushUndo()
        layers = listOf(Text3DLayer(id = 1L, text = "ملصق 3D", fontId = "cairo", style = MetalStyle.GOLD))
        selectedLayerId = 1L
        nextLayerId = 2L
        metalStyle = MetalStyle.GOLD
        shapeStyle = ShapeStyle.SLAB
        autoRotate = true
        manualRotation = 0f
        modelFile = null
        baseX = 0f
        baseY = 0f
        baseZ = 0f
        baseRotationX = 0f
        baseRotationY = 0f
        baseRotationZ = 0f
        baseScale = 1f
        moveTogether = true
        unifiedColor = false
        baseColorOverride = null
        lightMode = LightMode.ALL_SIDES
        lightAngle = 45f
        lightElevation = 35f
        backgroundMode = BackgroundMode.TRANSPARENT
        backgroundColor = 0xFF10152E.toInt()
        backgroundImage = null
        watermarkEnabled = false
        watermarkText = "All Stickers"
        watermarkColor = 0xFFFFFFFF.toInt()
        watermarkOpacity = 0.55f
        dragMode = false
        cameraKeyframes.clear()
        cameraPathActive = false
        modelKeyframes.clear()
        storeActiveFrame()
    }

    fun selectFrame(id: Long) {
        if (id == activeFrameId) return
        storeActiveFrame()
        val target = frames.firstOrNull { it.id == id }
        if (target == null || target.locked) return
        applySnapshot(target.snapshot)
        activeFrameId = id
    }

    fun addFrame() {
        storeActiveFrame()
        val id = nextFrameId
        nextFrameId += 1L
        frames.add(
            Text3DFrame(
                id = id,
                name = "الإطار ${frames.size + 1}",
                locked = false,
                visible = true,
                snapshot = currentSnapshot()
            )
        )
        activeFrameId = id
    }

    fun deleteActiveFrame() {
        if (frames.size <= 1) {
            Toast.makeText(context, "لا يمكن حذف الإطار الوحيد", Toast.LENGTH_SHORT).show()
            return
        }
        val index = frames.indexOfFirst { it.id == activeFrameId }
        if (index < 0) return
        frames.removeAt(index)
        activeFrameId = frames.first().id
        applySnapshot(frames.first().snapshot)
    }

    fun toggleActiveFrameLock() {
        val index = frames.indexOfFirst { it.id == activeFrameId }
        if (index >= 0) frames[index] = frames[index].copy(locked = !frames[index].locked)
    }

    fun toggleActiveFrameVisibility() {
        val index = frames.indexOfFirst { it.id == activeFrameId }
        if (index >= 0) frames[index] = frames[index].copy(visible = !frames[index].visible)
    }

    fun frameSummary(frame: Text3DFrame): String =
        if (frame.id == activeFrameId) {
            currentSnapshot().contentSummary()
        } else {
            frame.snapshot.contentSummary()
        }

    fun nudge(dx: Float, dy: Float, dz: Float) {
        if (moveTarget3D == 0) {
            updateSelected { layer ->
                layer.copy(
                    offsetX = layer.offsetX + dx,
                    offsetY = layer.offsetY + dy,
                    offsetZ = layer.offsetZ + dz
                )
            }
        } else if (moveTarget3D == 2) {
            pushUndo()
            modelOffsetX += dx
            modelOffsetY += dy
            modelOffsetZ += dz
        } else {
            pushUndo()
            baseX += dx
            baseY += dy
            baseZ += dz
        }
    }

    fun rotateBy(rx: Float, ry: Float, rz: Float) {
        if (moveTarget3D == 0) {
            updateSelected { layer ->
                layer.copy(
                    rotationX = layer.rotationX + rx,
                    rotationY = layer.rotationY + ry,
                    rotationZ = layer.rotationZ + rz
                )
            }
        } else if (moveTarget3D == 2) {
            pushUndo()
            modelRotationX += rx
            modelRotationY += ry
            modelRotationZ += rz
        } else {
            pushUndo()
            baseRotationX += rx
            baseRotationY += ry
            baseRotationZ += rz
        }
    }

    fun scaleBy(delta: Float) {
        if (moveTarget3D == 0) {
            updateSelected { layer ->
                layer.copy(scale = (layer.scale + delta).coerceIn(0.25f, 2.5f))
            }
        } else if (moveTarget3D == 2) {
            pushUndo()
            modelScale = (modelScale + delta).coerceIn(0.25f, 2.5f)
        } else {
            pushUndo()
            baseScale = (baseScale + delta).coerceIn(0.25f, 2.5f)
        }
    }

    fun timelineDuration(): Long = timelineDurationMs.toLong().coerceAtLeast(250L)

    fun currentTimelineTimeMs(): Long {
        if (!timelinePlaying) return timelineManualMs.toLong().coerceIn(0L, timelineDuration())
        val duration = timelineDuration()
        val elapsedMs = ((System.nanoTime() - timelineStartNanos) / 1_000_000.0) * timelineSpeed
        val raw = if (timelineReverse) duration - elapsedMs else elapsedMs
        val wrapped = ((raw % duration) + duration) % duration
        return if (timelineLoop) wrapped.toLong() else raw.toLong().coerceIn(0L, duration)
    }

    fun toggleTimelinePlay() {
        if (timelinePlaying) {
            timelineManualMs = currentTimelineTimeMs().toFloat()
            timelinePlaying = false
        } else {
            timelineStartNanos = System.nanoTime()
            timelinePlaying = true
        }
    }

    fun stopTimeline() {
        timelinePlaying = false
        timelineManualMs = 0f
        timelinePlayheadMs = 0L
    }

    fun seekTimeline(ms: Float) {
        timelinePlaying = false
        timelineManualMs = ms.coerceIn(0f, timelineDurationMs)
        timelinePlayheadMs = timelineManualMs.toLong()
    }

    fun addKeyframeAtPlayhead() {
        val layer = selectedLayer ?: return
        val time = currentTimelineTimeMs()
        keyframes.removeAll { kotlin.math.abs(it.timeMs - time) < 30L }
        keyframes.add(layer.toKeyframe(time))
        keyframes.sortBy { it.timeMs }
        timelineManualMs = time.toFloat()
        timelinePlayheadMs = time
        Toast.makeText(context, "تمت إضافة مفتاح عند ${time}ms", Toast.LENGTH_SHORT).show()
    }

    fun addModelKeyframeAtPlayhead() {
        val time = currentTimelineTimeMs()
        modelKeyframes.removeAll { kotlin.math.abs(it.timeMs - time) < 30L }
        modelKeyframes.add(
            StudioKeyframe(
                timeMs = time,
                x = modelOffsetX,
                y = modelOffsetY,
                z = modelOffsetZ,
                rotationX = modelRotationX,
                rotationY = modelRotationY,
                rotationZ = modelRotationZ,
                scale = modelScale
            )
        )
        modelKeyframes.sortBy { it.timeMs }
        timelineManualMs = time.toFloat()
        timelinePlayheadMs = time
        Toast.makeText(context, "تمت إضافة مفتاح للنموذج عند ${time}ms", Toast.LENGTH_SHORT).show()
    }

    /** Writes the current pose of the active drag target as a keyframe (when record mode is on). */
    fun recordDragKeyframe(force: Boolean = false) {
        if (!recordMotion && !force) return
        when (moveTarget3D) {
            0 -> layers.firstOrNull { it.id == selectedLayerId }?.let { layer ->
                val time = currentTimelineTimeMs()
                keyframes.removeAll { kotlin.math.abs(it.timeMs - time) < 30L }
                keyframes.add(layer.toKeyframe(time))
                keyframes.sortBy { it.timeMs }
            }
            2 -> addModelKeyframeAtPlayhead()
        }
    }

    fun applyAnimationPreset(preset: StudioAnimationPreset) {
        val layer = selectedLayer ?: return
        val track = buildPresetKeyframes(preset, layer.toKeyframe(0L), timelineDuration())
        keyframes.clear()
        keyframes.addAll(track)
        timelineManualMs = 0f
        timelinePlayheadMs = 0L
        timelineStartNanos = System.nanoTime()
        timelinePlaying = true
        Toast.makeText(context, "تم تطبيق حركة ${preset.label}", Toast.LENGTH_SHORT).show()
    }

    fun currentProjectData(): StudioProjectData {
        storeActiveFrame()
        return StudioProjectData(
            frames = frames.toList(),
            activeFrameId = activeFrameId,
            nextFrameId = nextFrameId,
            nextLayerId = nextLayerId,
            keyframes = keyframes.toList(),
            modelKeyframes = modelKeyframes.toList(),
            cameraKeyframes = cameraKeyframes.toList(),
            durationMs = timelineDuration(),
            easing = timelineEasing,
            loop = timelineLoop,
            reverse = timelineReverse,
            speed = timelineSpeed
        )
    }

    fun saveProject(showToast: Boolean): Boolean {
        val saved = StudioProjectStore.save(context, currentProjectData())
        if (showToast) {
            Toast.makeText(
                context,
                if (saved) "تم حفظ المشروع" else "تعذر حفظ المشروع",
                Toast.LENGTH_SHORT
            ).show()
        }
        return saved
    }

    fun restoreProject() {
        val project = StudioProjectStore.load(context)
        if (project == null) {
            Toast.makeText(context, "لا يوجد مشروع محفوظ", Toast.LENGTH_SHORT).show()
            return
        }
        pushUndo()
        frames.clear()
        frames.addAll(project.frames)
        activeFrameId = project.activeFrameId
        frames.firstOrNull { it.id == project.activeFrameId }?.let { active ->
            applySnapshot(active.snapshot)
        }
        nextFrameId = project.nextFrameId
        nextLayerId = project.nextLayerId
        keyframes.clear()
        keyframes.addAll(project.keyframes)
        modelKeyframes.clear()
        modelKeyframes.addAll(project.modelKeyframes)
        cameraKeyframes.clear()
        cameraKeyframes.addAll(project.cameraKeyframes)
        cameraPathActive = cameraKeyframes.isNotEmpty()
        timelineDurationMs = project.durationMs.toFloat().coerceIn(250f, 60_000f)
        timelineEasing = project.easing
        timelineLoop = project.loop
        timelineReverse = project.reverse
        timelineSpeed = project.speed.coerceIn(0.1f, 4f)
        timelinePlaying = false
        timelineManualMs = 0f
        timelinePlayheadMs = 0L
        Toast.makeText(context, "تم استعادة المشروع", Toast.LENGTH_SHORT).show()
    }

    fun newProject() {
        resetScene()
        frames.clear()
        activeFrameId = 1L
        nextFrameId = 2L
        frames.add(
            Text3DFrame(
                id = 1L,
                name = "الإطار 1",
                locked = false,
                visible = true,
                snapshot = currentSnapshot()
            )
        )
        keyframes.clear()
        modelKeyframes.clear()
        cameraKeyframes.clear()
        cameraPathActive = false
        timelinePlaying = false
        timelineManualMs = 0f
        timelinePlayheadMs = 0L
        timelineDurationMs = 5000f
        timelineSpeed = 1f
        timelineEasing = AnimationEasing.EASE_IN_OUT
        Toast.makeText(context, "تم إنشاء مشروع جديد", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    // Synchronous-ish scene capture (suspend) used by GIF/MP4 export
    // ------------------------------------------------------------------
    suspend fun captureSceneSuspend(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val sceneView = sceneViewRef
            if (sceneView == null || sceneView.width <= 0 || sceneView.height <= 0) {
                cont.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }
            val bitmap = Bitmap.createBitmap(sceneView.width, sceneView.height, Bitmap.Config.ARGB_8888)
            val activeWatermark = if (watermarkEnabled && watermarkText.isNotBlank()) {
                StickerWatermark(
                    text = watermarkText,
                    color = watermarkColor,
                    opacity = watermarkOpacity,
                    size = 64f
                )
            } else {
                null
            }
            PixelCopy.request(
                sceneView,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        cont.resumeWith(
                            Result.success(
                                composeExportBitmap(
                                    sceneBitmap = bitmap,
                                    backgroundMode = backgroundMode,
                                    backgroundColor = backgroundColor,
                                    backgroundImage = backgroundImage,
                                    watermark = activeWatermark
                                )
                            )
                        )
                    } else {
                        cont.resumeWith(Result.success(null))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }

    // ------------------------------------------------------------------
    // Offline stickers (normal image + 3D model) bundled inside the app
    // ------------------------------------------------------------------
    fun addStickerToScene(sticker: StudioSticker) {
        pushUndo()
        when (sticker.type) {
            StudioStickerType.NORMAL_IMAGE -> {
                val bitmap = stickerRepository.loadStickerBitmap(sticker.assetPath) ?: run {
                    Toast.makeText(
                        context,
                        "تعذر تحميل صورة الملصق: ${sticker.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                addImageLayer(bitmap)
                Toast.makeText(
                    context,
                    "تمت إضافة الملصق: ${sticker.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            StudioStickerType.MODEL_3D -> {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        copyAssetToCache(context, sticker.assetPath)
                    }
                    withContext(Dispatchers.Main) {
                        if (file != null) {
                            modelFile = file
                            Toast.makeText(
                                context,
                                "تمت إضافة النموذج ثلاثي الأبعاد: ${sticker.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "تعذر تحميل النموذج: ${sticker.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Export: animated GIF and MP4
    // ------------------------------------------------------------------
    fun shareFile(file: File, mimeType: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = android.content.Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "تصدير إلى"))
    }

    fun exportGif() {
        if (isExporting) return
        isExporting = true
        scope.launch {
            try {
                val durationMs = timelineDuration()
                val frameIntervalMs = 100L // ~10 fps
                val frameCount = (durationMs / frameIntervalMs).coerceIn(1, 150).toInt()
                val frames = mutableListOf<GifFrame>()
                val wasPlaying = timelinePlaying
                if (wasPlaying) toggleTimelinePlay()
                try {
                    for (i in 0 until frameCount) {
                        val targetMs = (i * frameIntervalMs).toLong()
                        seekTimeline(targetMs.toFloat())
                        delay(60) // let the 3D scene render this frame
                        val bitmap = captureSceneSuspend()
                        if (bitmap != null) {
                            frames += GifFrame(bitmap, delayMs = frameIntervalMs.toInt())
                        } else {
                            frames += GifFrame(
                                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888),
                                delayMs = frameIntervalMs.toInt()
                            )
                        }
                        exportProgress = (i + 1).toFloat() / frameCount
                    }
                } finally {
                    if (wasPlaying) toggleTimelinePlay()
                }

                val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.gif")
                withContext(Dispatchers.IO) {
                    java.io.FileOutputStream(outFile).use { os ->
                        AnimatedGifEncoder.encode(frames, os)
                    }
                }
                withContext(Dispatchers.Main) {
                    exportProgress = 1f
                    shareFile(outFile, "image/gif")
                    Toast.makeText(context, "تم تصدير GIF إلى الملفات المؤقتة", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "فشل تصدير GIF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isExporting = false
                    exportProgress = 0f
                }
            }
        }
    }

    fun exportMp4() {
        if (isExporting) return
        isExporting = true
        scope.launch {
            Mp4Exporter(File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4"), 512, 512).use { exporter ->
                try {
                    exporter.prepare(fps = 30)
                    val durationMs = timelineDuration()
                    val frameIntervalMs = (1000.0 / 30.0).toLong()
                    val frameCount = (durationMs / frameIntervalMs).coerceIn(1, 150).toInt()
                    val wasPlaying = timelinePlaying
                    if (wasPlaying) toggleTimelinePlay()
                    try {
                        for (i in 0 until frameCount) {
                            val targetMs = (i * frameIntervalMs).toLong()
                            seekTimeline(targetMs.toFloat())
                            delay(40)
                            val bitmap = captureSceneSuspend()
                                ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                            val scaled = if (bitmap.width != 512 || bitmap.height != 512) {
                                Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                            } else bitmap
                            exporter.encodeFrame(scaled, delayMs = frameIntervalMs)
                            exportProgress = (i + 1).toFloat() / frameCount
                            if (scaled != bitmap) scaled.recycle()
                        }
                    } finally {
                        if (wasPlaying) toggleTimelinePlay()
                    }
                    exporter.finish()
                    withContext(Dispatchers.Main) {
                        exportProgress = 1f
                        shareFile(exporter.outputFile, "video/mp4")
                        Toast.makeText(context, "تم تصدير MP4 إلى الملفات المؤقتة", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "فشل تصدير MP4: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        exportProgress = 0f
                    }
                }
            }
        }
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(y = 0.15f, z = 4.3f)
        lookAt(Position(0f, 0f, 0f))
    }
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 110_000f
    }
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = cameraNode.worldPosition,
        targetPosition = Position(0f, 0f, 0f)
    )
    val nodes = rememberNodes()

    fun applyCameraView(viewIndex: Int) {
        val position = when (viewIndex) {
            0 -> Position(x = 0f, y = 0.15f, z = 4.3f)   // front
            1 -> Position(x = 0f, y = 0.15f, z = -4.3f)  // back
            2 -> Position(x = -4.3f, y = 0.15f, z = 0f)  // left
            3 -> Position(x = 4.3f, y = 0.15f, z = 0f)   // right
            4 -> Position(x = 0f, y = 4.3f, z = 0.01f)   // top
            5 -> Position(x = 0f, y = -4.3f, z = 0.01f)  // bottom
            else -> Position(x = 2.6f, y = 1.6f, z = 4.0f) // perspective
        }
        cameraNode.position = position
        cameraNode.lookAt(Position(0f, 0f, 0f))
    }

    fun addCameraKeyframeAtPlayhead() {
        val time = currentTimelineTimeMs()
        cameraKeyframes.removeAll { kotlin.math.abs(it.timeMs - time) < 30L }
        cameraKeyframes.add(
            StudioCameraKeyframe(
                timeMs = time,
                position = cameraNode.worldPosition,
                lookAt = Position(0f, 0f, 0f)
            )
        )
        cameraKeyframes.sortBy { it.timeMs }
        cameraPathActive = true
        Toast.makeText(context, "تمت إضافة مفتاح كاميرا عند ${time}ms", Toast.LENGTH_SHORT).show()
    }

    var textGroupRoot by remember { mutableStateOf<Node?>(null) }
    var baseGroupRoot by remember { mutableStateOf<Node?>(null) }
    val extraLightNodes = remember { mutableListOf<LightNode>() }
    val layerRoots = remember { mutableMapOf<String, Node>() }
    val layerPlanes = remember { mutableMapOf<String, List<PlaneNode>>() }
    val layerMaterials = remember { mutableMapOf<String, MaterialInstance>() }
    val layerTextures = remember { mutableMapOf<String, Texture>() }
    val baseCleanup = remember { mutableListOf<() -> Unit>() }
    val renderedTextSignatures = remember { mutableMapOf<Long, String>() }

    val dragOffsetX = remember { mutableStateOf(0f) }
    val dragOffsetY = remember { mutableStateOf(0f) }
    val dragLayerId = remember { mutableStateOf(-1L) }
    val dragOnBase = remember { mutableStateOf(false) }

    fun teardownScene() {
        layerPlanes.values.forEach { planes ->
            planes.forEach { runCatching { it.destroy() } }
        }
        layerPlanes.clear()
        layerMaterials.values.forEach { runCatching { materialLoader.destroyMaterialInstance(it) } }
        layerMaterials.clear()
        layerTextures.values.forEach { runCatching { engine.destroyTexture(it) } }
        layerTextures.clear()
        baseCleanup.forEach { runCatching { it() } }
        baseCleanup.clear()
        layerRoots.values.forEach { runCatching { it.destroy() } }
        layerRoots.clear()
        nodes.forEach { runCatching { it.destroy() } }
        nodes.clear()
        extraLightNodes.clear()
        renderedTextSignatures.clear()
        modelNode = null
        textGroupRoot = null
        baseGroupRoot = null
    }

    fun textSignature(layer: Text3DLayer): String = buildString {
        append(layer.text).append('|').append(layer.fontId).append('|')
        append(layer.style.name).append('|').append(layer.color).append('|')
        append(layer.curve).append('|').append(layer.fontSize).append('|')
        append(layer.letterSpacing).append('|').append(layer.wordSpacing).append('|')
        append(layer.lineSpacing).append('|').append(layer.alignment).append('|')
        append(layer.outlineWidth).append('|').append(layer.outlineColor).append('|')
        append(layer.opacity).append('|').append(layer.bevel).append('|')
        append(layer.textureBitmap?.hashCode() ?: 0)
    }

    fun preparedTextBitmap(layer: Text3DLayer): Bitmap {
        val textBitmap = create3DTextBitmap(
            text = layer.text.ifBlank { "نصك هنا" },
            fontId = layer.fontId,
            style = layer.style,
            colorOverride = layer.color,
            curve = layer.curve,
            fontSizePercent = layer.fontSize,
            letterSpacing = layer.letterSpacing,
            wordSpacing = layer.wordSpacing,
            lineSpacing = layer.lineSpacing,
            alignment = layer.alignment,
            outlineWidth = layer.outlineWidth,
            outlineColor = layer.outlineColor,
            opacity = layer.opacity,
            bevel = layer.bevel
        )
        return layer.textureBitmap?.let { dressTextWithTexture(textBitmap, it) } ?: textBitmap
    }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoadingModel = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyModelToCache(context, uri) }
            isLoadingModel = false
            if (file != null) {
                pushUndo()
                modelFile = file
                Toast.makeText(context, "تم تحميل النموذج 3D", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "تعذر قراءة النموذج. اختر ملف GLB صالح.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBackgroundBitmap(context, uri) }
            if (bitmap != null) {
                pushUndo()
                backgroundImage = bitmap
                backgroundMode = BackgroundMode.IMAGE
            } else {
                Toast.makeText(context, "تعذر تحميل صورة الخلفية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val texturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { decodeBackgroundBitmap(context, uri) }
            val current = selectedLayer
            if (picked != null && current != null && !current.isImage) {
                val dressed = withContext(Dispatchers.Default) {
                    val textBitmap = create3DTextBitmap(
                        text = current.text.ifBlank { "نص" },
                        fontId = current.fontId,
                        style = current.style,
                        colorOverride = null,
                        curve = current.curve,
                        fontSizePercent = current.fontSize,
                        letterSpacing = current.letterSpacing,
                        wordSpacing = current.wordSpacing,
                        lineSpacing = current.lineSpacing,
                        alignment = current.alignment,
                        outlineWidth = current.outlineWidth,
                        outlineColor = current.outlineColor,
                        opacity = current.opacity,
                        bevel = current.bevel
                    )
                    val result = dressTextWithTexture(textBitmap, picked)
                    textBitmap.recycle()
                    result
                }
                updateSelected { it.copy(textureBitmap = dressed) }
            } else if (picked != null && current != null) {
                Toast.makeText(context, "طبقة الصورة لا تحتاج تكسية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imageLayerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { decodeBackgroundBitmap(context, uri) }
            if (picked != null) {
                pushUndo()
                addImageLayer(picked)
            }
        }
    }

    val structureKey = buildString {
        append(activeFrameId).append('|')
        frames.forEach { frame ->
            append(frame.id).append(':').append(frame.visible).append(',')
        }
        append('|')
        layers.forEach { layer ->
            append(layer.id).append(':').append(layer.isImage).append(':')
            append(layer.visible).append(':')
            append(layer.extrusion).append(':').append(layer.bevel).append(':')
            append(layer.imageBitmap?.let { "${it.width}x${it.height}" } ?: "-").append(',')
        }
        append('|')
        frames.filter { it.id != activeFrameId }.forEach { frame ->
            append(frame.id).append(':')
            frame.snapshot.layers.forEach { layer ->
                append(layer.id).append(':').append(layer.isImage).append(':')
                append(layer.visible).append(':')
                append(layer.extrusion).append(':').append(layer.bevel).append(':')
                append(layer.imageBitmap?.let { "${it.width}x${it.height}" } ?: "-").append(',')
            }
        }
        append('|').append(shapeStyle.name).append('|').append(unifiedColor).append('|')
        append(baseColorOverride).append('|').append(modelFile?.path).append('|')
        append(lightMode.name).append('|').append(metalStyle.name).append('|')
        append(if (unifiedColor) (selectedLayer?.color ?: 0) else 0)
    }

    LaunchedEffect(structureKey) {
        teardownScene()

        val textGroup = Node(engine)
        textGroupRoot = textGroup
        nodes += textGroup
        val baseGroup = Node(engine)
        baseGroupRoot = baseGroup
        nodes += baseGroup

        val frameSnapshot = frames.toList()
        val liveLayers = layers

        frameSnapshot.filter { it.visible }.forEach { frame ->
            val isActive = frame.id == activeFrameId
            val renderLayers = if (isActive) liveLayers else frame.snapshot.layers
            renderLayers.filter { it.visible }.forEach { layer ->
                val key = if (isActive) "a_${layer.id}" else "${frame.id}_${layer.id}"
                val layerRoot = Node(engine)
                layerRoot.name = "layer_$key"
                layerRoots[key] = layerRoot
                textGroup.addChildNode(layerRoot)

                if (layer.isImage) {
                    val imageBitmap = layer.imageBitmap
                    if (imageBitmap != null) {
                        val texture = ImageTexture.Builder().bitmap(imageBitmap).build(engine)
                        val material = materialLoader.createTextureInstance(
                            texture = texture,
                            isOpaque = true,
                            metallic = 0.1f,
                            roughness = 0.65f,
                            reflectance = 0.5f
                        )
                        val ratio = imageBitmap.height.toFloat() /
                            imageBitmap.width.coerceAtLeast(1)
                        val imageWidth = 2.4f
                        val plane = PlaneNode(
                            engine = engine,
                            size = Size(imageWidth, imageWidth * ratio, 0f),
                            normal = Direction(z = 1f),
                            materialInstance = material
                        )
                        layerRoot.addChildNode(plane)
                        layerPlanes[key] = listOf(plane)
                        layerMaterials[key] = material
                        layerTextures[key] = texture
                    }
                } else {
                    val finalBitmap = withContext(Dispatchers.Default) {
                        preparedTextBitmap(layer)
                    }
                    val texture = ImageTexture.Builder().bitmap(finalBitmap).build(engine)
                    val material = if (layer.style.unlit) {
                        materialLoader.createImageInstance(texture)
                    } else {
                        materialLoader.createTextureInstance(
                            texture = texture,
                            isOpaque = false,
                            metallic = layer.effectiveMetallic(),
                            roughness = layer.effectiveRoughness(),
                            reflectance = layer.effectiveReflectance()
                        )
                    }
                    val widthMeters = 3.4f
                    val heightMeters = widthMeters * (320f / 1024f)
                    val planes = mutableListOf<PlaneNode>()
                    val planeCount = extrusionPlaneCount(layer.extrusion)
                    for (planeIndex in (planeCount - 1) downTo 0) {
                        val plane = PlaneNode(
                            engine = engine,
                            size = Size(widthMeters, heightMeters, 0f),
                            normal = Direction(z = 1f),
                            materialInstance = material
                        )
                        plane.position = Position(z = -0.035f * planeIndex)
                        val bevelScale = 1f + layer.bevel.coerceIn(0f, 1f) * 0.006f * planeIndex
                        plane.scale = Scale(bevelScale, bevelScale, 1f)
                        layerRoot.addChildNode(plane)
                        planes += plane
                    }
                    layerPlanes[key] = planes
                    layerMaterials[key] = material
                    layerTextures[key] = texture
                    if (isActive) {
                        renderedTextSignatures[layer.id] = textSignature(layer)
                    }
                }
            }
        }

        val baseStyle = metalStyle
        val baseColor = if (unifiedColor) {
            Color(selectedLayer?.color ?: baseStyle.colors[0])
        } else {
            Color(baseColorOverride ?: baseStyle.colors[0])
        }
        val shapeMaterial = materialLoader.createColorInstance(
            color = baseColor,
            metallic = baseStyle.metallic,
            roughness = baseStyle.roughness,
            reflectance = baseStyle.reflectance
        )
        layerMaterials["__base"] = shapeMaterial

        fun attachBase(node: Node) {
            baseGroup.addChildNode(node)
            baseCleanup += { runCatching { node.destroy() } }
        }

        buildBaseShape(shapeStyle, engine, shapeMaterial, ::attachBase)

        modelNode = null
        modelFile?.let { file ->
            runCatching {
                val instance = modelLoader.createModelInstance(file)
                val loadedModel = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 1.1f
                )
                loadedModel.position = Position(modelOffsetX, modelOffsetY, modelOffsetZ)
                loadedModel.rotation = Rotation(modelRotationX, modelRotationY, modelRotationZ)
                loadedModel.scale = Scale(modelScale, modelScale, modelScale)
                nodes += loadedModel
                modelNode = loadedModel
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "تعذر تحميل النموذج: ${error.message ?: "صيغة غير مدعومة"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val mainDirection = lightDirectionFor(lightAngle, lightElevation)
        mainLightNode.lightDirection = mainDirection

        fun addExtraLight(dir: Direction, brightness: Float) {
            val light = LightNode(
                engine = engine,
                type = LightManager.Type.DIRECTIONAL,
                apply = {
                    intensity(brightness)
                    direction(dir.x, dir.y, dir.z)
                    castShadows(false)
                }
            )
            light.name = "studio_extra_light"
            nodes += light
            extraLightNodes += light
        }

        when (lightMode) {
            LightMode.ONE_SIDE -> Unit
            LightMode.TWO_SIDES -> {
                addExtraLight(
                    lightDirectionFor(lightAngle + 180f, lightElevation),
                    65_000f
                )
            }
            LightMode.ALL_SIDES -> {
                for (index in 1..3) {
                    addExtraLight(
                        lightDirectionFor(lightAngle + index * 90f, lightElevation),
                        55_000f
                    )
                }
            }
        }
    }

    LaunchedEffect(selectedLayerId) {
        typingUndoKey = null
    }

    val textKey = layers.joinToString("|") { layer ->
        "${layer.id}:${layer.isImage}:${textSignature(layer)}"
    }

    LaunchedEffect(textKey, activeFrameId) {
        val changed = layers.filter { layer ->
            !layer.isImage &&
                layer.visible &&
                renderedTextSignatures[layer.id] != textSignature(layer) &&
                layerPlanes.containsKey("a_${layer.id}")
        }
        if (changed.isEmpty()) return@LaunchedEffect
        delay(16)
        changed.forEach { layer ->
            val key = "a_${layer.id}"
            val signature = textSignature(layer)
            val finalBitmap = withContext(Dispatchers.Default) {
                preparedTextBitmap(layer)
            }
            val currentLayer = layers.firstOrNull { it.id == layer.id }
            if (currentLayer == null || textSignature(currentLayer) != signature) {
                return@forEach
            }
            val planes = layerPlanes[key] ?: return@forEach
            val texture = ImageTexture.Builder().bitmap(finalBitmap).build(engine)
            val material = if (layer.style.unlit) {
                materialLoader.createImageInstance(texture)
            } else {
                materialLoader.createTextureInstance(
                    texture = texture,
                    isOpaque = false,
                    metallic = layer.effectiveMetallic(),
                    roughness = layer.effectiveRoughness(),
                    reflectance = layer.effectiveReflectance()
                )
            }
            planes.forEach { plane -> runCatching { plane.materialInstance = material } }
            val oldMaterial = layerMaterials.put(key, material)
            val oldTexture = layerTextures.put(key, texture)
            oldMaterial?.let { runCatching { materialLoader.destroyMaterialInstance(it) } }
            oldTexture?.let { runCatching { engine.destroyTexture(it) } }
            renderedTextSignatures[layer.id] = signature
        }
    }

    LaunchedEffect(lightAngle, lightElevation, lightMode) {
        val mainDirection = lightDirectionFor(lightAngle, lightElevation)
        mainLightNode.lightDirection = mainDirection
        mainLightNode.position = Position(
            x = mainDirection.x * 6f,
            y = -mainDirection.y * 6f,
            z = -mainDirection.z * 6f
        )
        extraLightNodes.forEachIndexed { index, light ->
            val offset = if (lightMode == LightMode.ALL_SIDES) (index + 1) * 90f else 180f
            light.lightDirection = lightDirectionFor(lightAngle + offset, lightElevation)
        }
    }

    LaunchedEffect(autoRotate, metalStyle) {
        preferences.edit()
            .putBoolean("autoRotate3D", autoRotate)
            .putString("last3dStyle", metalStyle.name)
            .apply()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { saveProject(showToast = false) }
            teardownScene()
        }
    }

    fun captureScene() {
        val sceneView = sceneViewRef
        if (sceneView == null || sceneView.width <= 0 || sceneView.height <= 0) {
            Toast.makeText(context, "المشهد غير جاهز للالتقاط", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = Bitmap.createBitmap(
            sceneView.width,
            sceneView.height,
            Bitmap.Config.ARGB_8888
        )
        val activeWatermark = if (watermarkEnabled && watermarkText.isNotBlank()) {
            StickerWatermark(
                text = watermarkText,
                color = watermarkColor,
                opacity = watermarkOpacity,
                size = 64f
            )
        } else {
            null
        }
        PixelCopy.request(
            sceneView,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    onExport(
                        composeExportBitmap(
                            sceneBitmap = bitmap,
                            backgroundMode = backgroundMode,
                            backgroundColor = backgroundColor,
                            backgroundImage = backgroundImage,
                            watermark = activeWatermark
                        )
                    )
                } else {
                    Toast.makeText(context, "تعذر التقاط المشهد", Toast.LENGTH_SHORT).show()
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            when (backgroundMode) {
                BackgroundMode.TRANSPARENT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF090B1D),
                                        Color(0xFF17103A),
                                        Color(0xFF061A2A)
                                    )
                                )
                            )
                    )
                }
                BackgroundMode.COLOR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(backgroundColor))
                    )
                }
                BackgroundMode.IMAGE -> {
                    backgroundImage?.let { image ->
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(backgroundColor))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dragMode, cameraPathRecording, selectedLayer?.id) {
                        if (!dragMode || cameraPathRecording) return@pointerInput
                        val areaWidth = size.width.coerceAtLeast(1)
                        val areaHeight = size.height.coerceAtLeast(1)
                        val metersPerPxX = 6f / areaWidth
                        val metersPerPxY = 6f / areaHeight
                        awaitEachGesture {
                            awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false
                            )
                            var moved = false
                            var accumX = 0f
                            var accumY = 0f
                            dragOnBase.value = moveTarget3D == 1
                            dragLayerId.value = -1L
                            var lastRecordAt = 0L
                            var undoPushed = false
                            fun autoRecord(): Boolean =
                                (moveTarget3D == 0 && keyframes.isNotEmpty()) ||
                                    (moveTarget3D == 2 && modelKeyframes.isNotEmpty())
                            if (moveTarget3D != 1) recordDragKeyframe(force = autoRecord())
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) break
                                val pan = event.calculatePan()
                                if (!moved) {
                                    if (pan.getDistance() > 1f) {
                                        moved = true
                                    } else {
                                        continue
                                    }
                                }
                                if (!undoPushed) {
                                    pushUndo()
                                    undoPushed = true
                                }
                                val deltaX = pan.x * metersPerPxX
                                val deltaY = -pan.y * metersPerPxY
                                accumX += deltaX
                                accumY += deltaY
                                when (moveTarget3D) {
                                    0 -> updateSelected(record = false) { layer ->
                                        layer.copy(
                                            offsetX = layer.offsetX + deltaX,
                                            offsetY = layer.offsetY + deltaY
                                        )
                                    }
                                    2 -> {
                                        modelOffsetX += deltaX
                                        modelOffsetY += deltaY
                                    }
                                    else -> {
                                        dragOffsetX.value = accumX
                                        dragOffsetY.value = accumY
                                    }
                                }
                                val now = System.currentTimeMillis()
                                if (moveTarget3D != 1 && now - lastRecordAt >= 140L) {
                                    lastRecordAt = now
                                    recordDragKeyframe(force = autoRecord())
                                }
                                event.changes.forEach { it.consume() }
                            }
                            if (moved) {
                                if (moveTarget3D == 1) {
                                    baseX += accumX
                                    baseY += accumY
                                }
                                recordDragKeyframe(force = autoRecord())
                            }
                            dragOffsetX.value = 0f
                            dragOffsetY.value = 0f
                            dragLayerId.value = -1L
                            dragOnBase.value = false
                        }
                    }
            ) {
                Scene(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    materialLoader = materialLoader,
                    environmentLoader = environmentLoader,
                    view = view,
                    isOpaque = false,
                    renderer = renderer,
                    scene = scene,
                    mainLightNode = mainLightNode,
                    cameraNode = cameraNode,
                    childNodes = nodes,
                    collisionSystem = collisionSystem,
                    cameraManipulator = cameraManipulator,
                    onFrame = { frameTimeNanos ->
                        val time = frameTimeNanos / 1_000_000_000f
                        val globalRotation = if (autoRotate) {
                            (time * 30f) % 360f
                        } else {
                            manualRotation
                        }
                        textGroupRoot?.rotation = Rotation(y = globalRotation)
                        baseGroupRoot?.let { base ->
                            val dragX = if (dragOnBase.value) dragOffsetX.value else 0f
                            val dragY = if (dragOnBase.value) dragOffsetY.value else 0f
                            base.position = Position(baseX + dragX, baseY + dragY, baseZ)
                            base.rotation = Rotation(
                                x = baseRotationX,
                                y = baseRotationY + if (moveTogether) globalRotation else 0f,
                                z = baseRotationZ
                            )
                            base.scale = Scale(baseScale, baseScale, baseScale)
                        }
                        frames.forEach { frame ->
                            if (!frame.visible) return@forEach
                            val isActive = frame.id == activeFrameId
                            val renderLayers = if (isActive) layers else frame.snapshot.layers
                            renderLayers.forEach { layer ->
                                val rootKey = if (isActive) {
                                    "a_${layer.id}"
                                } else {
                                    "${frame.id}_${layer.id}"
                                }
                                val root = layerRoots[rootKey] ?: return@forEach
                                var x = layer.offsetX
                                var y = layer.offsetY
                                var rotationY = layer.rotationY
                                var scaleValue = layer.scale
                                if (isActive && dragLayerId.value == layer.id) {
                                    x += dragOffsetX.value
                                    y += dragOffsetY.value
                                }
                                when (layer.animation) {
                                    LayerAnimation.NONE -> Unit
                                    LayerAnimation.SPIN -> rotationY += time * 70f
                                    LayerAnimation.WAVE -> y += sin(time * 3.8f) * 0.14f
                                    LayerAnimation.BOB -> y += sin(time * 2.2f) * 0.2f
                                    LayerAnimation.PULSE ->
                                        scaleValue *= 1f + sin(time * 3.1f) * 0.09f
                                    LayerAnimation.SHAKE -> x += sin(time * 11f) * 0.05f
                                }
                                root.position = Position(x, y, layer.offsetZ)
                                root.rotation = Rotation(
                                    x = layer.rotationX,
                                    y = rotationY,
                                    z = layer.rotationZ
                                )
                                root.scale = Scale(scaleValue, scaleValue, scaleValue)
                            }
                        }

                        // Model transform (Phase 3) is applied live every frame.
                        modelNode?.let { model ->
                            model.position = Position(modelOffsetX, modelOffsetY, modelOffsetZ)
                            model.rotation = Rotation(
                                modelRotationX,
                                modelRotationY,
                                modelRotationZ
                            )
                            model.scale = Scale(modelScale, modelScale, modelScale)
                        }
                        if (modelKeyframes.isNotEmpty()) {
                            val pose = sampleKeyframes(
                                modelKeyframes,
                                currentTimelineTimeMs(),
                                timelineEasing
                            )
                            modelNode?.let { model ->
                                pose?.let {
                                    model.position = Position(it.x, it.y, it.z)
                                    model.rotation = Rotation(it.rotationX, it.rotationY, it.rotationZ)
                                    model.scale = Scale(it.scale, it.scale, it.scale)
                                }
                            }
                        }

                        // Camera path motion (تحريك الكاميرا عبر مسار حركة محدد)
                        if (cameraKeyframes.isNotEmpty() && !cameraPathRecording) {
                            val timelineMs = currentTimelineTimeMs()
                            if (cameraPathActive) {
                                sampleCameraPath(cameraKeyframes.toList(), timelineMs)?.let { cam ->
                                    cameraNode.position = cam.position
                                    cameraNode.lookAt(cam.lookAt)
                                }
                            } else {
                                // Static preview of the first camera keyframe
                                val first = cameraKeyframes.sortedBy { it.timeMs }.firstOrNull()
                                first?.let {
                                    cameraNode.position = it.position
                                    cameraNode.lookAt(it.lookAt)
                                }
                            }
                        }

                        if (cameraPathRecording && timelinePlaying) {
                            val recordTime = currentTimelineTimeMs()
                            if (recordTime - cameraRecordTickMs >= 140L ||
                                recordTime < cameraRecordTickMs
                            ) {
                                cameraRecordTickMs = recordTime
                                cameraKeyframes.removeAll {
                                    kotlin.math.abs(it.timeMs - recordTime) < 30L
                                }
                                cameraKeyframes.add(
                                    StudioCameraKeyframe(
                                        timeMs = recordTime,
                                        position = cameraNode.worldPosition,
                                        lookAt = Position(0f, 0f, 0f)
                                    )
                                )
                                cameraKeyframes.sortBy { it.timeMs }
                                cameraPathActive = true
                            }
                        }

                        // Keyframe timeline (Phase 4) overrides the selected layer.
                        if (keyframes.isNotEmpty()) {
                            val timelineMs = currentTimelineTimeMs()
                            if (timelinePlaying &&
                                kotlin.math.abs(timelineMs - timelinePlayheadMs) >= 200L
                            ) {
                                timelinePlayheadMs = timelineMs
                            }
                            val pose = sampleKeyframes(keyframes, timelineMs, timelineEasing)
                            val selectedRoot = selectedLayerId?.let { layerRoots["a_$it"] }
                            if (pose != null && selectedRoot != null) {
                                selectedRoot.position = Position(pose.x, pose.y, pose.z)
                                selectedRoot.rotation = Rotation(
                                    pose.rotationX,
                                    pose.rotationY,
                                    pose.rotationZ
                                )
                                selectedRoot.scale = Scale(pose.scale, pose.scale, pose.scale)
                            }
                        }
                    },
                    onViewCreated = { sceneViewRef = this }
                )
            }

            Text(
                text = if (dragMode) {
                    "وضع السحب: حرّك إصبعك لتحريك ${if (moveTarget3D == 0) "النص" else "القاعدة"} فورياً"
                } else {
                    "اسحب للتدوير • إصبعان للتكبير • فعّل السحب للتحريك المباشر"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )

            if (isLoadingModel) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp),
                    color = Color.White
                )
            }
        }

        StudioSectionBar(
            sections = STUDIO_3D_SECTIONS,
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
                            PanelLabel("إطارات المشهد (${frames.size})")
                            StudioToolRow {
                                ToolChip("إطار جديد +") { addFrame() }
                                frames.forEach { frame ->
                                    ToolChip(
                                        label = "${frame.name} · ${frameSummary(frame).shortenLabel()}" +
                                            if (!frame.visible) " (مخفي)" else "",
                                        selected = frame.id == activeFrameId,
                                        onClick = { selectFrame(frame.id) }
                                    )
                                }
                            }
                            PanelLabel("أدوات الإطار الحالي")
                            StudioToolRow {
                                ToolChip(
                                    label = if (activeFrame.locked) "فك القفل" else "قفل",
                                    selected = activeFrame.locked
                                ) { toggleActiveFrameLock() }
                                ToolChip(
                                    label = if (activeFrame.visible) "إخفاء" else "إظهار",
                                    selected = !activeFrame.visible
                                ) { toggleActiveFrameVisibility() }
                                ToolChip("حذف الإطار", enabled = frames.size > 1) {
                                    deleteActiveFrame()
                                }
                            }
                            PanelLabel("عناصر الإطار الحالي")
                            StudioToolRow {
                                layers.forEach { layer ->
                                    ToolChip(
                                        label = layer.contentLabel().shortenLabel(),
                                        selected = layer.id == selectedLayerId
                                    ) { selectedLayerId = layer.id }
                                }
                            }
                        }
                        1 -> {
                            PanelLabel("إضافة طبقة")
                            StudioToolRow {
                                ToolChip("نص عربي +") { addLayer(true) }
                                ToolChip("English +") { addLayer(false) }
                                ToolChip("صورة +") { imageLayerPicker.launch("image/*") }
                            }
                            PanelLabel("الطبقات (${layers.size})")
                            StudioToolRow {
                                layers.forEach { layer ->
                                    ToolChip(
                                        label = if (layer.id == selectedLayerId) {
                                            "● ${layer.contentLabel().shortenLabel()}"
                                        } else {
                                            layer.contentLabel().shortenLabel()
                                        },
                                        selected = layer.id == selectedLayerId
                                    ) { selectedLayerId = layer.id }
                                }
                            }
                            if (selectedLayer != null) {
                                PanelLabel("أدوات الطبقة")
                                StudioToolRow {
                                    ToolChip("نسخ") { duplicateSelected() }
                                    ToolChip("حذف", enabled = layers.size > 1) { deleteSelected() }
                                    ToolChip(
                                        if (selectedLayer.visible) "إخفاء" else "إظهار",
                                        selected = !selectedLayer.visible
                                    ) { updateSelected { it.copy(visible = !it.visible) } }
                                    ToolChip(
                                        if (selectedLayer.locked) "فك القفل" else "قفل",
                                        selected = selectedLayer.locked
                                    ) { updateSelected { it.copy(locked = !it.locked) } }
                                    ToolChip("للأمام") { moveSelectedLayer(-1) }
                                    ToolChip("للخلف") { moveSelectedLayer(1) }
                                }
                            }
                        }
                        2 -> {
                            PanelLabel("نص الطبقة المحددة")
                            OutlinedTextField(
                                value = selectedLayer?.text ?: "",
                                onValueChange = { value ->
                                    val id = selectedLayer?.id ?: return@OutlinedTextField
                                    if (typingUndoKey != id) {
                                        pushUndo()
                                        typingUndoKey = id
                                    }
                                    updateSelected(record = false) { it.copy(text = value) }
                                },
                                label = { Text("اكتب النص ويظهر فوراً في المشهد") },
                                singleLine = true,
                                enabled = selectedLayer?.locked != true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PanelLabel("إجراءات سريعة")
                            StudioToolRow {
                                ToolChip("نسخ الطبقة") { duplicateSelected() }
                                ToolChip("حذف الطبقة", enabled = layers.size > 1) { deleteSelected() }
                                ToolChip("نص جديد") { addLayer(true) }
                            }
                        }
                        3 -> {
                            PanelLabel("لغة الخطوط")
                            StudioToolRow {
                                ToolChip(
                                    "خطوط عربية",
                                    selected = fontLanguage3D == StickerFontLanguage.ARABIC
                                ) { fontLanguage3D = StickerFontLanguage.ARABIC }
                                ToolChip(
                                    "English",
                                    selected = fontLanguage3D == StickerFontLanguage.ENGLISH
                                ) { fontLanguage3D = StickerFontLanguage.ENGLISH }
                            }
                            PanelLabel("الخط (${selectedLayer?.fontId ?: "-"})")
                            StudioToolRow {
                                stickerFontsFor(fontLanguage3D).forEach { font ->
                                    ToolChip(
                                        label = font.arabicName.ifBlank { font.id },
                                        selected = selectedLayer?.fontId == font.id
                                    ) {
                                        updateSelected { it.copy(fontId = font.id) }
                                    }
                                }
                            }
                        }
                        4 -> {
                            if (selectedLayer != null && !selectedLayer.isImage) {
                                PanelLabel("التكسية بالخامة")
                                StudioToolRow {
                                    ToolChip(
                                        if (selectedLayer.textureBitmap != null) {
                                            "تغيير التكسية"
                                        } else {
                                            "إضافة تكسية"
                                        }
                                    ) { texturePicker.launch("image/*") }
                                    if (selectedLayer.textureBitmap != null) {
                                        ToolChip("إزالة التكسية") {
                                            updateSelected { it.copy(textureBitmap = null) }
                                        }
                                    }
                                }
                                PanelLabel("التقويس")
                                SliderField(
                                    label = "انحناء النص",
                                    value = selectedLayer.curve,
                                    range = -0.6f..0.6f,
                                    valueText = "${(selectedLayer.curve * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(curve = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                PanelLabel("الحركة التلقائية")
                                StudioToolRow {
                                    LayerAnimation.entries.forEach { animation ->
                                        ToolChip(
                                            animation.label,
                                            selected = selectedLayer.animation == animation
                                        ) {
                                            updateSelected { it.copy(animation = animation) }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "اختر طبقة نصية لتعديل التأثيرات.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                        5 -> {
                            PanelLabel("هدف التحريك")
                            StudioToolRow {
                                ToolChip("النص", selected = moveTarget3D == 0) {
                                    moveTarget3D = 0
                                }
                                ToolChip("القاعدة", selected = moveTarget3D == 1) {
                                    moveTarget3D = 1
                                }
                                ToolChip("النموذج 3D", selected = moveTarget3D == 2) {
                                    moveTarget3D = 2
                                }
                                ToolChip(
                                    if (dragMode) "السحب مفعّل ✓" else "تفعيل السحب",
                                    selected = dragMode
                                ) { dragMode = !dragMode }
                            }
                            SwitchField(
                                label = "تسجيل الحركة أثناء السحب (مفاتيح تلقائية)",
                                checked = recordMotion,
                                onCheckedChange = { recordMotion = it }
                            )
                            Text(
                                text = if (recordMotion) {
                                    "وضع التسجيل مفعّل: كل سحب للنص أو النموذج يُسجَّل كمفتاح في الوقت الحالي."
                                } else {
                                    "فعّل التسجيل ثم اسحب النص أو النموذج لتسجيل حركته على الخط الزمني."
                                },
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            PanelLabel("تحريك سريع")
                            StudioToolRow {
                                NudgeChip("X-", dragMode) { nudge(-0.05f, 0f, 0f) }
                                NudgeChip("X+", dragMode) { nudge(0.05f, 0f, 0f) }
                                NudgeChip("Y-", dragMode) { nudge(0f, -0.05f, 0f) }
                                NudgeChip("Y+", dragMode) { nudge(0f, 0.05f, 0f) }
                                NudgeChip("Z-", dragMode) { nudge(0f, 0f, -0.05f) }
                                NudgeChip("Z+", dragMode) { nudge(0f, 0f, 0.05f) }
                            }
                            PanelLabel("تدوير وتكبير")
                            StudioToolRow {
                                NudgeChip("دوران X-", dragMode) { rotateBy(-6f, 0f, 0f) }
                                NudgeChip("دوران X+", dragMode) { rotateBy(6f, 0f, 0f) }
                                NudgeChip("دوران Y-", dragMode) { rotateBy(0f, -6f, 0f) }
                                NudgeChip("دوران Y+", dragMode) { rotateBy(0f, 6f, 0f) }
                                NudgeChip("دوران Z-", dragMode) { rotateBy(0f, 0f, -6f) }
                                NudgeChip("دوران Z+", dragMode) { rotateBy(0f, 0f, 6f) }
                                NudgeChip("تكبير", dragMode) { scaleBy(0.05f) }
                                NudgeChip("تصغير", dragMode) { scaleBy(-0.05f) }
                            }
                            PanelLabel("تبديل سريع")
                            StudioToolRow {
                                ToolChip("مركز النص") {
                                    if (moveTarget3D == 0) {
                                        updateSelected { it.copy(offsetX = 0f, offsetY = 0f, offsetZ = 0f) }
                                    } else if (moveTarget3D == 2) {
                                        pushUndo()
                                        modelOffsetX = 0f
                                        modelOffsetY = 0f
                                        modelOffsetZ = 0f
                                    } else {
                                        pushUndo()
                                        baseX = 0f
                                        baseY = 0f
                                        baseZ = 0f
                                    }
                                }
                                ToolChip("تصفير الدوران") {
                                    if (moveTarget3D == 0) {
                                        updateSelected {
                                            it.copy(rotationX = 0f, rotationY = 0f, rotationZ = 0f)
                                        }
                                    } else if (moveTarget3D == 2) {
                                        pushUndo()
                                        modelRotationX = 0f
                                        modelRotationY = 0f
                                        modelRotationZ = 0f
                                    } else {
                                        pushUndo()
                                        baseRotationX = 0f
                                        baseRotationY = 0f
                                        baseRotationZ = 0f
                                    }
                                }
                            }
                        }
                        6 -> {
                            PanelLabel("الخامة")
                            StudioToolRow {
                                MetalStyle.entries.forEach { style ->
                                    ToolChip(
                                        style.label,
                                        selected = selectedLayer?.style == style
                                    ) {
                                        pushUndo()
                                        metalStyle = style
                                        updateSelected(record = false) { it.copy(style = style) }
                                    }
                                }
                            }
                            PanelLabel("لون النص المستقل")
                            ColorPaletteRow(
                                selectedItem = selectedLayer?.color,
                                onPick = { color -> updateSelected { it.copy(color = color) } }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            StudioToolRow {
                                ToolChip("لون تلقائي", selected = selectedLayer?.color == null) {
                                    updateSelected { it.copy(color = null) }
                                }
                            }
                            if (selectedLayer != null && !selectedLayer.isImage) {
                                PanelLabel("خصائص الخامة (تجاوز النمط)")
                                SliderField(
                                    "اللمعان المعدني", selectedLayer.effectiveMetallic(),
                                    0f..1f, selectedLayer.effectiveMetallic().format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) {
                                            it.copy(metallicOverride = value)
                                        }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "الخشونة", selectedLayer.effectiveRoughness(),
                                    0f..1f, selectedLayer.effectiveRoughness().format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) {
                                            it.copy(roughnessOverride = value)
                                        }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "الانعكاس", selectedLayer.effectiveReflectance(),
                                    0f..1f, selectedLayer.effectiveReflectance().format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) {
                                            it.copy(reflectanceOverride = value)
                                        }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                StudioToolRow {
                                    ToolChip("خصائص تلقائية") {
                                        updateSelected {
                                            it.copy(
                                                metallicOverride = -1f,
                                                roughnessOverride = -1f,
                                                reflectanceOverride = -1f
                                            )
                                        }
                                    }
                                }
                            }
                            PanelLabel("توحيد اللون")
                            SwitchField(
                                label = "لون موحد للنص والقاعدة",
                                checked = unifiedColor,
                                onCheckedChange = { checked ->
                                    pushUndo()
                                    unifiedColor = checked
                                }
                            )
                        }
                        7 -> {
                            PanelLabel("شكل القاعدة")
                            StudioToolRow {
                                ShapeStyle.entries.forEach { shape ->
                                    ToolChip(shape.label, selected = shapeStyle == shape) {
                                        pushUndo()
                                        shapeStyle = shape
                                    }
                                }
                            }
                            if (shapeStyle != ShapeStyle.NONE) {
                                PanelLabel("موضع القاعدة")
                                SliderField(
                                    "X", baseX, -2f..2f, baseX.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseX = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "Y", baseY, -2f..1f, baseY.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseY = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "Z", baseZ, -2f..2f, baseZ.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseZ = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                PanelLabel("دوران القاعدة")
                                SliderField(
                                    "X", baseRotationX, -180f..180f, "${baseRotationX.toInt()}°",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseRotationX = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "Y", baseRotationY, -180f..180f, "${baseRotationY.toInt()}°",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseRotationY = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "Z", baseRotationZ, -180f..180f, "${baseRotationZ.toInt()}°",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseRotationZ = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                PanelLabel("حجم القاعدة")
                                SliderField(
                                    "الحجم", baseScale, 0.3f..2.5f, baseScale.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        baseScale = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                PanelLabel("لون القاعدة")
                                ColorPaletteRow(
                                    selectedItem = baseColorOverride,
                                    onPick = { color ->
                                        pushUndo()
                                        baseColorOverride = color
                                    }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                StudioToolRow {
                                    ToolChip(
                                        "لون تلقائي",
                                        selected = baseColorOverride == null
                                    ) {
                                        pushUndo()
                                        baseColorOverride = null
                                    }
                                }
                            }
                            SwitchField(
                                label = "تحريك النص والقاعدة معاً",
                                checked = moveTogether,
                                onCheckedChange = { checked ->
                                    pushUndo()
                                    moveTogether = checked
                                }
                            )
                        }
                        8 -> {
                            PanelLabel("نمط الإضاءة")
                            StudioToolRow {
                                LightMode.entries.forEach { mode ->
                                    ToolChip(mode.label, selected = lightMode == mode) {
                                        pushUndo()
                                        lightMode = mode
                                    }
                                }
                            }
                            PanelLabel("زاوية الضوء")
                            SliderField(
                                "الزاوية", lightAngle, 0f..360f, "${lightAngle.toInt()}°",
                                onValueChange = { value ->
                                    if (!sliderPushed3D) {
                                        pushUndo()
                                        sliderPushed3D = true
                                    }
                                    lightAngle = value
                                },
                                onValueChangeFinished = { sliderPushed3D = false }
                            )
                            SliderField(
                                "الارتفاع", lightElevation, 0f..90f, "${lightElevation.toInt()}°",
                                onValueChange = { value ->
                                    if (!sliderPushed3D) {
                                        pushUndo()
                                        sliderPushed3D = true
                                    }
                                    lightElevation = value
                                },
                                onValueChangeFinished = { sliderPushed3D = false }
                            )
                            PanelLabel("الدوران")
                            SwitchField(
                                label = "دوران تلقائي",
                                checked = autoRotate,
                                onCheckedChange = { checked ->
                                    pushUndo()
                                    autoRotate = checked
                                }
                            )
                            if (!autoRotate) {
                                SliderField(
                                    "زاوية الدوران", manualRotation, 0f..360f,
                                    "${manualRotation.toInt()}°",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        manualRotation = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                            }
                        }
                        9 -> {
                            PanelLabel("الخلفية")
                            StudioToolRow {
                                BackgroundMode.entries.forEach { mode ->
                                    ToolChip(mode.label, selected = backgroundMode == mode) {
                                        pushUndo()
                                        backgroundMode = mode
                                    }
                                }
                            }
                            when (backgroundMode) {
                                BackgroundMode.TRANSPARENT -> {
                                    Text(
                                        "سيتم تصدير الخلفية شفافة.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                BackgroundMode.COLOR -> {
                                    PanelLabel("لون الخلفية")
                                    ColorPaletteRow(
                                        selectedItem = backgroundColor,
                                        onPick = { color ->
                                            pushUndo()
                                            backgroundColor = color
                                        }
                                    )
                                }
                                BackgroundMode.IMAGE -> {
                                    StudioToolRow {
                                        ToolChip("اختيار صورة") {
                                            backgroundPicker.launch("image/*")
                                        }
                                        ToolChip("إزالة الصورة", enabled = backgroundImage != null) {
                                            pushUndo()
                                            backgroundImage = null
                                            backgroundMode = BackgroundMode.TRANSPARENT
                                        }
                                    }
                                }
                            }
                        }
                        10 -> {
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
                                    onValueChange = { value ->
                                        watermarkText = value
                                    },
                                    label = { Text("نص العلامة المائية") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ColorPaletteRow(
                                    selectedItem = watermarkColor,
                                    onPick = { color -> watermarkColor = color }
                                )
                                SliderField(
                                    "الشفافية", watermarkOpacity, 0.05f..1f,
                                    "${(watermarkOpacity * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        watermarkOpacity = value
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                            }
                        }
                        11 -> {
                            PanelLabel("نموذج ثلاثي الأبعاد (GLB / GLTF)")
                            StudioToolRow {
                                ToolChip("تحميل نموذج") { modelPicker.launch("*/*") }
                                ToolChip("إزالة النموذج", enabled = modelFile != null) {
                                    pushUndo()
                                    modelFile = null
                                }
                            }
                            Text(
                                text = if (modelFile != null) {
                                    "النموذج محمّل ويظهر بجانب الشكل."
                                } else {
                                    "أضف ملف GLB أو GLTF ليظهر بجانب النص والقاعدة."
                                },
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            PanelLabel("تحويل النموذج (Move / Rotate / Scale)")
                            SliderField("X", modelOffsetX, -3f..3f, modelOffsetX.format2(),
                                onValueChange = { modelOffsetX = it },
                                onValueChangeFinished = {})
                            SliderField("Y", modelOffsetY, -3f..3f, modelOffsetY.format2(),
                                onValueChange = { modelOffsetY = it },
                                onValueChangeFinished = {})
                            SliderField("Z", modelOffsetZ, -3f..3f, modelOffsetZ.format2(),
                                onValueChange = { modelOffsetZ = it },
                                onValueChangeFinished = {})
                            SliderField("دوران X", modelRotationX, -180f..180f, "${modelRotationX.toInt()}°",
                                onValueChange = { modelRotationX = it },
                                onValueChangeFinished = {})
                            SliderField("دوران Y", modelRotationY, -180f..180f, "${modelRotationY.toInt()}°",
                                onValueChange = { modelRotationY = it },
                                onValueChangeFinished = {})
                            SliderField("دوران Z", modelRotationZ, -180f..180f, "${modelRotationZ.toInt()}°",
                                onValueChange = { modelRotationZ = it },
                                onValueChangeFinished = {})
                            SliderField("الحجم", modelScale, 0.2f..2.5f, modelScale.format2(),
                                onValueChange = { modelScale = it },
                                onValueChangeFinished = {})
                            StudioToolRow {
                                ToolChip("دوران 360°") {
                                    modelRotationY = (modelRotationY + 360f)
                                }
                                ToolChip("إعادة ضبط") {
                                    modelOffsetX = 1.65f
                                    modelOffsetY = -0.35f
                                    modelOffsetZ = 0f
                                    modelRotationX = 0f
                                    modelRotationY = 0f
                                    modelRotationZ = 0f
                                    modelScale = 1f
                                }
                            }
                        }
                        12 -> {
                            if (selectedLayer != null && !selectedLayer.isImage) {
                                PanelLabel("هندسة النص")
                                SliderField(
                                    "السماكة (Extrusion)", selectedLayer.extrusion,
                                    0f..1f, "${(selectedLayer.extrusion * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(extrusion = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "حجم الخط", selectedLayer.fontSize,
                                    40f..180f, "${selectedLayer.fontSize.toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(fontSize = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "تباعد الحروف", selectedLayer.letterSpacing,
                                    -0.08f..0.5f, selectedLayer.letterSpacing.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(letterSpacing = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "تباعد الكلمات", selectedLayer.wordSpacing,
                                    0f..1f, selectedLayer.wordSpacing.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(wordSpacing = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "تباعد الأسطر", selectedLayer.lineSpacing,
                                    0.6f..2.2f, selectedLayer.lineSpacing.format2(),
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(lineSpacing = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "سماكة الحدود", selectedLayer.outlineWidth,
                                    0f..16f, "${selectedLayer.outlineWidth.toInt()}px",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(outlineWidth = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "الشطف (Bevel)", selectedLayer.bevel,
                                    0f..1f, "${(selectedLayer.bevel * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(bevel = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                SliderField(
                                    "الشفافية", selectedLayer.opacity,
                                    0.1f..1f, "${(selectedLayer.opacity * 100).toInt()}%",
                                    onValueChange = { value ->
                                        if (!sliderPushed3D) {
                                            pushUndo()
                                            sliderPushed3D = true
                                        }
                                        updateSelected(record = false) { it.copy(opacity = value) }
                                    },
                                    onValueChangeFinished = { sliderPushed3D = false }
                                )
                                PanelLabel("محاذاة النص")
                                StudioToolRow {
                                    ToolChip("يسار", selected = selectedLayer.alignment == 0) {
                                        updateSelected { it.copy(alignment = 0) }
                                    }
                                    ToolChip("وسط", selected = selectedLayer.alignment == 1) {
                                        updateSelected { it.copy(alignment = 1) }
                                    }
                                    ToolChip("يمين", selected = selectedLayer.alignment == 2) {
                                        updateSelected { it.copy(alignment = 2) }
                                    }
                                }
                                PanelLabel("لون الحدود")
                                ColorPaletteRow(
                                    selectedItem = selectedLayer.outlineColor,
                                    onPick = { color ->
                                        updateSelected { it.copy(outlineColor = color) }
                                    }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                StudioToolRow {
                                    ToolChip("حروف متباعدة") {
                                        updateSelected { it.copy(letterSpacing = 0.14f) }
                                    }
                                    ToolChip("إعادة ضبط الهندسة") {
                                        updateSelected {
                                            it.copy(
                                                fontSize = 100f,
                                                letterSpacing = 0f,
                                                wordSpacing = 0f,
                                                lineSpacing = 1f,
                                                outlineWidth = 0f,
                                                bevel = 0f,
                                                extrusion = 1f,
                                                opacity = 1f,
                                                alignment = 1
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "اختر طبقة نصية لتعديل هندسة النص.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                        13 -> {
                            PanelLabel("عرض الكاميرا")
                            StudioToolRow {
                                ToolChip("أمام") { applyCameraView(0) }
                                ToolChip("خلف") { applyCameraView(1) }
                                ToolChip("يسار") { applyCameraView(2) }
                                ToolChip("يمين") { applyCameraView(3) }
                                ToolChip("أعلى") { applyCameraView(4) }
                                ToolChip("أسفل") { applyCameraView(5) }
                                ToolChip("منظور") { applyCameraView(6) }
                            }
                            PanelLabel("مسار حركة الكاميرا")
                            StudioToolRow {
                                CameraPathPreset.entries.forEach { preset ->
                                    ToolChip(
                                        preset.labelAr,
                                        selected = cameraPathPreset == preset
                                    ) {
                                        cameraPathPreset = preset
                                        val path = buildCameraPathPreset(preset, timelineDuration().toLong())
                                        cameraKeyframes.clear()
                                        cameraKeyframes.addAll(path)
                                        cameraPathActive = true
                                    }
                                }
                            }
                            StudioToolRow {
                                ToolChip(
                                    if (cameraPathActive) "تعطيل المسار" else "تفعيل المسار",
                                    selected = cameraPathActive,
                                    enabled = cameraKeyframes.isNotEmpty()
                                ) {
                                    cameraPathActive = !cameraPathActive
                                }
                                ToolChip("إضافة مفتاح كاميرا") {
                                    addCameraKeyframeAtPlayhead()
                                }
                                ToolChip(
                                    "تسجيل حركة الكاميرا",
                                    selected = cameraPathRecording
                                ) {
                                    cameraPathRecording = !cameraPathRecording
                                    if (cameraPathRecording) cameraPathActive = true
                                }
                                ToolChip("مسح المسار", enabled = cameraKeyframes.isNotEmpty()) {
                                    cameraKeyframes.clear()
                                    cameraPathActive = false
                                    cameraPathRecording = false
                                }
                            }
                            if (cameraKeyframes.isNotEmpty()) {
                                StudioToolRow {
                                    cameraKeyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                                        ToolChip(
                                            "${keyframe.timeMs}ms",
                                            selected = timelinePlayheadMs == keyframe.timeMs
                                        ) {
                                            seekTimeline(keyframe.timeMs.toFloat())
                                            cameraNode.position = keyframe.position
                                            cameraNode.lookAt(keyframe.lookAt)
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "المفاتيح المسجلة: ${cameraKeyframes.size} • ${if (cameraPathActive) "المسار نشط" else "المسار متوقف"}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                "اسحب المشهد للتدوير، واستخدم إصبعين للتكبير والتحريك. " +
                                    "أو فعّل تسجيل حركة الكاميرا وشغّل الخط الزمني ثم حرّك المشهد لتسجيل المسار.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            PanelLabel("الخلفية والإضاءة")
                            StudioToolRow {
                                ToolChip("وضع الأداء", selected = !autoRotate) {
                                    pushUndo()
                                    autoRotate = false
                                }
                                ToolChip("وضع الجودة", selected = autoRotate) {
                                    pushUndo()
                                    autoRotate = true
                                }
                            }
                            SwitchField(
                                label = "دوران تلقائي للمشهد",
                                checked = autoRotate,
                                onCheckedChange = { checked ->
                                    pushUndo()
                                    autoRotate = checked
                                }
                            )
                        }
                        14 -> {
                            PanelLabel("الخط الزمني")
                            StudioToolRow {
                                ToolChip(
                                    if (timelinePlaying) "إيقاف مؤقت" else "تشغيل",
                                    selected = timelinePlaying,
                                    onClick = { toggleTimelinePlay() }
                                )
                                ToolChip("إيقاف") { stopTimeline() }
                                ToolChip("عكس", selected = timelineReverse) {
                                    timelineReverse = !timelineReverse
                                }
                                ToolChip("تكرار", selected = timelineLoop) {
                                    timelineLoop = !timelineLoop
                                }
                            }
                            SliderField(
                                "الوقت", timelinePlayheadMs.toFloat(),
                                0f..timelineDurationMs,
                                "${timelinePlayheadMs}ms / ${timelineDuration().toInt()}ms",
                                onValueChange = { seekTimeline(it) },
                                onValueChangeFinished = {}
                            )
                            SliderField(
                                "المدة", timelineDurationMs,
                                1000f..15000f, "${(timelineDurationMs / 1000f).format2()}s",
                                onValueChange = { timelineDurationMs = it },
                                onValueChangeFinished = {}
                            )
                            SliderField(
                                "سرعة التشغيل", timelineSpeed,
                                0.25f..3f, "${timelineSpeed.format2()}x",
                                onValueChange = { timelineSpeed = it },
                                onValueChangeFinished = {}
                            )
                            PanelLabel("الانسيابية")
                            StudioToolRow {
                                AnimationEasing.entries.forEach { easing ->
                                    ToolChip(
                                        easing.label,
                                        selected = timelineEasing == easing
                                    ) { timelineEasing = easing }
                                }
                            }
                            PanelLabel("مفاتيح الحركة (${keyframes.size})")
                            StudioToolRow {
                                ToolChip("إضافة مفتاح +") { addKeyframeAtPlayhead() }
                                ToolChip(
                                    "تسجيل بالسحب",
                                    selected = recordMotion
                                ) { recordMotion = !recordMotion }
                                ToolChip("مسح الحركة", enabled = keyframes.isNotEmpty()) {
                                    keyframes.clear()
                                }
                            }
                            PanelLabel("مفاتيح حركة النموذج (${modelKeyframes.size})")
                            StudioToolRow {
                                ToolChip("مفتاح نموذج +") { addModelKeyframeAtPlayhead() }
                                ToolChip("مسح حركة النموذج", enabled = modelKeyframes.isNotEmpty()) {
                                    modelKeyframes.clear()
                                }
                            }
                            if (keyframes.isNotEmpty()) {
                                StudioToolRow {
                                    keyframes.sortedBy { it.timeMs }.forEach { keyframe ->
                                        ToolChip(
                                            "${keyframe.timeMs}ms",
                                            selected = timelinePlayheadMs == keyframe.timeMs
                                        ) {
                                            seekTimeline(keyframe.timeMs.toFloat())
                                        }
                                    }
                                }
                            }
                            PanelLabel("حركات جاهزة")
                            StudioToolRow {
                                StudioAnimationPreset.entries.forEach { preset ->
                                    ToolChip(preset.label) { applyAnimationPreset(preset) }
                                }
                            }
                        }
                        15 -> {
                            PanelLabel("إدارة المشروع")
                            StudioToolRow {
                                ToolChip("حفظ المشروع") { saveProject(true) }
                                ToolChip(
                                    "استعادة آخر مشروع",
                                    enabled = StudioProjectStore.hasProject(context)
                                ) { restoreProject() }
                                ToolChip("مشروع جديد") { newProject() }
                            }
                            Text(
                                text = "يُحفظ المشروع تلقائياً عند مغادرة الاستوديو. " +
                                    "الحفظ يشمل الإطارات والطبقات والتحويلات والخامات والحركة.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            PanelLabel("ملخص المشروع")
                            Text(
                                text = "الطبقات: ${layers.size} • الإطارات: ${frames.size} • " +
                                    "مفاتيح الحركة: ${keyframes.size} • النموذج: " +
                                    if (modelFile != null) "محمّل" else "بدون",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp
                            )
                            PanelLabel("التصدير")
                            StudioToolRow {
                                ToolChip("تصدير PNG شفاف") { captureScene() }
                                ToolChip(
                                    "تصدير GIF متحرك",
                                    enabled = !isExporting
                                ) { exportGif() }
                                ToolChip(
                                    "تصدير فيديو MP4",
                                    enabled = !isExporting
                                ) { exportMp4() }
                            }
                            if (isExporting) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "جارٍ التصدير...",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = exportProgress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                }
                            }
                            Text(
                                text = "يُسجل التصديرmovement المشهد أثناء تشغيل الخط الزمني. " +
                                    "للحصول على أفضل نتيجة، فعلّ مسار الكاميرا أو حركات الطبقات قبل التصدير.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        16 -> {
                            PanelLabel("الملصقات المتوفرة (بدون إنترنت)")
                            Text(
                                text = "الملصقات العادية تُضاف كصور والنماذج ثلاثية الأبعاد تُضاف بجانب النص. كلها مدمجة وتعمل بدون إنترنت.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                            PanelLabel("الفئة")
                            StudioToolRow {
                                val categories = stickerRepository.categories()
                                if (categories.isEmpty()) {
                                    ToolChip("لا توجد ملصقات", enabled = false) {}
                                } else {
                                    ToolChip(
                                        "الجميع",
                                        selected = stickerCategoryFilter == null
                                    ) { stickerCategoryFilter = null }
                                    categories.forEach { cat ->
                                        ToolChip(
                                            cat,
                                            selected = stickerCategoryFilter == cat
                                        ) { stickerCategoryFilter = cat }
                                    }
                                }
                            }
                            PanelLabel("البحث")
                            androidx.compose.material3.OutlinedTextField(
                                value = stickerSearch,
                                onValueChange = { stickerSearch = it },
                                label = { Text("ابحث عن ملصق...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PanelLabel("الملصقات (${filteredStickers.size})")
                            StudioToolRow {
                                val stickers = filteredStickers
                                if (stickers.isEmpty()) {
                                    Text(
                                        text = "لا توجد ملصقات مطابقة. جرّب بحثاً أو فئة أخرى.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        stickers.forEach { sticker ->
                                            val is3D = sticker.type == StudioStickerType.MODEL_3D
                                            val thumb = remember(sticker.id) {
                                                stickerRepository.loadStickerBitmap(
                                                    sticker.previewAssetPath ?: sticker.assetPath
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .width(78.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { addStickerToScene(sticker) }
                                                    .padding(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF1E2749)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (thumb != null) {
                                                        Image(
                                                            bitmap = thumb.asImageBitmap(),
                                                            contentDescription = sticker.name,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.size(58.dp)
                                                        )
                                                    } else {
                                                        Text(if (is3D) "3D" else "؟", color = Color.White)
                                                    }
                                                }
                                                Text(
                                                    text = sticker.name,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(top = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (stickerRepository.allStickers.isEmpty()) {
                                PanelLabel("تنبيه")
                                Text(
                                    text = "لا يوجد كتالوج ملصقات مدمج بعد. أضف ملف src/main/assets/stickers/catalog.json " +
                                        "وجمّل صور الملصقات (PNG) والنماذج ثلاثية الأبعاد (GLB) تحت src/main/assets/stickers/. " +
                                        "سيعمل الكتالوج بالكامل بدون إنترنت داخل APK.",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 6.dp)
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
            ToolChip("مسح") { resetScene() }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF22D3EE))
                        )
                    )
                    .clickable { captureScene() }
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
}

@Composable
private fun NudgeChip(
    label: String,
    dragMode: Boolean,
    onClick: () -> Unit
) {
    ToolChip(label = label, enabled = !dragMode, onClick = onClick)
}
