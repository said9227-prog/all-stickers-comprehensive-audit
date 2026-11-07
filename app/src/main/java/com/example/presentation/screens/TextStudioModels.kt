package com.example.presentation.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.creator.FontLibrary
import com.example.creator.StickerTextElement
import com.example.creator.StickerWatermark
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import io.github.sceneview.math.Direction
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class TextStudioMode(val label: String) {
    PLAIN_TEXT("ملصق نصي"),
    TEXT_3D("نص ثلاثي الأبعاد")
}

internal val STUDIO_COLOR_PALETTE = intArrayOf(
    0xFFFFFFFF.toInt(),
    0xFFFFD166.toInt(),
    0xFFF97316.toInt(),
    0xFFEF4444.toInt(),
    0xFFEC4899.toInt(),
    0xFFA855F7.toInt(),
    0xFF6366F1.toInt(),
    0xFF22D3EE.toInt(),
    0xFF10B981.toInt(),
    0xFF84CC16.toInt(),
    0xFF0F172A.toInt(),
    0xFF94A3B8.toInt()
)

internal enum class MetalStyle(
    val label: String,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float,
    val colors: IntArray,
    val glowColor: Int,
    val unlit: Boolean = false
) {
    GOLD("ذهب", 1f, 0.16f, 1f, intArrayOf(0xFF6B3F00.toInt(), 0xFFFFC83D.toInt(), 0xFFFFF4B8.toInt(), 0xFFFFD166.toInt(), 0xFF8A5A00.toInt()), 0xFFFFD166.toInt()),
    SILVER("فضة", 1f, 0.12f, 1f, intArrayOf(0xFF475569.toInt(), 0xFFE2E8F0.toInt(), 0xFFFFFFFF.toInt(), 0xFFCBD5E1.toInt(), 0xFF64748B.toInt()), 0xFFE2E8F0.toInt()),
    CHROME("كروم", 1f, 0.05f, 1f, intArrayOf(0xFF0C4A6E.toInt(), 0xFF7DD3FC.toInt(), 0xFFF8FAFC.toInt(), 0xFF38BDF8.toInt(), 0xFF1E3A8A.toInt()), 0xFF7DD3FC.toInt()),
    NEON("نيون", 0f, 1f, 0f, intArrayOf(0xFFFF2D95.toInt(), 0xFF22D3EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFA855F7.toInt(), 0xFF22D3EE.toInt()), 0xFF22D3EE.toInt(), true),
    HOLOGRAM("هولوغرام", 0f, 1f, 0f, intArrayOf(0xFF22D3EE.toInt(), 0xFFA855F7.toInt(), 0xFFF472B6.toInt(), 0xFFFFD166.toInt(), 0xFF22D3EE.toInt()), 0xFFA855F7.toInt(), true),
    COPPER("نحاس", 1f, 0.28f, 0.9f, intArrayOf(0xFF5C2E1A.toInt(), 0xFFB45309.toInt(), 0xFFF59E0B.toInt(), 0xFFD97706.toInt(), 0xFF7C2D12.toInt()), 0xFFF59E0B.toInt()),
    ROSE_GOLD("ذهب وردي", 1f, 0.2f, 0.95f, intArrayOf(0xFF7C2D4A.toInt(), 0xFFF9A8D4.toInt(), 0xFFFFE4E6.toInt(), 0xFFFB7185.toInt(), 0xFF9F1239.toInt()), 0xFFFB7185.toInt()),
    GLASS("زجاج", 0.1f, 0.05f, 0.95f, intArrayOf(0xFF94A3B8.toInt(), 0xFFE0F2FE.toInt(), 0xFFFFFFFF.toInt(), 0xFFBAE6FD.toInt(), 0xFF7DD3FC.toInt()), 0xFFE0F2FE.toInt()),
    MATTE("مطفي", 0f, 0.96f, 0.1f, intArrayOf(0xFF1F2937.toInt(), 0xFF6B7280.toInt(), 0xFFD1D5DB.toInt(), 0xFF9CA3AF.toInt(), 0xFF374151.toInt()), 0xFFD1D5DB.toInt()),
    PEARL("لؤلؤي", 0.55f, 0.18f, 0.75f, intArrayOf(0xFFE9D5FF.toInt(), 0xFFFDF2F8.toInt(), 0xFFFFFFFF.toInt(), 0xFFDBEAFE.toInt(), 0xFFFEF3C7.toInt()), 0xFFFDF2F8.toInt()),
    OBSIDIAN("سبج", 0.7f, 0.2f, 0.6f, intArrayOf(0xFF020617.toInt(), 0xFF1E293B.toInt(), 0xFF64748B.toInt(), 0xFF334155.toInt(), 0xFF0F172A.toInt()), 0xFF64748B.toInt()),
    RAINBOW("قوس قزح", 0f, 1f, 0f, intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFA855F7.toInt()), 0xFF3B82F6.toInt(), true),
    EMERALD("زمرد", 0.85f, 0.14f, 0.9f, intArrayOf(0xFF064E3B.toInt(), 0xFF10B981.toInt(), 0xFF6EE7B7.toInt(), 0xFF34D399.toInt(), 0xFF065F46.toInt()), 0xFF6EE7B7.toInt()),
    SAPPHIRE("سافير", 0.85f, 0.12f, 0.9f, intArrayOf(0xFF1E3A8A.toInt(), 0xFF3B82F6.toInt(), 0xFF93C5FD.toInt(), 0xFF60A5FA.toInt(), 0xFF1E40AF.toInt()), 0xFF93C5FD.toInt()),
    RUBY("روبي", 0.8f, 0.16f, 0.85f, intArrayOf(0xFF7F1D1D.toInt(), 0xFFEF4444.toInt(), 0xFFFCA5A5.toInt(), 0xFFF87171.toInt(), 0xFF991B1B.toInt()), 0xFFFCA5A5.toInt()),
    AMETHYST("جمشت", 0.6f, 0.22f, 0.8f, intArrayOf(0xFF4C1D95.toInt(), 0xFF8B5CF6.toInt(), 0xFFC4B5FD.toInt(), 0xFFA78BFA.toInt(), 0xFF5B21B6.toInt()), 0xFFC4B5FD.toInt()),
    BRONZE("برونز", 0.95f, 0.35f, 0.8f, intArrayOf(0xFF44303C.toInt(), 0xFF9A7B4F.toInt(), 0xFFCDAA7D.toInt(), 0xFFB08D57.toInt(), 0xFF59331D.toInt()), 0xFFCDAA7D.toInt()),
    PLATINUM("بلاتين", 1f, 0.09f, 1f, intArrayOf(0xFF64748B.toInt(), 0xFFF8FAFC.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2E8F0.toInt(), 0xFF94A3B8.toInt()), 0xFFF8FAFC.toInt()),
    TOXIC("سام", 0f, 0.7f, 0.4f, intArrayOf(0xFF14532D.toInt(), 0xFF84CC16.toInt(), 0xFFD9F99D.toInt(), 0xFFA3E635.toInt(), 0xFF166534.toInt()), 0xFFA3E635.toInt(), true),
    PASTEL("باستيل", 0.15f, 0.55f, 0.6f, intArrayOf(0xFFBBF7D0.toInt(), 0xFFFBCFE8.toInt(), 0xFFFDE68A.toInt(), 0xFFBAE6FD.toInt(), 0xFFDDD6FE.toInt()), 0xFFFBCFE8.toInt()),
    GALAXY("مجرة", 0.5f, 0.3f, 0.85f, intArrayOf(0xFF0B1026.toInt(), 0xFF5B21B6.toInt(), 0xFF93C5FD.toInt(), 0xFFF472B6.toInt(), 0xFF164E63.toInt()), 0xFF93C5FD.toInt()),
    INK("حبر", 0.2f, 0.85f, 0.2f, intArrayOf(0xFF0F172A.toInt(), 0xFF334155.toInt(), 0xFF94A3B8.toInt(), 0xFF475569.toInt(), 0xFF1E293B.toInt()), 0xFF94A3B8.toInt()),
    FIRE_METAL("ناري", 0.75f, 0.25f, 0.9f, intArrayOf(0xFF7C2D12.toInt(), 0xFFEA580C.toInt(), 0xFFFDE047.toInt(), 0xFFF59E0B.toInt(), 0xFFDC2626.toInt()), 0xFFFDE047.toInt()),
    ICE_METAL("جليدي", 0.4f, 0.1f, 0.95f, intArrayOf(0xFF0EA5E9.toInt(), 0xFFBAE6FD.toInt(), 0xFFF0F9FF.toInt(), 0xFF7DD3FC.toInt(), 0xFF0369A1.toInt()), 0xFFBAE6FD.toInt())
}

