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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.agent.ReasoningEffort

/**
 * Dedicated Chatbar Tune Controls Button.
 * Houses Plan Mode, Thinking & Reasoning Effort, and MCP Server controls
 * under a single clean Tune/Sliders icon button matching the user UI spec.
 */
@Composable
fun ChatTuneControlsButton(
    isPlanModeActive: Boolean,
    onTogglePlanMode: (Boolean) -> Unit,
    currentReasoningEffort: ReasoningEffort,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    selectedMcpServerIds: Set<String>,
    onOpenSelectMcpDialog: () -> Unit,
    onOpenReasoningDialog: () -> Unit,
    onStartNewChat: () -> Unit = {},
    onSelectChatSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Active indicator if plan mode is ON, MCP servers selected, or custom reasoning set
    val hasActiveSettings = isPlanModeActive || selectedMcpServerIds.isNotEmpty() || currentReasoningEffort != ReasoningEffort.NORMAL

    Box(modifier = modifier) {
        // Tune Icon Button
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = if (hasActiveSettings) Color(0xFF2B313E) else Color(0xFF262933),
            border = BorderStroke(
                1.dp,
                if (isPlanModeActive) Color(0xFFF59E0B)
                else if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8)
                else if (hasActiveSettings) Color(0xFF818CF8)
                else Color(0xFF373C4D)
            ),
            modifier = Modifier.size(34.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Tune Controls: Plan, Reasoning, MCP",
                    tint = if (isPlanModeActive) Color(0xFFF59E0B)
                    else if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8)
                    else if (hasActiveSettings) Color(0xFF818CF8)
                    else Color(0xFFC9D1D9),
                    modifier = Modifier.size(18.dp)
                )

                // Small badge dot when special settings are active
                if (hasActiveSettings) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                            .clip(CircleShape)
                            .background(if (isPlanModeActive) Color(0xFFF59E0B) else Color(0xFF38BDF8))
                    )
                }
            }
        }

        // Popup Menu Container
        if (isExpanded) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(0, -110),
                onDismissRequest = { isExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(310.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Color(0xFF333B4A)), RoundedCornerShape(16.dp)),
                    color = Color(0xFF1E222D),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Agent & Tool Controls",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF0F6FC)
                                )
                            }
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF8B949E),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF2A3140), thickness = 1.dp)

                        // Start New Chat & Recent Sessions (Integrated into filter button)
                        val projectSessions by com.example.chat.ProjectChatSessionManager.currentSessions.collectAsState()
                        val activeSessionId by com.example.chat.ProjectChatSessionManager.activeSessionId.collectAsState()
                        val sessionDateFormatter = remember { java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242936))
                                .border(1.dp, Color(0xFF2E3545), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isExpanded = false
                                        onStartNewChat()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF238636)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Start new chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start new chat",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF0F6FC)
                                    )
                                    Text(
                                        text = "Start fresh session in project",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }
                            }

                            if (projectSessions.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF2E3545), thickness = 1.dp)
                                Text(
                                    text = "RECENT CHATS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8B949E),
                                    letterSpacing = 0.5.sp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    projectSessions.take(3).forEach { session ->
                                        val isCurrent = session.id == activeSessionId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isCurrent) Color(0xFF1E293B) else Color.Transparent)
                                                .clickable {
                                                    isExpanded = false
                                                    onSelectChatSession(session.id)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = session.title,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent) Color(0xFF58A6FF) else Color(0xFFC9D1D9),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = sessionDateFormatter.format(java.util.Date(session.timestamp)),
                                                    fontSize = 9.5.sp,
                                                    color = Color(0xFF6E7681)
                                                )
                                            }
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = Color(0xFF58A6FF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Plan Mode Section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPlanModeActive) Color(0xFF2C281E) else Color(0xFF242936))
                                .border(
                                    1.dp,
                                    if (isPlanModeActive) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF2E3545),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onTogglePlanMode(!isPlanModeActive) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isPlanModeActive) Color(0xFF45300B) else Color(0xFF1E222D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        tint = if (isPlanModeActive) Color(0xFFF59E0B) else Color(0xFF8E95A5),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Plan Mode",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPlanModeActive) Color(0xFFF59E0B) else Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = if (isPlanModeActive) "Step-by-step plan enabled" else "Propose plan before building",
                                        fontSize = 11.sp,
                                        color = Color(0xFF8E95A5)
                                    )
                                }
                            }

                            Switch(
                                checked = isPlanModeActive,
                                onCheckedChange = { onTogglePlanMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFF59E0B),
                                    uncheckedThumbColor = Color(0xFF8E95A5),
                                    uncheckedTrackColor = Color(0xFF1E222D)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // 2. Thinking & Reasoning Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF242936))
                                .border(1.dp, Color(0xFF2E3545), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2C243B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = null,
                                            tint = Color(0xFFBC8CFF),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Thinking & Reasoning",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFE2E8F0)
                                        )
                                        Text(
                                            text = "Level: ${currentReasoningEffort.label} (${currentReasoningEffort.thinkingBudget} tokens)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8E95A5)
                                        )
                                    }
                                }

                                Text(
                                    text = "Details",
                                    fontSize = 11.sp,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            isExpanded = false
                                            onOpenReasoningDialog()
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            // Inline Effort Selector Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ReasoningEffort.values().forEach { effort ->
                                    val isSelected = currentReasoningEffort == effort
                                    Surface(
                                        onClick = { onSelectReasoningEffort(effort) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) Color(0xFF38294F) else Color(0xFF1B1E28),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFBC8CFF) else Color(0xFF2E3342)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = effort.label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFFBC8CFF) else Color(0xFFC9D1D9)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. MCP Servers Section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedMcpServerIds.isNotEmpty()) Color(0xFF16253B) else Color(0xFF242936))
                                .border(
                                    1.dp,
                                    if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF2E3545),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    isExpanded = false
                                    onOpenSelectMcpDialog()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedMcpServerIds.isNotEmpty()) Color(0xFF0F365E) else Color(0xFF1E222D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF8E95A5),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "MCP Servers",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = if (selectedMcpServerIds.isNotEmpty()) "${selectedMcpServerIds.size} servers connected" else "Tap to connect tools & APIs",
                                        fontSize = 11.sp,
                                        color = Color(0xFF8E95A5)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF2E3545),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = if (selectedMcpServerIds.isNotEmpty()) "Active" else "Manage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedMcpServerIds.isNotEmpty()) Color.Black else Color(0xFFC9D1D9),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
