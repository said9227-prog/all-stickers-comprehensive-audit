package com.example.presentation.dialogs

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.StickerItem
import com.example.core.model.StickerPack
import com.example.core.model.StickerType
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.DiagnosticsRepository
import com.example.whatsapp.WhatsAppExportOptions
import com.example.whatsapp.WhatsAppExportResult
import com.example.whatsapp.WhatsAppExporter
import com.example.whatsapp.WhatsAppPackPlan
import com.example.whatsapp.WhatsAppPackPlanner
import com.example.whatsapp.WhatsAppStickerValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppBusinessGreen = Color(0xFF128C7E)

private data class PendingWhatsAppAdd(
    val planId: String,
    val packId: String,
    val packName: String,
    val business: Boolean,
    val animated: Boolean,
    val stickerCount: Int
)

@Composable
fun WhatsAppExportSection(
    pack: StickerPack,
    stickers: List<StickerItem>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var convertAnimatedToStatic by remember(pack.id) { mutableStateOf(false) }
    var preparingPlanId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var pendingAdd by remember { mutableStateOf<PendingWhatsAppAdd?>(null) }
    val consumerInstalled = remember(pack.id) { WhatsAppExporter.isInstalled(context, false) }
    val businessInstalled = remember(pack.id) { WhatsAppExporter.isInstalled(context, true) }

    val planSet = remember(pack, stickers, convertAnimatedToStatic) {
        WhatsAppPackPlanner.plan(
            pack = pack,
            stickers = stickers,
            convertAnimatedToStatic = convertAnimatedToStatic
        )
    }
    val webmCount = remember(stickers) {
        stickers.count { it.type == StickerType.WEBM && it.originalUri.isNotBlank() }
    }

    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingAdd ?: return@rememberLauncherForActivityResult
        pendingAdd = null
        val validationError = result.data
            ?.getStringExtra("validation_error")
            ?.takeIf { it.isNotBlank() }
        scope.launch {
            delay(600)
            val whitelisted = withContext(Dispatchers.IO) {
                WhatsAppExporter.isWhitelisted(context, pending.business, pending.packId)
            }
            val target = if (pending.business) "واتساب بزنس" else "واتساب"
            val kind = if (pending.animated) "المتحركة" else "الثابتة"
            if (result.resultCode == Activity.RESULT_OK || whitelisted) {
                error = null
                success = "تمت إضافة الحزمة $kind إلى $target (${pending.stickerCount} ملصق)."
                DiagnosticsRepository.logInfo(
                    operation = "WHATSAPP_ADD",
                    stage = DiagnosticStage.EXPORT,
                    fileName = pending.packId,
                    message = "WhatsApp accepted pack: plan=${pending.planId}، target=$target، " +
                        "resultCode=${result.resultCode}، whitelisted=$whitelisted، " +
                        "validation_error=${validationError ?: "none"}"
                )
            } else {
                val reason = validationError ?: "لم يؤكد واتساب إضافة الحزمة."
                success = null
                error = "لم تُضف الحزمة $kind إلى $target: $reason"
                DiagnosticsRepository.logError(
                    context = context,
                    operation = "WHATSAPP_ADD",
                    stage = DiagnosticStage.EXPORT,
                    fileName = pending.packId,
                    customMessage = "pack=${pending.packId}، plan=${pending.planId}، target=$target، " +
                        "resultCode=${result.resultCode}، whitelisted=$whitelisted، validation_error=$reason",
                    sourceType = "WHATSAPP_ADD",
                    sourceUri = pending.packId,
                    sourceDisplayName = pending.packName
                )
            }
        }
    }

    fun startExport(plan: WhatsAppPackPlan, business: Boolean) {
        if (preparingPlanId != null) return
        error = null
        success = null
        preparingPlanId = plan.id
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    WhatsAppExporter.preparePack(
                        context = context,
                        pack = pack,
                        stickers = plan.stickers,
                        options = WhatsAppExportOptions(
                            animated = plan.animated,
                            packName = plan.name,
                            identifierSuffix = plan.identifierSuffix
                        )
                    )
                }
            } catch (e: Exception) {
                WhatsAppExportResult.Failure(
                    "تعذر تجهيز حزمة واتساب. حاول مرة أخرى.",
                    e.message
                )
            }
            preparingPlanId = null
            when (result) {
                is WhatsAppExportResult.Success -> {
                    try {
                        pendingAdd = PendingWhatsAppAdd(
                            planId = plan.id,
                            packId = result.packId,
                            packName = result.packName,
                            business = business,
                            animated = plan.animated,
                            stickerCount = result.stickerCount
                        )
                        addLauncher.launch(
                            WhatsAppExporter.createAddIntent(
                                context,
                                business,
                                result.packId,
                                result.packName
                            )
                        )
                        DiagnosticsRepository.logInfo(
                            operation = "WHATSAPP_ADD",
                            stage = DiagnosticStage.EXPORT,
                            fileName = result.packId,
                            message = "Launched add intent: plan=${plan.id}، " +
                                "target=${if (business) "whatsapp_business" else "whatsapp"}، " +
                                "authority=${result.authority}، stickers=${result.stickerCount}، " +
                                "animated=${result.animated}"
                        )
                        success = "تم تجهيز الحزمة \"${result.packName}\" وفتح " +
                            "${if (business) "واتساب بزنس" else "واتساب"}. " +
                            "أكمل الإضافة ثم ارجع للتأكيد (${result.stickerCount} ملصق)."
                    } catch (_: ActivityNotFoundException) {
                        pendingAdd = null
                        error = "تعذر فتح واتساب. تأكد من تثبيت التطبيق ثم أعد المحاولة."
                    } catch (_: Exception) {
                        pendingAdd = null
                        error = "تعذر فتح واتساب لإضافة الحزمة."
                    }
                }
                is WhatsAppExportResult.Failure -> {
                    error = result.userMessage
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "واتساب / واتساب بزنس",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "حزمة ملصقات أصلية تعمل مع التطبيقين — تُحوَّل تلقائيًا إلى WebP، " +
                "وتُقسَّم إلى حزم مستقلة عند الحاجة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (planSet.animatedCount > 0 || convertAnimatedToStatic) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تحويل الملصقات المتحركة إلى ثابتة",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (convertAnimatedToStatic) {
                                "سيتم أخذ أول إطار من كل ملصق متحرك وإضافته كملصق ثابت."
                            } else {
                                "فعّل الخيار لإضافة نسخة ثابتة (أول إطار) بدل النسخة المتحركة."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = convertAnimatedToStatic,
                        onCheckedChange = { checked ->
                            convertAnimatedToStatic = checked
                            error = null
                            success = null
                        },
                        modifier = Modifier.testTag("convert_animated_to_static_switch")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        SummaryCard(planSet = planSet, convertAnimatedToStatic = convertAnimatedToStatic)

        if (webmCount > 0 && !convertAnimatedToStatic) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$webmCount ملصق بصيغة WebM. واتساب لا يدعم WebM كمتحرك — " +
                    "فعّل خيار التحويل إلى ثابتة أو استبدله بنسخة WebP متحركة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (planSet.plans.isEmpty()) {
            Text(
                text = "لا توجد ملصقات صالحة للتصدير إلى واتساب.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        planSet.plans.forEach { plan ->
            PlanCard(
                plan = plan,
                preparing = preparingPlanId == plan.id,
                enabled = preparingPlanId == null,
                consumerInstalled = consumerInstalled,
                businessInstalled = businessInstalled,
                onAddConsumer = { startExport(plan, false) },
                onAddBusiness = { startExport(plan, true) },
                onInstallConsumer = {
                    try {
                        context.startActivity(WhatsAppExporter.createInstallIntent(false))
                    } catch (_: Exception) {
                        error = "تعذر فتح متجر التطبيقات. ثبّت واتساب يدويًا ثم أعد المحاولة."
                    }
                },
                onInstallBusiness = {
                    try {
                        context.startActivity(WhatsAppExporter.createInstallIntent(true))
                    } catch (_: Exception) {
                        error = "تعذر فتح متجر التطبيقات. ثبّت واتساب بزنس يدويًا ثم أعد المحاولة."
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (planSet.plans.any { !it.canAdd }) {
            Text(
                text = "الحزم التي تحتوي أقل من ${WhatsAppStickerValidator.MIN_STICKERS} " +
                    "ملصقات تبقى جاهزة هنا وتُفعَّل إضافتها تلقائيًا بعد إضافة الملصقات الناقصة إلى الحزمة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (success != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = success!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = WhatsAppGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "الحد الأدنى 3 والحد الأقصى 30 ملصقًا لكل حزمة. الثابت 512×512 وأقل من 100KB، " +
                "والمتحرك 512×512 وأقل من 500KB ومدة أقصاها 10 ثوانٍ. " +
                "الملصقات الثابتة والمتحركة تُقسَّم تلقائيًا إلى حزم منفصلة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SummaryCard(
    planSet: com.example.whatsapp.WhatsAppPackPlanSet,
    convertAnimatedToStatic: Boolean
) {
    val generated = planSet.plans.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (planSet.plans.any { it.canAdd }) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (planSet.plans.any { it.canAdd }) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = if (planSet.plans.any { it.canAdd }) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${planSet.usableCount} ملصق • " +
                        "${planSet.staticCount} ثابت • ${planSet.animatedCount} متحرك",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("سيتم إنشاء $generated ")
                    append(if (generated == 1) "حزمة" else "حزم")
                    if (planSet.oversized) append(" (تقسيم تلقائي بحد أقصى 30)")
                    if (convertAnimatedToStatic) {
                        append(" — سيتم تحويل الملصقات المتحركة إلى ثابتة")
                    }
                    if (planSet.skippedCount > 0) {
                        append(" — تم تجاهل ${planSet.skippedCount} بدون مصدر")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            if (planSet.hasMixed && !convertAnimatedToStatic) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "تم فصل الملصقات الثابتة عن المتحركة تلقائيًا لأن واتساب " +
                        "لا يسمح بخلط النوعين في حزمة واحدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: WhatsAppPackPlan,
    preparing: Boolean,
    enabled: Boolean,
    consumerInstalled: Boolean,
    businessInstalled: Boolean,
    onAddConsumer: () -> Unit,
    onAddBusiness: () -> Unit,
    onInstallConsumer: () -> Unit,
    onInstallBusiness: () -> Unit
) {
    val kindLabel = if (plan.animated) "المتحركة" else "الثابتة"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (plan.animated) "متحركة" else "ثابتة",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (plan.animated) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${plan.stickerCount} ملصق" +
                    if (plan.chunkCount > 1) " • الحزمة ${plan.chunkIndex + 1}/${plan.chunkCount}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )

            if (!plan.canAdd) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "بانتظار ${plan.missingForMinimum} ملصقات إضافية لتفعيل الإضافة " +
                        "(الحد الأدنى ${WhatsAppStickerValidator.MIN_STICKERS}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (preparing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جارٍ ضغط الملصقات وتجهيز الحزمة $kindLabel...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val canAdd = plan.canAdd && enabled
                if (consumerInstalled) {
                    Button(
                        onClick = onAddConsumer,
                        enabled = canAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("export_whatsapp_button_${plan.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "إضافة الحزمة $kindLabel إلى واتساب",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onInstallConsumer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("واتساب غير مثبت — تثبيت الآن", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (businessInstalled) {
                    Button(
                        onClick = onAddBusiness,
                        enabled = canAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppBusinessGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("export_whatsapp_business_button_${plan.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "إضافة الحزمة $kindLabel إلى واتساب بزنس",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onInstallBusiness,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("واتساب بزنس غير مثبت — تثبيت الآن", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
