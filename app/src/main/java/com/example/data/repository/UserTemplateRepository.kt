package com.example.data.repository

import android.content.Context
import com.example.creator.StickerFontStyle
import com.example.creator.StickerTextAlign
import com.example.creator.StickerTextDecoration
import com.example.creator.StickerTextEffect
import com.example.creator.StickerTextElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SavedTextTemplate(
    val id: String,
    val name: String,
    val text: String,
    val isEmoji: Boolean,
    val fontStyleName: String,
    val fontId: String,
    val italic: Boolean,
    val textAlignName: String,
    val lineSpacing: Float,
    val surroundEmoji: String,
    val effectName: String,
    val effectStartColor: Int,
    val effectEndColor: Int,
    val effectIntensity: Float,
    val color: Int,
    val strokeColor: Int,
    val strokeWidth: Float,
    val shadow: Boolean,
    val opacity: Float,
    val decorationName: String,
    val fontSize: Float,
    val scale: Float,
    val rotation: Float
) {
    fun toElement(): StickerTextElement = StickerTextElement(
        id = System.nanoTime(),
        text = text,
        isEmoji = isEmoji,
        x = 0.5f,
        y = 0.5f,
        scale = scale,
        rotation = rotation,
        fontSize = fontSize,
        color = color,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        shadow = shadow,
        opacity = opacity,
        fontStyle = runCatching { StickerFontStyle.valueOf(fontStyleName) }
            .getOrDefault(StickerFontStyle.SANS_BOLD),
        fontId = fontId,
        italic = italic,
        textAlign = runCatching { StickerTextAlign.valueOf(textAlignName) }
            .getOrDefault(StickerTextAlign.CENTER),
        lineSpacing = lineSpacing,
        surroundEmoji = surroundEmoji,
        decoration = runCatching { StickerTextDecoration.valueOf(decorationName) }
            .getOrDefault(StickerTextDecoration.NONE),
        effect = runCatching { StickerTextEffect.valueOf(effectName) }
            .getOrDefault(StickerTextEffect.NONE),
        effectStartColor = effectStartColor,
        effectEndColor = effectEndColor,
        effectIntensity = effectIntensity
    )
}

class UserTemplateRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "text_templates.json")
    private val _templates = MutableStateFlow<List<SavedTextTemplate>>(emptyList())
    val templates: StateFlow<List<SavedTextTemplate>> = _templates.asStateFlow()

    init {
        load()
    }

    @Synchronized
    fun save(element: StickerTextElement, name: String): SavedTextTemplate {
        val template = SavedTextTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "قالب ${_templates.value.size + 1}" }.take(48),
            text = element.text,
            isEmoji = element.isEmoji,
            fontStyleName = element.fontStyle.name,
            fontId = element.fontId,
            italic = element.italic,
            textAlignName = element.textAlign.name,
            lineSpacing = element.lineSpacing,
            surroundEmoji = element.surroundEmoji,
            effectName = element.effect.name,
            effectStartColor = element.effectStartColor,
            effectEndColor = element.effectEndColor,
            effectIntensity = element.effectIntensity,
            color = element.color,
            strokeColor = element.strokeColor,
            strokeWidth = element.strokeWidth,
            shadow = element.shadow,
            opacity = element.opacity,
            decorationName = element.decoration.name,
            fontSize = element.fontSize,
            scale = element.scale,
            rotation = element.rotation
        )
        _templates.value = listOf(template) + _templates.value
        persist()
        return template
    }

    @Synchronized
    fun delete(id: String) {
        _templates.value = _templates.value.filterNot { it.id == id }
        persist()
    }

    private fun load() {
        val loaded = try {
            if (!file.exists()) return
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val text = item.optString("text")
                    if (text.isBlank()) continue
                    add(
                        SavedTextTemplate(
                            id = id,
                            name = item.optString("name").ifBlank { "قالب" },
                            text = text,
                            isEmoji = item.optBoolean("isEmoji", false),
                            fontStyleName = item.optString("fontStyleName", "SANS_BOLD"),
                            fontId = item.optString("fontId", ""),
                            italic = item.optBoolean("italic", false),
                            textAlignName = item.optString("textAlignName", "CENTER"),
                            lineSpacing = item.optDouble("lineSpacing", 1.0).toFloat(),
                            surroundEmoji = item.optString("surroundEmoji", ""),
                            effectName = item.optString("effectName", "NONE"),
                            effectStartColor = item.optInt("effectStartColor", 0xFFFFD166.toInt()),
                            effectEndColor = item.optInt("effectEndColor", 0xFFFF4D6D.toInt()),
                            effectIntensity = item.optDouble("effectIntensity", 1.0).toFloat(),
                            color = item.optInt("color", 0xFFFFFFFF.toInt()),
                            strokeColor = item.optInt("strokeColor", 0xFF000000.toInt()),
                            strokeWidth = item.optDouble("strokeWidth", 6.0).toFloat(),
                            shadow = item.optBoolean("shadow", true),
                            opacity = item.optDouble("opacity", 1.0).toFloat(),
                            decorationName = item.optString("decorationName", "NONE"),
                            fontSize = item.optDouble("fontSize", 56.0).toFloat(),
                            scale = item.optDouble("scale", 1.0).toFloat(),
                            rotation = item.optDouble("rotation", 0.0).toFloat()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        _templates.value = loaded
    }

    private fun persist() {
        try {
            val array = JSONArray()
            _templates.value.forEach { template ->
                array.put(
                    JSONObject().apply {
                        put("id", template.id)
                        put("name", template.name)
                        put("text", template.text)
                        put("isEmoji", template.isEmoji)
                        put("fontStyleName", template.fontStyleName)
                        put("fontId", template.fontId)
                        put("italic", template.italic)
                        put("textAlignName", template.textAlignName)
                        put("lineSpacing", template.lineSpacing.toDouble())
                        put("surroundEmoji", template.surroundEmoji)
                        put("effectName", template.effectName)
                        put("effectStartColor", template.effectStartColor)
                        put("effectEndColor", template.effectEndColor)
                        put("effectIntensity", template.effectIntensity.toDouble())
                        put("color", template.color)
                        put("strokeColor", template.strokeColor)
                        put("strokeWidth", template.strokeWidth.toDouble())
                        put("shadow", template.shadow)
                        put("opacity", template.opacity.toDouble())
                        put("decorationName", template.decorationName)
                        put("fontSize", template.fontSize.toDouble())
                        put("scale", template.scale.toDouble())
                        put("rotation", template.rotation.toDouble())
                    }
                )
            }
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(array.toString(2))
            if (!temporary.renameTo(file)) {
                file.writeText(temporary.readText())
                temporary.delete()
            }
        } catch (_: Exception) {
        }
    }
}
