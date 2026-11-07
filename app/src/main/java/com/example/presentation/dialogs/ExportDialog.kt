package com.example.presentation.dialogs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.ConversionStatus
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.telegram.PackValidationResult
import com.example.telegram.TelegramExporter
import com.example.telegram.TelegramImportResult
import com.example.telegram.TelegramStickerValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ExportDialog(
    pack: StickerPack,
    stickers: List<StickerItem>,
    onDismiss: () -> Unit,
    onUpdatePack: (StickerPack) -> Unit = {}
) {
    val context = LocalContext.current
    val readyStickers = remember(stickers) {
        stickers.filter {
            it.conversionStatus == ConversionStatus.SUCCESS && !it.outputPath.isNullOrBlank()
        }
    }
    val totalReadySize = remember(readyStickers) {
        readyStickers.sumOf { if (it.convertedSize > 0L) it.convertedSize else 0L }
    }

    var shortName by remember(pack.id) {
        mutableStateOf(
            TelegramStickerValidator.sanitizeShortName(
                pack.shortName.ifBlank { pack.name }.ifBlank { "stickers_pack" }
            )
        )
    }
    var validation by remember { mutableStateOf<PackValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(true) }
    val telegramInstalled = remember { TelegramExporter.isTelegramInstalled(context) }
    val shortNameIsValid = TelegramStickerValidator.validatePackShortName(shortName)
    val stickersForTelegram = validation?.validStickers ?: readyStickers

    fun persistPack() {
        if (shortName != pack.shortName) {
            onUpdatePack(pack.copy(shortName = shortName))
        }
    }

    LaunchedEffect(readyStickers, shortName) {
        isValidating = true
        validation = withContext(Dispatchers.IO) {
            TelegramStickerValidator.validateEntirePackForImport(
                pack.copy(shortName = shortName),
                readyStickers
            )
        }
        isValidating = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تصدير الملصقات",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                WhatsAppExportSection(pack = pack, stickers = stickers)

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "تيليجرام",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "تصدير الحزمة إلى Telegram أو حفظها كملف ZIP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Ready summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${readyStickers.size} من ${stickers.size} ملصق جاهز",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الحجم الإجمالي: ${formatBytes(totalReadySize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (readyStickers.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (readyStickers.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Telegram short name editor
                OutlinedTextField(
                    value = shortName,
                    onValueChange = { value ->
                        shortName = value.filterNot { it.isWhitespace() }.take(64)
                    },
                    label = { Text("الاسم المختصر للحزمة (Short Name)") },
                    placeholder = { Text("my_stickers_pack") },
                    singleLine = true,
                    isError = !shortNameIsValid,
                    supportingText = {
                        Text(
                            text = if (shortNameIsValid) {
                                "يُستخدم في بيانات الحزمة عند التصدير"
                            } else {
                                "يجب أن يبدأ بحرف إنجليزي ويحتوي على أحرف وأرقام و _ فقط"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_short_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Validation status
                when {
                    readyStickers.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "لا توجد ملصقات جاهزة لتيليجرام. قم بتحويل الملصقات أولاً من شاشة الحزمة، أو استخدم تصدير واتساب أعلاه.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    isValidating -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "جارٍ التحقق من مطابقة الملصقات لتيليجرام...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    validation?.failures?.isNotEmpty() == true -> {
                        val failures = validation!!.failures
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${failures.size} ملاحظة تحتاج مراجعة قبل التصدير:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                failures.take(3).forEach { failure ->
                                    Text(
                                        text = "• ${failure.stickerName}: ${failure.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (failures.size > 3) {
                                    Text(
                                        text = "و ${failures.size - 3} ملاحظة أخرى...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "كل الملصقات الجاهزة مطابقة لمواصفات تيليجرام",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Direct import via Telegram intent
                Button(
                    onClick = {
                        val exportPack = pack.copy(shortName = shortName)
                        val result = TelegramExporter.prepareTelegramImport(
                            context,
                            exportPack,
                            stickersForTelegram
                        )
                        when (result) {
                            is TelegramImportResult.Success -> {
                                try {
                                    persistPack()
                                    context.startActivity(result.intent)
                                    Toast.makeText(
                                        context,
                                        "جارٍ فتح Telegram لإنشاء حزمة الملصقات (${result.validStickerCount} ملصق)...",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                } catch (e: Exception) {
                                    com.example.diagnostics.DiagnosticsRepository.logError(
                                        context = context,
                                        operation = "TELEGRAM_IMPORT",
                                        stage = com.example.diagnostics.DiagnosticStage.IMPORT,
                                        fileName = pack.name,
                                        exception = e,
                                        customMessage = "TELEGRAM_IMPORT_LAUNCH_FAILED: Failed to start Telegram import intent"
                                    )
                                    Toast.makeText(
                                        context,
                                        "فشل إطلاق تطبيق Telegram. يرجى التأكد من تثبيت التطبيق.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            is TelegramImportResult.Error -> {
                                Toast.makeText(context, result.userMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = stickersForTelegram.isNotEmpty() && telegramInstalled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_telegram_intent_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال مباشر إلى تيليجرام")
                }

                if (!telegramInstalled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(TelegramExporter.createTelegramInstallIntent())
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "تعذر فتح متجر التطبيقات. ثبّت Telegram يدويًا ثم أعد المحاولة.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("install_telegram_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تيليجرام غير مثبت — تثبيت الآن")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: ZIP archive
                Button(
                    onClick = {
                        val exportDir = File(context.cacheDir, "exports")
                        if (!exportDir.exists()) exportDir.mkdirs()
                        val safePackName = shortName.ifBlank { "stickers" }
                        val zipFile = File(
                            exportDir,
                            "Telegram_${safePackName}_${System.currentTimeMillis()}.zip"
                        )

                        val success = TelegramExporter.buildTelegramStickersZip(
                            context,
                            pack.copy(shortName = shortName),
                            stickersForTelegram,
                            zipFile
                        )
                        if (success && zipFile.exists()) {
                            persistPack()
                            val shareIntent = TelegramExporter.createShareZipIntent(context, zipFile)
                            context.startActivity(
                                Intent.createChooser(shareIntent, "مشاركة أرشيف الملصقات")
                            )
                            onDismiss()
                        } else {
                            Toast.makeText(context, "فشل إنشاء ملف الأرشيف المضغوط", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = stickersForTelegram.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_zip_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ ومشاركة كملف ZIP")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Share the sticker files directly
                OutlinedButton(
                    onClick = {
                        try {
                            val shareIntent = TelegramExporter.createShareStickersIntent(context, readyStickers)
                            if (shareIntent != null) {
                                persistPack()
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "مشاركة الملصقات")
                                )
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    "تعذر تجهيز ملفات الملصقات للمشاركة",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "فشل مشاركة الملصقات: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = readyStickers.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_share_stickers_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مشاركة الملصقات مباشرة")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "طريقة التصدير:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• واتساب: يضيف حزمة أصلية إلى واتساب أو واتساب بزنس مباشرة.\n" +
                                "• التقسيم تلقائي: كل 30 ملصقًا تصبح حزمة، والثابتة تُفصل عن المتحركة.\n" +
                                "• يمكن تحويل الملصقات المتحركة إلى ثابتة (أول إطار) قبل الإضافة.\n" +
                                "• الحزم التي تحتوي أقل من 3 ملصقات تنتظر إضافة الباقي قبل التفعيل.\n" +
                                "• الإرسال المباشر يفتح تيليجرام ويجهّز الحزمة تلقائيًا (يتطلب تثبيت التطبيق).\n" +
                                "• ملف ZIP مناسب للحفظ أو الإرسال إلى بوت @Stickers.\n" +
                                "• المشاركة المباشرة ترسل ملفات الملصقات إلى أي تطبيق آخر.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_export_dialog")
                ) {
                    Text("إغلاق")
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
