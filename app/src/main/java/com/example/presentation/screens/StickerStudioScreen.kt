package com.example.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.model.StudioImage
import com.example.presentation.components.AuroraDialog
import com.example.presentation.components.AuroraGradientButton
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStudioScreen(
    images: List<StudioImage>,
    onNavigateBack: () -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onPreviewImage: (String) -> Unit,
    onEditImage: (StudioImage) -> Unit,
    onDeleteImage: (String) -> Unit,
    onCreateText: () -> Unit,
    onOpen3DStudio: () -> Unit = {},
    onOpenAiStudio: () -> Unit = {},
    onCreatePack: (List<StudioImage>) -> Unit,
    isCreatingPack: Boolean = false
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImage by remember { mutableStateOf<StudioImage?>(null) }
    var deleteTarget by remember { mutableStateOf<StudioImage?>(null) }
    var showSingleSourceSheet by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(images) {
        val validIds = images.map { it.id }.toSet()
        selectedIds.retainAll(validIds)
    }

    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onAddImages(listOf(it)) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddImages(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                onAddImages(listOf(uri))
            }
        }
    }

    fun launchCamera() {
        try {
            val photoFile = File(
                context.cacheDir,
                "camera_studio_${System.currentTimeMillis()}.jpg"
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "لا يوجد تطبيق كاميرا متاح على هذا الجهاز",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleSelection(image: StudioImage) {
        if (selectedIds.contains(image.id)) {
            selectedIds.remove(image.id)
        } else {
            selectedIds.add(image.id)
        }
    }

    val selectedImages = images.filter { it.id in selectedIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "استوديو الملصقات",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "صمّم ملصقاتك بطريقتك",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تم تحديد ${selectedIds.size} صورة",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { selectedIds.clear() }) {
                                Text("إلغاء التحديد")
                            }
                        }
                        Button(
                            onClick = { onCreatePack(selectedImages) },
                            enabled = !isCreatingPack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("studio_create_pack"),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isCreatingPack) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("جارٍ إنشاء الحزمة...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إنشاء حزمة ملصقات (${selectedIds.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StudioHeroCard(
                    imageCount = images.size,
                    selectedCount = selectedIds.size
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.Default.CameraAlt,
                        label = "صورة واحدة",
                        description = "معرض أو كاميرا",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { showSingleSourceSheet = true },
                        modifier = Modifier.weight(1f),
                        testTag = "studio_single_image"
                    )
                    QuickActionTile(
                        icon = Icons.Default.PhotoLibrary,
                        label = "عدة صور",
                        description = "تحديد متعدد",
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        testTag = "studio_multi_images"
                    )
                    QuickActionTile(
                        icon = Icons.Default.TextFields,
                        label = "ملصق نصي",
                        description = "قوالب مزخرفة",
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = onCreateText,
                        modifier = Modifier.weight(1f),
                        testTag = "studio_text_sticker"
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                AuroraGradientButton(
                    label = "استوديو النصوص والمجسمات 3D",
                    onClick = onOpen3DStudio,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.AutoAwesome
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AuroraGradientButton(
                    label = "استوديو الذكاء الاصطناعي",
                    onClick = onOpenAiStudio,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Spa
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "صور الاستوديو",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedIds.isEmpty()) {
                                "${images.size} صورة — اختر صورًا لإنشاء حزمة"
                            } else {
                                "${selectedIds.size} محددة من ${images.size}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (images.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedIds.size == images.size) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(images.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                if (selectedIds.size == images.size) "إلغاء الكل" else "تحديد الكل",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (images.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryCard()
                }
            } else {
                items(images, key = { it.id }) { image ->
                    StudioImageCard(
                        image = image,
                        selected = image.id in selectedIds,
                        onToggleSelection = { toggleSelection(image) },
                        onClick = { selectedImage = image }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                TipsCard()
            }
        }
    }

    if (showSingleSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSingleSourceSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
            ) {
                Text(
                    text = "إضافة صورة واحدة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اختر مصدر الصورة، وستُضاف إلى الاستوديو دون حذف الصور السابقة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showSingleSourceSheet = false
                        singleGalleryLauncher.launch("image/*")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("من المعرض")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        showSingleSourceSheet = false
                        launchCamera()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("من الكاميرا")
                }
            }
        }
    }

    selectedImage?.let { image ->
        ImageActionsDialog(
            image = image,
            selected = image.id in selectedIds,
            onDismiss = { selectedImage = null },
            onToggleSelection = {
                toggleSelection(image)
                selectedImage = null
            },
            onPreview = {
                selectedImage = null
                onPreviewImage(image.id)
            },
            onEdit = {
                selectedImage = null
                onEditImage(image)
            },
            onDelete = {
                selectedImage = null
                deleteTarget = image
            }
        )
    }

    deleteTarget?.let { image ->
        AuroraDialog(
            onDismissRequest = { deleteTarget = null },
            title = "حذف الصورة؟",
            subtitle = "لا يمكن التراجع عن هذا الإجراء",
            icon = Icons.Default.Delete,
            confirmLabel = "حذف",
            onConfirm = {
                onDeleteImage(image.id)
                selectedIds.remove(image.id)
                deleteTarget = null
            }
        ) {
            Text(
                text = "سيتم حذف «${image.name}» وكل التعديلات المحفوظة عليها.",
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StudioHeroCard(
    imageCount: Int,
    selectedCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "حوّل صورك إلى ملصقات",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "أضف صورة أو عدة صور، عدّل كل واحدة على حدة، ثم أنشئ حزمة جاهزة للمعاينة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(text = "$imageCount صورة")
                    HeroChip(
                        text = if (selectedCount == 0) "لم يتم تحديد صور" else "$selectedCount محددة"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Card(
        modifier = modifier
            .height(126.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioImageCard(
    image: StudioImage,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "studio_image_border"
    )
    val shape = RoundedCornerShape(18.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                AsyncImage(
                    model = File(image.displayPath),
                    contentDescription = image.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    )
                }
                if (image.isText) {
                    Text(
                        text = "نصي",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                RoundedCornerShape(7.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Black.copy(alpha = 0.38f)
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        .clickable(onClick = onToggleSelection),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (selected) {
                            "إلغاء تحديد ${image.name}"
                        } else {
                            "تحديد ${image.name}"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(9.dp)) {
                Text(
                    text = image.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (image.editedPath != null) "معدّلة" else "أصلية",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "لا توجد صور بعد",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "أضف صورة واحدة أو عدة صور من المعرض أو الكاميرا لتظهر هنا.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImageActionsDialog(
    image: StudioImage,
    selected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AuroraDialog(
        onDismissRequest = onDismiss,
        title = image.name,
        subtitle = "اختر ما تريد فعله بهذه الصورة",
        icon = Icons.Default.AutoAwesome,
        showDismiss = true,
        dismissLabel = "إغلاق"
    ) {
        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("معاينة")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تعديل")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onToggleSelection,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selected) "إلغاء التحديد" else "تحديد لإنشاء حزمة")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("حذف", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "نصائح سريعة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• اضغط مربع التحديد على أي صورة لاختيارها للحزمة.\n" +
                    "• اضغط الصورة نفسها للمعاينة أو التعديل أو الحذف.\n" +
                    "• التعديلات تُحفظ لكل صورة على حدة، ويمكن فتحها مجددًا.\n" +
                    "• عند إنشاء الحزمة تنتقل مباشرة إلى صفحة حزم الملصقات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
