package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.conversion.BatchConversionProgress
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.ValidationResult
import com.example.presentation.components.AnimatedStickerView
import com.example.presentation.dialogs.ConversionProgressDialog
import com.example.presentation.dialogs.ExportDialog
import com.example.presentation.dialogs.StickerDetailDialog
import com.example.wastickers.WastickersPreviewKeys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDetailScreen(
    pack: StickerPack,
    stickers: List<StickerItem>,
    conversionProgress: BatchConversionProgress?,
    onNavigateBack: () -> Unit,
    onNavigateToImport: (Long) -> Unit,
    onUpdateStickerName: (Long, String) -> Unit,
    onUpdateStickerEmoji: (Long, String) -> Unit,
    onGenerateNamesAutomatically: (String) -> Unit,
    onDeleteSticker: (Long) -> Unit,
    onMoveStickerUp: (Long) -> Unit,
    onMoveStickerDown: (Long) -> Unit,
    onStartConversion: (List<StickerItem>) -> Unit,
    onUpdatePack: (StickerPack) -> Unit,
    onReprocessSticker: (Long) -> Unit,
    onCancelConversion: () -> Unit,
    onDismissConversionProgress: () -> Unit,
    onValidateSticker: (StickerItem) -> ValidationResult
) {
    var selectedStickerForDialog by remember { mutableStateOf<StickerItem?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAutoNameDialog by remember { mutableStateOf(false) }
    var autoNamePrefix by remember { mutableStateOf("ملصق") }

    val readyCount = stickers.count { it.conversionStatus == ConversionStatus.SUCCESS }
    val pendingCount = stickers.count { it.conversionStatus != ConversionStatus.SUCCESS }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stickers.size} ملصق • $readyCount جاهز لتيليجرام",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("pack_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("pack_export_action_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "تصدير")
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
            // Action Buttons Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onStartConversion(stickers) },
                            enabled = stickers.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("start_conversion_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pendingCount > 0) "بدء التحويل ($pendingCount)" else "إعادة تحويل الكل")
                        }

                        Button(
                            onClick = { showExportDialog = true },
                            enabled = readyCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("open_export_dialog_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير ($readyCount)")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAutoNameDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("auto_name_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسمية تلقائية", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onNavigateToImport(pack.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("add_more_stickers_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة ملصقات", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (stickers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لا توجد ملصقات في هذه الحزمة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onNavigateToImport(pack.id) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة ملصقات الآن")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(stickers, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStickerForDialog = item }
                                .testTag("sticker_card_${item.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Live Animation Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedStickerView(
                                        uriString = item.outputPath ?: item.originalUri,
                                        stickerType = item.type,
                                        modifier = Modifier.size(60.dp),
                                        cacheIdentity = WastickersPreviewKeys.stickerImage(
                                            item.packId,
                                            item.id,
                                            item.outputPath ?: item.originalUri
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.emoji,
                                            fontSize = 20.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Status Badge
                                    when (item.conversionStatus) {
                                        ConversionStatus.SUCCESS -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "جاهز لتيليجرام (${item.formattedConvertedSize})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF047857),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        ConversionStatus.PROCESSING -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.HourglassEmpty,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "جارٍ المعالجة...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        ConversionStatus.FAILED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = item.errorMessage ?: "تعذر التحويل",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = "بانتظار التحويل • ${item.formattedOriginalSize}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Quick Actions
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onMoveStickerUp(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onMoveStickerDown(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteSticker(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
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

    // Sticker Details & Live Inspection Dialog
    if (selectedStickerForDialog != null) {
        val currentSticker = selectedStickerForDialog!!
        val validation = onValidateSticker(currentSticker)
        StickerDetailDialog(
            sticker = currentSticker,
            validationResult = validation,
            onDismiss = { selectedStickerForDialog = null },
            onSave = { newName, newEmoji ->
                onUpdateStickerName(currentSticker.id, newName)
                onUpdateStickerEmoji(currentSticker.id, newEmoji)
                selectedStickerForDialog = null
            },
            onReprocess = {
                onReprocessSticker(currentSticker.id)
                selectedStickerForDialog = null
            }
        )
    }

    // Auto-name dialog
    if (showAutoNameDialog) {
        AlertDialog(
            onDismissRequest = { showAutoNameDialog = false },
            title = { Text("توليد أسماء الملصقات تلقائيًا") },
            text = {
                Column {
                    Text("أدخل بادئة التسمية (Prefix)، وسيتم ترقيم الملصقات تلقائيًا (مثل: ملصق 001، ملصق 002):")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = autoNamePrefix,
                        onValueChange = { autoNamePrefix = it },
                        label = { Text("البادئة (Prefix)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_name_prefix_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prefix = autoNamePrefix.ifBlank { "ملصق" }
                        onGenerateNamesAutomatically(prefix)
                        showAutoNameDialog = false
                    },
                    modifier = Modifier.testTag("confirm_auto_name_button")
                ) {
                    Text("تطبيق التسمية")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoNameDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Conversion Progress Dialog
    if (conversionProgress != null) {
        ConversionProgressDialog(
            progress = conversionProgress,
            onCancel = onCancelConversion,
            onDismiss = onDismissConversionProgress
        )
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            pack = pack,
            stickers = stickers,
            onDismiss = { showExportDialog = false },
            onUpdatePack = onUpdatePack
        )
    }
}
