package com.jarvis.assistant

import android.content.Context

object JarvisSettings {
    private const val PREFS = "jarvis_prefs"

    private fun p(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String, default: String = ""): String =
        p(context).getString(key, default) ?: default

    fun put(context: Context, key: String, value: String) {
        p(context).edit().putString(key, value).apply()
    }

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        p(context).getBoolean(key, default)

    fun putBool(context: Context, key: String, value: Boolean) {
        p(context).edit().putBoolean(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int =
        p(context).getInt(key, default)

    fun putInt(context: Context, key: String, value: Int) {
        p(context).edit().putInt(key, value).apply()
    }

    fun profile(context: Context): String = get(context, "profile", "عادی")

    fun setProfile(context: Context, name: String) {
        put(context, "profile", name)
    }

    fun applyProfile(context: Context, name: String) {
        setProfile(context, name)
        when (name) {
            "بازی" -> {
                putBool(context, "hologram_enabled", true)
                putInt(context, "response_length", 2)
                put(context, "theme", "فیروزه‌ای")
            }
            "کار" -> {
                putBool(context, "hologram_enabled", true)
                putInt(context, "response_length", 1)
                put(context, "theme", "آبی")
            }
            "شب" -> {
                putBool(context, "hologram_enabled", false)
                putInt(context, "response_length", 1)
                put(context, "theme", "بنفش")
            }
            else -> {
                putBool(context, "hologram_enabled", true)
                putInt(context, "response_length", 1)
                put(context, "theme", "فیروزه‌ای")
            }
        }
    }
}
