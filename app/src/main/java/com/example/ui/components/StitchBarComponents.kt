package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StitchTheme
import com.example.ui.theme.stitchPressFeedback

/**
 * Psychological Navigation Bar with smooth animated pill indicators,
 * high-contrast icons, and tactile tap feedback.
 */
@Composable
fun StitchBottomNavBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .border(
                BorderStroke(1.dp, StitchTheme.BorderGlass),
                RoundedCornerShape(26.dp)
            ),
        color = StitchTheme.SurfaceDark.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                val isSelected = selectedIndex == index
                val tabBackground by animateColorAsState(
                    targetValue = if (isSelected) StitchTheme.PrimaryViolet.copy(alpha = 0.25f) else Color.Transparent,
                    animationSpec = spring(stiffness = 400f),
                    label = "tab_bg"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) StitchTheme.ElectricCyan else StitchTheme.TextSub,
                    label = "tab_icon"
                )
                val borderWidth by animateDpAsState(
                    targetValue = if (isSelected) 1.dp else 0.dp,
                    label = "tab_border"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(tabBackground)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    BorderStroke(borderWidth, StitchTheme.ElectricCyan.copy(alpha = 0.4f)),
                                    RoundedCornerShape(18.dp)
                                )
                            } else Modifier
                        )
                        .stitchPressFeedback(scaleDown = 0.92f) { onTabSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = StitchTheme.TextMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Psychological Sleek Header for Project Workspace.
 */
@Composable
fun StitchWorkspaceHeader(
    projectName: String,
    statusText: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, StitchTheme.BorderSubtle)),
        color = StitchTheme.SurfaceDark.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StitchTheme.SurfaceElevated)
                        .border(BorderStroke(1.dp, StitchTheme.BorderGlass), RoundedCornerShape(12.dp))
                        .stitchPressFeedback(scaleDown = 0.90f, onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = StitchTheme.TextMain,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = projectName,
                        color = StitchTheme.TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StitchTheme.EmeraldSuccess)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            color = StitchTheme.TextSub,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = trailingActions
            )
        }
    }
}
