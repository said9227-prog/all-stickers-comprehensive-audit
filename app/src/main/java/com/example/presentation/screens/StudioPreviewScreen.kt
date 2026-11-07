package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.StudioImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPreviewScreen(
    images: List<StudioImage>,
    initialId: String,
    onNavigateBack: () -> Unit,
    onEditImage: (StudioImage) -> Unit
) {
    val initialIndex = remember(initialId, images) {
        images.indexOfFirst { it.id == initialId }.coerceAtLeast(0)
    }
    var currentIndex by remember(initialId, images) { mutableIntStateOf(initialIndex) }
    val current = images.getOrNull(currentIndex)
    var scale by remember(current?.id) { mutableFloatStateOf(1f) }
    var offset by remember(current?.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(current?.id) {
        scale = 1f
        offset = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "معاينة الصورة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = current?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { onEditImage(current) }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الصورة")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .transformable(transformState),
                contentAlignment = Alignment.Center
            ) {
                if (current != null) {
                    AsyncImage(
                        model = File(current.displayPath),
                        contentDescription = current.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    Text(
                        text = "لا توجد صورة للمعاينة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scale = (scale * 1.25f).coerceAtMost(5f)
                    }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "تكبير",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(
                    onClick = {
                        scale = (scale / 1.25f).coerceAtLeast(1f)
                        if (scale <= 1f) offset = Offset.Zero
                    }
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "تصغير",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                ) {
                    Icon(
                        Icons.Default.ZoomOutMap,
                        contentDescription = "إعادة ضبط التكبير",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "استخدم إصبعين للتكبير والتحريك",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (images.isEmpty()) "" else "${currentIndex + 1} / ${images.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { currentIndex = (currentIndex - 1).coerceAtLeast(0) },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("السابق", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { currentIndex = (currentIndex + 1).coerceAtMost(images.lastIndex) },
                    enabled = currentIndex < images.lastIndex,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("التالي", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
