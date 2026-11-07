package com.example.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.BitmapShader
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.SweepGradient
import android.graphics.Shader
import android.media.ExifInterface
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val STICKER_CANVAS_SIZE = 512

enum class StickerEditorMode { IMAGE, TEXT }

enum class StickerFontStyle(val label: String, val typeface: Typeface) {
    SANS("عادي", Typeface.create("sans-serif", Typeface.NORMAL)),
    SANS_BOLD("عريض", Typeface.create("sans-serif", Typeface.BOLD)),
    SERIF("كلاسيكي", Typeface.create("serif", Typeface.NORMAL)),
    MONO("أحادي", Typeface.create("monospace", Typeface.NORMAL)),
    CURSIVE("مزخرف", Typeface.create("cursive", Typeface.NORMAL)),
    CONDENSED("مضغوط", Typeface.create("sans-serif-condensed", Typeface.NORMAL))
}

enum class StickerTextAlign(val label: String) {
    START("بداية"),
    CENTER("وسط"),
    END("نهاية")
}

enum class StickerTextDecoration(val label: String) {
    NONE("بدون"),
    ARABIC_FLOURISH("زخرفة عربية"),
    ENGLISH_FLOURISH("زخرفة إنجليزية"),
    FRAME("إطار مزخرف"),
    HEARTS_ROSES("قلوب وورود"),
    SIDE_LINES("خطوط جانبية"),
    CIRCLE("تصميم دائري"),
    LOVE("ملصق حب"),
    CELEBRATION("تهنئة"),
    SPEECH_BUBBLE("فقاعة كلام")
}

enum class StickerTextEffect(val label: String, val emoji: String) {
    NONE("بدون", "🚫"),
    GRADIENT("تدرج لوني", "🌈"),
    GLOW("توهج", "🌟"),
    SHADOW("ظل", "🌑"),
    OUTLINE("حدود خارجية", "🖍️"),
    METALLIC("معدني", "💎"),
    GOLD("ذهبي", "🏆"),
    FIRE("ناري", "🔥"),
    ICE("جليدي", "❄️"),
    RAINBOW("قوس قزح", "🌈"),
    REFLECTION("انعكاس", "🪞"),
    IMAGE_FILL("ملء بصورة", "🎨"),
    THREE_D("ثلاثي الأبعاد", "💫"),
    DECORATED("زخرفة الحروف", "🌸"),
    GLASS("زجاجي", "🧊"),
    NEON("نيون", "💡"),
    EMBROIDERY("تطريز", "🧵")
}

enum class StickerBlendMode(val label: String) {
    NORMAL("عادي"),
    MULTIPLY("تضاعف"),
    SCREEN("شاشة"),
    ADD("إضافة"),
    OVERLAY("تراكب"),
    DARKEN("تغميق"),
    LIGHTEN("تفتيح")
}

data class StickerAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = 0f
)

data class StickerWatermark(
    val text: String = "",
    val color: Int = Color.WHITE,
    val opacity: Float = 0.85f,
    val size: Float = 32f
)

data class StickerTextElement(
    val id: Long = System.nanoTime(),
    val text: String,
    val isEmoji: Boolean = false,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val fontSize: Float = if (isEmoji) 96f else 56f,
    val color: Int = Color.WHITE,
    val strokeColor: Int = Color.BLACK,
    val strokeWidth: Float = 6f,
    val shadow: Boolean = true,
    val opacity: Float = 1f,
    val fontStyle: StickerFontStyle = StickerFontStyle.SANS_BOLD,
    val fontId: String = "",
    val italic: Boolean = false,
    val textAlign: StickerTextAlign = StickerTextAlign.CENTER,
    val lineSpacing: Float = 1f,
    val surroundEmoji: String = "",
    val decoration: StickerTextDecoration = StickerTextDecoration.NONE,
    val effect: StickerTextEffect = StickerTextEffect.NONE,
    val effectStartColor: Int = 0xFFFFD166.toInt(),
    val effectEndColor: Int = 0xFFFF4D6D.toInt(),
    val effectIntensity: Float = 1f,
    val visible: Boolean = true,
    val blendMode: StickerBlendMode = StickerBlendMode.NORMAL
)

enum class StickerTemplateCategory(val label: String, val emoji: String) {
    LAUGH("الضحك والمزاح", "😂"),
    GREETING("صباح ومساء", "🌹"),
    CELEBRATION("تهاني ومناسبات", "🎉"),
    ENCOURAGEMENT("تشجيع ومدح", "👍"),
    DAILY("عبارات يومية", "😎"),
    THANKS("شكر ودعاء", "🙏"),
    GAMES("ألعاب", "🎮"),
    BUBBLES("فقاعات كلام", "💬"),
    DECORATED("عبارات مزخرفة", "✨"),
    LOVE("حب", "❤️")
}

data class StickerTemplate(
    val label: String,
    val text: String,
    val color: Int = Color.WHITE,
    val strokeColor: Int = Color.BLACK,
    val category: StickerTemplateCategory = StickerTemplateCategory.DAILY,
    val fontStyle: StickerFontStyle = StickerFontStyle.SANS_BOLD,
    val decoration: StickerTextDecoration = StickerTextDecoration.NONE,
    val fontSize: Float = 64f
)

