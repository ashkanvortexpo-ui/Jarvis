package com.jarvis.assistant

import android.content.Context

/**
 * ذخیره‌ی کلید API به‌صورت محلی روی گوشی (SharedPreferences).
 * این کلید هیچ‌وقت جایی ارسال نمی‌شود مگر مستقیم به سرور گوگل برای گرفتن جواب.
 */
object PrefsHelper {
    private const val PREFS_NAME = "jarvis_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    fun saveApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()
}
