package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AgentSkill
import com.example.ui.AttachedFile
import com.example.agent.ReasoningEffort

/**
 * Pixel-perfect clean, professional chat bar matching modern IDEs (Cursor/Gemini).
 * Clean capsule shape, dark background, plus button, optional mic, and circular send arrow.
 */
@Composable
fun ProfessionalChatBar(
    chatInputText: String,
    onUpdateChatInputText: (String) -> Unit,
    isThinking: Boolean,
    onSend: () -> Unit,
    onStopAI: () -> Unit,
    onAttachClick: () -> Unit,
    selectedMcpServerIds: Set<String> = emptySet(),
    onOpenSelectMcpDialog: () -> Unit = {},
    attachedFiles: List<AttachedFile> = emptyList(),
    onRemoveAttachedFile: (AttachedFile) -> Unit = {},
    taggedFiles: List<com.example.data.ProjectFileEntity> = emptyList(),
    onRemoveTaggedFile: (com.example.data.ProjectFileEntity) -> Unit = {},
    taggedSkills: List<AgentSkill> = emptyList(),
    onRemoveTaggedSkill: (AgentSkill) -> Unit = {},
    currentReasoningEffort: ReasoningEffort = ReasoningEffort.NORMAL,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()
    var showPlusMenu by remember { mutableStateOf(false) }
    var showReasoningEffortDialog by remember { mutableStateOf(false) }

    if (showReasoningEffortDialog) {
        ReasoningEffortSelectorDialog(
            currentEffort = currentReasoningEffort,
            onSelectEffort = {
                onSelectReasoningEffort(it)
                showReasoningEffortDialog = false
            },
            onDismiss = { showReasoningEffortDialog = false }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Tagged/attached chips row
        if (attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachedFiles.forEach { file ->
                    ProfessionalChip(
                        label = file.name,
                        icon = Icons.Default.AttachFile,
                        accentColor = Color(0xFF58A6FF),
                        onRemove = { onRemoveAttachedFile(file) }
                    )
                }
                taggedFiles.forEach { file ->
                    ProfessionalChip(
                        label = "@${file.path}",
                        icon = Icons.Default.Description,
                        accentColor = Color(0xFF3FB950),
                        onRemove = { onRemoveTaggedFile(file) }
                    )
                }
                taggedSkills.forEach { skill ->
                    ProfessionalChip(
                        label = "/${skill.name}",
                        icon = Icons.Default.Psychology,
                        accentColor = Color(0xFFBC8CFF),
                        onRemove = { onRemoveTaggedSkill(skill) }
                    )
                }
            }
        }

        // Main Chat Bar Capsule Container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF1E1F22),
            border = BorderStroke(1.dp, Color(0xFF2B2D31)),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top area: Text Input
                TextField(
                    value = chatInputText,
                    onValueChange = onUpdateChatInputText,
                    placeholder = {
                        Text(
                            text = "describe your request (@ files, / skills)...",
                            color = Color(0xFF8E9297),
                            fontSize = 13.5.sp
                        )
                    },
                    textStyle = TextStyle(
                        color = Color(0xFFF2F3F5),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF2979FF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                    minLines = 1,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom row with + and MCP buttons on the left, and mic + circular send button on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: + Button and MCP button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            IconButton(
                                onClick = { showPlusMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add attachment or tools",
                                    tint = Color(0xFFDBDEE1),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showPlusMenu,
                                onDismissRequest = { showPlusMenu = false },
                                modifier = Modifier.background(Color(0xFF2B2D31))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Attach File", color = Color(0xFFF2F3F5), fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showPlusMenu = false
                                        onAttachClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selectedMcpServerIds.isNotEmpty()) "MCP Servers (${selectedMcpServerIds.size})" else "MCP Servers",
                                            color = Color(0xFFF2F3F5),
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showPlusMenu = false
                                        onOpenSelectMcpDialog()
                                    }
                                )
                                HorizontalDivider(color = Color(0xFF383A40))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Thinking: ${currentReasoningEffort.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                            color = Color(0xFFF2F3F5),
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFBC8CFF), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showPlusMenu = false
                                        showReasoningEffortDialog = true
                                    }
                                )
                            }
                        }

                        // Dedicated MCP Button
                        Surface(
                            onClick = onOpenSelectMcpDialog,
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8).copy(alpha = 0.18f) else Color(0xFF2B2D31),
                            border = BorderStroke(
                                1.dp,
                                if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8) else Color(0xFF383A40)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "MCP Servers",
                                    tint = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (selectedMcpServerIds.isNotEmpty()) "MCP (${selectedMcpServerIds.size})" else "MCP",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8) else Color(0xFF94A3B8)
                                )
                            }
                        }

                        // Dedicated Thinking Effort Pill Button (Opens list selection dialog)
                        val effortColor = when (currentReasoningEffort) {
                            ReasoningEffort.SMALL -> Color(0xFF38BDF8)
                            ReasoningEffort.NORMAL -> Color(0xFF34D399)
                            ReasoningEffort.MEDIUM -> Color(0xFFFBBF24)
                            ReasoningEffort.MAX -> Color(0xFFA855F7)
                        }
                        Surface(
                            onClick = {
                                showReasoningEffortDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = effortColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, effortColor.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Thinking Effort",
                                    tint = effortColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = currentReasoningEffort.name.lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = effortColor
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Choose effort",
                                    tint = effortColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // Right: Mic icon + Blue Circular Send Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Mic Icon (as seen in user screenshot)
                        IconButton(
                            onClick = {
                                // Informative prompt helper
                                onUpdateChatInputText(if (chatInputText.isBlank()) "Explain the project architecture" else chatInputText)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = Color(0xFFDBDEE1),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Send / Stop action
                        if (isThinking) {
                            Button(
                                onClick = onStopAI,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) Color(0xFF2979FF) else Color(0xFF313338)
                                    )
                                    .clickable(enabled = canSend, onClick = onSend),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (canSend) Color.White else Color(0xFF80848E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfessionalChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2B2D31),
        border = BorderStroke(1.dp, Color(0xFF383A40))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFF2F3F5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF949BA4),
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}
