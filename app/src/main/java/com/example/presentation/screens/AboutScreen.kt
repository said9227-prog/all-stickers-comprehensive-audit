package com.example.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OpenSourceLibrary(
    val name: String,
    val authorOrOrg: String,
    val license: String,
    val purpose: String
)

val LIBRARIES_LIST = listOf(
    OpenSourceLibrary(
        name = "WhatsApp Stickers Android",
        authorOrOrg = "WhatsApp Inc. / Meta",
        license = "BSD 2-Clause License",
        purpose = "معايير تنسيق ملصقات واتساب وهيكلية ملفات .wastickers والبيانات الوصفية"
    ),
    OpenSourceLibrary(
        name = "tg-video-sticker-webm-creator",
        authorOrOrg = "ahmcho",
        license = "MIT License",
        purpose = "معايير وضوابط Telegram Video Stickers (WebM VP9، yuva420p، 30fps، 512px)"
    ),
    OpenSourceLibrary(
        name = "Android Jetpack & Room Database",
        authorOrOrg = "Google LLC",
        license = "Apache License 2.0",
        purpose = "إدارة البيانات المحلية وقواعد البيانات ومعالجة وسائط أندرويد"
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose & Material 3",
        authorOrOrg = "Google LLC",
        license = "Apache License 2.0",
        purpose = "واجهة المستخدم الحديثة بنظام Material You والتصميم المتجاوب"
    ),
    OpenSourceLibrary(
        name = "Kotlin Coroutines & Flow",
        authorOrOrg = "JetBrains s.r.o.",
        license = "Apache License 2.0",
        purpose = "معالجة الوسائط المتعددة غير المتزامنة لمنع تجميد الشاشة أو حدوث ANR"
    ),
    OpenSourceLibrary(
        name = "Coil Image Loader",
        authorOrOrg = "Coil Contributors",
        license = "Apache License 2.0",
        purpose = "تحميل ومعاينة الصور والرسوم المتحركة في Jetpack Compose"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "عن التطبيق والتراخيص",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("about_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                text = "All stickers — ملصقاتك كلها وتصديرها إلى تيليجرام",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تطبيق أندرويد إنتاجي متكامل يقوم بتحويل ملصقات واتساب (الثابتة والمتحركة Animated WebP) إلى ملصقات تيليجرام رسمية (WEBM VP9 بشفافية أو WebP) محليًا بالكامل على الجهاز وبدون خادم خارجي، مع الحفاظ الكامل على جودة الحركة والشفافية وأسماء الملصقات والـ Emojis.\n\nيتضمن التطبيق استوديو ملصقات لإنشاء ملصقات جديدة من الصور أو النصوص، مع القص وإزالة الخلفية والممحاة والإيموجي والقوالب الجاهزة.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Telegram Specifications Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                text = "مواصفات تيليجرام الرسمية المعتمدة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• الملصقات المتحركة: صيغة WebM بترميز VP9 مع الشفافية، بدون مسار صوتي، أقصى مدة 3 ثوانٍ، أقصى معدل إطارات 30fps، والحجم لا يتجاوز 256 KB.\n" +
                                    "• الملصقات الثابتة: صيغة WebP أو PNG مع خلفية شفافة والحجم لا يتجاوز 512 KB.\n" +
                                    "• الأبعاد: أحد الأبعاد بالضبط 512px والآخر 512px أو أقل بنسبة العرض إلى الارتفاع الأصلية.\n" +
                                    "• التصدير: دعم Intent الاستيراد الرسمي لتيليجرام وتصدير الأرشيف ZIP المعتمد لبوت @Stickers.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Open Source Licenses Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "المكتبات مفتوحة المصدر والتراخيص (Open Source Licenses)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Libraries
            items(LIBRARIES_LIST) { lib ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = lib.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${lib.authorOrOrg} • ${lib.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lib.purpose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
