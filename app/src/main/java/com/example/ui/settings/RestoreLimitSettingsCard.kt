package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.RestoreManager

/**
 * RestoreLimitSettingsCard
 *
 * Professional UI card in the settings dialog allowing the user to configure
 * the version restore snapshot retention limit (default: 10, configurable up to 20).
 */
@Composable
fun RestoreLimitSettingsCard(
    currentLimit: Int,
    onLimitChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var limit by remember(currentLimit) { mutableStateOf(currentLimit.coerceIn(3, 20)) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2F81F7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Version Restore",
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Version Restore Retention",
                            color = Color(0xFFF0F6FC),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Project snapshots saved before each prompt",
                            color = Color(0xFF8B949E),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2F81F7).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFF2F81F7).copy(alpha = 0.5f)),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = "$limit Versions",
                        color = Color(0xFF79C0FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Default is 10 snapshots (upgraded from 3). You can preserve up to 20 historical versions for rollbacks.",
                color = Color(0xFF8B949E),
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            // Slider & Stepper Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = {
                        if (limit > 3) {
                            limit--
                            onLimitChanged(limit)
                        }
                    },
                    enabled = limit > 3,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease limit",
                        tint = if (limit > 3) Color(0xFFC9D1D9) else Color(0xFF484F58),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Slider(
                    value = limit.toFloat(),
                    onValueChange = {
                        limit = it.toInt()
                    },
                    onValueChangeFinished = {
                        onLimitChanged(limit)
                    },
                    valueRange = 3f..20f,
                    steps = 16,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF58A6FF),
                        activeTrackColor = Color(0xFF2F81F7),
                        inactiveTrackColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (limit < 20) {
                            limit++
                            onLimitChanged(limit)
                        }
                    },
                    enabled = limit < 20,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase limit",
                        tint = if (limit < 20) Color(0xFFC9D1D9) else Color(0xFF484F58),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick preset pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 20).forEach { preset ->
                    val isSelected = limit == preset
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF2F81F7).copy(alpha = 0.25f) else Color(0xFF161B22),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF2F81F7) else Color(0xFF30363D)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                limit = preset
                                onLimitChanged(preset)
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (preset == 10) "10 (Def)" else "$preset",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF79C0FF) else Color(0xFF8B949E)
                            )
                        }
                    }
                }
            }
        }
    }
}