internal enum class ShapeStyle(val label: String) {
    NONE("بدون قاعدة"),
    SLAB("بلاطة"),
    CUBE("مكعب"),
    SPHERE("كرة"),
    CYLINDER("أسطوانة"),
    DISC("قرص"),
    DOUBLE_DISC("قرصان"),
    STACKED_DISCS("أقراص متراكبة"),
    PILLAR("عمود"),
    RING("حلقة مكعبات"),
    STAR("نجمة مكعبات"),
    PLATFORM("منصة عائمة"),
    STEPS("درجات"),
    TOWER("برج"),
    CROSS("صليب مكعبات"),
    PYRAMID("هرم"),
    HEXAGON("سداسي مكعبات"),
    CAPSULE("كبسولة"),
    ARCH("قوس صلب"),
    GROUND_BOX("شبكة أرضية")
}

internal enum class LightMode(val label: String) {
    ALL_SIDES("كل الجهات"),
    ONE_SIDE("جهة واحدة"),
    TWO_SIDES("جهتان")
}

internal enum class BackgroundMode(val label: String) {
    TRANSPARENT("شفافة"),
    COLOR("لون"),
    IMAGE("صورة")
}

internal enum class LayerAnimation(val label: String) {
    NONE("ثابت"),
    SPIN("دوران مستمر"),
    WAVE("تموج"),
    BOB("طيران"),
    PULSE("نبض"),
    SHAKE("اهتزاز")
}

