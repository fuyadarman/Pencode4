package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StitchTheme
import com.example.ui.theme.stitchPressFeedback

/**
 * Modern Clean Action Button / FAB.
 */
@Composable
fun StitchFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    contentDescription: String? = "Create",
    gradient: Brush = StitchTheme.AuroraGradient,
    size: Dp = 52.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF238636))
            .border(BorderStroke(1.dp, Color(0xFF2EA043).copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
            .stitchPressFeedback(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Unified Tab Pill Group for workspace view switching (e.g. Chat, Code, Preview).
 */
@Composable
fun StitchTabPillGroup(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
            .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF21262D) else Color.Transparent,
                label = "tab_pill_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                label = "tab_pill_text"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                BorderStroke(1.dp, Color(0xFF30363D)),
                                RoundedCornerShape(6.dp)
                            )
                        } else Modifier
                    )
                    .stitchPressFeedback(scaleDown = 0.96f) { onTabSelected(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Quick Action Suggestion Chips.
 */
@Composable
fun StitchQuickActionChipsRow(
    actions: List<Pair<String, ImageVector?>>,
    onActionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (label, icon) ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, StitchTheme.BorderGlass), RoundedCornerShape(14.dp))
                    .stitchPressFeedback(scaleDown = 0.94f) { onActionSelected(label) },
                color = StitchTheme.SurfaceElevated.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = StitchTheme.VibrantSky,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = label,
                        color = StitchTheme.TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
