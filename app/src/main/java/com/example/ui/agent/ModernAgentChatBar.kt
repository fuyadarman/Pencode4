package com.example.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProjectFileEntity
import com.example.ui.AgentSkill
import com.example.ui.AttachedFile
import com.example.ui.CustomModelConfig

/**
 * Modern Agent ChatBar matching the requested UI design:
 * - Floating dark dock with 22.dp rounded corners
 * - Clean text input ("Work with PenCode...")
 * - Bottom row: [+] button, [Approve for me] toggle, Model selector pill, Mic icon, Stop/Send circle button
 */
@Composable
fun ModernAgentChatBar(
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
    taggedFiles: List<ProjectFileEntity> = emptyList(),
    onRemoveTaggedFile: (ProjectFileEntity) -> Unit = {},
    taggedSkills: List<AgentSkill> = emptyList(),
    onRemoveTaggedSkill: (AgentSkill) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E121A),
        border = BorderStroke(1.dp, if (canSend) Color(0xFF38BDF8).copy(alpha = 0.4f) else Color(0xFF252D3D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Attached / Tagged items chips row (if any)
            if (attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attachedFiles.forEach { file ->
                        ModernAttachmentChip(
                            label = file.name,
                            icon = Icons.Default.AttachFile,
                            accentColor = Color(0xFF58A6FF),
                            onRemove = { onRemoveAttachedFile(file) }
                        )
                    }
                    taggedFiles.forEach { file ->
                        ModernAttachmentChip(
                            label = "@${file.path}",
                            icon = Icons.Default.Description,
                            accentColor = Color(0xFF7EE787),
                            onRemove = { onRemoveTaggedFile(file) }
                        )
                    }
                    taggedSkills.forEach { skill ->
                        ModernAttachmentChip(
                            label = "/${skill.id}",
                            icon = Icons.Default.AutoAwesome,
                            accentColor = Color(0xFFA371F7),
                            onRemove = { onRemoveTaggedSkill(skill) }
                        )
                    }
                }
            }

            // Upper area: Input Text Field
            TextField(
                value = chatInputText,
                onValueChange = onUpdateChatInputText,
                placeholder = {
                    Text(
                        text = "Ask Codex Agent (@ files, / skills)...",
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp
                    )
                },
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                minLines = 1,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom row: Actions & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left controls: Attach button and MCP Selection button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onAttachClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = Color(0xFF8D96A0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // MCP Selection Button inside chatbar
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF6366F1).copy(alpha = 0.2f) else Color(0xFF21262D),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMcpServerIds.isNotEmpty()) Color(0xFF6366F1).copy(alpha = 0.6f) else Color(0xFF30363D)
                        ),
                        modifier = Modifier.clickable(onClick = onOpenSelectMcpDialog)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "MCP",
                                tint = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8) else Color(0xFF8D96A0),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (selectedMcpServerIds.isNotEmpty()) "MCP (${selectedMcpServerIds.size})" else "MCP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedMcpServerIds.isNotEmpty()) Color(0xFF818CF8) else Color(0xFF8D96A0)
                            )
                        }
                    }
                }

                // Right action: Stop or Send Button
                if (isThinking) {
                    Button(
                        onClick = onStopAI,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text("Stop", fontSize = 12.sp, color = Color.White)
                        }
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (canSend) Color(0xFF238636) else Color(0xFF21262D),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) Color.White else Color(0xFF6E7681),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernAttachmentChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF161B22),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
                color = Color(0xFFE6EDF3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF8B949E),
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}