internal data class Text3DLayer(
    val id: Long,
    val text: String = "ملصق 3D",
    val fontId: String = "cairo",
    val style: MetalStyle = MetalStyle.GOLD,
    val color: Int? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val scale: Float = 1f,
    val curve: Float = 0f,
    val animation: LayerAnimation = LayerAnimation.NONE,
    val textureBitmap: Bitmap? = null,
    val imageBitmap: Bitmap? = null,
    // --- Advanced text geometry (Phase 2) ---
    val fontSize: Float = 100f,
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val alignment: Int = 1,
    val outlineWidth: Float = 0f,
    val outlineColor: Int = 0xFF000000.toInt(),
    val extrusion: Float = 1f,
    val bevel: Float = 0f,
    val opacity: Float = 1f,
    // --- Advanced material overrides (Phase 2) ---
    val metallicOverride: Float = -1f,
    val roughnessOverride: Float = -1f,
    val reflectanceOverride: Float = -1f
) {
    val isImage: Boolean get() = imageBitmap != null

    fun effectiveMetallic(): Float =
        if (metallicOverride >= 0f) metallicOverride else style.metallic

    fun effectiveRoughness(): Float =
        if (roughnessOverride >= 0f) roughnessOverride else style.roughness

    fun effectiveReflectance(): Float =
        if (reflectanceOverride >= 0f) reflectanceOverride else style.reflectance
}

internal data class StudioSnapshot(
    val layers: List<Text3DLayer>,
    val selectedLayerId: Long?,
    val metalStyle: MetalStyle,
    val shapeStyle: ShapeStyle,
    val autoRotate: Boolean,
    val manualRotation: Float,
    val baseX: Float,
    val baseY: Float,
    val baseZ: Float,
    val baseRotationX: Float,
    val baseRotationY: Float,
    val baseRotationZ: Float,
    val baseScale: Float,
    val moveTogether: Boolean,
    val unifiedColor: Boolean,
    val baseColorOverride: Int?,
    val lightMode: LightMode,
    val lightAngle: Float,
    val lightElevation: Float,
    val backgroundMode: BackgroundMode,
    val backgroundColor: Int,
    val backgroundImage: Bitmap?,
    val watermarkEnabled: Boolean,
    val watermarkText: String,
    val watermarkColor: Int,
    val watermarkOpacity: Float
)

