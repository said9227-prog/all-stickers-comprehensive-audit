package com.example.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.StickerPack
import com.example.core.model.StickerType
import com.example.presentation.DetectedImportItem
import com.example.presentation.components.AnimatedStickerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    packs: List<StickerPack>,
    initialPackId: Long?,
    isImporting: Boolean,
    detectedItems: List<DetectedImportItem>,
    onInspectUris: (List<Uri>) -> Unit,
    onImportZip: (Uri, onComplete: (Long) -> Unit, onError: (String) -> Unit) -> Unit,
    onCommitImport: (packId: Long, items: List<DetectedImportItem>, onComplete: () -> Unit) -> Unit,
    onCreatePackAndImport: (name: String, items: List<DetectedImportItem>, onComplete: (Long) -> Unit) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenPack: (Long) -> Unit,
    onBrowseDeviceWastickers: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedPackId by remember(packs, initialPackId) {
        mutableStateOf(initialPackId ?: packs.firstOrNull()?.id)
    }
    var packDropdownExpanded by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }
    var createNewPackMode by remember { mutableStateOf(packs.isEmpty()) }

    // Launcher for selecting multiple sticker images / animations
    val multiDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onInspectUris(uris)
        }
    }

    // Launcher for selecting a ZIP or .wastickers file
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportZip(
                uri,
                { newPackId ->
                    Toast.makeText(context, "تم استخراج الحزمة بنجاح!", Toast.LENGTH_SHORT).show()
                    onOpenPack(newPackId)
                },
                { err ->
                    Toast.makeText(context, "خطأ: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "استيراد ملصقات",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("import_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Pick Action Buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "اختر طريقة الاستيراد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                multiDocLauncher.launch(
                                    arrayOf(
                                        "image/webp",
                                        "image/png",
                                        "image/jpeg",
                                        "video/webm",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pick_multiple_stickers_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ملفات ملصقات")
                        }

                        Button(
                            onClick = {
                                zipLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pick_zip_wastickers_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("أرشيف ZIP / WASTICKERS")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onBrowseDeviceWastickers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("browse_device_wastickers_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.wst_browse_device), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "جارٍ فحص واكتشاف صيغ الملفات بدقة...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (detectedItems.isNotEmpty()) {
                // Target Pack Selection
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "إضافة الملصقات إلى:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (packs.isNotEmpty() && !createNewPackMode) {
                            ExposedDropdownMenuBox(
                                expanded = packDropdownExpanded,
                                onExpandedChange = { packDropdownExpanded = it }
                            ) {
                                val selectedName = packs.firstOrNull { it.id == selectedPackId }?.name ?: "اختر حزمة"
                                OutlinedTextField(
                                    value = selectedName,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = packDropdownExpanded,
                                    onDismissRequest = { packDropdownExpanded = false }
                                ) {
                                    packs.forEach { pack ->
                                        DropdownMenuItem(
                                            text = { Text(pack.name) },
                                            onClick = {
                                                selectedPackId = pack.id
                                                packDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = newPackName,
                                onValueChange = { newPackName = it },
                                label = { Text("اسم الحزمة الجديدة") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_new_pack_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الملفات المكتشفة (${detectedItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            if (createNewPackMode || selectedPackId == null) {
                                val name = newPackName.ifBlank { "حزمة ملصقات جديدة" }
                                onCreatePackAndImport(name, detectedItems) { newId ->
                                    onOpenPack(newId)
                                }
                            } else {
                                onCommitImport(selectedPackId!!, detectedItems) {
                                    onOpenPack(selectedPackId!!)
                                }
                            }
                        },
                        modifier = Modifier.testTag("commit_import_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إتمام الاستيراد")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detectedItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedStickerView(
                                        uriString = item.uriString,
                                        stickerType = item.type,
                                        modifier = Modifier.size(44.dp),
                                        // Detected items have no persisted id yet:
                                        // the full source Uri + name + type is
                                        // the strongest identity available here.
                                        cacheIdentity = "import|${item.uriString}|${item.name}|${item.type}"
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Badge
                                        val (badgeColor, textColor) = when (item.type) {
                                            StickerType.ANIMATED_WEBP -> Pair(Color(0xFF3B82F6), Color.White)
                                            StickerType.STATIC_WEBP -> Pair(Color(0xFF10B981), Color.White)
                                            StickerType.PNG -> Pair(Color(0xFF8B5CF6), Color.White)
                                            StickerType.WEBM -> Pair(Color(0xFF06B6D4), Color.White)
                                            else -> Pair(Color(0xFF6B7280), Color.White)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = item.type.displayNameAr,
                                                color = textColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        val sizeKb = item.sizeBytes / 1024
                                        Text(
                                            text = "$sizeKb KB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (item.width > 0 && item.height > 0) {
                                            Text(
                                                text = " • ${item.width}x${item.height}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "اختر ملفات ملصقات فردية أو ملف مضغوط للبدء",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
