package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AgentSkill

data class SlashActionItem(
    val command: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String = "",
    val isSystemAction: Boolean = false,
    val onAction: () -> Unit
)

/**
 * SlashCommandMenuPopup
 * Interactive menu triggered when user types '/' in the chat bar.
 * Provides quick access to Agents, Schedules, and Skills.
 */
@Composable
fun SlashCommandMenuPopup(
    query: String,
    skills: List<AgentSkill>,
    onOpenAgentsDialog: () -> Unit,
    onOpenScheduleDialog: () -> Unit,
    onOpenSkillsDialog: () -> Unit,
    onSelectSkill: (AgentSkill) -> Unit,
    onSelectControlBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cleanQuery = query.lowercase().trim()

    val systemCommands = listOf(
        SlashActionItem(
            command = "/browser",
            title = "Controll Browser",
            subtitle = "Execute all AI online & browser tasks live in Preview Tab",
            iconEmoji = "🌐",
            isSystemAction = true,
            onAction = onSelectControlBrowser
        ),
        SlashActionItem(
            command = "/agents",
            title = "Multi-Agent Hub",
            subtitle = "Modify existing agents, configure roles, or create new agents",
            iconEmoji = "🤖",
            isSystemAction = true,
            onAction = onOpenAgentsDialog
        ),
        SlashActionItem(
            command = "/schedule",
            title = "Schedule Autonomous Tasks",
            subtitle = "Set recurring or future jobs (e.g. job apply, shopping, posts)",
            iconEmoji = "⏰",
            isSystemAction = true,
            onAction = onOpenScheduleDialog
        ),
        SlashActionItem(
            command = "/skills",
            title = "Agent Skills & Capabilities",
            subtitle = "View and customize system capabilities and knowledge",
            iconEmoji = "⚡",
            isSystemAction = true,
            onAction = onOpenSkillsDialog
        )
    )

    val filteredSystem = systemCommands.filter {
        cleanQuery.isBlank() || it.command.contains(cleanQuery) || it.title.lowercase().contains(cleanQuery)
    }

    val filteredSkills = skills.filter {
        cleanQuery.isNotBlank() && (it.name.lowercase().contains(cleanQuery) || it.id.lowercase().contains(cleanQuery))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(6.dp)) {
            // System Hub Actions
            if (filteredSystem.isNotEmpty()) {
                item {
                    Text(
                        "SYSTEM & AGENT HUBS",
                        color = Color(0xFF8B949E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                items(filteredSystem) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onAction() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(item.iconEmoji, fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(item.command, color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(item.subtitle, color = Color(0xFF8B949E), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Skills Section
            if (filteredSkills.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "MATCHING SKILLS",
                        color = Color(0xFF8B949E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                items(filteredSkills) { skill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSkill(skill) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                        Column {
                            Text("/${skill.name}", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(skill.description, color = Color(0xFF8B949E), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