internal data class Text3DFrame(
    val id: Long,
    val name: String,
    val locked: Boolean,
    val visible: Boolean,
    val snapshot: StudioSnapshot
)

internal fun emptyStudioSnapshot(metalStyle: MetalStyle = MetalStyle.GOLD): StudioSnapshot =
    StudioSnapshot(
        layers = listOf(Text3DLayer(id = 1L, text = "ملصق 3D", fontId = "cairo", style = metalStyle)),
        selectedLayerId = 1L,
        metalStyle = metalStyle,
        shapeStyle = ShapeStyle.SLAB,
        autoRotate = true,
        manualRotation = 0f,
        baseX = 0f,
        baseY = 0f,
        baseZ = 0f,
        baseRotationX = 0f,
        baseRotationY = 0f,
        baseRotationZ = 0f,
        baseScale = 1f,
        moveTogether = true,
        unifiedColor = false,
        baseColorOverride = null,
        lightMode = LightMode.ALL_SIDES,
        lightAngle = 45f,
        lightElevation = 35f,
        backgroundMode = BackgroundMode.TRANSPARENT,
        backgroundColor = 0xFF10152E.toInt(),
        backgroundImage = null,
        watermarkEnabled = false,
        watermarkText = "All Stickers",
        watermarkColor = 0xFFFFFFFF.toInt(),
        watermarkOpacity = 0.55f
    )

internal data class Text2DSnapshot(
    val elements: List<StickerTextElement> = emptyList(),
    val selectedId: Long? = null,
    val backgroundColor: Int = 0x00000000,
    val backgroundImage: Bitmap? = null,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "All Stickers",
    val watermarkColor: Int = 0xFFFFFFFF.toInt(),
    val watermarkOpacity: Float = 0.55f,
    val watermarkSize: Float = 40f
)

@Composable
internal fun ColorPaletteRow(
    selectedItem: Int?,
    onPick: (Int) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        STUDIO_COLOR_PALETTE.forEach { color ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selectedItem == color) 2.dp else 1.dp,
                        color = if (selectedItem == color) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onPick(color) }
            )
        }
    }
}

@Composable
internal fun StudioChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color(0xFF6366F1) else Color(0xFF1E2749)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

internal fun Float.format2(): String = String.format(Locale.US, "%.2f", this)

internal fun lightDirectionFor(angleDegrees: Float, elevationDegrees: Float): Direction {
    val angle = angleDegrees * PI.toFloat() / 180f
    val elevation = elevationDegrees * PI.toFloat() / 180f
    val horizontal = cos(elevation)
    return Direction(
        x = horizontal * sin(angle),
        y = -sin(elevation),
        z = -horizontal * cos(angle)
    )
}

internal fun shadeColor(color: Int, factor: Float): Int {
    val alpha = AndroidColor.alpha(color)
    val red = (AndroidColor.red(color) * factor).toInt().coerceIn(0, 255)
    val green = (AndroidColor.green(color) * factor).toInt().coerceIn(0, 255)
    val blue = (AndroidColor.blue(color) * factor).toInt().coerceIn(0, 255)
    return AndroidColor.argb(alpha, red, green, blue)
}

