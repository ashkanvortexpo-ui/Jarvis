package com.jarvis.assistant

import android.content.Context

/**
 * Controls the explicit voice-gated Gaming AI mode.
 *
 * This manager only tracks/announces the mode. It does not bypass
 * anti-cheat systems or inject hidden input into protected games.
 */
object GamingAiManager {
    private const val PREFS = "jarvis_gaming"
    private const val KEY_ENABLED = "gaming_ai_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).apply()
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
        context.stopService(android.content.Intent(context, ScreenCaptureService::class.java))
    }

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        if (next) enable(context) else disable(context)
        return next
    }
}
