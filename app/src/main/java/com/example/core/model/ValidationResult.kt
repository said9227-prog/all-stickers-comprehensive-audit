package com.example.core.model

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val format: String = "",
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val hasAlpha: Boolean = true,
    val hasAudio: Boolean = false,
    val fps: Float = 0f
) {
    val statusSummaryAr: String
        get() = if (isValid) "✓ جاهز لتيليجرام" else "✗ غير متوافق: " + errors.joinToString(", ")
}