internal fun create3DTextBitmap(
    text: String,
    fontId: String,
    style: MetalStyle,
    colorOverride: Int? = null,
    curve: Float = 0f,
    fontSizePercent: Float = 100f,
    letterSpacing: Float = 0f,
    wordSpacing: Float = 0f,
    lineSpacing: Float = 1f,
    alignment: Int = 1,
    outlineWidth: Float = 0f,
    outlineColor: Int = 0xFF000000.toInt(),
    opacity: Float = 1f,
    bevel: Float = 0f
): Bitmap {
    val width = 1024
    val height = 320
    val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(flat)
    val gradientColors = colorOverride?.let { color ->
        intArrayOf(
            shadeColor(color, 0.55f),
            color,
            shadeColor(color, 1.45f),
            color,
            shadeColor(color, 0.7f)
        )
    } ?: style.colors
    val glowColor = colorOverride?.let { shadeColor(it, 1.5f) } ?: style.glowColor
    val align = when (alignment) {
        0 -> AndroidPaint.Align.LEFT
        2 -> AndroidPaint.Align.RIGHT
        else -> AndroidPaint.Align.CENTER
    }
    val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = FontLibrary.typeface(fontId, Typeface.DEFAULT_BOLD)
        textSize = 190f * (fontSizePercent.coerceIn(20f, 200f) / 100f)
        textAlign = align
        isFakeBoldText = true
        textLocale = if (text.any { it.code in 0x0600..0x06FF }) Locale("ar") else Locale.US
        this.letterSpacing = letterSpacing
        this.wordSpacing = wordSpacing
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            gradientColors,
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        setShadowLayer(22f, 0f, 0f, glowColor)
        this.alpha = alpha
    }
    val lines = text.split("\n").ifEmpty { listOf("") }
    val maxWidth = width * 0.9f
    var widest = 0f
    lines.forEach { line ->
        widest = maxOf(widest, paint.measureText(line.ifEmpty { " " }))
    }
    if (widest > maxWidth && widest > 0f) {
        paint.textSize = (paint.textSize * (maxWidth / widest)).coerceAtLeast(18f)
    }
    val lineHeight = paint.textSize * 1.18f * lineSpacing.coerceIn(0.6f, 2.5f)
    val blockHeight = lineHeight * (lines.size - 1) + (paint.descent() - paint.ascent())
    val firstBaseline = height / 2f - blockHeight / 2f - paint.ascent()
    val x = when (align) {
        AndroidPaint.Align.LEFT -> 42f
        AndroidPaint.Align.RIGHT -> width - 42f
        else -> width / 2f
    }

    // Extruded side/outline pass. Drawn before the fill so the gradient stays on top.
    val effectiveOutline = outlineWidth.coerceIn(0f, 24f) + bevel.coerceIn(0f, 1f) * 10f
    if (effectiveOutline > 0.4f) {
        val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            typeface = paint.typeface
            textSize = paint.textSize
            textAlign = align
            isFakeBoldText = true
            textLocale = paint.textLocale
            this.letterSpacing = paint.letterSpacing
            this.wordSpacing = paint.wordSpacing
            this.style = AndroidPaint.Style.STROKE
            strokeWidth = effectiveOutline
            strokeJoin = AndroidPaint.Join.ROUND
            strokeCap = AndroidPaint.Cap.ROUND
            color = outlineColor
            this.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, firstBaseline + lineHeight * index, strokePaint)
        }
        if (bevel > 0.01f) {
            val highlight = AndroidPaint(strokePaint).apply {
                color = 0xFFFFFFFF.toInt()
                this.alpha = (this.alpha * 0.45f).toInt()
                strokeWidth = effectiveOutline * 0.45f
            }
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    x - effectiveOutline * 0.35f,
                    firstBaseline + lineHeight * index - effectiveOutline * 0.35f,
                    highlight
                )
            }
        }
    }

    lines.forEachIndexed { index, line ->
        canvas.drawText(line, x, firstBaseline + lineHeight * index, paint)
    }
    return if (abs(curve) < 0.02f) flat else bendTextBitmap(flat, curve)
}

private fun bendTextBitmap(source: Bitmap, curve: Float): Bitmap {
    val width = source.width
    val height = source.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val strips = 48
    val stripWidth = width / strips.toFloat()
    val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
    for (i in 0 until strips) {
        val t = (i + 0.5f) / strips
        val phase = (t - 0.5f) * PI.toFloat()
        val shift = curve * sin(phase) * height * 0.35f
        val rotate = (t - 0.5f) * curve * 26f
        canvas.save()
        canvas.translate((i + 0.5f) * stripWidth, height / 2f + shift)
        canvas.rotate(rotate)
        canvas.drawBitmap(
            source,
            Rect((i * stripWidth).toInt(), 0, ((i + 1) * stripWidth).toInt(), height),
            Rect(
                (-stripWidth / 2f).toInt(),
                (-height / 2f).toInt(),
                (stripWidth / 2f).toInt(),
                (height / 2f).toInt()
            ),
            paint
        )
        canvas.restore()
    }
    return output
}

