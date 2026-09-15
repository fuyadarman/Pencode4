package com.example.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.ReasoningEffort

/**
 * Compact AI Reasoning Effort selector pill for ModernAgentChatBar.
 * Displays current effort level and expands into an option menu for Small, Normal, Medium, Max.
 */
@Composable
fun ReasoningEffortSelector(
    currentEffort: ReasoningEffort,
    onSelectEffort: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val accentColor = when (currentEffort) {
        ReasoningEffort.SMALL -> Color(0xFF34D399) // Mint / Emerald
        ReasoningEffort.NORMAL -> Color(0xFF60A5FA) // Blue (Default)
        ReasoningEffort.MEDIUM -> Color(0xFFA78BFA) // Purple
        ReasoningEffort.MAX -> Color(0xFFF87171) // Coral Red
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Reasoning Effort",
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = currentEffort.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF161B22))
                .widthIn(min = 220.dp, max = 280.dp)
        ) {
            Text(
                text = "REASONING EFFORT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            ReasoningEffort.entries.forEach { effort ->
                val isSelected = effort == currentEffort
                val itemColor = when (effort) {
                    ReasoningEffort.SMALL -> Color(0xFF34D399)
                    ReasoningEffort.NORMAL -> Color(0xFF60A5FA)
                    ReasoningEffort.MEDIUM -> Color(0xFFA78BFA)
                    ReasoningEffort.MAX -> Color(0xFFF87171)
                }

                DropdownMenuItem(
                    onClick = {
                        onSelectEffort(effort)
                        expanded = false
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(itemColor)
                                    )
                                    Text(
                                        text = effort.label + if (effort == ReasoningEffort.NORMAL) " (Default)" else "",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFC9D1D9)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = itemColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = effort.description,
                                fontSize = 10.sp,
                                color = Color(0xFF8B949E),
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                            )
                        }
                    },
                    modifier = Modifier.background(
                        if (isSelected) itemColor.copy(alpha = 0.10f) else Color.Transparent
                    )
                )
            }
        }
    }
}