val STICKER_TEMPLATES = listOf(
    StickerTemplate(
        label = "😂 ضحك",
        text = "ههههههه",
        category = StickerTemplateCategory.LAUGH,
        fontStyle = StickerFontStyle.SANS_BOLD,
        fontSize = 76f
    ),
    StickerTemplate(
        label = "🤣 موت ضحك",
        text = "موت من الضحك",
        color = 0xFFFFD166.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.LAUGH,
        decoration = StickerTextDecoration.CELEBRATION,
        fontSize = 54f
    ),
    StickerTemplate(
        label = "😆 ضحكتني",
        text = "ضحكتني",
        color = 0xFF7BDFF2.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.LAUGH,
        decoration = StickerTextDecoration.SIDE_LINES,
        fontSize = 68f
    ),
    StickerTemplate(
        label = "🌹 صباح الخير",
        text = "صباح الخير",
        color = 0xFFFFD166.toInt(),
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.GREETING,
        fontStyle = StickerFontStyle.SERIF,
        decoration = StickerTextDecoration.ARABIC_FLOURISH,
        fontSize = 62f
    ),
    StickerTemplate(
        label = "🌙 مساء الخير",
        text = "مساء الخير",
        color = 0xFFE0E0FF.toInt(),
        strokeColor = 0xFF2B2D42.toInt(),
        category = StickerTemplateCategory.GREETING,
        fontStyle = StickerFontStyle.SERIF,
        decoration = StickerTextDecoration.ARABIC_FLOURISH,
        fontSize = 62f
    ),
    StickerTemplate(
        label = "🌞 يوم سعيد",
        text = "يوم سعيد",
        color = 0xFFFF9F1C.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.GREETING,
        decoration = StickerTextDecoration.HEARTS_ROSES,
        fontSize = 64f
    ),
    StickerTemplate(
        label = "🎉 مبروك",
        text = "مبروك",
        color = 0xFFFFD166.toInt(),
        strokeColor = 0xFF7B2CBF.toInt(),
        category = StickerTemplateCategory.CELEBRATION,
        decoration = StickerTextDecoration.CELEBRATION,
        fontSize = 78f
    ),
    StickerTemplate(
        label = "🎂 عيد ميلاد",
        text = "عيد ميلاد سعيد",
        color = 0xFFFF4D6D.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.CELEBRATION,
        decoration = StickerTextDecoration.FRAME,
        fontSize = 50f
    ),
    StickerTemplate(
        label = "✨ ألف مبروك",
        text = "ألف مبروك",
        color = Color.WHITE,
        strokeColor = 0xFF118AB2.toInt(),
        category = StickerTemplateCategory.CELEBRATION,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.ENGLISH_FLOURISH,
        fontSize = 64f
    ),
    StickerTemplate(
        label = "👍 كفو عليك",
        text = "كفو عليك",
        color = 0xFFFFD166.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.ENCOURAGEMENT,
        decoration = StickerTextDecoration.SIDE_LINES,
        fontSize = 64f
    ),
    StickerTemplate(
        label = "💪 أنت الأفضل",
        text = "أنت الأفضل",
        color = 0xFF06D6A0.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.ENCOURAGEMENT,
        decoration = StickerTextDecoration.HEARTS_ROSES,
        fontSize = 58f
    ),
    StickerTemplate(
        label = "🔥 استمر",
        text = "استمر",
        color = 0xFFFF9F1C.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.ENCOURAGEMENT,
        fontStyle = StickerFontStyle.CONDENSED,
        fontSize = 78f
    ),
    StickerTemplate(
        label = "😎 تمام",
        text = "تمام",
        color = Color.WHITE,
        strokeColor = 0xFF2B2D42.toInt(),
        category = StickerTemplateCategory.DAILY,
        decoration = StickerTextDecoration.SPEECH_BUBBLE,
        fontSize = 72f
    ),
    StickerTemplate(
        label = "👌 أوكي",
        text = "أوكي",
        color = 0xFF06D6A0.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.DAILY,
        fontStyle = StickerFontStyle.CURSIVE,
        fontSize = 76f
    ),
    StickerTemplate(
        label = "🙏 شكراً",
        text = "شكراً",
        color = 0xFFFFD166.toInt(),
        strokeColor = 0xFF7B2CBF.toInt(),
        category = StickerTemplateCategory.THANKS,
        decoration = StickerTextDecoration.ARABIC_FLOURISH,
        fontSize = 72f
    ),
    StickerTemplate(
        label = "🤲 جزاك الله خيراً",
        text = "جزاك الله خيراً",
        color = Color.WHITE,
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.THANKS,
        decoration = StickerTextDecoration.FRAME,
        fontSize = 48f
    ),
    StickerTemplate(
        label = "🌹 بارك الله فيك",
        text = "بارك الله فيك",
        color = 0xFFFF9F1C.toInt(),
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.THANKS,
        decoration = StickerTextDecoration.HEARTS_ROSES,
        fontSize = 48f
    ),
    StickerTemplate(
        label = "🎮 GG",
        text = "GG",
        color = 0xFF7BDFF2.toInt(),
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.GAMES,
        fontStyle = StickerFontStyle.MONO,
        decoration = StickerTextDecoration.CELEBRATION,
        fontSize = 84f
    ),
    StickerTemplate(
        label = "🏆 فوز",
        text = "فوز",
        color = 0xFFFFD166.toInt(),
        strokeColor = Color.BLACK,
        category = StickerTemplateCategory.GAMES,
        decoration = StickerTextDecoration.CIRCLE,
        fontSize = 72f
    ),
    StickerTemplate(
        label = "💬 أهلاً",
        text = "أهلاً",
        color = Color.WHITE,
        strokeColor = 0xFF2B2D42.toInt(),
        category = StickerTemplateCategory.BUBBLES,
        decoration = StickerTextDecoration.SPEECH_BUBBLE,
        fontSize = 68f
    ),
    StickerTemplate(
        label = "💬 ما رأيك؟",
        text = "ما رأيك؟",
        color = Color.WHITE,
        strokeColor = 0xFF118AB2.toInt(),
        category = StickerTemplateCategory.BUBBLES,
        decoration = StickerTextDecoration.SPEECH_BUBBLE,
        fontSize = 54f
    ),
    StickerTemplate(
        label = "✨ الحمد لله",
        text = "الحمد لله",
        color = 0xFFFFD166.toInt(),
        strokeColor = 0xFF7B2CBF.toInt(),
        category = StickerTemplateCategory.DECORATED,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.ARABIC_FLOURISH,
        fontSize = 58f
    ),
    StickerTemplate(
        label = "✨ ما شاء الله",
        text = "ما شاء الله",
        color = Color.WHITE,
        strokeColor = 0xFF118AB2.toInt(),
        category = StickerTemplateCategory.DECORATED,
        fontStyle = StickerFontStyle.SERIF,
        decoration = StickerTextDecoration.FRAME,
        fontSize = 48f
    ),
    StickerTemplate(
        label = "✨ بسم الله",
        text = "بسم الله",
        color = 0xFF06D6A0.toInt(),
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.DECORATED,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.ARABIC_FLOURISH,
        fontSize = 58f
    ),
    StickerTemplate(
        label = "✨ Love",
        text = "Love",
        color = 0xFFFF4D6D.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.DECORATED,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.ENGLISH_FLOURISH,
        fontSize = 74f
    ),
    StickerTemplate(
        label = "✨ Good Vibes",
        text = "Good Vibes",
        color = 0xFF7BDFF2.toInt(),
        strokeColor = 0xFF12355B.toInt(),
        category = StickerTemplateCategory.DECORATED,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.ENGLISH_FLOURISH,
        fontSize = 48f
    ),
    StickerTemplate(
        label = "❤️ أحبك",
        text = "أحبك",
        color = 0xFFFF4D6D.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.LOVE,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.LOVE,
        fontSize = 76f
    ),
    StickerTemplate(
        label = "❤️ قلبي لك",
        text = "قلبي لك",
        color = 0xFFFF4D6D.toInt(),
        strokeColor = 0xFF7B2CBF.toInt(),
        category = StickerTemplateCategory.LOVE,
        decoration = StickerTextDecoration.HEARTS_ROSES,
        fontSize = 58f
    ),
    StickerTemplate(
        label = "❤️ Forever",
        text = "Forever",
        color = 0xFFFF4D6D.toInt(),
        strokeColor = Color.WHITE,
        category = StickerTemplateCategory.LOVE,
        fontStyle = StickerFontStyle.CURSIVE,
        decoration = StickerTextDecoration.CIRCLE,
        fontSize = 54f
    )
)

