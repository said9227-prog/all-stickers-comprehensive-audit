package com.example.wastickers

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import java.util.Locale

/**
 * Professional WASTICKERS browser: discovery history with persistent NEW
 * badges, live search, multi-selection and a bounded, cancellable import with
 * per-file and overall progress. Material 3, dark-mode aware and RTL/LTR aware.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WastickersImportScreen(
    onNavigateBack: () -> Unit,
    onOpenPack: (Long) -> Unit,
    viewModel: WastickersImportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isRtl = Locale.getDefault().language == "ar"

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onTreeUriGranted(uri) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onLegacyPermissionResult(granted) }

    // .wastickers has NO registered MIME type, so the picker MUST use */* —
    // using "application/zip" or any specific type hides exactly the files the
    // user is trying to import. Multiple selection is supported in one go.
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.onDocumentsSelected(uris) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(Unit) {
        viewModel.openPackEvents.collect { packId -> onOpenPack(packId) }
    }

    // Leaving this screen for ANY reason (back, opening a pack, tab change)
    // clears the multi-selection. Returning never restores old selections.
    DisposableEffect(Unit) {
        onDispose { viewModel.clearSelectionOnLeave() }
    }

    // Debounced, index-backed results from the ViewModel. No storage read and
    // no re-normalisation happens here per keystroke.
    val visible by viewModel.visibleFiles.collectAsState()
    val hasNew = state.files.any { it.isNew }
    val importProgress = state.import
    val importRunning = importProgress?.isRunning == true

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.wst_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.wst_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.wst_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }, enabled = !importRunning) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.wst_refresh)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                ImportBottomBar(
                    state = state,
                    onImport = { viewModel.importSelected() },
                    onCancel = { viewModel.cancelImport() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ROOT-CAUSE FIX: these actions stay visible even after a
                // grant exists. Previously the card disappeared as soon as ANY
                // folder was granted, so a user who granted the wrong folder
                // could never add the folder that actually holds the packs.
                AccessActionsCard(
                    permission = state.permission,
                    onChooseFolder = { treeLauncher.launch(null) },
                    onChooseFiles = { documentLauncher.launch(arrayOf("*/*")) },
                    onGrantRead = {
                        if (Build.VERSION.SDK_INT <= 32) {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            treeLauncher.launch(null)
                        }
                    }
                )

                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.setQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.wst_clear_search)
                                )
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.wst_search_hint)) },
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.wst_files_count, state.files.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasNew) {
                        TextButton(onClick = { viewModel.markNewAsSeen() }) {
                            Text(
                                stringResource(R.string.wst_mark_seen),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                DiscoveryStatusBanners(state = state)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.wst_selected_format,
                            state.selectedIds.size,
                            state.files.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.selectAll(visible.map { it.id }) }) {
                        Icon(
                            Icons.Default.SelectAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.wst_select_all))
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.wst_clear_selection))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.isLoading && state.files.isEmpty() -> CenteredMessage(
                            showProgress = true,
                            title = stringResource(R.string.wst_loading)
                        )
                        state.error != null && state.files.isEmpty() -> CenteredMessage(
                            showProgress = false,
                            title = state.error ?: stringResource(R.string.wst_error),
                            actionLabel = stringResource(R.string.wst_retry),
                            onAction = { viewModel.refresh() }
                        )
                        visible.isEmpty() && state.query.isNotBlank() -> CenteredMessage(
                            showProgress = false,
                            title = stringResource(R.string.wst_no_results),
                            subtitle = stringResource(R.string.wst_no_results_hint)
                        )
                        visible.isEmpty() -> CenteredMessage(
                            showProgress = false,
                            title = stringResource(R.string.wst_empty),
                            subtitle = stringResource(R.string.wst_empty_guide),
                            actionLabel = stringResource(R.string.wst_pick_files_direct),
                            onAction = { documentLauncher.launch(arrayOf("*/*")) },
                            secondaryActionLabel = stringResource(R.string.wst_grant_folder),
                            onSecondaryAction = { treeLauncher.launch(null) }
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(items = visible, key = { it.id }) { file ->
                                WastickersFileCard(
                                    file = file,
                                    selected = file.id in state.selectedIds,
                                    inspection = state.inspections[file.id],
                                    previewUri = state.previewUris[file.id],
                                    progress = importProgress?.perFile?.get(file.id),
                                    importEnabled = !importRunning,
                                    onToggle = { viewModel.toggleSelection(file.id) },
                                    onImport = { viewModel.importSingle(file.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessActionsCard(
    permission: WastickersPermission,
    onChooseFolder: () -> Unit,
    onChooseFiles: () -> Unit,
    onGrantRead: () -> Unit
) {
    val granted = permission == WastickersPermission.GRANTED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (granted) Icons.Default.FolderOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (granted) R.string.wst_access_more_title
                        else R.string.wst_permission_title
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (permission) {
                    WastickersPermission.GRANTED ->
                        stringResource(R.string.wst_access_more_desc)
                    WastickersPermission.DENIED ->
                        stringResource(R.string.wst_permission_denied)
                    WastickersPermission.NEEDS_ACCESS ->
                        stringResource(R.string.wst_permission_reason)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onChooseFolder, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wst_grant_folder))
                }
                OutlinedButton(onClick = onChooseFiles, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wst_pick_files_direct))
                }
            }
            if (Build.VERSION.SDK_INT <= 32 && !granted) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onGrantRead,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.wst_grant_read))
                }
            }
        }
    }
}

