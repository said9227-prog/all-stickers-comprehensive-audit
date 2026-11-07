package com.example.core.model

data class StudioImage(
    val id: String,
    val name: String,
    val originalPath: String,
    val editedPath: String? = null,
    val isText: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val displayPath: String
        get() = editedPath ?: originalPath
}