internal fun dressTextWithTexture(textBitmap: Bitmap, texture: Bitmap): Bitmap {
    val width = textBitmap.width
    val height = textBitmap.height
    val dressed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(dressed)
    canvas.drawBitmap(
        texture,
        null,
        Rect(0, 0, width, height),
        AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
    )
    val maskPaint = AndroidPaint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    canvas.drawBitmap(textBitmap, 0f, 0f, maskPaint)
    return dressed
}

internal fun createWatermarkBitmap(
    text: String,
    color: Int,
    opacity: Float
): Bitmap {
    val width = 512
    val height = 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = FontLibrary.typeface("cairo", Typeface.DEFAULT_BOLD)
        textSize = 54f
        textAlign = AndroidPaint.Align.CENTER
        isFakeBoldText = true
        textLocale = Locale("ar")
        this.color = color
        alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        setShadowLayer(8f, 0f, 0f, color)
    }
    val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
    canvas.drawText(text, width / 2f, baseline, paint)
    return bitmap
}

internal fun buildBaseShape(
    shapeStyle: ShapeStyle,
    engine: Engine,
    materialInstance: MaterialInstance,
    attach: (Node) -> Unit
) {
    fun addCube(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float) {
        val cube = CubeNode(
            engine = engine,
            size = io.github.sceneview.math.Size(sx, sy, sz),
            materialInstance = materialInstance
        )
        cube.position = io.github.sceneview.math.Position(x, y, z)
        attach(cube)
    }
    fun addDisc(x: Float, y: Float, z: Float, radius: Float, height: Float = 0.1f) {
        val disc = CylinderNode(
            engine = engine,
            radius = radius,
            height = height,
            materialInstance = materialInstance
        )
        disc.position = io.github.sceneview.math.Position(x, y, z)
        attach(disc)
    }
    when (shapeStyle) {
        ShapeStyle.NONE -> Unit
        ShapeStyle.SLAB -> addCube(0f, -0.95f, -0.2f, 2.4f, 0.18f, 0.9f)
        ShapeStyle.CUBE -> addCube(0f, -0.8f, -0.45f, 1.35f, 1.35f, 1.35f)
        ShapeStyle.SPHERE -> {
            val sphere = SphereNode(
                engine = engine,
                radius = 0.48f,
                materialInstance = materialInstance
            )
            sphere.position = io.github.sceneview.math.Position(y = -0.95f, z = -0.4f)
            attach(sphere)
        }
        ShapeStyle.CYLINDER -> addDisc(0f, -0.95f, -0.2f, 0.72f, 0.18f)
        ShapeStyle.DISC -> addDisc(0f, -1f, -0.2f, 1.05f, 0.1f)
        ShapeStyle.DOUBLE_DISC -> {
            addDisc(0f, -1.02f, -0.2f, 1f, 0.08f)
            addDisc(0f, -0.84f, -0.2f, 0.68f, 0.08f)
        }
        ShapeStyle.STACKED_DISCS -> {
            for (index in 0 until 3) {
                addDisc(0f, -1.04f + index * 0.13f, -0.2f, 1f - index * 0.18f, 0.07f)
            }
        }
        ShapeStyle.PILLAR -> addDisc(0f, -0.55f, -0.75f, 0.28f, 1.8f)
        ShapeStyle.RING -> {
            for (index in 0 until 10) {
                val angle = (index * 36f) * PI.toFloat() / 180f
                addCube(
                    cos(angle) * 1.05f,
                    -0.95f,
                    -0.2f + sin(angle) * 1.05f,
                    0.32f, 0.18f, 0.32f
                )
            }
        }
        ShapeStyle.STAR -> {
            for (index in 0 until 12) {
                val angle = (index * 30f) * PI.toFloat() / 180f
                val radius = if (index % 2 == 0) 1.15f else 0.55f
                addCube(
                    cos(angle) * radius,
                    -0.98f,
                    -0.2f + sin(angle) * radius,
                    0.36f, 0.16f, 0.36f
                )
            }
        }
        ShapeStyle.PLATFORM -> {
            addCube(0f, -1.12f, -0.2f, 2.8f, 0.12f, 1.5f)
            addCube(0f, -1f, -0.2f, 2f, 0.08f, 1.05f)
        }
        ShapeStyle.STEPS -> {
            addCube(0f, -0.72f, -0.35f, 3.6f, 0.22f, 2.2f)
            addCube(0f, -1f, -0.1f, 3f, 0.22f, 1.7f)
            addCube(0f, -1.28f, 0.15f, 2.4f, 0.22f, 1.2f)
        }
        ShapeStyle.TOWER -> {
            addCube(0f, -0.35f, -0.2f, 1.5f, 0.4f, 1.5f)
            addCube(0f, -0.85f, -0.2f, 1.9f, 0.4f, 1.9f)
            addCube(0f, -1.35f, -0.2f, 2.3f, 0.4f, 2.3f)
        }
        ShapeStyle.CROSS -> {
            addCube(0f, -0.7f, -0.2f, 3.4f, 0.4f, 1.6f)
            addCube(0f, -1.15f, -0.2f, 1.6f, 0.5f, 3.2f)
        }
        ShapeStyle.PYRAMID -> {
            addDisc(0f, -0.72f, -0.2f, 1.35f, 0.16f)
            addDisc(0f, -0.95f, -0.2f, 1.05f, 0.16f)
            addDisc(0f, -1.18f, -0.2f, 0.75f, 0.16f)
            addDisc(0f, -1.41f, -0.2f, 0.45f, 0.16f)
        }
        ShapeStyle.HEXAGON -> {
            for (index in 0 until 6) {
                val angle = index * 60f * PI.toFloat() / 180f
                addCube(
                    cos(angle) * 0.85f,
                    -0.95f,
                    -0.2f + sin(angle) * 0.85f,
                    0.85f, 0.5f, 0.85f
                )
            }
        }
        ShapeStyle.CAPSULE -> {
            val body = CylinderNode(
                engine = engine,
                radius = 0.45f,
                height = 1.5f,
                materialInstance = materialInstance
            )
            body.rotation = io.github.sceneview.math.Rotation(z = 90f)
            body.position = io.github.sceneview.math.Position(x = 0.4f, y = -0.95f, z = -0.2f)
            attach(body)
            val left = SphereNode(
                engine = engine,
                radius = 0.45f,
                materialInstance = materialInstance
            )
            left.position = io.github.sceneview.math.Position(x = -0.35f, y = -0.95f, z = -0.2f)
            attach(left)
            val right = SphereNode(
                engine = engine,
                radius = 0.45f,
                materialInstance = materialInstance
            )
            right.position = io.github.sceneview.math.Position(x = 1.15f, y = -0.95f, z = -0.2f)
            attach(right)
        }
        ShapeStyle.ARCH -> {
            for (index in 0 until 9) {
                val angle = PI.toFloat() * (index / 8f)
                addCube(
                    cos(angle) * 1.35f,
                    -0.6f + sin(angle) * 1.15f,
                    -0.2f,
                    0.55f, 0.4f, 1.6f
                )
            }
        }
        ShapeStyle.GROUND_BOX -> {
            for (row in -2..2) {
                for (column in -3..3) {
                    addCube(column * 0.55f, -1.02f, -0.2f + row * 0.55f, 0.48f, 0.12f, 0.48f)
                }
            }
        }
    }
}

