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
                .clip(RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFF30363D)),
                    RoundedCornerShape(16.dp)
                ),
            color = Color(0xFF161B22),
            tonalElevation = 8.dp
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
                                .background(Color(0xFF21262D))
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFFF0F6FC),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Thinking & Reasoning",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Choose AI cognitive budget & planning depth",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E)
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
                            tint = Color(0xFF8B949E),
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
                        .background(Color(0xFF21262D).copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Applies automatically to Gemini, Claude 3.7 Thinking, o-series & DeepSeek models.",
                        fontSize = 10.5.sp,
                        color = Color(0xFF8B949E),
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
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF21262D) else Color(0xFF161B22),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF8B949E) else Color(0xFF30363D)
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
                        .background(if (isSelected) Color(0xFF30363D) else Color(0xFF21262D))
                        .border(1.dp, if (isSelected) Color(0xFF8B949E) else Color(0xFF30363D), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
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
                            color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFFC9D1D9)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF21262D),
                            border = BorderStroke(0.5.dp, Color(0xFF30363D))
                        ) {
                            Text(
                                text = tokenBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = effort.description,
                        fontSize = 11.5.sp,
                        color = Color(0xFF8B949E),
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
                    .background(if (isSelected) Color(0xFFF0F6FC) else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (isSelected) Color(0xFFF0F6FC) else Color(0xFF484F58),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF161B22),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
