package com.example.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppThemeManager
 * Dedicated manager for app theme settings (Light & Dark options).
 * Defaults to DARK theme by default.
 */
object AppThemeManager {

    private const val PREFS_NAME = "vibe_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    enum class ThemeMode {
        DARK,
        LIGHT
    }

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initialize(context: Context) {
        if (!isInitialized) {
            val savedStr = getPrefs(context).getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
            _themeMode.value = try {
                ThemeMode.valueOf(savedStr.uppercase())
            } catch (e: Exception) {
                ThemeMode.DARK
            }
            isInitialized = true
        }
    }

    fun getThemeMode(context: Context): ThemeMode {
        initialize(context)
        return _themeMode.value
    }

    fun isDarkTheme(context: Context): Boolean {
        return getThemeMode(context) == ThemeMode.DARK
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }
}
