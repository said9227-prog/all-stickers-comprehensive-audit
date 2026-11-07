package com.example.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.StickerPack
import com.example.core.model.StickerPreview
import com.example.presentation.components.AuroraDialog
import com.example.presentation.components.PackPreviewStrip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackListScreen(
    packs: List<StickerPack>,
    packPreviews: Map<Long, List<StickerPreview>> = emptyMap(),
    onOpenPack: (Long) -> Unit,
    onCreatePack: (name: String, shortName: String, author: String, defaultEmoji: String) -> Unit,
    onRenamePack: (Long, String) -> Unit,
    onDeletePack: (Long) -> Unit,
    onDeletePacks: (List<Long>) -> Unit,
    onExportPack: (Long) -> Unit,
    onOpenStudio: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var packToDelete by remember { mutableStateOf<StickerPack?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var packToRename by remember { mutableStateOf<StickerPack?>(null) }
    var renameText by remember { mutableStateOf("") }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var newPackName by remember { mutableStateOf("") }
    var newShortName by remember { mutableStateOf("") }
    var newAuthor by remember { mutableStateOf("") }
    var newDefaultEmoji by remember { mutableStateOf("✨") }

    // Drop ids that no longer exist (for example after a pack was deleted).
    LaunchedEffect(packs) {
        val existing = packs.map { it.id }.toSet()
        if (selectedIds.any { it !in existing }) {
            selectedIds = selectedIds.intersect(existing)
        }
        if (packs.isEmpty()) {
            selectionMode = false
            selectedIds = emptySet()
        }
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(packId: Long) {
        selectedIds = if (packId in selectedIds) {
            selectedIds - packId
        } else {
            selectedIds + packId
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "تم تحديد ${selectedIds.size} من ${packs.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اختر الحزم ثم استخدم أدوات الإدارة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.testTag("exit_selection_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { selectedIds = packs.map { it.id }.toSet() },
                            modifier = Modifier.testTag("select_all_top_button")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "تحديد الكل")
                        }
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.testTag("delete_selected_top_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "حذف المحدد",
                                tint = if (selectedIds.isNotEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "All stickers",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "كل ملصقاتك في مكان واحد • تصدير إلى تيليجرام",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { selectionMode = true },
                            modifier = Modifier.testTag("enter_selection_button")
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "تحديد الحزم")
                        }
                        IconButton(
                            onClick = onNavigateToDiagnostics,
                            modifier = Modifier.testTag("diagnostics_screen_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = "سجل التشخيص")
                        }
                        IconButton(
                            onClick = onNavigateToAbout,
                            modifier = Modifier.testTag("about_screen_button")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "عن التطبيق")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${selectedIds.size} محددة",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { selectedIds = packs.map { it.id }.toSet() },
                                enabled = packs.isNotEmpty()
                            ) {
                                Text("تحديد الكل")
                            }
                            TextButton(
                                onClick = { selectedIds = emptySet() },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Text("إلغاء التحديد")
                            }
                            Button(
                                onClick = { showBulkDeleteDialog = true },
                                enabled = selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_pack")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "حزمة جديدة")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sticker Studio Banner (the whole banner opens the studio)
            Card(
                onClick = onOpenStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    .testTag("studio_banner_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "استوديو الملصقات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أنشئ ملصقًا من صورة أو نص مع القص وإزالة الخلفية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onOpenStudio,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_studio_button")
                    ) {
                        Text("ابدأ")
                    }
                }
            }

            // Quick Import Banner
            Card(
                onClick = onNavigateToImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("import_banner_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "استيراد ملصقات مباشرة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "يدعم WebP متحرك وثابت، وZIP، و.wastickers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onNavigateToImport,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("banner_import_button")
                    ) {
                        Text("استيراد")
                    }
                }
            }

            Text(
                text = "حزم الملصقات (${packs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (packs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoAlbum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد حزم ملصقات بعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ابدأ بإنشاء حزمة جديدة أو استيراد ملصقات من واتساب",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.testTag("empty_create_pack_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إنشاء حزمة")
                            }
                            Button(
                                onClick = onNavigateToImport,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("empty_import_btn")
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استيراد ملصقات")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(packs, key = { it.id }) { pack ->
                        val selected = pack.id in selectedIds
                        var menuOpen by remember(pack.id) { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            toggleSelection(pack.id)
                                        } else {
                                            onOpenPack(pack.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) selectionMode = true
                                        if (pack.id !in selectedIds) {
                                            selectedIds = selectedIds + pack.id
                                        }
                                    }
                                )
                                .testTag("pack_item_${pack.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (selected) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { toggleSelection(pack.id) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("select_pack_${pack.id}")
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pack.defaultEmoji.ifBlank { "✨" },
                                            fontSize = 26.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pack.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${pack.stickerCount} ملصق",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (pack.readyCount > 0) {
                                                Text(
                                                    text = " • ",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${pack.readyCount} جاهز لتيليجرام",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF047857),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    if (!selectionMode) {
                                        IconButton(
                                            onClick = { onExportPack(pack.id) },
                                            enabled = pack.readyCount > 0,
                                            modifier = Modifier.testTag("quick_export_pack_${pack.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "تصدير إلى تيليجرام",
                                                tint = if (pack.readyCount > 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                }
                                            )
                                        }
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { menuOpen = true },
                                            modifier = Modifier.testTag("pack_menu_${pack.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "خيارات الحزمة"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("إعادة تسمية") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Edit, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    packToRename = pack
                                                    renameText = pack.name
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("تصدير إلى تيليجرام") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Send,
                                                        contentDescription = null
                                                    )
                                                },
                                                enabled = pack.readyCount > 0,
                                                onClick = {
                                                    menuOpen = false
                                                    onExportPack(pack.id)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("تحديد هذه الحزمة") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Checklist, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    selectionMode = true
                                                    selectedIds = selectedIds + pack.id
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "حذف الحزمة",
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    packToDelete = pack
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                PackPreviewStrip(
                                    previews = packPreviews[pack.id].orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pack_previews_${pack.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Pack Dialog
    if (showCreateDialog) {
        AuroraDialog(
            onDismissRequest = { showCreateDialog = false },
            title = "حزمة ملصقات جديدة",
            subtitle = "صمّم حزمة أحلامك وابدأ بإضافة الملصقات",
            icon = Icons.Default.AutoAwesome,
            confirmLabel = "إنشاء الحزمة",
            onConfirm = {
                val name = newPackName.ifBlank { "حزمة ملصقات" }
                val shortName = newShortName.ifBlank { "pack_" + System.currentTimeMillis() % 10000 }
                onCreatePack(name, shortName, newAuthor, newDefaultEmoji)
                newPackName = ""
                newShortName = ""
                newAuthor = ""
                showCreateDialog = false
            }
        ) {
            OutlinedTextField(
                value = newPackName,
                onValueChange = {
                    newPackName = it
                    if (newShortName.isBlank() || newShortName.startsWith("pack_")) {
                        newShortName = it.replace("[^\\w]".toRegex(), "_").lowercase()
                    }
                },
                label = { Text("اسم الحزمة") },
                placeholder = { Text("مثال: ملصقات واتساب مضحكة") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_pack_name_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newShortName,
                onValueChange = { newShortName = it },
                label = { Text("الاسم المختصر لتيليجرام") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_pack_shortname_input"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newAuthor,
                onValueChange = { newAuthor = it },
                label = { Text("المؤلف (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newDefaultEmoji,
                onValueChange = { newDefaultEmoji = it },
                label = { Text("الإيموجي الافتراضي") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }

    // Rename Pack Dialog
    packToRename?.let { pack ->
        AuroraDialog(
            onDismissRequest = { packToRename = null },
            title = "إعادة تسمية الحزمة",
            subtitle = "لن تتغير ملصقات الحزمة أو معرّفها",
            icon = Icons.Default.Edit,
            confirmLabel = "حفظ الاسم",
            confirmEnabled = renameText.trim().isNotEmpty(),
            onConfirm = {
                val trimmed = renameText.trim()
                if (trimmed.isNotEmpty()) {
                    onRenamePack(pack.id, trimmed)
                }
                packToRename = null
            }
        ) {
            Text(
                text = "الاسم الحالي: ${pack.name}",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text("الاسم الجديد") },
                singleLine = true,
                isError = renameText.trim().isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_pack_input")
            )
            if (renameText.trim().isEmpty()) {
                Text(
                    text = "لا يمكن ترك الاسم فارغاً",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Delete Confirmation Dialog (single)
    if (packToDelete != null) {
        AuroraDialog(
            onDismissRequest = { packToDelete = null },
            title = "تأكيد حذف الحزمة",
            subtitle = "سيتم حذف جميع الملصقات المرتبطة بها",
            icon = Icons.Default.Delete,
            confirmLabel = "حذف",
            onConfirm = {
                val target = packToDelete
                if (target != null) {
                    onDeletePack(target.id)
                    selectedIds = selectedIds - target.id
                }
                packToDelete = null
            }
        ) {
            Text(
                text = "هل أنت متأكد من حذف «${packToDelete?.name}» وجميع ملصقاتها؟",
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }

    // Delete Confirmation Dialog (bulk)
    if (showBulkDeleteDialog) {
        AuroraDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = "حذف ${selectedIds.size} حزمة",
            subtitle = "لا يمكن التراجع عن هذه العملية",
            icon = Icons.Default.DeleteSweep,
            confirmLabel = "حذف الكل",
            confirmEnabled = selectedIds.isNotEmpty(),
            onConfirm = {
                val ids = selectedIds.toList()
                onDeletePacks(ids)
                selectedIds = emptySet()
                selectionMode = false
                showBulkDeleteDialog = false
            }
        ) {
            Text(
                text = "سيتم حذف ${selectedIds.size} حزمة وجميع ملصقاتها نهائياً. هل تريد المتابعة؟",
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
