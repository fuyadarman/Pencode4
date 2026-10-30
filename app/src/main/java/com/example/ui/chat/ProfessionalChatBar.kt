package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
    onInjectInFlightTask: (String) -> Unit = {},
    isPlanModeActive: Boolean = false,
    onTogglePlanMode: (Boolean) -> Unit = {},
    skills: List<AgentSkill> = emptyList(),
    onOpenAgentsDialog: () -> Unit = {},
    onOpenScheduleDialog: () -> Unit = {},
    onOpenSkillsDialog: () -> Unit = {},
    onSelectSkill: (AgentSkill) -> Unit = {},
    isControlBrowserActive: Boolean = false,
    onToggleControlBrowser: (Boolean) -> Unit = {},
    onSelectCustomModel: ((modelId: String, provider: String, baseUrl: String, apiKey: String) -> Unit)? = null,
    currentModelDisplayName: String = "Gemini",
    onStartNewChat: () -> Unit = {},
    onSelectChatSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()
    var showPlusMenu by remember { mutableStateOf(false) }
    var showReasoningEffortDialog by remember { mutableStateOf(false) }
    var showQuickTaskInjectionBar by remember { mutableStateOf(false) }


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

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val updated = if (chatInputText.isBlank()) spokenText else "$chatInputText $spokenText"
                onUpdateChatInputText(updated)
            }
        }
    }

    // Outer Chat Bar Container matching user screenshot with zero extra items outside
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E2028),
        border = BorderStroke(1.dp, Color(0xFF2E3342)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Attached/tagged chips row inside the chatbar container
            if (attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
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

            // In-flight quick task injector bar popup when '#' is typed or pressed
            AnimatedVisibility(
                visible = showQuickTaskInjectionBar,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                QuickTaskInjectionBar(
                    isThinking = isThinking,
                    onInjectTask = {
                        onInjectInFlightTask(it)
                        showQuickTaskInjectionBar = false
                    },
                    onDismiss = { showQuickTaskInjectionBar = false },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Slash Command Menu popup when '/' is typed
            val isSlashTyped = chatInputText.startsWith("/") || chatInputText.contains(" /")
            val slashQuery = if (isSlashTyped) {
                val lastSlashIdx = chatInputText.lastIndexOf('/')
                chatInputText.substring(lastSlashIdx + 1)
            } else ""

            AnimatedVisibility(
                visible = isSlashTyped,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                SlashCommandMenuPopup(
                    query = slashQuery,
                    skills = skills,
                    onOpenAgentsDialog = {
                        onUpdateChatInputText("")
                        onOpenAgentsDialog()
                    },
                    onOpenScheduleDialog = {
                        onUpdateChatInputText("")
                        onOpenScheduleDialog()
                    },
                    onOpenSkillsDialog = {
                        onUpdateChatInputText("")
                        onOpenSkillsDialog()
                    },
                    onSelectSkill = { skill ->
                        onUpdateChatInputText("")
                        onSelectSkill(skill)
                    },
                    onSelectControlBrowser = {
                        onUpdateChatInputText("")
                        onToggleControlBrowser(true)
                    },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Controll Browser Active Mode Pill
            AnimatedVisibility(visible = isControlBrowserActive) {
                Surface(
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🌐", fontSize = 12.sp)
                        Text(
                            text = "Controll Browser Mode Active",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove mode",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onToggleControlBrowser(false) }
                        )
                    }
                }
            }

            // Text Input with "Make, test, iterate..." placeholder
            TextField(
                value = chatInputText,
                onValueChange = { input ->
                    if (input.contains("#")) {
                        showQuickTaskInjectionBar = true
                        onUpdateChatInputText(input.replace("#", ""))
                    } else {
                        onUpdateChatInputText(input)
                    }
                },
                placeholder = {
                    Text(
                        text = "describe your request (#task,/skills,@files)",
                        color = Color(0xFF8E95A5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                },
                textStyle = TextStyle(
                    color = com.example.ui.theme.AppTheme.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF60A5FA)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                minLines = 1,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom action row: [+] [ [ ] Plan ] [ ::: Free ⌄ ]          [Mic] [↑]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left action controls: [+] [ [ ] Plan ] [ 🧠 Normal ⌄ ] [ 🔀 MCP ]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())
                ) {
                    // 1. Plus Button (+)
                    Box {
                        IconButton(
                            onClick = { showPlusMenu = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add attachment or tools",
                                tint = Color(0xFF8E95A5),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPlusMenu,
                            onDismissRequest = { showPlusMenu = false },
                            modifier = Modifier.background(com.example.ui.theme.AppTheme.bgSurfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Attach File", color = com.example.ui.theme.AppTheme.textPrimary, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = com.example.ui.theme.AppTheme.accentBlue, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showPlusMenu = false
                                    onAttachClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Live Task (#)", color = Color(0xFFA5B4FC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showPlusMenu = false
                                    showQuickTaskInjectionBar = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (selectedMcpServerIds.isNotEmpty()) "MCP Servers (${selectedMcpServerIds.size})" else "MCP Servers",
                                        color = com.example.ui.theme.AppTheme.textPrimary,
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

                            HorizontalDivider(color = com.example.ui.theme.AppTheme.border)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Thinking: ${currentReasoningEffort.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                        color = com.example.ui.theme.AppTheme.textPrimary,
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
                    // 2. Tune Controls Button (Houses Plan, Thinking & Reasoning, MCP, and Chat Sessions)
                    ChatTuneControlsButton(
                        isPlanModeActive = isPlanModeActive,
                        onTogglePlanMode = onTogglePlanMode,
                        currentReasoningEffort = currentReasoningEffort,
                        onSelectReasoningEffort = onSelectReasoningEffort,
                        selectedMcpServerIds = selectedMcpServerIds,
                        onOpenSelectMcpDialog = onOpenSelectMcpDialog,
                        onOpenReasoningDialog = { showReasoningEffortDialog = true },
                        onStartNewChat = onStartNewChat,
                        onSelectChatSession = onSelectChatSession
                    )
                }

                // Right action controls: [Mic] [↑]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to AI Studio...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = Color(0xFF8E95A5),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isThinking) {
                        Button(
                            onClick = onStopAI,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (canSend) Color(0xFF2563EB) else Color(0xFF282B34)
                                )
                                .clickable(enabled = canSend, onClick = onSend),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(17.dp)
                            )
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
        color = com.example.ui.theme.AppTheme.bgSurfaceElevated,
        border = BorderStroke(1.dp, com.example.ui.theme.AppTheme.border)
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
                color = com.example.ui.theme.AppTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = com.example.ui.theme.AppTheme.textSecondary,
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}
