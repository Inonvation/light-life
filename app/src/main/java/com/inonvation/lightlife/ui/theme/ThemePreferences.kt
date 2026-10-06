package com.inonvation.lightlife.ui.theme

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ColorTheme {
    GREEN, PINK, YELLOW, BLUE, BROWN
}

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode = try {
        ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    } catch (_: Exception) {
        ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getColorTheme(): ColorTheme = try {
        ColorTheme.valueOf(prefs.getString(KEY_COLOR_THEME, ColorTheme.GREEN.name) ?: ColorTheme.GREEN.name)
    } catch (_: Exception) {
        ColorTheme.GREEN
    }

    fun setColorTheme(theme: ColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
    }

    private companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
    }
}
