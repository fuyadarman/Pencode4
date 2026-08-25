package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.harness.ParallelSubAgentCoordinator

/**
 * Jcode Harness Status & Telemetry UI Component.
 * Displays live indicators for Semantic Memory Store, Parallel Sub-Agents, and Lean Context optimization.
 */
@Composable
fun JcodeHarnessStatusCard(
    memoryCount: Int,
    recalledMemoriesCount: Int,
    isMultiAgentActive: Boolean,
    activeSubAgents: List<ParallelSubAgentCoordinator.SubAgentTask> = emptyList(),
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131622),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282F48))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Harness Branding Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFF818CF8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Jcode Harness",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = "Jcode Harness Runtime",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Semantic Memory Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Semantic Memory",
                        tint = if (recalledMemoriesCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (recalledMemoriesCount > 0) "Recalled $recalledMemoriesCount memories" else "$memoryCount indexed memories",
                        color = if (recalledMemoriesCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            // Sub-Agent Parallel Execution Row (visible when parallel agents are spawned)
            AnimatedVisibility(
                visible = isMultiAgentActive && activeSubAgents.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = "Parallel Teammate Agents:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeSubAgents.forEach { subAgent ->
                            val (bgColor, textColor, borderColor) = when (subAgent.status) {
                                "completed" -> Triple(Color(0xFF065F46).copy(alpha = 0.4f), Color(0xFF34D399), Color(0xFF10B981))
                                "running" -> Triple(Color(0xFF1E3A8A).copy(alpha = 0.5f), Color(0xFF60A5FA), Color(0xFF3B82F6))
                                "failed" -> Triple(Color(0xFF7F1D1D).copy(alpha = 0.4f), Color(0xFFF87171), Color(0xFFEF4444))
                                else -> Triple(Color(0xFF1E293B), Color(0xFF94A3B8), Color(0xFF334155))
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = bgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(textColor, CircleShape)
                                    )
                                    Text(
                                        text = subAgent.role.displayName.replace(" Agent", ""),
                                        color = textColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