/**
 * Explicit, distinguishable states: files found, folder inaccessible,
 * invalid file, import failed. None of them is silently swallowed.
 */
@Composable
private fun DiscoveryStatusBanners(state: WastickersUiState) {
    val noticeColor = MaterialTheme.colorScheme.errorContainer
    val onNotice = MaterialTheme.colorScheme.onErrorContainer

    if (!state.isLoading && state.files.isNotEmpty()) {
        Text(
            text = stringResource(R.string.wst_found_banner, state.files.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }
    if (state.inaccessibleFolders > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = noticeColor)
        ) {
            Text(
                text = stringResource(
                    R.string.wst_inaccessible_folders, state.inaccessibleFolders
                ),
                style = MaterialTheme.typography.labelSmall,
                color = onNotice,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
    if (state.invalidFiles > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = noticeColor)
        ) {
            Text(
                text = stringResource(R.string.wst_invalid_files, state.invalidFiles),
                style = MaterialTheme.typography.labelSmall,
                color = onNotice,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
    val import = state.import
    if (import != null && !import.isRunning && !import.isCancelled &&
        (import.failed > 0 || import.skipped > 0)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = noticeColor)
        ) {
            Text(
                text = stringResource(
                    R.string.wst_import_issues, import.failed, import.skipped
                ),
                style = MaterialTheme.typography.labelSmall,
                color = onNotice,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun WastickersFileCard(
    file: WastickersFile,
    selected: Boolean,
    inspection: ArchiveInspection?,
    previewUri: String?,
    progress: FileImportProgress?,
    importEnabled: Boolean,
    onToggle: () -> Unit,
    onImport: () -> Unit
) {
    val packName = WastickersFileOps.packNameOf(file.displayName)
    val showFileName = packName != file.displayName
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // REAL preview of THIS archive. Its Uri is versioned by archive
                // id + size + modification date, never by "1.webp", so a reused
                // card can never show another pack's sticker.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri.isNullOrBlank()) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = stringResource(R.string.wst_no_preview),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = packName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (file.isNew) {
                            Spacer(modifier = Modifier.width(6.dp))
                            NewBadge()
                        }
                    }
                    if (showFileName) {
                        Text(
                            text = stringResource(R.string.wst_file_name, file.displayName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    MetaLine(
                        icon = Icons.Default.Schedule,
                        text = stringResource(
                            R.string.wst_modified,
                            WastickersFileOps.formatDate(file.lastModified)
                        )
                    )
                    MetaLine(
                        icon = Icons.Default.Info,
                        text = stringResource(
                            R.string.wst_size,
                            WastickersFileOps.formatSize(file.sizeBytes)
                        )
                    )
                    if (inspection != null && inspection.kind == ArchiveKind.STANDALONE) {
                        MetaLine(
                            icon = Icons.Default.Archive,
                            text = stringResource(
                                R.string.wst_sticker_count, inspection.stickerCount
                            ) + " · " + stringResource(
                                if (inspection.hasAnimated) R.string.wst_type_animated
                                else R.string.wst_type_static
                            )
                        )
                    }
                    if (file.location.isNotBlank()) {
                        MetaLine(
                            icon = Icons.Default.FolderOpen,
                            text = stringResource(R.string.wst_location, file.location)
                        )
                    }
                    if (file.firstSeenAt > 0L) {
                        MetaLine(
                            icon = Icons.Default.Download,
                            text = stringResource(
                                R.string.wst_discovered_at,
                                WastickersFileOps.formatDate(file.firstSeenAt)
                            )
                        )
                    }
                }
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status = file.status)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (file.source) {
                        WastickersSource.MEDIA_STORE -> "MediaStore"
                        WastickersSource.SAF_TREE -> "SAF"
                        WastickersSource.USER_DOCUMENT ->
                            stringResource(R.string.wst_source_direct)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Content-based warnings decided from the archive BYTES, not from
            // its name: a genuine split segment needs the other parts, and an
            // invalid archive is clearly labelled. A standalone pack that just
            // happens to be named "*.part2.wastickers" is NOT flagged here.
            val inspectionWarning = when (inspection?.kind) {
                ArchiveKind.NEEDS_OTHER_PARTS ->
                    inspection?.reason ?: stringResource(R.string.wst_status_needs_parts)
                ArchiveKind.INVALID ->
                    inspection?.reason ?: stringResource(R.string.wst_invalid_archive)
                else -> null
            }
            if (inspectionWarning != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = inspectionWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (progress != null && progress.status == WastickersStatus.IMPORTING) {
                Spacer(modifier = Modifier.height(8.dp))
                ProgressBar(
                    progress = progress.progress,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.wst_per_file_progress,
                        (progress.progress * 100).toInt()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress?.message != null &&
                progress.status in setOf(
                    WastickersStatus.FAILED,
                    WastickersStatus.CORRUPT,
                    WastickersStatus.DUPLICATE,
                    WastickersStatus.NEEDS_PARTS
                )
            ) {
                // NEEDS_PARTS is informational, not a failure: use a neutral
                // tint so a missing split segment is never shown as an error.
                val isError = progress.status in setOf(
                    WastickersStatus.FAILED, WastickersStatus.CORRUPT
                )
                val tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isError) Icons.Default.ErrorOutline else Icons.Default.Info,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }

            // Per-file import button. The card itself toggles selection; this
            // button consumes its own click and imports only this archive.
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onImport, enabled = importEnabled) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.wst_import_one))
            }
        }
    }
}

@Composable
private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.wst_new_badge),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusChip(status: WastickersStatus) {
    val label = when (status) {
        WastickersStatus.READY -> stringResource(R.string.wst_status_ready)
        WastickersStatus.IMPORTING -> stringResource(R.string.wst_status_importing)
        WastickersStatus.IMPORTED -> stringResource(R.string.wst_status_imported)
        WastickersStatus.DUPLICATE -> stringResource(R.string.wst_status_duplicate)
        WastickersStatus.CORRUPT -> stringResource(R.string.wst_status_corrupt)
        WastickersStatus.NEEDS_PARTS -> stringResource(R.string.wst_status_needs_parts)
        WastickersStatus.FAILED -> stringResource(R.string.wst_status_failed)
    }
    val color = when (status) {
        WastickersStatus.READY -> MaterialTheme.colorScheme.secondaryContainer
        WastickersStatus.IMPORTING -> MaterialTheme.colorScheme.primaryContainer
        WastickersStatus.IMPORTED -> Color(0xFF1B7F52)
        WastickersStatus.DUPLICATE, WastickersStatus.NEEDS_PARTS ->
            MaterialTheme.colorScheme.surfaceVariant
        WastickersStatus.CORRUPT, WastickersStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
    val textColor = when (status) {
        WastickersStatus.CORRUPT, WastickersStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
private fun CenteredMessage(
    showProgress: Boolean,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
        }
    }
}

@Composable
private fun ImportBottomBar(
    state: WastickersUiState,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    val progress = state.import
    val running = progress?.isRunning == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.wst_overall_progress,
                        (progress.overall * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${progress.completed}/${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            ProgressBar(
                progress = progress.overall,
                color = if (progress.isCancelled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxWidth()
            )
            // Pack currently being imported, derived from real per-file progress.
            if (running) {
                state.currentImportName?.let { name ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.wst_import_current, name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!running) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (progress.isCancelled) {
                        stringResource(R.string.wst_import_cancelled)
                    } else {
                        stringResource(
                            R.string.wst_import_done,
                            progress.succeeded,
                            progress.failed,
                            progress.skipped
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress.isCancelled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                // End-of-batch report built from the real per-file progress map:
                // succeeded / failed / duplicate / needsParts, never guessed.
                val report = state.importReport
                if (report != null && report.finished) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.wst_import_report,
                            report.succeeded,
                            report.failed,
                            report.skipped,
                            report.needsParts
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (report.hasIssues) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onImport,
                enabled = state.selectedIds.isNotEmpty() && !running,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wst_import_selected) +
                        " (${state.selectedIds.size})",
                    fontWeight = FontWeight.Bold
                )
            }
            if (running) {
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.wst_cancel_import))
                }
            }
        }
    }
}
