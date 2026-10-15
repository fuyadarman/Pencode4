package com.example.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF21262D),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
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
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = currentEffort.label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6EDF3)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
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
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF2F81F7) else Color(0xFF484F58))
                                    )
                                    Text(
                                        text = effort.label + if (effort == ReasoningEffort.NORMAL) " (Default)" else "",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF2F81F7),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = effort.description,
                                fontSize = 10.5.sp,
                                color = Color(0xFF6E7681),
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                            )
                        }
                    },
                    modifier = Modifier.background(
                        if (isSelected) Color(0xFF21262D) else Color.Transparent
                    )
                )
            }
        }
    }
}
