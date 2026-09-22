package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.ReasoningEffort

/**
 * Modern, professional selection dialog for AI Reasoning & Thinking Effort.
 * Provides a clean modal list where users can inspect details, token budgets,
 * and select the desired effort level directly in a single tap.
 */
@Composable
fun ReasoningEffortSelectorDialog(
    currentEffort: ReasoningEffort,
    onSelectEffort: (ReasoningEffort) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF6366F1).copy(alpha = 0.2f), Color(0xFF1E293B)))),
                    RoundedCornerShape(20.dp)
                ),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Thinking & Reasoning",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = "Choose AI cognitive budget & planning depth",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options list
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReasoningEffort.entries.forEach { effort ->
                        val isSelected = effort == currentEffort
                        ReasoningEffortCard(
                            effort = effort,
                            isSelected = isSelected,
                            onClick = {
                                onSelectEffort(effort)
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Applies automatically to Gemini, Claude 3.7 Thinking, o-series & DeepSeek models.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningEffortCard(
    effort: ReasoningEffort,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = when (effort) {
        ReasoningEffort.SMALL -> Color(0xFF38BDF8)
        ReasoningEffort.NORMAL -> Color(0xFF34D399)
        ReasoningEffort.MEDIUM -> Color(0xFFFBBF24)
        ReasoningEffort.MAX -> Color(0xFFA855F7)
    }

    val icon: ImageVector = when (effort) {
        ReasoningEffort.SMALL -> Icons.Default.Bolt
        ReasoningEffort.NORMAL -> Icons.Default.Tune
        ReasoningEffort.MEDIUM -> Icons.Default.Lightbulb
        ReasoningEffort.MAX -> Icons.Default.AutoAwesome
    }

    val tokenBadge = when (effort) {
        ReasoningEffort.SMALL -> "~1K tokens"
        ReasoningEffort.NORMAL -> "~4K tokens • Default"
        ReasoningEffort.MEDIUM -> "~8K tokens • Deep"
        ReasoningEffort.MAX -> "~24.5K tokens • Extended"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else Color(0xFF1E293B).copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else Color(0xFF334155).copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = if (isSelected) 0.25f else 0.12f))
                        .border(1.dp, accentColor.copy(alpha = if (isSelected) 0.6f else 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = effort.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFFE2E8F0)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = tokenBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = effort.description,
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Radio / Check Indicator
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (isSelected) accentColor else Color(0xFF475569),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
