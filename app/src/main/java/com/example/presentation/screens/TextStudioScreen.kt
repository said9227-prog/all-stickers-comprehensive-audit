package com.example.presentation.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.StickerPack
import com.example.data.media.GallerySaver
import com.example.creator.FontLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStudioScreen(
    packs: List<StickerPack> = emptyList(),
    initialMode: TextStudioMode = TextStudioMode.TEXT_3D,
    onNavigateBack: () -> Unit = {},
    onSaveToStudio: ((Bitmap, String) -> Unit)? = null,
    onSaveSticker: ((Bitmap, String, String, Long?, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    FontLibrary.initialize(context)

    var currentMode by rememberSaveable { mutableStateOf(initialMode.name) }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val requestExport: (Bitmap) -> Unit = { output ->
        exportBitmap = output
    }

    val movable3D = remember {
        androidx.compose.runtime.movableContentOf {
            TextStudio3DEditor(
                onExport = requestExport
            )
        }
    }
    val movable2D = remember {
        androidx.compose.runtime.movableContentOf {
            TextStudio2DEditor(
                onExport = requestExport
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFF080B18),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "استوديو النصوص",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0F22)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B0F22)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextStudioMode.entries.forEach { mode ->
                        val selected = currentMode == mode.name
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        Color(0xFF6366F1)
                                    } else {
                                        Color(0xFF1A2140)
                                    }
                                )
                                .clickable { currentMode = mode.name }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            when (currentMode) {
                TextStudioMode.TEXT_3D.name -> movable3D()
                else -> movable2D()
            }
        }
    }

    val pending = exportBitmap
    if (pending != null) {
        SaveStickerDialog(
            packs = packs,
            onDismiss = { exportBitmap = null },
            onSave = { name, emoji, packId, newPackName ->
                exportBitmap = null
                if (onSaveSticker != null) {
                    onSaveSticker.invoke(
                        pending,
                        name.ifBlank { "sticker_${System.currentTimeMillis()}" },
                        emoji,
                        packId,
                        newPackName
                    )
                } else if (onSaveToStudio != null) {
                    onSaveToStudio.invoke(
                        pending,
                        "sticker_${System.currentTimeMillis()}.png"
                    )
                } else {
                    val saved = GallerySaver.saveToGallery(context, pending, name)
                    if (saved != null) {
                        Toast.makeText(context, "تم الحفظ في المعرض", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "تعذر الحفظ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
