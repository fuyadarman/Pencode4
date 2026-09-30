package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.settings.AppThemeManager

/**
 * AppThemePalette
 * Centralized dynamic theme color resolver that delivers full Dark & Light mode
 * switching across the entire Android application.
 */
object AppTheme {

    val isDark: Boolean
        @Composable
        get() = AppThemeManager.themeMode.collectAsState().value == AppThemeManager.ThemeMode.DARK

    val bgApp: Color
        @Composable
        get() = if (isDark) Color(0xFF08080C) else Color(0xFFF6F8FA)

    val bgCanvas: Color
        @Composable
        get() = if (isDark) Color(0xFF0D1117) else Color(0xFFFFFFFF)

    val bgSurface: Color
        @Composable
        get() = if (isDark) Color(0xFF161B22) else Color(0xFFFFFFFF)

    val bgSurfaceElevated: Color
        @Composable
        get() = if (isDark) Color(0xFF21262D) else Color(0xFFF3F4F6)

    val bgCard: Color
        @Composable
        get() = if (isDark) Color(0xFF161B22) else Color(0xFFFFFFFF)

    val bgCardElevated: Color
        @Composable
        get() = if (isDark) Color(0xFF21262D) else Color(0xFFF6F8FA)

    val border: Color
        @Composable
        get() = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)

    val borderSubtle: Color
        @Composable
        get() = if (isDark) Color(0xFF21262D) else Color(0xFFE2E8F0)

    val textPrimary: Color
        @Composable
        get() = if (isDark) Color(0xFFF0F6FC) else Color(0xFF1F2328)

    val textSecondary: Color
        @Composable
        get() = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A)

    val textMuted: Color
        @Composable
        get() = if (isDark) Color(0xFF6E7681) else Color(0xFF6E7781)

    val topBarBg: Color
        @Composable
        get() = if (isDark) Color(0xFF0A0B10) else Color(0xFFFFFFFF)

    val bottomBarBg: Color
        @Composable
        get() = if (isDark) Color(0xFF161B22) else Color(0xFFFFFFFF)

    val inputBg: Color
        @Composable
        get() = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)

    val dialogBg: Color
        @Composable
        get() = if (isDark) Color(0xFF161B22) else Color(0xFFFFFFFF)

    val accentBlue: Color
        @Composable
        get() = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA)

    val primary: Color
        @Composable
        get() = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)

    val accentGreen: Color
        @Composable
        get() = if (isDark) Color(0xFF238636) else Color(0xFF1A7F37)
}
