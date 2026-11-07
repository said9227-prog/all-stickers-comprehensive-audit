package com.example.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object BitmapUtils {

    const val TELEGRAM_MAX_DIMENSION = 512

    data class ScaledDimensions(val width: Int, val height: Int)

    /**
     * Calculates dimensions such that the largest side is exactly 512px,
     * maintaining aspect ratio, and ensuring dimensions are even (divisible by 2) for video encoders.
     */
    fun calculateTelegramDimensions(originalWidth: Int, originalHeight: Int, forceEven: Boolean = false): ScaledDimensions {
        if (originalWidth <= 0 || originalHeight <= 0) {
            return ScaledDimensions(TELEGRAM_MAX_DIMENSION, TELEGRAM_MAX_DIMENSION)
        }

        val targetW: Int
        val targetH: Int

        if (originalWidth >= originalHeight) {
            targetW = TELEGRAM_MAX_DIMENSION
            val ratio = TELEGRAM_MAX_DIMENSION.toFloat() / originalWidth.toFloat()
            targetH = (originalHeight * ratio).toInt().coerceIn(1, TELEGRAM_MAX_DIMENSION)
        } else {
            targetH = TELEGRAM_MAX_DIMENSION
            val ratio = TELEGRAM_MAX_DIMENSION.toFloat() / originalHeight.toFloat()
            targetW = (originalWidth * ratio).toInt().coerceIn(1, TELEGRAM_MAX_DIMENSION)
        }

        val finalW = if (forceEven && (targetW % 2 != 0)) targetW - 1 else targetW
        val finalH = if (forceEven && (targetH % 2 != 0)) targetH - 1 else targetH

        return ScaledDimensions(finalW.coerceAtLeast(2), finalH.coerceAtLeast(2))
    }

    /**
     * Scales a bitmap preserving transparency without creating a black or white background.
     */
    fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source
        }

        val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaled)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val matrix = Matrix()
        val scaleX = targetWidth.toFloat() / source.width.toFloat()
        val scaleY = targetHeight.toFloat() / source.height.toFloat()
        matrix.setScale(scaleX, scaleY)

        canvas.drawBitmap(source, matrix, paint)
        return scaled
    }
}
