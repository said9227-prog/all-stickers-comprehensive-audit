package com.example.wastickers

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.data.db.AppDatabase
import com.example.data.repository.PackRepository
import com.example.data.repository.StickerRepository
import com.example.storage.TempFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class WastickersPermission {
    GRANTED,
    NEEDS_ACCESS,
    DENIED
}

data class WastickersUiState(
    val isLoading: Boolean = false,
    val files: List<WastickersFile> = emptyList(),
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
    val permission: WastickersPermission = WastickersPermission.NEEDS_ACCESS,
    val error: String? = null,
    /** Folder nodes that could not be read (revoked grant / broken provider). */
    val inaccessibleFolders: Int = 0,
    /** Directly picked documents that are not .wastickers archives. */
    val invalidFiles: Int = 0,
    val import: ImportProgress? = null,
    /** Content inspection of each archive, decided from its BYTES, not name. */
    val inspections: Map<String, ArchiveInspection> = emptyMap(),
    /** Versioned preview file Uris; never keyed by image name alone. */
    val previewUris: Map<String, String> = emptyMap(),
    /** End-of-batch summary derived from the real per-file progress. */
    val importReport: ImportReport? = null,
    /** Pack currently being imported, shown in the progress bar. */
    val currentImportName: String? = null
)

@OptIn(FlowPreview::class) // Flow.debounce is still a FlowPreview API
class WastickersImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()

    private val db = AppDatabase.getInstance(app)
    private val packRepository = PackRepository(db.stickerDao())
    private val stickerRepository = StickerRepository(db.stickerDao())
    private val tempFileManager = TempFileManager(app)

    private val history = DiscoveryHistoryStore(app)
    private val hashLog: ImportedHashLog = SharedPrefsImportedHashLog(app)
    private val treeStore = TreePermissionStore(app)
    private val documentStore = DocumentPermissionStore(app)
    private val scanner: WastickersDataSource =
        DeviceWastickersScanner(app, treeStore, documentStore)

    private val _uiState = MutableStateFlow(WastickersUiState())
    val uiState: StateFlow<WastickersUiState> = _uiState.asStateFlow()

    private val _openPackEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val openPackEvents: SharedFlow<Long> = _openPackEvents.asSharedFlow()

    /* --- Instant, debounced, index-based search -------------------------- */
    private val _query = MutableStateFlow("")
    private val _files = MutableStateFlow<List<WastickersFile>>(emptyList())
    private val _visibleFiles = MutableStateFlow<List<WastickersFile>>(emptyList())
    val visibleFiles: StateFlow<List<WastickersFile>> = _visibleFiles.asStateFlow()

    /** Bounded LRU of archive inspections, shared with preview generation. */
    private val inspectionCache = WastickersInspectionCache(maxEntries = 120)
    private val previewDir: File by lazy {
        File(app.cacheDir, "wastickers_previews").apply { mkdirs() }
    }
    private var inspectionJob: Job? = null

    init {
        // The normalised index is built ONCE per list change (on a background
        // dispatcher). Each debounced keystroke then reuses that SAME index and
        // only does substring matching: no storage reads, no re-normalisation
        // of thousands of haystacks per character.
        combine(
            _query.debounce(SEARCH_DEBOUNCE_MS),
            _files.map { files -> WastickersSearchIndex(files) }
        ) { query, index -> index.search(query) }
            .flowOn(Dispatchers.Default)
            .onEach { _visibleFiles.value = it }
            .launchIn(viewModelScope)
    }

    private val importer: WastickersImporter = WastickersImporter(
        opener = ArchiveOpener { file ->
            try {
                app.contentResolver.openInputStream(Uri.parse(file.uri))
            } catch (_: Exception) {
                null
            }
        },
        writer = PackWriter { pack -> persistPack(pack) },
        hashLog = hashLog,
        tempDirProvider = { File(app.cacheDir, "wastickers_import").apply { mkdirs() } }
    )

    private var importJob: Job? = null

    /**
     * Lazily inspects the CURRENTLY VISIBLE archives on Dispatchers.IO.
     *
     * This is the root-cause fix for "content decides, not the extension":
     * a file named pack.part2.wastickers may be a complete standalone archive,
     * while a file named pack.wastickers may be a split segment. Results are
     * cached by archive VERSION (id + size + modification time), so a refresh
     * never re-reads unchanged files and a changed file never shows a stale
     * preview from another pack.
     */
    private fun inspectVisible(files: List<WastickersFile>) {
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val versions = files.map { WastickersPreviewKeys.archiveVersion(it) }
                inspectionCache.retainVersions(versions)
                val results = LinkedHashMap<String, ArchiveInspection>()
                val previews = LinkedHashMap<String, String>()

                for (file in files.take(MAX_INSPECTIONS_PER_SCAN)) {
                    if (!isActive) break
                    val version = WastickersPreviewKeys.archiveVersion(file)
                    val cacheKey = "$version|inspection"
                    val cached = inspectionCache.get(cacheKey)
                    val inspection = cached ?: WastickersArchiveInspector.inspect(file.displayName) {
                        try {
                            app.contentResolver.openInputStream(Uri.parse(file.uri))
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (cached == null) inspectionCache.put(cacheKey, inspection)
                    results[file.id] = inspection

                    val bytes = inspection.previewBytes
                    val entry = inspection.previewEntryName
                    if (bytes != null && entry != null && bytes.isNotEmpty()) {
                        val preview = writeVersionedPreview(version, entry, bytes)
                        if (preview != null) previews[file.id] = Uri.fromFile(preview).toString()
                    }
                }

                // Delete preview files belonging to archives that disappeared
                // or changed, so no stale image can ever be shown.
                val expected = previews.values.mapNotNull { raw ->
                    runCatching { File(Uri.parse(raw).path.orEmpty()).name }.getOrNull()
                }.toHashSet()
                previewDir.listFiles()?.forEach { candidate ->
                    if (candidate.name !in expected) runCatching { candidate.delete() }
                }

                results to previews
            }
            _uiState.update { state ->
                state.copy(inspections = snapshot.first, previewUris = snapshot.second)
            }
        }
    }

    /** Preview file name is derived from the FULL archive identity, never "1.webp". */
    private fun writeVersionedPreview(
        version: String,
        entryName: String,
        bytes: ByteArray
    ): File? = try {
        val key = WastickersPreviewKeys.archivePreviewKey(version, entryName)
        val extension = entryName.substringAfterLast('.', "webp")
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(6).ifBlank { "webp" }
        val target = File(previewDir, "p_${Integer.toHexString(key.hashCode())}.$extension")
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        target.takeIf { it.exists() && it.length() > 0L }
    } catch (_: Exception) {
        null
    }

    /** Persists one extracted pack using the existing Room + managed storage. */
    private suspend fun persistPack(pack: ImportedPack): Long {
        val normalized = pack.name.replace("[^\\w]".toRegex(), "_").lowercase(Locale.US)
        val shortName = "pack_${normalized.take(24)}_${System.currentTimeMillis() % 100000}"
        val packId = packRepository.createPack(
            StickerPack(
                name = pack.name,
                shortName = shortName,
                author = pack.author,
                defaultEmoji = "✨"
            )
        )

        val items = pack.stickers.mapIndexed { index, sticker ->
            val preferredName = "${sticker.displayName}.${sticker.file.extension}"
            val (stableFile, stableUri) =
                tempFileManager.saveToPackOriginals(packId, sticker.file, preferredName)
            val mime = when (sticker.type) {
                com.example.core.model.StickerType.WEBM -> "video/webm"
                com.example.core.model.StickerType.PNG,
                com.example.core.model.StickerType.JPG -> "image/png"
                else -> "image/webp"
            }
            StickerItem(
                packId = packId,
                originalUri = stableUri.toString(),
                originalName = stableFile.name,
                displayName = sticker.displayName,
                emoji = "✨",
                type = sticker.type,
                originalSize = stableFile.length(),
                width = sticker.width,
                height = sticker.height,
                durationMs = sticker.durationMs,
                sortOrder = index,
                sourceUri = "wastickers://${pack.sourceId}",
                sourceMimeType = mime,
                sourceDisplayName = sticker.displayName
            )
        }
        stickerRepository.addStickers(items)
        _openPackEvents.tryEmit(packId)
        return packId
    }

    /** Re-scans every source and merges the result with the persistent history. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val outcome = scanner.scan()
                val now = System.currentTimeMillis()
                val previous = withContext(Dispatchers.IO) { history.load() }
                val merge = withContext(Dispatchers.Default) {
                    WastickersFileOps.mergeDiscovery(previous, outcome.files, now)
                }
                // SMART REFRESH: newly discovered archives go to the top,
                // everything else is preserved (no fabricated dates), and known
                // files are never flagged NEW again.
                val ordered = withContext(Dispatchers.Default) {
                    WastickersFileOps.orderForRefresh(merge.files, merge.newIds)
                }
                withContext(Dispatchers.IO) { history.save(merge.records) }

                val permission = when {
                    scanner.hasAnyAccess() -> WastickersPermission.GRANTED
                    _uiState.value.permission == WastickersPermission.DENIED ->
                        WastickersPermission.DENIED
                    else -> WastickersPermission.NEEDS_ACCESS
                }
                val availableIds = ordered.map { it.id }.toSet()
                _files.value = ordered
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        files = ordered,
                        selectedIds = state.selectedIds.intersect(availableIds),
                        permission = permission,
                        error = null,
                        inaccessibleFolders = outcome.inaccessibleFolders,
                        invalidFiles = outcome.invalidFiles
                    )
                }
                inspectVisible(ordered)
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        permission = WastickersPermission.DENIED,
                        error = "تم رفض صلاحية الوصول إلى الملفات"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "تعذر فحص الملفات")
                }
            }
        }
    }

    /** Persists a SAF tree grant and refreshes the list. */
    fun onTreeUriGranted(uri: Uri?) {
        if (uri == null) {
            onPermissionDenied()
            return
        }
        viewModelScope.launch {
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers already hold the grant; scanning will tell us.
            }
            withContext(Dispatchers.IO) { treeStore.add(uri) }
            refresh()
        }
    }

    /**
     * Handles ACTION_OPEN_DOCUMENT results. This is the reliable channel on
     * Android 13+: the user points at the exact .wastickers archive, and the
     * read grant is persisted so it still works after an app restart.
     */
    fun onDocumentsSelected(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onPermissionDenied()
            return
        }
        viewModelScope.launch {
            val accepted = ArrayList<Uri>(uris.size)
            for (uri in uris) {
                try {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers already hold the grant; scanning will tell.
                }
                accepted.add(uri)
            }
            withContext(Dispatchers.IO) { documentStore.addAll(accepted) }
            refresh()
        }
    }

    fun onLegacyPermissionResult(granted: Boolean) {
        if (!granted) {
            onPermissionDenied()
        } else {
            refresh()
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                permission = WastickersPermission.DENIED,
                error = "تم رفض الصلاحية — يمكنك اختيار مجلد أو ملف يدوياً"
            )
        }
    }

    fun setQuery(query: String) {
        // The text field updates immediately; the debounced pipeline rebuilds
        // the visible list after SEARCH_DEBOUNCE_MS without touching storage.
        _query.value = query
        _uiState.update { it.copy(query = query) }
    }

    /**
     * Called from the screen's DisposableEffect when the composable leaves the
     * composition (not only on ViewModel.onCleared). Navigating away and back
     * therefore never restores a stale selection and never imports packs the
     * user selected during a previous visit.
     */
    fun clearSelectionOnLeave() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun toggleSelection(id: String) {
        _uiState.update {
            it.copy(selectedIds = WastickersFileOps.toggleSelection(it.selectedIds, id))
        }
    }

    /** Selects all currently visible (filtered) files. */
    fun selectAll(ids: Collection<String>) {
        _uiState.update {
            it.copy(selectedIds = WastickersFileOps.selectAll(it.selectedIds, ids))
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    /** Clears the NEW badge but keeps the discovery date in history. */
    fun markNewAsSeen() {
        viewModelScope.launch {
            val newIds = _uiState.value.files.filter { it.isNew }.map { it.id }
            withContext(Dispatchers.IO) { history.markSeen(newIds) }
            _uiState.update { state ->
                state.copy(files = state.files.map { if (it.isNew) it.copy(isNew = false) else it })
            }
        }
    }

    /** Imports every selected archive in one bounded, cancellable operation. */
    fun importSelected() {
        val selected = _uiState.value.files.filter { it.id in _uiState.value.selectedIds }
        startImport(selected)
    }

    /** Per-file import button: imports just this archive, without altering the
     *  user's multi-selection. Duplicate protection is handled by the hash log. */
    fun importSingle(id: String) {
        val file = _uiState.value.files.firstOrNull { it.id == id } ?: return
        startImport(listOf(file))
    }

    private fun startImport(files: List<WastickersFile>) {
        if (files.isEmpty()) return
        importJob?.cancel()
        importJob = importer.import(files)
            .onEach { progress ->
                _uiState.update { state ->
                    val updated = state.files.map { file ->
                        progress.perFile[file.id]?.let { file.copy(status = it.status) } ?: file
                    }
                    state.copy(
                        files = updated,
                        import = progress,
                        importReport = WastickersFileOps.buildImportReport(progress),
                        currentImportName = WastickersFileOps.currentImportLabel(updated, progress)
                    )
                }
            }
            .onCompletion { cause ->
                _uiState.update { state ->
                    val progress = state.import
                    if (progress == null) {
                        state
                    } else {
                        val terminal = if (cause is CancellationException) {
                            progress.copy(isRunning = false, isCancelled = true)
                        } else {
                            progress.copy(isRunning = false)
                        }
                        state.copy(
                            import = terminal,
                            importReport = WastickersFileOps.buildImportReport(terminal),
                            currentImportName = null
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { state ->
            val terminal = state.import?.copy(isRunning = false, isCancelled = true)
            state.copy(
                import = terminal,
                importReport = terminal?.let { WastickersFileOps.buildImportReport(it) },
                currentImportName = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        importJob?.cancel()
        inspectionJob?.cancel()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 180L
        /** Only the visible top of the list is inspected lazily per refresh. */
        const val MAX_INSPECTIONS_PER_SCAN = 60
    }
}