enum class StickerFilter(val label: String, val adjustments: StickerAdjustments) {
    NONE("بدون", StickerAdjustments()),
    WARM("دافئ", StickerAdjustments(brightness = 5f, saturation = 1.12f, warmth = 0.35f)),
    COOL("بارد", StickerAdjustments(brightness = 3f, saturation = 1.05f, warmth = -0.35f)),
    MONO("أبيض وأسود", StickerAdjustments(contrast = 0.15f, saturation = 0f)),
    VIVID("حيوي", StickerAdjustments(contrast = 0.12f, saturation = 1.45f)),
    FADED("باهت", StickerAdjustments(brightness = 7f, contrast = -0.18f, saturation = 0.8f)),
    DRAMA("درامي", StickerAdjustments(contrast = 0.3f, saturation = 1.1f, vignette = 0.35f))
}

/**
 * Pure bitmap toolkit for creating stickers locally without any external service.
 * Everything happens on the device and preserves transparent backgrounds.
 */
object StickerCreatorEngine {

    fun loadBitmap(context: Context, uri: Uri, maxSize: Int = 1400): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            applyOrientation(decoded, orientation)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Crops and zooms the source image inside a transparent square canvas.
     * [offsetX] and [offsetY] are normalized (-1..1) relative to the canvas size.
     */
    fun cropToCanvas(
        source: Bitmap,
        zoom: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        size: Int = STICKER_CANVAS_SIZE
    ): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val safeZoom = zoom.coerceIn(1f, 6f)
        val baseScale = max(size / source.width.toFloat(), size / source.height.toFloat())
        val scale = baseScale * safeZoom

        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val maxOffsetX = ((scaledWidth - size) / 2f / size).coerceAtLeast(0f)
        val maxOffsetY = ((scaledHeight - size) / 2f / size).coerceAtLeast(0f)
        val safeOffsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        val safeOffsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

