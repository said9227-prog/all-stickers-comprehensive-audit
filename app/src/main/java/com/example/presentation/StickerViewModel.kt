package com.example.presentation

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.conversion.BatchConversionProgress
import com.example.conversion.StickerConversionEngine
import com.example.creator.StickerCreatorEngine
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.StickerPreview
import com.example.core.model.StickerType
import com.example.core.model.StudioImage
import com.example.core.model.ValidationResult
import com.example.data.db.AppDatabase
import com.example.data.repository.PackRepository
import com.example.data.repository.StickerRepository
import com.example.data.repository.StudioRepository
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.media.MediaDetector
import com.example.storage.SafeZipExtractor
import com.example.storage.TempFileManager
import com.example.telegram.TelegramStickerValidator
import com.example.whatsapp.WhatsAppExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class StickerCreatorEvent {
    data class Saved(val packId: Long) : StickerCreatorEvent()
    data class Failed(val message: String) : StickerCreatorEvent()
}

data class PackExportTarget(
    val pack: StickerPack,
    val stickers: List<StickerItem>
)

data class DetectedImportItem(
    val uriString: String,
    val name: String,
    val type: StickerType,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val mimeType: String? = null
)

class StickerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val packRepository = PackRepository(db.stickerDao())
    private val stickerRepository = StickerRepository(db.stickerDao())
    private val studioRepository = StudioRepository(application)
    val studioImages: StateFlow<List<StudioImage>> = studioRepository.images
    private val _isCreatingStudioPack = MutableStateFlow(false)
    val isCreatingStudioPack: StateFlow<Boolean> = _isCreatingStudioPack.asStateFlow()
    val tempFileManager = TempFileManager(application)
    private val conversionEngine = StickerConversionEngine(application, tempFileManager, stickerRepository)
    private val safeZipExtractor = SafeZipExtractor()

    val packs: StateFlow<List<StickerPack>> = packRepository.getAllPacks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Animated/static thumbnails per pack for the home screen. Only a small
     * projection is read from the database and at most four previews per pack
     * are kept, so the list stays light on low-end devices.
     */
    val packPreviews: StateFlow<Map<Long, List<StickerPreview>>> =
        stickerRepository.getStickerPreviewsFlow(maxPerPack = 4)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _currentPack = MutableStateFlow<StickerPack?>(null)
    val currentPack: StateFlow<StickerPack?> = _currentPack.asStateFlow()

    private val _currentStickers = MutableStateFlow<List<StickerItem>>(emptyList())
    val currentStickers: StateFlow<List<StickerItem>> = _currentStickers.asStateFlow()

    private val _packExportTarget = MutableStateFlow<PackExportTarget?>(null)
    val packExportTarget: StateFlow<PackExportTarget?> = _packExportTarget.asStateFlow()

    private val _conversionProgress = MutableStateFlow<BatchConversionProgress?>(null)
    val conversionProgress: StateFlow<BatchConversionProgress?> = _conversionProgress.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importDetectedItems = MutableStateFlow<List<DetectedImportItem>>(emptyList())
    val importDetectedItems: StateFlow<List<DetectedImportItem>> = _importDetectedItems.asStateFlow()

    private val _creatorEvents = MutableSharedFlow<StickerCreatorEvent>(extraBufferCapacity = 4)
    val creatorEvents: SharedFlow<StickerCreatorEvent> = _creatorEvents.asSharedFlow()

    private var conversionJob: Job? = null
    private var stickerCollectionJob: Job? = null

    init {
        // Clean temporary files from previous runs (never touches permanent files in filesDir/stickers)
        viewModelScope.launch(Dispatchers.IO) {
            tempFileManager.cleanTempDirectory()
        }
    }

    fun selectPack(packId: Long) {
        stickerCollectionJob?.cancel()
        viewModelScope.launch {
            _currentPack.value = packRepository.getPackById(packId)
            stickerCollectionJob = launch {
                stickerRepository.getStickersByPackIdFlow(packId).collect { list ->
                    _currentStickers.value = list
                }
            }
        }
    }

    fun createPack(
        name: String,
        shortName: String,
        author: String,
        defaultEmoji: String,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val pack = StickerPack(
                name = name.ifBlank { "حزمة ملصقات جديدة" },
                shortName = shortName.ifBlank { "pack_" + System.currentTimeMillis() },
                author = author,
                defaultEmoji = defaultEmoji.ifBlank { "✨" }
            )
            val newId = packRepository.createPack(pack)
            selectPack(newId)
            onCreated(newId)
        }
    }

    fun updatePack(pack: StickerPack) {
        viewModelScope.launch {
            packRepository.updatePack(pack)
            if (_currentPack.value?.id == pack.id) {
                _currentPack.value = pack
            }
            val target = _packExportTarget.value
            if (target != null && target.pack.id == pack.id) {
                _packExportTarget.value = target.copy(pack = pack)
            }
        }
    }

    /**
     * Loads a pack and its stickers on demand so the export dialog can be opened
     * directly from the pack list without entering the pack details screen.
     */
    fun openExportForPack(packId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val pack = packRepository.getPackById(packId) ?: return@launch
            val stickers = stickerRepository.getStickersByPackId(packId)
            _packExportTarget.value = PackExportTarget(pack = pack, stickers = stickers)
        }
    }

    fun closeExportDialog() {
        _packExportTarget.value = null
    }

    fun deletePack(packId: Long) {
        viewModelScope.launch {
            packRepository.deletePack(packId)
            tempFileManager.deletePackFiles(packId)
            withContext(Dispatchers.IO) {
                WhatsAppExporter.deleteExportedPacks(getApplication(), packId)
            }
            if (_currentPack.value?.id == packId) {
                _currentPack.value = null
                _currentStickers.value = emptyList()
            }
        }
    }

    /**
     * Deletes several packs in one background operation. Database rows and the
     * managed sticker files are removed for every pack, together with any
     * exported WhatsApp pack records.
     */
    fun deletePacks(packIds: List<Long>) {
        val uniqueIds = packIds.distinct()
        if (uniqueIds.isEmpty()) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            uniqueIds.forEach { packId ->
                try {
                    packRepository.deletePack(packId)
                    tempFileManager.deletePackFiles(packId)
                    withContext(Dispatchers.IO) {
                        WhatsAppExporter.deleteExportedPacks(context, packId)
                    }
                } catch (_: Exception) {
                    // Keep going: one bad pack must not block the rest of the batch.
                }
                if (_currentPack.value?.id == packId) {
                    _currentPack.value = null
                    _currentStickers.value = emptyList()
                }
            }
        }
    }

    /**
     * Renames a pack without touching its id, sticker rows or managed files.
     * The new title is trimmed and ignored when blank.
     */
    fun renamePack(packId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val pack = packRepository.getPackById(packId) ?: return@launch
            val updated = pack.copy(name = trimmed)
            packRepository.updatePack(updated)
            if (_currentPack.value?.id == packId) {
                _currentPack.value = updated
            }
            val target = _packExportTarget.value
            if (target != null && target.pack.id == packId) {
                _packExportTarget.value = target.copy(pack = updated)
            }
        }
    }

    fun inspectUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            val items = mutableListOf<DetectedImportItem>()
            val context = getApplication<Application>()

            for (uri in uris) {
                try {
                    // Gallery first-class URI checks
                    if (uri == null) continue
                    val scheme = uri.scheme ?: ""
                    val authority = uri.authority ?: ""
                    
                    if (scheme != "content" && scheme != "file") {
                        throw IllegalArgumentException("Unsupported URI scheme: '$scheme'")
                    }
                    if (authority.isBlank() && scheme == "content") {
                        throw IllegalArgumentException("Invalid empty authority content URI")
                    }

                    // Query ContentResolver OpenableColumns for actual original name and size
                    var displayName = "sticker"
                    var originalSize = 0L
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIdx != -1) {
                                    displayName = cursor.getString(nameIdx) ?: "sticker"
                                }
                                if (sizeIdx != -1) {
                                    originalSize = cursor.getLong(sizeIdx)
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val cleanName = File(displayName).nameWithoutExtension
                    val mime = context.contentResolver.getType(uri) ?: "image/octet-stream"

                    // Try opening the stream
                    val temp = tempFileManager.createTempFile("inspect_", ".tmp")
                    val openSuccess = try {
                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                            FileOutputStream(temp).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (!openSuccess || !temp.exists() || temp.length() == 0L) {
                        temp.delete()
                        throw java.io.IOException("ContentResolver stream read failed or empty file generated")
                    }

                    val detected = MediaDetector.detect(temp)
                    val fileSizeToUse = if (originalSize > 0L) originalSize else temp.length()

                    items.add(
                        DetectedImportItem(
                            uriString = uri.toString(),
                            name = cleanName,
                            type = detected.type,
                            sizeBytes = fileSizeToUse,
                            width = detected.width,
                            height = detected.height,
                            durationMs = detected.durationMs,
                            mimeType = mime
                        )
                    )
                    temp.delete()
                } catch (e: Exception) {
                    DiagnosticsRepository.logError(
                        context = context,
                        operation = "SAF_INSPECT",
                        stage = DiagnosticStage.IMPORT,
                        fileName = uri.lastPathSegment ?: "unknown",
                        uriString = uri.toString(),
                        exception = e,
                        customMessage = "Failed to inspect selected document: ${e.message}"
                    )
                }
            }
            _importDetectedItems.value = items
            _isImporting.value = false
        }
    }

    fun commitImportToPack(packId: Long, items: List<DetectedImportItem>, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val pack = packRepository.getPackById(packId)
            val defaultEmoji = pack?.defaultEmoji ?: "✨"
            val existing = stickerRepository.getStickersByPackId(packId)
            var currentOrder = existing.size

            val newStickers = mutableListOf<StickerItem>()

            for (item in items) {
                currentOrder++
                try {
                    val ext = when (item.type) {
                        StickerType.ANIMATED_WEBP, StickerType.STATIC_WEBP -> "webp"
                        StickerType.PNG -> "png"
                        StickerType.JPG -> "jpg"
                        StickerType.WEBM -> "webm"
                        else -> "bin"
                    }

                    // Copy SAF stream to permanent managed storage
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(item.uriString))
                    if (inputStream == null) {
                        DiagnosticsRepository.logError(
                            context = context,
                            operation = "IMPORT_COMMIT",
                            stage = DiagnosticStage.URI_OPEN,
                            fileName = item.name,
                            uriString = item.uriString,
                            customMessage = "Could not open stream from selected URI"
                        )
                        continue
                    }

                    val (stableFile, stableUri) = inputStream.use { stream ->
                        tempFileManager.saveStreamToPackOriginals(packId, stream, "${item.name}.$ext")
                    }

                    // Validate that ContentResolver can read the permanent FileProvider URI
                    var canRead = false
                    try {
                        context.contentResolver.openInputStream(stableUri)?.use {
                            canRead = true
                        }
                    } catch (e: Exception) {
                        DiagnosticsRepository.logError(
                            context = context,
                            operation = "IMPORT_COMMIT",
                            stage = DiagnosticStage.URI_OPEN,
                            fileName = stableFile.name,
                            file = stableFile,
                            uriString = stableUri.toString(),
                            exception = e,
                            customMessage = "Failed to verify ContentResolver access to permanent URI"
                        )
                    }

                    if (!canRead) continue

                    newStickers.add(
                        StickerItem(
                            packId = packId,
                            originalUri = stableUri.toString(),
                            originalName = item.name,
                            displayName = item.name,
                            emoji = defaultEmoji,
                            type = item.type,
                            originalSize = stableFile.length(),
                            width = item.width,
                            height = item.height,
                            durationMs = item.durationMs,
                            sortOrder = currentOrder,
                            sourceUri = item.uriString,
                            sourceMimeType = item.mimeType,
                            sourceDisplayName = item.name
                        )
                    )
                } catch (e: Exception) {
                    DiagnosticsRepository.logError(
                        context = context,
                        operation = "IMPORT_COMMIT",
                        stage = DiagnosticStage.STORE,
                        fileName = item.name,
                        uriString = item.uriString,
                        exception = e,
                        customMessage = "Failed to store imported sticker to managed storage: ${e.message}"
                    )
                }
            }

            stickerRepository.addStickers(newStickers)
            _importDetectedItems.value = emptyList()
            withContext(Dispatchers.Main) {
                selectPack(packId)
                onComplete()
            }
        }
    }

    fun importZipArchive(
        zipUri: Uri,
        packNameOverride: String?,
        authorOverride: String?,
        onComplete: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            val context = getApplication<Application>()
            val extractDir = tempFileManager.createTempDirectory("zip_import_")

            try {
                // 1. Open ZIP Stream
                val inStream = context.contentResolver.openInputStream(zipUri)
                if (inStream == null) {
                    DiagnosticsRepository.logError(
                        context = context,
                        operation = "ZIP_IMPORT",
                        stage = DiagnosticStage.IMPORT,
                        fileName = zipUri.lastPathSegment ?: "archive.zip",
                        uriString = zipUri.toString(),
                        customMessage = "Could not open input stream from ZIP URI"
                    )
                    withContext(Dispatchers.Main) {
                        onError("تعذر فتح ملف الأرشيف")
                        _isImporting.value = false
                    }
                    return@launch
                }

                // 2. Extract into temporary working directory
                val result = try {
                    safeZipExtractor.extract(inStream, extractDir)
                } catch (e: Exception) {
                    DiagnosticsRepository.logError(
                        context = context,
                        operation = "ZIP_IMPORT",
                        stage = DiagnosticStage.EXTRACT,
                        fileName = zipUri.lastPathSegment ?: "archive.zip",
                        exception = e,
                        customMessage = "SafeZipExtractor failed: ${e.message}"
                    )
                    throw e
                }

                if (result.extractedFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError("لم يتم العثور على ملصقات صالحة في الملف")
                        _isImporting.value = false
                    }
                    return@launch
                }

                // 3. Determine Pack Details
                val finalPackName = packNameOverride?.ifBlank { null }
                    ?: result.packTitle
                    ?: "حزمة ملصقات ${System.currentTimeMillis() % 1000}"

                val finalAuthor = authorOverride?.ifBlank { null }
                    ?: result.packAuthor
                    ?: ""

                val shortName = "pack_" + (finalPackName.replace("[^\\w]".toRegex(), "_").lowercase())

                val pack = StickerPack(
                    name = finalPackName,
                    shortName = shortName,
                    author = finalAuthor,
                    defaultEmoji = "✨"
                )

                val packId = packRepository.createPack(pack)

                // 4. Move extracted files to PERMANENT managed sticker storage & create stable FileProvider URIs
                val stickersToInsert = mutableListOf<StickerItem>()

                for ((index, extracted) in result.extractedFiles.withIndex()) {
                    val rawFile = extracted.file

                    // Stage: VALIDATE
                    if (!rawFile.exists() || !rawFile.isFile || rawFile.length() == 0L) {
                        DiagnosticsRepository.logError(
                            context = context,
                            operation = "ZIP_IMPORT",
                            stage = DiagnosticStage.VALIDATE,
                            fileName = rawFile.name,
                            file = rawFile,
                            customMessage = "Extracted file does not exist or is empty"
                        )
                        continue
                    }

                    // Stage: STORE (Move/Copy to managed permanent storage)
                    val preferredName = "${extracted.cleanDisplayName}.${rawFile.extension}"
                    val (stableFile, stableUri) = try {
                        tempFileManager.saveToPackOriginals(packId, rawFile, preferredName)
                    } catch (e: Exception) {
                        DiagnosticsRepository.logError(
                            context = context,
                            operation = "ZIP_IMPORT",
                            stage = DiagnosticStage.STORE,
                            fileName = rawFile.name,
                            file = rawFile,
                            exception = e,
                            customMessage = "Failed to copy extracted file to permanent pack storage"
                        )
                        continue
                    }

                    // Stage: URI_VALIDATE & URI_OPEN
                    var isStreamOpenable = false
                    try {
                        context.contentResolver.openInputStream(stableUri)?.use {
                            isStreamOpenable = true
                        }
                    } catch (e: Exception) {
                        DiagnosticsRepository.logError(
                            context = context,
                            operation = "ZIP_IMPORT",
                            stage = DiagnosticStage.URI_OPEN,
                            fileName = stableFile.name,
                            file = stableFile,
                            uriString = stableUri.toString(),
                            exception = e,
                            customMessage = "ContentResolver cannot open generated FileProvider URI"
                        )
                    }

                    if (!isStreamOpenable) {
                        continue
                    }

                    // Detect format from stable file
                    val detected = MediaDetector.detect(stableFile)

                    DiagnosticsRepository.logInfo(
                        operation = "ZIP_IMPORT",
                        stage = DiagnosticStage.STORE,
                        fileName = stableFile.name,
                        message = "Sticker successfully extracted and stored in managed storage: ${stableFile.name} (${detected.type.displayNameAr})"
                    )

                    stickersToInsert.add(
                        StickerItem(
                            packId = packId,
                            originalUri = stableUri.toString(),
                            originalName = extracted.cleanDisplayName,
                            displayName = extracted.cleanDisplayName,
                            emoji = "✨",
                            type = detected.type,
                            originalSize = stableFile.length(),
                            width = detected.width,
                            height = detected.height,
                            durationMs = detected.durationMs,
                            sortOrder = index,
                            sourceUri = "zip_entry://" + extracted.cleanDisplayName,
                            sourceMimeType = if (detected.type.isAnimated) "video/webm" else "image/webp",
                            sourceDisplayName = extracted.cleanDisplayName
                        )
                    )
                }

                if (stickersToInsert.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError("تعذر استيراد أي ملصقات صالحة من الأرشيف")
                        _isImporting.value = false
                    }
                    return@launch
                }

                stickerRepository.addStickers(stickersToInsert)

                // Clean up temporary extraction folder now that all files are in permanent storage
                try {
                    extractDir.deleteRecursively()
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    selectPack(packId)
                    _isImporting.value = false
                    onComplete(packId)
                }
            } catch (e: Exception) {
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "ZIP_IMPORT",
                    stage = DiagnosticStage.IMPORT,
                    fileName = zipUri.lastPathSegment ?: "archive.zip",
                    uriString = zipUri.toString(),
                    exception = e,
                    customMessage = "ZIP import operation failed: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "فشل استخراج الأرشيف")
                    _isImporting.value = false
                }
            }
        }
    }

    /**
     * Saves a locally rendered sticker (PNG with transparency) into a pack,
     * creates the pack when needed, and converts the sticker to Telegram format.
     */
    fun saveCreatedSticker(
        bitmap: Bitmap,
        displayName: String,
        emoji: String,
        targetPackId: Long?,
        newPackName: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val packId = targetPackId ?: packRepository.createPack(
                    StickerPack(
                        name = newPackName?.ifBlank { null } ?: "ملصقاتي",
                        shortName = "pack_" + (System.currentTimeMillis() % 1000000),
                        author = "",
                        defaultEmoji = emoji.ifBlank { "✨" }
                    )
                )

                val safeName = displayName.ifBlank { "ملصق" }
                val emojiToUse = emoji.ifBlank { "✨" }

                tempFile = tempFileManager.createTempFile("created_", ".png")
                if (!StickerCreatorEngine.savePng(bitmap, tempFile)) {
                    throw java.io.IOException("تعذر إنشاء ملف PNG للملصق")
                }

                val (stableFile, stableUri) = tempFileManager.saveToPackOriginals(
                    packId,
                    tempFile,
                    "$safeName.png"
                )

                val existing = stickerRepository.getStickersByPackId(packId)
                val item = StickerItem(
                    packId = packId,
                    originalUri = stableUri.toString(),
                    originalName = "$safeName.png",
                    displayName = safeName,
                    emoji = emojiToUse,
                    type = StickerType.PNG,
                    originalSize = stableFile.length(),
                    width = bitmap.width,
                    height = bitmap.height,
                    sortOrder = existing.size,
                    sourceUri = stableUri.toString(),
                    sourceMimeType = "image/png",
                    sourceDisplayName = safeName
                )
                val insertedId = stickerRepository.addSticker(item)
                val inserted = stickerRepository.getStickerById(insertedId)
                if (inserted != null) {
                    conversionEngine.convertSticker(inserted)
                }

                withContext(Dispatchers.Main) {
                    selectPack(packId)
                }
                _creatorEvents.tryEmit(StickerCreatorEvent.Saved(packId))
            } catch (e: Exception) {
                _creatorEvents.tryEmit(
                    StickerCreatorEvent.Failed(e.message ?: "تعذر حفظ الملصق")
                )
            } finally {
                try { tempFile?.delete() } catch (_: Exception) {}
            }
        }
    }

    fun createPackFromStudioImages(
        images: List<StudioImage>,
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (images.isEmpty()) {
            onError("اختر صورة واحدة على الأقل")
            return
        }
        if (_isCreatingStudioPack.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isCreatingStudioPack.value = true
            var createdPackId: Long? = null
            try {
                val context = getApplication<Application>()
                val packName = if (images.size == 1) {
                    "استوديو ${images.first().name}".take(60)
                } else {
                    "استوديو ${images.size} ملصقات"
                }
                val packId = packRepository.createPack(
                    StickerPack(
                        name = packName,
                        shortName = "studio_" + System.currentTimeMillis().toString().takeLast(8),
                        author = "",
                        defaultEmoji = "✨"
                    )
                )
                createdPackId = packId

                images.forEachIndexed { index, image ->
                    val sourceUri = Uri.parse(studioRepository.uriFor(image))
                    val loaded = StickerCreatorEngine.loadBitmap(context, sourceUri)
                        ?: throw java.io.IOException("تعذر فتح الصورة: ${image.name}")
                    val square = StickerCreatorEngine.cropToCanvas(loaded)
                    val safeName = image.name
                        .ifBlank { "ملصق ${index + 1}" }
                        .take(40)

                    val tempFile = tempFileManager.createTempFile("studio_", ".png")
                    try {
                        if (!StickerCreatorEngine.savePng(square, tempFile)) {
                            throw java.io.IOException("تعذر إنشاء ملف الملصق")
                        }
                        val (stableFile, stableUri) = tempFileManager.saveToPackOriginals(
                            packId,
                            tempFile,
                            "$safeName.png"
                        )
                        val existing = stickerRepository.getStickersByPackId(packId)
                        val item = StickerItem(
                            packId = packId,
                            originalUri = stableUri.toString(),
                            originalName = "$safeName.png",
                            displayName = safeName,
                            emoji = "✨",
                            type = StickerType.PNG,
                            originalSize = stableFile.length(),
                            width = square.width,
                            height = square.height,
                            sortOrder = existing.size,
                            sourceUri = stableUri.toString(),
                            sourceMimeType = "image/png",
                            sourceDisplayName = safeName
                        )
                        val insertedId = stickerRepository.addSticker(item)
                        val inserted = stickerRepository.getStickerById(insertedId)
                        if (inserted != null) {
                            conversionEngine.convertSticker(inserted)
                        }
                    } finally {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }
                }

                withContext(Dispatchers.Main) {
                    selectPack(packId)
                    onCreated(packId)
                }
            } catch (e: Exception) {
                createdPackId?.let { packId ->
                    try { packRepository.deletePack(packId) } catch (_: Exception) {}
                    try { tempFileManager.deletePackFiles(packId) } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "تعذر إنشاء حزمة الملصقات")
                }
            } finally {
                _isCreatingStudioPack.value = false
            }
        }
    }

    fun updateStickerName(stickerId: Long, newName: String) {
        viewModelScope.launch {
            val item = stickerRepository.getStickerById(stickerId) ?: return@launch
            stickerRepository.updateSticker(item.copy(displayName = newName))
        }
    }

    fun updateStickerEmoji(stickerId: Long, newEmoji: String) {
        viewModelScope.launch {
            val item = stickerRepository.getStickerById(stickerId) ?: return@launch
            stickerRepository.updateSticker(item.copy(emoji = newEmoji))
        }
    }

    fun generateNamesAutomatically(prefix: String = "ملصق") {
        viewModelScope.launch {
            val current = _currentStickers.value
            val updated = current.mapIndexed { index, item ->
                val autoName = String.format(java.util.Locale.US, "%s %03d", prefix, index + 1)
                item.copy(displayName = autoName)
            }
            stickerRepository.updateStickers(updated)
        }
    }

    fun addStudioImages(uris: List<Uri>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var added = 0
            uris.forEach { uri ->
                val name = queryDisplayName(context, uri)
                if (studioRepository.addFromUri(uri, name) != null) {
                    added++
                }
            }
            withContext(Dispatchers.Main) {
                onComplete(added)
            }
        }
    }

    fun deleteStudioImage(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            studioRepository.delete(id)
        }
    }

    fun saveStudioImage(
        id: String?,
        name: String,
        bitmap: Bitmap,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id == null) {
                studioRepository.createFromBitmap(name, bitmap)
            } else {
                studioRepository.saveEdited(id, name, bitmap)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun studioImageUri(image: StudioImage): String = studioRepository.uriFor(image)

    fun createAiPack(
        images: List<Bitmap>,
        packDisplayName: String,
        emoji: String,
        onCreated: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (images.isEmpty()) {
            onError("لا توجد صور لإنشاء الحزمة")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            var createdPackId: Long? = null
            try {
                val packId = packRepository.createPack(
                    StickerPack(
                        name = packDisplayName.ifBlank { "ملصقات الذكاء الاصطناعي" },
                        shortName = "ai_" + System.currentTimeMillis().toString().takeLast(8),
                        author = "",
                        defaultEmoji = emoji.ifBlank { "✨" }
                    )
                )
                createdPackId = packId
                val emojiToUse = emoji.ifBlank { "✨" }
                var sortOrder = stickerRepository.getStickersByPackId(packId).size
                images.forEachIndexed { index, bitmap ->
                    val safeName = "ملصق ${index + 1}"
                    tempFile = tempFileManager.createTempFile("ai_", ".png")
                    if (!StickerCreatorEngine.savePng(bitmap, tempFile!!)) {
                        throw java.io.IOException("تعذر إنشاء ملف PNG للملصق")
                    }
                    val (stableFile, stableUri) = tempFileManager.saveToPackOriginals(
                        packId,
                        tempFile!!,
                        "sticker_${index + 1}.png"
                    )
                    val item = StickerItem(
                        packId = packId,
                        originalUri = stableUri.toString(),
                        originalName = "${safeName}.png",
                        displayName = safeName,
                        emoji = emojiToUse,
                        type = StickerType.PNG,
                        originalSize = stableFile.length(),
                        width = bitmap.width,
                        height = bitmap.height,
                        sortOrder = sortOrder,
                        sourceUri = stableUri.toString(),
                        sourceMimeType = "image/png",
                        sourceDisplayName = safeName
                    )
                    sortOrder += 1
                    val insertedId = stickerRepository.addSticker(item)
                    val inserted = stickerRepository.getStickerById(insertedId)
                    if (inserted != null) {
                        conversionEngine.convertSticker(inserted)
                    }
                    tempFile?.let { try { it.delete() } catch (_: Exception) {} }
                    tempFile = null
                }
                withContext(Dispatchers.Main) {
                    selectPack(packId)
                    onCreated(packId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "تعذر إنشاء حزمة الذكاء الاصطناعي")
                }
            } finally {
                try { tempFile?.delete() } catch (_: Exception) {}
            }
        }
    }

    fun saveAiImagesToGallery(
        images: List<Bitmap>,
        baseName: String,
        onComplete: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var saved = 0
            images.forEachIndexed { index, bitmap ->
                val name = "${baseName.ifBlank { "ai_sticker" }}_${index + 1}"
                if (com.example.data.media.GallerySaver.saveToGallery(context, bitmap, name) != null) {
                    saved++
                }
            }
            withContext(Dispatchers.Main) {
                if (saved > 0) {
                    onComplete(saved)
                } else {
                    onError("تعذر حفظ الصور في المعرض")
                }
            }
        }
    }

    private fun queryDisplayName(context: Application, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteSticker(stickerId: Long) {
        viewModelScope.launch {
            val item = stickerRepository.getStickerById(stickerId)
            if (item != null) {
                try {
                    val uri = Uri.parse(item.originalUri)
                    if (uri.scheme == "file") {
                        File(uri.path ?: "").delete()
                    }
                } catch (_: Exception) {}
                stickerRepository.deleteSticker(stickerId)
            }
        }
    }

    fun moveStickerUp(stickerId: Long) {
        val list = _currentStickers.value.toMutableList()
        val index = list.indexOfFirst { it.id == stickerId }
        if (index > 0) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            viewModelScope.launch {
                stickerRepository.reorderStickers(list.map { it.id })
            }
        }
    }

    fun moveStickerDown(stickerId: Long) {
        val list = _currentStickers.value.toMutableList()
        val index = list.indexOfFirst { it.id == stickerId }
        if (index >= 0 && index < list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            viewModelScope.launch {
                stickerRepository.reorderStickers(list.map { it.id })
            }
        }
    }

    fun startConversion(stickers: List<StickerItem>) {
        if (conversionJob?.isActive == true) return

        conversionJob = viewModelScope.launch {
            conversionEngine.convertBatch(stickers) { progress ->
                _conversionProgress.value = progress
            }
        }
    }

    fun reprocessSingleSticker(stickerId: Long) {
        viewModelScope.launch {
            val item = stickerRepository.getStickerById(stickerId) ?: return@launch
            stickerRepository.updateSticker(item.copy(conversionStatus = ConversionStatus.PROCESSING))
            conversionEngine.convertSticker(item)
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _conversionProgress.value = null
    }

    fun dismissConversionDialog() {
        _conversionProgress.value = null
    }

    fun validateSticker(sticker: StickerItem): ValidationResult {
        val path = sticker.outputPath
        return if (path != null && File(path).exists()) {
            TelegramStickerValidator.validate(File(path), sticker.type.isAnimated)
        } else {
            ValidationResult(isValid = false, errors = listOf("لم يتم التحويل بعد"))
        }
    }
}
