package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.AppThemeManager

@Composable
fun SettingsThemeSelectionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentThemeMode by AppThemeManager.themeMode.collectAsState()

    LaunchedEffect(Unit) {
        AppThemeManager.initialize(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(com.example.ui.theme.AppTheme.bgSurfaceElevated, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, com.example.ui.theme.AppTheme.border), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "App Theme Mode",
                    color = com.example.ui.theme.AppTheme.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select your preferred visual style. Default is Dark Theme.",
                    color = com.example.ui.theme.AppTheme.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isAppDark = com.example.ui.theme.AppTheme.isDark
            // Dark Mode Option (Default)
            val isDarkSelected = currentThemeMode == AppThemeManager.ThemeMode.DARK
            val darkCardBg = if (isDarkSelected) {
                if (isAppDark) Color(0xFF21262D) else Color(0xFFE2E8F0)
            } else {
                if (isAppDark) Color(0xFF161B22) else Color(0xFFFFFFFF)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(darkCardBg)
                    .border(
                        BorderStroke(
                            width = if (isDarkSelected) 1.5.dp else 1.dp,
                            color = if (isDarkSelected) com.example.ui.theme.AppTheme.accentBlue else com.example.ui.theme.AppTheme.border
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        AppThemeManager.setThemeMode(context, AppThemeManager.ThemeMode.DARK)
                    }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = "Dark Theme",
                        tint = if (isDarkSelected) com.example.ui.theme.AppTheme.accentBlue else com.example.ui.theme.AppTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Dark (Default)",
                        fontSize = 12.5.sp,
                        fontWeight = if (isDarkSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isDarkSelected) com.example.ui.theme.AppTheme.textPrimary else com.example.ui.theme.AppTheme.textSecondary
                    )
                }
            }

            // Light Mode Option
            val isLightSelected = currentThemeMode == AppThemeManager.ThemeMode.LIGHT
            val lightCardBg = if (isLightSelected) {
                if (isAppDark) Color(0xFF21262D) else Color(0xFFE2E8F0)
            } else {
                if (isAppDark) Color(0xFF161B22) else Color(0xFFFFFFFF)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lightCardBg)
                    .border(
                        BorderStroke(
                            width = if (isLightSelected) 1.5.dp else 1.dp,
                            color = if (isLightSelected) com.example.ui.theme.AppTheme.accentBlue else com.example.ui.theme.AppTheme.border
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        AppThemeManager.setThemeMode(context, AppThemeManager.ThemeMode.LIGHT)
                    }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = "Light Theme",
                        tint = if (isLightSelected) Color(0xFFD29922) else com.example.ui.theme.AppTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Light Theme",
                        fontSize = 12.5.sp,
                        fontWeight = if (isLightSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLightSelected) com.example.ui.theme.AppTheme.textPrimary else com.example.ui.theme.AppTheme.textSecondary
                    )
                }
            }
        }
    }
}