        val dx = size / 2f + safeOffsetX * size
        val dy = size / 2f + safeOffsetY * size
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx - scaledWidth / 2f, dy - scaledHeight / 2f)
        }
        canvas.drawBitmap(
            source,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    fun fitToCanvas(source: Bitmap, size: Int = STICKER_CANVAS_SIZE): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = min(size / source.width.toFloat(), size / source.height.toFloat())
        val width = source.width * scale
        val height = source.height * scale
        val left = (size - width) / 2f
        val top = (size - height) / 2f
        canvas.drawBitmap(
            source,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }

    /**
     * Removes a solid/similar background sampled from the border of the image.
     * Returns the number of removed pixels.
     */
    fun removeSolidBackground(bitmap: Bitmap, tolerance: Int = 38): Int {
        if (!bitmap.isMutable) return 0
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val samples = intArrayOf(
            pixels[0],
            pixels[width / 2],
            pixels[width - 1],
            pixels[(height / 2) * width],
            pixels[(height - 1) * width],
            pixels[(height - 1) * width + width / 2],
            pixels[height * width - 1]
        ).filter { Color.alpha(it) > 8 }
        if (samples.isEmpty()) return 0

        val maxDistance = (tolerance.coerceIn(0, 100) * 2.55f)
        val maxDistanceSquared = maxDistance * maxDistance * 3f
        var removed = 0

        for (index in pixels.indices) {
            val pixel = pixels[index]
            if (Color.alpha(pixel) < 8) continue
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            var shouldRemove = false
            for (sample in samples) {
                val dr = (red - Color.red(sample)).toFloat()
                val dg = (green - Color.green(sample)).toFloat()
                val db = (blue - Color.blue(sample)).toFloat()
                if (dr * dr + dg * dg + db * db <= maxDistanceSquared) {
                    shouldRemove = true
                    break
                }
            }
            if (shouldRemove) {
                pixels[index] = 0
                removed++
            }
        }

        if (removed > 0) {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return removed
    }

    fun erase(bitmap: Bitmap, x: Float, y: Float, radius: Float) {
        if (!bitmap.isMutable) return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawCircle(x, y, radius.coerceAtLeast(1f), paint)
    }

    fun rotate90(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return try {
            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotated.isMutable) {
                rotated
            } else {
                rotated.copy(Bitmap.Config.ARGB_8888, true)
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return try {
            val flipped = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (flipped.isMutable) flipped else flipped.copy(Bitmap.Config.ARGB_8888, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun drawBrushStroke(
        bitmap: Bitmap,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        color: Int,
        radius: Float,
        alpha: Float
    ) {
        if (!bitmap.isMutable) return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
            style = Paint.Style.STROKE
            strokeWidth = radius.coerceAtLeast(1f) * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(endX, endY, radius.coerceAtLeast(1f), paint)
    }

    fun render(
        baseBitmap: Bitmap?,
        elements: List<StickerTextElement>,
        size: Int = STICKER_CANVAS_SIZE,
        backgroundColor: Int = Color.TRANSPARENT,
        backgroundBitmap: Bitmap? = null,
        watermark: StickerWatermark = StickerWatermark()
    ): Bitmap = render(
        baseBitmap,
        null,
        elements,
        StickerAdjustments(),
        size,
        backgroundColor,
        backgroundBitmap,
        watermark
    )

    fun render(
        baseBitmap: Bitmap?,
        drawLayer: Bitmap?,
        elements: List<StickerTextElement>,
        adjustments: StickerAdjustments,
        size: Int = STICKER_CANVAS_SIZE,
        backgroundColor: Int = Color.TRANSPARENT,
        backgroundBitmap: Bitmap? = null,
        watermark: StickerWatermark = StickerWatermark()
    ): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawComposite(
            canvas,
            baseBitmap,
            drawLayer,
            elements,
            adjustments,
            size,
            backgroundColor,
            backgroundBitmap,
            watermark
        )
        return output
    }

    fun drawComposite(
        canvas: Canvas,
        baseBitmap: Bitmap?,
        drawLayer: Bitmap?,
        elements: List<StickerTextElement>,
        adjustments: StickerAdjustments,
        size: Int = STICKER_CANVAS_SIZE,
        backgroundColor: Int = Color.TRANSPARENT,
        backgroundBitmap: Bitmap? = null,
        watermark: StickerWatermark = StickerWatermark()
    ) {
        if (Color.alpha(backgroundColor) > 0) {
            canvas.drawColor(backgroundColor)
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        applyAdjustments(bitmapPaint, adjustments)
        if (backgroundBitmap != null) {
            drawCenterCropped(canvas, backgroundBitmap, size, bitmapPaint)
        }
        if (baseBitmap != null) {
            canvas.drawBitmap(baseBitmap, null, Rect(0, 0, size, size), bitmapPaint)
        }
        if (drawLayer != null) {
            canvas.drawBitmap(
                drawLayer,
                null,
                Rect(0, 0, size, size),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
        if (baseBitmap != null || backgroundBitmap != null) {
            drawVignette(canvas, adjustments, size)
        }
        elements.forEach { element ->
            drawTextElement(canvas, element, size, baseBitmap)
        }
        drawWatermark(canvas, watermark, size)
    }

    private fun drawCenterCropped(
        canvas: Canvas,
        bitmap: Bitmap,
        size: Int,
        paint: Paint
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = max(size / bitmap.width.toFloat(), size / bitmap.height.toFloat())
        val sourceWidth = (size / scale).coerceAtMost(bitmap.width.toFloat())
        val sourceHeight = (size / scale).coerceAtMost(bitmap.height.toFloat())
        val sourceLeft = (bitmap.width - sourceWidth) / 2f
        val sourceTop = (bitmap.height - sourceHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            Rect(
                sourceLeft.toInt().coerceIn(0, bitmap.width - 1),
                sourceTop.toInt().coerceIn(0, bitmap.height - 1),
                (sourceLeft + sourceWidth).toInt().coerceIn(1, bitmap.width),
                (sourceTop + sourceHeight).toInt().coerceIn(1, bitmap.height)
            ),
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            paint
        )
    }

    private fun drawWatermark(
        canvas: Canvas,
        watermark: StickerWatermark,
        size: Int
    ) {
        val text = watermark.text.trim()
        if (text.isBlank() || watermark.opacity <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = watermark.color
            alpha = (watermark.opacity.coerceIn(0f, 1f) * 255f).toInt()
            textSize = watermark.size.coerceIn(8f, 200f)
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            if (text.any { it in '\u0600'..'\u06FF' }) {
                textLocale = Locale("ar")
            }
            setShadowLayer(
                textSize * 0.18f,
                0f,
                textSize * 0.06f,
                Color.argb(140, 0, 0, 0)
            )
        }
        val margin = size * 0.045f
        canvas.drawText(text, size - margin, size - margin, paint)
    }

    private fun applyAdjustments(paint: Paint, adjustments: StickerAdjustments) {
        val matrix = ColorMatrix()
        val contrast = adjustments.contrast.coerceIn(-1f, 1f) + 1f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, 128f * (1f - contrast),
                    0f, contrast, 0f, 0f, 128f * (1f - contrast),
                    0f, 0f, contrast, 0f, 128f * (1f - contrast),
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val saturation = adjustments.saturation.coerceIn(0f, 3f)
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    lumR * (1f - saturation) + saturation, lumG * (1f - saturation), lumB * (1f - saturation), 0f, 0f,
                    lumR * (1f - saturation), lumG * (1f - saturation) + saturation, lumB * (1f - saturation), 0f, 0f,
                    lumR * (1f - saturation), lumG * (1f - saturation), lumB * (1f - saturation) + saturation, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val brightness = adjustments.brightness.coerceIn(-100f, 100f) * 2.55f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightness,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val hue = Math.toRadians(adjustments.hue.coerceIn(-180f, 180f).toDouble())
        val cosValue = cos(hue).toFloat()
        val sinValue = sin(hue).toFloat()
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.213f + cosValue * 0.787f - sinValue * 0.213f, 0.715f - cosValue * 0.715f - sinValue * 0.715f, 0.072f - cosValue * 0.072f + sinValue * 0.928f, 0f, 0f,
                    0.213f - cosValue * 0.213f + sinValue * 0.143f, 0.715f + cosValue * 0.285f + sinValue * 0.140f, 0.072f - cosValue * 0.072f - sinValue * 0.283f, 0f, 0f,
                    0.213f - cosValue * 0.213f - sinValue * 0.787f, 0.715f - cosValue * 0.715f + sinValue * 0.715f, 0.072f + cosValue * 0.928f + sinValue * 0.072f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val warmth = adjustments.warmth.coerceIn(-1f, 1f)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f + warmth * 0.15f, 0f, 0f, 0f, warmth * 12f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - warmth * 0.15f, 0f, -warmth * 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(matrix)
    }

    private fun drawVignette(canvas: Canvas, adjustments: StickerAdjustments, size: Int) {
        val strength = adjustments.vignette.coerceIn(0f, 1f)
        if (strength <= 0f) return
        val shader = RadialGradient(
            size / 2f,
            size / 2f,
            size * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.argb((strength * 200f).toInt(), 0, 0, 0)),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    }

    fun textBounds(element: StickerTextElement, size: Int = STICKER_CANVAS_SIZE): RectF? {
        if (!element.visible) return null
        val text = element.text.trimEnd()
        if (text.isBlank()) return null
        val lines = text.split("\n")
        val baseTypeface = FontLibrary.typeface(element.fontId, element.fontStyle.typeface)
        val typeface = if (element.italic) {
            Typeface.create(baseTypeface, Typeface.ITALIC)
        } else {
            baseTypeface
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = element.fontSize
            this.typeface = typeface
        }
        val lineHeight = paint.fontSpacing * element.lineSpacing.coerceIn(0.6f, 3f)
        val blockHeight = lineHeight * lines.size
        val textWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val padding = element.fontSize * 0.32f
        val sidePadding = if (element.surroundEmoji.isNotBlank()) {
            element.fontSize * 0.95f
        } else {
            0f
        }
        return RectF(
            -textWidth / 2f - padding - sidePadding,
            -blockHeight / 2f - padding,
            textWidth / 2f + padding + sidePadding,
            blockHeight / 2f + padding
        )
    }

    fun hitTestText(
        elements: List<StickerTextElement>,
        normalizedX: Float,
        normalizedY: Float,
        size: Int = STICKER_CANVAS_SIZE
    ): Long? {
        val pointX = normalizedX * size
        val pointY = normalizedY * size
        return elements.asReversed().firstOrNull { element ->
            val bounds = textBounds(element, size) ?: return@firstOrNull false
            val scale = element.scale.takeIf { it > 0.01f } ?: 1f
            val dx = pointX - element.x * size
            val dy = pointY - element.y * size
            val radians = Math.toRadians(-element.rotation.toDouble())
            val cosValue = cos(radians).toFloat()
            val sinValue = sin(radians).toFloat()
            val localX = (dx * cosValue + dy * sinValue) / scale
            val localY = (-dx * sinValue + dy * cosValue) / scale
            bounds.contains(localX, localY)
        }?.id
    }

    private fun drawTextElement(
        canvas: Canvas,
        element: StickerTextElement,
        size: Int,
        baseBitmap: Bitmap? = null
    ) {
        if (!element.visible) return
        val text = element.text.trimEnd()
        if (text.isBlank()) return

        val lines = text.split("\n")
        val baseTypeface = FontLibrary.typeface(element.fontId, element.fontStyle.typeface)
        val typeface = if (element.italic) {
            Typeface.create(baseTypeface, Typeface.ITALIC)
        } else {
            baseTypeface
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = element.color
            alpha = (element.opacity.coerceIn(0f, 1f) * 255f).toInt()
            textSize = element.fontSize
            this.typeface = typeface
            textAlign = when (element.textAlign) {
                StickerTextAlign.START -> Paint.Align.LEFT
                StickerTextAlign.CENTER -> Paint.Align.CENTER
                StickerTextAlign.END -> Paint.Align.RIGHT
            }
            if (text.any { it in '\u0600'..'\u06FF' }) {
                textLocale = Locale("ar")
            }
            if (element.blendMode != StickerBlendMode.NORMAL) {
                xfermode = PorterDuffXfermode(element.blendMode.toPorterDuff())
            }
        }
        val opacity = (element.opacity.coerceIn(0f, 1f) * 255f).toInt()
        val effect = element.effect
        val intensity = element.effectIntensity.coerceIn(0.2f, 2f)
        val outlineWidth = when (effect) {
            StickerTextEffect.OUTLINE -> max(element.strokeWidth, element.fontSize * 0.09f)
            StickerTextEffect.GLASS -> max(element.strokeWidth, element.fontSize * 0.035f)
            StickerTextEffect.EMBROIDERY -> max(element.strokeWidth, element.fontSize * 0.045f)
            else -> element.strokeWidth
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = outlineWidth
            color = when (effect) {
                StickerTextEffect.OUTLINE -> element.effectStartColor
                StickerTextEffect.GLASS -> Color.argb((opacity * 0.75f).toInt(), 255, 255, 255)
                StickerTextEffect.EMBROIDERY -> element.effectStartColor
                StickerTextEffect.NEON -> element.effectStartColor
                else -> element.strokeColor
            }
            alpha = opacity
            xfermode = null
            shader = null
            if (effect == StickerTextEffect.EMBROIDERY) {
                pathEffect = DashPathEffect(
                    floatArrayOf(
                        max(3f, element.fontSize * 0.11f),
                        max(2f, element.fontSize * 0.075f)
                    ),
                    0f
                )
            }
        }
        val shadowPaint = Paint(fillPaint).apply {
            alpha = opacity
            xfermode = null
            shader = null
            if (element.shadow || effect == StickerTextEffect.SHADOW) {
                val shadowScale = if (effect == StickerTextEffect.SHADOW) 0.20f else 0.12f
                setShadowLayer(
                    element.fontSize * shadowScale * intensity,
                    if (effect == StickerTextEffect.SHADOW) element.fontSize * 0.05f else 0f,
                    if (effect == StickerTextEffect.SHADOW) element.fontSize * 0.12f else element.fontSize * 0.06f,
                    Color.argb(190, 0, 0, 0)
                )
            }
        }

        val lineHeight = fillPaint.fontSpacing * element.lineSpacing.coerceIn(0.6f, 3f)
        val blockHeight = lineHeight * lines.size
        val firstBaseline = -blockHeight / 2f - fillPaint.ascent()
        val textWidth = lines.maxOfOrNull { fillPaint.measureText(it) } ?: 0f
        val padding = element.fontSize * 0.55f
        val top = -blockHeight / 2f
        val bottom = blockHeight / 2f
        val lineX = when (element.textAlign) {
            StickerTextAlign.START -> -textWidth / 2f
            StickerTextAlign.CENTER -> 0f
            StickerTextAlign.END -> textWidth / 2f
        }

        canvas.save()
        canvas.translate(element.x * size, element.y * size)
        canvas.rotate(element.rotation)
        canvas.scale(element.scale, element.scale)

        val decorationElement = if (
            effect == StickerTextEffect.DECORATED &&
            element.decoration == StickerTextDecoration.NONE
        ) {
            element.copy(decoration = StickerTextDecoration.ARABIC_FLOURISH)
        } else {
            element
        }
        drawTextDecoration(
            canvas = canvas,
            element = decorationElement,
            textWidth = textWidth,
            top = top,
            bottom = bottom,
            padding = padding,
            opacity = opacity
        )

        if (effect == StickerTextEffect.REFLECTION) {
            drawReflection(
                canvas,
                lines,
                lineX,
                firstBaseline,
                lineHeight,
                blockHeight,
                fillPaint,
                element,
                opacity
            )
        }

        val fillEffectPaint = createEffectFillPaint(
            fillPaint = fillPaint,
            element = element,
            textWidth = textWidth,
            top = top,
            bottom = bottom,
            baseBitmap = baseBitmap
        )

        when (effect) {
            StickerTextEffect.GLOW -> drawGlow(
                canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                element.effectStartColor, intensity * 1.2f
            )
            StickerTextEffect.NEON -> {
                drawGlow(
                    canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                    element.effectEndColor, intensity * 1.8f
                )
                drawGlow(
                    canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                    element.effectStartColor, intensity * 0.9f
                )
            }
            StickerTextEffect.FIRE -> {
                drawGlow(
                    canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                    0xFFFF3D00.toInt(), intensity * 1.9f
                )
                drawGlow(
                    canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                    0xFFFFB300.toInt(), intensity * 1.1f
                )
            }
            StickerTextEffect.ICE -> drawGlow(
                canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                0xFF7DD3FC.toInt(), intensity * 1.6f
            )
            StickerTextEffect.METALLIC -> drawGlow(
                canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                0xFFFFE8A3.toInt(), intensity * 0.7f
            )
            StickerTextEffect.GOLD -> drawGlow(
                canvas, lines, lineX, firstBaseline, lineHeight, fillPaint, element,
                0xFFFFD166.toInt(), intensity * 0.9f
            )
            else -> Unit
        }

        if (effect == StickerTextEffect.THREE_D) {
            val depth = element.fontSize * 0.09f * intensity
            val depthPaint = Paint(fillEffectPaint).apply {
                shader = null
                xfermode = null
                color = darkenColor(element.effectEndColor, 0.45f)
            }
            for (layer in 5 downTo 1) {
                val offset = depth * layer / 5f
                lines.forEachIndexed { index, line ->
                    canvas.drawText(
                        line,
                        lineX + offset,
                        firstBaseline + index * lineHeight + offset,
                        depthPaint
                    )
                }
            }
        }

        lines.forEachIndexed { index, line ->
            val baseline = firstBaseline + index * lineHeight
            if (element.shadow || effect == StickerTextEffect.SHADOW) {
                canvas.drawText(line, lineX, baseline, shadowPaint)
            }
            if (outlineWidth > 0f && (element.strokeColor != Color.TRANSPARENT || effect != StickerTextEffect.NONE)) {
                canvas.drawText(line, lineX, baseline, strokePaint)
            }
            canvas.drawText(line, lineX, baseline, fillEffectPaint)
        }

        if (element.surroundEmoji.isNotBlank()) {
            val emojiPaint = Paint(fillPaint).apply {
                shader = null
                xfermode = null
                alpha = opacity
                textSize = element.fontSize * 0.82f
                textAlign = Paint.Align.CENTER
                color = element.color
                setShadowLayer(
                    element.fontSize * 0.12f,
                    0f,
                    0f,
                    Color.argb(170, 0, 0, 0)
                )
            }
            val emojiBaseline = firstBaseline + (lines.size - 1) * lineHeight / 2f
            canvas.drawText(
                element.surroundEmoji,
                -textWidth / 2f - padding * 0.72f,
                emojiBaseline,
                emojiPaint
            )
            canvas.drawText(
                element.surroundEmoji,
                textWidth / 2f + padding * 0.72f,
                emojiBaseline,
                emojiPaint
            )
        }
        canvas.restore()
    }

    private fun drawGlow(
        canvas: Canvas,
        lines: List<String>,
        lineX: Float,
        firstBaseline: Float,
        lineHeight: Float,
        basePaint: Paint,
        element: StickerTextElement,
        glowColor: Int,
        intensity: Float
    ) {
        val glowPaint = Paint(basePaint).apply {
            shader = null
            xfermode = null
            color = glowColor
            alpha = (element.opacity.coerceIn(0f, 1f) * 150f).toInt()
            setShadowLayer(
                element.fontSize * 0.34f * intensity.coerceIn(0.2f, 2.2f),
                0f,
                0f,
                glowColor
            )
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, lineX, firstBaseline + index * lineHeight, glowPaint)
        }
    }

    private fun drawReflection(
        canvas: Canvas,
        lines: List<String>,
        lineX: Float,
        firstBaseline: Float,
        lineHeight: Float,
        blockHeight: Float,
        basePaint: Paint,
        element: StickerTextElement,
        opacity: Int
    ) {
        val reflectionPaint = Paint(basePaint).apply {
            shader = null
            xfermode = null
            color = element.color
            alpha = (opacity * 0.26f).toInt()
        }
        canvas.save()
        canvas.scale(1f, -1f, 0f, blockHeight / 2f + element.fontSize * 0.18f)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, lineX, firstBaseline + index * lineHeight, reflectionPaint)
        }
        canvas.restore()
    }

    private fun createEffectFillPaint(
        fillPaint: Paint,
        element: StickerTextElement,
        textWidth: Float,
        top: Float,
        bottom: Float,
        baseBitmap: Bitmap?
    ): Paint {
        val paint = Paint(fillPaint)
        val width = max(textWidth, 1f)
        val height = max(bottom - top, 1f)
        val start = element.effectStartColor
        val end = element.effectEndColor
        val intensity = element.effectIntensity.coerceIn(0.2f, 2f)
        paint.xfermode = fillPaint.xfermode
        when (element.effect) {
            StickerTextEffect.GRADIENT,
            StickerTextEffect.THREE_D,
            StickerTextEffect.DECORATED,
            StickerTextEffect.EMBROIDERY -> {
                paint.shader = LinearGradient(
                    -width / 2f, 0f, width / 2f, 0f,
                    intArrayOf(start, end), null, Shader.TileMode.CLAMP
                )
            }
            StickerTextEffect.RAINBOW -> {
                paint.shader = SweepGradient(
                    0f, 0f,
                    intArrayOf(
                        0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(),
                        0xFF34C759.toInt(), 0xFF32ADE6.toInt(), 0xFF5856D6.toInt(),
                        0xFFAF52DE.toInt(), 0xFFFF3B30.toInt()
                    ),
                    null
                )
            }
            StickerTextEffect.METALLIC -> {
                paint.shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                        darkenColor(start, 0.55f),
                        start,
                        0xFFFFF8DC.toInt(),
                        end,
                        darkenColor(end, 0.45f)
                    ),
                    floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            StickerTextEffect.GOLD -> {
                paint.shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                        0xFF5C3A00.toInt(),
                        0xFFB8860B.toInt(),
                        0xFFFFE082.toInt(),
                        0xFFFFF8DC.toInt(),
                        0xFFFFC107.toInt(),
                        0xFF8A5A00.toInt()
                    ),
                    floatArrayOf(0f, 0.18f, 0.38f, 0.55f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            StickerTextEffect.FIRE -> {
                paint.shader = LinearGradient(
                    0f, bottom, 0f, top,
                    intArrayOf(
                        0xFFB71C1C.toInt(),
                        0xFFFF3D00.toInt(),
                        0xFFFF9800.toInt(),
                        0xFFFFEB3B.toInt(),
                        0xFFFFFDE7.toInt()
                    ),
                    floatArrayOf(0f, 0.28f, 0.55f, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            StickerTextEffect.ICE -> {
                paint.shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                        0xFFFFFFFF.toInt(),
                        0xFFB3E5FC.toInt(),
                        0xFF4FC3F7.toInt(),
                        0xFF0277BD.toInt()
                    ),
                    floatArrayOf(0f, 0.36f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            StickerTextEffect.IMAGE_FILL -> {
                val source = baseBitmap ?: textureBitmap()
                val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix().apply {
                    setScale(width / source.width, height / source.height)
                    postTranslate(-width / 2f, top)
                }
                shader.setLocalMatrix(matrix)
                paint.shader = shader
            }
            StickerTextEffect.GLASS -> {
                paint.color = Color.argb(
                    (55f + 50f * intensity).toInt().coerceIn(35, 120),
                    Color.red(start),
                    Color.green(start),
                    Color.blue(start)
                )
            }
            StickerTextEffect.NEON -> {
                paint.color = start
            }
            StickerTextEffect.GLOW -> {
                paint.color = start
            }
            StickerTextEffect.OUTLINE,
            StickerTextEffect.SHADOW,
            StickerTextEffect.REFLECTION,
            StickerTextEffect.NONE -> {
                paint.color = element.color
            }
        }
        return paint
    }

    private fun textureBitmap(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(
                    0xFF4C1D95.toInt(),
                    0xFF0E7490.toInt(),
                    0xFFBE185D.toInt(),
                    0xFFFFD166.toInt(),
                    0xFF4C1D95.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size * 0.25f, size * 0.3f, size * 0.18f, glow)
        canvas.drawCircle(size * 0.72f, size * 0.68f, size * 0.24f, glow)
        return bitmap
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val safe = factor.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * safe).toInt().coerceIn(0, 255),
            (Color.green(color) * safe).toInt().coerceIn(0, 255),
            (Color.blue(color) * safe).toInt().coerceIn(0, 255)
        )
    }

    private fun drawTextDecoration(
        canvas: Canvas,
        element: StickerTextElement,
        textWidth: Float,
        top: Float,
        bottom: Float,
        padding: Float,
        opacity: Int
    ) {
        if (element.decoration == StickerTextDecoration.NONE) return
        val left = -textWidth / 2f
        val right = textWidth / 2f
        val strokeWidth = max(2f, element.fontSize * 0.035f)
        val accentColor = if (element.strokeColor != Color.TRANSPARENT) {
            element.strokeColor
        } else {
            element.color
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = accentColor
            alpha = (opacity * 0.9f).toInt()
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
            alpha = (opacity * 0.9f).toInt()
        }
        val softFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
            alpha = (opacity * 0.18f).toInt()
        }
        val ornamentSize = max(element.fontSize * 0.16f, 8f)

        when (element.decoration) {
            StickerTextDecoration.NONE -> Unit
            StickerTextDecoration.ARABIC_FLOURISH -> {
                val flourish = Path()
                flourish.moveTo(left - padding * 1.35f, 0f)
                flourish.cubicTo(
                    left - padding * 1.05f, -padding * 0.9f,
                    left - padding * 0.55f, padding * 0.9f,
                    left - padding * 0.18f, 0f
                )
                flourish.moveTo(right + padding * 1.35f, 0f)
                flourish.cubicTo(
                    right + padding * 1.05f, -padding * 0.9f,
                    right + padding * 0.55f, padding * 0.9f,
                    right + padding * 0.18f, 0f
                )
                canvas.drawPath(flourish, linePaint)
                canvas.drawCircle(left - padding * 1.45f, 0f, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(right + padding * 1.45f, 0f, ornamentSize * 0.5f, fillPaint)
                val diamondY = top - padding * 0.55f
                canvas.save()
                canvas.rotate(45f, 0f, diamondY)
                canvas.drawRect(
                    -ornamentSize / 2f, diamondY - ornamentSize / 2f,
                    ornamentSize / 2f, diamondY + ornamentSize / 2f,
                    fillPaint
                )
                canvas.restore()
                canvas.drawCircle(0f, bottom + padding * 0.55f, ornamentSize * 0.55f, fillPaint)
            }
            StickerTextDecoration.ENGLISH_FLOURISH -> {
                val flourish = Path()
                flourish.moveTo(left - padding * 0.4f, top - padding * 0.15f)
                flourish.cubicTo(
                    left - padding * 1.2f, top - padding * 1.0f,
                    left + padding * 0.2f, top - padding * 1.15f,
                    left - padding * 0.1f, top - padding * 0.45f
                )
                flourish.moveTo(right + padding * 0.4f, bottom + padding * 0.15f)
                flourish.cubicTo(
                    right + padding * 1.2f, bottom + padding * 1.0f,
                    right - padding * 0.2f, bottom + padding * 1.15f,
                    right + padding * 0.1f, bottom + padding * 0.45f
                )
                canvas.drawPath(flourish, linePaint)
                canvas.drawLine(left - padding * 1.4f, bottom, left - padding * 0.45f, bottom, linePaint)
                canvas.drawLine(right + padding * 0.45f, top, right + padding * 1.4f, top, linePaint)
                canvas.drawCircle(left - padding * 1.5f, bottom, ornamentSize * 0.45f, fillPaint)
                canvas.drawCircle(right + padding * 1.5f, top, ornamentSize * 0.45f, fillPaint)
            }
            StickerTextDecoration.FRAME -> {
                val frame = RectF(
                    left - padding, top - padding * 0.75f,
                    right + padding, bottom + padding * 0.75f
                )
                val radius = padding * 0.55f
                canvas.drawRoundRect(frame, radius, radius, linePaint)
                canvas.drawRoundRect(
                    RectF(
                        frame.left + strokeWidth * 2.2f,
                        frame.top + strokeWidth * 2.2f,
                        frame.right - strokeWidth * 2.2f,
                        frame.bottom - strokeWidth * 2.2f
                    ),
                    radius, radius, linePaint
                )
                canvas.drawCircle(frame.left, frame.top, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(frame.right, frame.top, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(frame.left, frame.bottom, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(frame.right, frame.bottom, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(0f, frame.top, ornamentSize * 0.4f, fillPaint)
                canvas.drawCircle(0f, frame.bottom, ornamentSize * 0.4f, fillPaint)
            }
            StickerTextDecoration.HEARTS_ROSES -> {
                drawHeart(canvas, left - padding * 1.15f, 0f, ornamentSize * 1.4f, fillPaint)
                drawHeart(canvas, right + padding * 1.15f, 0f, ornamentSize * 1.4f, fillPaint)
                drawRose(canvas, left - padding * 0.6f, top - padding * 0.8f, ornamentSize * 0.75f, linePaint)
                drawRose(canvas, right + padding * 0.6f, bottom + padding * 0.8f, ornamentSize * 0.75f, linePaint)
                canvas.drawCircle(left - padding * 1.75f, bottom + padding * 0.25f, ornamentSize * 0.28f, fillPaint)
                canvas.drawCircle(right + padding * 1.75f, top - padding * 0.25f, ornamentSize * 0.28f, fillPaint)
            }
            StickerTextDecoration.SIDE_LINES -> {
                val y = 0f
                canvas.drawLine(left - padding * 1.6f, y, left - padding * 0.25f, y, linePaint)
                canvas.drawLine(right + padding * 0.25f, y, right + padding * 1.6f, y, linePaint)
                canvas.drawCircle(left - padding * 1.75f, y, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(right + padding * 1.75f, y, ornamentSize * 0.5f, fillPaint)
                canvas.drawCircle(0f, top - padding * 0.55f, ornamentSize * 0.4f, fillPaint)
                canvas.drawCircle(0f, bottom + padding * 0.55f, ornamentSize * 0.4f, fillPaint)
            }
            StickerTextDecoration.CIRCLE -> {
                val oval = RectF(
                    left - padding * 1.05f, top - padding * 1.05f,
                    right + padding * 1.05f, bottom + padding * 1.05f
                )
                canvas.drawOval(oval, softFillPaint)
                canvas.drawOval(oval, linePaint)
                canvas.drawArc(
                    RectF(
                        oval.left + strokeWidth * 3f, oval.top + strokeWidth * 3f,
                        oval.right - strokeWidth * 3f, oval.bottom - strokeWidth * 3f
                    ),
                    200f, 140f, false, linePaint
                )
                canvas.drawCircle(0f, oval.top, ornamentSize * 0.45f, fillPaint)
                canvas.drawCircle(0f, oval.bottom, ornamentSize * 0.45f, fillPaint)
            }
            StickerTextDecoration.LOVE -> {
                drawHeart(canvas, 0f, top - padding * 0.8f, ornamentSize * 1.5f, fillPaint)
                drawHeart(canvas, left - padding * 1.2f, 0f, ornamentSize * 1.1f, fillPaint)
                drawHeart(canvas, right + padding * 1.2f, 0f, ornamentSize * 1.1f, fillPaint)
                canvas.drawLine(
                    left - padding * 1.6f, bottom + padding * 0.45f,
                    right + padding * 1.6f, bottom + padding * 0.45f,
                    linePaint
                )
                canvas.drawCircle(0f, bottom + padding * 0.45f, ornamentSize * 0.55f, fillPaint)
            }
            StickerTextDecoration.CELEBRATION -> {
                val outerLeft = left - padding * 1.45f
                val outerRight = right + padding * 1.45f
                val outerTop = top - padding * 0.85f
                val outerBottom = bottom + padding * 0.85f
                val points = listOf(
                    outerLeft to outerTop,
                    outerLeft + padding * 0.45f to outerBottom,
                    outerRight - padding * 0.45f to outerTop,
                    outerRight to outerBottom,
                    left - padding * 0.8f to top - padding * 0.65f,
                    right + padding * 0.8f to top - padding * 0.65f,
                    left - padding * 0.8f to bottom + padding * 0.65f,
                    right + padding * 0.8f to bottom + padding * 0.65f
                )
                points.forEachIndexed { index, point ->
                    if (index % 2 == 0) {
                        canvas.drawCircle(point.first, point.second, ornamentSize * 0.32f, fillPaint)
                    } else {
                        canvas.drawLine(
                            point.first, point.second,
                            point.first + padding * 0.22f, point.second - padding * 0.22f,
                            linePaint
                        )
                    }
                }
                canvas.drawArc(
                    RectF(outerLeft, outerTop, outerLeft + padding * 0.7f, outerTop + padding * 0.7f),
                    180f, 90f, false, linePaint
                )
                canvas.drawArc(
                    RectF(outerRight - padding * 0.7f, outerBottom - padding * 0.7f, outerRight, outerBottom),
                    0f, 90f, false, linePaint
                )
            }
            StickerTextDecoration.SPEECH_BUBBLE -> {
                val bubble = RectF(
                    left - padding * 1.1f, top - padding * 0.85f,
                    right + padding * 1.1f, bottom + padding * 0.85f
                )
                val radius = padding * 0.8f
                val bubbleFill = Paint(fillPaint).apply {
                    alpha = (opacity * 0.82f).toInt()
                }
                canvas.drawRoundRect(bubble, radius, radius, bubbleFill)
                canvas.drawRoundRect(bubble, radius, radius, linePaint)
                val tail = Path()
                tail.moveTo(bubble.left + radius, bubble.bottom - strokeWidth)
                tail.lineTo(bubble.left + radius * 0.4f, bubble.bottom + padding * 0.6f)
                tail.lineTo(bubble.left + radius * 1.7f, bubble.bottom - strokeWidth)
                tail.close()
                canvas.drawPath(tail, bubbleFill)
                canvas.drawPath(tail, linePaint)
                canvas.drawCircle(
                    bubble.right - radius * 0.8f, bubble.top + radius * 0.8f,
                    ornamentSize * 0.28f, fillPaint
                )
            }
        }
    }

    private fun drawHeart(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
        val path = Path()
        path.moveTo(centerX, centerY + size * 0.35f)
        path.cubicTo(
            centerX - size, centerY - size * 0.35f,
            centerX - size * 0.55f, centerY - size * 0.95f,
            centerX, centerY - size * 0.35f
        )
        path.cubicTo(
            centerX + size * 0.55f, centerY - size * 0.95f,
            centerX + size, centerY - size * 0.35f,
            centerX, centerY + size * 0.35f
        )
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawRose(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
        canvas.drawCircle(centerX, centerY, size, paint)
        canvas.drawCircle(centerX, centerY, size * 0.55f, paint)
        canvas.drawArc(
            RectF(centerX - size * 0.5f, centerY - size * 0.5f, centerX + size * 0.5f, centerY + size * 0.5f),
            210f, 240f, false, paint
        )
    }

    private fun StickerBlendMode.toPorterDuff(): PorterDuff.Mode = when (this) {
        StickerBlendMode.NORMAL -> PorterDuff.Mode.SRC_OVER
        StickerBlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        StickerBlendMode.SCREEN -> PorterDuff.Mode.SCREEN
        StickerBlendMode.ADD -> PorterDuff.Mode.ADD
        StickerBlendMode.OVERLAY -> PorterDuff.Mode.OVERLAY
        StickerBlendMode.DARKEN -> PorterDuff.Mode.DARKEN
        StickerBlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }

    fun savePng(bitmap: Bitmap, destination: File): Boolean {
        return try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            destination.exists() && destination.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    fun clampOffsetForZoom(
        source: Bitmap,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        size: Int = STICKER_CANVAS_SIZE
    ): Pair<Float, Float> {
        val safeZoom = zoom.coerceIn(1f, 6f)
        val baseScale = max(size / source.width.toFloat(), size / source.height.toFloat())
        val scale = baseScale * safeZoom
        val maxOffsetX = ((source.width * scale - size) / 2f / size).coerceAtLeast(0f)
        val maxOffsetY = ((source.height * scale - size) / 2f / size).coerceAtLeast(0f)
        return offsetX.coerceIn(-maxOffsetX, maxOffsetX) to
            offsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    fun scaledToFit(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = min(maxDimension / bitmap.width.toFloat(), maxDimension / bitmap.height.toFloat())
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