internal fun decodeBackgroundBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val decoded = BitmapFactory.decodeStream(input) ?: return null
            val maxDimension = 2048
            if (decoded.width > maxDimension || decoded.height > maxDimension) {
                val ratio = minOf(
                    maxDimension.toFloat() / decoded.width,
                    maxDimension.toFloat() / decoded.height
                )
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun copyModelToCache(context: Context, uri: Uri): File? {
    return try {
        val folder = File(context.cacheDir, "models").apply { mkdirs() }
        val file = File(folder, "scene_${System.currentTimeMillis()}.glb")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        if (file.length() > 1024L) file else {
            file.delete()
            null
        }
    } catch (_: Exception) {
        null
    }
}

internal fun composeExportBitmap(
    sceneBitmap: Bitmap,
    backgroundMode: BackgroundMode,
    backgroundColor: Int,
    backgroundImage: Bitmap?,
    watermark: StickerWatermark?
): Bitmap {
    val size = 512
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    when (backgroundMode) {
        BackgroundMode.TRANSPARENT -> Unit
        BackgroundMode.COLOR -> canvas.drawColor(backgroundColor)
        BackgroundMode.IMAGE -> backgroundImage?.let { drawCenterCrop(canvas, it, size) }
    }
    val side = minOf(sceneBitmap.width, sceneBitmap.height)
    if (side > 0) {
        val left = (sceneBitmap.width - side) / 2
        val top = (sceneBitmap.height - side) / 2
        val source = Rect(left, top, left + side, top + side)
        val destination = Rect(0, 0, size, size)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmap(sceneBitmap, source, destination, paint)
    }
    if (watermark != null) {
        val watermarkBitmap = createWatermarkBitmap(
            text = watermark.text,
            color = watermark.color,
            opacity = watermark.opacity
        )
        val destination = Rect(8, size - 96, size / 2 + 60, size - 8)
        canvas.drawBitmap(
            watermarkBitmap,
            null,
            destination,
            AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
        )
        watermarkBitmap.recycle()
    }
    return output
}

internal fun drawCenterCrop(canvas: AndroidCanvas, bitmap: Bitmap, size: Int) {
    val scale = maxOf(
        size.toFloat() / bitmap.width.coerceAtLeast(1),
        size.toFloat() / bitmap.height.coerceAtLeast(1)
    )
    val destinationWidth = (bitmap.width * scale).toInt()
    val destinationHeight = (bitmap.height * scale).toInt()
    val destination = Rect(
        (size - destinationWidth) / 2,
        (size - destinationHeight) / 2,
        (size + destinationWidth) / 2,
        (size + destinationHeight) / 2
    )
    val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, null, destination, paint)
}

