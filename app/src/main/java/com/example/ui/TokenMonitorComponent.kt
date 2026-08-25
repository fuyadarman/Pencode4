package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TokenMetrics
import java.text.NumberFormat

@Composable
fun CollapsibleTokenMonitor(
    metrics: TokenMetrics,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize()
    ) {
        if (!isExpanded) {
            // Small Collapsed Round Tab / Pill Button
            Surface(
                onClick = { isExpanded = true },
                shape = CircleShape,
                color = Color(0xFF0F1420),
                border = BorderStroke(0.8.dp, if (metrics.isLive) Color(0xFF00F2FE).copy(alpha = 0.5f) else Color(0xFF262E42)),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Token Monitor",
                        tint = if (metrics.isLive) Color(0xFF00F2FE) else Color(0xFFFFB020),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${metrics.modelName.take(18)}${if (metrics.modelName.length > 18) "..." else ""} • ${NumberFormat.getInstance().format(metrics.calculatedTotalInput)} tokens",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand Token Monitor",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            // Full Token Monitor Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1420)),
                border = BorderStroke(1.dp, if (metrics.isLive) Color(0xFF00F2FE).copy(alpha = 0.5f) else Color(0xFF262E42)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Row: Model & Timer & Collapse Trigger
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = false },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Model",
                                tint = if (metrics.isLive) Color(0xFF00F2FE) else Color(0xFFFFB020),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (metrics.isLive) "${metrics.modelName} (${metrics.executionTimeSeconds}s)" else "${metrics.modelName} (${metrics.executionTimeSeconds}s)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (metrics.isLive) Color(0xFF00F2FE).copy(alpha = 0.15f) else Color(0xFF1E293B)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (metrics.isLive) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFF00F2FE)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Timer",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = if (metrics.isLive) "${metrics.executionTimeSeconds}s" else "${metrics.executionTimeSeconds}s",
                                        color = if (metrics.isLive) Color(0xFF00F2FE) else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = "Collapse",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

                    // Token Breakdown Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Token Monitor",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "TOKEN MONITOR" + if (metrics.isLive) " (LIVE)" else "",
                            color = Color(0xFFA855F7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Detailed Breakdown List
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TokenMetricRowItem(label = "System Instruction", count = metrics.systemTokens, color = Color(0xFF38BDF8))
                        TokenMetricRowItem(label = "User Prompt", count = metrics.userTokens, color = Color(0xFF4ADE80))
                        TokenMetricRowItem(label = "Chat History", count = metrics.historyTokens, color = Color(0xFFF472B6))
                        TokenMetricRowItem(label = "Skill / Tool Usage", count = metrics.skillTokens, color = Color(0xFFFACC15))
                    }

                    HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

                    // Jcode Harness Architecture Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, Color(0xFF818CF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Jcode Harness",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Jcode Runtime: Lean Prompt + Semantic Vector Memory",
                                color = Color(0xFFC7D2FE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Totals Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total Input:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("${NumberFormat.getInstance().format(metrics.calculatedTotalInput)} tokens", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total Output:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("${NumberFormat.getInstance().format(metrics.totalOutputTokens)} tokens", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenMetricRowItem(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(6.dp)
            ) {}
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp
            )
        }
        Text(
            text = "${NumberFormat.getInstance().format(count)} tokens",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
