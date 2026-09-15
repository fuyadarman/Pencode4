package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.HybridSelfLearningEngine

/**
 * SelfLearningSettingsCard
 *
 * A modern, executive-styled configuration card in the Settings Dialog
 * providing access to the Hybrid Self-Learning Cognitive Memory system.
 */
@Composable
fun SelfLearningSettingsCard(
    onOpenSelfLearningDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val learnedPatterns by HybridSelfLearningEngine.learnedPatterns.collectAsState()
    val patternCount = learnedPatterns.size

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF8957E5).copy(alpha = 0.5f),
                    Color(0xFF2F81F7).copy(alpha = 0.3f),
                    Color(0xFF30363D)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenSelfLearningDialog() }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF8957E5).copy(alpha = 0.35f),
                                        Color(0xFF2F81F7).copy(alpha = 0.2f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Hybrid Self Learning",
                            tint = Color(0xFFD2A8FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Hybrid Self-Learning Agent",
                                color = Color(0xFFF0F6FC),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Pulse dot badge
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3FB950))
                            )
                        }

                        Text(
                            text = "Autonomous pattern memory & bug fix retention",
                            color = Color(0xFF8B949E),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFFBC8CFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Cognitive Knowledge Base",
                        color = Color(0xFFC9D1D9),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (patternCount > 0) Color(0xFF8957E5).copy(alpha = 0.22f) else Color(0xFF21262D),
                    border = BorderStroke(
                        1.dp,
                        if (patternCount > 0) Color(0xFF8957E5).copy(alpha = 0.6f) else Color(0xFF30363D)
                    ),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = if (patternCount > 0) "$patternCount Patterns" else "Active (0)",
                        color = if (patternCount > 0) Color(0xFFD2A8FF) else Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
