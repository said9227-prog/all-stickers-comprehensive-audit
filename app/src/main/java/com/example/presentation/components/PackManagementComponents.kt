package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.StickerPreview
import com.example.wastickers.WastickersPreviewKeys
import java.util.concurrent.atomic.AtomicInteger

/**
 * Global budget that prevents dozens of animated WebP thumbnails from
 * decoding at the same time while scrolling the pack list.
 */
private object PreviewAnimationBudget {
    private const val MAX_CONCURRENT = 4
    private val active = AtomicInteger(0)

    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            if (current >= MAX_CONCURRENT) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        active.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }
}

/**
 * A short row of sticker thumbnails shown inside each pack card. Only the first
 * animated thumbnail may play, and a global budget stops off-screen cards from
 * keeping many animations alive. Static stickers are always shown.
 */
@Composable
fun PackPreviewStrip(
    previews: List<StickerPreview>,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = 48.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (previews.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "لا توجد معاينات",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        } else {
            previews.forEachIndexed { index, preview ->
                val wantsAnimation = index == 0 && preview.isAnimated
                var budgetGranted by remember(preview.id) { mutableStateOf(false) }
                DisposableEffect(preview.id, wantsAnimation) {
                    budgetGranted = wantsAnimation && PreviewAnimationBudget.tryAcquire()
                    onDispose {
                        if (budgetGranted) PreviewAnimationBudget.release()
                    }
                }
                Box(
                    modifier = Modifier
                        .size(thumbnailSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedStickerView(
                        uriString = preview.previewUri,
                        stickerType = preview.type,
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = wantsAnimation && budgetGranted,
                        // pack + sticker + source identity: two packs that both
                        // contain "1.webp" can never share a cached preview.
                        cacheIdentity = WastickersPreviewKeys.stickerImage(
                            preview.packId, preview.id, preview.previewUri
                        )
                    )
                }
            }

            if (previews.size == 1) {
                Text(
                    text = "معاينة",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}
