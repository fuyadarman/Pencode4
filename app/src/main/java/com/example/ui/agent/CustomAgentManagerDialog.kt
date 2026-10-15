package com.example.ui.agent

import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.multiagent.CustomAgent
import com.example.agent.multiagent.CustomAgentManager

/**
 * CustomAgentManagerDialog
 * Dialog for modifying existing agents, toggling capabilities, or creating new autonomous AI agents.
 */
@Composable
fun CustomAgentManagerDialog(
    onDismiss: () -> Unit,
    onSelectAgentForChat: ((CustomAgent) -> Unit)? = null
) {
    val agents by CustomAgentManager.agents.collectAsState()
    var editingAgent by remember { mutableStateOf<CustomAgent?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        Text("🤖", fontSize = 22.sp)
                        Column {
                            Text(
                                "Multi-Agent Hub",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Configure, modify, or create AI agents",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8B949E))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (editingAgent != null || isCreatingNew) {
                    AgentEditorForm(
                        initialAgent = editingAgent,
                        onSave = { savedAgent ->
                            CustomAgentManager.addOrUpdateAgent(savedAgent)
                            editingAgent = null
                            isCreatingNew = false
                        },
                        onCancel = {
                            editingAgent = null
                            isCreatingNew = false
                        }
                    )
                } else {
                    // Create New Agent Button
                    Button(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Custom Agent", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Agents List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(agents) { agent ->
                            AgentItemCard(
                                agent = agent,
                                onEdit = { editingAgent = agent },
                                onDelete = { CustomAgentManager.deleteAgent(agent.id) },
                                onToggle = { CustomAgentManager.toggleAgentEnabled(agent.id, it) },
                                onSelect = {
                                    onSelectAgentForChat?.invoke(agent)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentItemCard(
    agent: CustomAgent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        border = BorderStroke(1.dp, if (agent.isEnabled) Color(0xFF30363D) else Color(0xFF21262D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(agent.iconEmoji, fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        agent.name,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        agent.role,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = agent.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF238636)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                agent.description,
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (agent.isCustom) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF43F5E)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Delete", fontSize = 11.sp)
                    }
                }

                TextButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF58A6FF)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Modify", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Use in Chat", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AgentEditorForm(
    initialAgent: CustomAgent?,
    onSave: (CustomAgent) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialAgent?.name ?: "") }
    var role by remember { mutableStateOf(initialAgent?.role ?: "") }
    var description by remember { mutableStateOf(initialAgent?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(initialAgent?.systemPrompt ?: "") }
    var iconEmoji by remember { mutableStateOf(initialAgent?.iconEmoji ?: "🤖") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (initialAgent != null) "Modify Agent: ${initialAgent.name}" else "Create New Autonomous Agent",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = iconEmoji,
                onValueChange = { iconEmoji = it.take(2) },
                label = { Text("Icon") },
                modifier = Modifier.width(70.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF30363D)
                )
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Agent Name") },
                placeholder = { Text("e.g. Shopping Pro Agent") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF30363D)
                )
            )
        }

        OutlinedTextField(
            value = role,
            onValueChange = { role = it },
            label = { Text("Agent Role / Title") },
            placeholder = { Text("e.g. E-Commerce Checkout Specialist") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF30363D)
            )
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            placeholder = { Text("Brief description of what this agent does") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF30363D)
            )
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Instructions & Prompt") },
            placeholder = { Text("Define how this agent should behave, navigate the web, and solve tasks...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF30363D)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFF8B949E))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val id = initialAgent?.id ?: "agent_${System.currentTimeMillis()}"
                        val agent = CustomAgent(
                            id = id,
                            name = name.trim(),
                            role = role.trim().ifBlank { "Autonomous Assistant" },
                            description = description.trim().ifBlank { "Custom AI agent" },
                            systemPrompt = systemPrompt.trim().ifBlank { "You are a helpful autonomous agent." },
                            iconEmoji = iconEmoji.ifBlank { "🤖" },
                            isCustom = true
                        )
                        onSave(agent)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Agent", fontWeight = FontWeight.Bold)
            }
        }
    }
}
