package com.example.presentation.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.model.StickerItem
import com.example.core.model.ValidationResult
import com.example.presentation.components.AnimatedStickerView
import com.example.wastickers.WastickersPreviewKeys

val COMMON_EMOJIS = listOf(
    "😂", "🤣", "😍", "😡", "😭", "👍", "🔥", "🎉",
    "💔", "👏", "🙏", "😎", "🥳", "🤔", "❤️", "✨",
    "😴", "🥺", "🤯", "💩", "👀", "💯", "🤫", "🤦‍♂️"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerDetailDialog(
    sticker: StickerItem,
    validationResult: ValidationResult,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String) -> Unit,
    onReprocess: () -> Unit
) {
    var name by remember(sticker) { mutableStateOf(sticker.displayName) }
    var emoji by remember(sticker) { mutableStateOf(sticker.emoji) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفاصيل الملصق",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sticker_detail")) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Live Preview
                Card(
                    modifier = Modifier
                        .size(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    AnimatedStickerView(
                        uriString = sticker.outputPath ?: sticker.originalUri,
                        stickerType = sticker.type,
                        modifier = Modifier
                            .size(180.dp)
                            .padding(12.dp),
                        cacheIdentity = WastickersPreviewKeys.stickerImage(
                            sticker.packId,
                            sticker.id,
                            sticker.outputPath ?: sticker.originalUri
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الملصق (Sticker Name)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sticker_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Emoji input & quick selector
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("الرمز التعبيري (Emoji)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sticker_emoji_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "اختيار سريع للـ Emoji:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COMMON_EMOJIS.forEach { em ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (emoji == em) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable { emoji = em }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = em, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Telegram Validation Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (validationResult.isValid) Color(0xFF10B981).copy(alpha = 0.12f)
                        else Color(0xFFEF4444).copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (validationResult.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (validationResult.isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (validationResult.isValid) "جاهز لتيليجرام (Telegram Valid)" else "غير متوافق مع شروط تيليجرام",
                                fontWeight = FontWeight.Bold,
                                color = if (validationResult.isValid) Color(0xFF047857) else Color(0xFFB91C1C),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• الصيغة: ${sticker.type.displayNameAr}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• الحجم الأصلي: ${sticker.formattedOriginalSize} | بعد التحويل: ${sticker.formattedConvertedSize}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (sticker.width > 0 && sticker.height > 0) {
                            Text(
                                text = "• الأبعاد: ${sticker.width}x${sticker.height} px",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (sticker.durationMs > 0) {
                            Text(
                                text = "• المدة: ${String.format(java.util.Locale.US, "%.1f", sticker.durationMs / 1000.0)} ثانية",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (validationResult.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الأسباب: " + validationResult.errors.joinToString("، "),
                                color = Color(0xFFB91C1C),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReprocess,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reprocess_sticker_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إعادة التحويل")
                    }

                    Button(
                        onClick = {
                            onSave(name, emoji)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_sticker_button")
                    ) {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }
    }
}