@Composable
internal fun CheckerboardCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = (size.minDimension / 12f).coerceAtLeast(8f)
        val light = Color(0xFFB8BCC6)
        val dark = Color(0xFF8A8E98)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var column = 0
            var x = 0f
            while (x < size.width) {
                drawRect(
                    color = if ((row + column) % 2 == 0) dark else light,
                    topLeft = Offset(x, y),
                    size = GeometrySize(cell, cell)
                )
                x += cell
                column += 1
            }
            y += cell
            row += 1
        }
    }
}

internal fun Text3DLayer.contentLabel(): String =
    if (isImage) "صورة" else "نص: " + text.ifBlank { "فارغ" }.take(12)

internal fun StudioSnapshot.contentSummary(): String {
    val parts = mutableListOf<String>()
    layers.take(2).forEach { parts += it.contentLabel() }
    if (layers.size > 2) parts += "+${layers.size - 2}"
    if (shapeStyle != ShapeStyle.NONE) parts += "شكل: ${shapeStyle.label}"
    if (parts.isEmpty()) return "فارغ"
    return parts.joinToString(" • ")
}

internal fun String.shortenLabel(): String =
    if (length <= 14) this else take(13) + "…"

/**
 * Copies a bundled asset to the app cache so it can be loaded by the 3D model loader.
 * Returns null when the asset is missing.
 */
internal fun copyAssetToCache(context: android.content.Context, assetPath: String): java.io.File? {
    return runCatching {
        val input = context.assets.open(assetPath)
        input.use { src ->
            val folder = java.io.File(context.cacheDir, "stickers").apply { mkdirs() }
            val file = java.io.File(folder, assetPath.substringAfterLast('/'))
            java.io.FileOutputStream(file).use { dst -> src.copyTo(dst) }
            if (file.length() > 0L) file else { file.delete(); null }
        }
    }.getOrNull()
}

@Composable
internal fun StudioSectionBar(
    sections: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Color(0xFF6366F1) else Color(0xFF1A2140))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun ToolChip(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = when {
        !enabled -> Color(0xFF141A2E)
        selected -> Color(0xFF6366F1)
        else -> Color(0xFF1A2140)
    }
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.3f)
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
internal fun StudioToolRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
internal fun PanelLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
