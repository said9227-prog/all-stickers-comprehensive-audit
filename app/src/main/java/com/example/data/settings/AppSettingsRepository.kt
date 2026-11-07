package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettingsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getGeminiApiKey(): String {
        return preferences.getString(KEY_GEMINI_API_KEY, "").orEmpty().trim()
    }

    fun setGeminiApiKey(apiKey: String) {
        preferences.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun saveApiKey(apiKey: String) {
        setGeminiApiKey(apiKey)
    }

    fun clearApiKey() {
        preferences.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasApiKey(): Boolean {
        return getGeminiApiKey().isNotBlank()
    }

    fun isAutoRotate3DEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTO_ROTATE_3D, true)
    }

    fun getAutoRotate3D(): Boolean {
        return isAutoRotate3DEnabled()
    }

    fun setAutoRotate3D(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_ROTATE_3D, enabled).apply()
    }

    fun getLastAiStyle(): String? {
        return preferences.getString(KEY_LAST_AI_STYLE, null)?.takeIf { it.isNotBlank() }
    }

    fun setLastAiStyle(style: String?) {
        if (style.isNullOrBlank()) {
            preferences.edit().remove(KEY_LAST_AI_STYLE).apply()
        } else {
            preferences.edit().putString(KEY_LAST_AI_STYLE, style.trim()).apply()
        }
    }

    fun getLastAiCategory(): String? {
        return preferences.getString(KEY_LAST_AI_CATEGORY, null)?.takeIf { it.isNotBlank() }
    }

    fun setLastAiCategory(category: String?) {
        if (category.isNullOrBlank()) {
            preferences.edit().remove(KEY_LAST_AI_CATEGORY).apply()
        } else {
            preferences.edit().putString(KEY_LAST_AI_CATEGORY, category.trim()).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_settings"
        const val KEY_GEMINI_API_KEY = "geminiApiKey"
        const val KEY_AUTO_ROTATE_3D = "autoRotate3D"
        const val KEY_LAST_AI_STYLE = "lastAiStyle"
        const val KEY_LAST_AI_CATEGORY = "lastAiCategory"
    }
}
