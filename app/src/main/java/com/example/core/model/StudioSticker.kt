package com.example.core.model

/**
 * A sticker that lives entirely inside the app (offline, no internet required).
 *
 * [type] = NORMAL_IMAGE  -> a flat PNG/WebP image added as an image layer in the studio
 * [type] = MODEL_3D      -> a GLB/GLTF 3D model added as a model node beside the text
 *
 * [assetPath]        path to the asset inside src/main/assets/stickers/...
 * [previewAssetPath] optional small preview thumbnail (falls back to assetPath)
 */
data class StudioSticker(
    val id: String,
    val name: String,
    val type: StudioStickerType,
    val assetPath: String,
    val previewAssetPath: String? = null,
    val category: String = "عام",
    val tags: List<String> = emptyList()
)

enum class StudioStickerType(val labelAr: String, val is3D: Boolean) {
    NORMAL_IMAGE("ملصق عادي", false),
    MODEL_3D("نموذج ثلاثي الأبعاد", true)
}
