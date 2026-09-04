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
    customModels: List<CustomModelConfig>,
    selectedModelId: String,
    onSelectCustomModel: (String) -> Unit,
    attachedFiles: List<AttachedFile> = emptyList(),
    onRemoveAttachedFile: (AttachedFile) -> Unit = {},
    taggedFiles: List<ProjectFileEntity> = emptyList(),
    onRemoveTaggedFile: (ProjectFileEntity) -> Unit = {},
    taggedSkills: List<AgentSkill> = emptyList(),
    onRemoveTaggedSkill: (AgentSkill) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isApproveForMeActive by remember { mutableStateOf(true) }
    var showModelMenu by remember { mutableStateOf(false) }

    val activeModelName = remember(selectedModelId, customModels) {
        val found = customModels.find { it.id == selectedModelId }
        found?.let { if (it.alias.isNotBlank()) it.alias else it.modelId } ?: "Daybreak Blue Ultra"
    }

    val canSend = chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1E1E22),
        border = BorderStroke(1.dp, Color(0xFF2E2E34)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        text = "Work with PenCode...",
                        color = Color(0xFF7D8590),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
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

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom row: Actions & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left controls: [+] and [Approve for me] pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // + button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAttachClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // "Approve for me" toggle pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isApproveForMeActive) Color(0xFF26292E) else Color(0xFF1E1E22),
                        border = BorderStroke(
                            1.dp,
                            if (isApproveForMeActive) Color(0xFF3B414B) else Color(0xFF30363D)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isApproveForMeActive = !isApproveForMeActive }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isApproveForMeActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isApproveForMeActive) Color(0xFF7EE787) else Color(0xFF8B949E),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Approve for me",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = if (isApproveForMeActive) Color(0xFFE6EDF3) else Color(0xFF8B949E)
                            )
                        }
                    }
                }

                // Right controls: Model Selector Pill, Mic Icon, Action Button (Stop/Send)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Model Selector Dropdown Pill
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF21262D),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showModelMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF58A6FF), CircleShape)
                                )
                                Text(
                                    text = activeModelName.take(16) + if (activeModelName.length > 16) ".." else "",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color(0xFFC9D1D9),
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Model",
                                    tint = Color(0xFF8B949E),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.background(Color(0xFF1E1E22))
                        ) {
                            if (customModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Daybreak Blue Ultra", color = Color.White, fontSize = 12.sp) },
                                    onClick = { showModelMenu = false }
                                )
                            } else {
                                customModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (model.alias.isNotBlank()) model.alias else model.modelId,
                                                color = if (model.id == selectedModelId) Color(0xFF58A6FF) else Color.White,
                                                fontSize = 12.sp
                                            )
                                        },
                                        onClick = {
                                            onSelectCustomModel(model.id)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Voice / Mic icon button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { /* mic input trigger */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Action Button (Stop or Send)
                    if (isThinking) {
                        // White circle with dark stop square (directly matching screenshot!)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onStopAI),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF121417), RoundedCornerShape(2.dp))
                            )
                        }
                    } else {
                        // Send Circle button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (canSend) Color.White else Color(0xFF28282E),
                                    CircleShape
                                )
                                .clip(CircleShape)
                                .clickable(enabled = canSend, onClick = onSend),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color(0xFF121417) else Color(0xFF6E7681),
                                modifier = Modifier.size(15.dp)
                            )
                        }
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
