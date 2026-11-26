package com.example.ui.theme

import android.content.Context

object ThemePreferences {
    private const val PREFS_NAME = "life_line_theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode_enabled"

    fun hasUserChoice(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_DARK_MODE)
    }

    fun isDarkMode(context: Context, defaultDark: Boolean): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_DARK_MODE)) {
            prefs.getBoolean(KEY_DARK_MODE, defaultDark)
        } else {
            defaultDark
        }
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }
}
